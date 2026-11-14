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
  everses(tr, verse_id, text, spans, plain)             English verse (tr = 'kjv' | 'bsb') on Van Dyke verse ids;
                                                        spans = "start,end,oword.oword;..." (character offsets
                                                        of the parts of `text` tied to original words)
  lex_entries(lex, n, label, page, y)                   HALOT/BDAG entry positions in the owner's PDF (1-based page, y from top)
  lex_links(lex, page, x0, y0, x1, y1, to_n)            cross-reference hot spots inside the PDFs
  meta(key, value)

Usage: python3 build_db.py out.db
"""
import glob, json, os, re, sqlite3, sys, unicodedata, collections, html
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from books import BOOKS, STEP2ID, OSIS2ID
from translit import hebrew_to_arabic, greek_academic, greek_modern
import english
import open_lexicons

HERE = os.path.dirname(os.path.abspath(__file__))
DL = os.path.join(HERE, 'downloads', 'STEPBible-Data')
ARA = os.path.join(DL, 'Tagged-Bibles', 'Arabic Bibles')
TA = os.path.join(DL, 'Translators Amalgamated OT+NT')
ENG = os.path.join(HERE, 'downloads', 'english')
IDX = os.path.join(HERE, '..', 'content', 'indexes')

REF = re.compile(r'((?:[1-3][A-Z][a-z]|[A-Z][a-z]{2}))\.(\d+)\.(\d+)(?:[({](\d+)\.(\d+)[)}])?#(\d+)=(\S*)')


def schema(db):
    db.executescript('''
    PRAGMA journal_mode=OFF; PRAGMA synchronous=OFF;
    CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT);
    CREATE TABLE books(id INTEGER PRIMARY KEY, code TEXT, osis TEXT, name_ar TEXT, testament TEXT, chapters INTEGER, name_en TEXT);
    CREATE TABLE verses(id INTEGER PRIMARY KEY, book INTEGER, ch INTEGER, v INTEGER, text TEXT, plain TEXT);
    CREATE TABLE cards(strong TEXT, kind TEXT, json TEXT);
    CREATE TABLE tokens(id INTEGER PRIMARY KEY, verse_id INTEGER, idx INTEGER, text TEXT, strongs TEXT);
    CREATE TABLE owords(id INTEGER PRIMARY KEY, lang TEXT, book INTEGER, ch INTEGER, v INTEGER, oc INTEGER, ov INTEGER,
        wno INTEGER, text TEXT, tr TEXT, tr2 TEXT, gloss TEXT, dstrong TEXT, dstrongs TEXT, grammar TEXT, editions TEXT,
        vd_verse INTEGER);
    CREATE TABLE links(token_id INTEGER, oword_id INTEGER);
    CREATE TABLE lexdefs(key TEXT, src TEXT, html TEXT);
    CREATE TABLE dict(dstrong TEXT PRIMARY KEY, json TEXT);
    CREATE TABLE rend(dstrong TEXT PRIMARY KEY, json TEXT);
    CREATE TABLE lex_entries(lex TEXT, n INTEGER, label TEXT, page INTEGER, y REAL, PRIMARY KEY(lex, n)) WITHOUT ROWID;
    CREATE TABLE lex_links(lex TEXT, page INTEGER, x0 REAL, y0 REAL, x1 REAL, y1 REAL, to_n INTEGER);
    CREATE TABLE everses(tr TEXT, verse_id INTEGER, text TEXT, spans TEXT, plain TEXT, PRIMARY KEY(tr, verse_id)) WITHOUT ROWID;
    CREATE TABLE lemmas(dstrong TEXT PRIMARY KEY, lang TEXT, lemma TEXT, tr TEXT, tr2 TEXT, gloss TEXT, count INTEGER,
        halot TEXT, halot_page INTEGER, halot_y REAL, bdag TEXT, bdag_page INTEGER, bdag_y REAL);
    ''')


def plain_ar(s):
    """Arabic without harakat, unified alef/ya/ta marbuta, for searching."""
    s = re.sub(r'[\u064b-\u065f\u0670\u0640]', '', s)
    s = re.sub('[أإآٱ]', 'ا', s).replace('ى', 'ي').replace('ة', 'ه')
    return re.sub(r'[^\u0621-\u064a ]', ' ', s)


def load_english(db):
    """KJV and BSB, each segment linked to the original words of the same Van Dyke verse."""
    vid = {(b, c, v): i for i, b, c, v in db.execute('SELECT id, book, ch, v FROM verses')}
    ow = collections.defaultdict(list)
    for row in db.execute('SELECT vd_verse, id, lang, dstrong, dstrongs, editions FROM owords '
                          'WHERE vd_verse IS NOT NULL ORDER BY id'):
        ow[row[0]].append(row[1:])
    stats = {}
    for tr, loader, path, ed in (('kjv', english.load_kjv, os.path.join(ENG, 'eng-kjv_usfm.zip'), 'K'),
                                 ('bsb', english.load_bsb, os.path.join(ENG, 'bsb_tables.tsv.gz'), 'N')):
        verses = loader(path)
        rows = []
        missing = 0
        seg_linked = seg_tagged = 0
        for key in sorted(verses):
            v = vid.get(key)
            if v is None:
                missing += 1
                continue
            segs = verses[key]
            links = english.link_verse(segs, ow.get(v, []), ed)
            pos = 0
            spans = []
            for (t, st), ids in zip(segs, links):
                if ids:
                    spans.append('%d,%d,%s' % (pos, pos + len(t), '.'.join(map(str, ids))))
                if st:
                    seg_tagged += 1
                    seg_linked += 1 if ids else 0
                pos += len(t)
            text = ''.join(t for t, _ in segs)
            rows.append((tr, v, text, ';'.join(spans), english.plain_en(text)))
        db.executemany('INSERT INTO everses VALUES (?,?,?,?,?)', rows)
        stats[tr] = {'verses': len(rows), 'not_in_van_dyke': missing, 'tagged_segments': seg_tagged,
                     'linked_segments': seg_linked}
    return stats


def load_lex_index(db):
    """Entry positions and cross-reference links from the committed page indexes (no lexicon text)."""
    idx = os.path.join(HERE, '..', 'content', 'indexes')
    info = {}
    for lex, fname, key, label in (('halot', 'halot_index.json', 'n', 'he'), ('bdag', 'bdag_index.json', 'id', 'lemma')):
        d = json.load(open(os.path.join(idx, fname), encoding='utf-8'))
        db.executemany('INSERT OR IGNORE INTO lex_entries VALUES (?,?,?,?,?)',
                       [(lex, e[key], e[label], e['page'], e['y']) for e in d['entries']])
        db.executemany('INSERT INTO lex_links VALUES (?,?,?,?,?,?,?)',
                       [(lex, l['p'], *l['r'], l['to']) for l in d.get('links', [])])
        info[lex] = d['file']
    return info


def load_dict(db):
    """Claude's dictionary entries (content/dictionary/*/*.json, lists under "words")."""
    n = 0
    for f in sorted(glob.glob(os.path.join(HERE, '..', 'content', 'dictionary', '*', '*.json'))):
        for w in json.load(open(f, encoding='utf-8')).get('words', []):
            db.execute('INSERT OR REPLACE INTO dict VALUES (?,?)', (w['dstrong'], json.dumps(w, ensure_ascii=False)))
            n += 1
    return n


def load_renderings(db):
    """For every lemma, the Arabic words Van Dyke uses for it (from the alignment), most frequent first.
    Counted on the search form (no harakat, unified letters) but shown as Van Dyke spells it."""
    c = collections.defaultdict(collections.Counter)
    shown = collections.defaultdict(collections.Counter)
    for ds, text in db.execute('SELECT o.dstrong, t.text FROM links k JOIN owords o ON o.id=k.oword_id '
                               'JOIN tokens t ON t.id=k.token_id WHERE o.dstrong IS NOT NULL'):
        w = plain_ar(text).strip()
        if w:
            key = re.sub(r'\s+', ' ', w)
            c[ds][key] += 1
            bare = re.sub(r'[\u064b-\u065f\u0670\u0640]', '', text)
            shown[key][re.sub(r'\s+', ' ', re.sub(r'[^\u0621-\u064a ]', ' ', bare)).strip()] += 1
    db.executemany('INSERT INTO rend VALUES (?,?)',
                   [(ds, json.dumps([(shown[k].most_common(1)[0][0], n) for k, n in cnt.most_common(12)],
                                    ensure_ascii=False)) for ds, cnt in c.items()])
    return len(c)


def load_cards(db):
    rows = []
    for f in glob.glob(os.path.join(HERE, '..', 'content', 'rare_words', '*', '*.json')):
        d = json.load(open(f, encoding='utf-8'))
        for w in d.get('words', []):
            rows.append((w['strong'], 'rare', json.dumps(w, ensure_ascii=False)))
    db.executemany('INSERT INTO cards VALUES (?,?,?)', rows)
    return len(rows)


HARAKA = '\u064b-\u0652\u0670'
_AT_START = re.compile(r'^([' + HARAKA + r']+)([\u0621-\u064a])')
_AFTER_SPACE = re.compile(r'( )([' + HARAKA + r']+)([\u0621-\u064a])')
_AFTER_PUNCT = re.compile(r'([\u0621-\u064a][' + HARAKA + r']*)([،؛.:؟!«»()\[\]]+)([' + HARAKA + r']+)')
_STRAY = re.compile(r'(?:^|[^\u0621-\u064a\u0640' + HARAKA + r'])[' + HARAKA + r']')
_DOUBLE = re.compile(r'([\u064b-\u0652])\1')
HARAKAT_LOG = []  # (ref, before, after, 'auto' | 'manual') for the build report


def fix_harakat(s, word_start=True):
    """AraSVD 7.0.1 has some harakat in the wrong place. A mark after a space belongs to the first
    letter of the next word (كَاهِنًا ِللهِ -> كَاهِنًا لِلهِ); a mark after punctuation that follows a
    letter belongs to that letter (الْحِمَار،ِ -> الْحِمَارِ، and و«َأَنَا -> وَ«أَنَا). word_start says whether
    the text starts a word (the previous text ended with a space), so a leading mark moves too."""
    if word_start:
        s = _AT_START.sub(r'\2\1', s)
    s = _AFTER_SPACE.sub(r'\1\3\2', s)
    return _AFTER_PUNCT.sub(r'\1\3\2', s)


def load_harakat_overrides():
    """Hand-checked fixes that the rules cannot decide (harakat_overrides.json): ref -> [(from, to)]."""
    d = json.load(open(os.path.join(HERE, 'harakat_overrides.json'), encoding='utf-8'))
    out = collections.defaultdict(list)
    for f in d['fixes']:
        out[f['ref']].append([f['from'], f['to']])
    return out


def load_verses(db):
    """Van Dyke verses from the tagged OSIS file -> verses, units, tokens."""
    f = sorted(glob.glob(os.path.join(ARA, 'AraSVD_sb-7*.xml')))[-1]
    t = open(f, encoding='utf-8').read()
    vid = {}
    tok_rows, verse_rows = [], []
    tid = 0
    overrides = load_harakat_overrides()
    unused = {(r, f) for r, fs in overrides.items() for f, _ in fs}
    stray = []
    for m in re.finditer(r"<verse osisID='([^']+)'>(.*?)</verse>", t, re.S):
        osis, body = m.group(1), m.group(2)
        b, c, v = osis.split('.')
        book = OSIS2ID[b]
        verse_id = len(verse_rows) + 1
        vid[(book, int(c), int(v))] = verse_id
        idx, plain = 0, []
        first_tok = len(tok_rows)
        for part in re.split(r'(<w [^>]*>.*?</w>)', body, flags=re.S):
            if not part:
                continue
            wm = re.match(r'<w lemma="([^"]*)">(.*?)</w>', part, re.S)
            if wm:
                txt = html.unescape(re.sub(r'<[^>]+>', '', wm.group(2)))
                txt = wasl(osis, txt, tok_rows, plain, first_tok)
                txt = fixed(osis, txt, not plain or plain[-1][-1:] in ' «(')
                strongs = ' '.join(s.replace('strong:', '') for s in wm.group(1).split())
                tid += 1
                tok_rows.append((tid, verse_id, idx, txt, strongs or '-'))
                plain.append(txt)
            else:
                txt = html.unescape(re.sub(r'<[^>]+>', '', part))
                if not txt:
                    continue
                txt = fixed(osis, txt, not plain or plain[-1][-1:] in ' «(')
                tid += 1
                tok_rows.append((tid, verse_id, idx, txt, None))
                plain.append(txt)
            idx += 1
        for frm, to in overrides.get(osis, []):
            for k in range(first_tok, len(tok_rows)):
                tk = tok_rows[k]
                if frm in tk[3]:
                    tok_rows[k] = (tk[0], tk[1], tk[2], tk[3].replace(frm, to, 1), tk[4])
                    plain[k - first_tok] = tok_rows[k][3]
                    HARAKAT_LOG.append((osis, frm, to, 'manual'))
                    unused.discard((osis, frm))
                    break
        vt = re.sub(r'\s+', ' ', ''.join(plain)).strip()
        if _STRAY.search(vt):
            stray.append((osis, vt))
        verse_rows.append((verse_id, book, int(c), int(v), vt, plain_ar(vt)))
    print(f'harakat: {sum(1 for x in HARAKAT_LOG if x[3] == "auto")} moved by rule, '
          f'{sum(1 for x in HARAKAT_LOG if x[3] == "manual")} fixed from harakat_overrides.json')
    for ref, a, b, how in HARAKAT_LOG:
        print(f'  {how:6} {ref:12} {a} -> {b}')
    if unused:
        raise SystemExit(f'harakat_overrides.json: not found in the text: {sorted(unused)}')
    if stray:
        raise SystemExit('harakat after a space or punctuation remain in %d verses, e.g. %s' % (len(stray), stray[:5]))
    db.executemany('INSERT INTO verses VALUES (?,?,?,?,?,?)', verse_rows)
    db.executemany('INSERT INTO tokens VALUES (?,?,?,?,?)', tok_rows)
    return vid


def wasl(ref, txt, tok_rows, plain, first_tok):
    """A mark after a space that sits before a hamzat-wasl alif (الْكَذِب ِالَّذِي) cannot go on the
    alif: it is the end vowel of the word before (الْكَذِبِ الَّذِي). Moves it to the end of the
    previous token of the same verse, which keeps every token boundary."""
    m = re.match('([' + HARAKA + ']+)ا', txt)
    if not m or not plain or plain[-1] != ' ':
        return txt
    for k in range(len(tok_rows) - 1, first_tok - 1, -1):
        prev = tok_rows[k][3]
        if prev.strip():
            if re.search(r'[\u0621-\u064a]$', prev):
                tok_rows[k] = (tok_rows[k][0], tok_rows[k][1], tok_rows[k][2], prev + m.group(1), tok_rows[k][4])
                plain[k - first_tok] = tok_rows[k][3]
                HARAKAT_LOG.append((ref, prev.split()[-1] + ' ' + txt.split()[0], tok_rows[k][3].split()[-1] + ' ' + txt[len(m.group(1)):].split()[0], 'auto'))
                return txt[len(m.group(1)):]
            break
    return txt


def fixed(ref, txt, word_start):
    """fix_harakat() on one token, logged; letters and token boundaries never change."""
    new = fix_harakat(txt, word_start)
    if len(_DOUBLE.findall(new)) > len(_DOUBLE.findall(txt)):
        new = _DOUBLE.sub(r'\1', new)  # the moved mark was already on that letter: a stray copy
    if new != txt:
        if re.sub('[' + HARAKA + ']', '', new) != re.sub('[' + HARAKA + ']', '', txt):
            raise SystemExit(f'fix_harakat changed letters in {ref}: {txt!r} -> {new!r}')
        HARAKAT_LOG.append((ref, txt.strip(), new.strip(), 'auto'))
    return new


def load_owords(db, vid):
    rows, key2id, lem = [], {}, {}
    count = collections.Counter()
    votes = collections.defaultdict(collections.Counter)
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
                ex = re.search(r'\{' + re.escape(ds) + r'=([^=]*)=([^}]*)\}', p[11])
                if ex:
                    gloss = ex.group(2).lstrip(': ').split('»')[0]
                    votes[ds][(lang, ex.group(1).split(',')[0].strip(), gloss)] += 1
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
                if '=' in dfg:
                    form, gloss = dfg.split('=', 1)
                    votes[ds][('grc', unicodedata.normalize('NFC', form.split(',')[0].strip()), gloss.strip())] += 1
    # the dictionary form most occurrences give (the first one is sometimes a related word, e.g. G1473)
    for ds, c in votes.items():
        lem[ds] = c.most_common(1)[0][0]
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
    ncards = load_cards(db)
    estats = load_english(db)
    lexinfo = load_lex_index(db)
    ndict = load_dict(db)
    nrend = load_renderings(db)
    db.executemany('INSERT INTO lexdefs VALUES (?,?,?)', open_lexicons.load_all(
        DL, os.path.join(HERE, 'downloads', 'HebrewLexicon'), os.path.join(HERE, 'downloads', 'ubs-open-license'),
        os.path.join(HERE, '..', 'content', 'lexicons', 'mm.json.gz'),
        db.execute("SELECT dstrong, lemma FROM lemmas WHERE dstrong LIKE 'G%'").fetchall()))
    chap = collections.Counter()
    for b, c in db.execute('SELECT book, MAX(ch) FROM verses GROUP BY book'):
        chap[b] = c
    en_names = [('Psalms' if n == 'Psalm' else n) for n in english.BSB_BOOKS]
    db.executemany('INSERT INTO books VALUES (?,?,?,?,?,?,?)',
                   [(i + 1, s, o, n, t, chap[i + 1], en_names[i]) for i, (s, o, n, t) in enumerate(BOOKS)])
    db.executescript('''
    CREATE INDEX i_verses ON verses(book, ch, v);
    CREATE INDEX i_lex_links ON lex_links(lex, page);
    CREATE INDEX i_lexdefs ON lexdefs(key);
    CREATE INDEX i_lex_pos ON lex_entries(lex, page, y);
    CREATE INDEX i_cards ON cards(strong);
    CREATE INDEX i_tokens ON tokens(verse_id);
    CREATE INDEX i_owords_v ON owords(vd_verse);
    CREATE INDEX i_owords_ds ON owords(dstrong);
    CREATE INDEX i_links_u ON links(token_id);
    CREATE INDEX i_links_o ON links(oword_id);
    ''')
    meta = {
        'built_from': 'STEPBible-Data 5de0a47 (AraSVD 7.0.1, TAHOT, TAGNT, Translation Tags)',
        'halot_sha256': hfile['sha256'], 'bdag_sha256': bfile['sha256'],
        'halot_pages': str(lexinfo['halot']['pages']), 'bdag_pages': str(lexinfo['bdag']['pages']),
        'licence': 'Texts and tags CC BY / CC BY-SA 4.0 (STEPBible.org, Arabic Bible Outreach Ministry); '
                   'KJV (eBible.org, public domain outside the UK); BSB and its translation tables (public domain)',
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
        'cards': q('SELECT COUNT(*) FROM cards'),
        'lex_entries': q('SELECT COUNT(*) FROM lex_entries'),
        'lex_links': q('SELECT COUNT(*) FROM lex_links'),
        'dict_entries': ndict, 'renderings': nrend,
        'harakat_moved_by_rule': sum(1 for x in HARAKAT_LOG if x[3] == 'auto'),
        'harakat_fixed_by_hand': sum(1 for x in HARAKAT_LOG if x[3] == 'manual'),
        **{'open_' + s_: n_ for s_, n_ in db.execute('SELECT src, COUNT(*) FROM lexdefs GROUP BY src')},
        'lemmas_with_halot': q("SELECT COUNT(*) FROM lemmas WHERE halot IS NOT NULL"),
        'lemmas_with_bdag': q("SELECT COUNT(*) FROM lemmas WHERE bdag IS NOT NULL"),
        **{'links_' + k: v for k, v in lstats.items()},
        **{tr + '_' + k: v for tr, d in estats.items() for k, v in d.items()},
    }
    db.execute('VACUUM')
    db.close()
    # the app reads this to show copy progress on first run
    with open(out + '.len', 'w') as f:
        f.write(str(os.path.getsize(out)))
    print(json.dumps(checks, ensure_ascii=False, indent=1))
    assert checks['verses'] == 31102, 'Van Dyke must have 31,102 verses'
    assert checks['kjv_verses'] > 31000 and checks['bsb_verses'] > 30900, 'English Bibles incomplete'
    return checks


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, 'build', 'ketaby.db'))
