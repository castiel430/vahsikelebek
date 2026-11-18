import urllib.request, ssl, re

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/131.0.0.0"}

body = urllib.request.urlopen(
    urllib.request.Request("https://ultraiz.net/", headers=UA), timeout=25, context=ctx
).read().decode("utf-8", "ignore")
slugs = re.findall(r"/filmler9/[^\"'\s]+/", body)
print("filmler9 links:", len(slugs), slugs[:5])
kategori = re.findall(r"/kategori/[^\"'\s]+/", body)
print("kategori links:", len(kategori), kategori[:5])

try:
    r = urllib.request.urlopen(
        urllib.request.Request("https://ultraiz.net/wp-json/wp/v2/search?search=matrix&per_page=3", headers=UA),
        timeout=25, context=ctx,
    )
    print("wp-json search HTTP", r.status)
    print(r.read()[:300])
except Exception as e:
    print("wp-json FAIL", e)
