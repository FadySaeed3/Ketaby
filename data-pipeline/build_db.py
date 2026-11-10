"""Build ketaby.db (SQLite) from the pinned open sources.

Tables
  books(id, code, osis, name_ar, testament, chapters)
  verses(id, book, ch, v, text)                         Van Dyke text and numbering
  tokens(id, verse_id, idx, text, strongs)              display order; strongs NULL = punctuation/space,
                                                        otherwise an Arabic word/phrase unit (tagged)
  owords(id, lang, book, ch, v, oc, ov, wno, text, tr, tr2, gloss, dstrong, dstrongs, grammar, editions, vd_verse)
                                                        tr = Arabic-script reading (Greek: academic), tr2 = Greek modern
                                                        original words (TAHOT/TAGNT), oc/ov = original numbering
  links(token_id, oword_id)                             Arabic unit -> original word(s)
  lemmas(dstrong, lang, lemma, tr, tr2, gloss, count, halot, halot_page, halot_y, bdag, bdag_page, bdag_y)
  meta(key, value)

Usage: python3 build_db.py out.db
"""
import glob, json, os, re, sqlite3, sys, unicodedata, collections, html
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from books import BOOKS, STEP2ID, OSIS2ID
from translit import hebrew_to_arabic, greek_academic, greek_modern

HERE = os.path.dirname(os.path.abspath(__file__))
DL = os.path.join(HERE, 'downloads', 'STEPBible-Data')
ARA = os.path.join(DL, 'Tagged-Bibles', 'Arabic Bibles')
TA = os.path.join(DL, 'Translators Amalgamated OT+NT')
IDX = os.path.join(HERE, '..', 'content', 'indexes')

REF = re.compile(r'((?:[1-3][A-Z][a-z]|[A-Z][a-z]{2}))\.(\d+)\.(\d+)(?:[({](\d+)\.(\d+)[)}])?#(\d+)=(\S*)')


def schema(db):
    db.executescript('''
    PRAGMA journal_mode=OFF; PRAGMA synchronous=OFF;
    CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT);
    CREATE TABLE books(id INTEGER PRIMARY KEY, code TEXT, osis TEXT, name_ar TEXT, testament TEXT, chapters INTEGER);
    CREATE TABLE verses(id INTEGER PRIMARY KEY, book INTEGER, ch INTEGER, v INTEGER, text TEXT);
    CREATE TABLE tokens(id INTEGER PRIMARY KEY, verse_id INTEGER, idx INTEGER, text TEXT, strongs TEXT);
    CREATE TABLE owords(id INTEGER PRIMARY KEY, lang TEXT, book INTEGER, ch INTEGER, v INTEGER, oc INTEGER, ov INTEGER,
        wno INTEGER, text TEXT, tr TEXT, tr2 TEXT, gloss TEXT, dstrong TEXT, dstrongs TEXT, grammar TEXT, editions TEXT,
        vd_verse INTEGER);
    CREATE TABLE links(token_id INTEGER, oword_id INTEGER);
    CREATE TABLE lemmas(dstrong TEXT PRIMARY KEY, lang TEXT, lemma TEXT, tr TEXT, tr2 TEXT, gloss TEXT, count INTEGER,
        halot TEXT, halot_page INTEGER, halot_y REAL, bdag TEXT, bdag_page INTEGER, bdag_y REAL);
    ''')


def load_verses(db):
    """Van Dyke verses from the tagged OSIS file -> verses, units, tokens."""
    f = sorted(glob.glob(os.path.join(ARA, 'AraSVD_sb-7*.xml')))[-1]
    t = open(f, encoding='utf-8').read()
    vid = {}
    tok_rows, verse_rows = [], []
    tid = 0
    for m in re.finditer(r"<verse osisID='([^']+)'>(.*?)</verse>", t, re.S):
        osis, body = m.group(1), m.group(2)
        b, c, v = osis.split('.')
        book = OSIS2ID[b]
        verse_id = len(verse_rows) + 1
        vid[(book, int(c), int(v))] = verse_id
        idx, plain = 0, []
        for part in re.split(r'(<w [^>]*>.*?</w>)', body, flags=re.S):
            if not part:
                continue
            wm = re.match(r'<w lemma="([^"]*)">(.*?)</w>', part, re.S)
            if wm:
                txt = html.unescape(re.sub(r'<[^>]+>', '', wm.group(2)))
                strongs = ' '.join(s.replace('strong:', '') for s in wm.group(1).split())
                tid += 1
                tok_rows.append((tid, verse_id, idx, txt, strongs or '-'))
                plain.append(txt)
            else:
                txt = html.unescape(re.sub(r'<[^>]+>', '', part))
                if not txt:
                    continue
                tid += 1
                tok_rows.append((tid, verse_id, idx, txt, None))
                plain.append(txt)
            idx += 1
        verse_rows.append((verse_id, book, int(c), int(v), re.sub(r'\s+', ' ', ''.join(plain)).strip()))
    db.executemany('INSERT INTO verses VALUES (?,?,?,?,?)', verse_rows)
    db.executemany('INSERT INTO tokens VALUES (?,?,?,?,?)', tok_rows)
    return vid


def load_owords(db, vid):
    rows, key2id, lem = [], {}, {}
    count = collections.Counter()
    oid = 0
    # Hebrew / Aramaic
    for f in sorted(glob.glob(os.path.join(TA, 'TAHOT*.txt'))):
        for line in open(f, encoding='utf-8'):
            m = REF.match(line)
            if not m:
                continue
            p = line.rstrip('\n').split('\t')
            if len(p) < 12:
                continue
            book = STEP2ID[m.group(1)]
            ch, v = int(m.group(2)), int(m.group(3))
            oc, ov = (int(m.group(4)), int(m.group(5))) if m.group(4) else (ch, v)
            wno = int(m.group(6))
            text = p[1].replace('/', '').replace('\\', '')
            main = re.search(r'\{(H\d+[A-Za-z]?)\}', p[4])
            ds = main.group(1) if main else ''
            lang = 'arc' if p[5].startswith('A') else 'heb'
            oid += 1
            rows.append((oid, lang, book, ch, v, oc, ov, wno, text, hebrew_to_arabic(text), None, p[3], ds, p[4], p[5], m.group(7),
                         vid.get((book, ch, v))))
            key2id[('H', book, oc, ov, wno)] = oid
            if ds:
                count[ds] += 1
                if ds not in lem:
                    ex = re.search(r'\{' + re.escape(ds) + r'=([^=]*)=([^}]*)\}', p[11])
                    if ex:
                        gloss = ex.group(2).lstrip(': ').split('»')[0]
                        lem[ds] = (lang, ex.group(1).split(',')[0].strip(), gloss)
    # Greek
    for f in sorted(glob.glob(os.path.join(TA, 'TAGNT*.txt'))):
        for line in open(f, encoding='utf-8'):
            m = REF.match(line)
            if not m:
                continue
            p = line.rstrip('\n').split('\t')
            if len(p) < 6:
                continue
            book = STEP2ID[m.group(1)]
            ch, v = int(m.group(2)), int(m.group(3))
            oc, ov = (int(m.group(4)), int(m.group(5))) if m.group(4) else (ch, v)
            wno = int(m.group(6))
            gm = re.match(r'(.*?)\s*\((.*)\)\s*$', p[1])
            text, translit = (gm.group(1), gm.group(2)) if gm else (p[1], '')
            ds_g = p[3].split('+')
            ds = ds_g[0].split('=')[0].strip()
            grammar = '+'.join(x.split('=')[1] if '=' in x else '' for x in ds_g)
            dfg = p[4].split('+')[0]
            oid += 1
            text = unicodedata.normalize('NFC', text)
            rows.append((oid, 'grc', book, ch, v, oc, ov, wno, text, greek_academic(text), greek_modern(text), p[2],
                         ds, p[3], grammar, m.group(7), vid.get((book, ch, v))))
            key2id[('G', book, ch, v, wno)] = oid
            if ds:
                count[ds] += 1
                if ds not in lem and '=' in dfg:
                    form, gloss = dfg.split('=', 1)
                    lem[ds] = ('grc', unicodedata.normalize('NFC', form.split(',')[0].strip()), gloss.strip())
    db.executemany('INSERT INTO owords VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)', rows)
    return key2id, lem, count


def load_links(db, vid, key2id):
    """Translation Tags: Arabic unit n of a verse -> original word numbers."""
    units = collections.defaultdict(list)
    for uid, verse_id in db.execute('SELECT id, verse_id FROM tokens WHERE strongs IS NOT NULL ORDER BY verse_id, idx'):
        units[verse_id].append(uid)
    rows, stats = [], collections.Counter()
    files = glob.glob(os.path.join(ARA, 'Translation Tags Individual Books', '*.txt'))
    for f in files:
        cur, k = None, 0
        for line in open(f, encoding='utf-8'):
            line = line.rstrip('\n')
            m = re.fullmatch(r'((?:[1-3][A-Z][a-z]|[A-Z][a-z]{2}))\.(\d+)\.(\d+)', line)
            if m:
                cur = (STEP2ID.get(m.group(1)), int(m.group(2)), int(m.group(3))); k = 0
                continue
            if not cur or not line.startswith('#'):
                continue
            p = line.split('\t')
            if len(p) < 9:
                continue
            verse_id = vid.get(cur)
            ul = units.get(verse_id, [])
            if k >= len(ul):
                stats['unit_overflow'] += 1; k += 1; continue
            uid = ul[k]; k += 1
            lang = 'G' if p[2].lstrip('{').startswith('G') else 'H'
            for ref in p[8].split(';'):
                ref = ref.strip()
                rm = re.match(r'(?:\((\d+)\.(\d+)\))?#(\d+)', ref)
                if not rm:
                    continue
                oc, ov = (int(rm.group(1)), int(rm.group(2))) if rm.group(1) else (cur[1], cur[2])
                oid = key2id.get((lang, cur[0], oc, ov, int(rm.group(3))))
                if oid:
                    rows.append((uid, oid)); stats['linked'] += 1
                else:
                    stats['missing_oword'] += 1
    db.executemany('INSERT INTO links VALUES (?,?)', rows)
    return stats


def load_lemmas(db, lem, count):
    hal = json.load(open(os.path.join(IDX, 'halot_index.json'), encoding='utf-8'))
    bd = json.load(open(os.path.join(IDX, 'bdag_index.json'), encoding='utf-8'))
    hE = {e['n']: e for e in hal['entries']}
    bE = {e['id']: e for e in bd['entries']}
    rows = []
    for ds, (lang, form, gloss) in lem.items():
        h = hal.get('lemmas_dstrong', {}).get(ds) if lang != 'grc' else None
        b = bd.get('lemmas_dstrong', {}).get(ds) if lang == 'grc' else None
        hp = hal.get('lemma_pos_dstrong', {}).get(ds) or ([hE[h[0]]['page'], hE[h[0]]['y']] if h else [None, None])
        bp = [bE[b[0]]['page'], bE[b[0]]['y']] if b else [None, None]
        if lang == 'grc':
            tr, tr2 = greek_academic(form), greek_modern(form)
        else:
            tr, tr2 = (hebrew_to_arabic(form) if re.search(r'[\u05d0-\u05ea]', form) else ''), None
        rows.append((ds, lang, form, tr, tr2, gloss, count[ds], json.dumps(h) if h else None, hp[0], hp[1],
                     json.dumps(b) if b else None, bp[0], bp[1]))
    db.executemany('INSERT INTO lemmas VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)', rows)
    return hal['file'], bd['file']


def main(out):
    if os.path.exists(out):
        os.remove(out)
    db = sqlite3.connect(out)
    schema(db)
    vid = load_verses(db)
    key2id, lem, count = load_owords(db, vid)
    lstats = load_links(db, vid, key2id)
    hfile, bfile = load_lemmas(db, lem, count)
    chap = collections.Counter()
    for b, c in db.execute('SELECT book, MAX(ch) FROM verses GROUP BY book'):
        chap[b] = c
    db.executemany('INSERT INTO books VALUES (?,?,?,?,?,?)',
                   [(i + 1, s, o, n, t, chap[i + 1]) for i, (s, o, n, t) in enumerate(BOOKS)])
    db.executescript('''
    CREATE INDEX i_verses ON verses(book, ch, v);
    CREATE INDEX i_tokens ON tokens(verse_id);
    CREATE INDEX i_owords_v ON owords(vd_verse);
    CREATE INDEX i_owords_ds ON owords(dstrong);
    CREATE INDEX i_links_u ON links(token_id);
    CREATE INDEX i_links_o ON links(oword_id);
    ''')
    meta = {
        'built_from': 'STEPBible-Data 5de0a47 (AraSVD 7.0.1, TAHOT, TAGNT, Translation Tags)',
        'halot_sha256': hfile['sha256'], 'bdag_sha256': bfile['sha256'],
        'licence': 'Texts and tags CC BY / CC BY-SA 4.0 (STEPBible.org, Arabic Bible Outreach Ministry)',
    }
    db.executemany('INSERT INTO meta VALUES (?,?)', meta.items())
    db.commit()
    # ---- checks ----
    q = lambda s: db.execute(s).fetchone()[0]
    checks = {
        'verses': q('SELECT COUNT(*) FROM verses'),
        'units': q('SELECT COUNT(*) FROM tokens WHERE strongs IS NOT NULL'),
        'owords_heb': q("SELECT COUNT(*) FROM owords WHERE lang!='grc'"),
        'owords_grc': q("SELECT COUNT(*) FROM owords WHERE lang='grc'"),
        'owords_without_vd_verse': q('SELECT COUNT(*) FROM owords WHERE vd_verse IS NULL'),
        'units_linked': q('SELECT COUNT(DISTINCT token_id) FROM links'),
        'lemmas': q('SELECT COUNT(*) FROM lemmas'),
        'lemmas_with_halot': q("SELECT COUNT(*) FROM lemmas WHERE halot IS NOT NULL"),
        'lemmas_with_bdag': q("SELECT COUNT(*) FROM lemmas WHERE bdag IS NOT NULL"),
        **{'links_' + k: v for k, v in lstats.items()},
    }
    db.execute('VACUUM')
    db.close()
    print(json.dumps(checks, ensure_ascii=False, indent=1))
    assert checks['verses'] == 31102, 'Van Dyke must have 31,102 verses'
    return checks


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, 'build', 'ketaby.db'))
