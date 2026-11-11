"""update(lang, {dstrong: {eg, msa, en}}, gloss=None, batch=1, new=None): replace entry texts in a batch file;
`new` = list of full word dicts to append (for new batches)."""
import json, os

ROOT = __import__('os').path.join(__import__('os').path.dirname(__import__('os').path.abspath(__file__)), '..', '..', 'content', 'dictionary')


def update(lang, entries, gloss=None, batch=1, new=None, about=None):
    p = os.path.join(ROOT, lang, f'batch_{batch:03d}.json')
    if os.path.exists(p):
        d = json.load(open(p, encoding='utf-8'))
    else:
        d = {'batch': batch, 'about': about or '', 'words': []}
    if about:
        d['about'] = about
    by = {w['dstrong']: w for w in d['words']}
    for w in new or []:
        if w['dstrong'] in by:
            by[w['dstrong']].update(w)
        else:
            d['words'].append(w); by[w['dstrong']] = w
    for ds, e in entries.items():
        assert set(e) == {'eg', 'msa', 'en'}, ds
        by[ds]['entry'] = e
    for ds, g in (gloss or {}).items():
        by[ds]['gloss'] = g
    os.makedirs(os.path.dirname(p), exist_ok=True)
    json.dump(d, open(p, 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
    print(p, len(d['words']), 'updated', list(entries))
