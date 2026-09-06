#!/usr/bin/env python3
"""
各種の Wikipedia 記事冒頭(日本語版・英語版)と記事の代表画像(pageimage)を取得する。
入力: wd_species.json  出力: wiki_extracts.json = {qid: {"ja": {...}, "en": {...}}}
記事冒頭は解説文(desc)・生息地・脅威の一次資料。本文はアプリに同梱しない(要約のみ)。
"""
import sys
from common import get_json, load, save, pmap, chunks

API = {"ja": "https://ja.wikipedia.org/w/api.php", "en": "https://en.wikipedia.org/w/api.php"}


def fetch(lang, titles):
    d = get_json(API[lang], {
        "action": "query", "format": "json", "formatversion": 2, "redirects": 1,
        "prop": "extracts|pageimages|pageprops",
        "exintro": 1, "explaintext": 1, "exlimit": 20, "exchars": 2500,
        "piprop": "name|original", "ppprop": "disambiguation|wikibase_item",
        "titles": "|".join(titles),
    })
    out = {}
    if not d:
        return out
    q = d.get("query", {})
    redir = {r["from"]: r["to"] for r in q.get("redirects", [])}
    norm = {r["from"]: r["to"] for r in q.get("normalized", [])}
    pages = {p["title"]: p for p in q.get("pages", [])}
    for t in titles:
        final = redir.get(norm.get(t, t), norm.get(t, t))
        p = pages.get(final)
        if not p or p.get("missing"):
            out[t] = {"title": final, "missing": True}
            continue
        out[t] = {
            "title": p["title"],
            "extract": p.get("extract", ""),
            "pageimage": p.get("pageimage", ""),
            "disambiguation": "disambiguation" in p.get("pageprops", {}),
            "wikibase": p.get("pageprops", {}).get("wikibase_item", ""),
        }
    return out


def main():
    species = load("wd_species.json")
    result = load("wiki_extracts.json", {})
    todo = {"ja": [], "en": []}
    for s in species:
        r = result.get(s["qid"], {})
        if s["jaTitle"] and "ja" not in r:
            todo["ja"].append(s)
        if s["enTitle"] and "en" not in r:
            todo["en"].append(s)
    for lang in ("ja", "en"):
        batches = list(chunks(todo[lang], 20))
        print(f"{lang}: {len(todo[lang])} titles in {len(batches)} batches")
        key = "jaTitle" if lang == "ja" else "enTitle"

        def work(batch, lang=lang, key=key):
            return fetch(lang, [s[key] for s in batch])

        for i, res in enumerate(pmap(work, batches, workers=3)):
            for s in batches[i]:
                result.setdefault(s["qid"], {})[lang] = res.get(s[key], {"missing": True})
            if i % 10 == 0:
                save("wiki_extracts.json", result)
                print(f"  {lang} batch {i + 1}/{len(batches)}")
    save("wiki_extracts.json", result)
    miss = sum(1 for s in species if result.get(s["qid"], {}).get("ja", {}).get("missing"))
    print(f"done. ja missing: {miss}; ja extract empty: {sum(1 for s in species if not result.get(s['qid'], {}).get('ja', {}).get('extract'))}")


if __name__ == "__main__":
    main()
