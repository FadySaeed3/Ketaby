"""Open lexicons shown in the word panel, as light HTML (<b>, <i>, <br> only).

  bdb   Brown-Driver-Briggs (OpenScriptures HebrewLexicon; text PD, markup CC BY 4.0), keyed by plain Strong's
  ubs   UBS Dictionary of Biblical Hebrew / of the Greek NT (CC BY-SA 4.0), keyed by plain Strong's
  lsj   Liddell-Scott-Jones for NT words (STEPBible TFLSJ, CC BY 4.0), keyed by extended Strong's
  tbes  STEPBible brief lexicons TBESH / TBESG (CC BY 4.0), keyed by extended Strong's
  mm    Moulton & Milligan, Vocabulary of the Greek Testament (1930, public domain; OCR of the
        Internet Archive scan, see mm_extract.py), keyed by dStrong through the Greek lemma

Keys: plain Strong's are written "H430" / "G3056"; extended ones as in STEPBible ("H0430G", "G3056").
"""
import glob
import html
import json
import os
import re

ALLOWED = re.compile(r'</?(b|i|br)\s*/?>', re.I)


def plain(code):
    """'H0430' / 'A0002' / 'G0101' / 'H0430G' -> 'H430' / 'H2' / 'G101'."""
    m = re.match(r'([HAG])0*(\d+)', code or '')
    if not m:
        return None
    return ('G' if m.group(1) == 'G' else 'H') + m.group(2)


def tidy(s):
    """Keep only <b>, <i>, <br>; unescape entities; collapse spaces."""
    s = re.sub(r'<br\s*/?>', '<br>', s, flags=re.I)
    s = re.sub(r'<(?!/?(?:b|i|br)\b)[^>]*>', '', s)
    s = html.unescape(s)
    s = re.sub(r'[ \t\r\n]+', ' ', s)
    s = re.sub(r'(\s*<br>\s*){3,}', '<br><br>', s)
    return s.strip()


# ---------------------------------------------------------------- BDB
def load_bdb(lex_dir):
    li = open(os.path.join(lex_dir, 'LexicalIndex.xml'), encoding='utf-8').read()
    strong_to_bdb = {}
    for m in re.finditer(r'<xref\s+bdb="([^"]+)"[^>]*?\bstrong="(\d+)"', li):
        strong_to_bdb.setdefault('H' + str(int(m.group(2))), []).append(m.group(1))
    x = open(os.path.join(lex_dir, 'BrownDriverBriggs.xml'), encoding='utf-8').read()
    entries = {}
    for m in re.finditer(r'<entry id="([^"]+)"[^>]*>(.*?)</entry>', x, re.S):
        body = m.group(2)
        body = re.sub(r'<status[^>]*>.*?</status>', '', body, flags=re.S)
        body = re.sub(r'<sense n="([^"]+)">', r'<br><b>\1.</b> ', body)
        body = re.sub(r'<stem>(.*?)</stem>', r'<br><b>\1</b>', body, flags=re.S)
        body = re.sub(r'<def>(.*?)</def>', r'<b>\1</b>', body, flags=re.S)
        body = re.sub(r'<pos>(.*?)</pos>', r'<i>\1</i>', body, flags=re.S)
        body = re.sub(r'<em>(.*?)</em>', r'<i>\1</i>', body, flags=re.S)
        entries[m.group(1)] = tidy(body)
    out = {}
    for s, ids in strong_to_bdb.items():
        parts = [entries[i] for i in dict.fromkeys(ids) if entries.get(i)]
        if parts:
            out[s] = '<br><br>'.join(parts)
    return out


# ---------------------------------------------------------------- UBS
def load_ubs(path):
    data = json.load(open(path, encoding='utf-8-sig'))
    out = {}
    for e in data:
        keys = {plain(c) for c in (e.get('StrongCodes') or []) if plain(c)}
        if not keys:
            continue
        lines = ['<b>' + html.escape(e.get('Lemma') or '') + '</b>']
        for bf in e.get('BaseForms') or []:
            pos = ', '.join(p for p in (bf.get('PartsOfSpeech') or []) if p)
            if pos:
                lines.append('<i>' + html.escape(pos) + '</i>')
            n = 0
            for lm in bf.get('LEXMeanings') or []:
                dom = ', '.join(d.get('Domain') or '' for d in (lm.get('LEXDomains') or []) if d.get('Domain'))
                for sense in lm.get('LEXSenses') or []:
                    if sense.get('LanguageCode') not in (None, 'en'):
                        continue
                    n += 1
                    gl = ', '.join(sense.get('Glosses') or [])
                    d = sense.get('DefinitionLong') or sense.get('DefinitionShort') or ''
                    line = f'<b>{n}. {html.escape(gl)}</b>'
                    if dom:
                        line += f' <i>[{html.escape(dom)}]</i>'
                    if d:
                        line += ' ' + html.escape(d)
                    if sense.get('Comments'):
                        line += ' ' + html.escape(sense['Comments'])
                    lines.append(line)
        text = '<br>'.join(lines)
        text = re.sub(r'\{L:([^<}]*)<[^>]*>\}', r'\1', text)     # {L:ῥῆμα<SDBG:ῥῆμα:000001>} -> ῥῆμα
        text = re.sub(r'\{S:\d+\}', '', text)                    # verse codes
        text = re.sub(r'\{D:([^}]*)\}', r'§\1', text)             # domain numbers
        text = re.sub(r'\{[A-Z]:([^}]*)\}', r'\1', text)
        text = tidy(text.replace('|', '<br>'))
        for k in keys:
            out[k] = (out[k] + '<br><br>' + text) if k in out else text
    return out


# ---------------------------------------------------------------- STEPBible tab files (TFLSJ, TBESH, TBESG)
def load_step_tab(paths, col_def):
    """Lines 'G0015\tG0015 =\tG0015\tlemma\ttranslit\tmorph\tgloss\tdefinition'."""
    out = {}
    for p in paths:
        for line in open(p, encoding='utf-8-sig'):
            parts = line.rstrip('\n').split('\t')
            if len(parts) <= col_def or not re.fullmatch(r'[HG]\d{4}[A-Za-z]?', parts[0]):
                continue
            d = parts[col_def]
            d = re.sub(r'<a [^>]*>(.*?)</a>', r'\1', d, flags=re.S)        # keep the visible reference summary
            d = re.sub(r'</?Level\d>', '', d)
            d = d.replace('_', ' ')
            gloss = parts[col_def - 1] if col_def >= 1 else ''
            text = tidy(d)
            if gloss and col_def == 7:
                text = f'<b>{html.escape(parts[3])}</b> · {html.escape(gloss)}<br>' + text
            if text:
                out[parts[0]] = text
    return out


# ---------------------------------------------------------------- Moulton & Milligan
def load_mm(path, greek_lemmas):
    """greek_lemmas: [(dstrong, lemma)]. Entries carry a normalized lemma key "k"."""
    import gzip
    from mm_extract import norm
    by_key = {}
    for ds, lemma in greek_lemmas:
        by_key.setdefault(norm(lemma), []).append((ds, lemma))
    texts = {}
    for e in json.load(gzip.open(path, 'rt', encoding='utf-8')):
        head, body = e.get('h'), e['t']
        if head and body.startswith(head):
            body = body[len(head):]
        elif not head:                      # the heading was misread as Latin letters: drop it
            body = re.sub(r'^[|\s]*[^\s.,]+', '', body, count=1)
        texts.setdefault(e['k'], []).append((head, html.escape(body.lstrip(' .,|')) + f'<br><i>scan p. {e["p"]}</i>'))
    out = []
    for k, parts in texts.items():
        for ds, lemma in by_key.get(k, []):
            out.append((ds, 'mm', '<br><br>'.join(f'<b>{html.escape(h or lemma)}</b> {b}' for h, b in parts)))
    return out


def load_all(stepbible_dir, hebrew_lexicon_dir, ubs_dir, mm_path=None, greek_lemmas=()):
    lexd = os.path.join(stepbible_dir, 'Lexicons')
    tbes = glob.glob(os.path.join(lexd, 'TBES*.txt'))
    lsj = glob.glob(os.path.join(lexd, 'TFLSJ*.txt'))
    rows = []
    for k, v in load_bdb(hebrew_lexicon_dir).items():
        rows.append((k, 'bdb', v))
    for f in (os.path.join(ubs_dir, 'dictionaries', 'hebrew', 'JSON', 'UBSHebrewDic-v0.9.4-en.JSON'),
              os.path.join(ubs_dir, 'dictionaries', 'greek', 'JSON', 'UBSGreekNTDic-v1.1-en.JSON')):
        for k, v in load_ubs(f).items():
            rows.append((k, 'ubs', v))
    for k, v in load_step_tab(lsj, 7).items():
        rows.append((k, 'lsj', v))
    for k, v in load_step_tab(tbes, 7).items():
        rows.append((k, 'tbes', v))
    if mm_path and os.path.exists(mm_path):
        rows += load_mm(mm_path, greek_lemmas)
    return rows
