"""Turn STEPBible book codes in English entry text (Jhn 3:16, 1Co 13:4) into the usual abbreviations (John 3:16, 1 Cor 13:4).
  python3 enrefs.py file.json ...   (a list of entries or a {words:[...]} file; rewrites in place)"""
import json, re, sys
M = {'Exo': 'Exod', 'Deu': 'Deut', 'Jos': 'Josh', 'Jdg': 'Judg', 'Rut': 'Ruth', '1Sa': '1 Sam', '2Sa': '2 Sam',
     '1Ki': '1 Kgs', '2Ki': '2 Kgs', '1Ch': '1 Chr', '2Ch': '2 Chr', 'Ezr': 'Ezra', 'Est': 'Esth', 'Psa': 'Ps',
     'Pro': 'Prov', 'Ecc': 'Eccl', 'Sng': 'Song', 'Ezk': 'Ezek', 'Jol': 'Joel', 'Amo': 'Amos', 'Oba': 'Obad',
     'Jon': 'Jonah', 'Nam': 'Nah', 'Zep': 'Zeph', 'Zec': 'Zech', 'Mat': 'Matt', 'Mrk': 'Mark', 'Luk': 'Luke',
     'Jhn': 'John', 'Act': 'Acts', '1Co': '1 Cor', '2Co': '2 Cor', 'Php': 'Phil', '1Th': '1 Thess', '2Th': '2 Thess',
     '1Ti': '1 Tim', '2Ti': '2 Tim', 'Tit': 'Titus', 'Phm': 'Phlm', '1Pe': '1 Pet', '2Pe': '2 Pet', '1Jn': '1 John',
     '2Jn': '2 John', '3Jn': '3 John', 'Jud': 'Jude'}
R = re.compile(r'\b(' + '|'.join(map(re.escape, M)) + r')(?= \d+:\d+)')
for p in sys.argv[1:]:
    raw = json.load(open(p, encoding='utf-8')); ws = raw if isinstance(raw, list) else raw['words']
    n = 0
    for w in ws:
        new = R.sub(lambda m: M[m.group(1)], w['entry']['en'])
        n += new != w['entry']['en']; w['entry']['en'] = new
    json.dump(raw, open(p, 'w', encoding='utf-8'), ensure_ascii=False, indent=2 if isinstance(raw, dict) else 1)
    print(p.split('/')[-1], n, 'entries changed')
