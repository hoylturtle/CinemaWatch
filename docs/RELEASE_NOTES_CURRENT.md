CinemaWatch 0.9.1 preview — open-source readiness remediation

- Build the minified, non-debuggable release variant; reject debug artifacts before signing/publication.
- Verify the real published 0.9.0 baseline and test code/data replacement on Android API29 and API35 with disposable CI signatures. Preserve the production signing certificate for published updates.
- Clear private maintenance photos, plans, experiment reports and associated caches together with cinema data; retain language preferences. Asset deletion removes its plan markers and only unshared photos.
- Pin GitHub Actions and the Gradle distribution checksum; clarify actual functionality, contribution instructions and future research.

No new localization accuracy, people-counting or automatic surveying capability is claimed. Backup data before installing previews. External exports are not removed by app reset. Fieldwatch attribution and fixed source baseline are retained.
