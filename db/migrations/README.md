# SQL migration policy

`V001__initial_schema.sql` is the approved six-entity baseline. `../schema.sql`
is a psql compatibility entrypoint that includes it, not a second schema definition.
Once installed, do not edit V001 to introduce future features.

The explicit setup command installs V001 into an empty public schema, optionally
loads development fixtures, and records its SHA-256 checksum in `schema_migration`.
All three operations use one transaction. The extra ledger is technical metadata,
not a seventh business entity. A populated schema is refused rather than reset.

`V002__authentication.sql` adds authentication and nullable customer/engineer login
links. V003 adds the intake notification/audit tables and ticket display sequence.
V004 adds category-to-specialization rules, engineer notification recipients and
assignment audit details without rewriting existing tickets.
V005 adds resolution/cancellation/feedback storage and the feedback notification
event. Legacy resolved tickets are preserved without fabricated resolution records.
V006 adds escalation state/history/recipients and explicit SYSTEM audit actors.
V007 adds local delivery receipts/retries and network nodes, mappings, events and
unique event/service ticket associations. V008 expands the resolution-code catalog
while retaining all previous values.

Apply pending versions explicitly with `scripts/Update-Database.ps1`. DatabaseMigrate verifies
V001-V008 checksums, applies missing versions and ledger entries atomically, and is a no-op when
already current. It refuses missing/altered baselines or unknown newer versions.
Optional -DevAccounts provisions demo logins separately after migration; it does not
overwrite existing credentials/accounts. See [authentication](../../docs/AUTHENTICATION.md).

Future migrations use increasing names from V009 onward. Extend the runner's known
versions; each migration must be additive and record its checksum in the same
transaction. There is no automatic migration on application startup.

The psql compatibility entrypoint does not create the ledger. If an existing database
was installed manually, inspect its tables/constraints and record a verified baseline
through a separate reviewed adoption migration. Do not reapply V001 to it.

For the complete version inventory and ER diagrams, see [database design](../../docs/design/DATABASE.md).
