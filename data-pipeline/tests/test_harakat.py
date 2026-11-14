"""The harakat clean-up in build_db.py, on the AraSVD cases it was written for.

Usage: python3 tests/test_harakat.py [path/to/ketaby.db]
"""
import json, os, re, sqlite3, sys, unicodedata
HERE = os.path.dirname(__file__)
sys.path.insert(0, os.path.join(HERE, '..'))
from build_db import fix_harakat, _STRAY, HARAKA

CASES = [  # (token text, starts a word?, expected)
    ('ِللهِ', True, 'لِلهِ'),                        # Gen 14:18 كَاهِنًا ِللهِ
    ('ِللهِ:', True, 'لِلهِ:'),
    ('عَلَى الْحِمَار،ِ', True, 'عَلَى الْحِمَارِ،'),  # 1 Kgs 13:23
    ('و«َأَنَا', True, 'وَ«أَنَا'),                    # 1 Cor 1:12
    ('قَالَ ِللهِ', True, 'قَالَ لِلهِ'),               # mark after a space inside one token
    ('ِللهِ', False, 'ِللهِ'),                         # not at a word start: left alone
    ('كَلِمَة', True, 'كَلِمَة'),                      # nothing to do
]
fails = []
for txt, start, want in CASES:
    got = fix_harakat(txt, start)
    if got != want:
        fails.append(f'fix_harakat({txt!r}, {start}) = {got!r}, want {want!r}')

ov = json.load(open(os.path.join(HERE, '..', 'harakat_overrides.json'), encoding='utf-8'))['fixes']
bare = lambda s: re.sub('[' + HARAKA + ']', '', s)
for f in ov:
    if bare(f['from']) != bare(f['to']):
        fails.append(f"override {f['ref']} changes letters: {f['from']} -> {f['to']}")

db_path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, '..', 'build', 'ketaby.db')
if os.path.exists(db_path):
    db = sqlite3.connect(db_path)
    left = [(i, t) for i, t in db.execute('SELECT id, text FROM verses') if _STRAY.search(t)]
    if left:
        fails.append(f'{len(left)} verses still have a mark after a space or punctuation, e.g. verse {left[0][0]}')
    for ref, word in (((40, 16, 10), 'سَلاًّ'), ((47, 9, 10), 'غَلاَّتِ'), ((1, 14, 18), 'لِلهِ'), ((19, 59, 12), 'الْكَذِبِ الَّذِي')):
        t = db.execute('SELECT text FROM verses WHERE book=? AND ch=? AND v=?', ref).fetchone()[0]
        if unicodedata.normalize('NFC', word) not in unicodedata.normalize('NFC', t):
            fails.append(f'{ref}: {word} not in {t}')
else:
    print('no database at', db_path, '- skipped the database checks')

for f in fails:
    print('FAIL', f)
print('ok' if not fails else f'{len(fails)} failures')
sys.exit(1 if fails else 0)
