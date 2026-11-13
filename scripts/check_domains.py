#!/usr/bin/env python3
"""
Web sitelerinin domain/adres değişikliklerini kontrol eden basit araç.

Kullanım:
  python scripts/check_domains.py
  python scripts/check_domains.py --file scripts/domains.json
  python scripts/check_domains.py --timeout 15

domains.json formatı:
{
  "sites": [
    {"name": "Ornek", "url": "https://ornek.com/"}
  ]
}
"""

from __future__ import annotations

import argparse
import json
import socket
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import Request, urlopen


@dataclass
class CheckResult:
    name: str
    input_url: str
    input_host: str
    final_url: str | None
    final_host: str | None
    status: str
    ok: bool
    changed: bool
    note: str


def load_sites(path: Path) -> list[dict[str, str]]:
    data: Any = json.loads(path.read_text(encoding="utf-8"))
    sites = data.get("sites", []) if isinstance(data, dict) else []
    out: list[dict[str, str]] = []
    for item in sites:
        if not isinstance(item, dict):
            continue
        name = str(item.get("name", "")).strip() or "(isimsiz)"
        url = str(item.get("url", "")).strip()
        if url:
            out.append({"name": name, "url": url})
    return out


def normalize_url(url: str) -> str:
    url = url.strip()
    if not urlparse(url).scheme:
        url = "https://" + url
    return url


def check_site(name: str, url: str, timeout: float) -> CheckResult:
    input_url = normalize_url(url)
    input_host = (urlparse(input_url).hostname or "").lower()
    req = Request(
        input_url,
        headers={
            "User-Agent": "Mozilla/5.0 (compatible; vahsikelebek-domain-check/1.0)",
            "Accept": "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8",
        },
        method="GET",
    )
    try:
        with urlopen(req, timeout=timeout) as resp:
            final_url = resp.geturl()
            status = getattr(resp, "status", 200)
            final_host = (urlparse(final_url).hostname or "").lower()
            changed = bool(input_host and final_host and input_host != final_host)
            note = "Domain değişti" if changed else "Aynı domain"
            return CheckResult(
                name=name,
                input_url=input_url,
                input_host=input_host,
                final_url=final_url,
                final_host=final_host,
                status=str(status),
                ok=200 <= int(status) < 400,
                changed=changed,
                note=note,
            )
    except HTTPError as e:
        final_url = e.geturl() if hasattr(e, "geturl") else None
        final_host = (urlparse(final_url).hostname or "").lower() if final_url else None
        changed = bool(input_host and final_host and input_host != final_host)
        note = f"HTTP hata: {e.code}"
        if changed:
            note += " (farklı domaine yönlenmiş olabilir)"
        return CheckResult(name, input_url, input_host, final_url, final_host, str(e.code), False, changed, note)
    except URLError as e:
        reason = getattr(e, "reason", e)
        return CheckResult(name, input_url, input_host, None, None, "ERR", False, False, f"Bağlantı hatası: {reason}")
    except socket.timeout:
        return CheckResult(name, input_url, input_host, None, None, "ERR", False, False, "Zaman aşımı")
    except Exception as e:
        return CheckResult(name, input_url, input_host, None, None, "ERR", False, False, f"Hata: {e}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Web sitelerinin domain değişikliklerini kontrol eder.")
    parser.add_argument(
        "--file",
        "-f",
        default=str(Path(__file__).with_name("domains.json")),
        help="Site listesinin bulunduğu JSON dosyası (varsayılan: scripts/domains.json)",
    )
    parser.add_argument("--timeout", "-t", type=float, default=15.0, help="İstek zaman aşımı (saniye)")
    parser.add_argument("--json", action="store_true", help="Sonuçları JSON olarak yazdır")
    args = parser.parse_args(argv)

    path = Path(args.file)
    if not path.exists():
        print(f"Hata: Dosya bulunamadı: {path}", file=sys.stderr)
        return 2

    sites = load_sites(path)
    if not sites:
        print("Kontrol edilecek site bulunamadı.", file=sys.stderr)
        return 1

    results = [check_site(s["name"], s["url"], args.timeout) for s in sites]

    if args.json:
        payload = [
            {
                "name": r.name,
                "input_url": r.input_url,
                "input_host": r.input_host,
                "final_url": r.final_url,
                "final_host": r.final_host,
                "status": r.status,
                "ok": r.ok,
                "changed": r.changed,
                "note": r.note,
            }
            for r in results
        ]
        print(json.dumps(payload, ensure_ascii=False, indent=2))
    else:
        name_w = max(len("Site"), *(len(r.name) for r in results))
        host_w = max(len("Giriş Domain"), *(len(r.input_host or "-") for r in results))
        print(f"{'Site'.ljust(name_w)}  {'Giriş Domain'.ljust(host_w)}  {'Son Domain'.ljust(host_w)}  Durum  Not")
        print("-" * (name_w + host_w * 2 + 40))
        for r in results:
            final_host = r.final_host or "-"
            status = r.status
            mark = "!" if r.changed else ("OK" if r.ok else "X")
            print(
                f"{r.name.ljust(name_w)}  {(r.input_host or '-').ljust(host_w)}  "
                f"{final_host.ljust(host_w)}  {mark}:{status:<4}  {r.note}"
            )
        changed = [r for r in results if r.changed]
        failed = [r for r in results if not r.ok]
        print()
        print(f"Toplam: {len(results)} | Değişen domain: {len(changed)} | Erişim sorunu: {len(failed)}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
