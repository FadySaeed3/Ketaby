"""vd.py Gen 1:1 'Exod 3:14' ... -> Van Dyke text without harakat (+ the original words' Strong's if -s)."""
import re, sqlite3, sys
db = sqlite3.connect(__import__('os').path.join(__import__('os').path.dirname(__import__('os').path.abspath(__file__)), '..', 'build', 'ketaby.db'))
codes = {c.lower(): i for i, c in db.execute('select id, code from books')}
codes.update({o.lower(): i for i, o in db.execute('select id, osis from books')})
def strip(s): return re.sub(r'[ً-ٰٟـ]', '', s)
for ref in sys.argv[1:]:
    m = re.match(r'(\S+)\s+(\d+):(\d+)(?:-(\d+))?', ref)
    b = codes[m.group(1).lower()]; ch = int(m.group(2)); v1 = int(m.group(3)); v2 = int(m.group(4) or v1)
    for v in range(v1, v2 + 1):
        r = db.execute('select text from verses where book=? and ch=? and v=?', (b, ch, v)).fetchone()
        print(f'{ref.split()[0]} {ch}:{v}', strip(r[0]) if r else '—')
