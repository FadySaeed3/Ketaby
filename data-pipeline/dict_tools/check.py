"""Review helper for agent-written entries: structure, length, Egyptian passive scan, and
whether each verse cited in the eg examples section actually carries the entry's dstrong."""
import json, re, sqlite3, sys

DB = sqlite3.connect(__import__('os').path.join(__import__('os').path.dirname(__import__('os').path.abspath(__file__)), '..', 'build', 'ketaby.db'))
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

def book_no(n):
    n = ALIASES.get(n, n); return NAMES.index(n) + 1

def has(ds, b, c, v):
    vid = DB.execute("select id from verses where book=? and ch=? and v=?", (b, c, v)).fetchone()
    if not vid: return None
    rows = DB.execute("select dstrong, dstrongs from owords where vd_verse=?", (vid[0],)).fetchall()
    return any(r[0] == ds or (r[1] and ds in r[1].split()) for r in rows)

PASS = re.compile(r'(?:^|[«\s])((?:و|ف|ب|هي|بي|ي|ت|بت|هت)?(?:ات|مت|يت|تت)[ء-ي]{2,})')
OK = set("اتولد اتكتب اتقال اتعمل اتكسر اتسرق اتجوز اتصلب يتكلم بيتكلم اتكلم يتكلمون بتتكلم متأكد متبادلة يتعلم بيتعلم متعلم".split())

for path in sys.argv[1:]:
    data = json.load(open(path))
    print('==', path, len(data))
    for e in data:
        ds = e['dstrong']; eg = e['entry']['eg']
        probs = []
        for k in ('eg', 'msa', 'en'):
            if e['entry'][k].count('**') != 8: probs.append(f'{k} sections={e["entry"][k].count("**")//2}')
        words = len(eg.split())
        if not 200 <= words <= 380: probs.append(f'eg words={words}')
        ex = eg.split('**في فاندايك:**')[0]
        for m in REF.finditer(ex):
            r = has(ds, book_no(m.group(1)), int(m.group(2).translate(AD)), int(m.group(3).translate(AD)))
            if r is False: probs.append(f'no {ds} in {m.group(0)}')
            if r is None: probs.append(f'bad ref {m.group(0)}')
        pas = [w for w in PASS.findall(eg) if w.lstrip('وفب') not in OK and w not in OK]
        if pas: probs.append('check: ' + ' '.join(pas))
        print(ds, words, '|', '; '.join(probs) if probs else 'ok')
