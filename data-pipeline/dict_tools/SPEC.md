# Writing dictionary entries for Ketaby (كتابي)

Ketaby is an offline Android Bible app: Van Dyke Arabic text linked word by word to the Hebrew and Greek.
Tapping a word opens a panel with a dictionary entry written by Claude in three languages:
Egyptian colloquial Arabic (`eg`, the main one, read by the owner Fady on his phone), Modern Standard Arabic (`msa`) and English (`en`).
You are writing a set of these entries. Quality bar: the existing entries in
`/home/claude/ketaby/content/dictionary/hebrew/batch_003.json` and `/home/claude/ketaby/content/dictionary/greek/batch_003.json`.
**Before writing, read at least three entries there in full** (e.g. H3820A, H7725G, H0518A for Hebrew; G4100, G3686, G2309 for Greek)
and match their length, structure, tone and density. Do not modify those files or anything in the repo.

**Saving effort (read this):** build the output with a small script that loads the pack and copies `dstrong` and `lemma`
from it (typed Hebrew can look identical but store its vowel marks in another order). Take examples from the pack's
occurrences; look up an extra verse with `ex.py` only for a famous verse the pack lacks, at most one per entry.
`check.py` checks every reference written before «في فاندايك» (Meaning section included), so a verse that carries a
different sense number belongs in the Note, named with its number. Words inside «Van Dyke quotes» are not passive-scanned.

## Tools (run from `/home/claude/ketaby/data-pipeline/dict_tools`)
- `python3 ex.py BASE "Book ch:v" "Book ch:v" ...` where BASE is the Strong's number WITHOUT the sense letter (H5221, G3551).
  First line(s): every sense number of BASE with its gloss and count. Then each verse in Van Dyke, followed by the list of
  (dstrong, original word) for every occurrence of BASE in that verse. An empty list means the word is NOT in that verse
  (the verse uses another word): do not use that verse as an example of this word.
- `python3 vd.py "Book ch:v" ...` prints Van Dyke text (no diacritics).
- Book codes: Gen Exo Lev Num Deu Jos Jdg Rut 1Sa 2Sa 1Ki 2Ki 1Ch 2Ch Ezr Neh Est Job Psa Pro Ecc Sng Isa Jer Lam Ezk Dan Hos Jol Amo Oba Jon Mic Nam Hab Zep Hag Zec Mal
  Mat Mrk Luk Jhn Act Rom 1Co 2Co Gal Eph Php Col 1Th 2Th 1Ti 2Ti Tit Phm Heb Jas 1Pe 2Pe 1Jn 2Jn 3Jn Jud Rev.
  If a code fails, try a variant.

## Data pack (when your task gives one)
`prep.py` makes a pack file for your lemmas: gloss, count, all sense numbers of the root, Van Dyke renderings with counts,
open-lexicon definitions (`defs`) and about 25 occurrences spread over the Bible (`ref` = the Arabic reference to write,
`vd` = Van Dyke verse, `ar` = the Van Dyke word aligned to this lemma, `orig` = the Hebrew/Greek form).
Every occurrence in the pack carries this exact dstrong, so you can quote it without checking. Start from the pack;
use ex.py for any other verse you want (famous verses not in the sample), with the rules below.
Shorter entries for less frequent words have their own specs: SPEC_B.md (10–49 times), SPEC_C.md (rare), SPEC_N.md (names).

## Accuracy rules (most important)
1. Every verse you cite as an example of THIS word must be checked with ex.py and must show THIS exact dstrong (including the sense letter).
   If a famous verse turns out to carry a different sense number or a different word, either leave it out or mention it only in the
   note section as "this verse has another number/word", as the existing entries do.
2. Quote Van Dyke exactly as vd.py/ex.py prints it (you may shorten with « ... » and add normal Arabic punctuation; never reword it).
   Then explain the example in your own words.
3. Verse references in Arabic text: Arabic book name + Arabic-Indic digits, e.g. «متى ٥: ٣», «١ صموئيل ٣: ١٠», «تكوين ٢٢: ١٣».
   In English: "Matt 5:3", "1 Sam 3:10".
4. Never quote or paraphrase HALOT or BDAG, and never claim what they say. Base the meaning on general knowledge, the open lexicons and the text itself.
5. Hedge anything debated (etymologies, textual variants, interpretive disputes): "كتير من العلما بيشوفوا"، "فيه رأي"، "الأرجح". Never state a guess as fact.
6. Count claims ("the word appears three times in this verse") must match ex.py output.

## Entry format
Each entry is a JSON object:
```json
{"dstrong": "H5221", "lemma": "נָכָה",
 "gloss": {"eg": "...", "msa": "...", "en": "..."},
 "entry": {"eg": "...", "msa": "...", "en": "..."},
 "based_on": ["bdb", "ubs"], "halot_checked": false}
```
Greek entries use `"based_on": ["ubs", "tbes"], "bdag_checked": false` instead.
Use the lemma exactly as given in your task list. `gloss` = a few words, comma-separated.

Each `entry` text has four sections separated by "\n", each starting with a bold lead-in:
- eg:  `**المعنى:** ...` \n `**الاستخدامات والأمثلة:** ١) ... ٢) ... ` \n `**في فاندايك:** ...` \n `**ملاحظة:** ...`
- msa: `**المعنى:** ...` \n `**الاستعمالات والأمثلة:** ...` \n `**في فاندايك:** ...` \n `**ملاحظة:** ...`
- en:  `**Meaning:** ...` \n `**Uses and examples:** 1) ... 2) ...` \n `**In Van Dyke:** ...` \n `**Note:** ...`
Meaning: 2–4 sentences (root, related words or English derivatives where real, how the sense numbers split if relevant).
Uses and examples: 5–8 numbered examples, each = short label + colon + verse ref + exact Van Dyke quote + one or two sentences on what it shows.
In Van Dyke: the common Van Dyke renderings in «» joined by «و».
Note: one insight the reader would not see in Arabic (another sense number, a wordplay, a textual or translation point).
The `eg` text is about 230–320 words. `msa` is the same content in MSA, `en` the same in English (English verse quotes may follow a standard modern English wording).

## Egyptian Arabic rules for `eg` (strict; Fady notices every slip)
- Natural spoken Egyptian, like a knowledgeable friend explaining: «يعني»، «علشان»، «كده»، «دي»، «اللي»، «مش»، «بيقول»، «ربنا». Never Fusha in `eg` except inside Van Dyke quotes.
- ACTIVE voice. Build sentences from who does the action. Do not use artificial passives:
  no «اتـ/يتـ/بيتـ/هيتـ» passive on verbs like اختار، اختبر، احترم، اعتبر، استخدم، انتقد، اقتبس، ترجم، شرح، بذل، رفع (bookish) — rephrase:
  «بيقتبسها عبرانيين» not «بتتقتبس»، «الكتاب بيستخدمها» not «بتستخدم». Allowed only for everyday verbs Egyptians really say this way
  (اتولد، اتكتب، اتقال، اتعمل، اتكسر، اتسرق، اتجوز، اتصلب) and only when the doer is unknown.
- State participles use the normal مفعول form: «مكتوب، معذّب، مربوط، مقفول»، never artificial «متـ» forms (no متعذب، متقفول، متكتب).
- No calques of English idioms; translate the meaning.
- Divine name: «ربنا» or «الله» in eg, «الله» in msa.
- Terms: Hebrew/Greek words transliterated in Arabic letters in «» (e.g. «شالَح»، «لوغوس»); keep Strong's numbers like (H3318) in Latin.
After writing, scan your `eg` texts for any word starting with ات/يت/تت/بيت/بتت/هيت or متـ and fix any that is an artificial passive or state form.

## Output
Write a JSON array of your entries (in the order given) to the output path in your task, UTF-8, `ensure_ascii=False`, indent 2,
with `"tier": "A"` on each entry. Validate it with `python3 check.py --tier A <file>` and fix what it reports
(artificial passives, verses without this dstrong, quotes that are not in the verse, length). Do not commit, push or edit repo files.
In your final reply list: each dstrong done, any verse you dropped because the tag did not match, and anything you were unsure about.
