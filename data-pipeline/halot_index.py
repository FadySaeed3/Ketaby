"""Build the HALOT page/position index from the user's own text-layer HALOT PDF.

Output (committed): content/indexes/halot_index.json
  - file fingerprint (sha256, page count)
  - entries: entry number, decoded headword, Aramaic flag, page, y (PDF points)
  - lemmas: OSHB lemma key -> ranked HALOT entry numbers (disambiguated by
    overlap between the lemma's Bible references and the references cited
    in each candidate entry)
  - links: cross-reference arrows inside HALOT -> target entry number,
    with the tap rectangle on the page

Nothing from the entry bodies is written out: only numbers, headwords and
coordinates. Entry text is read in memory to score references and then
discarded.

Usage:
  python3 halot_index.py HALOT.pdf path/to/morphhb/wlc path/to/HebrewStrong.xml out.json [path/to/TAHOT_dir]
"""
import hashlib, json, re, sys, glob, os, collections, unicodedata, difflib
import pymupdf
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from bw_decode import decode_hebrew, consonants

BOOKS = {  # OSIS -> HALOT abbreviation(s)
    'Gen': ['Gn'], 'Exod': ['Ex'], 'Lev': ['Lv'], 'Num': ['Nu'], 'Deut': ['Dt'], 'Josh': ['Jos'],
    'Judg': ['Ju'], 'Ruth': ['Ru'], '1Sam': ['1S'], '2Sam': ['2S'], '1Kgs': ['1K'], '2Kgs': ['2K'],
    '1Chr': ['1C'], '2Chr': ['2C'], 'Ezra': ['Ezr'], 'Neh': ['Neh'], 'Esth': ['Est'], 'Job': ['Jb', 'Job'],
    'Ps': ['Ps'], 'Prov': ['Pr'], 'Eccl': ['Qoh'], 'Song': ['Song'], 'Isa': ['Is'], 'Jer': ['Jr'],
    'Lam': ['Lam', 'La'], 'Ezek': ['Ezk'], 'Dan': ['Da'], 'Hos': ['Hos'], 'Joel': ['Jl'], 'Amos': ['Am'],
    'Obad': ['Ob'], 'Jonah': ['Jon'], 'Mic': ['Mi'], 'Nah': ['Nah'], 'Hab': ['Hab'], 'Zeph': ['Zeph'],
    'Hag': ['Hg'], 'Zech': ['Zech'], 'Mal': ['Mal'],
}
AB2OSIS = {a: o for o, abs_ in BOOKS.items() for a in abs_}
REF_RE = re.compile(r'(?<![A-Za-z])(' + '|'.join(sorted(map(re.escape, AB2OSIS), key=len, reverse=True)) +
                    r') (\d+):(\d+)((?:\s?[.,]\s?:?\d+(?:-\d+)?)*)')


def sha256(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()


def read_spans(pdf):
    doc = pymupdf.open(pdf)
    spans = []
    for pno in range(doc.page_count):
        d = doc[pno].get_text('dict')
        for b in d['blocks']:
            for l in b.get('lines', []):
                for s in l['spans']:
                    spans.append((pno + 1, s['bbox'], s['font'], s['size'], s['text']))
    return doc.page_count, spans


def find_headers(spans):
    heads = []
    i = 0
    while i < len(spans):
        p, bb, f, sz, t = spans[i]
        if f == 'TimesNewRoman,Bold' and re.fullmatch(r'\s*\d{1,5}\s*', t):
            parts, j = [], i + 1
            while j < len(spans) and spans[j][0] == p and abs(spans[j][1][1] - bb[1]) < 4 and spans[j][1][0] >= bb[0]:
                parts.append(spans[j]); j += 1
            if parts and parts[0][2] == 'Bwhebb' and any(q[2] == 'Bwhebb' and q[4].strip() for q in parts):
                bw = ''.join(q[4] for q in parts if q[2] == 'Bwhebb').strip()
                heads.append({'n': int(t), 'page': p, 'y': round(bb[1], 1), 'idx': i,
                              'he': decode_hebrew(bw),
                              'aram': any('Aramaic' in q[4] for q in parts)})
            i = j
        else:
            i += 1
    # keep the longest increasing chain of entry numbers (drops bold sense numbers)
    import bisect
    ns = [h['n'] for h in heads]
    ti, prev = [], [-1] * len(heads)
    for k, n in enumerate(ns):
        j = bisect.bisect_left([ns[t] for t in ti], n)
        if j > 0:
            prev[k] = ti[j - 1]
        if j == len(ti):
            ti.append(k)
        else:
            ti[j] = k
    chain, k = [], ti[-1]
    while k != -1:
        chain.append(heads[k]); k = prev[k]
    return chain[::-1]


def entry_refs(spans, a, b):
    """References cited in spans[a:b] as a set of (osis, ch, v)."""
    buf = []
    for p, bb, f, sz, t in spans[a:b]:
        if sz < 9 and re.fullmatch(r'\s*\d[\d.,\-–f ]*\s*', t):
            buf.append(':' + t.strip())
        else:
            buf.append(t)
    text = ''.join(buf)
    refs = set()
    for m in REF_RE.finditer(text):
        osis, ch = AB2OSIS[m.group(1)], int(m.group(2))
        refs.add((osis, ch, int(m.group(3))))
        for v in re.findall(r'[.,]\s?:?(\d+)', m.group(4) or ''):
            refs.add((osis, ch, int(v)))
    return refs


def oshb_lemmas(wlc_dir):
    refs = collections.defaultdict(set)
    for f in glob.glob(os.path.join(wlc_dir, '*.xml')):
        t = open(f, encoding='utf-8').read()
        for vm in re.finditer(r'<verse osisID="([^"]+)">(.*?)</verse>', t, re.S):
            b, c, v = vm.group(1).split('.')
            for lm in re.findall(r'<w [^>]*lemma="([^"]+)"', vm.group(2)):
                for part in lm.split('/'):
                    part = part.strip()
                    if re.match(r'^\d', part):
                        refs[part].add((b, int(c), int(v)))
    return refs


def strong_heads(xml):
    sx = open(xml, encoding='utf-8').read()
    out = {}
    for m in re.finditer(r'<entry id="H(\d+)">(.*?)</entry>', sx, re.S):
        lang = re.search(r'xml:lang="(\w+)"', m.group(2))
        out[m.group(1)] = (re.search(r'<w [^>]*>([^<]+)</w>', m.group(2)).group(1),
                           lang.group(1) if lang else 'heb')
    return out


def match_lemmas(lemrefs, headinfo, heads, spans, by_cons, by_loose, by_stem, by_letter, loose, stem):
    """lemrefs: key -> set of (osis, ch, v); headinfo: key -> (pointed head, is_aramaic) or None."""
    lemmas, stats, low, pos = {}, collections.Counter(), [], {}
    num_of = {h['n']: h for h in heads}
    for key, refs in lemrefs.items():
        hi = headinfo(key)
        if hi is None:
            stats['no_head'] += 1; continue
        head, aram = hi
        hc = consonants(head)
        if not hc:
            stats['no_head'] += 1; continue
        def pick(cs):
            cs = [c for c in cs if c['aram'] == aram] or cs
            return sorted(((len(refs & c['refs']), c['n']) for c in {c['n']: c for c in cs}.values()), reverse=True)
        scored, how = pick(by_cons.get(hc, [])), 'exact'
        if not scored or scored[0][0] == 0:
            lo = pick(by_loose.get(loose(hc), []))
            if lo and lo[0][0] > 0:
                scored, how = lo, 'loose'
        if not scored or scored[0][0] == 0:
            st = pick(by_stem.get(stem(hc), []))
            if st and st[0][0] > 0:
                scored, how = st, 'stem'
        if not scored or scored[0][0] == 0:
            pool = [c for c in by_letter.get(hc[:1], [])
                    if difflib.SequenceMatcher(None, hc, consonants(c['he'])).ratio() >= 0.6]
            best = max(pool, key=lambda c: len(refs & c['refs']), default=None)
            if best is not None and len(refs & best['refs']) >= max(1, min(3, len(refs))):
                scored, how = [(len(refs & best['refs']), best['n'])], 'refs_only'
        if not scored:
            stats['unmatched'] += 1; continue
        best = scored[0][0]
        if best > 0:
            top = [n for s_, n in scored if s_ == best]
            stats[how + ('_by_refs' if len(top) == 1 else '_tie')] += 1
        else:
            top = [n for s_, n in scored]
            stats[how + '_no_ref_overlap'] += 1
            low.append(key)
        lemmas[key] = top
        if how != 'exact' and len(top) == 1:
            h = num_of[top[0]]
            for p_, bb_, f_, sz_, t_ in spans[h['idx']:h['end']]:
                if f_ == 'Bwhebb' and consonants(decode_hebrew(t_)) == hc:
                    if (p_, bb_[1]) != (h['page'], h['y']):
                        pos[key] = [p_, round(bb_[1], 1)]
                    break
    return lemmas, stats, low, pos


def line_start_fallback(trefs, theads, lemmas_d, heads, spans):
    """Headwords the entry detector missed (mostly compound names such as Kiriath-jearim, whose words come
    out reversed from the visual-order font): look for the word(s) at the start of a line, in the part of
    the alphabet where the lemma belongs. Stores (entry number, [page, y]) for the caller."""
    import bisect
    lines = collections.defaultdict(list)
    for p, bb, f, sz, t in spans:
        lines[(p, round(bb[1]))].append((bb[0], bb[1], f, t))
    starts = collections.defaultdict(list)
    for (p, _), ss in lines.items():
        ss.sort()
        if ss[0][0] > 110:
            continue
        words = [consonants(decode_hebrew(t)) for x, y, f, t in ss if f == 'Bwhebb' and t.strip()][:4]
        words = [w for w in words if w]
        for k in range(1, len(words) + 1):
            for key in {''.join(words[:k]), ''.join(reversed(words[:k]))}:
                starts[key].append((p, round(ss[0][1], 1)))
    keys = [(h['page'], h['y']) for h in heads]
    found = 0
    for key in trefs:
        if key in lemmas_d or key not in theads:
            continue
        head, aram = theads[key]
        if not re.search(r'[\s\u05be]', head.strip()):
            continue   # single words found this way are too often mentions inside other entries
        target = consonants(head)
        cands = starts.get(target)
        if not target or not cands:
            continue
        # where entries with the same first letters sit (robust to the few mis-decoded headwords)
        seq = [h for h in heads if h['aram'] == aram]
        inside = []
        for L in range(min(len(target), 4), 0, -1):
            pages = sorted(h['page'] for h in seq if consonants(h['he']).startswith(target[:L]))
            if len(pages) < 3:
                continue
            lo, hi = pages[len(pages) // 10] - 1, pages[(len(pages) * 9) // 10] + 1
            inside = [c for c in cands if lo <= c[0] <= hi]
            if inside:
                break
        if not inside:
            continue
        p, y = inside[0]
        n = heads[max(bisect.bisect_right(keys, (p, y + 0.5)) - 1, 0)]['n']
        lemmas_d[key] = (n, [p, y])
        found += 1
    return found


def tahot_lemmas(tahot_dir):
    """STEPBible TAHOT: extended Strong's (main lemma) -> refs (Hebrew numbering), head, aramaic flag."""
    from books import STEP2OSIS
    refs, heads = collections.defaultdict(set), {}
    for f in glob.glob(os.path.join(tahot_dir, 'TAHOT*.txt')):
        for line in open(f, encoding='utf-8'):
            m = re.match(r'((?:[1-3][A-Z][a-z]|[A-Z][a-z]{2}))\.(\d+)\.(\d+)(?:\((\d+)\.(\d+)\))?#\d+=\S*\t', line)
            if not m:
                continue
            parts = line.rstrip('\n').split('\t')
            if len(parts) < 12:
                continue
            ch, v = (m.group(4), m.group(5)) if m.group(4) else (m.group(2), m.group(3))
            osis = STEP2OSIS.get(m.group(1))
            main = re.search(r'\{(H\d+[A-Za-z]?)\}', parts[4])
            if not main or not osis:
                continue
            key = main.group(1)
            refs[key].add((osis, int(ch), int(v)))
            if key not in heads:
                ex = re.search(r'\{' + re.escape(key) + r'=([^=]+)=', parts[11])
                heads[key] = ((ex.group(1).split(',')[0].strip() if ex else ''), parts[5].startswith('A'))
    return refs, heads


def main(pdf, wlc_dir, strong_xml, out_path, tahot_dir=None):
    pages, spans = read_spans(pdf)
    heads = find_headers(spans)
    # entry bodies -> references (in memory only)
    for k, h in enumerate(heads):
        end = heads[k + 1]['idx'] if k + 1 < len(heads) else len(spans)
        h['refs'] = entry_refs(spans, h['idx'], end)
        h['end'] = end
    by_cons = collections.defaultdict(list)
    by_loose = collections.defaultdict(list)   # without matres lectionis (ו/י)
    by_letter = collections.defaultdict(list)
    loose = lambda c: c.replace('ו', '').replace('י', '')
    stem = lambda c: loose(re.sub(r'(ות|ימ|ים|ה|ת)$', '', c)) if len(c) > 3 else loose(c)  # consonants() writes final mem as מ
    by_stem = collections.defaultdict(list)
    for h in heads:
        forms = set(re.split(r' / | ', h['he'])) | set(h['he'].split(' / '))
        # compound names come out of the visual-order font with their words reversed
        forms |= {' '.join(reversed(alt.split(' '))) for alt in h['he'].split(' / ') if ' ' in alt}
        for form in forms:
            c = consonants(form)
            if c:
                by_cons[c].append(h)
                by_loose[loose(c)].append(h)
                by_stem[stem(c)].append(h)
        c0 = consonants(h['he'])[:1]
        if c0:
            by_letter[c0].append(h)
    lemrefs = oshb_lemmas(wlc_dir)
    sh = strong_heads(strong_xml)
    def oshb_head(key):
        num = re.match(r'\d+', key).group(0)
        if num not in sh:
            return None
        return sh[num][0], sh[num][1] == 'arc'
    lemmas, stats, low, pos = match_lemmas(lemrefs, oshb_head, heads, spans, by_cons, by_loose, by_stem, by_letter, loose, stem)
    lemmas_d, stats_d, low_d, pos_d = {}, {}, [], {}
    if tahot_dir:
        trefs, theads = tahot_lemmas(tahot_dir)
        lemmas_d, stats_d, low_d, pos_d = match_lemmas(trefs, lambda k: theads.get(k), heads, spans,
                                                       by_cons, by_loose, by_stem, by_letter, loose, stem)
    if tahot_dir:
        found = line_start_fallback(trefs, theads, lemmas_d, heads, spans)
        stats_d['line_start'] = found
        stats_d['unmatched'] = stats_d.get('unmatched', 0) - found
        for k in list(lemmas_d):
            if isinstance(lemmas_d[k], tuple):
                lemmas_d[k], pos_d[k] = [lemmas_d[k][0]], lemmas_d[k][1]
    ov_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'halot_overrides.json')
    if os.path.exists(ov_path):
        for k, v in json.load(open(ov_path, encoding='utf-8')).items():
            if not k.startswith('_'):
                if k.startswith('H'):
                    lemmas_d[k] = v; pos_d.pop(k, None)
                    if k in low_d:
                        low_d.remove(k)
                    stats['manual'] += 1
                    continue
                lemmas[k] = v; pos.pop(k, None)
                if k in low:
                    low.remove(k)
                stats['manual'] += 1
    # cross-reference arrows -> link rectangles
    import bisect
    keys = [(h['page'], h['y']) for h in heads]
    links = []
    for i, (p, bb, f, sz, t) in enumerate(spans):
        if f in ('BwLexa', 'TimesNewRoman') and 'ï' in t:
            j = i + 1
            while j < len(spans) and spans[j][2] != 'Bwhebb' and j - i < 3 and spans[j][4].strip() in ('', 'I', 'II', 'III', 'IV', 'V'):
                j += 1
            if j < len(spans) and spans[j][2] == 'Bwhebb' and spans[j][0] == p:
                target = decode_hebrew(spans[j][4])
                c = consonants(target.split(' ')[0])
                cand = by_cons.get(c)
                if cand:
                    # nearest entry in document order wins (same letter section, closest number)
                    k = bisect.bisect_right(keys, (p, bb[1])) - 1
                    here = heads[k]['n'] if k >= 0 else 0
                    tgt = min(cand, key=lambda h: abs(h['n'] - here))['n']
                    x0, y0, x1, y1 = bb
                    ex = spans[j][1]
                    links.append({'p': p, 'r': [round(min(x0, ex[0]), 1), round(min(y0, ex[1]), 1),
                                                round(max(x1, ex[2]), 1), round(max(y1, ex[3]), 1)], 'to': tgt})
    out = {
        'source': 'HALOT (text-layer PDF supplied by the owner)',
        'file': {'sha256': sha256(pdf), 'pages': pages},
        'note': 'Numbers, headwords and coordinates only. No entry text.',
        'entries': [{'n': h['n'], 'he': h['he'], 'aram': h['aram'], 'page': h['page'], 'y': h['y']} for h in heads],
        'lemmas': lemmas,
        'lemma_pos': pos,
        'lemmas_dstrong': lemmas_d,
        'lemma_pos_dstrong': pos_d,
        'low_confidence_dstrong': sorted(low_d),
        'low_confidence': sorted(low),
        'links': links,
        'stats': dict(stats, entries=len(heads), lemmas_total=len(lemrefs), links=len(links)),
        'stats_dstrong': dict(stats_d),
    }
    with open(out_path, 'w', encoding='utf-8') as f:
        json.dump(out, f, ensure_ascii=False, separators=(',', ':'))
    print(json.dumps(out['stats'], ensure_ascii=False))


if __name__ == '__main__':
    main(*sys.argv[1:6])
