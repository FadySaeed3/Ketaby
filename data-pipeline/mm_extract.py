"""Moulton & Milligan, The Vocabulary of the Greek Testament (1930, public domain), from the
Internet Archive scan "vocabularyofgree00mouluoft" (University of Toronto copy, OCR text layer).

Run once by hand (needs PyMuPDF), not in CI:
    python3 mm_extract.py path/to/vocabularyofgree00mouluoft.pdf ../content/lexicons/mm.json.gz build/ketaby.db

It finds headwords by their larger type at the column margin, lines them up with the NT lemma
list in alphabetical order (so OCR slips like "καοδία" still land on καρδία), and writes
[{"h": headword as printed, "k": normalized NT lemma key, "p": scan page, "t": entry text}].
build_db.py maps "k" to dStrong numbers.
"""
import collections
import gzip
import json
import re
import statistics
import sys
import unicodedata

GR = 'Ͱ-Ͽἀ-῿'
SHORT = re.compile(rf'^[\[(*“"]?[{GR}][{GR}᾿’ʼ\-]*([ ,]+[{GR}][{GR}᾿’ʼ\-]*){{0,3}}[.,:]?\s*$')
HEAD = re.compile(rf'^[\[(*“"]?([{GR}][{GR}᾿’ʼ\-]*)')


LATIN = re.compile(r'^([A-Za-z][A-Za-z01\-]{1,20})[.,]?\s*$')
# bold Greek headwords that the OCR read as Latin letters
CONF = {  # Latin letter -> the Greek letters it usually stands for in this scan
    'a': 'α', 'b': 'βθ', 'c': 'ζσ', 'd': 'δ', 'e': 'ε', 'f': 'φβ', 'g': 'γ', 'h': 'ηλ', 'i': 'ι', 'j': 'ι',
    'k': 'κ', 'l': 'λζι', 'm': 'μ', 'n': 'νη', 'o': 'οσρθω', 'p': 'ρβ', 'q': 'θ', 'r': 'ρυ', 's': 'σ', 't': 'τι',
    'u': 'υμ', 'v': 'νυ', 'w': 'ω', 'x': 'χκ', 'y': 'γυη', 'z': 'ζ',
    'A': 'αλδ', 'B': 'β', 'C': 'ζσ', 'D': 'δ', 'E': 'ε', 'F': 'φ', 'G': 'γα', 'H': 'ηκ', 'I': 'ι', 'K': 'κ',
    'L': 'λσ', 'M': 'μ', 'N': 'ν', 'O': 'οθσ', 'P': 'ρβ', 'Q': 'θ', 'R': 'ρ', 'S': 'σ', 'T': 'τ', 'U': 'υ',
    'V': 'ν', 'W': 'ω', 'X': 'χκ', 'Y': 'υ', 'Z': 'ζ', '0': 'οθ', '1': 'ι',
}


def lev_latin(lat, g):
    """Edit distance where a Latin letter matches any Greek letter it is often misread from."""
    prev = list(range(len(g) + 1))
    for i, ca in enumerate(lat, 1):
        opts = CONF.get(ca, '')
        cur = [i]
        for j, cb in enumerate(g, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (cb not in opts)))
        prev = cur
    return prev[-1]


def norm(s):
    s = unicodedata.normalize('NFD', s)
    s = ''.join(c for c in s if not unicodedata.combining(c))
    s = s.lower().replace('ς', 'σ').replace('ϲ', 'σ')
    return re.sub(r'[^α-ω]', '', s)


def lev(a, b, cap=3):
    if abs(len(a) - len(b)) > cap:
        return cap + 1
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


EXTRA = {  # NT lemma key -> headword M&M files it under
    'ειδω': ['οιδα'], 'επω': ['ειπον'], 'φαγειν': ['εσθιω'], 'εαυτου': ['εαυτου'], 'ουτω': ['ουτωσ'],
    'αλληλων': ['αλληλουσ'], 'αιτιαμα': ['αιτιωμα'], 'μεγασ': ['μειζων'], 'ουδεισ': ['ουθεισ'],
    'μηδεισ': ['μηθεισ'], 'ησσων': ['ηττων'],
}


def variants(k):
    out = [k]
    m = re.match(r'(συ|ε|παλι|συνε)([μγλρσ])(.*)', k)   # WH spell compounds unassimilated: συνπ-, ενκ-
    if m and m.group(3)[:1] and m.group(3)[:1] not in 'αεηιουω':
        out.append(m.group(1) + 'ν' + m.group(3))
    if k.startswith('συσ'):
        out.append('συν' + k[3:])
    if k.endswith('ω'):
        out.append(k[:-1] + 'ομαι')
    if k.endswith('ομαι'):
        out.append(k[:-4] + 'ω')
    if k.endswith('μι'):
        out.append(k[:-2] + 'μαι')
    if k.endswith('σ') and len(k) > 3:
        out.append(k[:-1])
    out += EXTRA.get(k, [])
    more = []
    for v in out:
        if v.endswith('ω'):
            more.append(v[:-1] + 'ομαι')
        if v.endswith('ομαι'):
            more.append(v[:-4] + 'ω')
    return list(dict.fromkeys(out + more))


def lines_of(pdf):
    if pdf.endswith('.json'):      # a cached dump of the same lines
        yield from json.load(open(pdf, encoding='utf-8'))
        return
    import pymupdf
    doc = pymupdf.open(pdf)
    for pi, page in enumerate(doc):
        for b in page.get_text('dict')['blocks']:
            for ln in b.get('lines', []):
                t = ''.join(s['text'] for s in ln['spans'])
                if t.strip():
                    x0, y0, x1, y1 = ln['bbox']
                    yield pi + 1, x0, y0, x1, max(s['size'] for s in ln['spans']), t


def columns(lines):
    pages = collections.defaultdict(list)
    for pg, x0, y0, x1, sz, t in lines:
        pages[pg].append((x0, y0, x1, sz, t))
    for pg in sorted(pages):
        ls = pages[pg]
        body = [sz for x0, y0, x1, sz, t in ls if y0 > 38 and len(t) > 30]
        med = statistics.median(body) if body else 7.3
        xs = collections.Counter(round(x0) for x0, y0, x1, sz, t in ls if 150 < x0 < 330 and len(t) > 30)
        good = [x for x, n in xs.items() if n >= max(3, max(xs.values()) * 0.3)] if xs else []
        split = (min(good) - 12) if good else 248
        for col in (0, 1):
            cl = sorted((l for l in ls if (l[0] < split) == (col == 0) and l[1] > 38),
                        key=lambda l: (round(l[1] / 3), l[0]))
            rows = []
            for l in cl:
                if rows and abs(rows[-1][1] - l[1]) < 3:
                    r = rows[-1]
                    rows[-1] = (r[0], r[1], l[2], r[3], r[4] + ' ' + l[4].strip())
                else:
                    rows.append((l[0], l[1], l[2], l[3], l[4].strip()))
            if rows:
                margin = statistics.median(sorted(r[0] for r in rows)[:max(1, len(rows) // 3)])
                yield pg, med, margin, rows


FIX = [  # frequent OCR slips in the Latin reference apparatus
    (r'\bSy//', 'Syll'), (r'\bSyi//', 'Syll'), (r'\bSyl/', 'Syll'), (r'\bPro/eg\.', 'Proleg.'),
    (r'\bOG/S\b', 'OGIS'), (r'\b7/6\.', 'ib.'), (r'\bzd\.', 'ib.'), (r'\b2b\.', 'ib.'), (r'\b76\. ', 'ib. '),
    (r'\bKow[#ħ]', 'Κοινή'), (r'\bkal\b', 'καὶ'), (r'\bKTA\.', 'κτλ.'), (r'\bKrA\.', 'κτλ.'),
    (r'\ba\.p\.', 'A.D.'), (r'\bA\.p\.', 'A.D.'), (r'\bA\.IJ\.', 'A.D.'), (r'\b8\.6\.', 'B.C.'), (r'\bB\.c\.', 'B.C.'),
    (r'\bpp- ', 'pp. '), (r'\bp- ', 'p. '), (r'Ρ\. (\d)', r'p. \1'),
]


def clean(text):
    for a, b in FIX:
        text = re.sub(a, b, text)
    return text


def join_rows(rows):
    out = ''
    for t in rows:
        t = t.strip()
        if not t:
            continue
        if out.endswith('-') and t[:1].islower():
            out = out[:-1] + t
        elif out:
            out += ' ' + t
        else:
            out = t
    return re.sub(r'\s+', ' ', out)


def main(pdf, dest, lemma_file):
    """lemma_file: a built ketaby.db (its Greek lemma list)."""
    import sqlite3
    db = sqlite3.connect(lemma_file)
    canon = {}
    real = {norm(l) for (l,) in db.execute("SELECT lemma FROM lemmas WHERE dstrong LIKE 'G%'")} - {''}
    for k in real:
        canon[k] = k
    for k in sorted(real):
        for v in variants(k):
            canon.setdefault(v, k)
    nt = sorted(canon)
    ntset = set(nt)
    flat = []          # (page, text, headword candidate, strong signal)
    for pg, med, margin, rows in columns(lines_of(pdf)):
        gaps = [rows[j][1] - rows[j - 1][1] for j in range(1, len(rows))]
        step = statistics.median(gaps) if gaps else 9.0
        for j, (x0, y0, x1, sz, t) in enumerate(rows):
            if t.startswith('|'):            # a column rule read as a bar
                t = t.lstrip('| ')
                x0 = max(x0, margin)
            m = HEAD.match(t)
            head, strong = None, False
            lat = LATIN.match(unicodedata.normalize('NFD', t).encode('ascii', 'ignore').decode()
                              if re.fullmatch(r'[A-Za-z\u00c0-\u017f01\-.,\s]+', t) else '')
            if lat and abs(x0 - margin) < 9:
                gap = (y0 - rows[j - 1][1]) / step if j else None
                if gap is not None and gap > 1.3 and sz / med >= 1.05:
                    flat.append((pg, t, '~' + lat.group(1), True))
                    continue
            if m and abs(x0 - margin) < 9 and len(norm(m.group(1))) >= 1:
                gap = (y0 - rows[j - 1][1]) / step if j else None
                ratio = sz / med
                strong = (ratio >= 1.2 or (gap is not None and gap > 1.2 and ratio >= 1.06)
                          or (gap is not None and gap > 1.5) or (gap is None and ratio >= 1.1))
                nxt = rows[j + 1] if j + 1 < len(rows) else None
                short = SHORT.match(t) is not None
                weak = short and nxt is not None and nxt[0] > margin + 4
                if strong or weak:
                    head = m.group(1)
            flat.append((pg, t, head, strong))
    # align candidate headwords with the NT list (alphabetical; keep only an increasing run of exact hits)
    import bisect
    keys = [None] * len(flat)
    idx = [i for i, f in enumerate(flat) if f[2]]
    ex = [(i, norm(flat[i][2])) for i in idx if not flat[i][2].startswith('~') and norm(flat[i][2]) in ntset]
    # longest non-decreasing subsequence by key
    tails, tails_i, prev = [], [], [None] * len(ex)
    for n, (i, k) in enumerate(ex):
        p = bisect.bisect_right(tails, k)
        if p == len(tails):
            tails.append(k); tails_i.append(n)
        else:
            tails[p] = k; tails_i[p] = n
        prev[n] = tails_i[p - 1] if p else None
    keep, n = [], (tails_i[-1] if tails_i else None)
    while n is not None:
        keep.append(ex[n]); n = prev[n]
    exact = dict(keep)
    anchors = sorted(exact)          # the ordered spine used to place fuzzy matches
    for i, k in ex:                  # a clearly marked heading counts even if the book's order differs a little
        if flat[i][3] and i not in exact:
            p = bisect.bisect_left(anchors, i)
            near = [exact[anchors[q]] for q in (p - 1, p) if 0 <= q < len(anchors)]
            if any(n[:3] == k[:3] for n in near):
                exact[i] = k
    for i in idx:
        if i in exact:
            keys[i] = exact[i]
            continue
        k = bisect.bisect_left(anchors, i)
        lo = exact[anchors[k - 1]] if k > 0 else ''
        hi = exact[anchors[k]] if k < len(anchors) else '\uffff'
        a, b = bisect.bisect_right(nt, lo), bisect.bisect_left(nt, hi)
        h = flat[i][2]
        if h.startswith('~'):
            w = h[1:].replace('-', '')
            best = min(((lev_latin(w, c), c) for c in nt[a:b]), default=(9, None))
            lim = 0 if len(w) <= 5 else (1 if len(w) <= 9 else 2)
        else:
            w = norm(h)
            if len(w) < 3 or not flat[i][3]:
                continue
            best = min(((lev(w, c, 4), c) for c in nt[a:b]), default=(9, None))
            lim = 1 if len(w) <= 5 else 2
        if best[1] and best[0] <= lim:
            keys[i] = best[1]
            exact[i] = best[1]
            anchors = sorted(exact)
    # cut entries
    out = []
    cur = None
    strong_of = [f[3] for f in flat]
    for i, (pg, t, head, _) in enumerate(flat):
        if keys[i] and cur and canon[keys[i]] == cur['k']:
            cur['rows'].append(t)          # a new paragraph of the same entry
        elif keys[i]:
            if cur:
                out.append(cur)
            cur = {'h': None if head.startswith('~') else head, 'k': canon[keys[i]], 'p': pg, 'rows': [t]}
        elif head and strong_of[i] and (head.startswith('~') or SHORT.match(t)):
            if cur:                        # a heading we could not place: end the entry before it
                out.append(cur)
            cur = None
        elif cur:
            cur['rows'].append(t)
    if cur:
        out.append(cur)
    for e in out:
        e['t'] = clean(join_rows(e.pop('rows')))
    with gzip.open(dest, 'wt', encoding='utf-8') as f:
        json.dump(out, f, ensure_ascii=False, separators=(',', ':'))
    got = {e['k'] for e in out}
    print(f'{len(out)} entries, {len(got)} of {len(real)} NT lemmas')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2], sys.argv[3])
