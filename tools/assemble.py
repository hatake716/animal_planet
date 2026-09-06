#!/usr/bin/env python3
"""
エージェントの出力(out/{i}.json)と修正(out/{i}.fact.fix.json / out/{i}.geo.fix.json)を統合し、
アプリ用の species.json と写真(assets/img/{id}.jpg)を生成する。
入力: species_index.json, wd_species.json, image_meta.json, images/{qid}.orig, out/
出力: <assets>/species.json, <assets>/img/{id}.jpg, credits.json(写真クレジット一覧)
使い方: assemble.py <assets ディレクトリ>
"""
import glob
import os
import subprocess
import sys
from common import load, save

REGIONS = ["日本", "東アジア", "東南アジア", "南アジア", "中央アジア・西アジア", "ヨーロッパ", "アフリカ",
           "北アメリカ", "中央アメリカ・カリブ", "南アメリカ", "オセアニア", "海洋", "北極・南極"]
GROUP_CODE = {"哺乳類": 0, "鳥類": 1, "爬虫類": 2, "両生類": 3, "魚類": 4, "無脊椎動物": 5}
MAX_SIDE = 640


def convert_image(src, dst):
    """長辺 MAX_SIDE 以下の JPEG に変換する(ffmpeg)。"""
    if os.path.exists(dst):
        return True
    vf = f"scale='if(gt(iw,ih),min({MAX_SIDE},iw),-2)':'if(gt(iw,ih),-2,min({MAX_SIDE},ih))'"
    r = subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", src, "-vf", vf, "-q:v", "5", "-pix_fmt", "yuvj420p", dst],
                       capture_output=True, text=True)
    if r.returncode != 0:
        print(f"  ffmpeg failed {src}: {r.stderr.strip()[:200]}", file=sys.stderr)
        return False
    return True


def main():
    assets = sys.argv[1] if len(sys.argv) > 1 else "assets_out"
    index = load("species_index.json")
    wd = {s["qid"]: s for s in load("wd_species.json")}
    imgs = load("image_meta.json", {})
    # エージェント出力の統合
    edited = {}
    for f in sorted(glob.glob("out/*.json")):
        base = os.path.basename(f)
        if ".fix." in base:
            continue
        for it in load(f):
            edited[it["id"]] = it
    fixes = 0
    for f in sorted(glob.glob("out/*.fact.fix.json")) + sorted(glob.glob("out/*.geo.fix.json")):
        for fx in load(f) or []:
            e = edited.get(fx.get("id"))
            if e is None:
                continue
            for k, v in fx.items():
                if k in ("id", "reason"):
                    continue
                if v is None or v == "":
                    if k == "threats":
                        e[k] = ""
                    continue
                e[k] = v
                fixes += 1
    print(f"edited: {len(edited)}/{len(index)}, fixes applied: {fixes}")

    os.makedirs(os.path.join(assets, "img"), exist_ok=True)
    entries = []
    credits = []
    missing = []
    skipped = 0
    for it in index:
        e = edited.get(it["id"])
        if e is None:
            missing.append(it["id"])
            continue
        if e.get("nameOk") is False:
            skipped += 1
            print(f"  skip (nameOk=false): {it['id']} {it['name']} {e.get('note', '')}", file=sys.stderr)
            continue
        s = wd[it["qid"]]
        lat = float(e["lat"])
        lon = float(e["lon"])
        if not (-90 <= lat <= 90 and -180 <= lon <= 180):
            print(f"  bad coords {it['id']} {it['name']}: {lat},{lon}", file=sys.stderr)
            continue
        regions = [r for r in e.get("regions", []) if isinstance(r, int) and 0 <= r < len(REGIONS)]
        mask = 0
        for r in regions:
            mask |= 1 << r
        if mask == 0:
            print(f"  no region {it['id']} {it['name']}", file=sys.stderr)
        m = imgs.get(it["qid"])
        img_file = author = lic = lic_url = ""
        if m and m.get("ok") and os.path.exists(f"images/{it['qid']}.orig"):
            dst = os.path.join(assets, "img", f"{it['id']}.jpg")
            if convert_image(f"images/{it['qid']}.orig", dst):
                img_file = m["file"]
                author = m.get("artist") or ""
                lic = m.get("license") or m.get("licenseShort") or ""
                lic_url = m.get("licenseUrl") or ""
                credits.append({"id": it["id"], "name": it["name"], "file": img_file, "author": author, "license": lic, "licenseUrl": lic_url, "url": m.get("descUrl", "")})
        yomi = e.get("yomi") or ""
        order_ja = (e.get("orderJa") or "").strip() or it["orderJa"]
        family_ja = (e.get("familyJa") or "").strip() or it["familyJa"]
        row = [
            it["id"], it["name"], "|".join(it["aliases"]), it["sci"], it["jaTitle"], it["enTitle"] or "",
            round(lat, 4), round(lon, 4), (e.get("place") or "")[:30],
            GROUP_CODE[it["group"]], it["status"], mask, int(e.get("importance") or 1),
            (e.get("desc") or "").strip(), (e.get("habitat") or "").strip(), (e.get("threats") or "").strip(),
            order_ja, family_ja, img_file, author, lic, lic_url,
        ]
        if yomi and yomi != it["name"]:
            row.append(yomi)
        entries.append(row)
    # アプリ同梱用はコンパクトに書く(インデントなし)
    import json
    with open(os.path.join(assets, "species.json"), "w", encoding="utf-8") as f:
        json.dump({"regions": REGIONS, "entries": entries}, f, ensure_ascii=False, separators=(",", ":"))
    save("credits.json", credits)
    print(f"written {len(entries)} entries ({len(credits)} with photo); missing: {len(missing)} {missing[:20]}; skipped: {skipped}")


if __name__ == "__main__":
    main()
