"""Parse saved arabiccatholicbible.com book pages into structured JSON.
Usage: python3 -I jparse.py <html_dir> <out.json>"""
import html, json, re, sys, os, collections

SUBS = 'أبتثجحخدذرزسشصضطظعغفقكلمنهوي'
NONCONN = 'أادذرزو'
AD = str.maketrans('٠١٢٣٤٥٦٧٨٩', '0123456789')
BLOCK = re.compile(r'<(h[1-6]|p)\b([^>]*)>(.*?)</\1>', re.S)
TAG = re.compile(r'<[^>]+>')
NUM = re.compile(r'[٠-٩]+')
MARK = re.compile(r'<a id="trigger(\d+)"[^>]*>\s*\(?\d+\)?\s*</a>')


def text_of(frag):
    frag = MARK.sub(lambda m: '⁣[[n%s]]' % m.group(1), frag)
    frag = re.sub(r'<br\s*/?>', '\n', frag)
    t = html.unescape(TAG.sub('', frag))
    t = t.replace('\xa0', ' ')
    return re.sub(r'[ \t]+', ' ', t).strip()


def parse_book(path):
    raw = open(path, encoding='utf-8').read()
    title = html.unescape(TAG.sub('', re.search(r'<h1 class="entry-title">(.*?)</h1>', raw, re.S).group(1))).strip()
    body = raw[raw.find('<div class="entry-content">'):]
    body = re.sub(r'<div id="slider".*?</div>\s*</div>\s*</div>', '', body, count=1, flags=re.S)
    ids = re.findall(r'<div id="([a-z0-9]+)-chap(\d+)"', body)
    cnt = collections.Counter(p for p, _ in ids)
    if len(cnt) > 1 and 'luke' in cnt and not os.path.basename(path).startswith('book_luke'):
        del cnt['luke']
    prefix = cnt.most_common(1)[0][0]
    parts = re.split(r'<div id="%s-chap(\d+)"' % prefix, body)
    intro_html = parts[0]
    intro = [{'tag': t, 'text': text_of(x)} for t, a, x in BLOCK.findall(intro_html) if text_of(x)]
    chapters = []
    for k in range(1, len(parts), 2):
        cn = int(parts[k]); chunk = parts[k + 1]
        chunk = chunk.split('<div id="', 1)[0] if False else chunk
        items = []; cur = 0; subs = {}; plain = set()
        lettered = os.path.basename(path) == 'book_est.html'
        for tag, attrs, inner in BLOCK.findall(chunk):
            t = text_of(inner)
            if not t:
                continue
            if tag == 'p' and re.fullmatch(r'\s*(<strong>.*?</strong>\s*)+', inner.replace('\xa0', ' '), re.S) and len(t) < 120:
                items.append({'k': 'h', 'lvl': 'ps', 'text': t, 'center': False})
                continue
            if tag.startswith('h'):
                if re.fullmatch(r'.*\s[٠-٩]+', t) and t.split()[-1].translate(AD) == str(cn) and len(t) < 40:
                    continue  # chapter title like «راعوت ١»
                items.append({'k': 'h', 'lvl': tag, 'text': t, 'center': 'center' in attrs})
                continue
            # paragraph: split on verse numbers
            pos = 0; segs = []; first = True
            for m in NUM.finditer(t):
                n = int(m.group().translate(AD))
                nxt = t[m.end():m.end() + 2]
                after = nxt[:1]
                ok_after = bool(after) and after != '/' and (after.isalpha() or after.isspace() or after in ' «"(…' or (after in '-–' and nxt[1:2] == ' ') or (after == '.' and nxt[1:2] == ' '))
                if lettered and after and '\u0621' <= after <= '\u064A':
                    nexts = SUBS[SUBS.index(subs.get(n, '')) + 1:] if subs.get(n) else SUBS
                    tat = nxt[1:2] == '\u0640'
                    if (tat or after in NONCONN) and after in nexts[:3] and (n == cur or cur < n <= cur + 3 or cur == 0):
                        subs[n] = after
                        end = m.end() + 1 + (1 if tat else 0)
                        segs.append((pos, m.start(), None)); segs.append((m.start(), end, (n, after))); pos = end; cur = n
                        continue
                if ok_after and ((cur == 0 and n <= 3) or cur < n <= cur + 3 or (lettered and n == cur and n in subs and n not in plain)):
                    segs.append((pos, m.start(), None)); segs.append((m.start(), m.end(), n)); pos = m.end(); cur = n; plain.add(n)
            segs.append((pos, len(t), None))
            newpara = True
            v_open = None
            for a, b, n in segs:
                if n is not None:
                    if isinstance(n, tuple):
                        items.append({'k': 'v', 'v': n[0], 'sub': n[1], 'text': '', 'p': newpara})
                    else:
                        items.append({'k': 'v', 'v': n, 'text': '', 'p': newpara})
                    newpara = False; v_open = items[-1]
                else:
                    s = t[a:b].strip()
                    if v_open is not None and not v_open['text']:
                        s = s.lstrip('-–. ')
                    if not s:
                        continue
                    if v_open is None:  # continuation line of previous verse (poetry) or stray text
                        last = next((x for x in reversed(items) if x['k'] == 'v'), None)
                        if last is not None:
                            last['text'] += '\n' + s; last.setdefault('lines', True)
                        else:
                            items.append({'k': 'h', 'lvl': 'sub', 'text': s, 'center': False})
                    else:
                        if not v_open['text']:
                            s = s.lstrip('. ')
                        v_open['text'] = (v_open['text'] + ' ' + s).strip()
                    newpara = False
            # a paragraph that starts without a verse number continues the open verse on a new line
        chapters.append({'n': cn, 'items': items})
    return {'title': title, 'prefix': prefix, 'intro': intro, 'chapters': chapters}


def fixups(books):
    """Numbering slips on the site, checked against the printed edition."""
    def chap(slug, n):
        return next(c for c in books[slug]['chapters'] if c['n'] == n)['items']
    # Romans 8:28 is printed on the site as «٣٨» and was glued to 8:27
    it = chap('rom', 8)
    for k, x in enumerate(it):
        if x['k'] == 'v' and x['v'] == 27 and '٣٨' in x['text']:
            a, b = x['text'].split('٣٨', 1)
            x['text'] = a.strip(); it.insert(k + 1, {'k': 'v', 'v': 28, 'text': b.strip(), 'p': False}); break
    # 1 Kings 7: the site numbers 7:50-51 as 51-52
    for x in chap('1kings', 7):
        if x['k'] == 'v' and x['v'] > 50:
            x['v'] -= 1


def main(d, out):
    books = {}
    for f in sorted(os.listdir(d)):
        if f.startswith('book_'):
            slug = f[5:-5]
            try:
                books[slug] = parse_book(os.path.join(d, f))
            except Exception as e:
                print('FAIL', slug, e)
    fixups(books)
    json.dump(books, open(out, 'w', encoding='utf-8'), ensure_ascii=False)
    for s, b in books.items():
        vs = [x for c in b['chapters'] for x in c['items'] if x['k'] == 'v']
        print(f"{s:10s} {b['prefix']:6s} ch={len(b['chapters']):3d} v={len(vs):5d} intro={sum(len(i['text']) for i in b['intro']):6d}")


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
