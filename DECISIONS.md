# Decision log

Choices about fantasy-bff that the code shows but cannot explain, one line each. The format and
the rules live in the monorepo root `DECISIONS.md`; decisions that span repos go there instead.

---

[2026-09-11] fantasy-bff: a Paddle checkout attaches the buyer's Paddle customer, found or created by email, and only when the account's email is verified — having the web pass the email to Paddle.js instead was rejected because Paddle does not document combining that with a transaction made on the server, and an unverified address could name someone else's customer and open their billing portal to this account; a lookup that fails opens the checkout without a customer rather than failing the purchase.
