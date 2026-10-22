# Contributing

The project-level license for original CinemaWatch work is awaiting a maintainer decision. These development instructions do not grant a license or imply that external contributions have accepted unresolved licensing terms. Existing third-party notices remain authoritative for their portions.

## Before a change

Read AGENTS.md, docs/UPSTREAM_PIN.md and the current capability boundary. File a focused proposal or link an existing issue, state current behavior, expected behavior and data implications. Do not describe prototype classes or copied upstream utilities as shipped features. No CLA/DCO requirement is asserted here; maintainers must choose any contribution terms explicitly.

## Development

Use JDK17, SDK35 and the checked-in Gradle wrapper. Work from a topic branch following the repository's branch guidance; do not push experimental work directly to main. Build debug locally without a maintainer signing key:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :fieldwatch-core:testDebugUnitTest
./gradlew :app:assembleDebugAndroidTest
```

Use a device/emulator for connected tests and document the API level. The CI upgrade script expects its separately built fixture. Keep DB migrations and export formats compatible; explain any intentional break. Add meaningful tests for behavioral changes, including unknown/stale/error cases. Supply matching Simplified Chinese, Traditional Chinese and English strings for UI changes.

## Provenance and test data

Keep the Fieldwatch pin and 51-file manifest auditable. Any upstream change needs a separate source/license review and updated provenance, not an unnoticed edit of copied files. Use synthetic or explicitly authorized fixtures. Explain the origin of complete wireless addresses, payloads and coordinates; never upload signing material, real customer identifiers, private floor plans or production exports to public issues.

## Pull requests

Describe the problem, final behavior, evidence and limitations. List commands actually run and their results. Distinguish simulated observations, emulator integration tests and physical RF tests. Never report future thresholds as measured accuracy. Include migration/rollback and user-data considerations when applicable. Use a maintainer-reviewed release process; do not request production secrets in fork workflows.

## Security and privacy reports

Do not open a public issue containing a secret, personal identifier or exploit details against a real deployment. If private vulnerability reporting is enabled, use it; otherwise ask maintainers for a private reporting channel without including sensitive details. No currently enabled reporting mechanism, response SLA or security email is invented here.

License terms, a dedicated security policy, support expectations and community conduct remain follow-up work; no SLA is promised.
