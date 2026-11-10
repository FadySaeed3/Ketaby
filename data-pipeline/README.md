# data-pipeline

Python scripts that download the open sources, normalise them and build `build/ketaby.db`, the SQLite database bundled in the app. Nothing here touches HALOT or BDAG text; the page-index scripts read the user's local PDFs and write only page numbers to `content/indexes/`.

Planned scripts (Phase 1 onward):

| Script | Output |
|---|---|
| `fetch_sources.py` | pinned downloads into `downloads/` (git-ignored) |
| `build_texts.py` | Van Dyke verses, Hebrew/Greek words with morphology |
| `build_alignment.py` | Van Dyke word ↔ original word links (Strong's) |
| `build_versification.py` | Hebrew/Greek ↔ Van Dyke verse map (TVTMS) |
| `translit.py` + `tests/` | Hebrew → Arabic, Greek → Arabic (academic + modern) |
| `rare_words.py` | lemma frequency, rare-word lists per book |
| `halot_index.py` | lemma → HALOT page (header OCR + monotone filter) |
| `bdag_index.py` | lemma → BDAG page (from the BDAG index pages) |
| `build_db.py` | assembles `build/ketaby.db` and checks counts |
