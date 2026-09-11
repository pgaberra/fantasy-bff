# Decision log

Non-obvious choices in fantasy-bff, one line each. The format and rules are in the monorepo
root's `DECISIONS.md`; choices that span repos go there.

---

[2026-09-11] fantasy-bff: whether the AI projection is served is one answer, `AiProjectionAvailability` (`ai-projection.enabled` AND `security.projection-model-enabled`), enforced at both doors and reported on a new public `GET /api/v1/features` that the web follows — rejected: a field on `/api/v1/versions`, which probes three downstreams per call (10 s read timeouts), so a preset would wait on ESPN's actuator; and folding the two properties into one, which would lose "model off, Who's hot on".
[2026-09-11] fantasy-bff: a Paddle checkout attaches the buyer's Paddle customer, found or created by email, and only when the account's email is verified — having the web pass the email to Paddle.js instead was rejected because Paddle does not document combining that with a transaction made on the server, and an unverified address could name someone else's customer and open their billing portal to this account; a lookup that fails opens the checkout without a customer rather than failing the purchase.
[2026-09-11] fantasy-bff: Who's hot's season lengths and default season come from projection-service's `/splits/seasons` (served as `/projection-model/splits/seasons`), and a split with no season is passed on without one, replacing the 82-game constant and "the projected season minus one" (#223, fantasy-projection-service#165) — 2026-27 is 84 games and "minus one" is wrong from opening night on; a free account's explicit window is judged against its own season's length with a service call, taken only for a window of five or fewer, and a constant 84 was rejected because 2025-26's free five (78-82) would then be refused.
