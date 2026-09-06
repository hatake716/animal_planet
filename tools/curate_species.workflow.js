// Claude Code の Workflow ツールで実行する種データ確定スクリプト。
// 各バッチ(batches/{i}.json)について、編集者エージェントが座標・地域・解説などを決めて out/{i}.json に書き、
// 2 名の検証者(事実関係 / 地理)が独立に監査して out/{i}.fact.fix.json / out/{i}.geo.fix.json に修正を書く。
// args: { dir: batches の絶対パス, out: out の絶対パス, batches: [0,1,...] }
export const meta = {
  name: 'curate-species',
  description: '絶滅危惧種データの確定: 編集者が座標・地域・解説を作り、2名の検証者(事実/地理)が監査する',
  phases: [
    { title: 'Edit', detail: 'バッチごとに編集者が out/{i}.json を書く' },
    { title: 'Verify', detail: '事実関係と地理の検証者が修正ファイルを書く' },
  ],
}

const DIR = args.dir
const OUT = args.out
const BATCHES = args.batches

const REGIONS = '0=日本 1=東アジア(日本以外: 中国・朝鮮半島・台湾・モンゴル・ロシア極東) 2=東南アジア 3=南アジア(インド・スリランカ・ネパール・パキスタン・バングラデシュ) 4=中央アジア・西アジア(中東・コーカサス・イラン・アラビア半島・中央アジア諸国) 5=ヨーロッパ(ロシア西部を含む) 6=アフリカ(マダガスカル・島嶼を含む) 7=北アメリカ(米国・カナダ・メキシコ北部) 8=中央アメリカ・カリブ(メキシコ南部〜パナマ・カリブ海諸島) 9=南アメリカ(ガラパゴスを含む) 10=オセアニア(オーストラリア・ニュージーランド・ニューギニア・太平洋諸島・ハワイ) 11=海洋(外洋や複数の大洋を回遊・分布する海洋生物。沿岸性の種は隣接する陸域の地域も付ける) 12=北極・南極'

const EDIT_SCHEMA = {
  type: 'object',
  properties: {
    written: { type: 'boolean' },
    count: { type: 'integer' },
    notes: { type: 'string' },
  },
  required: ['written', 'count', 'notes'],
}
const FIX_SCHEMA = {
  type: 'object',
  properties: {
    written: { type: 'boolean' },
    fixes: { type: 'integer' },
    notes: { type: 'string' },
  },
  required: ['written', 'fixes', 'notes'],
}

const editPrompt = (i) => `あなたは日本語の生物図鑑の編集者です。ファイル ${DIR}/${i}.json を Read で読み、各種について下記の項目を決め、JSON 配列として ${OUT}/${i}.json に Write してください(他のファイルは作らない。Web 検索や外部アクセスは不要で、入力ファイルの情報だけで判断する)。

入力の各要素: id, name(和名), aliases, sci(学名), status(CR/EN/VU), group(分類), orderJa(目), familyJa(科), jaTitle/enTitle(Wikipedia 記事名), jaExtract(日本語版 Wikipedia の記事冒頭), enExtract(英語版の記事冒頭), endemic(Wikidata の固有分布), gbif(出現記録から推定した代表点。lat, lon, n=記録数, countries=記録の多い国コード。動物園・博物館などの記録が混ざることがある), hasImage。

出力の各要素(すべてのキーを必ず含める。入力のすべての id を漏れなく出力する):
- "id": 入力と同じ整数
- "lat", "lon": 代表的な生息地 1 点(十進、小数 2 桁以上)。規則: 分布の中心、または最も代表的な保護区・島・山地・河川・海域に置く。陸生・淡水生の種は必ず陸上(海上に置かない)、海生の種は海域(内陸に置かない)。固有種はその島・地域の中。広域分布種は分布の中心付近の代表地。gbif の点は参考情報(記録数が多く、記述と矛盾しなければ有力)。日本に分布する種は日本国内の代表地。
- "place": 生息地の短い地名(20 字以内。例「中国・四川省の山地」「マダガスカル北東部」「西表島」「東太平洋」「アマゾン川流域」)
- "regions": 生息地域の添字の配列(複数可、最低 1 つ)。${REGIONS}
- "desc": 解説。60〜120 字の日本語、「です・ます」調で統一。jaExtract / enExtract の内容だけに基づいて自分の言葉で要約し、出典にない事実を書かない。大きさ・見た目・生態など特徴を優先する。冒頭に和名を繰り返さない(「〜科の…です」から始めてよい)。
- "habitat": 生息環境と分布。40〜90 字、「〜に生息します」調。出典に基づく。
- "threats": 主な減少要因。20〜60 字(例「森林伐採による生息地の減少と密猟」)。出典に記述がなければ空文字 ""。
- "importance": 知名度 1〜3 の整数(3=ジャイアントパンダ・トラ・ゾウ・ジュゴンなど誰でも知る種、2=図鑑でよく見る種、1=それ以外)
- "yomi": 和名の読み(ひらがな)。和名がカタカナだけなら、それをひらがなにしたもの。
- "nameOk": 和名と記事がその学名の種(または亜種)を指していれば true。記事が別種や上位分類群の記事なら false にして note に理由を書く。
- "note": 補足(通常は "")

注意: 出力は JSON 配列のみ。文字列内に改行を入れない。座標は数値。すべての id を含めること。最後に、書き込んだ件数と気づいた問題を報告してください。`

const factPrompt = (i) => `あなたは生物図鑑の監査役(事実関係担当)です。${DIR}/${i}.json(入力: 各種の Wikipedia 記事冒頭 jaExtract/enExtract など)と ${OUT}/${i}.json(編集者の出力)を Read で読み、各種について次を厳しく確認してください。
1. desc / habitat / threats が入力の jaExtract / enExtract に基づいているか。出典にない事実、数値の誤り、別種の記述の混入、分類(group/orderJa/familyJa)との矛盾がないか。
2. desc は 60〜120 字、habitat は 40〜90 字、threats は 60 字以内(または空)で、「です・ます」調か。
3. yomi が和名の正しいひらがな読みか。
4. nameOk の判定が妥当か(記事が別種・上位分類群なら false)。
問題のある種だけ、修正後の値を含むオブジェクト {"id": 整数, "desc"?: 文字列, "habitat"?: 文字列, "threats"?: 文字列, "yomi"?: 文字列, "nameOk"?: 真偽, "reason": 理由} の JSON 配列を ${OUT}/${i}.fact.fix.json に Write してください(問題がなければ空配列 [] を書く)。修正する項目だけを含め、修正しない項目は含めないこと。最後に修正件数と要点を報告してください。`

const geoPrompt = (i) => `あなたは生物図鑑の監査役(地理担当)です。${DIR}/${i}.json(入力: 記事冒頭 jaExtract/enExtract、endemic、gbif の推定点)と ${OUT}/${i}.json(編集者の出力)を Read で読み、各種の lat / lon / place / regions を検証してください。
1. 座標が出典(記事の分布記述、endemic、gbif)に照らして分布域の中にあるか。
2. 陸生・淡水生の種が海上に、海生の種が内陸に置かれていないか(緯度経度から地理的に判断する)。
3. place が座標と対応する地名か(20 字以内)。
4. regions が座標と分布記述に合っているか。複数地域に分布する種は該当する地域をすべて含めているか。日本に分布する種は 0(日本)を含むか。海洋生物は 11(海洋)を含み、沿岸性なら隣接する陸域も含むか。${REGIONS}
問題のある種だけ、修正後の値を含む {"id": 整数, "lat"?: 数値, "lon"?: 数値, "place"?: 文字列, "regions"?: 整数の配列, "reason": 理由} の JSON 配列を ${OUT}/${i}.geo.fix.json に Write してください(問題がなければ空配列 [])。修正する項目だけを含めること。最後に修正件数と要点を報告してください。`

const results = await pipeline(
  BATCHES,
  (i) => agent(editPrompt(i), { label: `edit:${i}`, phase: 'Edit', schema: EDIT_SCHEMA }),
  (edit, i) => {
    if (!edit || !edit.written) {
      log(`batch ${i}: editor failed`)
      return { i, edit, fact: null, geo: null }
    }
    return parallel([
      () => agent(factPrompt(i), { label: `fact:${i}`, phase: 'Verify', schema: FIX_SCHEMA }),
      () => agent(geoPrompt(i), { label: `geo:${i}`, phase: 'Verify', schema: FIX_SCHEMA }),
    ]).then(([fact, geo]) => ({ i, edit, fact, geo }))
  },
)

const summary = results.filter(Boolean).map((r) => ({
  batch: r.i,
  edited: r.edit ? r.edit.count : 0,
  factFixes: r.fact ? r.fact.fixes : -1,
  geoFixes: r.geo ? r.geo.fixes : -1,
  notes: [r.edit && r.edit.notes, r.fact && r.fact.notes, r.geo && r.geo.notes].filter(Boolean).join(' / ').slice(0, 300),
}))
const failed = summary.filter((s) => s.edited === 0 || s.factFixes < 0 || s.geoFixes < 0).map((s) => s.batch)
log(`done: ${summary.length} batches, failed: ${failed.join(',') || 'none'}`)
return { summary, failed }
