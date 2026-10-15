# Local setup, verification and final walkthrough

Start with README.md for Java 8/Maven/PostgreSQL and ignored external configuration.
Use the project root as working directory. Never copy private local settings into
src/main/resources or the JAR. No JPA/Hibernate is required.

## Fresh checkout / fresh dedicated database

1. Configure Java 8 and PostgreSQL tools; use scripts/mvn.ps1 to avoid invalid global
   JAVA_HOME settings. Copy config/application-local.properties.example to the ignored
   local file and supply your own datasource settings. Do not commit that file.
2. On an EMPTY dedicated database only, run .\scripts\Initialize-Database.ps1 -Seed.
3. Run .\scripts\Update-Database.ps1 -DevAccounts once. This applies V002-V008 and
   creates four demo users, with initial passwords in ignored config/dev-accounts.properties.
   On this workstation's existing database, only run Update-Database.ps1 WITHOUT
   -DevAccounts when future migrations are pending. Never rerun initialization/seed.
4. Run the commands below. Writable tests provision and remove their own PostgreSQL
   instance; they never reset the configured development database.

```powershell
.\scripts\mvn.ps1 test
.\scripts\Test-AuthIntegration.ps1
.\scripts\mvn.ps1 '-Dtelecom.integration=true' '-Dtest=DatabaseSchemaIntegrationTest' test
.\scripts\mvn.ps1 package
.\scripts\Verify-Delivery.ps1
```

Verify-Delivery checks Java 8 bytecode/private-config exclusion, generates synthetic
CSV/TXT samples, then starts a separate loopback packaged app on a free port with
background jobs disabled and datasource read-only. It checks login/CAPTCHA and protected
redirect, then stops only its own helper process. Its logs are in ignored target/.
It neither stops your IntelliJ process nor runs writable live business tests.

## IntelliJ / normal local run

Select **TelecomOSS Dev**, Java 8 SDK, main com.amdocs.telecom.TelecomApplication,
project-root working directory and --spring.profiles.active=dev. Stop/restart that
configuration to load newly compiled classes. Visit http://localhost:8080/login.

Alternatively, from the project root:

```powershell
.\scripts\mvn.ps1 spring-boot:run '-Dspring-boot.run.profiles=dev'
# Or, after packaging with Java 8 selected:
java -jar target/telecom-assurance-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

Read the saved initial password locally; it is not included in shared documentation.
Enter the answer to the actual displayed addition question, then enter the six-digit
development code shown on step 2 in the same session. No trip to /dev/otp is needed.
Explicit logout lets you change roles; separate private browser windows also work.
Password resets do not rewrite the saved initial-password file.

## Complete manual journey

1. customer.demo: Services / raise a ticket, select active SVC-MOB-001, create a fresh
   NETWORK_OUTAGE ticket with LOW priority and clear description. Track its detail,
   history, SLA deadlines/remaining seconds and notifications.
2. desk.demo: Assignment queue, open the fresh ticket and choose an eligible engineer
   from recommendations. ENG1008 is the original engineer.demo profile, Core Network /
   West; eligibility still depends on current settings. Supply a reason and assign.
3. engineer.demo: Assigned tickets, open that ticket, acknowledge, optionally request
   customer information/resume, then resolve with root cause, resolution text and code.
   Waiting for customer input does not pause SLA clocks. First response is one-time.
4. desk.demo: Review structured resolution, then close the ticket. Use a fresh ticket;
   old seed resolutions intentionally lack structured metadata and cannot be fabricated.
5. customer.demo: Open the closed ticket, submit one rating/comment, then view your
   notification inbox and mark messages read. Delivery updates independently, normally
   within five seconds. Customer access is restricted to linked service ownership.
6. desk.demo / manager.demo: Check current operational dashboard, SLA counters and
   Generate reports. Choose the ticket's local creation date, CSV or TXT, submit,
   refresh status and download after COMPLETE. Test another user's job URL: access
   is denied. Jobs expire/restart clears them; reports can be regenerated.
7. desk.demo: Network events and simulator, register a node, link one or more exact
   existing service codes, then simulate OUTAGE/DEGRADATION using its numeric node ID.
   Refresh event detail for automatically created ticket links. Re-submit the same
   form key to see replay protection. An empty node gives UNMAPPED for desk review;
   link an active service and Retry after review. These are real development tickets.
8. Review SLA monitoring after restart. Fixed-date seeded active tickets can already
   be overdue. New due levels escalate on the 60-second monitor; desk explicitly
   resumes the recorded operational state. Priority changes use original creation
   time. Do not edit live clocks/deadlines to test boundaries: the fixed-Clock scratch
   tests exercise exact warning, deadline and extra-resolution-duration thresholds.

The combined final rehearsal automates the event/assignment/engineer resolution/desk
closure/customer feedback/notification/manager report path using MockMvc and real
scratch PostgreSQL. The packaged smoke test separately validates the runnable artifact.
No live browser business mutation was needed for final verification.

For detailed rules use docs/REPORTS.md, docs/NOTIFICATIONS_EVENTS.md,
docs/SLA_MONITORING.md and docs/LIFECYCLE.md. MILESTONES.md contains exact final test
counts/migration state; ASSESSMENT.md maps the case-study requirements to code/tests.
