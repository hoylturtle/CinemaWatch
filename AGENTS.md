# CinemaWatch Agent Rules

## Goal
Build an Android cinema digital-device inspection system plus privacy-preserving aggregate RF analytics PoC from the Fieldwatch foundation.

## Non-negotiable
- Build untouched upstream before scanner refactors.
- Preserve Fieldwatch license/attribution.
- Do not equate MAC/BLE identifiers with people.
- Do not persist raw customer radio identifiers in Cinema Flow by default.
- Do not implement cross-day customer tracking.
- Do not link RF identifiers to member, ticket or POS identity.
- Cinema Watch may identify authorized cinema-owned assets.
- Occupancy outputs confidence/risk, not an invented exact people count from RF alone.
- MVP is local-first and must not depend on a cloud LLM.

## Development
Use branch cinema-watch-mvp.
Keep changes incremental and testable.
Document architecture changes and changed files.
