# データ生成の手順

アプリに同梱する `app/src/main/assets/species.json`(種 → 座標・解説・写真の出典など)、
`app/src/main/assets/img/{id}.jpg`(写真)、`app/src/main/assets/tiles/`(地球テクスチャ)の作り方。

**絶対条件**: 写真・データは第三者の利用が自由な情報源(パブリックドメイン / CC0 / CC BY / CC BY-SA)からのみ取得する。
スクリプトは `tools/` にあり、Python 3 の標準ライブラリと ffmpeg だけで動く(`pip` 不要)。
作業ディレクトリで `PYTHONPATH=tools` を付けて実行する(`tools/run_fetch.sh` が 1〜5 をまとめて実行する)。

## 1. 対象種の取得(`tools/fetch_wikidata.py`)

Wikidata の SPARQL で、IUCN レッドリストのカテゴリ(P141)が **CR(Q219127)/ EN(Q96377276)/ VU(Q278113)** で、
**日本語版 Wikipedia の記事がある**分類群を集める。複数の評価がある場合は「優先ランク → 最新の時点 → 最も深刻」の順で現在の評価を決める。
和名(ラベル)・別名(ja の別名と P1843)・学名(P225)・階級(P105)・画像(P18)・GBIF ID(P846)・Commons カテゴリ(P373)・固有分布(P183)を取る。
上位分類群チェーン(P171*)で **哺乳類 / 鳥類 / 爬虫類 / 両生類 / 魚類(その他の脊索動物)/ 無脊椎動物(その他の動物)** に機械分類し、植物・菌類などは除外する。
出力: `wd_species.json`

## 2. 目・科の和名(`tools/fetch_taxonomy.py`)

親分類群(P171)を Wikidata API(`wbgetentities`)でたどり、階級が目(Q36602)・科(Q35409)の分類群の日本語ラベルを付ける。
分類群の判定も再確認する(SPARQL の P171* は重く、タイムアウトするため API で幅優先探索する)。

## 3. Wikipedia 記事冒頭の取得(`tools/fetch_wiki_extracts.py`)

日本語版・英語版の記事冒頭(`extracts`、プレーンテキスト 2,500 字まで)と記事の代表画像(`pageimages`)を取得する。
**この記事冒頭が解説(desc)・生息環境(habitat)・脅威(threats)の一次資料**になる。記事本文は同梱しない。
出力: `wiki_extracts.json`

## 4. 写真の選定とダウンロード(`tools/fetch_images.py`)

候補: Wikidata P18 → 日本語版記事の代表画像 → 英語版記事の代表画像 → Commons カテゴリ内の画像。
Commons API の `extmetadata` で **ライセンスを確認し、CC0 / パブリックドメイン / CC BY / CC BY-SA のラスター画像だけを採用**する
(NC・ND・GFDL 単独・不明は不採用)。作者(Artist)・ライセンス名・ライセンス URL・ファイルページ URL を記録し、幅 640px のサムネイルを保存する。
出力: `image_meta.json`, `images/{qid}.orig`
Wikimedia のレート制限(429)に当たった場合は再実行すると未取得分だけ取り直す(並列数は 4 以下)。

## 5. 分布の代表点(`tools/fetch_gbif.py`)

GBIF の出現記録のうち **CC0 / CC BY 4.0 のレコード**(座標あり・地理的問題なし・在)を最大 300 件取り、
5° 格子で最頻のセル近傍の中央値を代表点にする(動物園などの外れ値に強い)。記録の多い国コードも記録する。
出力: `gbif_geo.json`(記録そのものは同梱しない。編集者エージェントへの参考情報)

## 6. エージェントによる確定(Claude Code の Workflow: `tools/curate_species.workflow.js`)

`tools/prepare_batches.py` で 20 種ずつのバッチ(`batches/{i}.json`)を作る。各種には和名・学名・評価・分類・
日本語版/英語版の記事冒頭・固有分布・GBIF の代表点を添える。バッチごとに

1. 編集者エージェント: 記事冒頭を読んで、代表的な生息地の座標(`lat`, `lon`)と地名(`place`)、生息地域(`regions`、複数可)、
   解説(`desc`、60〜120 字)、生息環境(`habitat`)、主な脅威(`threats`)、知名度(`importance`)、読み(`yomi`)、
   和名と記事の一致(`nameOk`)を決めて `out/{i}.json` に書く。
2. 検証エージェント × 2(事実関係: 記事冒頭に基づくか・文体・読み / 地理: 座標が分布域内か・陸海の取り違え・地名・地域)が
   独立に監査し、修正を `out/{i}.fact.fix.json` `out/{i}.geo.fix.json` に書く。

座標の規則: 分布の中心または最も代表的な保護区・島・山地・河川・海域。陸生種は陸上、海生種は海域。固有種は固有の地域。

生息地域: 0=日本 1=東アジア 2=東南アジア 3=南アジア 4=中央アジア・西アジア 5=ヨーロッパ 6=アフリカ 7=北アメリカ
8=中央アメリカ・カリブ 9=南アメリカ 10=オセアニア 11=海洋 12=北極・南極(種ごとに複数持てる)

## 7. 統合と検証

- `tools/assemble.py <assets>` で修正を適用し、写真を長辺 640px の JPEG に変換して `species.json` と `img/` を生成する。
  写真のクレジット一覧 `credits.json` も出力する(`docs/CREDITS.md` の元)。
- `tools/validate.py <assets>` で座標範囲・地域・解説の長さ・写真ファイル・記事名の存在(API で再確認)を検証する。

## 8. 地球テクスチャ・アイコン

- タイルは「地球儀で見る世界史wikipedia」と同じ NASA Blue Marble Next Generation を縮小・分割したもの(level 0〜4、約 42MB)。
- アイコンは `tools/make_icon.sh <assets/tiles> <res> [<docs/play>]` で level 1 のタイルから正射投影の地球儀を描き、足跡を添えて生成する。

## species.json の形式

```json
{
  "regions": ["日本", "東アジア", "東南アジア", "南アジア", "中央アジア・西アジア", "ヨーロッパ", "アフリカ",
              "北アメリカ", "中央アメリカ・カリブ", "南アメリカ", "オセアニア", "海洋", "北極・南極"],
  "entries": [
    [id, "和名", "別名1|別名2", "学名", "日本語版記事名", "英語版記事名", lat, lon, "生息地名",
     group(0=哺乳類,1=鳥類,2=爬虫類,3=両生類,4=魚類,5=無脊椎動物), "CR"|"EN"|"VU", regionMask(ビット), importance(1-3),
     "解説", "生息環境", "主な脅威", "目", "科",
     "Commons ファイル名(なければ空)", "作者", "ライセンス", "ライセンスURL", "読み(任意)"]
  ]
}
```
