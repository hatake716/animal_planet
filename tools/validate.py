#!/usr/bin/env python3
"""
生成した species.json を検証する。
- 座標の範囲、地域マスク、解説・生息地の有無と長さ、重複する和名、写真ファイルの存在
- 日本語版 Wikipedia の記事名が存在するか(API で再確認、リダイレクトは警告)
使い方: validate.py <assets ディレクトリ>
"""
import os
import sys
from collections import Counter
from common import get_json, load, chunks

API = "https://ja.wikipedia.org/w/api.php"


def main():
    assets = sys.argv[1]
    data = load(os.path.join(assets, "species.json"))
    regions = data["regions"]
    rows = data["entries"]
    problems = []
    names = Counter(r[1] for r in rows)
    for r in rows:
        id_, name, _, sci, title, _, lat, lon, place, group, status, mask, imp, desc, habitat, threats, order, family, img = r[:19]
        if not (-90 <= lat <= 90 and -180 <= lon <= 180):
            problems.append(f"{id_} {name}: coords {lat},{lon}")
        if mask == 0 or mask >= (1 << len(regions)):
            problems.append(f"{id_} {name}: region mask {mask}")
        if not desc or len(desc) < 30:
            problems.append(f"{id_} {name}: desc short ({len(desc)})")
        if len(desc) > 200:
            problems.append(f"{id_} {name}: desc long ({len(desc)})")
        if not habitat:
            problems.append(f"{id_} {name}: habitat empty")
        if not place:
            problems.append(f"{id_} {name}: place empty")
        if status not in ("CR", "EN", "VU"):
            problems.append(f"{id_} {name}: status {status}")
        if not (1 <= imp <= 3):
            problems.append(f"{id_} {name}: importance {imp}")
        if img and not os.path.exists(os.path.join(assets, "img", f"{id_}.jpg")):
            problems.append(f"{id_} {name}: image file missing")
        if names[name] > 1:
            problems.append(f"{id_} {name}: duplicate name")
        if not title:
            problems.append(f"{id_} {name}: no wiki title")
    # 記事名の存在確認
    titles = sorted({r[4] for r in rows if r[4]})
    missing, redirects = [], []
    for batch in chunks(titles, 50):
        d = get_json(API, {"action": "query", "format": "json", "formatversion": 2, "titles": "|".join(batch), "redirects": 1})
        q = d.get("query", {})
        for p in q.get("pages", []):
            if p.get("missing"):
                missing.append(p["title"])
        for rd in q.get("redirects", []):
            redirects.append((rd["from"], rd["to"]))
    for t in missing:
        problems.append(f"wiki title missing: {t}")
    print(f"entries: {len(rows)}, with photo: {sum(1 for r in rows if r[18])}")
    print("groups:", Counter(r[9] for r in rows))
    print("status:", Counter(r[10] for r in rows))
    print("regions:", {regions[i]: sum(1 for r in rows if r[11] >> i & 1) for i in range(len(regions))})
    print(f"redirects (ok, but note): {len(redirects)}")
    for a, b in redirects[:20]:
        print(f"  {a} -> {b}")
    print(f"problems: {len(problems)}")
    for p in problems[:200]:
        print("  " + p)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
