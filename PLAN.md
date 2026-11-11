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
| HALOT copy | **Text-layer edition only** (CD-ROM layout, 3537 pages, sha256 `7ba7a134…c964`). The scanned edition is dropped for good. References are HALOT entry number + page/position in this file (no printed-book page numbers) |
| BDAG copy | `BDAG.pdf` (text layer, 1080 pages, sha256 `4daf02c8…dd95`) |
| Releases | Bump `versionName` in `app/build.gradle.kts` and add `.github/release-notes/v<version>.md`; the next push to main publishes a GitHub pre-release with `ketaby-v<version>.apk`. Signing uses the `KETABY_KEYSTORE_B64` / `KETABY_KEY_PASS` secrets when set (needed so updates install over the old version); otherwise a throwaway key per build |
| In-app links | Tapping a word opens the owner's PDF at the exact entry (page + y), highlighted; cross-references inside HALOT (→) and BDAG (s.) are tappable and jump to the target entry |

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

## Known facts

- Van Dyke follows English versification. Hebrew Song 7:1 = Van Dyke 6:13, Hebrew 7:n = Van Dyke 7:(n−1). Use TVTMS for every book.
- The text HALOT stores Hebrew in the BibleWorks `Bwhebb` font encoding (visual order, Latin characters). `data-pipeline/bw_decode.py` converts it to Unicode; mapping learned against Strong's headwords, tests in `data-pipeline/tests/`.
- HALOT index (`content/indexes/halot_index.json`, built by `halot_index.py`): 11,190 entries with page + y; 9,519 OSHB lemma keys, 97% mapped (homonyms resolved by overlap between the lemma's verses and the verses each entry cites); 284 low-confidence and 258 unmatched keys listed for manual fixes in `halot_overrides.json`; 7,143 internal cross-reference links. A few entry numbers are absent from the source file itself (e.g. 3873); their text sits inside the previous entry and `lemma_pos` gives the exact spot.
- BDAG index (`content/indexes/bdag_index.json`, built by `bdag_index.py`): 8,139 entries; 5,342 of 5,461 MorphGNT lemmas mapped (98%); 119 unmatched.
- Phase 1 done: `fetch_sources.py` + `build_db.py` build `ketaby.db` from STEPBible-Data (pinned). 31,102 Van Dyke verses (fully pointed, CC BY-SA), 382,210 Arabic word units, 305,648 Hebrew/Aramaic words, 141,860 Greek words (all editions, TR/KJV-type words flagged `K`/`k`), 99.8% of Arabic units linked to original words via STEPBible Translation Tags; 17,000 extended-Strong's lemmas, 11,240 linked to HALOT and 5,360 to BDAG. CI builds the DB into the APK assets (~110 MB raw, ~38 MB compressed).
- Phase 2 done: `translit.py` (rule-based) — Hebrew/Aramaic in Arabic letters with harakat (ח ع ط ص ق kept, ב/כ/פ spirants ڤ/خ/ف, vav = و), Greek academic (Erasmian) and modern readings without harakat; tests in `tests/test_translit.py`; columns `tr`, `tr2` on `owords` and `lemmas`.
- Lemma keys in the app are STEPBible extended Strong's (`H3820A`, `G2889`); both indexes carry `lemmas_dstrong` for them (OSHB keys kept for reference).
- Phase 3 done (v0.1.0): the app copies `ketaby.db` from its assets to private storage on first run (and after each update), then reads it offline. Screens: books → chapters → reader (tappable Arabic units, verse numbers, last position remembered), word panel (original word, transliteration, Arabic morphology from `morph/Morph.kt`, lemma, count, temporary English gloss, rare-word card, concordance button; HALOT/BDAG button disabled until Phase 4), interlinear view, concordance, search (Arabic without harakat; Strong's numbers; Hebrew/Greek lemma; English gloss; Arabic transliteration), settings (text size, scripture font Naskh/Plex, harakat, Hebrew cantillation, Greek reading). Fonts bundled under OFL (`licenses/FONTS.md`). CI also runs an emulator smoke test that taps through the main screens and prints screenshots and crash logs into the job log.
- English (v0.2.0): KJV (eBible USFM with Strong's) and BSB (Berean translation tables), both public domain, in `everses` with character spans linked to `owords` of the same verse (KJV 96%, BSB 99% of tagged phrases linked; Greek matched to TR-type words for KJV and to Nestle words for BSB). NKJV is not possible: full-text use needs a paid licence from Thomas Nelson.
- Languages (v0.2.0): interface in Egyptian, Modern Standard Arabic or English (English switches the layout to left-to-right); explanation language eg / msa / en / all three for cards and parsing. Content files carry `{eg, msa, en}`; msa and en are written by Claude alongside the Egyptian text. For the dictionary (Phase 5), English = a short Claude summary shown next to the open-lexicon entries themselves.
- Song of Songs: 76 rare lemmas, every card checked against the text HALOT, in `content/rare_words/hebrew/song.json`.

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
4. **HALOT/BDAG viewer** — done in v0.3.0: import screen (system file picker, private copy, sha256 checked against the index), PdfRenderer viewer that opens on the entry and highlights it to the next entry, tappable cross-references (HALOT 7,143, BDAG 32), zoom, night mode, back returns to the verse. Also in v0.3.0: new home screen (search/reference field, continue card with the verse, recent chapters, lexicon status, books grouped by section) with a home button on every screen, and the app icon (Arabic kaf cradling a Hebrew aleph in a ring of gold dots, drawn from the OFL fonts). Remaining: fix low-confidence and unmatched index mappings.
5. **Arabic dictionary** — (a) one-line Arabic gloss for all ~14,000 lemmas; (b) full Arabic entries synthesised from the four open lexicons, in batches.
6. **Rare-word cards** — HALOT (Hebrew ~3,000) and BDAG (Greek ~2,800, count to be re-verified), book by book, same template as the Song pilot.
7. **Study notes** — verse/chapter notes on request.

## Content templates

Rare word (see `content/rare_words/hebrew/song.json`): `strong`, `lemma`, `translit`, `count_in_ot`, `refs_hebrew_numbering`, `refs_vandyke`, `vandyke_word`, `certainty{eg,msa,en}` (واضح / فيه خلاف / غامض), `notes{eg,msa,en}`, `halot{checked, entry, headword, page, y, also?}`, `sources`. Greek cards use `bdag{...}` with the same shape.
