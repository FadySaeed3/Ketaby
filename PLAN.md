# Ketaby (كتابي) — Plan

Offline Android app: the full Arabic Bible (Van Dyke) linked word by word to the Hebrew and Greek originals, with Arabic transliteration, Arabic morphology, an Arabic dictionary built from open lexicons, Arabic notes on rare words drawn from HALOT and BDAG, and the user's own HALOT/BDAG PDFs viewable inside the app.

Agreed with Fady on 2026-11-10.

## Hard rules

1. **No HALOT or BDAG material in this repository.** Not the PDFs, not OCR text, not entry text, not translations or paraphrases of entries. `.gitignore` blocks the files. The only HALOT/BDAG-derived data allowed here is (a) page-number indexes (lemma → PDF page / printed page) and (b) original Arabic notes written by Claude about rare words, each a few sentences, citing the page.
2. The app never ships HALOT/BDAG. On first run the user picks his own copies from phone storage; the app copies them into its private storage and opens pages from there, offline.
3. Fully offline app. No network permission.
4. All Arabic content (dictionary, rare-word notes, study notes) is written in chat sessions inside Fady's subscription, then committed as JSON under `content/`. No paid API scripts.
5. Every content file records its sources. Notes not checked against HALOT/BDAG say so.

## Decisions

| Topic | Decision |
|---|---|
| Platform | Native Android, Kotlin + Jetpack Compose |
| Build | GitHub Actions builds the APK on every push; Fady installs from the run artifact |
| Hebrew transliteration | Classical Semitic values written in Arabic letters (ح ع ص ق ط kept distinct), rule-based, with tests |
| Greek transliteration | Two fields side by side: academic (Erasmian) and modern Greek pronunciation, rule-based, with tests |
| Rare words | Lemmas (excluding proper names) occurring 1, 2 or 3 times in the Hebrew Bible or Greek NT |
| HALOT copy | The full scanned 1859-page PDF (`HALOT-full.pdf`). The partial text-layer copy (א–ל) is used only to cross-check OCR, never for page numbers |
| BDAG copy | `BDAG.pdf` (text layer, 1080 pages) |

## Sources (open)

| Layer | Source | Licence |
|---|---|---|
| Arabic text | Smith & Van Dyke | Public domain |
| Hebrew text + morphology | Westminster Leningrad Codex + OpenScriptures morphhb | PD + CC BY 4.0 |
| Greek text + morphology | SBLGNT + MorphGNT | CC BY 4.0 / CC BY-SA |
| Sense-tagged texts, versification map, proper names | STEPBible-Data (TAHOT, TAGNT, TVTMS, TIPNR) | CC BY 4.0 |
| Arabic ↔ original alignment | sabdelmalik Van Dyke Strong's tagging | open (check per file) |
| Lexicon 1 | BDB (OpenScriptures HebrewLexicon / STEPBible TFBDB) | PD |
| Lexicon 2 | UBS Dictionary of Biblical Hebrew + UBS Dictionary of the Greek NT | CC BY-SA 4.0 |
| Lexicon 3 | LSJ, NT vocabulary (STEPBible TFLSJ) | CC BY 4.0 |
| Lexicon 4 | Moulton & Milligan, Vocabulary of the Greek Testament (1914–1929) | PD; Fady will upload a scan for OCR |

Note: UBS is share-alike, so the Arabic dictionary derived from it is CC BY-SA as well.

## Known facts from the pilot (Song of Songs)

- Van Dyke follows English versification. Hebrew Song 7:1 = Van Dyke 6:13, Hebrew 7:n = Van Dyke 7:(n−1). Use TVTMS for every book.
- HALOT-full printed page = PDF page − 57 up to about PDF page 422, then − 56 (verified on 68 pages). Always store both numbers, measured not computed.
- HALOT page headers OCR well (`tesseract heb --psm 7` on the top band); a longest-increasing-subsequence pass over header words removes OCR noise and brackets every lemma to 1–3 pages.
- Song of Songs has 76 rare lemmas; pilot notes are in `content/rare_words/hebrew/song.json`.

## Repository layout

```
PLAN.md, CLAUDE.md
app/                      Android app (Kotlin, Compose)
data-pipeline/            Python scripts that build the SQLite database and indexes
  sources.md              exact URLs/commits of every source
content/
  rare_words/hebrew/      one JSON per book
  rare_words/greek/
  dictionary/             Arabic dictionary batches
  study_notes/            chapter-level notes
  indexes/                halot_pages.json, bdag_pages.json
.github/workflows/        APK build
```

## Phases

0. **Scaffold** — this plan, CLAUDE.md, .gitignore, pipeline skeleton, minimal app, CI producing an APK. Commit the Song pilot.
1. **Texts and alignment** — build `ketaby.db` (SQLite): books, chapters, verses (Van Dyke), Hebrew/Greek words with lemma, Strong's, morphology, sense tags; Van Dyke word ↔ original word links; versification map. Checks: 31,102 Van Dyke verses; word counts equal the sources.
2. **Transliteration engine** — Hebrew → Arabic, Greek → Arabic ×2. Unit tests with hand-checked samples.
3. **App v1** — reader, tap a word → panel (original, transliteration, Arabic morphology, short meaning), interlinear view, concordance, search. Fady tests on his phone.
4. **HALOT/BDAG index + viewer** — lemma → page index for every lemma (Hebrew from HALOT headers, Greek from the BDAG index pages); import screen; in-app PDF viewer that opens on the right page.
5. **Arabic dictionary** — (a) one-line Arabic gloss for all ~14,000 lemmas; (b) full Arabic entries synthesised from the four open lexicons, in batches.
6. **Rare-word cards** — HALOT (Hebrew ~3,000) and BDAG (Greek ~2,800, count to be re-verified), book by book, same template as the Song pilot.
7. **Study notes** — verse/chapter notes on request.

## Content templates

Rare word (see `content/rare_words/hebrew/song.json`): `strong`, `lemma`, `translit`, `count_in_ot`, `refs_hebrew_numbering`, `song_refs_vandyke` (generalise to `refs_vandyke`), `vandyke_word`, `certainty` (واضح / فيه خلاف / غامض), `note_ar`, `halot{checked,pdf_page,printed_page}`, `sources`.
