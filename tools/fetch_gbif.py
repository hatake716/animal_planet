#!/usr/bin/env python3
"""
GBIF から各種の出現記録(CC0 / CC BY 4.0 のレコードのみ)を取り、生息域の代表点を推定する。
代表点: 5°格子で最頻のセルを求め、その近傍(±7.5°)の記録の中央値。動物園などの外れ値に強い。
入力: wd_species.json  出力: gbif_geo.json = {qid: {key, n, lat, lon, countries[...]}}
(分布データそのものは同梱しない。代表点の推定にのみ使う)
"""
import statistics
import sys
from collections import Counter
from common import get_json, load, save, pmap

GBIF = "https://api.gbif.org/v1"


def match_key(s):
    if s.get("gbif"):
        return int(s["gbif"])
    if not s.get("sci"):
        return None
    d = get_json(f"{GBIF}/species/match", {"name": s["sci"]})
    if d and d.get("matchType") in ("EXACT", "FUZZY") and d.get("usageKey"):
        return d["usageKey"]
    return None


def occurrences(key):
    d = get_json(f"{GBIF}/occurrence/search?license=CC0_1_0&license=CC_BY_4_0", {
        "taxonKey": key, "hasCoordinate": "true", "hasGeospatialIssue": "false",
        "occurrenceStatus": "PRESENT", "limit": 300,
    })
    if not d:
        return []
    return [(r["decimalLatitude"], r["decimalLongitude"], r.get("countryCode", "")) for r in d.get("results", [])
            if r.get("decimalLatitude") is not None and r.get("decimalLongitude") is not None]


def center(recs):
    if not recs:
        return None
    cells = Counter((int((la + 90) // 5), int((lo + 180) // 5)) for la, lo, _ in recs)
    (cy, cx), _ = cells.most_common(1)[0]
    cla, clo = cy * 5 - 90 + 2.5, cx * 5 - 180 + 2.5
    near = [(la, lo) for la, lo, _ in recs if abs(la - cla) <= 7.5 and abs(((lo - clo + 180) % 360) - 180) <= 7.5]
    if not near:
        near = [(la, lo) for la, lo, _ in recs]
    return round(statistics.median(p[0] for p in near), 3), round(statistics.median(p[1] for p in near), 3)


def main():
    species = load("wd_species.json")
    geo = load("gbif_geo.json", {})
    todo = [s for s in species if s["qid"] not in geo]
    print(f"todo: {len(todo)}")

    def work(s):
        try:
            key = match_key(s)
            if not key:
                return s["qid"], {"key": None, "n": 0}
            recs = occurrences(key)
            c = center(recs)
            countries = [cc for cc, _ in Counter(r[2] for r in recs if r[2]).most_common(6)]
            return s["qid"], {"key": key, "n": len(recs), "lat": c[0] if c else None, "lon": c[1] if c else None, "countries": countries}
        except Exception as e:  # noqa: BLE001
            print(f"  failed {s['qid']} {s['sci']}: {e}", file=sys.stderr)
            return s["qid"], None

    for i, (q, r) in enumerate(pmap(work, todo, workers=6)):
        if r is not None:
            geo[q] = r
        if i % 100 == 0:
            save("gbif_geo.json", geo)
            print(f"  {i + 1}/{len(todo)}")
    save("gbif_geo.json", geo)
    print(f"done. with center: {sum(1 for g in geo.values() if g.get('lat') is not None)}/{len(species)}")


if __name__ == "__main__":
    main()
