"""Download the pinned open sources into data-pipeline/downloads/ (git-ignored).

Only the folders we use are checked out (sparse), at a fixed commit, so every
build sees exactly the same data.
"""
import os, subprocess, sys

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
    print('sources ready in', DL)
