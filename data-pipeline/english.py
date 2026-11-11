"""English layers: KJV (eBible USFM with Strong's) and BSB (Berean translation tables).

Both are public domain. Each English verse becomes a list of segments whose
concatenation is the verse text; a segment that translates an original word
carries the Strong's number(s) used to link it to `owords` of the same verse.

Van Dyke and both English Bibles use English versification, so verses are
matched by (book, chapter, verse).
"""
import csv
import gzip
import os
import re
import zipfile

USFM_BOOKS = ['GEN', 'EXO', 'LEV', 'NUM', 'DEU', 'JOS', 'JDG', 'RUT', '1SA', '2SA', '1KI', '2KI', '1CH', '2CH',
              'EZR', 'NEH', 'EST', 'JOB', 'PSA', 'PRO', 'ECC', 'SNG', 'ISA', 'JER', 'LAM', 'EZK', 'DAN', 'HOS',
              'JOL', 'AMO', 'OBA', 'JON', 'MIC', 'NAM', 'HAB', 'ZEP', 'HAG', 'ZEC', 'MAL', 'MAT', 'MRK', 'LUK',
              'JHN', 'ACT', 'ROM', '1CO', '2CO', 'GAL', 'EPH', 'PHP', 'COL', '1TH', '2TH', '1TI', '2TI', 'TIT',
              'PHM', 'HEB', 'JAS', '1PE', '2PE', '1JN', '2JN', '3JN', 'JUD', 'REV']
USFM2ID = {c: i + 1 for i, c in enumerate(USFM_BOOKS)}

BSB_BOOKS = ['Genesis', 'Exodus', 'Leviticus', 'Numbers', 'Deuteronomy', 'Joshua', 'Judges', 'Ruth', '1 Samuel',
             '2 Samuel', '1 Kings', '2 Kings', '1 Chronicles', '2 Chronicles', 'Ezra', 'Nehemiah', 'Esther', 'Job',
             'Psalm', 'Proverbs', 'Ecclesiastes', 'Song of Solomon', 'Isaiah', 'Jeremiah', 'Lamentations',
             'Ezekiel', 'Daniel', 'Hosea', 'Joel', 'Amos', 'Obadiah', 'Jonah', 'Micah', 'Nahum', 'Habakkuk',
             'Zephaniah', 'Haggai', 'Zechariah', 'Malachi', 'Matthew', 'Mark', 'Luke', 'John', 'Acts', 'Romans',
             '1 Corinthians', '2 Corinthians', 'Galatians', 'Ephesians', 'Philippians', 'Colossians',
             '1 Thessalonians', '2 Thessalonians', '1 Timothy', '2 Timothy', 'Titus', 'Philemon', 'Hebrews',
             'James', '1 Peter', '2 Peter', '1 John', '2 John', '3 John', 'Jude', 'Revelation']
BSB2ID = {n: i + 1 for i, n in enumerate(BSB_BOOKS)}

STRONG_RE = re.compile(r'([HG])0*(\d+)')


def norm_strong(s):
    """'H0430' / 'H430G' / 430 + lang -> 'H430'."""
    m = STRONG_RE.match(s)
    return m.group(1) + m.group(2) if m else None


# ------------------------------------------------------------------ KJV
W_RE = re.compile(r'\\\+?w ([^|\\]*)\|strong="([^"]*)"\\\+?w\*')
FOOT_RE = re.compile(r'\\f .*?\\f\*|\\x .*?\\x\*')
MARK_RE = re.compile(r'\\\+?[a-z]+[0-9]?\*|\\\+?[a-z]+[0-9]? ?')


def _kjv_segments(raw):
    """USFM verse body -> [(text, [strongs] or None)]."""
    raw = FOOT_RE.sub('', raw).replace('¶', '')
    segs = []
    pos = 0
    for m in W_RE.finditer(raw):
        if m.start() > pos:
            segs.append((raw[pos:m.start()], None))
        strongs = [norm_strong(s) for s in m.group(2).split() if norm_strong(s)]
        segs.append((m.group(1), strongs or None))
        pos = m.end()
    segs.append((raw[pos:], None))
    out = []
    for text, st in segs:
        text = MARK_RE.sub('', text) if st is None else text
        if not text:
            continue
        if st is None and out and out[-1][1] is None:
            out[-1] = (out[-1][0] + text, None)
        else:
            out.append((text, st))
    # tidy whitespace at the ends and inside plain runs
    out = [(re.sub(r'\s+', ' ', t), s) for t, s in out]
    if out:
        out[0] = (out[0][0].lstrip(), out[0][1])
        out[-1] = (out[-1][0].rstrip(), out[-1][1])
    return [(t, s) for t, s in out if t]


def load_kjv(zip_path):
    """-> {(book, ch, v): segments}"""
    verses = {}
    with zipfile.ZipFile(zip_path) as z:
        for name in z.namelist():
            m = re.search(r'\d+-([1-3A-Z]{3})', os.path.basename(name))
            if not m or m.group(1) not in USFM2ID:
                continue
            book = USFM2ID[m.group(1)]
            text = z.read(name).decode('utf-8-sig')
            ch = 0
            title = ''
            cur = None
            buf = []

            def flush():
                if cur is not None:
                    body = ' '.join(buf)
                    verses[(book, ch, cur)] = _kjv_segments(body)

            for line in text.splitlines():
                mc = re.match(r'\\c (\d+)', line)
                if mc:
                    flush(); cur = None; buf = []
                    ch = int(mc.group(1)); title = ''
                    continue
                if line.startswith('\\d '):     # psalm title: Van Dyke keeps it in verse 1
                    title = line[3:]
                    continue
                parts = re.split(r'\\v (\d+) ', line)
                if len(parts) == 1:
                    if cur is not None and not re.match(r'\\(id|h|toc\d|mt\d?|s\d?|ms\d?|ide|imt\d?|ip|is\d?)\b', line):
                        buf.append(re.sub(r'^\\(p|q\d?|b|m|pi\d?|nb)\s*', '', line))
                    continue
                if cur is not None:
                    buf.append(re.sub(r'^\\(p|q\d?|b|m|pi\d?|nb)\s*', '', parts[0]))
                for i in range(1, len(parts), 2):
                    flush()
                    cur = int(parts[i])
                    buf = [parts[i + 1]]
                    if cur == 1 and title:
                        buf.insert(0, title)
                        title = ''
            flush()
    return verses


# ------------------------------------------------------------------ BSB
TAG_RE = re.compile(r'<span class=\|reftext\|>.*?</span>|<[^>]*>')
SKIP = {'', '-', '. . .', 'vvv'}


def _clean(s):
    s = TAG_RE.sub('', s)
    s = s.replace('[“]', '“').replace('[‘]', '‘').replace('[', '').replace(']', '').replace('{', '').replace('}', '')
    s = re.sub(r'\bvvv\b', '', s)
    return re.sub(r'\s+', ' ', s).strip()


def load_bsb(tsv_gz_path):
    """-> {(book, ch, v): segments} built from the translation tables in BSB order."""
    rows_by_verse = {}
    key = None
    with gzip.open(tsv_gz_path, 'rt', encoding='utf-8-sig') as f:
        r = csv.reader(f, delimiter='\t', quoting=csv.QUOTE_NONE)
        next(r)
        for x in r:
            vid = x[12].strip()
            if vid:
                name, cv = vid.rsplit(' ', 1)
                c, v = cv.split(':')
                key = (BSB2ID[name], int(c), int(v))
            if key is None:
                continue
            eng = x[18].strip()
            if eng in SKIP and not x[17].strip() and not x[19].strip() and not x[20].strip():
                continue
            lang = x[4]
            num = x[10].strip() if lang == 'Hebrew' else x[11].strip()
            strong = (('H' if lang in ('Hebrew', 'Aramaic') else 'G') + str(int(num))) if num.isdigit() else None
            rows_by_verse.setdefault(key, []).append((int(x[2]), _clean(x[17]), eng, strong, _clean(x[19]), _clean(x[20])))
    verses = {}
    for key, rows in rows_by_verse.items():
        rows.sort()
        segs = []
        for _, begq, eng, strong, pnc, endq in rows:
            word = '' if eng in SKIP else _clean(eng)
            if begq:
                segs.append([(' ' if segs else '') + begq, None])
            if word:
                lead = ' ' if segs and not segs[-1][0].endswith(('“', '‘', '(')) else ''
                segs.append([lead, None])
                segs.append([word, [strong] if strong else None])
            tail = pnc + endq
            if tail:
                segs.append([tail, None])
        out = []
        for t, s in segs:
            if not t:
                continue
            if s is None and out and out[-1][1] is None:
                out[-1][0] += t
            else:
                out.append([t, s])
        if out and out[0][1] is None:
            out[0][0] = out[0][0].lstrip()
            if not out[0][0]:
                out = out[1:]
        verses[key] = [(t, s) for t, s in out]
    return verses


# ------------------------------------------------------------------ linking
def link_verse(segments, owords, editions_pref):
    """Match English segments to original words of the same verse.

    segments: [(text, [strongs] or None)]
    owords: [(id, lang, dstrong, dstrongs, editions)] in original order
    editions_pref: letter the edition must carry for Greek ('K' for KJV, 'N' for BSB)
    -> list parallel to segments of [oword ids]
    """
    pref = [o for o in owords if o[1] != 'grc' or (o[4] and editions_pref in o[4].upper())]
    main = {}
    anyk = {}
    for pool, dst in ((pref, main), (owords, anyk)):
        for o in pool:
            ms = norm_strong(o[2] or '')
            if ms:
                dst.setdefault(ms, []).append(o[0])
    used = {}
    out = []
    for text, strongs in segments:
        ids = []
        for s in strongs or []:
            cands = main.get(s) or anyk.get(s)
            if not cands:
                # a prefix or suffix lemma (e.g. Hebrew conjunction) listed in dstrongs
                cands = [o[0] for o in owords if o[3] and re.search(r'\b' + s[0] + '0*' + s[1:] + r'[A-Z]?\b', o[3])]
            if not cands:
                continue
            k = used.get(s, 0)
            ids.append(cands[min(k, len(cands) - 1)])
            used[s] = k + 1
        out.append(ids)
    return out


def plain_en(s):
    return re.sub(r'[^a-z0-9 ]+', ' ', s.lower()).strip()
