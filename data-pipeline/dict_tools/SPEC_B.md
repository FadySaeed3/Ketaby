# Tier B entries: words used 10–49 times (medium length)

Read `SPEC.md` first: its **Accuracy rules**, **Entry format** (JSON fields) and **Egyptian Arabic rules** all apply.
This file changes only the length and the shape of the entry.

## Your data pack
Your task gives a pack file made by `prep.py`: for each lemma its gloss, count, sense numbers, Van Dyke renderings with
counts, open-lexicon definitions (`defs`), and up to 14 occurrences spread over the Bible, each with the Arabic reference
to write (`ref`), the Van Dyke verse (`vd`) and the Van Dyke word aligned to this lemma (`ar`).
**Every occurrence in the pack carries this exact dstrong, so you may quote it without checking.** Write from the pack.
Use `ex.py` only for a verse that is not in the pack (and then it must show this dstrong), and never cite a verse from memory.

## Shape
- eg: `**المعنى:** ...` \n `**الاستخدامات والأمثلة:** ١) ... ٢) ... ٣) ...` \n `**في فاندايك:** ...` \n `**ملاحظة:** ...`
  - Meaning: 1–3 sentences (what it means, root or related word if useful, how the sense numbers split if there are several).
  - Examples: 2–4 numbered examples, each a short label, the ref, an exact Van Dyke quote (shortened with « ... » if long)
    and one sentence on what it shows. Pick examples that show different uses, and well-known verses when they exist.
  - In Van Dyke: the main renderings from `renderings`.
  - Note: only when there is something worth saying (a sense split, a wordplay, a related more common word, a translation
    point). If there is nothing, leave the Note section out entirely (then the entry has 3 sections).
- `eg` length: 110–200 words.
- `msa` and `en`: the same content and sections, same length range.

## Model (G5532, χρεία, 49 times)
```json
{"dstrong": "G5532", "lemma": "χρεία",
 "gloss": {"eg": "احتياج، حاجة، لزوم", "msa": "حاجة، احتياج، عوز", "en": "need, necessity"},
 "entry": {
  "eg": "**المعنى:** «خريا» يعني الاحتياج: حاجة ناقصة ولازم حد يسدها. جاية من فعل معناه «يعوز». أشهر تعبير بيها «يكون عنده خريا»، وفاندايك بيترجمه «محتاج» أو «ليه حاجة».\n**الاستخدامات والأمثلة:** ١) الاحتياج المادي: أعمال ٢: ٤٥ «ويقسمونها بين الجميع، كما يكون لكل واحد احتياج»، الكنيسة الأولى كانت بتوزع على قد احتياج كل واحد. ٢) «مش محتاج»: لوقا ٥: ٣١ «لا يحتاج الأصحاء إلى طبيب، بل المرضى»، يسوع بيرد على اللي استغربوا إنه بياكل مع الخطاة. ٣) احتياج الرب نفسه: لوقا ١٩: ٣١ «إن الرب محتاج إليه»، عن الجحش اللي هيدخل بيه أورشليم. ٤) اللزوم والمنفعة: ١ كورنثوس ١٢: ٢١ «لا تقدر العين أن تقول لليد: لا حاجة لي إليك!»، بولس بيقول إن كل عضو في الجسد ليه لزمة.\n**في فاندايك:** «حاجة» و«احتياج» و«محتاج» و«يحتاج».\n**ملاحظة:** في أفسس ٤: ٢٩ «حسب الحاجة» يعني الكلام الكويس هو اللي على قد احتياج السامع، مش أي كلام حلو والسلام.",
  "msa": "**المعنى:** «خريا» تعني الحاجة: نقصًا ينبغي سدّه، وهي من فعل بمعنى «أعوز». وأشهر تعبير بها «تكون له حاجة»، ويترجمه فاندايك «محتاج» أو «له حاجة».\n**الاستعمالات والأمثلة:** ١) الحاجة المادية: أعمال ٢: ٤٥ «ويقسمونها بين الجميع، كما يكون لكل واحد احتياج»، فالكنيسة الأولى وزّعت بحسب حاجة كل فرد. ٢) نفي الحاجة: لوقا ٥: ٣١ «لا يحتاج الأصحاء إلى طبيب، بل المرضى»، ردًّا على من استغربوا أكله مع الخطاة. ٣) حاجة الرب: لوقا ١٩: ٣١ «إن الرب محتاج إليه»، عن الجحش الذي سيدخل به أورشليم. ٤) اللزوم والنفع: ١ كورنثوس ١٢: ٢١ «لا تقدر العين أن تقول لليد: لا حاجة لي إليك!»، فلكل عضو في الجسد نفعه.\n**في فاندايك:** «حاجة» و«احتياج» و«محتاج» و«يحتاج».\n**ملاحظة:** في أفسس ٤: ٢٩ «حسب الحاجة» أي أن الكلام الصالح هو ما يوافق حاجة السامع، لا مجرد كلام حسن.",
  "en": "**Meaning:** chreia is need: something lacking that ought to be supplied, from a verb meaning \"to lack\". Its most common phrase is \"to have need\", which Van Dyke renders «محتاج» or «له حاجة».\n**Uses and examples:** 1) material need: Acts 2:45 \"distributed them to all, as any had need\": the first church shared according to each one's need. 2) no need: Luke 5:31 \"those who are well have no need of a physician, but those who are sick\", Jesus' answer to those surprised that he ate with sinners. 3) the Lord's need: Luke 19:31 \"the Lord has need of it\", the colt for the entry into Jerusalem. 4) usefulness: 1 Cor 12:21 \"the eye cannot say to the hand, I have no need of you\": every member of the body has its use.\n**In Van Dyke:** «حاجة», «احتياج», «محتاج» and «يحتاج».\n**Note:** in Eph 4:29 \"as fits the need\" means good speech is speech that meets the hearer's need, not merely fine words."},
 "based_on": ["ubs", "tbes"], "bdag_checked": false, "tier": "B"}
```

## Output
Same as SPEC.md, plus `"tier": "B"` on every entry. Write the JSON array to your output path, check it with
`python3 check.py --tier B <file>` and fix everything it reports (artificial passives, wrong refs, length).
