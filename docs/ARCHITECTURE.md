# Cinema Watch × Cinema Flow Architecture v1.1

## Shared RF foundation
Fieldwatch scanner/signature/observation layer is reused behind an adapter.

## Cinema Watch
Scanner -> RadioObservation -> AssetResolver -> Baseline -> Anomaly -> Device Hunt -> Report.

## Cinema Flow
Scanner -> PrivacyFilter -> zone/time aggregate buckets -> RF Density -> Occupancy / Aggregate Flow -> Dashboard.

## Hard separation
Cinema Watch may retain identifiers for authorized cinema assets.
Cinema Flow must not persist raw customer MAC/BLE identifiers by default.

## Occupancy PoC
Phase 1 uses one Android phone as a mobile measurement probe. A manager selects an auditorium and samples for 2–3 minutes, recording planned count, actual count/gate count when available, and RF density. Fixed probes are considered only after correlation is validated.
