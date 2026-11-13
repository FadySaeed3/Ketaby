"""Data packs for dictionary writers: everything an agent needs about each lemma in one JSON file,
so it can write without searching verse by verse.

  python3 prep.py next  TIER LANG N [--skip K] [--single|--multi] [--out pack.json]   pick the next N unwritten lemmas of a tier
                  (--single / --multi: only lemmas whose base has one / several sense numbers; tier B uses Sonnet for single, Opus for multi)
  python3 prep.py words DSTRONG ... [--out pack.json]               a pack for given lemmas

TIER: A (count >= 50), B (10-49), C (2-9), D (1), N (proper names, any count < 50; names with 50+ stay in A).
LANG: heb (Hebrew + Aramaic) or grc.
Lemmas listed in content/dictionary/*/*.json or in reserved.txt (one dstrong per line, for agents already
working) are skipped. `next` appends the picked lemmas to reserved.txt.

Each item: dstrong, lemma, translit, gloss, count, kind ("name:person", "name:place", "name", or "word"),
senses (all sense numbers of the same base with gloss/count), renderings (Van Dyke's Arabic words for it, with
counts), defs (open lexicons: bdb/ubs/tbes for Hebrew, ubs/tbes/lsj/mm for Greek; trimmed), and occurrences:
all of them when count <= 12, otherwise an evenly spread sample. Each occurrence: ref (Arabic, as it must be
written in entries), ref_en, vd (Van Dyke verse, no diacritics), ar (the Van Dyke word(s) aligned to it),
orig (the Hebrew/Greek form), editions (STEPBible's edition letters: for Hebrew L = in the Leningrad text,
X = a word scholars supply that is not in it; for Greek, which printed texts have the word, e.g. K = Received Text only).
"""
import collections, glob, html, json, os, re, sqlite3, sys

HERE = os.path.dirname(os.path.abspath(__file__))
DB = sqlite3.connect(os.path.join(HERE, '..', 'build', 'ketaby.db'))
CONTENT = os.path.join(HERE, '..', '..', 'content', 'dictionary')
RESERVED = os.path.join(HERE, 'reserved.txt')
NAMES = ["تكوين", "خروج", "لاويين", "عدد", "تثنية", "يشوع", "قضاة", "راعوث", "١ صموئيل", "٢ صموئيل", "١ ملوك", "٢ ملوك",
         "١ أخبار", "٢ أخبار", "عزرا", "نحميا", "أستير", "أيوب", "مزمور", "أمثال", "جامعة", "نشيد الأنشاد", "إشعياء", "إرميا",
         "مراثي", "حزقيال", "دانيال", "هوشع", "يوئيل", "عاموس", "عوبديا", "يونان", "ميخا", "ناحوم", "حبقوق", "صفنيا", "حجي",
         "زكريا", "ملاخي", "متى", "مرقس", "لوقا", "يوحنا", "أعمال", "رومية", "١ كورنثوس", "٢ كورنثوس", "غلاطية", "أفسس",
         "فيلبي", "كولوسي", "١ تسالونيكي", "٢ تسالونيكي", "١ تيموثاوس", "٢ تيموثاوس", "تيطس", "فليمون", "عبرانيين", "يعقوب",
         "١ بطرس", "٢ بطرس", "١ يوحنا", "٢ يوحنا", "٣ يوحنا", "يهوذا", "رؤيا"]
AR = str.maketrans('0123456789', '٠١٢٣٤٥٦٧٨٩')
CODES = {i: c for i, c in DB.execute('select id, code from books')}
NOT_NAMES = {'God', 'Lord', 'LORD', 'Most High', 'Almighty', 'Christ', 'Messiah', 'Spirit', 'Holy Spirit', 'Sabbath',
             'Passover', 'Pentecost', 'Amen', 'Hallelujah', 'Hosanna', 'Abba', 'Rabbi', 'Rabboni', 'Maranatha', 'I', 'O'}
DIA = re.compile(r'[ً-ٰٟـ]')


def written():
    s = set()
    for f in glob.glob(os.path.join(CONTENT, '*', '*.json')):
        s |= {w['dstrong'] for w in json.load(open(f, encoding='utf-8')).get('words', [])}
    if os.path.exists(RESERVED):
        s |= {l.strip() for l in open(RESERVED) if l.strip()}
    return s


def base(ds):
    return re.match(r'[HG]\d+', ds).group(0)


def clean(h, limit):
    t = html.unescape(re.sub(r'<[^>]+>', ' ', h))
    t = re.sub(r'\s+', ' ', t).strip()
    return t if len(t) <= limit else t[:limit].rsplit(' ', 1)[0] + ' …'


def defs(ds):
    b = base(ds)
    out = {}
    for key, src, h in DB.execute("select key, src, html from lexdefs where key = ? or key = ? or key like ?", (ds, b, b + '%')):
        if key not in (ds, b) and not (src == 'tbes' and re.fullmatch(b + '[a-z]?', key)) and not key.startswith(ds):
            continue
        out.setdefault(src, [])
        if len(out[src]) < 3:
            out[src].append(clean(h, 1500 if src in ('bdb', 'ubs', 'tbes') else 900))
    return {k: ' ‖ '.join(v) for k, v in out.items()}


def kind(ds, d, gloss=None):
    """Proper names have a capitalised STEPBible gloss AND a name marker in an open lexicon."""
    if gloss is None:
        gloss = (DB.execute('select gloss from lemmas where dstrong = ?', (ds,)).fetchone() or [''])[0] or ''
    if not gloss[:1].isupper() or gloss.split('/')[0].strip() in NOT_NAMES:
        return 'word'
    t = ' '.join(d.values())
    if re.search(r'Names of People|n\.pr\.m|n\.pr\.f|A (man|woman) living|son of:|daughter of:', t):
        return 'name:person'
    if re.search(r'Names of Locations|n\.pr\.loc|A (place|city|region|town|mountain|river)', t):
        return 'name:place'
    if re.search(r'Names of (Deities|Groups)|n\.pr\.|n\.gent\.', t):
        return 'name'
    return 'name'     # a capitalised gloss is a name even when the lexicon text says nothing more


def tier(count, k):
    if k.startswith('name') and count < 50:
        return 'N'
    return 'A' if count >= 50 else 'B' if count >= 10 else 'C' if count >= 2 else 'D'


def occurrences(ds, count):
    # glob, not like: like ignores case, and H5838x (lowercase sense letter) is a different number from H5838X
    rows = DB.execute("select o.id, o.text, v.book, v.ch, v.v, v.text, o.editions, o.dstrong, o.dstrongs from owords o "
                      "join verses v on v.id = o.vd_verse where o.dstrong = ? or o.dstrongs glob ? order by o.id",
                      (ds, '*' + ds + '*')).fetchall()
    whole = re.compile(re.escape(ds) + r'(?![A-Za-z0-9])')
    rows = [r[:7] for r in rows if r[1] and (r[7] == ds or whole.search(r[8] or ''))]
    want = len(rows) if len(rows) <= 12 else (22 if count >= 50 else 14)
    if len(rows) > want:
        step = len(rows) / want
        rows = [rows[int(i * step)] for i in range(want)]
    out = []
    for oid, orig, b, c, v, vt, ed in rows:
        ar = ' '.join(t for (t,) in DB.execute('select t.text from links k join tokens t on t.id = k.token_id '
                                               'where k.oword_id = ? order by t.idx', (oid,)))
        out.append({'ref': f'{NAMES[b - 1]} {str(c).translate(AR)}: {str(v).translate(AR)}', 'ref_en': f'{CODES[b]} {c}:{v}',
                    'vd': DIA.sub('', vt), 'ar': DIA.sub('', ar), 'orig': orig, 'editions': ed})
    return out


def item(ds):
    r = DB.execute('select dstrong, lang, lemma, tr, gloss, count from lemmas where dstrong = ?', (ds,)).fetchone()
    d = defs(ds)
    k = kind(ds, d, r[4] or '')
    senses = [{'dstrong': a, 'gloss': g, 'count': n} for a, g, n in
              DB.execute('select dstrong, gloss, count from lemmas where dstrong like ? order by dstrong', (base(ds) + '%',))
              if re.fullmatch(base(ds) + '[A-Z]?', a)]
    if k.startswith('name') and len(senses) > 1:     # which verses each sister number covers, to tell same-name people apart
        for se in senses:
            refs = DB.execute("select distinct v.book, v.ch, v.v, v.id from owords o join verses v on v.id = o.vd_verse where o.dstrong = ? "
                              "order by v.id limit 3", (se["dstrong"],)).fetchall()
            se['sense_refs'] = [f'{NAMES[b - 1]} {str(c).translate(AR)}: {str(v).translate(AR)}' for b, c, v, _ in refs]
    rend = DB.execute('select json from rend where dstrong = ?', (ds,)).fetchone()
    return {'dstrong': ds, 'lemma': r[2], 'lang': r[1], 'translit': r[3], 'gloss': r[4], 'count': r[5], 'kind': k,
            'tier': tier(r[5], k), 'senses': senses, 'renderings': json.loads(rend[0])[:8] if rend else [],
            'defs': d, 'occurrences': occurrences(ds, r[5])}


def main():
    a = sys.argv[1:]
    out = a[a.index('--out') + 1] if '--out' in a else None
    skip = int(a[a.index('--skip') + 1]) if '--skip' in a else 0
    only = 'single' if '--single' in a else 'multi' if '--multi' in a else None   # tier B split: one sense number vs several
    nsenses = collections.Counter(base(d) for (d,) in DB.execute('select dstrong from lemmas where count > 0'))
    if a[0] == 'next':
        t, lang, n = a[1], a[2], int(a[3])
        langs = ('heb', 'arc') if lang == 'heb' else ('grc',)
        done = written()
        picked = []
        for ds, lg, cnt, gl in DB.execute('select dstrong, lang, count, gloss from lemmas order by count desc, dstrong'):
            if lg not in langs or ds in done or not cnt:
                continue
            if t in 'ABCD' and tier(cnt, 'word') != t:
                continue
            if t == 'N' and (cnt >= 50 or not (gl or '')[:1].isupper()):
                continue
            k = kind(ds, defs(ds), gl or '') if (gl or '')[:1].isupper() else 'word'
            if tier(cnt, k) != t:
                continue
            if only and (nsenses[base(ds)] > 1) != (only == 'multi'):
                continue
            if skip:
                skip -= 1; continue
            picked.append(ds)
            if len(picked) == n:
                break
        with open(RESERVED, 'a') as f:
            f.write(''.join(p + '\n' for p in picked))
    else:
        picked = [x for x in a[1:] if re.fullmatch(r'[HG]\d+[A-Z]?', x)]
    pack = [item(ds) for ds in picked]
    s = json.dumps(pack, ensure_ascii=False, indent=1)
    if out:
        open(out, 'w', encoding='utf-8').write(s)
        print(out, len(pack), 'items:', ' '.join(p['dstrong'] for p in pack))
    else:
        print(s)


if __name__ == '__main__':
    main()
