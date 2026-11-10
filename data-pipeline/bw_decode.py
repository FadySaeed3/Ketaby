"""Decode BibleWorks legacy font encodings (Bwhebb) to Unicode Hebrew.

BibleWorks fonts store Hebrew as Latin characters in *visual* left-to-right
order: each consonant is followed by its marks, and the consonants run
backwards. We tokenise into clusters (consonant + following marks), reverse
the clusters, and map each character. Bracket glyphs swap when reversed.

The map below was derived by aligning ~9,400 decoded headwords with the
pointed headwords of the open Strong's Hebrew lexicon (OpenScriptures).
"""
import unicodedata

CONSONANTS = {
    'a': 'א', 'b': 'ב', 'g': 'ג', 'd': 'ד', 'h': 'ה', 'w': 'ו', 'z': 'ז', 'x': 'ח',
    'j': 'ט', 'y': 'י', 'k': 'כ', '%': 'ךְ', '$': 'ך', 'l': 'ל', 'm': 'מ', '~': 'ם',
    'n': 'נ', '!': 'ן', 's': 'ס', '[': 'ע', 'p': 'פ', '@': 'ף', 'c': 'צ', '#': 'ץ',
    'q': 'ק', 'r': 'ר', 'f': 'שׂ', 'v': 'שׁ', 'X': 'ש', 't': 'ת',
    # upper case = consonant with dagesh (mappiq for H)
    'B': 'בּ', 'G': 'גּ', 'D': 'דּ', 'H': 'הּ', 'Z': 'זּ', 'J': 'טּ', 'Y': 'יּ', 'K': 'כּ',
    'L': 'לּ', 'M': 'מּ', 'N': 'נּ', 'S': 'סּ', 'P': 'פּ', 'C': 'צּ', 'Q': 'קּ', 'R': 'רּ',
    'F': 'שּׂ', 'V': 'שּׁ', 'T': 'תּ',
    # vowel letters
    'A': 'וֹ', 'W': 'וּ',
}
SHEVA, HSEG, HPAT, HQAM = 'ְ', 'ֱ', 'ֲ', 'ֳ'
HIRIQ, TSERE, SEGOL, PATAH, QAMATS, HOLAM, QUBUTS = 'ִ', 'ֵ', 'ֶ', 'ַ', 'ָ', 'ֹ', 'ֻ'
METEG, MAQAF = 'ֽ', '־'
MARKS = {
    "'": QAMATS, '"': QAMATS, ';': PATAH, ':': PATAH, '.': SHEVA, '>': SHEVA,
    'i': HIRIQ, 'I': HIRIQ, 'e': TSERE, 'E': TSERE, ',': SEGOL, '<': SEGOL,
    'o': HOLAM, 'O': HOLAM, '{': HOLAM, 'u': QUBUTS, 'U': QUBUTS,
    ']': HPAT, '/': HSEG, '\\': HQAM, '}': HQAM,
    'ñ': METEG, '†': METEG,
}
STANDALONE = {'-': MAQAF, '·': MAQAF, ' ': ' ', 'Æ': ' / ', '¿': ')', 'À': '(', '(': ')', ')': '('}
DROP = set('Ý')


def _tokens(s):
    toks = []
    for ch in s:
        if ch in CONSONANTS:
            toks.append([CONSONANTS[ch]])
        elif ch in MARKS and toks and isinstance(toks[-1], list):
            toks[-1].append(MARKS[ch])
        elif ch in STANDALONE:
            toks.append(STANDALONE[ch])
        elif ch in DROP:
            continue
        else:
            toks.append(ch)
    return toks


def decode_hebrew(s: str) -> str:
    """Bwhebb string (visual order) -> Unicode Hebrew (logical order, NFC)."""
    s = s.replace('&zwj;', '')
    out = []
    for t in reversed(_tokens(s)):
        out.append(''.join(t) if isinstance(t, list) else t)
    return unicodedata.normalize('NFC', ''.join(out)).strip()


FINALS = {'ך': 'כ', 'ם': 'מ', 'ן': 'נ', 'ף': 'פ', 'ץ': 'צ'}


def consonants(u: str) -> str:
    """Bare consonant skeleton, final forms normalised, no marks."""
    d = unicodedata.normalize('NFD', u)
    return ''.join(FINALS.get(c, c) for c in d if 'א' <= c <= 'ת')
