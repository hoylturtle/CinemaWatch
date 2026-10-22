# Roadmap

Updated 2026-10-21. Future milestones are proposals, not implemented capabilities. Release-variant validation, reference-aware data deletion, pinned Actions/wrapper inputs and documentation reconciliation are included in the current remediation; their acceptance requires passing checks. See [status](docs/REMEDIATION_20261021.md).

## P0 — publication and contribution readiness

1. **Clarify rights:** Original code MIT approved on 2026-10-22; retain upstream notices and complete separate review of IEEE/Bluetooth SIG/Fast Pair terms and dependency licenses. Acceptance: public readers can identify the grant and exceptions without inference. No automatic relicensing.
2. **Publish a verified release variant:** build/test release rather than re-sign debug; preserve the existing certificate. Acceptance: artifact manifest is not debuggable, package/version/certificate/digest agree and actual historical release upgrades preserve data.
3. **Complete deletion:** inventory Room, plans, pilot reports, photos, camera/export/update caches and orphans. Acceptance: reset/delete/restart tests verify appropriate files are removed, without deleting unrelated shared files.
4. **Reconcile documentation and issues:** replace present-tense roadmap claims with evidence-based current architecture; archive obsolete build blockers; review acceptance of the five open issues. Do not close them merely because similar code exists.

## P1 — trustworthy inspection and region experiments

- Split asset baselines by radio, band and receiver profile; retain authorized asset rollups. Preserve source time separately from processing time.
- Produce reproducible synthetic replay fixtures and an opt-in field protocol with device model/OS/placement, receiver calibration, tag settings and environmental notes. No customer identifiers or public business layouts by default.
- Freeze region rules on training/calibration routes, evaluate different held-out routes, dates and device placements. Report confusion matrices, macro metrics, unknown coverage, transition latency distributions and dropout recovery. Define acceptance targets before test collection; do not publish guessed accuracy.
- Calibrate confidence on held-out ground truth and report reliability/Brier or equivalent measures. Add explicit reason codes for weak evidence, model mismatch, clock drift, load, stale data and moved anchors.
- Add API29/31/33/35+ permission/background tests, multiple physical vendors, true release tests, migration schema exports and coverage measurement. A method count is not coverage.
- Tighten LAN node enrollment and session lifecycle. Scope keys to sessions, bind registered nodes to areas, plan revoke/rejoin and distinguish authenticated data from trustworthy measurements.
- Add contribution/security policies, pinned build inputs, dependency updates and license/SBOM evidence. Keep release secrets out of contributor builds.

## P2 — first real fusion module

Deliver per-source quality and typed observation adapters before complex ML. Compare the existing BLE region baseline with robust per-node medians, calibrated radio fingerprints and topology-constrained temporal models. Add Wi-Fi fingerprints only for known authorized transmitters/receiver localization; never assume AP scans observe all customer phones. Avoid automatic cross-radio identity linkage for unregistered visitors. See [fusion proposal](docs/MULTISIGNAL_FUSION_PROPOSAL.md).

Acceptance: identical replay inputs produce deterministic outputs; unknown states are preserved; packet loss cannot increase confidence; field results beat the frozen baseline on held-out sessions, with disclosed failure cases.

## P3 — dual-device assisted surveying research

1. Capability survey and two-device shared-frame demonstration using camera/IMU plus a known visual marker and a measured reference.
2. Manual wall-corner capture with tracking-quality gates; optional depth support and optional compatible peer ranging.
3. Closed-loop drift checks, multi-room relocalization, range/visual residual rejection, human geometry editing and versioned map export.
4. Independent measurement against tape/laser reference plans in bright/dark, feature-poor, glass and reflective scenes. Publish dimension/closure/alignment errors and map completeness; do not label conceptual or visually plausible geometry accurate.

Stop gates: no common frame, unsupported ranging hardware, tracking lost, NLOS bias or inconsistent scale must pause mapping and offer manual editing. Two phones exchanging RSSI are not sufficient to reconstruct walls.

## Explicitly outside these milestones

Production customer journey reconstruction, cross-day person identity, RF-to-member/POS linkage, exact crowd counting from radio counts, projector/HVAC control and cloud SaaS operations. Separate legal, product and technical review would be needed for any changed scope.
