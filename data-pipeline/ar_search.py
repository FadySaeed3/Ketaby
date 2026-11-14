"""Rules for Arabic search: the same as app/.../data/ArSearch.kt.

Both are tested against tests/ar_search_cases.tsv, so change the three together.
plain_ar() in build_db.py is normalize(s, loose=False) without the trim.
"""
import re

CONJ = ['', 'و', 'ف']
PREP = ['', 'ب', 'ل', 'ك']
MARKS = re.compile(r'[ً-ٰٟـ]')


def norm_char(c, loose=False):
    """The search form of one character, or None when it is dropped. Non-letters become a space."""
    if MARKS.match(c):
        return None
    if c in 'أإآٱ':
        return 'ا'
    if c == 'ى':
        return 'ي'
    if c == 'ة':
        return 'ه'
    if loose and c == 'ؤ':
        return 'و'
    if loose and c == 'ئ':
        return 'ي'
    if loose and c == 'ء':
        return None
    if 'ء' <= c <= 'ي':
        return c
    return ' '


def normalize(s, loose=False):
    return ''.join(x for x in (norm_char(c, loose) for c in s) if x is not None).strip()


def loosen(plain):
    """The loose form of text that is already in strict search form (verses.plain)."""
    return plain.replace('ؤ', 'و').replace('ئ', 'ي').replace('ء', '')


def words(query, loose=False):
    return [w for w in normalize(query, loose).split(' ') if w]


def forms(word):
    """Every written form that counts as the whole word: the word and the word with proclitics."""
    out = []
    article = len(word) > 2 and word.startswith('ال')
    for a in CONJ:
        for b in PREP:
            out.append(a + b + word)
            if article:
                if b == 'ل':
                    out.append(a + 'لل' + word[2:])
            else:
                out.append(a + ('لل' if b == 'ل' else b + 'ال') + word)
    return list(dict.fromkeys(out))


def matches_plain(plain_verse, query, whole=True, loose=False):
    ws = words(query, loose)
    if not ws:
        return False
    text = loosen(plain_verse) if loose else plain_verse
    if whole:
        toks = set(t for t in text.split(' ') if t)
        return all(any(f in toks for f in forms(w)) for w in ws)
    return all(w in text for w in ws)
