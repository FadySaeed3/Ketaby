import sys, os, unicodedata
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))
from bw_decode import decode_hebrew, consonants

N = lambda s: unicodedata.normalize('NFC', s)

CASES = [
    ("sm'k.mi", 'מִכְמָס'),
    ("!m'k.mi", 'מִכְמָן'),
    ('~yNIm;k.mi', 'מִכְמַנִּים'),
    ('yn:T.T;', 'תַּתְּנַי'),
    ('!ATx.T;', 'תַּחְתּוֹן'),
    ("ba'", 'אָב'),
    ('bae', 'אֵב'),
    ('%l,m,ybia]', 'אֲבִימֶלֶךְ'),
    ("aT'g>b;a]", 'אֲבַגְתָּא'),
]


def test_cases():
    for bw, want in CASES:
        assert decode_hebrew(bw) == N(want), (bw, decode_hebrew(bw), want)


def test_consonants():
    assert consonants('מִכְמָן') == 'מכמנ'


if __name__ == '__main__':
    test_cases(); test_consonants(); print('ok')
