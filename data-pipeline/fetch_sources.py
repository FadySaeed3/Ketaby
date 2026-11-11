"""Download the pinned open sources into data-pipeline/downloads/ (git-ignored).

Only the folders we use are checked out (sparse), at a fixed commit, so every
build sees exactly the same data.
"""
import gzip, hashlib, os, shutil, subprocess, sys, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
DL = os.path.join(HERE, 'downloads')

SOURCES = [
    {
        'name': 'STEPBible-Data',
        'url': 'https://github.com/STEPBible/STEPBible-Data.git',
        'commit': '5de0a47f35f0fc13dfe1430d104e5db8a4285f9c',
        'paths': ['/Tagged-Bibles/Arabic Bibles/*', '/Translators Amalgamated OT+NT/*'],
    },
]

# Public-domain English Bibles. Each file is pinned by sha256; if the publisher
# has changed or removed it, the copy on this repo's `source-mirror` branch
# (same bytes, pinned commit) is used instead.
MIRROR = ('https://raw.githubusercontent.com/FadySaeed3/Ketaby/'
          '67c15e0ca3308d2df3ac5ef5ccec275661997109/')
FILES = [
    {'name': 'eng-kjv_usfm.zip', 'url': 'https://ebible.org/Scriptures/eng-kjv_usfm.zip',
     'sha256': '941ef56e3e38a5cd7d72a2ea582559e37e9da5be0b241cf8caf71014650b8133'},
    {'name': 'bsb_tables.tsv.gz', 'url': 'https://bereanbible.com/bsb_tables.tsv', 'gzip': True,
     'sha256': 'aa2a9a0f004d015738cd1839c7c56c3be2549e0d4f4537090c2017268c223c60'},  # of the unzipped tsv
]


def sha256(path, unzip=False):
    h = hashlib.sha256()
    with (gzip.open(path, 'rb') if unzip else open(path, 'rb')) as f:
        for b in iter(lambda: f.read(1 << 20), b''):
            h.update(b)
    return h.hexdigest()


def get(url, dest):
    print('+ download', url, flush=True)
    req = urllib.request.Request(url, headers={'User-Agent': 'ketaby-build'})
    with urllib.request.urlopen(req, timeout=120) as r, open(dest, 'wb') as f:
        shutil.copyfileobj(r, f)


def fetch_file(spec):
    d = os.path.join(DL, 'english')
    os.makedirs(d, exist_ok=True)
    dest = os.path.join(d, spec['name'])
    gz = spec.get('gzip', False)
    ok = lambda: os.path.exists(dest) and sha256(dest, unzip=gz) == spec['sha256']
    if ok():
        return
    try:
        if gz:
            raw = dest[:-3]
            get(spec['url'], raw)
            with open(raw, 'rb') as fi, gzip.GzipFile(dest, 'wb', mtime=0) as fo:
                shutil.copyfileobj(fi, fo)
            os.remove(raw)
        else:
            get(spec['url'], dest)
        if ok():
            return
        print('  sha256 changed at source; using the mirror', flush=True)
    except Exception as e:
        print('  source unavailable (%s); using the mirror' % e, flush=True)
    get(MIRROR + spec['name'], dest)
    assert ok(), 'mirror copy does not match the pinned sha256: ' + spec['name']


def run(cmd, cwd=None):
    print('+', ' '.join(cmd), flush=True)
    subprocess.run(cmd, cwd=cwd, check=True)


def fetch(src):
    dest = os.path.join(DL, src['name'])
    if not os.path.isdir(os.path.join(dest, '.git')):
        os.makedirs(dest, exist_ok=True)
        run(['git', 'init', '-q'], cwd=dest)
        run(['git', 'remote', 'add', 'origin', src['url']], cwd=dest)
        run(['git', 'config', 'core.sparseCheckout', 'true'], cwd=dest)
    with open(os.path.join(dest, '.git', 'info', 'sparse-checkout'), 'w', encoding='utf-8') as f:
        f.write('\n'.join(src['paths']) + '\n')
    run(['git', 'fetch', '-q', '--depth', '1', '--filter=blob:none', 'origin', src['commit']], cwd=dest)
    run(['git', 'checkout', '-q', src['commit']], cwd=dest)


if __name__ == '__main__':
    os.makedirs(DL, exist_ok=True)
    for s in SOURCES:
        fetch(s)
    for f in FILES:
        fetch_file(f)
    print('sources ready in', DL)
