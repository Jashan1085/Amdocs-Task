# Milestone 3: authentication and shared UI

## Local use

For an existing V001 database, explicitly apply the additive migration:

```powershell
.\scripts\Update-Database.ps1
```

On the seeded development database, create the four demo accounts once:

```powershell
.\scripts\Update-Database.ps1 -DevAccounts
```

The latter enables the dev profile only for that command, applies pending migrations through V008,
then provisions customer.demo, desk.demo, engineer.demo and manager.demo. Their
random initial passwords are written to **config/dev-accounts.properties**, ignored
by Git and outside the application JAR. Passwords are never printed by the command.
The customer account links to CUST1001; the engineer links to ENG1008. Those seeded
profiles must exist and be unlinked. Other seeded profiles remain without logins.

Provisioning refuses an existing credentials file or duplicate account instead of
overwriting/resetting it. The four accounts/profile links are transactional. Migration
and optional account provisioning are separate operations: if provisioning fails,
V002 may already be committed; the migration ledger records the actual schema state.
After a password reset the initial-password file is no longer authoritative for that
account. Do not delete it to force re-provisioning; use the reset workflow.

Restart the application/IDE run to load the new classes and resources:

```powershell
.\scripts\mvn.ps1 spring-boot:run '-Dspring-boot.run.profiles=dev'
```

Open http://localhost:8080/login (or the port configured in your run). Enter the demo
username/password and answer the actual addition displayed beside the security check.
On step 2 the **development verification code is displayed above its input field**.
Enter that six-digit code and submit to finish sign-in; no separate inbox navigation
is needed. Password reset similarly shows its development reset code on the reset
form. Codes are session-bound and only shown by the development delivery adapter.
The /dev/otp inbox remains available for diagnostics in the same browser session.
Visiting /login while a live sign-in challenge is pending returns to verification;
use the CSRF-protected Restart sign-in button to discard it explicitly. Simply
viewing the password-recovery page also preserves pending verification.

There is no SMTP integration in this milestone. Without the dev profile, the demo
inbox controller is absent and the default delivery adapter fails closed. A real
delivery adapter must be supplied before sign-in can work outside development.
The reset-request response remains generic when delivery is unavailable.

## Behavior and interfaces

| Route | Behavior |
|---|---|
| GET/POST /login | Render CAPTCHA form / validate password and issue login OTP |
| GET/POST /login/otp | Render verification / complete authentication |
| POST /login/restart | CSRF-protected explicit restart; clears pending verification |
| GET/POST /forgot-password | Request reset code, with CAPTCHA and generic account response |
| GET/POST /reset-password | Verify reset code, enforce confirmation/policy, change password |
| GET /dev/otp | Dev-only inbox for this session's pending challenge; no ID lookup API |
| GET /dashboard | Route authenticated user to their role workspace |
| GET /customer/dashboard | Customer services/tickets entry point |
| GET /service-desk/dashboard | Desk intake/assignment entry point |
| GET /engineer/dashboard | Assigned work/lifecycle entry point |
| GET /manager/dashboard | Global operations, SLA and report entry point |
| POST /logout | CSRF-protected logout, clears session |

Customer and desk workspaces link to the service/ticket intake and tracking pages;
see TICKET_WORKFLOW.md. Desk assignment/reassignment is described in ASSIGNMENT.md.
Engineer work and the manual lifecycle are described in LIFECYCLE.md. The manager
workspace includes operational metrics, SLA monitoring and reports; see [REPORTS.md](REPORTS.md). Bootstrap CSS
is served locally from the pinned WebJar; no external CDN or browser JavaScript is
required. Shared layout, form labels, keyboard focus, mobile layout, and generic
403/404/500/503 pages are included.

Accounts can have several roles. Default workspace precedence is manager, desk,
engineer, customer. Route permissions always check the actual role; the demo seeds
use one role each. Missing customer/engineer profile links prevent sign-in for those
roles. DAOs remain internal: the authentication feature does not expose ticket data.

## Policies

- Passwords: BCrypt strength 10; new passwords at least 12 characters and at most
  72 UTF-8 bytes, avoiding BCrypt truncation. Confirmation must match.
- CAPTCHA: each rendered login/recovery form carries an opaque challenge ID; the
  answer hash stays in its issuing session. Loading another form does not replace
  existing questions. Each challenge is single-use, has a five-minute expiry, and
  is consumed even on a wrong answer. Keep at most 20 outstanding forms per session,
  removing expired challenges and evicting the oldest at the limit. After deployment
  or restart, reload old login/recovery pages to receive a new form and challenge ID.
- Login: five failed password attempts lock the account for 15 minutes. Counters
  reset after completed authentication or successful password reset. Expired locks
  restart the failure count. Row locks prevent concurrent attempts losing updates.
- OTP: random six digits, BCrypt hash in the database, five-minute expiry, five wrong
  valid-format attempts per challenge, single-use, and separate LOGIN/RESET purposes.
  A new code invalidates older active codes for the same user/purpose.
- Pending verification is bound to a random server-side session secret whose hash is
  stored with the challenge. Password validation alone grants no authenticated access.
  One pending login/reset flow is stored per browser session.
- Successful OTP login rotates the session ID and CSRF token. Sessions last 30 idle
  minutes; cookies are HttpOnly with SameSite=Lax. Local HTTP is supported. An HTTPS
  environment can set SERVER_SERVLET_SESSION_COOKIE_SECURE=true.
- Reset increments auth_version, unlocks the user, clears failures and consumes all
  pending user challenges atomically. Existing sessions are revoked on their next
  protected request. Disablement, role changes and active locks also revoke access.
- Password/OTP success, password/OTP failures, locks, CAPTCHA failures, and resets
  are recorded through JDBC login_history. Missing-user records have nullable user_id.
- General logs contain no OTP, plaintext password or database credentials. Session
  principals contain IDs/roles/display name, never a password hash. Public errors are
  generic; database failure returns 503 rather than empty data or SQL diagnostics.

The local OTP adapter stores at most 1000 unexpired messages in memory and never logs
codes. Restarting the application loses demo delivery messages; request a new code.
The inbox is explicitly a development feature, not an external delivery service.

## Schema and code handoff

V002 adds app_user, app_role, user_role, login_history and otp_challenge, plus nullable
unique user_id foreign keys on customer/network_engineer. It preserves V001, ticket
rows and immutable ticket status history. The explicit DatabaseMigrate runner checks
V001-V008 checksums and rejects unknown versions; pending DDL and ledger records
commit together. Normal startup still performs no migrations or account provisioning.

Core entrypoints: AuthenticationService for transactional flows; JdbcAuthenticationDAO
for SQL; security/ for principals, CAPTCHA, OTP delivery and session validation;
SecurityConfiguration for URL permissions; controller/ and templates/auth/ for forms.
The CLI DatabaseMigrate and DevAccountProvisioner are explicit tools, not automatic
startup writers. Future migrations must extend the runner's known version list.

## Tests

```powershell
.\scripts\mvn.ps1 test
.\scripts\Test-AuthIntegration.ps1
```

The second command creates separate temporary DAO and authentication databases, runs
unit tests, existing DAO tests and complete authentication MVC/JDBC/template tests,
then shuts down/removes the cluster. It never loads the private dev datasource into
those integration contexts. The earlier read-only dev baseline suite remains separate:

```powershell
.\scripts\mvn.ps1 '-Dtelecom.integration=true' '-Dtest=DatabaseSchemaIntegrationTest' test
```

Coverage includes all roles, password-only denial, session rotation, CAPTCHA/OTP
expiry/reuse/binding, lock boundaries and concurrent failures, reset confirmation,
session revocation, missing/disabled users, CSRF, logout, safe error pages, Bootstrap
assets, checksum refusal, migration idempotency and rollback of OTP consumption when
success-history insertion fails.
