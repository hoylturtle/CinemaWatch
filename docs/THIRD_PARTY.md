# Source and dependency provenance

This is an inventory boundary, not a legal opinion or a completed license clearance.

|Portion|Evidence and terms|Status|
|---|---|---|
|Pinned Fieldwatch source|v1.1.21 / `5379a2049c351c2483f7f106ca68d5d6d5f00e64`; [source manifest](../third_party/fieldwatch/SOURCE_MANIFEST.json), [MIT](../third_party/fieldwatch/LICENSE), [notice](../third_party/fieldwatch/NOTICE)|51 manifest entries verified against recorded Git blob hashes; unchanged by remediation|
|IEEE and Bluetooth SIG lookup data|Source URLs and separate terms described in upstream NOTICE|Not covered by Fieldwatch's original-code MIT grant; redistribution/usage terms need maintainer review|
|Fast Pair model listings|Public partner/community provenance described in upstream NOTICE|No blanket MIT grant asserted; rights review pending|
|AndroidX, Kotlin and related dependencies|Gradle declarations and resolved `releaseRuntimeClasspath` report in CI build evidence; upstream NOTICE lists Apache-2.0 components|Resolved dependency report is not an SBOM or a complete transitive-license audit; full notices/license inventory pending|
|Original CinemaWatch work|[Root MIT License](../LICENSE), explicitly approved by maintainer on 2026-10-22|MIT grant applies to original code; upstream notices and separate dataset terms remain|

New public fixtures must be synthetic or explicitly authorized, with provenance recorded in the change. Existing upstream address/payload examples are preserved with upstream attribution; this does not prove every historic example was synthetically generated. Do not publish production device captures, signing backups, private cinema plans or customer identifiers.

Keep artifact hashes and source provenance separate from claims of field accuracy or license clearance. A matching hash proves byte identity, not permission to redistribute every underlying dataset.
