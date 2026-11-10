"""Build the BDAG page/position index from the user's own BDAG PDF.

Output (committed): content/indexes/bdag_index.json
  - file fingerprint (sha256, page count)
  - entries: lemma (as printed), page, y (PDF points)
  - lemmas: MorphGNT lemma -> entry ids
  - links: "s. <lemma>" cross-references -> target entry id, with tap rectangle

Only lemmas, numbers and coordinates are written out. No entry text.

Usage:
  python3 bdag_index.py BDAG.pdf path/to/morphgnt_sblgnt_dir out.json
"""
import glob, json, os, re, sys, unicodedata, collections
import pymupdf
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from halot_index import sha256

GREEK = re.compile(r'[Ͱ-Ͽἀ-῿]')


def strip_accents(s):
    d = unicodedata.normalize('NFD', s)
    return unicodedata.normalize('NFC', ''.join(c for c in d if not unicodedata.combining(c))).lower()


def clean(s):
    s = unicodedata.normalize('NFC', s.strip().strip(',').strip())
    return re.sub(r'^[\d\s]+|[\d\s*†]+$', '', s)


def main(pdf, gnt_dir, out_path):
    doc = pymupdf.open(pdf)
    spans = []
    for pno in range(doc.page_count):
        for b in doc[pno].get_text('dict')['blocks']:
            for l in b.get('lines', []):
                for s in l['spans']:
                    spans.append((pno + 1, s['bbox'], s['font'], s['size'], s['text']))
    entries = []
    for i, (p, bb, f, sz, t) in enumerate(spans[:-1]):
        j = i + 1
        while j < len(spans) - 1 and not spans[j][4].strip() and j - i < 4:
            j += 1
        nxt = spans[j]
        if 'Bold' in f and sz >= 12.5 and GREEK.search(t) and nxt[0] == p and nxt[4].lstrip().startswith('⟦') \
                and abs(nxt[1][1] - bb[1]) < 8:
            lemma = clean(t)
            if lemma:
                entries.append({'id': len(entries) + 1, 'lemma': lemma, 'page': p, 'y': round(bb[1], 1)})
    by_exact = collections.defaultdict(list)
    by_loose = collections.defaultdict(list)
    for e in entries:
        head = e['lemma'].split(',')[0].strip()
        by_exact[head].append(e['id'])
        by_loose[strip_accents(head)].append(e['id'])
    gnt = collections.Counter()
    for f in glob.glob(os.path.join(gnt_dir, '*-morphgnt.txt')):
        for line in open(f, encoding='utf-8'):
            parts = line.split()
            if len(parts) >= 7:
                gnt[unicodedata.normalize('NFC', parts[6])] += 1
    lemmas, stats = {}, collections.Counter()
    for lem in gnt:
        if lem in by_exact:
            lemmas[lem] = by_exact[lem]; stats['exact'] += 1
        elif strip_accents(lem) in by_loose:
            lemmas[lem] = by_loose[strip_accents(lem)]; stats['loose'] += 1
        else:
            variants = {re.sub(r'\((\w)\)', '', lem), re.sub(r'\((\w)\)', r'\1', lem)}
            variants |= {re.sub(r'ομαι$', 'ω', v) for v in variants} | {re.sub(r'αμαι$', 'ημι', v) for v in variants}
            hit = next((by_loose[strip_accents(v)] for v in variants if strip_accents(v) in by_loose), None)
            if hit:
                lemmas[lem] = hit; stats['variant'] += 1
            else:
                stats['unmatched'] += 1
    # "s. <lemma>" cross references
    links = []
    for i, (p, bb, f, sz, t) in enumerate(spans[:-1]):
        if re.search(r'(?:^|\s)s\.(?:v\.)?\s*$', t):
            nxt = spans[i + 1]
            if nxt[0] == p and GREEK.search(nxt[4]):
                w = clean(nxt[4].split()[0]) if nxt[4].split() else ''
                ids = by_exact.get(w) or by_loose.get(strip_accents(w))
                if ids:
                    links.append({'p': p, 'r': [round(x, 1) for x in nxt[1]], 'to': ids[0]})
    out = {
        'source': 'BDAG (PDF supplied by the owner)',
        'file': {'sha256': sha256(pdf), 'pages': doc.page_count},
        'note': 'Lemmas, numbers and coordinates only. No entry text.',
        'entries': entries,
        'lemmas': lemmas,
        'links': links,
        'stats': dict(stats, entries=len(entries), gnt_lemmas=len(gnt), links=len(links)),
    }
    with open(out_path, 'w', encoding='utf-8') as f:
        json.dump(out, f, ensure_ascii=False, separators=(',', ':'))
    print(json.dumps(out['stats'], ensure_ascii=False))


if __name__ == '__main__':
    main(*sys.argv[1:4])
