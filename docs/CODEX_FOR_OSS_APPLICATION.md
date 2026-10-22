# Codex for Open Source — application draft

Prepared 2026-10-22 against the official form. Draft only; not submitted. Each answer is under 500 characters including spaces and punctuation. Do not claim broad adoption or measured positioning accuracy.

Repository: https://github.com/hoylturtle/CinemaWatch
GitHub username: hoylturtle
Role: Primary maintainer

## Why does this repository qualify?

CinemaWatch is an early-stage Android project for local, authorized cinema-device inspection. It reuses pinned Fieldwatch code with attribution and adds asset records, maintenance rechecks, report previews and a manual floor-plan editor. Its reusable value is a documented inspection workflow and an authorized BLE region-testing pilot. I do not claim broad adoption, validated people counting or customer tracking.

Character count: 415

## How will you use API credits for your project?

I would use API credits for PR review, issue triage, regression-test generation, migration checks and release validation. I would also build reproducible synthetic RF replay and region-evaluation benchmarks. Requests would use public source code and synthetic or explicitly authorized test data; customer identifiers, private floor plans and production exports would stay out of prompts.

Character count: 387

## Anything else we should know?

I am the primary maintainer of CinemaWatch. The merged remediation passed 223 unit tests and 11 Android tests on each of API29 and API35, including upgrade checks using published 0.9.0 code. Original code is MIT-licensed; Fieldwatch attribution is preserved. I maintain three interface languages and fixed-certificate releases. Accurate positioning and automatic indoor mapping remain future research.

Character count: 401

## Evidence and remaining work

- [Merged remediation PR20](https://github.com/hoylturtle/CinemaWatch/pull/20).
- [Passing CI39696178012](https://github.com/hoylturtle/CinemaWatch/actions/runs/39696178012): 223 unit tests and 11 Android tests on each API29/API35 emulator, verified from downloaded logs/XML. One test per matrix entry seeds the old code; ten verify the replacement release. These are not coverage or field-accuracy percentages.
- [MIT](../LICENSE), [Fieldwatch MIT](../third_party/fieldwatch/LICENSE), [notice](../third_party/fieldwatch/NOTICE) and [third-party inventory](THIRD_PARTY.md). Dataset rights review and SBOM remain open.
- [Current architecture](ARCHITECTURE.md), [roadmap](../ROADMAP.md) and [authorized-tag pilot](PHONE_FLOW_PILOT.md). RF counts are not people counts.

## Official form and practical preparation

[Program](https://developers.openai.com/community/codex-for-oss) · [Apply](https://openai.com/form/codex-for-oss/)

The official program offers selected maintainers six months of ChatGPT Pro with Codex, conditional security access and potential API credits. Selection is reviewed by OpenAI; no approval is guaranteed. The form asks for public GitHub profile/repository, maintainer role, the three short answers above, a ChatGPT-account email, name and OpenAI organization ID. Enter personal/account information yourself; do not commit it here.

Positioning recommendation: emphasize a maintained, local-first Android inspection workflow, traceable Fieldwatch reuse, test evidence and a concrete maintenance plan. This is an early-stage niche project, not a widely adopted infrastructure dependency. Current stars/forks/download counters, if included, must be refreshed and dated; downloads are not unique users. API-credit usage is a future plan; do not imply an existing API integration or customer-data upload.
