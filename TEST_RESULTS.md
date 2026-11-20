# Task 4 test results - 20 November 2026

## Test environment

- Compiled the recovered Java source with `javac --release 17` and included `lib/` dependencies.
- Updated the included runnable `campus-register.jar` with the compiled `Application.class`.
- Launched the Java application locally with a dedicated temporary H2 data directory in public-mode configuration.
- Used a private test-only password in an environment variable, not in source control.

## HTTP smoke test outcomes (all passed)

| Test | Actual result |
| --- | --- |
| Public-mode staff login is initialized securely from environment | `GET /api/session` returned `setupRequired=false` |
| Unauthenticated access to student directory is denied | HTTP 401 |
| Correct staff password accepted | HTTP 200, `signedIn=true` |
| Add synthetic student | HTTP 201 |
| Reuse same roll number | HTTP 409 |
| Query student directory | Record returned with `matches=1` |
| Add assessment marks | HTTP 201 |
| Record attendance for a date | HTTP 200 |
| Open student profile | Linked marks and attendance present |
| Stop and restart the app using same H2 database | Record, assessment and attendance remained present |

## Automated tests included

`src/test/java/college/register/RegisterTest.java` includes three JUnit Jupiter regression tests for duplicate/stale edits, attendance changes and stale conflicts, and marks/persistence/deletion cascading.

JUnit was **not** executed during Task 4 preparation because Maven was unavailable in this execution environment. Run `mvn clean test` on a machine with Maven or inspect the GitHub Actions build result after pushing. Docker deployment was not tested in this local environment; verify deployment after connecting the hosting account.

## Limitations

These tests used fictional/synthetic records and local HTTP. They do not prove that a public HTTPS URL exists. A live Render deployment, its external access, and environment variables must be checked after the service is created.
