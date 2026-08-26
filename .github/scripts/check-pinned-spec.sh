#!/usr/bin/env bash
#
# Checks one pinned copy of a producer service's OpenAPI spec against the producer's master.
#
# The point of the pin is that a producer's breaking change surfaces here rather than at
# runtime. The point of *this* script is that it only blames the branch it is running on.
#
# A producer merge makes the pinned copy stale in every open PR of this repo at the same
# instant, and none of those PRs did anything wrong — most of them have not gone near the
# spec. Failing them all buys nothing: the author cannot fix it from their branch without
# dragging an unrelated re-pin into it, and the re-pin PR is itself subject to this check,
# so it races the producer's next merge. That is how one afternoon lost three rounds to it.
#
# So: stale because of this branch is an error, stale because the world moved is a warning.
# The scheduled spec-freshness workflow is what stops the warning from being ignored — it
# runs with no base to compare against, and in that mode staleness is an error again.
set -euo pipefail

producer_repo=$1   # owner/name of the repo that owns the spec
producer_path=$2   # where the spec lives there
pinned_path=$3     # our verbatim copy
remedy=$4          # what to run here after re-copying it

: "${PRODUCER_TOKEN:?PRODUCER_TOKEN is not set. It needs a fine-grained PAT with read access to ${producer_repo} contents.}"

producer_copy=$(mktemp)
if ! GH_TOKEN="$PRODUCER_TOKEN" gh api "repos/${producer_repo}/contents/${producer_path}" \
      -H "Accept: application/vnd.github.raw" > "$producer_copy" 2> /tmp/gh-err.txt; then
  echo "::error::Could not read ${producer_path} from ${producer_repo} — PRODUCER_TOKEN is likely invalid, expired, or missing read access there. This is an auth failure, not a spec mismatch."
  cat /tmp/gh-err.txt
  exit 1
fi

if diff -u "$pinned_path" "$producer_copy"; then
  echo "${pinned_path} is in sync with ${producer_repo}."
  exit 0
fi

# No base to compare against — the scheduled run, where any staleness is the whole point.
if [ -z "${BASE_SHA:-}" ]; then
  echo "::error::${pinned_path} is behind ${producer_repo}. Copy ${producer_path} from ${producer_repo} into ${pinned_path} and run ${remedy}."
  exit 1
fi

base_copy=$(mktemp)
if ! gh api "repos/${GITHUB_REPOSITORY}/contents/${pinned_path}?ref=${BASE_SHA}" \
      -H "Accept: application/vnd.github.raw" > "$base_copy" 2>/dev/null; then
  echo "::error::Could not read ${pinned_path} at base ${BASE_SHA}, so there is no way to tell whether this branch caused the drift. Treating it as an error."
  exit 1
fi

if diff -q "$base_copy" "$pinned_path" > /dev/null; then
  echo "::warning::${pinned_path} is behind ${producer_repo}, and was already behind on the base branch — this PR did not cause it and does not have to fix it. Someone should land a re-pin: copy ${producer_path} from ${producer_repo} into ${pinned_path} and run ${remedy}."
  {
    echo "### \`${pinned_path}\` is behind \`${producer_repo}\`"
    echo
    echo "Not caused by this PR — the base branch is already behind. A separate re-pin PR should copy \`${producer_path}\` from \`${producer_repo}\` over it and run \`${remedy}\`."
  } >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
  exit 0
fi

echo "::error::This branch changed ${pinned_path} to something that is not ${producer_repo}'s current ${producer_path}. Either it was edited by hand, or it re-pinned and the producer has merged again since. Copy ${producer_path} from ${producer_repo} over it and run ${remedy}."
exit 1
