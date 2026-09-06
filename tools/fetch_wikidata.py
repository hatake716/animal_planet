#!/usr/bin/env python3
"""
Wikidata から IUCN レッドリストで CR/EN/VU と評価された分類群(日本語版 Wikipedia 記事あり)を取得する。
出力: wd_species.json = [{qid, name, jaTitle, enTitle, sci, rank, status, img, gbif, commonsCat,
                          aliases[], endemic[], group, orderJa, familyJa}]
group は上位分類群チェーン(P171*)から機械判定: 0=哺乳類 1=鳥類 2=爬虫類 3=両生類 4=魚類 5=無脊椎動物。
植物・菌類などは除外する。目・科の和名は fetch_taxonomy.py で付ける。
"""
import sys
import time
from common import get_json, save, chunks

SPARQL = "https://query.wikidata.org/sparql"
STATUS = {"Q219127": "CR", "Q96377276": "EN", "Q278113": "VU"}
SEVERITY = {"CR": 3, "EN": 2, "VU": 1}
# 判定の優先順(先に一致したものを採用)
GROUPS = [
    ("Q7377", 0),      # Mammalia
    ("Q5113", 1),      # Aves
    ("Q10908", 3),     # Amphibia
    ("Q10811", 2),     # Reptilia
    ("Q1211871", 2),   # Sauropsida
    ("Q10915", 4),     # Chordata(残り=魚類・無顎類など)
    ("Q729", 5),       # Animalia(残り=無脊椎動物)
]
EXCLUDE = {"Q756": "plant", "Q764": "fungi"}  # 動物界(Q729)に属さないものは group 判定で落ちる


def sparql(q, timeout=90):
    for attempt in range(6):
        try:
            d = get_json(SPARQL, {"query": q, "format": "json"}, headers={"Accept": "application/sparql-results+json"}, timeout=timeout, retries=1)
            return d["results"]["bindings"]
        except Exception as e:  # noqa: BLE001
            print(f"  sparql retry {attempt + 1}: {e}", file=sys.stderr)
            time.sleep(10 * (attempt + 1))
    raise SystemExit("SPARQL failed")


def qid(uri):
    return uri.rsplit("/", 1)[-1]


def main():
    # 1. 候補: CR/EN/VU のいずれかの statement を持ち、jawiki 記事がある分類群。全 P141 statement を集める。
    rows = sparql("""
SELECT ?item ?status ?rank ?time WHERE {
  { SELECT DISTINCT ?item WHERE {
      VALUES ?s { wd:Q219127 wd:Q96377276 wd:Q278113 }
      ?item wdt:P141 ?s .
      ?a schema:about ?item ; schema:isPartOf <https://ja.wikipedia.org/> .
  } }
  ?item p:P141 ?st . ?st ps:P141 ?status ; wikibase:rank ?rank .
  OPTIONAL { ?st pq:P585 ?time }
}""")
    statements = {}
    for r in rows:
        statements.setdefault(qid(r["item"]["value"]), []).append(
            (qid(r["status"]["value"]), qid(r["rank"]["value"]), r.get("time", {}).get("value", "")))
    print(f"candidates: {len(statements)}")
    # 現在の評価を決める: 優先ランク > 最新の時点 > 最も深刻
    current = {}
    for q, sts in statements.items():
        pref = [s for s in sts if s[1] == "PreferredRank"]
        pool = pref if pref else sts
        pool = sorted(pool, key=lambda s: (s[2], SEVERITY.get(STATUS.get(s[0], ""), 0)), reverse=True)
        st = STATUS.get(pool[0][0])
        if st:
            current[q] = st
    print(f"currently CR/EN/VU: {len(current)}")
    ids = sorted(current)

    # 2. 基本情報
    info = {}
    for batch in chunks(ids, 200):
        values = " ".join(f"wd:{q}" for q in batch)
        rows = sparql(f"""
SELECT ?item ?itemLabel ?jaTitle ?enTitle ?sci ?rank ?img ?gbif ?commonsCat WHERE {{
  VALUES ?item {{ {values} }}
  ?ja schema:about ?item ; schema:isPartOf <https://ja.wikipedia.org/> ; schema:name ?jaTitle .
  OPTIONAL {{ ?en schema:about ?item ; schema:isPartOf <https://en.wikipedia.org/> ; schema:name ?enTitle . }}
  OPTIONAL {{ ?item wdt:P225 ?sci }}
  OPTIONAL {{ ?item wdt:P105 ?rank }}
  OPTIONAL {{ ?item wdt:P18 ?img }}
  OPTIONAL {{ ?item wdt:P846 ?gbif }}
  OPTIONAL {{ ?item wdt:P373 ?commonsCat }}
  SERVICE wikibase:label {{ bd:serviceParam wikibase:language "ja,en". }}
}}""")
        for r in rows:
            q = qid(r["item"]["value"])
            d = info.setdefault(q, {"qid": q, "name": "", "jaTitle": "", "enTitle": "", "sci": "", "rank": "", "img": "", "gbif": "", "commonsCat": "", "aliases": [], "endemic": []})
            d["name"] = d["name"] or r.get("itemLabel", {}).get("value", "")
            d["jaTitle"] = d["jaTitle"] or r.get("jaTitle", {}).get("value", "")
            d["enTitle"] = d["enTitle"] or r.get("enTitle", {}).get("value", "")
            d["sci"] = d["sci"] or r.get("sci", {}).get("value", "")
            d["rank"] = d["rank"] or qid(r["rank"]["value"]) if "rank" in r else d["rank"]
            d["img"] = d["img"] or (r["img"]["value"].rsplit("/", 1)[-1] if "img" in r else "")
            d["gbif"] = d["gbif"] or r.get("gbif", {}).get("value", "")
            d["commonsCat"] = d["commonsCat"] or r.get("commonsCat", {}).get("value", "")
        print(f"  info {len(info)}/{len(ids)}")
    for q in ids:
        info[q]["status"] = current[q]

    # 3. 別名(日本語)と固有分布(P183)
    for batch in chunks(ids, 200):
        values = " ".join(f"wd:{q}" for q in batch)
        rows = sparql(f"""
SELECT ?item ?alt ?cn ?endLabel WHERE {{
  VALUES ?item {{ {values} }}
  OPTIONAL {{ ?item skos:altLabel ?alt FILTER(LANG(?alt) = "ja") }}
  OPTIONAL {{ ?item wdt:P1843 ?cn FILTER(LANG(?cn) = "ja") }}
  OPTIONAL {{ ?item wdt:P183 ?end . ?end rdfs:label ?endLabel FILTER(LANG(?endLabel) = "ja") }}
}}""")
        for r in rows:
            d = info[qid(r["item"]["value"])]
            for k, dst in (("alt", "aliases"), ("cn", "aliases"), ("endLabel", "endemic")):
                v = r.get(k, {}).get("value")
                if v and v not in d[dst] and v != d["name"]:
                    d[dst].append(v)
        print(f"  aliases/endemic batch done")

    # 4. 分類群(上位分類群チェーン)
    targets = [g for g, _ in GROUPS] + list(EXCLUDE)
    member = {}
    for batch in chunks(ids, 120):
        values = " ".join(f"wd:{q}" for q in batch)
        tv = " ".join(f"wd:{t}" for t in targets)
        rows = sparql(f"""
SELECT ?item ?grp WHERE {{
  VALUES ?item {{ {values} }}
  VALUES ?grp {{ {tv} }}
  ?item wdt:P171* ?grp .
}}""")
        for r in rows:
            member.setdefault(qid(r["item"]["value"]), set()).add(qid(r["grp"]["value"]))
        print(f"  groups {len(member)}")
    out = []
    dropped = {}
    for q in ids:
        d = info[q]
        m = member.get(q, set())
        ex = [EXCLUDE[e] for e in m if e in EXCLUDE]
        if ex:
            dropped[ex[0]] = dropped.get(ex[0], 0) + 1
            continue
        g = next((gi for gq, gi in GROUPS if gq in m), None)
        if g is None:
            dropped["not-animal"] = dropped.get("not-animal", 0) + 1
            print(f"  not animal / unknown group: {q} {d['name']} {d['sci']}", file=sys.stderr)
            continue
        d["group"] = g
        d["orderJa"] = ""
        d["familyJa"] = ""
        out.append(d)
    out.sort(key=lambda d: (d["group"], d["sci"]))
    save("wd_species.json", out)
    from collections import Counter
    print("dropped:", dropped)
    print("groups:", Counter(d["group"] for d in out))
    print("status:", Counter(d["status"] for d in out))
    print("with image:", sum(1 for d in out if d["img"]), "/", len(out))


if __name__ == "__main__":
    main()
