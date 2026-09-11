# Decision log

Non-obvious choices in fantasy-bff, one line each. The format and rules are in the monorepo
root's `DECISIONS.md`; choices that span repos go there.

---

[2026-09-11] fantasy-bff: whether the AI projection is served is one answer, `AiProjectionAvailability` (`ai-projection.enabled` AND `security.projection-model-enabled`), enforced at both doors and reported on a new public `GET /api/v1/features` that the web follows — rejected: a field on `/api/v1/versions`, which probes three downstreams per call (10 s read timeouts), so a preset would wait on ESPN's actuator; and folding the two properties into one, which would lose "model off, Who's hot on".
[2026-09-11] fantasy-bff: a Paddle checkout attaches the buyer's Paddle customer, found or created by email, and only when the account's email is verified — having the web pass the email to Paddle.js instead was rejected because Paddle does not document combining that with a transaction made on the server, and an unverified address could name someone else's customer and open their billing portal to this account; a lookup that fails opens the checkout without a customer rather than failing the purchase.
