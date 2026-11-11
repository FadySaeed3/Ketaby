"""ex.py BASE ref ref ... -> VD text + which dstrong numbers of BASE (e.g. H7200) occur in each verse.
   ex.py BASE ? -> list the sense numbers of BASE with glosses/counts."""
import re, sqlite3, sys
db = sqlite3.connect(__import__('os').path.join(__import__('os').path.dirname(__import__('os').path.abspath(__file__)), '..', 'build', 'ketaby.db'))
base = sys.argv[1]
print(db.execute("select group_concat(dstrong||' '||gloss||' '||count, ' ; ') from lemmas where dstrong like ?", (base + '%',)).fetchone()[0])
codes = {c.lower(): i for i, c in db.execute('select id, code from books')}
codes.update({o.lower(): i for i, o in db.execute('select id, osis from books')})
def strip(s): return re.sub(r'[ً-ٰٟـ]', '', s)
for ref in sys.argv[2:]:
    if ref == '?': continue
    m = re.match(r'(\S+)\s+(\d+):(\d+)', ref)
    b = codes[m.group(1).lower()]; ch, v = int(m.group(2)), int(m.group(3))
    r = db.execute('select id, text from verses where book=? and ch=? and v=?', (b, ch, v)).fetchone()
    ds = db.execute('select dstrong, text from owords where vd_verse=? and (dstrong like ? or dstrongs like ?)', (r[0], base + '%', '%' + base + '%')).fetchall()
    print(f'{ref}:', strip(r[1]), '||', ds)
