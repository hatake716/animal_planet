#!/usr/bin/env python3
"""
生成した species.json を検証する。
- 座標の範囲、地域マスク、解説・生息地の有無と長さ、重複する和名、写真ファイルの存在
- 日本語版 Wikipedia の記事名が存在するか(API で再確認、リダイレクトは警告)
使い方: validate.py <assets ディレクトリ>
"""
import os
import re
import sys
from collections import Counter
from common import get_json, load, chunks

MAP_PAT = re.compile(r"(map|range|distribution|distribu|verbreitung|\barea\b|habitat|locator|karte|\bmapa\b|carte|\bdist\b|distmap|występowanie|分布)", re.I)
GROUPS = ["哺乳類", "鳥類", "爬虫類", "両生類", "魚類", "無脊椎動物"]

API = "https://ja.wikipedia.org/w/api.php"


def main():
    assets = sys.argv[1]
    data = load(os.path.join(assets, "species.json"))
    regions = data["regions"]
    rows = data["entries"]
    problems = []
    names = Counter(r[1] for r in rows)
    for r in rows:
        id_, name, _, sci, title, _, lat, lon, place, group, status, mask, imp, desc, habitat, threats, order, family, img, author, lic, lic_url = r[:22]
        if img and MAP_PAT.search(img):
            problems.append(f"{id_} {name}: image looks like a map: {img}")
        if img and lic not in ("CC0 1.0", "Public domain") and not author:
            problems.append(f"{id_} {name}: attribution license without author ({lic})")
        if img and lic and not re.match(r"^(CC0 1\.0|Public domain|CC BY(-SA)? [0-9.]+( [a-z]{2})?)$", lic):
            problems.append(f"{id_} {name}: unexpected license label {lic}")
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
    # 分類 6 区分・地域全部に最低 1 種あること(絞り込みチップが常に 0 件にならない)
    for gi, g in enumerate(GROUPS):
        if not any(r[9] == gi for r in rows):
            problems.append(f"no species in group {g}")
    for i, rg in enumerate(regions):
        if not any(r[11] >> i & 1 for r in rows):
            problems.append(f"no species in region {rg}")
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
