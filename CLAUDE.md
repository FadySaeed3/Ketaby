# Instructions for Claude working in this repo

Read `PLAN.md` first. Work phase by phase and keep `PLAN.md` current when a decision changes.

## Never commit
- HALOT or BDAG files, OCR output, extracted entry text, or translations/paraphrases of entries. Only page-number indexes and short original Arabic notes (with page citations) are allowed. If in doubt, leave it out.
- The Jesuit (Dar el-Machreq) Arabic Bible: no scraped HTML, parsed text, footnotes or margin references read from the scans, and no built `jesuit.ketaby` package — not in the repo, not in CI, not in the APK. Only the code that builds it (`data-pipeline/jesuit/*.py`) and the fake test fixture (`.github/jesuit_fixture.py`) are committed. The package is built in the session and sent to Fady directly; he imports it on his phone.
- Anything large and regenerable (built SQLite DBs, downloaded source dumps). The pipeline rebuilds them; CI or Fady's build step produces the DB asset.

## Owner
Fady builds from his phone and does not write code. Explain changes in plain Egyptian Arabic, keep him informed of what to test on the phone, and deliver installable APKs through GitHub Actions artifacts.

## Code
- App: Kotlin + Jetpack Compose, minSdk 26, no INTERNET permission.
- Pipeline: Python 3, standard library + small pinned deps listed in `data-pipeline/requirements.txt`.
- Arabic UI is RTL; Hebrew uses a Hebrew font with full pointing; Greek uses a polytonic-capable font.

## Content style (Arabic notes)
Natural Egyptian Arabic, active voice, short (2–4 sentences per rare word), state clearly what the lexicon says vs. Claude's own observation, and mark notes not checked against HALOT/BDAG.
