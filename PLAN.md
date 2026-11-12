# Ketaby (كتابي) — Plan

Offline Android app: the full Arabic Bible (Van Dyke) linked word by word to the Hebrew and Greek originals, with Arabic transliteration, Arabic morphology, an Arabic dictionary built from open lexicons, Arabic notes on rare words drawn from HALOT and BDAG, and the user's own HALOT/BDAG PDFs viewable inside the app.

Agreed with Fady on 2026-11-10.

## Hard rules

1. **No HALOT or BDAG material in this repository.** Not the PDFs, not OCR text, not entry text, not translations or paraphrases of entries. `.gitignore` blocks the files. The only HALOT/BDAG-derived data allowed here is (a) page-number indexes (lemma → PDF page / printed page) and (b) original Arabic notes written by Claude about rare words, each a few sentences, citing the page.
2. The app never ships HALOT/BDAG. On first run the user picks his own copies from phone storage; the app copies them into its private storage and opens pages from there, offline.
3. Fully offline app. No network permission.
4. All Arabic content (dictionary, rare-word notes, study notes) is written in chat sessions inside Fady's subscription, then committed as JSON under `content/`. No paid API scripts.
5. Every content file records its sources. Notes not checked against HALOT/BDAG say so.
6. **No Jesuit (Dar el-Machreq) text in this repository or the APK** (copyrighted, 1988 revision): not the scraped pages, not parsed text, not footnotes read from the scans, not the built package. Fady imports `jesuit.ketaby` himself, like HALOT/BDAG.

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
| Lexicon 4 | Moulton & Milligan, Vocabulary of the Greek Testament (1914–1929) | PD; OCR text of the Internet Archive scan, `content/lexicons/mm.json.gz` |

Note: UBS is share-alike, so the Arabic dictionary derived from it is CC BY-SA as well.

## Known facts

- Van Dyke follows English versification. Hebrew Song 7:1 = Van Dyke 6:13, Hebrew 7:n = Van Dyke 7:(n−1). Use TVTMS for every book.
- The text HALOT stores Hebrew in the BibleWorks `Bwhebb` font encoding (visual order, Latin characters). `data-pipeline/bw_decode.py` converts it to Unicode; mapping learned against Strong's headwords, tests in `data-pipeline/tests/`.
- HALOT index (`content/indexes/halot_index.json`, built by `halot_index.py`): 11,190 entries with page + y; 9,519 OSHB lemma keys, 97% mapped (homonyms resolved by overlap between the lemma's verses and the verses each entry cites); 284 low-confidence and 258 unmatched keys listed for manual fixes in `halot_overrides.json`; 7,143 internal cross-reference links. A few entry numbers are absent from the source file itself (e.g. 3873); their text sits inside the previous entry and `lemma_pos` gives the exact spot.
- BDAG index (`content/indexes/bdag_index.json`, built by `bdag_index.py`): 8,139 entries; 5,342 of 5,461 MorphGNT lemmas mapped (98%); 119 unmatched.
- Phase 1 done: `fetch_sources.py` + `build_db.py` build `ketaby.db` from STEPBible-Data (pinned). 31,102 Van Dyke verses (fully pointed, CC BY-SA), 382,210 Arabic word units, 305,648 Hebrew/Aramaic words, 141,860 Greek words (all editions, TR/KJV-type words flagged `K`/`k`), 99.8% of Arabic units linked to original words via STEPBible Translation Tags; 17,000 extended-Strong's lemmas, 11,240 linked to HALOT and 5,360 to BDAG. CI builds the DB into the APK assets (~110 MB raw, ~38 MB compressed).
- Phase 2 done: `translit.py` (rule-based) — Hebrew/Aramaic in Arabic letters with harakat (ח ع ط ص ق kept, ב/כ/פ spirants ڤ/خ/ف, vav = و), Greek academic (Erasmian) and modern readings without harakat; tests in `tests/test_translit.py`; columns `tr`, `tr2` on `owords` and `lemmas`.
- Lemma keys in the app are STEPBible extended Strong's (`H3820A`, `G2889`); both indexes carry `lemmas_dstrong` for them (OSHB keys kept for reference).
- Phase 3 done (v0.1.0): the app copies `ketaby.db` from its assets to private storage on first run (and after each update), then reads it offline. Screens: books → chapters → reader (tappable Arabic units, verse numbers, last position remembered), word panel (original word, transliteration, Arabic morphology from `morph/Morph.kt`, lemma, count, temporary English gloss, rare-word card, concordance button; HALOT/BDAG button disabled until Phase 4), interlinear view, concordance, search (Arabic without harakat; Strong's numbers; Hebrew/Greek lemma; English gloss; Arabic transliteration), settings (text size, scripture font Naskh/Plex, harakat, Hebrew cantillation, Greek reading). Fonts bundled under OFL (`licenses/FONTS.md`). CI also runs an emulator smoke test that taps through the main screens and prints screenshots and crash logs into the job log. The app accepts one test extra, `--es sheetStrong <value>`: a Strong's number opens its word panel, `lex:halot:1200` opens the lexicon viewer on a page, `conc:G3056` opens a concordance, `read:40:7:28` opens the reader at a verse, `jesuit` opens the Jesuit screens, `jread:1:1:3` the Jesuit reader (the reader logs the verses on screen under the tag `KetabyReader`). Screenshots are base64 in the smoke job log; read them with `gh api repos/FadySaeed3/Ketaby/actions/jobs/<id>/logs` (artifact downloads are blocked in Claude sessions).
- App fixes after v0.4.0: v0.4.1 pinch zoom in the HALOT/BDAG viewer is one transform (scale + translation, no re-layout, focus point stays under the fingers, animated double tap and +/−). v0.4.3 the reader jumps back to the opened verse each time its content grows (English line, interlinear) until the reader touches the list; before, a verse near the end of a chapter slid off the screen, mostly in the NT where verses are short. The concordance keeps its scroll position when coming back from a verse.
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
4. **HALOT/BDAG viewer** — done in v0.3.0: import screen (system file picker, private copy, sha256 checked against the index), PdfRenderer viewer that opens on the entry and highlights it to the next entry, tappable cross-references (HALOT 7,143, BDAG 32), zoom, night mode, back returns to the verse. Also in v0.3.0: new home screen (search/reference field, continue card with the verse, recent chapters, lexicon status, books grouped by section) with a home button on every screen, and the app icon (Arabic kaf cradling a Hebrew aleph in a ring of gold dots, drawn from the OFL fonts). Index pass in v0.3.1: compound names found at the start of lines (53), plural headwords fixed (final-mem bug in the stem rule), 122 homonyms decided by comparing glosses with the candidate entries (`halot_overrides.json`, numbers only), BDAG forms with "/" and "(…)" split, deponent/-μι variants, 28 manual BDAG fixes (`bdag_overrides.json`). Now 11,305 Hebrew/Aramaic and 5,480 Greek lemmas open an entry. Left as is: 247 Hebrew lemmas where the index cannot tell homonyms apart (the word panel offers each candidate entry), 108 Hebrew lemmas with no entry (mostly pronominal suffixes H9030–H9039 and entries absent from the owner's text file, e.g. HALOT 9257–9274 שׁאב, שׁאג …), 97 Greek lemmas with no entry (e.g. ζάω, Σατανᾶς, γονεύς are missing from the BDAG file's entry headings).
5. **Arabic dictionary** — full entries for all ~17,000 lemmas (no short cuts: every lemma gets a gloss and a full entry in eg, msa and en), written in chat sessions in frequency order, Hebrew and Greek side by side. Next to them the word panel shows the open lexicons themselves and how Van Dyke renders the word. In v0.4.0: open lexicons in the word panel — BDB (8,673 Strong's), UBS Hebrew + Greek (13,912), LSJ (9,361), STEPBible brief / Abbott-Smith (18,707), Moulton & Milligan (4,302 NT lemmas; OCR of the 1930 scan, headwords found by type size and placed by alphabetical alignment, so OCR slips like "Caw." still land on ζάω; re-run `mm_extract.py` only to improve the extraction) — plus the Van Dyke renderings row (top 12 Arabic words per lemma from the alignment) and two-finger zoom in the HALOT/BDAG viewer. Dictionary files: `content/dictionary/{hebrew,greek}/batch_NNN.json`, template below.
5b. **Jesuit translation (v0.5.0)** — imported, not shipped. Pipeline in `data-pipeline/jesuit/`: `fetch_site.py` (Fady runs it; saves the book pages of arabiccatholicbible.com, 4 s apart, into `jesuit_html.zip`), `parse_site.py` (headings, verses with Arabic-Indic numbers, poetry lines, Esther's lettered Greek additions, book introductions; two site numbering slips fixed in `fixups()`), `build_pack.py` (SQLite package: `meta`, `jbooks` 73 books in Catholic order, `jintro`, `jitems` headings + verses in reading order, `jnotes` footnotes, `jmargin` margin references, `jmap` Jesuit verse ↔ Van Dyke verse by a per-book global alignment of word sets, so Mal 4:1→3:19, Joel 2:28→3:1, psalm titles, Dan 3 additions all land right). Note markers sit in the text as U+E000 n U+E001. App: `data/Jesuit.kt` (import into private storage, queries) and `ui/JesuitScreens.kt` (import screen, books, intro, reader with tappable note numbers and margin references, notes sheet); the Van Dyke reader can show the Jesuit verses under each verse (menu) and jump to the same place. Footnotes and margin references are not on the site: they are read from the 300 dpi scans by agents following a spec (`JSPEC.md` in the session, page tiles + the site text for anchoring), checked with `jcheck.py`, book by book (Gospels first) as the budget allows; the dictionary keeps priority. Pilot: Genesis 1–8 (printed pages 68–85), 71 notes, all markers placed. The smoke test uses a fake package built by `.github/jesuit_fixture.py`.
6. **Rare-word cards** — HALOT (Hebrew ~3,000) and BDAG (Greek ~2,800, count to be re-verified), book by book, same template as the Song pilot.
7. **Study notes** — verse/chapter notes on request.

## Content templates

Rare word (see `content/rare_words/hebrew/song.json`): `strong`, `lemma`, `translit`, `count_in_ot`, `refs_hebrew_numbering`, `refs_vandyke`, `vandyke_word`, `certainty{eg,msa,en}` (واضح / فيه خلاف / غامض), `notes{eg,msa,en}`, `halot{checked, entry, headword, page, y, also?}`, `sources`. Greek cards use `bdag{...}` with the same shape.

Dictionary entry (`content/dictionary/{hebrew,greek}/batch_NNN.json`, a list under `words`): `dstrong`, `lemma`, `gloss{eg,msa,en}` (one line), `entry{eg,msa,en}` (sections with bold lead-ins `**المعنى:**`, `**الاستخدامات:**`, `**في فاندايك:**`, `**ملاحظة:**`; English `**Meaning:**`, `**Uses:**`, `**In Van Dyke:**`, `**Note:**`), `based_on` (open lexicons used), `halot_checked` / `bdag_checked` (false until compared with Fady's HALOT/BDAG). Batches go in frequency order; batch 1 = the 26 most frequent Hebrew and 26 most frequent Greek lemmas (v0.4.0). Since v0.4.1 entries use the long format (about three times longer, sections `**المعنى:**` / `**الاستخدامات والأمثلة:**` / `**في فاندايك:**` / `**ملاحظة:**`, numbered examples quoting Van Dyke, each example checked against the STEPBible sense number of that verse). Batch 2 (lemmas 27–52 each side) shipped in v0.4.2, batch 3 (lemmas 53–78) in v0.4.4, batch 4 (lemmas 79–104) in v0.4.5; 208 entries so far. From batch 4 on, each batch is written by four parallel agents (13 lemmas each) following `data-pipeline/dict_tools/SPEC.md`, then reviewed with `dict_tools/check.py` (structure, length, cited verse actually carries the dstrong, Egyptian passive scan) and merged with `dict_tools/upd.py`. `ex.py` and `vd.py` there are the lookup helpers (they need the built DB). Batch 4 took about 25 minutes instead of about 2 hours. Batch lists come from `lemmas` ordered by `count` (Hebrew+Aramaic together, Greek apart), skipping dstrongs already written.
