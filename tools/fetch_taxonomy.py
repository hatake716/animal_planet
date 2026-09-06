#!/usr/bin/env python3
"""
Wikidata の親分類群(P171)を wbgetentities でたどり、各種の目(P105=Q36602)・科(Q35409)の和名と、
分類群(哺乳類/鳥類/…)の再確認を行う。SPARQL の P171* が重いので API で BFS する。
入力/出力: wd_species.json(orderJa / familyJa を埋め、group を検証する)
"""
import sys
from common import get_json, load, save, chunks

API = "https://www.wikidata.org/w/api.php"
ORDER, FAMILY = "Q36602", "Q35409"
GROUPS = [("Q7377", 0), ("Q5113", 1), ("Q10908", 3), ("Q10811", 2), ("Q1211871", 2), ("Q10915", 4), ("Q729", 5)]


def fetch_entities(ids):
    d = get_json(API, {"action": "wbgetentities", "format": "json", "ids": "|".join(ids), "props": "claims|labels", "languages": "ja|en"})
    out = {}
    for q, e in (d or {}).get("entities", {}).items():
        claims = e.get("claims", {})
        parents = []
        for st in claims.get("P171", []):
            if st.get("rank") == "deprecated":
                continue
            v = st.get("mainsnak", {}).get("datavalue", {}).get("value", {})
            if isinstance(v, dict) and v.get("id"):
                parents.append((st.get("rank") == "preferred", v["id"]))
        parents = [p for _, p in sorted(parents, key=lambda x: not x[0])]
        rank = ""
        for st in claims.get("P105", []):
            v = st.get("mainsnak", {}).get("datavalue", {}).get("value", {})
            if isinstance(v, dict) and v.get("id"):
                rank = v["id"]
                break
        labels = e.get("labels", {})
        out[q] = {"parents": parents, "rank": rank, "ja": labels.get("ja", {}).get("value", ""), "en": labels.get("en", {}).get("value", "")}
    return out


def main():
    species = load("wd_species.json")
    ent = load("taxon_cache.json", {})
    frontier = [s["qid"] for s in species if s["qid"] not in ent]
    depth = 0
    while frontier:
        need = sorted({q for q in frontier if q not in ent})
        print(f"depth {depth}: fetching {len(need)} entities")
        for batch in chunks(need, 50):
            ent.update(fetch_entities(batch))
        save("taxon_cache.json", ent)
        frontier = [p for q in need for p in ent.get(q, {}).get("parents", []) if p not in ent]
        depth += 1
        if depth > 40:
            print("too deep, stop", file=sys.stderr)
            break

    def ancestors(q):
        seen, order, stack = set(), [], [q]
        while stack:
            x = stack.pop(0)
            if x in seen:
                continue
            seen.add(x)
            order.append(x)
            stack.extend(ent.get(x, {}).get("parents", []))
        return order

    mismatch = 0
    for s in species:
        anc = ancestors(s["qid"])
        for a in anc:
            e = ent.get(a, {})
            if e.get("rank") == ORDER and not s.get("orderJa"):
                s["orderJa"] = e.get("ja") or e.get("en") or ""
            if e.get("rank") == FAMILY and not s.get("familyJa"):
                s["familyJa"] = e.get("ja") or e.get("en") or ""
        ancset = set(anc)
        g = next((gi for gq, gi in GROUPS if gq in ancset), None)
        if g is not None and g != s["group"]:
            mismatch += 1
            print(f"  group mismatch {s['qid']} {s['name']}: sparql={s['group']} api={g}", file=sys.stderr)
            s["group"] = g
    save("wd_species.json", species)
    print(f"done. order: {sum(1 for s in species if s['orderJa'])}, family: {sum(1 for s in species if s['familyJa'])}, group mismatches fixed: {mismatch}")


if __name__ == "__main__":
    main()
