#!/usr/bin/env python3
"""
各種の写真を Wikimedia Commons から選び、ライセンス情報を取得し、640px のサムネイルを保存する。
候補の優先順: Wikidata P18 → 日本語版記事の代表画像 → 英語版記事の代表画像 → Commons カテゴリ内の画像(最大 12 枚)。
採用条件(絶対条件: 第三者利用が自由な素材のみ):
  - Commons 上の画像で、ライセンスが CC0 / パブリックドメイン / CC BY / CC BY-SA(いずれのバージョンも可)
  - GFDL のみ・不明・Fair use・非商用(NC)・改変禁止(ND)・汎用テンプレート(Attribution 等)・米国限定 PD は不採用
  - CC BY / CC BY-SA(帰属表示が必要)で作者が分からない画像は不採用(Own work はアップロード者を作者とする)
  - ラスター画像(jpeg/png/tiff/webp)。svg/gif は不採用
  - 分布図(ファイル名に map / range / distribution / area などを含む)は不採用。写真(JPEG)を PNG より優先する
入力: wd_species.json, wiki_extracts.json  出力: image_meta.json, images/{qid}.orig(元サムネイル)
imageinfo の結果は imageinfo_cache.json にキャッシュし、再実行時は未取得分だけ問い合わせる。
"""
import html
import os
import re
import sys
from common import get, get_json, load, save, pmap, chunks

COMMONS = "https://commons.wikimedia.org/w/api.php"
OK_MIME = {"image/jpeg", "image/png", "image/tiff", "image/webp"}
FIELDS = "LicenseShortName|License|Artist|Credit|UsageTerms|AttributionRequired|Copyrighted|Restrictions|LicenseUrl|ImageDescription"
MAP_PAT = re.compile(
    r"(map|range|distribution|distribu|verbreitung|\barea\b|habitat|locator|karte|\bmapa\b|carte|\bdist\b|distmap|"
    r"występowanie|分布|区域|areal|localis|location|\bloc\b|\bareas\b|\brange\b)",
    re.I,
)


def strip_tags(s):
    s = re.sub(r"<br\s*/?>", " ", s or "")
    s = re.sub(r"<[^>]+>", "", s)
    s = html.unescape(s)
    return re.sub(r"\s+", " ", s).strip()


def looks_like_map(filename):
    return bool(MAP_PAT.search(filename))


def license_ok(short, usage, lic):
    """(採用可否, 正規化した短い表記)。CC0 / パブリックドメイン / CC BY / CC BY-SA だけを採用する。"""
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
        # 米国内でのみパブリックドメインとされるテンプレート(更新なし・表示なし・1996 年など)は日本向けには使わない
        if re.search(r"pd-us-(not|no-notice|1996|unpublished|expired-abroad|record)", l) or "in the united states" in u:
            return False, s
        return True, "Public domain"
    if low.startswith("cc by-sa") or low.startswith("cc-by-sa"):
        return True, s.replace("CC-BY-SA", "CC BY-SA")
    if low.startswith("cc by") or low.startswith("cc-by"):
        return True, s.replace("CC-BY", "CC BY")
    # GFDL 単独、Attribution / No restrictions などの汎用テンプレート、GPL 等は宣言(NOTICE)と揃えるため不採用
    return False, s


def imageinfo(files):
    d = get_json(COMMONS, {
        "action": "query", "format": "json", "formatversion": 2,
        "titles": "|".join("File:" + f for f in files),
        "prop": "imageinfo", "iiprop": "extmetadata|url|size|mime|user", "iiurlwidth": 640,
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
        name = t[5:]
        artist = strip_tags(em.get("Artist", ""))
        credit = strip_tags(em.get("Credit", ""))
        uploader = ii.get("user", "")
        # 作者が空のとき: 「Own work」ならアップロード者が作者。それ以外は Credit(出典)欄で代用する。
        if not artist:
            if uploader and re.search(r"own work|自身の作品|eigenes werk|travail personnel|obra propia", credit, re.I):
                artist = uploader
            elif credit and credit.lower() not in ("own work",):
                artist = credit
        if len(artist) > 300:
            print(f"  long artist truncated: {name} ({len(artist)} chars)", file=sys.stderr)
            artist = artist[:300]
        attribution_required = lic not in ("CC0 1.0", "Public domain")
        out[f] = {
            "file": name, "mime": ii.get("mime", ""), "width": ii.get("width", 0), "height": ii.get("height", 0),
            "thumb": ii.get("thumburl", ""), "descUrl": ii.get("descriptionurl", ""),
            "artist": artist, "credit": credit[:200], "uploader": uploader,
            "licenseShort": em.get("LicenseShortName", ""), "license": lic, "licenseUrl": em.get("LicenseUrl", ""),
            "licenseTemplate": em.get("License", ""),
            "usageTerms": em.get("UsageTerms", ""), "restrictions": em.get("Restrictions", ""),
            # 帰属表示が必要なライセンスで作者が分からない写真は採用しない
            "ok": ok and ii.get("mime", "") in OK_MIME and bool(ii.get("thumburl")) and not looks_like_map(name)
                and (artist != "" or not attribution_required),
            "map": looks_like_map(name),
        }
    return out


def category_files(cat, limit=12):
    d = get_json(COMMONS, {
        "action": "query", "format": "json", "formatversion": 2, "list": "categorymembers",
        "cmtitle": "Category:" + cat, "cmtype": "file", "cmlimit": 40,
    })
    if not d:
        return []
    names = [m["title"][5:] for m in d.get("query", {}).get("categorymembers", [])]
    names = [n for n in names if n.lower().endswith((".jpg", ".jpeg", ".png")) and not looks_like_map(n)]
    # 写真らしい JPEG を先に
    names.sort(key=lambda n: 0 if n.lower().endswith((".jpg", ".jpeg")) else 1)
    return names[:limit]


def choose(cands, info):
    """候補(優先順)から採用可能な最初の画像。JPEG を PNG より優先する。"""
    usable = [info[f] for f in cands if info.get(f) and info[f]["ok"]]
    if not usable:
        return None
    jpeg = [m for m in usable if m["mime"] == "image/jpeg"]
    return (jpeg or usable)[0]


def main():
    species = load("wd_species.json")
    extracts = load("wiki_extracts.json", {})
    meta = load("image_meta.json", {})
    info = load("imageinfo_cache.json", {})
    os.makedirs("images", exist_ok=True)
    # 1. 候補ファイル名(P18 → ja pageimage → en pageimage)
    cands = {}
    for s in species:
        c = []
        if s["img"]:
            c.append(s["img"])
        for lang in ("ja", "en"):
            pi = extracts.get(s["qid"], {}).get(lang, {}).get("pageimage")
            if pi and pi not in c:
                c.append(pi)
        cands[s["qid"]] = [x.replace("_", " ") for x in c]

    def fetch_info(files):
        need = sorted({f for f in files if f not in info or (info[f] is not None and "uploader" not in info[f])})
        batches = list(chunks(need, 40))
        for i, res in enumerate(pmap(imageinfo, batches, workers=3)):
            info.update(res)
            if i % 10 == 0:
                save("imageinfo_cache.json", info)
        save("imageinfo_cache.json", info)

    fetch_info([f for c in cands.values() for f in c])
    # 2. 直接候補で JPEG の写真が取れない種は Commons カテゴリからも探す
    weak = [s for s in species if s["commonsCat"] and (lambda m: m is None or m["mime"] != "image/jpeg")(choose(cands[s["qid"]], info))]
    print(f"looking into commons category: {len(weak)}")
    catfiles = pmap(lambda s: category_files(s["commonsCat"]), weak, workers=3)
    for s, fs in zip(weak, catfiles):
        for f in fs:
            if f not in cands[s["qid"]]:
                cands[s["qid"]].append(f)
    fetch_info([f for c in cands.values() for f in c])
    # 3. 選定
    changed = 0
    for s in species:
        pick = choose(cands[s["qid"]], info)
        old = meta.get(s["qid"])
        if (pick or {}).get("file") != (old or {}).get("file"):
            changed += 1
            p = f"images/{s['qid']}.orig"
            if os.path.exists(p):
                os.remove(p)
        meta[s["qid"]] = pick
    save("image_meta.json", meta)
    print(f"changed selections: {changed}")
    # 4. サムネイルのダウンロード(未取得分のみ)
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

    done = sum(1 for ok in pmap(fetch_one, dl, workers=3) if ok)
    n_img = sum(1 for q in meta if meta[q])
    n_png = sum(1 for q in meta if meta[q] and meta[q]["mime"] == "image/png")
    print(f"downloaded {done}. species with image: {n_img}/{len(species)} (png: {n_png})")
    no_img = [s["name"] for s in species if not meta.get(s["qid"])]
    print("without image:", len(no_img), no_img[:40])


if __name__ == "__main__":
    main()
