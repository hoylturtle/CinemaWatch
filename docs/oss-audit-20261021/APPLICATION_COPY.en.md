# Codex for OSS — three draft paragraphs

Draft only; not submitted. Each paragraph below is under500 characters including spaces and punctuation. Headings, counts and evidence notes are not part of the text to paste. The current official form should be checked for its actual field prompts before use.

## Paragraph 1 (397 characters)

CinemaWatch is a public Android 10+ project for authorized cinema-device inspection. It combines Wi-Fi AP scans and BLE advertisements with local asset records, manually confirmed RSSI baselines, maintenance rechecks, report previews and a manual floor-plan editor. Its multi-phone BLE pilot evaluates authorized test-tag regions; it does not claim customer tracking or accurate automatic mapping.

## Paragraph 2 (378 characters)

As of October 21, 2026, the repository has 69 main-branch commits and 11 preview releases. The latest main CI passed 218 unit tests, including 60 app tests and 158 reused-core tests, plus 9 Android tests. Fieldwatch code is pinned and attributed. The project is early-stage, with no stars or forks, and still needs project-level license clarification and real-device validation.

## Paragraph 3 (383 characters)

I would use Codex to improve release-build validation, privacy-safe deletion, migration tests and documentation, then develop reproducible signal-quality and region-evaluation benchmarks. A later research track would evaluate visual-inertial mapping with optional supported ranging. These are proposed next steps, not existing precision-location or automatic floor-plan capabilities.

## Evidence mapping (not for pasting)

1. Runtime paths in CAPABILITY_MATRIX.md: ScanEngine, repository, maintenance UI, reports, FloorPlanStore and PhoneFlow. Android minimum API29 from Gradle and published manifest.
2. repository-metrics.json; main run38004959930 and downloaded XML/instrumentation logs; third_party/fieldwatch/SOURCE_MANIFEST.json; absent root LICENSE and separate upstream notices. Stars/forks are a dated snapshot, not a lasting claim.
3. Explicitly future intent consistent with ROADMAP.md; not claimed as an already implemented repository fact. No promised accuracy, adoption, project acceptance or API-credit entitlement.
