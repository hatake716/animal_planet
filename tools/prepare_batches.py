#!/usr/bin/env python3
"""
エージェント(編集者・検証者)に渡すバッチを作る。
入力: wd_species.json, wiki_extracts.json, gbif_geo.json, image_meta.json
出力: batches/{i}.json(各 BATCH 種)、species_index.json(id ↔ qid の対応と基本情報)
"""
import os
import sys
from common import load, save, chunks

BATCH = 20
GROUP_LABELS = ["哺乳類", "鳥類", "爬虫類", "両生類", "魚類", "無脊椎動物"]
MAX_EXTRACT = 1400


def trim(s, n=MAX_EXTRACT):
    s = (s or "").replace("\n", " ").strip()
    return s if len(s) <= n else s[:n] + "…"


def main():
    species = load("wd_species.json")
    extracts = load("wiki_extracts.json", {})
    geo = load("gbif_geo.json", {})
    imgs = load("image_meta.json", {})
    species.sort(key=lambda s: (s["group"], s["orderJa"], s["familyJa"], s["name"]))
    items = []
    for i, s in enumerate(species):
        ex = extracts.get(s["qid"], {})
        ja = ex.get("ja", {})
        en = ex.get("en", {})
        if ja.get("missing"):
            print(f"  ja article missing: {s['qid']} {s['name']} {s['jaTitle']}", file=sys.stderr)
        g = geo.get(s["qid"]) or {}
        items.append({
            "id": i,
            "qid": s["qid"],
            "name": s["name"],
            "aliases": s["aliases"][:6],
            "sci": s["sci"],
            "status": s["status"],
            "group": GROUP_LABELS[s["group"]],
            "orderJa": s["orderJa"],
            "familyJa": s["familyJa"],
            "jaTitle": ja.get("title") or s["jaTitle"],
            "enTitle": en.get("title") or s["enTitle"],
            "jaExtract": trim(ja.get("extract")),
            "enExtract": trim(en.get("extract")),
            "endemic": s["endemic"],
            "gbif": {"lat": g.get("lat"), "lon": g.get("lon"), "n": g.get("n", 0), "countries": g.get("countries", [])} if g.get("lat") is not None else None,
            "hasImage": bool(imgs.get(s["qid"])),
        })
    os.makedirs("batches", exist_ok=True)
    for bi, batch in enumerate(chunks(items, BATCH)):
        save(f"batches/{bi}.json", batch)
    save("species_index.json", [{k: it[k] for k in ("id", "qid", "name", "sci", "status", "group", "jaTitle", "enTitle", "orderJa", "familyJa", "aliases")} for it in items])
    nb = (len(items) + BATCH - 1) // BATCH
    print(f"{len(items)} species -> {nb} batches of {BATCH}")


if __name__ == "__main__":
    main()
