"""Build the private Jesuit translation package (jesuit.ketaby) that the app imports.

The package holds copyrighted text: it is built locally and handed to the owner only. Never commit it,
never commit the inputs (saved web pages, parsed JSON, footnote readings).

Usage:
  python3 build_pack.py <parsed.json from parse_site.py> <notes dir with *.jsonl> <ketaby.db> <out jesuit.ketaby>

Tables in the package:
  meta(key, value)
  jbooks(id, slug, name, short, testament, vd_book, chapters, notes)      id = Jesuit (Catholic) order 1..73
  jintro(book, seq, tag, text)                                          book introductions
  jitems(book, ch, seq, kind, lvl, v, sub, para, text)                  kind 'h' heading / 'v' verse; text has
                                                                        note markers as U+E000 n U+E001
  jnotes(book, ch, n, text)                                             footnotes, numbered per chapter
  jmargin(book, ch, v, sub, refs, links)                                margin cross-references; links = JSON [start, end, book, ch, v]
  jmap(vd_book, vd_ch, vd_v, book, ch, v, sub)                          which Jesuit verses sit under a Van Dyke verse
"""
import glob, json, os, re, sqlite3, sys, collections, datetime

ORDER = """gen ex lev num deut josh judg ruth 1sam 2sam 1kings 2kings 1chron 2chron ezra neh tob jth est 1macc 2macc
job ps prov eccles song wisdofsol sir isa jer lam bar ezek dan hos joel amos obad jonah mic nah hab zeph hag zech mal
matt mac luke john acts rom 1cor 2cor gal eph phil col 1thess 2thess 1tim 2tim titus philem heb james 1pet 2pet
1john 2john 3john jude rev""".split()
SHORT = """التكوين،الخروج،الأحبار،العدد،تثنية الاشتراع،يشوع،القضاة،راعوت،صموئيل الأول،صموئيل الثاني،الملوك الأول،الملوك الثاني،
الأخبار الأول،الأخبار الثاني،عزرا،نحميا،طوبيا،يهوديت،أستير،المكابيين الأول،المكابيين الثاني،أيوب،المزامير،الأمثال،الجامعة،
نشيد الأناشيد،الحكمة،يشوع بن سيراخ،أشعيا،إرميا،المراثي،باروك،حزقيال،دانيال،هوشع،يوئيل،عاموس،عوبديا،يونان،ميخا،ناحوم،
حبقوق،صفنيا،حجاي،زكريا،ملاخي،متى،مرقس،لوقا،يوحنا،أعمال الرسل،رومة،١ قورنتس،٢ قورنتس،غلاطية،أفسس،فيلبي،قولسي،
١ تسالونيقي،٢ تسالونيقي،١ طيموتاوس،٢ طيموتاوس،طيطس،فيلمون،العبرانيين،يعقوب،١ بطرس،٢ بطرس،١ يوحنا،٢ يوحنا،٣ يوحنا،
يهوذا،الرؤيا""".replace('\n', '').split('،')
VD_ORDER = """gen ex lev num deut josh judg ruth 1sam 2sam 1kings 2kings 1chron 2chron ezra neh est job ps prov eccles song isa
jer lam ezek dan hos joel amos obad jonah mic nah hab zeph hag zech mal matt mac luke john acts rom 1cor 2cor gal eph
phil col 1thess 2thess 1tim 2tim titus philem heb james 1pet 2pet 1john 2john 3john jude rev""".split()
DEUTERO = {'tob', 'jth', '1macc', '2macc', 'wisdofsol', 'sir', 'bar'}
assert len(ORDER) == 73 and len(SHORT) == 73 and len(VD_ORDER) == 66

# The edition's own book abbreviations (front matter table «الترتيب الأبجدي لأسفار الكتاب المقدس»).
ABBR = {
    'أح': 'lev', '١ اخ': '1chron', '٢ اخ': '2chron', 'ار': 'jer', 'اس': 'est', 'اش': 'isa', 'اف': 'eph', 'اي': 'job',
    'با': 'bar', '١ بط': '1pet', '٢ بط': '2pet', 'تث': 'deut', '١ تس': '1thess', '٢ تس': '2thess', 'تك': 'gen',
    'جا': 'eccles', 'حب': 'hab', 'حج': 'hag', 'حز': 'ezek', 'حك': 'wisdofsol', 'خر': 'ex', 'دا': 'dan', 'را': 'ruth',
    'رسل': 'acts', 'روم': 'rom', 'رؤ': 'rev', 'زك': 'zech', 'سي': 'sir', 'صف': 'zeph', '١ صم': '1sam', '٢ صم': '2sam',
    'طو': 'tob', 'طي': 'titus', '١ طيم': '1tim', '٢ طيم': '2tim', 'عا': 'amos', 'عب': 'heb', 'عد': 'num', 'عز': 'ezra',
    'عو': 'obad', 'غل': 'gal', 'ف': 'philem', 'فل': 'phil', 'قض': 'judg', '١ قور': '1cor', '٢ قور': '2cor', 'قول': 'col',
    'لو': 'luke', 'متى': 'matt', 'مثل': 'prov', 'مر': 'mac', 'مرا': 'lam', 'مز': 'ps', '١ مك': '1macc', '٢ مك': '2macc',
    '١ مل': '1kings', '٢ مل': '2kings', 'ملا': 'mal', 'مي': 'mic', 'نح': 'neh', 'نحو': 'nah', 'نش': 'song', 'هو': 'hos',
    'يش': 'josh', 'يع': 'james', 'يو': 'john', '١ يو': '1john', '٢ يو': '2john', '٣ يو': '3john', 'يوء': 'joel',
    'يون': 'jonah', 'يه': 'jth', 'يهو': 'jude',
}
# spelling variants seen in the scans / readings
ABBR.update({'اح': 'lev', 'أي': 'job', 'إر': 'jer', 'إش': 'isa', 'أف': 'eph', '١ أخ': '1chron', '٢ أخ': '2chron', 'سير': 'sir'})
AR_DIG = str.maketrans('٠١٢٣٤٥٦٧٨٩', '0123456789')
_ABBR_RE = '|'.join(sorted(map(re.escape, ABBR), key=len, reverse=True))
_REF = re.compile(r'(?:(?P<abbr>' + _ABBR_RE + r')\s+)?(?P<nums>[٠-٩]+(?:\s*/\s*[٠-٩]+)?(?:\s*[-–]\s*[٠-٩]*(?:/[٠-٩]+)?)?\s*[+ت]?)')


def ref_links(text, book_slug):
    """Spans of a margin reference group that can open a passage: [start, end, book_id, ch, v]."""
    out = []
    for part in re.finditer(r'[^؛]+', text):
        cur = book_slug                     # a reference without a book name is in the current book
        last_ch = None
        seg, base = part.group(0), part.start()
        pos = 0
        while True:
            m = _REF.search(seg, pos)
            if not m:
                break
            pos = m.end()
            if m.group('abbr'):
                cur = ABBR[m.group('abbr')]
            nums = m.group('nums')
            first = re.match(r'([٠-٩]+)(?:\s*/\s*([٠-٩]+))?', nums)
            a = int(first.group(1).translate(AR_DIG))
            if first.group(2):
                ch, v = a, int(first.group(2).translate(AR_DIG)); last_ch = ch
            elif last_ch is not None and not m.group('abbr'):
                ch, v = last_ch, a          # «يو ١/١-٣ و١٤»: a verse of the same chapter
            else:
                ch, v = a, 1                # «مز ٨»: a whole chapter
            end = m.start() + len(m.group(0).rstrip())
            if cur in ORDER:
                out.append([base + m.start(), base + end, ORDER.index(cur) + 1, ch, v])
    return out


DIA = re.compile('[ً-ْٰـۡ]')
MARK_L, MARK_R = '', ''


def norm_words(s):
    s = DIA.sub('', s)
    s = re.sub('[إأآٱ]', 'ا', s).replace('ى', 'ي').replace('ة', 'ه').replace('ؤ', 'و').replace('ئ', 'ي')
    out = set()
    for w in re.findall(r'[ء-ي]+', s):
        for p in ('وال', 'فال', 'بال', 'كال', 'لل', 'ال'):
            if w.startswith(p) and len(w) - len(p) >= 3:
                w = w[len(p):]; break
        else:
            if w[:1] in 'وفبل' and len(w) >= 5:
                w = w[1:]
        if len(w) >= 3:
            out.add(w)
    return out


def sim(a, b):
    if not a or not b:
        return 0.0
    return len(a & b) / (len(a) * len(b)) ** 0.5


def align(vd, js):
    """vd: [(ch, v, words)], js: [(ch, v, sub, words)]. Returns list of (vd_index or None, js_index).
    Global alignment where a Jesuit verse may be skipped (attached to its neighbour) and a Van Dyke
    verse may get nothing. Same chapter:verse gets a bonus so equal numbering wins ties."""
    n, m = len(vd), len(js)
    NEG = -1e9
    GAP_J, GAP_V = -0.12, -0.12
    score = [[NEG] * (m + 1) for _ in range(n + 1)]
    back = [[0] * (m + 1) for _ in range(n + 1)]
    score[0][0] = 0
    for i in range(n + 1):
        for j in range(m + 1):
            if i == 0 and j == 0:
                continue
            best, how = NEG, 0
            if i > 0 and j > 0:
                s = sim(vd[i - 1][2], js[j - 1][3]) + (0.25 if vd[i - 1][:2] == js[j - 1][:2] else 0)
                c = score[i - 1][j - 1] + s
                if c > best: best, how = c, 1
            if j > 0 and score[i][j - 1] + GAP_J > best:
                best, how = score[i][j - 1] + GAP_J, 2
            if i > 0 and score[i - 1][j] + GAP_V > best:
                best, how = score[i - 1][j] + GAP_V, 3
            score[i][j], back[i][j] = best, how
    pairs = []; i, j = n, m
    while i > 0 or j > 0:
        how = back[i][j]
        if how == 1: pairs.append((i - 1, j - 1)); i -= 1; j -= 1
        elif how == 2: pairs.append((None, j - 1)); j -= 1
        else: i -= 1
    pairs.reverse()
    # a skipped Jesuit verse goes under the Van Dyke verse of its neighbour (previous, else next)
    # (a psalm title that opens a new chapter goes with the first verse after it, not the last one before)
    out = []; last = None; last_j = None
    for k, (vi, ji) in enumerate(pairs):
        if vi is None:
            nxt = next((p for p, _ in pairs[k:] if p is not None), None)
            new_chapter = last_j is None or js[ji][0] != js[last_j][0]
            vi = nxt if (new_chapter and nxt is not None) or last is None else last
        else:
            last = vi; last_j = ji
        out.append((vi, ji))
    return out


def main(parsed, notes_dir, vd_db, out):
    J = json.load(open(parsed, encoding='utf-8'))
    J.pop('zech-2', None)
    # footnote readings
    notes, marks, margin = {}, collections.defaultdict(list), []
    for f in sorted(glob.glob(os.path.join(notes_dir, '*.jsonl'))):
        for line in open(f, encoding='utf-8'):
            if not line.strip():
                continue
            p = json.loads(line)
            b = p['book']
            for n in p.get('notes', []):
                k = (b, n['ch'], n['n'])
                notes[k] = (notes[k] + ' ' + n['text']) if n.get('cont') and k in notes else n['text']
            for m in p.get('markers', []):
                marks[(b, m['ch'])].append(m)
            for g in p.get('margin', []):
                v, sub = g.get('v'), g.get('sub', '')
                if v is None and g.get('heading'):
                    # parallels printed beside a section heading belong to the first verse under it
                    items = next((c['items'] for c in J[b]['chapters'] if c['n'] == g['ch']), [])
                    hk = next((k for k, x in enumerate(items) if x['k'] == 'h' and DIA.sub('', g['heading']) in DIA.sub('', x['text'])), None)
                    nxt = next((x for x in items[hk + 1:] if x['k'] == 'v'), None) if hk is not None else None
                    if nxt is None:
                        print('margin heading not found', b, g['ch'], g['heading']); continue
                    v, sub = nxt['v'], nxt.get('sub', '')
                margin.append((b, g['ch'], v, sub, g['refs']))

    if os.path.exists(out):
        os.remove(out)
    db = sqlite3.connect(out)
    db.executescript("""
      CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT);
      CREATE TABLE jbooks(id INTEGER PRIMARY KEY, slug TEXT, name TEXT, short TEXT, testament TEXT, vd_book INTEGER, chapters INTEGER, notes INTEGER);
      CREATE TABLE jintro(book INTEGER, seq INTEGER, tag TEXT, text TEXT);
      CREATE TABLE jitems(book INTEGER, ch INTEGER, seq INTEGER, kind TEXT, lvl TEXT, v INTEGER, sub TEXT, para INTEGER, text TEXT);
      CREATE TABLE jnotes(book INTEGER, ch INTEGER, n INTEGER, text TEXT);
      CREATE TABLE jmargin(book INTEGER, ch INTEGER, v INTEGER, sub TEXT, refs TEXT, links TEXT);
      CREATE TABLE jmap(vd_book INTEGER, vd_ch INTEGER, vd_v INTEGER, book INTEGER, ch INTEGER, v INTEGER, sub TEXT);
    """)
    vdb = sqlite3.connect(vd_db)
    placed = missed = 0
    stats = []
    for bid, slug in enumerate(ORDER, 1):
        b = J[slug]
        vd_book = VD_ORDER.index(slug) + 1 if slug in VD_ORDER else None
        testament = 'NT' if ORDER.index(slug) >= ORDER.index('matt') else 'OT'
        has_notes = sum(1 for k in notes if k[0] == slug)
        db.execute('INSERT INTO jbooks VALUES(?,?,?,?,?,?,?,?)', (bid, slug, b['title'], SHORT[bid - 1], testament, vd_book, len(b['chapters']), has_notes))
        for k, it in enumerate(b['intro']):
            if it['tag'] in ('h1',) and k == 0:
                continue
            if re.fullmatch(r'.*\s[٠-٩]+', it['text']) and len(it['text']) < 30:
                continue  # chapter index links at the end of the intro
            db.execute('INSERT INTO jintro VALUES(?,?,?,?)', (bid, k, it['tag'], it['text']))
        for c in b['chapters']:
            items = c['items']
            # place note markers
            for m in marks.get((slug, c['n']), []):
                tag = MARK_L + str(m['n']) + MARK_R
                if 'heading' in m:
                    tgt = next((x for x in items if x['k'] == 'h' and (DIA.sub('', m['heading']) in DIA.sub('', x['text']))), None)
                    if tgt is not None:
                        tgt['text'] += tag; placed += 1
                    else:
                        missed += 1; print('  heading marker not placed:', slug, c['n'], m)
                    continue
                tgt = next((x for x in items if x['k'] == 'v' and x['v'] == m['v'] and x.get('sub', '') == m.get('sub', '')), None)
                if tgt is not None and m['before'] not in tgt['text']:
                    # same letters, different diacritics or diacritic order: find it without them
                    inmark = set()
                    for mm in re.finditer(MARK_L + r'\d+' + MARK_R, tgt['text']):
                        inmark.update(range(mm.start(), mm.end()))
                    keep = [k for k, ch_ in enumerate(tgt['text']) if not DIA.match(ch_) and k not in inmark]
                    bare = ''.join(tgt['text'][k] for k in keep)
                    want = DIA.sub('', m['before'])
                    if want and bare.count(want) >= 1:
                        e = bare.index(want) + len(want) - 1
                        end = keep[e] + 1
                        while end < len(tgt['text']) and DIA.match(tgt['text'][end]):
                            end += 1
                        m = dict(m, before=tgt['text'][keep[bare.index(want)]:end], _end=end)
                if tgt is None or ('_end' not in m and m['before'] not in tgt['text']):
                    missed += 1; print('  marker not placed:', slug, c['n'], m)
                    continue
                pos = m['_end'] if '_end' in m else tgt['text'].index(m['before']) + len(m['before'])
                # a second marker with the same anchor goes after the next occurrence
                while tgt['text'][pos:pos + 1] == MARK_L:
                    nxt = tgt['text'].find(m['before'], pos)
                    if nxt < 0: break
                    pos = nxt + len(m['before'])
                tgt['text'] = tgt['text'][:pos] + tag + tgt['text'][pos:]
                placed += 1
            for s, x in enumerate(items):
                if x['k'] == 'h':
                    db.execute('INSERT INTO jitems VALUES(?,?,?,?,?,?,?,?,?)', (bid, c['n'], s, 'h', x['lvl'] + ('c' if x.get('center') else ''), None, None, 0, x['text']))
                else:
                    db.execute('INSERT INTO jitems VALUES(?,?,?,?,?,?,?,?,?)', (bid, c['n'], s, 'v', None, x['v'], x.get('sub', ''), 1 if x.get('p') else 0, x['text']))
        # verse map
        if vd_book:
            vd = [(ch, v, norm_words(t)) for ch, v, t in vdb.execute('SELECT ch, v, text FROM verses WHERE book=? ORDER BY ch, v', (vd_book,))]
            js = [(c['n'], x['v'], x.get('sub', ''), norm_words(x['text'])) for c in b['chapters'] for x in c['items'] if x['k'] == 'v']
            pairs = align(vd, js)
            same = 0
            for vi, ji in pairs:
                if vi is None:
                    continue
                db.execute('INSERT INTO jmap VALUES(?,?,?,?,?,?,?)', (vd_book, vd[vi][0], vd[vi][1], bid, js[ji][0], js[ji][1], js[ji][2]))
                same += vd[vi][:2] == js[ji][:2]
            stats.append((slug, len(js), same))
    for (b, ch, n), text in notes.items():
        db.execute('INSERT INTO jnotes VALUES(?,?,?,?)', (ORDER.index(b) + 1, ch, n, text))
    nlinks = 0
    verses = {(ORDER.index(slug) + 1, c['n'], x['v']) for slug in ORDER for c in J[slug]['chapters'] for x in c['items'] if x['k'] == 'v'}
    bad = []
    for b, ch, v, sub, refs in margin:
        links = ref_links(refs, b)
        bad += [(b, ch, v, refs[l[0]:l[1]]) for l in links if (l[2], l[3], l[4]) not in verses]
        links = [l for l in links if (l[2], l[3], l[4]) in verses]   # a misprinted reference stays as text
        nlinks += len(links)
        db.execute('INSERT INTO jmargin VALUES(?,?,?,?,?,?)', (ORDER.index(b) + 1, ch, v, sub, refs, json.dumps(links)))
    db.executescript("""
      CREATE INDEX ji ON jitems(book, ch, seq); CREATE INDEX jn ON jnotes(book, ch); CREATE INDEX jm ON jmargin(book, ch);
      CREATE INDEX jmv ON jmap(vd_book, vd_ch); CREATE INDEX jmj ON jmap(book, ch); CREATE INDEX jin ON jintro(book);
    """)
    for k, v in {'format': '1', 'kind': 'jesuit', 'name': 'الترجمة اليسوعية (الكاثوليكية)', 'built': datetime.date.today().isoformat(),
                 'notes': str(len(notes))}.items():
        db.execute('INSERT INTO meta VALUES(?,?)', (k, v))
    db.commit(); db.execute('VACUUM'); db.close()
    print(f'markers placed {placed}, missed {missed}; notes {len(notes)}; margin groups {len(margin)}, links {nlinks}')
    for x in bad:
        print('  reference not found, left as text:', *x)
    worst = sorted(stats, key=lambda s: s[2] / max(1, s[1]))[:12]
    print('verse map, same reference share (lowest):', ', '.join(f'{s}:{d}/{n}' for s, n, d in worst))
    print('size', os.path.getsize(out) // 1024, 'KB')


if __name__ == '__main__':
    main(*sys.argv[1:5])
