"""Builds a tiny fake Jesuit package for the emulator smoke test.

The real package is copyrighted and never enters the repo or CI, so this one holds made-up
placeholder sentences with the same tables, markers and verse map (Genesis 1:1-5 and 2:1-3).
Usage: python3 jesuit_fixture.py out.ketaby
"""
import sqlite3, sys, os

L, R = '', ''
out = sys.argv[1]
if os.path.exists(out):
    os.remove(out)
db = sqlite3.connect(out)
db.executescript("""
  CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT);
  CREATE TABLE jbooks(id INTEGER PRIMARY KEY, slug TEXT, name TEXT, short TEXT, testament TEXT, vd_book INTEGER, chapters INTEGER, notes INTEGER);
  CREATE TABLE jintro(book INTEGER, seq INTEGER, tag TEXT, text TEXT);
  CREATE TABLE jitems(book INTEGER, ch INTEGER, seq INTEGER, kind TEXT, lvl TEXT, v INTEGER, sub TEXT, para INTEGER, text TEXT);
  CREATE TABLE jnotes(book INTEGER, ch INTEGER, n INTEGER, text TEXT);
  CREATE TABLE jmargin(book INTEGER, ch INTEGER, v INTEGER, sub TEXT, refs TEXT);
  CREATE TABLE jmap(vd_book INTEGER, vd_ch INTEGER, vd_v INTEGER, book INTEGER, ch INTEGER, v INTEGER, sub TEXT);
""")
for k, v in {'format': '1', 'kind': 'jesuit', 'name': 'نسخة تجريبية', 'notes': '2'}.items():
    db.execute('INSERT INTO meta VALUES(?,?)', (k, v))
db.execute("INSERT INTO jbooks VALUES(1,'gen','سفر التكوين','التكوين','OT',1,2,2)")
db.execute("INSERT INTO jbooks VALUES(2,'matt','إنجيل متى','متى','NT',40,1,0)")
db.execute("INSERT INTO jintro VALUES(1,1,'h2','عنوان تجريبي في المقدمة')")
db.execute("INSERT INTO jintro VALUES(1,2,'p','فقرة تجريبية مكان مقدمة السفر، علشان نتأكد إن الشاشة بتعرض المقدمة صح.')")
rows = [
    (1, 0, 'h', 'h2', None, '', 0, 'عنوان تجريبي للقسم' + L + '1' + R),
    (1, 1, 'v', None, 1, '', 1, 'جملة تجريبية مكان الآية الأولى' + L + '2' + R + ' وبعدها كلام كمان.'),
    (1, 2, 'v', None, 2, '', 0, 'جملة تجريبية مكان الآية الثانية.'),
    (1, 3, 'h', 'ps', None, '', 0, 'عنوان فرعي تجريبي'),
    (1, 4, 'v', None, 3, '', 1, 'سطر شعري أول\nسطر شعري ثاني'),
    (1, 5, 'v', None, 4, '', 0, 'جملة تجريبية مكان الآية الرابعة.'),
    (1, 6, 'v', None, 5, '', 0, 'جملة تجريبية مكان الآية الخامسة.'),
    (2, 0, 'v', None, 1, '', 1, 'جملة تجريبية في الفصل الثاني.'),
    (2, 1, 'v', None, 2, '', 0, 'جملة تجريبية تانية.'),
    (2, 2, 'v', None, 3, '', 0, 'جملة تجريبية تالتة.'),
]
for ch, seq, kind, lvl, v, sub, para, text in rows:
    db.execute('INSERT INTO jitems VALUES(1,?,?,?,?,?,?,?,?)', (ch, seq, kind, lvl, v, sub if kind == 'v' else None, para, text))
db.execute("INSERT INTO jitems VALUES(2,1,0,'v',NULL,1,'',1,'جملة تجريبية في أول متى.')")
db.execute("INSERT INTO jnotes VALUES(1,1,1,'حاشية تجريبية على العنوان.')")
db.execute("INSERT INTO jnotes VALUES(1,1,2,'حاشية تجريبية على الآية الأولى.')")
db.execute("INSERT INTO jmargin VALUES(1,1,1,'','مز ٨ ؛ يو ١/١-٣')")
for v in range(1, 6):
    db.execute('INSERT INTO jmap VALUES(1,1,?,1,1,?,\'\')', (v, v))
for v in range(1, 4):
    db.execute('INSERT INTO jmap VALUES(1,2,?,1,2,?,\'\')', (v, v))
db.execute("INSERT INTO jmap VALUES(40,1,1,2,1,1,'')")
db.commit()
db.close()
print('fixture', out, os.path.getsize(out), 'bytes')
