"""Arabic search rules (ar_search.py), the shared cases with ArSearch.kt, and counts on the built database.

Usage: python3 tests/test_ar_search.py [path/to/ketaby.db]
"""
import os, sqlite3, sys
HERE = os.path.dirname(__file__)
sys.path.insert(0, os.path.join(HERE, '..'))
import ar_search as A
from build_db import plain_ar

fails = []


def check(ok, msg):
    if not ok:
        fails.append(msg)


for line in open(os.path.join(HERE, 'ar_search_cases.tsv'), encoding='utf-8'):
    line = line.rstrip('\n')
    if not line or line.startswith('#'):
        continue
    kind, mode, a, b, *rest = line.split('\t')
    if kind == 'norm':
        got = A.normalize(a, loose=(mode == 'loose'))
        check(got == b, f'norm {mode} {a!r}: got {got!r} want {b!r}')
        if mode == 'strict':
            check(plain_ar(a).strip() == b, f'plain_ar {a!r} differs from ar_search')
    elif kind == 'match':
        want = rest[0] == '1'
        got = A.matches_plain(A.normalize(a), b, whole=('whole' in mode), loose=mode.startswith('loose'))
        check(got == want, f'match {mode} {a!r} / {b!r}: got {got}')
    elif kind == 'forms':
        check(b in A.forms(a), f'forms({a!r}) lacks {b!r}')

db_path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, '..', 'build', 'ketaby.db')
if os.path.exists(db_path):
    db = sqlite3.connect(db_path)
    rows = db.execute('SELECT text, plain FROM verses').fetchall()
    check(all(plain_ar(t) == p for t, p in rows), 'verses.plain is not plain_ar(text)')
    check(all(A.normalize(t) == p.strip() for t, p in rows), 'ar_search.normalize differs from plain_ar on some verse')
    whole = sum(A.matches_plain(p, 'خبزا') for _, p in rows)
    part = sum(A.matches_plain(p, 'خبزا', whole=False) for _, p in rows)
    check(whole == 115, f'خبزا whole-word: {whole} verses, want 115')
    check(part == 122, f'خبزا part-of-word: {part} verses, want 122')
    print(f'database: خبزا {whole} whole-word, {part} part-of-word')
else:
    print('no database at', db_path, '- skipped the count checks')

for f in fails:
    print('FAIL', f)
print('ok' if not fails else f'{len(fails)} failures')
sys.exit(1 if fails else 0)
