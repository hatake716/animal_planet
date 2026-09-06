#!/usr/bin/env python3
"""
各種の写真を Wikimedia Commons から選び、ライセンス情報を取得し、640px のサムネイルを保存する。
候補の優先順: Wikidata P18 → 日本語版記事の代表画像 → 英語版記事の代表画像 → Commons カテゴリ内の画像。
採用条件(絶対条件: 第三者利用が自由な素材のみ):
  - Commons 上の画像で、ライセンスが CC0 / パブリックドメイン / CC BY / CC BY-SA (いずれのバージョンも可)
  - GFDL のみ・不明・Fair use・非商用(NC)・改変禁止(ND) は不採用
  - ラスター画像(jpeg/png/tiff/webp)。svg/gif は不採用
入力: wd_species.json, wiki_extracts.json  出力: image_meta.json, images/{qid}.jpg(元サムネイル)
"""
import html
import os
import re
import sys
from common import get, get_json, load, save, pmap, chunks

COMMONS = "https://commons.wikimedia.org/w/api.php"
OK_MIME = {"image/jpeg", "image/png", "image/tiff", "image/webp"}
FIELDS = "LicenseShortName|License|Artist|Credit|UsageTerms|AttributionRequired|Copyrighted|Restrictions|LicenseUrl|ImageDescription"


def strip_tags(s):
    s = re.sub(r"<br\s*/?>", " ", s or "")
    s = re.sub(r"<[^>]+>", "", s)
    s = html.unescape(s)
    return re.sub(r"\s+", " ", s).strip()


def license_ok(short, usage, lic):
    """(採用可否, 正規化した短い表記)"""
    s = (short or "").strip()
    u = (usage or "").lower()
    l = (lic or "").lower()
    low = s.lower()
    if not s:
        return False, ""
    if "nc" in l.split("-") or "noncommercial" in u or "non-commercial" in u or "-nc" in low:
        return False, s
    if "-nd" in low or "nd" in l.split("-") or "no derivative" in u:
        return False, s
    if "fair use" in low or "non-free" in low or "copyrighted" == low:
        return False, s
    if low in ("cc0", "cc zero") or low.startswith("cc0"):
        return True, "CC0 1.0"
    if "public domain" in low or low.startswith("pd") or "pd-" in l:
        return True, "Public domain"
    if low.startswith("cc by-sa") or low.startswith("cc-by-sa"):
        return True, s.replace("CC-BY-SA", "CC BY-SA")
    if low.startswith("cc by") or low.startswith("cc-by"):
        return True, s.replace("CC-BY", "CC BY")
    if low.startswith("gfdl") or "gnu free documentation" in u:
        # GFDL 単独は不採用(通常は CC BY-SA と併記されており、その場合 short name は CC 側になる)
        return False, s
    if "attribution" in u and "share" in u:
        return True, "CC BY-SA"
    return False, s


def imageinfo(files):
    d = get_json(COMMONS, {
        "action": "query", "format": "json", "formatversion": 2,
        "titles": "|".join("File:" + f for f in files),
        "prop": "imageinfo", "iiprop": "extmetadata|url|size|mime", "iiurlwidth": 640,
        "iiextmetadatafilter": FIELDS,
    })
    out = {}
    if not d:
        return out
    q = d.get("query", {})
    norm = {r["from"]: r["to"] for r in q.get("normalized", [])}
    pages = {p["title"]: p for p in q.get("pages", [])}
    for f in files:
        t = norm.get("File:" + f, "File:" + f)
        p = pages.get(t)
        if not p or p.get("missing") or not p.get("imageinfo"):
            out[f] = None
            continue
        ii = p["imageinfo"][0]
        em = {k: v.get("value", "") for k, v in ii.get("extmetadata", {}).items()}
        ok, lic = license_ok(em.get("LicenseShortName"), em.get("UsageTerms"), em.get("License"))
        out[f] = {
            "file": t[5:], "mime": ii.get("mime", ""), "width": ii.get("width", 0), "height": ii.get("height", 0),
            "thumb": ii.get("thumburl", ""), "descUrl": ii.get("descriptionurl", ""),
            "artist": strip_tags(em.get("Artist", ""))[:120], "credit": strip_tags(em.get("Credit", ""))[:120],
            "licenseShort": em.get("LicenseShortName", ""), "license": lic, "licenseUrl": em.get("LicenseUrl", ""),
            "usageTerms": em.get("UsageTerms", ""), "restrictions": em.get("Restrictions", ""),
            "ok": ok and ii.get("mime", "") in OK_MIME and bool(ii.get("thumburl")),
        }
    return out


def category_files(cat, limit=8):
    d = get_json(COMMONS, {
        "action": "query", "format": "json", "formatversion": 2, "list": "categorymembers",
        "cmtitle": "Category:" + cat, "cmtype": "file", "cmlimit": limit,
    })
    if not d:
        return []
    return [m["title"][5:] for m in d.get("query", {}).get("categorymembers", []) if m["title"].lower().endswith((".jpg", ".jpeg", ".png"))]


def main():
    species = load("wd_species.json")
    extracts = load("wiki_extracts.json", {})
    meta = load("image_meta.json", {})
    os.makedirs("images", exist_ok=True)
    # 候補ファイル名の列挙
    cands = {}
    for s in species:
        if s["qid"] in meta and meta[s["qid"]] is not None and meta[s["qid"]].get("ok"):
            continue
        c = []
        if s["img"]:
            c.append(s["img"])
        for lang in ("ja", "en"):
            pi = extracts.get(s["qid"], {}).get(lang, {}).get("pageimage")
            if pi and pi not in c:
                c.append(pi)
        cands[s["qid"]] = [x.replace("_", " ") for x in c]
    print(f"species needing image: {len(cands)}")
    # 1. まとめて imageinfo
    allfiles = sorted({f for c in cands.values() for f in c})
    info = {}
    batches = list(chunks(allfiles, 40))
    for i, res in enumerate(pmap(imageinfo, batches, workers=3)):
        info.update(res)
        if i % 10 == 0:
            print(f"  imageinfo {i + 1}/{len(batches)}")
    chosen = {}
    need_cat = []
    for s in species:
        q = s["qid"]
        if q not in cands:
            continue
        pick = next((info[f] for f in cands[q] if info.get(f) and info[f]["ok"]), None)
        if pick:
            chosen[q] = pick
        elif s["commonsCat"]:
            need_cat.append(s)
        else:
            chosen[q] = None
    # 2. カテゴリから探す
    print(f"fallback to commons category: {len(need_cat)}")
    catfiles = pmap(lambda s: category_files(s["commonsCat"]), need_cat, workers=3)
    extra = sorted({f for fs in catfiles for f in fs})
    for res in pmap(imageinfo, list(chunks(extra, 40)), workers=3):
        info.update(res)
    for s, fs in zip(need_cat, catfiles):
        pick = next((info[f] for f in fs if info.get(f) and info[f]["ok"]), None)
        chosen[s["qid"]] = pick
    meta.update(chosen)
    save("image_meta.json", meta)
    # 3. サムネイルのダウンロード
    dl = [(q, m) for q, m in meta.items() if m and m.get("ok") and not os.path.exists(f"images/{q}.orig")]
    print(f"download: {len(dl)}")

    def fetch_one(qm):
        q, m = qm
        try:
            data = get(m["thumb"], binary=True, timeout=120)
        except Exception as e:  # noqa: BLE001
            print(f"  download failed {q} {m['file']}: {e}", file=sys.stderr)
            return False
        if not data:
            return False
        with open(f"images/{q}.orig", "wb") as f:
            f.write(data)
        return True

    done = sum(1 for ok in pmap(fetch_one, dl, workers=4) if ok)
    rej = {}
    for f, m in info.items():
        if m and not m["ok"]:
            rej[m["licenseShort"] or m["mime"]] = rej.get(m["licenseShort"] or m["mime"], 0) + 1
    print(f"downloaded {done}. species with image: {sum(1 for q in meta if meta[q])}/{len(species)}")
    print("rejected licenses/mimes:", sorted(rej.items(), key=lambda x: -x[1])[:15])


if __name__ == "__main__":
    main()
