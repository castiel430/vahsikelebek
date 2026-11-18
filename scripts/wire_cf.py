import re, sys, io

files = [
    (r"CizgiMax\src\main\kotlin\com\cizgimax\CizgimaxProvider.kt", True),
    (r"HDFilmDiziIzle\src\main\kotlin\com\hdfilmdiziizle\HDFilmDiziIzleProvider.kt", True),
    (r"Kultfilmler\src\main\kotlin\com\kultfilmler\KultfilmlerProvider.kt", True),
    (r"TLCtr\src\main\kotlin\com\tlctr\TLCtrProvider.kt", True),
    (r"WebdramaTurkey2\src\main\kotlin\com\webdramaturkey2\WebdramaTurkey2Provider.kt", True),
    (r"FullHDFilmizlesene\src\main\kotlin\com\neowatch\FullHDFilmizlesene.kt", False),
    (r"KoreanTurk\src\main\kotlin\com\neowatch\KoreanTurk.kt", False),
    (r"FilmMakinesi\src\main\kotlin\com\neowatch\FilmMakinesi.kt", False),
    (r"HDFilmCehennemi\src\main\kotlin\com\neowatch\HDFilmCehennemi.kt", False),
]

for path, needs_import in files:
    src = open(path, encoding="utf-8").read()
    out, idx, added = [], 0, 0
    while True:
        i = src.find("app.get(", idx)
        if i == -1:
            out.append(src[idx:])
            break
        p0 = i + len("app.get(") - 1  # '(' pozisyonu
        depth, j = 0, p0
        in_str = None
        while j < len(src):
            c = src[j]
            if in_str:
                if c == in_str and src[j-1] != "\\":
                    in_str = None
            elif c in ('"', "'"):
                in_str = c
            elif c == "(":
                depth += 1
            elif c == ")":
                depth -= 1
                if depth == 0:
                    break
            j += 1
        span = src[p0:j]
        out.append(src[idx:i])
        if "interceptor" in span:
            out.append(src[i:j])
            idx = j
            continue
        before = src[i:j]
        b = before.rstrip()
        if b.endswith(","):
            out.append(before + " interceptor = CfBypass.interceptor,")
        else:
            out.append(before + ", interceptor = CfBypass.interceptor")
        added += 1
        idx = j
    newsrc = "".join(out)
    if needs_import and "import com.vahsikelebek.common.CfBypass" not in newsrc:
        m = None
        for m in re.finditer(r"(?m)^import [^\n]+\n", newsrc):
            pass
        if m:
            pos = m.end()
            newsrc = newsrc[:pos] + "import com.vahsikelebek.common.CfBypass\n" + newsrc[pos:]
    open(path, "w", encoding="utf-8", newline="\n").write(newsrc)
    print(f"{path}: {added} app.get() çağrısına interceptor eklendi")
