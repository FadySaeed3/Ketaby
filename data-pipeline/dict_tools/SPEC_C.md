# Tier C and D entries: rare words (used 2–9 times = C, once = D)

Read `SPEC.md` first: its **Accuracy rules**, **Entry format** (JSON fields) and **Egyptian Arabic rules** all apply.
This file changes only the length and the shape.

**Saving effort (read this):** build the output with a small script that loads the pack and copies `dstrong` and `lemma`
from it (typed Hebrew can look identical but store its vowel marks in another order). Take examples from the pack's
occurrences; look up an extra verse with `ex.py` only for a famous verse the pack lacks, at most one per entry.
`check.py` checks every reference written before «في فاندايك» (Meaning section included), so a verse that carries a
different sense number belongs in the Note, named with its number. References to the other Testament (a NT verse
quoting a Hebrew word, an OT verse behind a Greek word) are not checked and may be given anywhere. Words inside «Van Dyke quotes» are not passive-scanned.
Each occurrence has `editions`: a Hebrew word marked only X is not in the Hebrew text (a scholarly supplement), and a
Greek word with K or k alone is only in the Received Text; do not build an example on such a word without saying so.
Never mention the pack, the data, the tags or yourself in an entry (no «الباك», «مشيت ورا القاموس», "the pack's gloss"):
the reader sees only the dictionary. Say «القواميس بتقول ...» or «ترجمات زي الملك جيمس ...» instead.
In `en`, write references with the usual English abbreviations (John 3:16, 1 Cor 13:4, Ps 23:1), not the pack's `ref_en` codes.
Other agents work in parallel: keep your scripts in your own folder, `<output folder>/work_<output name>/`.

## Your data pack
Your task gives a pack file made by `prep.py`. For these words the pack lists **every occurrence** (ref, Van Dyke verse
`vd`, the Van Dyke word aligned to this lemma `ar`, the Hebrew/Greek form `orig`), plus the open-lexicon definitions
(`defs`), the sense numbers of the same root (`senses`) and the Van Dyke renderings. Write from the pack; every occurrence
in it carries this exact dstrong. Do not cite any other verse as an example of this word.

**Alignment caveat:** the `ar` field and `renderings` come from an automatic word alignment, which is often wrong for
rare words (it can point at a neighbouring word such as «الله» or «أو»). If `ar` does not fit the meaning, find the real
Van Dyke words for it in `vd` and write those. If the pack's `gloss` or `lemma` looks wrong against `defs` and the verses,
follow the defs and the verses, keep the pack's lemma string, and say so briefly in a note.
`based_on` lists the open lexicons you actually used (keys present in `defs`).

## What matters for rare words
- The meaning of a rare word is often uncertain. Say what the open lexicons give, and when they differ or the meaning
  rests mostly on context, say so plainly («المعنى مفهوم من السياق»، «القواميس بتختلف: ...»). Never sound surer than the evidence.
- Show how Van Dyke rendered it in each place (the `ar` field), because that is what the reader sees.
- Mention the word's count exactly as in the pack («جاية ٣ مرات بس»، «جاية مرة واحدة بس في الكتاب كله»).
- If it is part of a known pair or phrase, or related to a more common word, say so briefly (only real relations).

## Shape
- eg (C, 2–9 times): `**المعنى:** ...` \n `**المواضع:** ...` \n `**في فاندايك:** ...` and optionally \n `**ملاحظة:** ...`
  - Meaning: 1–3 sentences. Places: every occurrence (or, if there are more than 5, the clearest 4–5 and «وكمان: ...» refs
    for the rest), each with a short exact Van Dyke quote and, where useful, a few words of context. Separate places with «؛».
- eg (D, once): `**المعنى:** ...` \n `**الموضع:** ...` \n `**في فاندايك:** ...` (+ optional note).
- `eg` length: C 50–140 words, D 35–110 words.
- `msa` and `en`: **short**, one or two sentences in a single section: `**المعنى:** ...` / `**Meaning:** ...` giving the
  meaning, the count and the refs (no quotes needed). 15–60 words.

## Model C (H0922, בֹּהוּ, 3 times)
```json
{"dstrong": "H0922", "lemma": "בֹּהוּ",
 "gloss": {"eg": "فراغ، خلا", "msa": "خلاء، فراغ", "en": "emptiness, void"},
 "entry": {
  "eg": "**المعنى:** «بوهو» يعني الفراغ أو الخلا: حاجة فاضية مالهاش شكل. الكلمة جاية ٣ مرات بس، وفي التلاتة جاية جنب «توهو» (H8414) اللي معناها الخراب، فالاتنين مع بعض بيوصفوا حالة الأرض قبل ما ربنا يرتبها.\n**المواضع:** تكوين ١: ٢ «وكانت الأرض خربة وخالية»، أول وصف للأرض في الكتاب؛ إرميا ٤: ٢٣ «نظرت إلى الأرض وإذا هي خربة وخالية»، النبي بيشوف خراب يهوذا كأن الخلق بيرجع لورا؛ إشعياء ٣٤: ١١ «ومطمار الخلاء»، عن خراب أدوم.\n**في فاندايك:** «خالية» مرتين و«الخلاء» مرة.\n**ملاحظة:** التعبير «توهو ڤابوهو» عايش لحد النهارده في العبري الحديث بمعنى الفوضى الكاملة.",
  "msa": "**المعنى:** «بوهو» الخلاء والفراغ، وترد ٣ مرات فقط، دائمًا مع «توهو» (H8414): تكوين ١: ٢، وإرميا ٤: ٢٣، وإشعياء ٣٤: ١١، ويترجمها فاندايك «خالية» و«الخلاء».",
  "en": "**Meaning:** bohu is emptiness, void. It occurs only 3 times, always paired with tohu (H8414): Gen 1:2, Jer 4:23 and Isa 34:11; Van Dyke renders it «خالية» and «الخلاء»."},
 "based_on": ["tbes"], "halot_checked": false, "tier": "C"}
```

## Model D (H0017, אֲבוֹי, once)
```json
{"dstrong": "H0017", "lemma": "אֲבוֹי",
 "gloss": {"eg": "آه، يا ويلي", "msa": "صيحة ألم: آه", "en": "alas!, woe!"},
 "entry": {
  "eg": "**المعنى:** «أڤوي» صرخة وجع أو ضيق، زي «آه» أو «يا ويلي». الكلمة جاية مرة واحدة بس في الكتاب كله، فمعناها مفهوم من السياق ومن الكلمة اللي جنبها.\n**الموضع:** أمثال ٢٣: ٢٩ «لمن الويل؟ لمن الشقاوة؟»، أول سؤالين في سلسلة أسئلة عن السكّير. قبلها على طول «أوي» (H0188) اللي فاندايك ترجمها «الويل»، فالاتنين صرختين ورا بعض.\n**في فاندايك:** «الشقاوة».",
  "msa": "**المعنى:** «أڤوي» صيحة ألم أو ضيق، ترد مرة واحدة فقط في أمثال ٢٣: ٢٩ بعد «أوي» (H0188)، ويترجمها فاندايك «الشقاوة».",
  "en": "**Meaning:** avoy is a cry of pain or distress, found only once, in Prov 23:29, right after oy (H0188); Van Dyke renders it «الشقاوة»."},
 "based_on": ["tbes"], "halot_checked": false, "tier": "D"}
```

## Output
Same as SPEC.md, with `"tier": "C"` or `"tier": "D"` (the pack gives the tier). Check with
`python3 check.py --tier C <file>` (it handles C and D) and fix everything it reports.
