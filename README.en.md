# CinemaWatch

[简体中文](README.md) · [Remediation status](docs/REMEDIATION_20261021.md)

A local-first Android 10+ preview app for authorized cinema-device inspections, built on pinned Fieldwatch v1.1.21 code. The interface supports Simplified Chinese, Traditional Chinese, and English, with system matching and a Simplified Chinese fallback.

Current preview: **0.9.0**. This is an early-stage project. Its multi-phone experiment evaluates region changes for explicitly authorized BLE test tags. It does not establish customer trajectories, attendance estimates, or accurate indoor coordinates.

## Implemented workflows

- Cinema, auditorium and common-area setup; authorized assets with names, locations, notes and Wi-Fi/BLE bindings.
- Wi-Fi AP and BLE advertisement inspection, quality checks, manually confirmed baselines, weak/unstable/missing evidence and maintenance rechecks.
- Maintenance notes and photos; native report previews, CSV sharing/export and aggregate-history import.
- Evidence-based radio categories and domestic brand ecosystems. These are hypotheses, not verified product identity or ownership.
- Manual floor-plan drawing, walls/rooms/doors, asset markers, landscape editing and gesture zoom/pan. Image/PDF import is raster-based; some CDR containers only provide a usable embedded PNG preview.
- A bounded LAN coordinator/collector experiment for registered BLE tags, RSSI comparison, region transitions, ground-truth checks and local JSON reports.
- Manual updates with package, version, hash and certificate checks before Android installation confirmation.

## Not implemented

Automatic surveying or floor-plan reconstruction; metric indoor localization; UWB/RTT/visual-inertial fusion; statistically calibrated location confidence; validated people counting; real customer journeys; ticketing/POS integrations; cross-day personal tracking; remote equipment control. Copied upstream utilities and `prototype/` are not proof of exposed app features.

## Install

[Download 0.9.0 APK](https://github.com/hoylturtle/CinemaWatch/releases/download/v0.9.0-preview/CinemaWatch-0.9.0.apk) · [Releases](https://github.com/hoylturtle/CinemaWatch/releases)

Versions from 0.3 use the persistent certificate. Older 0.1/0.2 temporary signatures may prevent replacement installation. Do not install transient Actions candidates. The current published preview is a re-signed debug variant and remains debuggable; this remediation builds a release variant and rejects debuggable artifacts before publication. A changed workflow does not mean a new release has already passed verification or shipped.

## Build

Use JDK17, Android SDK35 and the bundled Gradle8.11.1 wrapper, with access to Google/Maven/Gradle dependencies.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :fieldwatch-core:testDebugUnitTest
./gradlew :app:assembleDebugAndroidTest
```

A debug build needs no maintainer key and cannot replace the official app unless its signature matches. With a device/emulator connected, run `./gradlew :app:connectedDebugAndroidTest`. This alone does not prove replacement installation. The CI fixture replacement workflow additionally requires the artifacts prepared in `.github/workflows/android-preview.yml`. Publishing needs maintainer-configured signing secrets; never commit or request private signing material.

## Hardware experiment

Use fixed phones in different named areas, the same non-isolated Wi-Fi LAN, and stable-address owned/authorized BLE tags. Keep Bluetooth, Location and scan permissions enabled. Record independently known tag positions. Wi-Fi carries experiment messages; it is not a customer-phone sniffing or ranging implementation. See [pilot instructions](docs/PHONE_FLOW_PILOT.md).

## Data and validation

Unregistered identifiers remain transient within bounded inspection state; authorized asset bindings may be stored locally. Notes, photos, floor plans and labels can still contain sensitive business information. Review exports before sharing. GitHub is contacted for user-requested updates, and authorized-tag data is exchanged on the LAN during the pilot. Reset stops update/experiment writes and removes the database, photos, plans, experiment reports and related caches while retaining language settings. Asset deletion reclaims unshared photos and its plan markers. Exports saved outside the app must be deleted by the user. This is application-level deletion, not forensic flash erasure.

The latest main CI reports 60 app and 158 reused-core unit tests, plus 9 Android tests, passing on an API29 emulator. These are historical baseline counts, not coverage percentages or measured RF accuracy. The remediation adds API29/35 release checks; consult the PR for their actual results before claiming a pass. [Verification run](https://github.com/hoylturtle/CinemaWatch/actions/runs/38004959930).

## Provenance and licensing

Upstream: Fieldwatch v1.1.21, commit `5379a2049c351c2483f7f106ca68d5d6d5f00e64`. Preserve its [MIT notice](third_party/fieldwatch/LICENSE) and [third-party notice](third_party/fieldwatch/NOTICE). Assigned-number tables and Fast Pair data have separate rights/terms; see [provenance and pending reviews](docs/THIRD_PARTY.md). Original CinemaWatch code is licensed under the [MIT License](LICENSE). This grant does not replace Fieldwatch attribution or relicense third-party datasets.

Use synthetic or explicitly authorized fixtures in public contributions. Historical documents are marked accordingly. See [architecture](docs/ARCHITECTURE.md), [roadmap](ROADMAP.md) and [contributing](CONTRIBUTING.md).
