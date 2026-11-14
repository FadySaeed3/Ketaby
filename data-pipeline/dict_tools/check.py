"""Review helper for agent-written entries.

  python3 check.py [--tier A|B|C|N] file.json ...     (default tier A; C covers C and D; each entry's own "tier" wins)

Checks: section count, eg/msa/en length for the tier, Egyptian passive scan, every verse cited before «في فاندايك»
carries the entry's dstrong, every «quote» right after a reference is really in that Van Dyke verse, count claims for
rare words, and that the dstrong is not already written in content/dictionary."""
import glob, json, os, re, sqlite3, sys

HERE = os.path.dirname(os.path.abspath(__file__))
DB = sqlite3.connect(os.path.join(HERE, '..', 'build', 'ketaby.db'))
NAMES = ["تكوين","خروج","لاويين","عدد","تثنية","يشوع","قضاة","راعوث","١ صموئيل","٢ صموئيل","١ ملوك","٢ ملوك",
 "١ أخبار","٢ أخبار","عزرا","نحميا","أستير","أيوب","مزمور","أمثال","جامعة","نشيد الأنشاد","إشعياء","إرميا","مراثي",
 "حزقيال","دانيال","هوشع","يوئيل","عاموس","عوبديا","يونان","ميخا","ناحوم","حبقوق","صفنيا","حجي","زكريا","ملاخي",
 "متى","مرقس","لوقا","يوحنا","أعمال","رومية","١ كورنثوس","٢ كورنثوس","غلاطية","أفسس","فيلبي","كولوسي",
 "١ تسالونيكي","٢ تسالونيكي","١ تيموثاوس","٢ تيموثاوس","تيطس","فليمون","عبرانيين","يعقوب","١ بطرس","٢ بطرس",
 "١ يوحنا","٢ يوحنا","٣ يوحنا","يهوذا","رؤيا"]
ALIASES = {"المزامير":"مزمور","مزامير":"مزمور","الأمثال":"أمثال","النشيد":"نشيد الأنشاد","أخبار الأيام الأول":"١ أخبار","أخبار الأيام الثاني":"٢ أخبار"}
AD = str.maketrans('٠١٢٣٤٥٦٧٨٩', '0123456789')
names = sorted(NAMES + list(ALIASES), key=len, reverse=True)
REF = re.compile('(' + '|'.join(map(re.escape, names)) + r') ([٠-٩]+): ([٠-٩]+)')
QUOTE = re.compile(r'\s*«([^»]+)»')
DIA = re.compile(r'[ً-ٰٟـ]')
# tier: (eg min, eg max, sections eg, msa/en min words, msa/en max words, msa/en sections)
LIMITS = {'A': (200, 380, (4,), 150, 470, (4,)), 'B': (100, 215, (3, 4), 90, 240, (3, 4)),
          'C': (45, 150, (3, 4), 12, 70, (1, 2)), 'D': (30, 120, (3, 4), 12, 70, (1, 2)),
          'N': (22, 110, (3,), 10, 60, (1, 2))}

def book_no(n):
    n = ALIASES.get(n, n); return NAMES.index(n) + 1

def verse(b, c, v):
    return DB.execute("select id, text from verses where book=? and ch=? and v=?", (b, c, v)).fetchone()

def has(ds, vid):
    rows = DB.execute("select dstrong, dstrongs from owords where vd_verse=?", (vid,)).fetchall()
    return any(r[0] == ds or (r[1] and ds in re.findall(r'[HG]\d+[A-Z]?', r[1])) for r in rows)

def norm(s):
    s = DIA.sub('', s)
    s = re.sub('[أإآ]', 'ا', s).replace('ى', 'ي').replace('ة', 'ه')
    return re.sub(r'[^ء-ي ]', ' ', s).split()

PASS = re.compile(r'(?:^|[«\s])((?:و|ف|ب|هي|بي|ي|ت|بت|هت)?(?:ات|مت|يت|تت)[ء-ي]{2,})')
OK = set("اتولد اتكتب اتقال اتعمل اتكسر اتسرق اتجوز اتصلب يتكلم بيتكلم اتكلم يتكلمون بتتكلم متأكد متبادلة يتعلم بيتعلم متعلم اتنين اتناشر".split())

# everyday Egyptian verbs that start with ات/ت anyway (not passives): any form containing these stems is fine
OK_STEMS = 'تتناي تمرد تبرع توقع تمتع تحرج اتفاق متعدي تاجر امتنع يمتنع توه تمايل تضرع تظاهر تذلل تدخل تولى تكل اتكال اتسع تدفأ متمرد تنكر اتجاه توسل تمسك تكئ تزوج تخاصم تعامل تكرر ترك تعب تعرض تجعد تقال امتحن يمتحن متحن اتحط تحط اتشال تشال اتقال اتكتب تنبأ تفرج تحمل تعلق متأكد تقزز تكبر تغذى ترنح تأمل تراجع تجبر متكبر متراس تكسر تمشو تخب اتصال متان متياس متيا يتير تكاثر تجمد تمتم تحدى متنيا تريق تباهى اتهام اتباع تبع متناقض تحسر تخلى تهم توب متوشالح متثيا متفق متين يتيم ايتام أيتام ترجم متواضع تواضع تمنى تمني ترجى ترجو اترج تأثر اتأثر تحول اتحول اتلم تلم اتجمع تجمع اتكل تكلم اتعلم تعلم اتفق تفق اتصل تصل اتحرك تحرك اتغير تغير اتمشى تمشى اتعشى اتغدى اتأخر تأخر اتولد اتجوز تجوز اتخانق اتنين اتناشر'.split()

written = {}
for f in glob.glob(os.path.join(HERE, '..', '..', 'content', 'dictionary', '*', '*.json')):
    for w in json.load(open(f, encoding='utf-8')).get('words', []):
        written[w['dstrong']] = os.path.basename(f)

args = sys.argv[1:]
default_tier = 'A'
if '--tier' in args:
    i = args.index('--tier'); default_tier = args[i + 1]; del args[i:i + 2]
total = 0
for path in args:
    data = json.load(open(path, encoding='utf-8'))
    print('==', path, len(data))
    for e in data:
        ds = e['dstrong']; eg = e['entry']['eg']
        t = e.get('tier', default_tier)
        lim = LIMITS[t]
        probs = []
        if ds in written: probs.append(f'already written in {written[ds]}')
        for k in ('eg', 'msa', 'en'):
            n = e['entry'][k].count('**') // 2
            if n not in (lim[2] if k == 'eg' else lim[5]): probs.append(f'{k} sections={n}')
        words = len(eg.split())
        if not lim[0] <= words <= lim[1]: probs.append(f'eg words={words}')
        for k in ('msa', 'en'):
            n = len(e['entry'][k].split())
            if not lim[3] <= n <= lim[4]: probs.append(f'{k} words={n}')
        ex = eg.split('**في فاندايك:**')[0]
        for m in REF.finditer(ex):
            bk = book_no(m.group(1))
            if (ds[0] == 'H') == (bk >= 40):    # the other Testament (NT quoting the OT, or the reverse): a cross-reference
                continue
            r = verse(bk, int(m.group(2).translate(AD)), int(m.group(3).translate(AD)))
            if r is None: probs.append(f'bad ref {m.group(0)}'); continue
            if not has(ds, r[0]): probs.append(f'no {ds} in {m.group(0)}')
            q = QUOTE.match(ex, m.end())
            if q:
                vt = ' ' + ' '.join(norm(r[1])) + ' '
                for part in re.split(r'\.\.\.|…', q.group(1)):
                    p = ' '.join(norm(part))
                    if p and ' ' + p + ' ' not in vt: probs.append(f'quote not in {m.group(0)}: «{part.strip()[:40]}»')
        if t in ('C', 'D'):
            cnt = DB.execute('select count from lemmas where dstrong=?', (ds,)).fetchone()
            cnt = cnt[0] if cnt else None
            meaning = re.sub(r'«[^»]*»', ' ', re.split(r'\*\*الموضع|\*\*المواضع', eg)[0])   # the count claim lives in the Meaning section
            m = re.search(r'([٠-٩]+) مر(?:ات|تين)', meaning)
            said = 1 if 'مرة واحدة' in meaning else 2 if re.search(r'(?<![٠-٩] )مرتين', meaning) and not m else int(m.group(1).translate(AD)) if m else None
            if said is not None and cnt is not None and said != cnt: probs.append(f'count says {said}, DB {cnt}')
        leak = [w for w in (r'الباك\b', r'الداتا', r'\bpack\b', r'dataset', r'مشيت ورا', r'اللي قبلها', r'اللي قبله\b', r'اللي بعده\b', r'اللي بعدها', r'««')
                if any(re.search(w, e['entry'][k]) for k in ('eg', 'msa', 'en'))]
        if leak: probs.append('talks about the pack/itself: ' + ' '.join(leak))
        odd = set(re.findall(r'[\u0400-\u04ff\u0530-\u058f\u0e00-\u0e7f\u3040-\u30ff\u4e00-\u9fff\uac00-\ud7af]', ' '.join(e['entry'].values()) + ' '.join(e['gloss'].values())))
        if odd: probs.append('stray script: ' + ''.join(sorted(odd)))
        pas = [w for w in PASS.findall(re.sub(r'«[^»]*»', ' ', eg)) if w.lstrip('وفب') not in OK and w not in OK
               and not any(st in w for st in OK_STEMS)]
        if pas: probs.append('check: ' + ' '.join(pas))
        total += bool(probs)
        print(ds, t, words, '|', '; '.join(probs) if probs else 'ok')
print('entries with something to look at:', total)
