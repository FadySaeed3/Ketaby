# Tier N entries: proper names (people, places, peoples) used fewer than 50 times

Read `SPEC.md` first: its **Accuracy rules**, **Entry format** (JSON fields) and **Egyptian Arabic rules** all apply.

## Your data pack
Your task gives a pack made by `prep.py`. For names, the open-lexicon definitions (`defs`, especially `tbes` and `ubs`)
give who the person is or where the place is, family links («son of: ...», «father of: ...»), the first mention, other
people with the same name, and often a meaning of the name. The pack also has the occurrences (all of them up to 12,
otherwise a spread sample) and Van Dyke's spellings (`renderings`). **Build the entry from these facts only.** Do not add
facts from memory beyond what is common knowledge about well-known biblical figures, and never invent family links.

## What to write
- Who or what: one or two sentences (person: whose son or daughter, role, period or book; place: region or kind of place;
  people group: whose descendants, where they lived). If the same Hebrew/Greek name belongs to several people under this
  one number, say so briefly («الاسم ده لـ ٣ أشخاص: ...»).
- Meaning of the name: only if the lexicons give one, and hedged («اسمه معناه غالبًا ...»). No hedge when the name is
  simply an ordinary word (Gibeah = «تل», Satan = «خصم»). Leave out meanings that look like folk etymology.
- Key places: 1–3 references from the pack (up to 4 for very well-known figures), the clearest ones, with a very short quote or context.
- Van Dyke spelling: the main form(s) from `renderings` (without prefixes like و/ب/ل when the plain form exists).

- Several sense numbers (`senses`): each item's `sense_refs` shows the first verses of every sister number, so you can
  tell which person or place this number covers. Name the sister number with its code when it helps («يوحنا المعمدان G2491G»).
- `based_on`: the open lexicons you actually used (only keys present in `defs`).

## Shape
- eg: `**مين ده:** ...` (or `**فين ده:**` for a place, `**مين دول:**` for a people) \n `**أهم المواضع:** ...` \n `**في فاندايك:** ...`
- `eg` length: 25–100 words (well-known people near the top, obscure names in genealogies near the bottom).
- `msa` and `en`: one or two sentences in one section (`**المعنى:**` / `**Meaning:**`): who/what, and 1–2 refs. 12–50 words.

## Models
```json
{"dstrong": "H7354", "lemma": "רָחֵל",
 "gloss": {"eg": "راحيل", "msa": "راحيل", "en": "Rachel"},
 "entry": {
  "eg": "**مين ده:** راحيل بنت لابان وأخت ليئة، الزوجة اللي يعقوب حبها أكتر، وأم يوسف وبنيامين. اسمها معناه غالبًا «نعجة».\n**أهم المواضع:** تكوين ٢٩: ٣٠ «وأحب أيضا راحيل أكثر من ليئة»؛ تكوين ٣٥: ١٩ ماتت وهي بتولد بنيامين «ودفنت في طريق أفراتة، التي هي بيت لحم»؛ إرميا ٣١: ١٥ «راحيل تبكي على أولادها»، واللي متى بيقتبسها بعدين عن أطفال بيت لحم.\n**في فاندايك:** «راحيل».",
  "msa": "**المعنى:** راحيل ابنة لابان وزوجة يعقوب المحبوبة وأم يوسف وبنيامين (تكوين ٢٩: ٣٠؛ ٣٥: ١٩)، ويبكيها إرميا ٣١: ١٥ على أولادها.",
  "en": "**Meaning:** Rachel, Laban's daughter, Jacob's favoured wife and mother of Joseph and Benjamin (Gen 29:30; 35:19); Jer 31:15 pictures her weeping for her children."},
 "based_on": ["bdb", "ubs", "tbes"], "halot_checked": false, "tier": "N"}
```
```json
{"dstrong": "G2321", "lemma": "Θεόφιλος",
 "gloss": {"eg": "ثاوفيلس", "msa": "ثاوفيلس", "en": "Theophilus"},
 "entry": {
  "eg": "**مين ده:** ثاوفيلس هو الشخص اللي لوقا كتب له إنجيله وسفر الأعمال، ومانعرفش عنه حاجة أكيدة غير كده. اسمه يوناني معناه «حبيب الله» أو «محب الله».\n**أهم المواضع:** لوقا ١: ٣ «أن أكتب على التوالي إليك أيها العزيز ثاوفيلس»؛ أعمال ١: ١ «الكلام الأول أنشأته يا ثاوفيلس».\n**في فاندايك:** «ثاوفيلس».",
  "msa": "**المعنى:** ثاوفيلس، الرجل الذي وجّه إليه لوقا إنجيله وسفر الأعمال (لوقا ١: ٣؛ أعمال ١: ١)، واسمه يوناني بمعنى «محب الله».",
  "en": "**Meaning:** Theophilus, the man to whom Luke addressed his Gospel and Acts (Luke 1:3; Acts 1:1); the Greek name means \"friend of God\"."},
 "based_on": ["ubs", "tbes"], "bdag_checked": false, "tier": "N"}
```

## Output
Same as SPEC.md, with `"tier": "N"`. Check with `python3 check.py --tier N <file>` and fix everything it reports.
