# Decision log

Non-obvious choices in fantasy-bff, one line each. The format and rules are in the monorepo
root's `DECISIONS.md`; choices that span repos go there.

---

[2026-09-11] fantasy-bff: whether the AI projection is served is one answer, `AiProjectionAvailability` (`ai-projection.enabled` AND `security.projection-model-enabled`), enforced at both doors and reported on a new public `GET /api/v1/features` that the web follows — rejected: a field on `/api/v1/versions`, which probes three downstreams per call (10 s read timeouts), so a preset would wait on ESPN's actuator; and folding the two properties into one, which would lose "model off, Who's hot on".
