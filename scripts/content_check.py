import os, re, ssl, urllib.request, concurrent.futures

ROOT = r"C:\Users\Akreditest\Desktop\cloudstream\vahsikelebek"
SKIP = {"gradle", "scripts", ".github", ".git", ".gradle", ".kotlin", "TraktSync"}
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

providers = []
for d in sorted(os.listdir(ROOT)):
    p = os.path.join(ROOT, d)
    if not os.path.isdir(p) or d in SKIP:
        continue
    main_url = None
    for dirpath, _, files in os.walk(p):
        for f in files:
            if not f.endswith(".kt"):
                continue
            fp = os.path.join(dirpath, f)
            try:
                txt = open(fp, encoding="utf-8", errors="ignore").read()
            except Exception:
                continue
            if "MainAPI()" not in txt:
                continue
            m = re.search(r'override\s+(?:var|val)\s+mainUrl\s*(?::\s*String)?\s*=\s*"([^"]+)"', txt)
            if m:
                main_url = m.group(1)
                break
        if main_url:
            break
    if main_url:
        providers.append((d, main_url))

print(f"{len(providers)} provider mainUrl bulundu\n")

def fetch(item):
    name, url = item
    try:
        req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "text/html,application/xhtml+xml", "Accept-Language": "tr,en;q=0.9"})
        ctx = ssl.create_default_context()
        with urllib.request.urlopen(req, timeout=20, context=ctx) as r:
            body = r.read()
            final = r.geturl()
            return (name, url, final, None, len(body), body)
    except Exception as e:
        return (name, url, None, str(e)[:70], 0, None)

results = []
with concurrent.futures.ThreadPoolExecutor(max_workers=16) as ex:
    for res in ex.map(fetch, providers):
        results.append(res)

def title_of(body):
    if not body:
        return ""
    m = re.search(rb"<title[^>]*>(.*?)</title>", body[:30000], re.I | re.S)
    if not m:
        return ""
    t = m.group(1).decode("utf-8", "ignore")
    return re.sub(r"\s+", " ", t).strip()[:70]

CHALLENGE = [b"just a moment", b"cf-chl", b"challenge-platform", b"px-captcha", b"attention required", b"/cdn-cgi/challenge"]
PARKED = [b"domain is for sale", b"buy this domain", b"sedo.com", b"parkingcrew", b"domain parking", b"parked by"]

ok, suspect = [], []
for name, url, final, err, size, body in results:
    title = title_of(body)
    low = (body or b"").lower()
    links = len(re.findall(rb"<a\s", body or b""))
    if err:
        tags = "ERR " + err
    elif size < 3000:
        tags = f"BOS size={size}"
    elif any(c in low for c in CHALLENGE):
        tags = "CF/CHALLENGE"
    elif any(c in low for c in PARKED):
        tags = "PARKED"
    elif title.lower().rstrip(".") in ("redirecting", "", "..."):
        tags = f"REDIRECT? title='{title}' size={size}"
    elif links < 10:
        tags = f"AZ-LINK links={links} size={size}"
    else:
        tags = ""
    if tags:
        suspect.append((name, url, title, tags))
    else:
        ok.append((name, links, size, title))
        print(f"OK   {name:<22} links={links:<5} size={size}")

print("\n===== SORUNLU / ŞÜPHELİ =====")
for name, url, title, tags in suspect:
    print(f"{name:<22} {tags:<34} {url}  title='{title}'")

print(f"\nSONUC: {len(ok)} saglam / {len(suspect)} supheli-bozuk")
