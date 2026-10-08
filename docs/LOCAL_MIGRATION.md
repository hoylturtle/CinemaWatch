# Local / Cloud Migration Plan

1. Pin the known-good Fieldwatch upstream baseline.
2. Build untouched upstream before modifying scanner code.
3. Preserve upstream license and attribution.
4. Audit actual scanner models and write adapters instead of guessing fields.
5. Add Cinema/Zone/Asset domain and Room persistence.
6. Add Baseline/Anomaly/Device Hunt.
7. Add RF LAB with manual Zone selection.
8. Put PrivacyFilter before all Cinema Flow persistence.
9. Build Occupancy PoC and collect real auditorium calibration samples.
10. Build/test after every phase and keep changes in reviewable commits/PRs.
