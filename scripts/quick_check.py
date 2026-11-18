import ssl, urllib.request, sys

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

for url in sys.argv[1:]:
    try:
        req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "text/html,*/*", "Accept-Language": "tr,en;q=0.9"})
        with urllib.request.urlopen(req, timeout=25, context=ctx) as r:
            body = r.read(200000)
            final = r.geturl()
            print(f"{r.status} {len(body):>8}B  {url}  -> {final}")
    except Exception as e:
        print(f"ERR        {url}  {str(e)[:90]}")
