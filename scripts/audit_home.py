# -*- coding: utf-8 -*-
import os, re, sys, json, concurrent.futures, urllib.request, urllib.parse, ssl

ROOT = r"C:\Users\Akreditest\Desktop\cloudstream\vahsikelebek"
SKIP = {"gradle", "scripts", ".github", "TraktSync", "Watch2Movies"}
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

ctx = ssl.create_default_context(); ctx.check_hostname = False; ctx.verify_mode = ssl.CERT_NONE

def fetch(url, timeout=20):
    try:
        req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "tr-TR,tr;q=0.9,en-US;q=0.8", "Accept": "text/html,*/*;q=0.8"})
        x = urllib.request.urlopen(req, timeout=timeout, context=ctx)
        data = x.read(700000)
        return x.status, x.geturl(), data.decode("utf-8", "ignore")
    except urllib.error.HTTPError as e:
        return e.code, url, ""
    except Exception as e:
        return "ERR", url, str(e)

def extract_selectors(src):
    sels = []
    for m in re.finditer(r'select(?:First)?\("([^"]+)"', src):
        sels.append(m.group(1))
    for m in re.finditer(r"select(?:First)?\('([^']+)'", src):
        sels.append(m.group(1))
    # keep css-like only
    out = []
    for s in sels:
        if not s or s.startswith("http") or s.startswith("/") or "\\" in s:
            continue
        if not re.search(r"[.#a-zA-Z]", s):
            continue
        out.append(s)
    return list(dict.fromkeys(out))

def css_ok(sel):
    # strip jsoup-only pseudo classes unsupported by soupsieve
    s = sel
    s = re.sub(r":contains\([^)]*\)", "", s)
    s = re.sub(r":has\((?:\([^()]*\)|[^()])*\)", "", s)
    s = re.sub(r":not\([^)]*\)", "", s)
    s = re.sub(r"::?(text|ownText|regex|matches)\([^)]*\)", "", s)
    return s.strip()

def check_page(sels, html):
    try:
        from bs4 import BeautifulSoup
        import soupsieve as sv
        soup = BeautifulSoup(html, "html.parser")
    except Exception:
        soup = None
    results = {}
    for sel in sels:
        s = css_ok(sel)
        ok = None
        if soup is not None and s:
            try:
                ok = len(soup.select(s))
            except Exception:
                ok = None
        if ok is None:
            # token presence fallback
            toks = re.findall(r"[.#]([A-Za-z0-9_-]+)", sel)
            if toks:
                present = sum(1 for t in toks if t in html)
                ok = present if present else 0
            else:
                ok = html.count(sel.split()[0])
        results[sel] = ok
    return results

def find_main_url(src):
    m = re.search(r'override\s+(?:var|val)\s+mainUrl\s*(?::\s*String)?\s*=\s*"([^"]+)"', src)
    return m.group(1) if m else None

def process(mod):
    path = os.path.join(ROOT, mod)
    kt_files = []
    for dp, _, fs in os.walk(path):
        for f in fs:
            if f.endswith(".kt"):
                kt_files.append(os.path.join(dp, f))
    src = "\n".join(open(f, encoding="utf-8", errors="ignore").read() for f in kt_files)
    main_url = find_main_url(src)
    sels = extract_selectors(src)
    if not main_url:
        return mod, None, "no mainUrl", {}, {}
    st, fin, html = fetch(main_url)
    home = check_page(sels, html) if isinstance(html, str) else {}
    return mod, main_url, st, home, html

mods = [d for d in sorted(os.listdir(ROOT)) if os.path.isdir(os.path.join(ROOT, d)) and d not in SKIP and os.path.exists(os.path.join(ROOT, d, "build.gradle.kts"))]

with concurrent.futures.ThreadPoolExecutor(max_workers=10) as ex:
    results = list(ex.map(process, mods))

report = []
for mod, url, st, home, html in results:
    broken = [s for s, c in sorted(home.items()) if not c]
    report.append((mod, url, st, broken))
    print(f"{mod:<22} st={st} url={url}")
    for s in broken:
        print(f"    BROKEN homepage: {s}")

json.dump([{"module": m, "url": u, "status": s, "broken_homepage": b} for m, u, s, b in report],
          open(os.path.join(ROOT, "scripts", "audit_homepage.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=2)

print()
print("TABLO:")
for m, u, s, b in sorted(report, key=lambda r: -len(r[3])):
    print(f"{m:<22} {len(b):>2} broken")
