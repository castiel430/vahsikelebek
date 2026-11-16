import os, re, json, ssl, socket, urllib.request, urllib.error, concurrent.futures, time

ROOT = r"C:\Users\Akreditest\Desktop\cloudstream\vahsikelebek"
SKIP = {"gradle", "scripts", ".github", "TraktSync"}

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

providers = []
for d in sorted(os.listdir(ROOT)):
    p = os.path.join(ROOT, d)
    if not os.path.isdir(p) or d in SKIP:
        continue
    main_url = None
    src_file = None
    for dirpath, _, files in os.walk(p):
        for f in files:
            if not f.endswith(".kt"):
                continue
            fp = os.path.join(dirpath, f)
            try:
                txt = open(fp, encoding="utf-8").read()
            except Exception:
                continue
            if "MainAPI()" not in txt:
                continue
            m = re.search(r"override\s+(?:var|val)\s+mainUrl\s*(?::\s*String)?\s*=\s*\"([^\"]+)\"", txt)
            if m:
                main_url = m.group(1)
                src_file = fp
                break
        if main_url:
            break
    if main_url:
        providers.append({"module": d, "url": main_url, "src": os.path.relpath(src_file, ROOT)})
    else:
        providers.append({"module": d, "url": None, "src": None})

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE


def test(p):
    url = p["url"]
    if not url:
        return {**p, "status": "NOMAURL", "note": "mainUrl bulunamadi"}
    req = urllib.request.Request(url, headers={
        "User-Agent": UA,
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "tr-TR,tr;q=0.9,en;q=0.8",
    })
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=20, context=ctx) as r:
            body = r.read(200000).decode("utf-8", "ignore")
            st = r.status
            final = r.geturl()
            title = ""
            mt = re.search(r"<title[^>]*>(.*?)</title>", body, re.S | re.I)
            if mt:
                title = re.sub(r"\s+", " ", mt.group(1)).strip()[:60]
            note = title
            if st == 200 and len(body) < 1500:
                note += " | KISA ICERIK"
            if "cloudflare" in body.lower()[:5000] and ("just a moment" in body.lower() or "checking your browser" in body.lower()):
                note += " | CLOUDFLARE CHALLENGE"
            return {**p, "status": st, "final": final, "note": note, "ms": int((time.time()-t0)*1000), "len": len(body)}
    except urllib.error.HTTPError as e:
        return {**p, "status": e.code, "note": "HTTP " + str(e.code), "ms": int((time.time()-t0)*1000)}
    except urllib.error.URLError as e:
        return {**p, "status": "ERR", "note": str(getattr(e, "reason", e))[:90], "ms": int((time.time()-t0)*1000)}
    except Exception as e:
        return {**p, "status": "ERR", "note": str(e)[:90], "ms": int((time.time()-t0)*1000)}


with concurrent.futures.ThreadPoolExecutor(max_workers=16) as ex:
    results = list(ex.map(test, providers))

results.sort(key=lambda r: (str(r.get("status")), r["module"]))
json.dump(results, open(os.path.join(ROOT, "scripts", "health_report.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=2)

ok = [r for r in results if r.get("status") == 200]
bad = [r for r in results if r.get("status") != 200]
print(f"TOPLAM {len(results)}  | 200 OK: {len(ok)}  | SORUNLU: {len(bad)}")
print()
for r in results:
    print(f'{r["module"]:<22} {str(r.get("status")):<4} {(r.get("url") or "-")[:40]:<42} {r.get("note","")}')
