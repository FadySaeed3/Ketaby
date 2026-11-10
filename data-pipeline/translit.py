"""Arabic-script transliteration of Biblical Hebrew/Aramaic and Greek.

Hebrew (agreed scheme): classical Semitic values in Arabic letters. The
gutturals and emphatics keep their own letters (ح ع ط ص ق); begadkefat
spirantisation is shown only where Arabic has a familiar letter
(ב ڤ, כ خ, פ ف); gimel = ج (Egyptian g), dalet = د, tav = ت. Vowels as
harakat: patah/hataf-patah fatha, qamats fatha+alef, tsere kasra+ya,
segol/hiriq/vocal sheva kasra, holam damma+waw, qubuts damma, shureq
damma+waw; silent sheva sukun; dagesh forte shadda.

Greek: two readings, both without harakat (the usual way Arabic writes
Greek words): `academic` (Erasmian, as taught in seminaries) and `modern`
(Modern Greek pronunciation, as heard in Orthodox/Coptic churches).
"""
import re
import unicodedata

# ---------------------------------------------------------------- Hebrew
SHEVA, HSEG, HPAT, HQAM = 'ְ', 'ֱ', 'ֲ', 'ֳ'
HIRIQ, TSERE, SEGOL, PATAH, QAMATS, HOLAM, HOLAM_W, QUBUTS = 'ִ', 'ֵ', 'ֶ', 'ַ', 'ָ', 'ֹ', 'ֺ', 'ֻ'
DAGESH, SHIN_DOT, SIN_DOT, QAMATS_Q = 'ּ', 'ׁ', 'ׂ', 'ׇ'
FATHA, KASRA, DAMMA, SUKUN, SHADDA = 'َ', 'ِ', 'ُ', 'ْ', 'ّ'
VOWELS = {PATAH, QAMATS, QAMATS_Q, TSERE, SEGOL, HIRIQ, HOLAM, HOLAM_W, QUBUTS, HSEG, HPAT, HQAM}
LONG = {QAMATS, TSERE, HOLAM, HOLAM_W}
BGDKPT = set('בגדכפת')
GUTT = set('אהחע')
CONS = {
    'א': 'ء', 'ב': 'ب', 'ג': 'ج', 'ד': 'د', 'ה': 'ه', 'ו': 'و', 'ז': 'ز', 'ח': 'ح', 'ט': 'ط',
    'י': 'ي', 'כ': 'ك', 'ך': 'ك', 'ל': 'ل', 'מ': 'م', 'ם': 'م', 'נ': 'ن', 'ן': 'ن', 'ס': 'س',
    'ע': 'ع', 'פ': 'پ', 'ש': 'ش', 'ף': 'پ', 'צ': 'ص', 'ץ': 'ص', 'ק': 'ق', 'ר': 'ر', 'ת': 'ت',
}
SPIRANT = {'ב': 'ڤ', 'כ': 'خ', 'ך': 'خ', 'פ': 'ف', 'ף': 'ف'}


def _clusters(word):
    d = unicodedata.normalize('NFD', word)
    out = []
    for ch in d:
        if 'א' <= ch <= 'ת':
            out.append({'c': ch, 'm': set()})
        elif out and ('ְ' <= ch <= 'ׇ'):
            out[-1]['m'].add(ch)
    return out


def _vowel(m):
    for v in (PATAH, QAMATS, QAMATS_Q, TSERE, SEGOL, HIRIQ, HOLAM, HOLAM_W, QUBUTS, HSEG, HPAT, HQAM):
        if v in m:
            return v
    return SHEVA if SHEVA in m else None


def hebrew_to_arabic(word: str) -> str:
    """One pointed Hebrew/Aramaic word (cantillation allowed) -> Arabic script."""
    word = re.sub(r'[֑-ֽֿ֯׀׃ׅׄ׆]', '', word)
    if not word:
        return ''
    parts = re.split(r'([־\- ])', word)
    return ''.join('-' if p in ('־', '-') else (' ' if p == ' ' else _word(p)) for p in parts)


def _word(word):
    cl = _clusters(word)
    if not cl:
        return ''
    cons = ''.join(x['c'] for x in cl)
    if cons == 'יהוה':
        return 'يَهْوَه'
    n = len(cl)
    for x in cl:
        x['v'] = _vowel(x['m'])
        x['skip'] = False
        x['male'] = False
    # vowel letters (matres lectionis)
    for i, x in enumerate(cl):
        prev = cl[i - 1] if i > 0 else None
        c, m, v = x['c'], x['m'], x['v']
        if c == 'ו' and v in (HOLAM, HOLAM_W) and prev and prev['v'] in (None, SHEVA) and not prev['skip']:
            prev['v'] = HOLAM; prev['male'] = True; x['skip'] = True
        elif c == 'ו' and DAGESH in m and v is None and prev and prev['v'] in (None, SHEVA) and not prev['skip']:
            prev['v'] = 'SHUREQ'; x['skip'] = True
        elif c == 'י' and v is None and DAGESH not in m and prev and prev['v'] in (HIRIQ, TSERE, SEGOL):
            prev['male'] = True; x['skip'] = True
        elif c == 'א' and v is None and i > 0:
            x['skip'] = True
        elif c == 'ה' and i == n - 1 and v is None and DAGESH not in m and i > 0:
            x['skip'] = True
    out = []
    prev_full = None   # vowel of the previous pronounced consonant (None: no vowel / silent)
    live = [x for x in cl if not x['skip']]
    for k, x in enumerate(live):
        c, m, v = x['c'], x['m'], x['v']
        first, last = k == 0, k == len(live) - 1
        nxt = live[k + 1] if not last else None
        # conjunction u- written as shureq on its own
        if first and c == 'ו' and DAGESH in m and v is None:
            out.append('أُو'); prev_full = 'SHUREQ'; continue
        if c in SPIRANT:
            letter = CONS[c] if DAGESH in m else SPIRANT[c]
        elif c == 'ש':
            letter = 'س' if SIN_DOT in m else 'ش'
        else:
            letter = CONS[c]
        if c == 'א':
            if first:
                letter = 'آ' if v == QAMATS else ('إ' if v in (HIRIQ, SEGOL, TSERE, HSEG) else 'أ')
            else:
                letter = 'ء'
        shadda = DAGESH in m and not first and prev_full not in (None,) and c not in GUTT and c != 'ר'
        if last and v == PATAH and c in ('ח', 'ע', 'ה') and prev_full is not None and not first:
            out.append(FATHA + letter); continue
        if v == SHEVA and c in ('י', 'ו') and not first and not shadda:
            mark = '' if last else SUKUN
            prev_full = None
            out.append(letter + mark)
            continue
        if v == SHEVA:
            vocal = first or shadda or (prev_full in (QAMATS, TSERE, HOLAM, 'SHUREQ') or (prev_full == HIRIQ and live[k - 1]['male'])) \
                    or (nxt is not None and nxt['c'] == c)
            if last:
                mark, prev_full = '', None
            elif vocal:
                mark, prev_full = KASRA, HSEG
            else:
                mark, prev_full = SUKUN, None
        elif v in (PATAH, HPAT):
            mark, prev_full = FATHA, PATAH
        elif v == QAMATS:
            diph = nxt is not None and nxt['c'] in ('י', 'ו') and nxt['v'] in (None, SHEVA) and DAGESH not in nxt['m']
            mark, prev_full = ('' if letter == 'آ' else (FATHA if diph else FATHA + 'ا')), QAMATS
        elif v in (QAMATS_Q, HQAM, QUBUTS):
            mark, prev_full = DAMMA, QUBUTS
        elif v == TSERE:
            mark, prev_full = KASRA + 'ي', TSERE
        elif v in (SEGOL, HSEG):
            mark, prev_full = KASRA, SEGOL
        elif v == HIRIQ:
            mark, prev_full = KASRA + ('ي' if x['male'] else ''), HIRIQ
        elif v in (HOLAM, HOLAM_W):
            mark, prev_full = DAMMA + 'و', HOLAM
        elif v == 'SHUREQ':
            mark, prev_full = DAMMA + 'و', 'SHUREQ'
        else:
            mark, prev_full = ('' if last else SUKUN), None
        out.append(letter + (SHADDA if shadda else '') + mark)
    return unicodedata.normalize('NFC', ''.join(out))


# ---------------------------------------------------------------- Greek
def _strip_greek(w):
    d = unicodedata.normalize('NFD', w.lower())
    rough = '̔' in d
    base = ''.join(ch for ch in d if not unicodedata.combining(ch))
    base = base.replace('ς', 'σ')
    base = re.sub(r'[^α-ω]', '', base)
    return base, rough


ACAD_DI = [('ου', 'و'), ('αι', 'اي'), ('ει', 'ي'), ('οι', 'وي'), ('υι', 'ي'), ('αυ', 'او'), ('ευ', 'يو'), ('ηυ', 'يو'),
           ('γγ', 'نج'), ('γκ', 'نك'), ('γξ', 'نكس'), ('γχ', 'نخ')]
ACAD = {'α': 'ا', 'β': 'ب', 'γ': 'ج', 'δ': 'د', 'ε': 'ي', 'ζ': 'ز', 'η': 'ي', 'θ': 'ث', 'ι': 'ي', 'κ': 'ك',
        'λ': 'ل', 'μ': 'م', 'ν': 'ن', 'ξ': 'كس', 'ο': 'و', 'π': 'پ', 'ρ': 'ر', 'σ': 'س', 'τ': 'ت', 'υ': 'ي',
        'φ': 'ف', 'χ': 'خ', 'ψ': 'پس', 'ω': 'و'}
MOD_DI = [('ου', 'و'), ('αι', 'ي'), ('ει', 'ي'), ('οι', 'ي'), ('υι', 'ي'), ('μπ', 'ب'), ('ντ', 'د'),
          ('γγ', 'نج'), ('γκ', 'نج'), ('γξ', 'نكس'), ('γχ', 'نخ'), ('τζ', 'دز')]
MOD = {'α': 'ا', 'β': 'ڤ', 'γ': 'غ', 'δ': 'ذ', 'ε': 'ي', 'ζ': 'ز', 'η': 'ي', 'θ': 'ث', 'ι': 'ي', 'κ': 'ك',
       'λ': 'ل', 'μ': 'م', 'ν': 'ن', 'ξ': 'كس', 'ο': 'و', 'π': 'پ', 'ρ': 'ر', 'σ': 'س', 'τ': 'ت', 'υ': 'ي',
       'φ': 'ف', 'χ': 'خ', 'ψ': 'پس', 'ω': 'و'}
VOICED = set('βγδζλμνραεηιουω')


def _greek(word, academic):
    base, rough = _strip_greek(word)
    if not base:
        return ''
    out = []
    i = 0
    di = ACAD_DI if academic else MOD_DI
    table = ACAD if academic else MOD
    while i < len(base):
        two = base[i:i + 2]
        hit = None
        for k, val in di:
            if two == k:
                hit = val; break
        if not academic and two in ('αυ', 'ευ', 'ηυ'):
            nxt = base[i + 2:i + 3]
            f = 'ڤ' if (not nxt or nxt in VOICED) else 'ف'
            hit = {'αυ': 'ا', 'ευ': 'ي', 'ηυ': 'ي'}[two] + f
        if not academic and base[i] == 'γ' and base[i + 1:i + 2] in ('ε', 'η', 'ι', 'υ') and hit is None:
            out.append('ي'); i += 1; continue
        if hit is not None:
            out.append(hit); i += 2; continue
        out.append(table[base[i]]); i += 1
    s = ''.join(out)
    if academic and rough:
        return 'ه' + s
    if base[0] in 'αεηιουω' and s:
        if s[0] == 'ا':
            s = 'أ' + s[1:]
        elif s[0] == 'و':
            s = 'أ' + s
        elif s[0] == 'ي':
            s = ('إ' + s[1:]) if (base[0] == 'ε' and base[:2] not in ('ει', 'ευ')) else ('إ' + s)
    return s


def greek_academic(word: str) -> str:
    return _greek(word, True)


def greek_modern(word: str) -> str:
    return _greek(word, False)
