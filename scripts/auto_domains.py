#!/usr/bin/env python3
"""
Sağlayıcı domainlerini denetler ve (isteğe bağlı) yeni domaine taşır.

Neden ayrı bir araç?
  * Domainler Kotlin kaynağında sabit (`override var mainUrl = "..."`).
  * GitHub Actions Birleşik Krallık'ta çalışır; BTK'nın Türkiye'de yaptığı
    DNS zehirleme / SNI engellemesini GÖREMEZ. Bu yüzden denetim, kullanıcının
    kendi ağından (bu makineden) çalışmalıdır.
  * DNS zehirlemesi 195.175.254.2 / 2a01:358:4014::/48 adreslerine düşer;
    bu yüzden gerçek adresler DNS-over-HTTPS ile ayrıca çözülür.

Kullanım:
  python scripts/auto_domains.py                  # sadece rapor
  python scripts/auto_domains.py --apply          # yönlenen domainleri uygula
  python scripts/auto_domains.py --only DiziBox   # tek modül
  python scripts/auto_domains.py --json           # JSON rapor

`--apply` yalnızca eski domain YENİ bir domaine yönleniyorsa ve yeni domain
gerçekten içerik döndürüyorsa çalışır; modülün `build.gradle.kts` sürümünü
otomatik artırır (aksi halde uygulama güncellemeyi görmez).
"""

from __future__ import annotations

import argparse
import json
import re
import socket
import ssl
import sys
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field, asdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SKIP_DIRS = {"gradle", "scripts", ".github", ".git", ".gradle", ".kotlin", "build"}
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
POISON_PREFIXES = ("195.175.254.", "2a01:358:4014")
MAIN_URL_RE = re.compile(
    r'override\s+(?:var|val)\s+mainUrl\s*(?::\s*String)?\s*=\s*"([^"]+)"')
VERSION_RE = re.compile(r"(?m)^version\s*=\s*(\d+)\s*$")


@dataclass
class Module:
    name: str
    url: str
    source: Path
    gradle: Path


@dataclass
class Result:
    module: str
    url: str
    dns: str = "?"
    real_ip: str = ""
    http: str = ""
    note: str = ""
    new_url: str = ""
    ok: bool = False
    applies: bool = False


def discover(only: str | None = None) -> list[Module]:
    modules: list[Module] = []
    for d in sorted(ROOT.iterdir()):
        if not d.is_dir() or d.name in SKIP_DIRS or d.name.startswith("."):
            continue
        if only and d.name.lower() != only.lower():
            continue
        found = None
        for kt in d.rglob("*.kt"):
            try:
                text = kt.read_text(encoding="utf-8", errors="ignore")
            except OSError:
                continue
            if "MainAPI()" not in text:
                continue
            m = MAIN_URL_RE.search(text)
            if m:
                found = (m.group(1).strip(), kt)
                break
        if not found:
            continue
        gradle = d / "build.gradle.kts"
        modules.append(Module(d.name, found[0], found[1], gradle))
    return modules


def doh_lookup(host: str) -> tuple[str, list[str]]:
    url = "https://dns.google/resolve?" + urllib.parse.urlencode(
        {"name": host, "type": "A"})
    try:
        with urllib.request.urlopen(url, timeout=12) as r:
            data = json.load(r)
    except Exception as exc:  # noqa: BLE001
        return f"DoH-ERR {exc}", []
    status = data.get("Status", -1)
    ips = [a["data"] for a in data.get("Answer", []) if a.get("type") == 1]
    if status != 0 or not ips:
        return f"NXDOMAIN({status})", ips
    return "ok", ips


def system_ips(host: str) -> list[str]:
    try:
        return sorted({ai[4][0] for ai in socket.getaddrinfo(host, 443)})
    except OSError:
        return []


def http_get(host: str, path: str = "/") -> tuple[str, str, str]:
    """
    (status, son_url, gövde) — uygulamayla aynı yolu izlemek için sistem DNS'i
    kullanır (urllib). Yönlendirmeleri kendisi takip eder.
    """
    url = f"https://{host}{path}"
    request = urllib.request.Request(url, headers={
        "User-Agent": UA,
        "Accept": "text/html,application/json;q=0.9,*/*;q=0.8",
        "Accept-Language": "tr,en;q=0.9",
    })
    ctx = ssl.create_default_context()
    try:
        with urllib.request.urlopen(request, timeout=15, context=ctx) as response:
            body = response.read(60000).decode("utf-8", "ignore")
            return f"HTTP {response.status}", response.geturl(), body
    except urllib.error.HTTPError as exc:
        body = ""
        try:
            body = exc.read(30000).decode("utf-8", "ignore")
        except Exception:  # noqa: BLE001
            pass
        return f"HTTP {exc.code}", exc.geturl() or url, body
    except Exception as exc:  # noqa: BLE001
        return f"CONN-ERR {exc}", "", ""


def host_of(url: str) -> str:
    if "//" not in url:
        url = "https://" + url
    return (urllib.parse.urlparse(url).hostname or "").lower()


def check(module: Module) -> Result:
    host = host_of(module.url)
    res = Result(module=module.name, url=module.url)
    sys_ips = system_ips(host)
    poisoned = any(ip.startswith(POISON_PREFIXES) for ip in sys_ips)
    res.dns = "POISON" if poisoned else ("ok" if sys_ips else "DNSFAIL")

    res.real_ip = (doh_lookup(host)[1] or [""])[0]
    status_line, final_url, body = http_get(host)
    res.http = status_line

    if status_line.startswith(("HTTP 2", "HTTP 3")):
        final_host = host_of(final_url) if final_url else host
        if final_host and final_host != host:
            res.new_url = f"https://{final_host}/"
            res.note = f"yönlendi → {res.new_url}"
            res.applies = len(body) > 3000
            res.ok = res.applies
            return res
        low = body.lower()
        if "just a moment" in low or "challenge-platform" in low:
            res.note = "Cloudflare challenge (uygulamada CfBypass çözer)"
        elif "erisime" in low and "engel" in low:
            res.note = "BTK engel sayfası"
        elif len(body) < 1500:
            res.note = f"boş sayfa ({len(body)} B)"
        else:
            res.ok = True
        return res

    if "CONN-ERR" in status_line:
        dns_status, _ = doh_lookup(host)
        if dns_status.startswith("NXDOMAIN"):
            res.note = "domain yok (NXDOMAIN)"
            res.dns = dns_status
        elif res.dns == "POISON":
            res.note = "DNS zehirli (BTK) — gerçek adres de açılmıyor (SNI engeli)"
        else:
            res.note = "bağlantı kurulamadı (site kapalı ya da engelli)"
        return res

    res.note = status_line + (" · Cloudflare 403 (uygulamada CfBypass çözer)"
                              if status_line == "HTTP 403" else "")
    return res


def bump_version(gradle: Path) -> int | None:
    if not gradle.exists():
        return None
    text = gradle.read_text(encoding="utf-8")
    m = VERSION_RE.search(text)
    if not m:
        return None
    old = int(m.group(1))
    text = VERSION_RE.sub(f"version = {old + 1}", text, count=1)
    gradle.write_text(text, encoding="utf-8")
    return old + 1


def apply_change(module: Module, result: Result) -> str:
    old_host = host_of(module.url)
    new_host = host_of(result.new_url)
    if not old_host or not new_host:
        return "atlandı (host çözülemedi)"
    scheme = urllib.parse.urlparse(result.new_url).scheme or "https"
    changed_files = 0
    for kt in module.source.parent.parent.parent.parent.rglob("*.kt"):
        try:
            text = kt.read_text(encoding="utf-8")
        except OSError:
            continue
        if old_host not in text:
            continue
        updated = text.replace(old_host, new_host)
        if updated != text:
            kt.write_text(updated, encoding="utf-8")
            changed_files += 1
    if changed_files == 0:
        return "değişiklik yok"
    new_version = bump_version(module.gradle)
    return f"{changed_files} dosya güncellendi, sürüm → {new_version}"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Sağlayıcı domain denetimi/güncellemesi")
    parser.add_argument("--apply", action="store_true",
                        help="Yönlenen domainleri kaynağa yaz ve sürümü artır")
    parser.add_argument("--only", help="Yalnızca bu modülü denetle")
    parser.add_argument("--json", action="store_true", help="JSON rapor yaz")
    parser.add_argument("--workers", type=int, default=12)
    args = parser.parse_args(argv)

    modules = discover(args.only)
    if not modules:
        print("Modül bulunamadı.", file=sys.stderr)
        return 2

    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        results = list(pool.map(check, modules))

    applied: list[str] = []
    if args.apply:
        for module, result in zip(modules, results):
            if result.applies:
                print(f"UYGULA {module.name}: {module.url} → {result.new_url}")
                print("        " + apply_change(module, result))
                applied.append(module.name)

    width = max(len(r.module) for r in results)
    print(f"\n{'Modül'.ljust(width)}  {'DNS':<8} {'HTTP':<22} Not")
    print("-" * (width + 70))
    for r in results:
        flag = "OK " if r.ok else "!! "
        print(f"{flag}{r.module.ljust(width)}  {r.dns:<8} {r.http[:21]:<22} {r.note}")

    ok_count = sum(1 for r in results if r.ok)
    poison = [r.module for r in results if r.dns == "POISON"]
    dead = [r.module for r in results if "NXDOMAIN" in r.http or "NXDOMAIN" in r.dns]
    blocked = [r.module for r in results if "CONN-ERR" in r.http]
    movable = [r.module for r in results if r.applies]
    print(f"\n{len(results)} modül · {ok_count} çalışıyor · "
          f"{len(poison)} DNS zehirli · {len(dead)} domain yok · "
          f"{len(blocked)} bağlantı engelli · {len(movable)} taşınabilir")
    if args.apply and applied:
        print("Uygulanan: " + ", ".join(applied))
    elif movable:
        print("Taşımak için: python scripts/auto_domains.py --apply")

    report = Path(__file__).with_name("domain_report.json")
    payload = []
    for module, result in zip(modules, results):
        item = asdict(result)
        item["source"] = str(module.source.relative_to(ROOT))
        payload.append(item)
    report.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    if args.json:
        print(json.dumps(payload, ensure_ascii=False, indent=2))
    else:
        print(f"Rapor: {report}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
