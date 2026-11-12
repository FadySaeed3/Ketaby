"""Save the Jesuit (Catholic) Arabic Bible pages from arabiccatholicbible.com for personal offline use.

Run:  python fetch_jesuit.py
Needs only Python 3 (no extra packages). It waits 4 seconds between pages, saves every page
as raw HTML in a folder called jesuit_html, and at the end makes jesuit_html.zip next to this file.
If it stops halfway, run it again: pages already saved are skipped.
"""
import os, re, time, urllib.request, urllib.parse, zipfile

BASE = "https://arabiccatholicbible.com"
PATHS = """gen ex lev num deut josh judg ruth 1sam 2sam 1kings 2kings ezra 1chron 2chron neh tob jth est
1macc 2macc job ps prov eccles song wisdofsol sir isa jer lam bar ezek dan hos joel amos obad jonah mic nah
hab zeph hag zech zech-2 mal matt mac luke john acts rom 1cor 2cor gal eph phil col 1thess 2thess 1tim 2tim
titus philem heb james 1pet 2pet 1john 2john 3john jude rev""".split()
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "jesuit_html")
HEADERS = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36",
           "Accept-Language": "ar,en;q=0.8"}
DELAY = 4


def get(url):
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers=HEADERS)
            with urllib.request.urlopen(req, timeout=60) as r:
                return r.read()
        except Exception as e:
            print("   retry", attempt + 1, "-", e)
            time.sleep(10 * (attempt + 1))
    return None


def save(name, url):
    path = os.path.join(OUT, name + ".html")
    if os.path.exists(path) and os.path.getsize(path) > 5000:
        print("skip", name)
        return open(path, "rb").read()
    print("get ", url)
    data = get(url)
    if data:
        open(path, "wb").write(data)
        print("   ok", len(data) // 1024, "KB")
    else:
        print("   FAILED", url)
    time.sleep(DELAY)
    return data


def main():
    os.makedirs(OUT, exist_ok=True)
    save("_home", BASE + "/")
    for p in PATHS:
        save("book_" + p, f"{BASE}/project/{p}/")
    # glossary index and every glossary entry linked from it
    g = save("_glossary", BASE + "/glossary/")
    if g:
        links = sorted(set(re.findall(r'href="(https?://arabiccatholicbible\.com/glossary/[^"#?]+)"', g.decode("utf-8", "ignore"))))
        for u in links:
            slug = urllib.parse.unquote(u.rstrip("/").rsplit("/", 1)[-1])
            if slug and slug != "glossary":
                save("glossary_" + re.sub(r'[\\/:*?"<>|]', "_", slug)[:80], u)
    zpath = OUT + ".zip"
    with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
        for f in sorted(os.listdir(OUT)):
            z.write(os.path.join(OUT, f), f)
    print("\nDone:", zpath, os.path.getsize(zpath) // 1024, "KB")


if __name__ == "__main__":
    main()
