#!/usr/bin/env bash
# データ取得を順に実行する(作業ディレクトリで実行)。TOOLS=このディレクトリ。
set -u
TOOLS="$(cd "$(dirname "$0")" && pwd)"
export PYTHONPATH="$TOOLS"
run() { echo "== $1 $(date +%T)"; python3 -u "$TOOLS/$1" > "$1.log" 2>&1; echo "== $1 exit $? $(date +%T)"; tail -3 "$1.log"; }
run fetch_wikidata.py
run fetch_taxonomy.py
( run fetch_wiki_extracts.py; run fetch_images.py ) &
( run fetch_gbif.py ) &
wait
echo "== all done $(date +%T)"
