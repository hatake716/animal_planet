# Play Console 申告フォームの回答案

コード(権限・保存データ・通信・依存ライブラリ)を確認して作成した回答案です。Play Console の設問文言はときどき変わるため、趣旨が同じ設問に読み替えて入力してください。

## 1. データセーフティ

# データセーフティ(Data safety)フォーム 回答案

## 結論

**「データを収集しない・共有しない」で申告できます。** アプリ本体はユーザーデータを端末外に送信するコードを持たず、外部 SDK も組み込んでいません。既読・ブックマーク・地球儀のカメラ位置は端末内にのみ保存され、`allowBackup="false"` のためクラウドバックアップにも乗りません。WebView で表示する Wikipedia / YouTube / Wikimedia Commons は第三者サイトであり、アプリがそのコードや挙動を制御していないため、Google の定義上「アプリが収集するデータ」には該当しません(詳細は末尾)。

以下、Play Console のフォームの設問順に「設問 → 回答 → 根拠」を示します。設問文は Play Console の日本語 UI を想定した表現で、実際の文言は版によって多少異なります。

---

## 1. 概要(Overview)

- 特に入力なし。フォームの説明画面です。「ユーザーデータ」の定義(端末外に送信されるデータ)を確認してから次へ進みます。

## 2. データの収集とセキュリティ(Data collection and security)

### 設問 2-1: アプリは、必要なユーザーデータの種類のいずれかを収集または共有しますか?
- **回答: いいえ**
- 根拠:
  - 権限は `android.permission.INTERNET` のみ(`/home/takeshi/StudioProjects/animal_planet/app/src/main/AndroidManifest.xml`)。位置情報・連絡先・カメラ・マイク・ストレージ等の権限は要求していない。ビルド後のマージ済みマニフェスト(`app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml`)でも `INTERNET` と AndroidX が自動生成するアプリ内部用の `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` だけで、`AD_ID` 権限はない。
  - 依存ライブラリは AndroidX(core / lifecycle / activity / Compose / Material3 / DataStore)と junit のみ(`/home/takeshi/StudioProjects/animal_planet/gradle/libs.versions.toml`、`app/build.gradle.kts`)。広告 SDK・解析 SDK・クラッシュレポート SDK・課金 SDK・認証 SDK はない。
  - アプリ内に HTTP クライアントのコードがない(`HttpURLConnection` / OkHttp / `openConnection` 等の使用箇所なし)。通信は `ui/WebScreen.kt` の WebView が第三者サイトを表示する場合のみ。
  - `docs/PLAY_STORE.md` の方針(データの収集: なし / 共有: なし)、`docs/PRIVACY.md` の記述(個人情報を収集・送信しない)と一致。

### 設問 2-2: アプリが収集したユーザーデータはすべて転送中に暗号化されますか?
- **回答: 設問 2-1 で「いいえ」と答えると表示されません(該当なし)。**
- 参考: アプリが開く URL はすべて `https://`(`data/Entry.kt` の `wikipediaUrl` / `enWikipediaUrl` / `youtubeSearchUrl` / `commonsUrl`)。

### 設問 2-3: ユーザーがデータの削除をリクエストする方法を提供していますか?
- **回答: 設問 2-1 で「いいえ」と答えると表示されません(該当なし)。**
- 参考: 端末内データはアンインストールまたは「アプリのデータを消去」で完全に削除される(`docs/PRIVACY.md` 「データの削除」)。プライバシーポリシーにその旨を記載済み。

### 設問 2-4(表示される場合): アプリでユーザーがアカウントを作成できますか? / アカウント削除の方法を提供していますか?
- **回答: いいえ(アカウント機能なし)**
- 根拠: ログイン・登録の UI、認証 SDK、サーバー通信のいずれも存在しない。ユーザーごとの状態は `data/UserData.kt` の既読・ブックマークと `ui/MainViewModel.kt` のカメラ位置のみで、すべて端末内。

## 3. データの種類(Data types)

- **回答: 設問 2-1 で「いいえ」のため、このセクションは表示されず、何もチェックしません。**
- 念のため、各カテゴリを「収集していない」と言える根拠を整理します(審査で問われたときの説明用)。

| Play の分類 | 回答 | 根拠 |
|---|---|---|
| 位置情報(おおよそ / 正確) | 収集なし | 位置情報権限なし。`LocationManager` / `FusedLocation` の使用箇所なし。`MainViewModel.kt` が SharedPreferences `globe` に保存する `lat` / `lon` / `alt` は**地球儀のカメラ位置**(最後に見ていた地図上の地点)であり端末の位置情報ではない。端末内にのみ保存。 |
| 個人情報(氏名・メール・ユーザー ID・住所・電話 等) | 収集なし | 入力欄がない。アカウントなし。 |
| 財務情報 | 収集なし | 有料アプリの決済は Google Play 側で完結。アプリ内購入・課金 SDK なし。 |
| 健康とフィットネス | 収集なし | 該当機能なし。 |
| メッセージ | 収集なし | 該当機能なし。 |
| 写真と動画 | 収集なし | 表示する写真はアプリ同梱の assets(`data/ImageStore.kt`)。端末の写真へのアクセス権限なし。 |
| 音声ファイル | 収集なし | 該当機能なし。 |
| ファイルとドキュメント | 収集なし | 該当機能なし。 |
| カレンダー / 連絡先 | 収集なし | 権限なし。 |
| アプリのアクティビティ(アプリ内操作、検索履歴 等) | 収集なし | 既読(`read_ids`)・ブックマーク(`bookmark_ids`)は DataStore `user_data` に端末内保存のみ(`data/UserData.kt`)。アプリ内検索は同梱カタログを端末内で検索するだけで送信しない(`MainViewModel.updateQuery` → `Catalog.search`)。 |
| ウェブブラウジング | 収集なし | WebView の閲覧履歴・URL をアプリが記録・送信するコードはない。 |
| アプリの情報とパフォーマンス(クラッシュログ、診断) | 収集なし | クラッシュレポート SDK・解析 SDK なし。 |
| デバイスまたはその他の ID(広告 ID 等) | 収集なし | `AD_ID` 権限なし。広告 SDK なし。ID を読む API の使用箇所なし。 |

## 4. データの使用と処理(Data usage and handling)

- **回答: 表示されません(データの種類を選択していないため)。**

## 5. プレビュー(Preview)

- ストアに表示される内容は「データは収集されません」「第三者とデータは共有されません」となります。プレビューを確認して送信します。

---

## WebView で表示する外部サイトの扱い(申告に含めない理由)

- Google の Data safety の「収集」定義は、アプリから開いた WebView 内のデータについて、アプリがその WebView に配信されるコードや挙動を制御している場合に限ってアプリの収集に含める、としています(Play Console ヘルプ「Google Play のデータ セーフティ セクションの情報を提供する」より。原文: "if your app is in control of the code/behavior delivered through that webview")。
- 本アプリの WebView(`ui/WebScreen.kt`)は、`data/Entry.kt` が組み立てる URL、すなわち Wikipedia(`ja.m.wikipedia.org` / `en.m.wikipedia.org`)、YouTube の検索結果(`m.youtube.com/results?search_query=<種名 絶滅危惧種>`)、Wikimedia Commons のファイルページ(`commons.m.wikimedia.org`)をそのまま表示するだけで、アプリはこれらのサイトのコードを制御していません。ページに JavaScript を注入する `addJavascriptInterface` の使用もなく、ページ内容を読み取って送信する処理もありません。
- したがって、これらのサイト側(Wikimedia Foundation / Google)が行うログ記録や Cookie の利用は、各サービスのプライバシーポリシーの範囲であり、本アプリの申告対象ではありません。この説明は `docs/PRIVACY.md` の「通信」節に記載済みで、ストアに登録するプライバシーポリシーがこの役割を担います。
- 補足事項(申告には影響しない、説明用):
  - WebView は `javaScriptEnabled` / `domStorageEnabled` を有効にしていますが、これは第三者ページを正しく表示するための設定で、アプリ自身がデータを取得するものではありません。
  - ページ内の `http(s)` リンクは同じ WebView 内で開き(`shouldOverrideUrlLoading` が http/https では `false` を返す)、`mailto:` 等それ以外のスキームと「ブラウザで開く」ボタンは `ACTION_VIEW` で端末の他アプリに渡します。いずれもユーザー操作で始まり、アプリがユーザーデータを添えることはありません。
  - 種の「共有」(`ui/App.kt` の `shareEntry`)は、和名・学名・IUCN カテゴリ・生息地・Wikipedia の URL というカタログ固有のテキストを `ACTION_SEND` でシステムの共有シートに渡すだけで、ユーザーのデータは含まれません。
  - すべての通信はユーザーが種を選んで「Wikipedia」「動画」「写真の出典」を押したときにのみ発生し、図鑑の閲覧(地球儀・写真・解説)はオフラインで完結します(`README.md`、`docs/PRIVACY.md`)。

## 端末内にのみ保存するデータ(「収集」に該当しない理由)

| 保存先 | キー | 内容 | 端末外へ出るか |
|---|---|---|---|
| DataStore Preferences `user_data`(`data/UserData.kt`) | `read_ids` | 一度でも詳細を開いた種の id(カンマ区切りの整数) | 出ない |
| 同上 | `bookmark_ids` | ブックマークした種の id(最大 1,000 件、追加順) | 出ない |
| SharedPreferences `globe`(`ui/MainViewModel.kt`) | `lat` / `lon` / `alt` | 地球儀カメラの緯度・経度・高度(復元用) | 出ない |

- 「収集」は端末外への送信を意味し、端末内で処理・保存されるだけのデータは申告不要、というのが Google の定義です。上記はサーバーへ送信されず、`allowBackup="false"`(AndroidManifest.xml)により Android の自動バックアップ経由でも端末外へ出ません。

## 2. コンテンツのレーティング(IARC アンケート)

# コンテンツレーティング(IARC アンケート)回答案

## 前提

- Play Console の「アプリのコンテンツ → コンテンツのレーティング」から IARC のアンケートに回答します。最初に **連絡用メールアドレス** と **カテゴリ** を選び、その後にカテゴリ別の設問が出ます。
- 設問文は IARC / Play Console の版で変わるため、以下は「想定設問」です。実際の設問は趣旨で対応付けてください。
- 本アプリの内容(根拠となる事実):
  - IUCN レッドリストで CR / EN / VU と評価された動物 1,063 種を地球儀にピン表示する教育用の図鑑(`README.md`、`ui/AboutDialog.kt`)。
  - 各種の写真(1,009 種、Wikimedia Commons の自由ライセンス画像)、解説・生息環境・主な脅威(Wikipedia 記事冒頭の要約)、分類・学名・IUCN カテゴリ(Wikidata)を同梱(`NOTICE.md`)。
  - 「Wikipedia」「動画(YouTube 検索結果)」「写真の出典(Wikimedia Commons)」をアプリ内ブラウザ(WebView)で表示(`ui/WebScreen.kt`、`data/Entry.kt`)。
  - アカウント・投稿・チャット・課金・広告・位置情報の機能はない(`AndroidManifest.xml`、`gradle/libs.versions.toml`)。

---

## 0. カテゴリの選択

### 設問: アプリのカテゴリを選択してください
- **回答: 「参考資料、ニュース、または教育」に相当するカテゴリ**(ゲーム / ソーシャル / エンターテインメント / ユーティリティ / ウェブブラウザ等ではない)
- 根拠: ストア掲載のカテゴリも「教育」(`docs/PLAY_STORE.md`)。図鑑・参考資料アプリ。
- 補足: 「このアプリはウェブブラウザまたは検索エンジンですか?」の趣旨の設問があれば **いいえ**。アプリ内ブラウザに URL 入力欄はなく、開けるのはアプリが組み立てた Wikipedia / YouTube / Commons の URL とそこからたどれるリンクだけで、汎用ブラウザではない(`ui/WebScreen.kt` に URL 入力 UI がない)。

## 1. 暴力

### 設問: アプリに暴力的なコンテンツ(暴力の描写、流血、死体、拷問など)が含まれていますか?
- **回答: いいえ**
- 根拠: 収録内容は動物の写真と、解説・生息環境・主な脅威の説明文。写真は Wikimedia Commons の各種の写真で暴力を描写するものではない(`NOTICE.md`)。「主な脅威」には密猟・乱獲・生息地の破壊などが**事実の記述として文章で**含まれるが、暴力の描写・画像ではない(`README.md` 「種の詳細」、`docs/PLAY_STORE.md` 「暴力・性的表現・ギャンブル等なし」)。
- 派生設問(リアルな暴力か / 人間に対する暴力か / 流血表現か)はすべて **該当なし**。

## 2. 性的表現

### 設問: アプリに性的なコンテンツやヌードが含まれていますか?
- **回答: いいえ**
- 根拠: 動物の写真と生物学的な解説のみ(`README.md`、`docs/PLAY_STORE.md`)。

## 3. 不適切な言葉・下品なユーモア

### 設問: アプリに冒とく的な表現や下品なユーモアが含まれていますか?
- **回答: いいえ**
- 根拠: テキストは Wikipedia / Wikidata に基づく図鑑の解説文(`NOTICE.md`)。

## 4. 規制物質(薬物・アルコール・タバコ)

### 設問: アプリに違法薬物・アルコール・タバコへの言及や描写が含まれていますか?
- **回答: いいえ**
- 根拠: 該当する内容はない。収録テーマは野生動物と保全状況。

## 5. 恐怖・ホラー

### 設問: アプリに恐怖を与えるような表現やホラー要素が含まれていますか?
- **回答: いいえ**
- 根拠: 同上。地球儀の演出(大気のグロー・星空)と動物写真のみ(`README.md`)。

## 6. ギャンブル

### 設問: アプリに現金を使うギャンブル、または模擬ギャンブル(カジノ、スロット等)が含まれていますか?
- **回答: いいえ(どちらも含まない)**
- 根拠: 該当機能なし。課金・くじ・ランダム報酬の仕組みもない(依存ライブラリに課金 SDK がない: `gradle/libs.versions.toml`)。

## 7. 差別的表現・ヘイト・ナチス関連(ドイツ向け設問)

### 設問: アプリに差別を助長する表現、ヘイトスピーチ、ナチス関連のシンボル等が含まれていますか?
- **回答: いいえ**
- 根拠: 該当なし。

## 8. ユーザー交流(ユーザー生成コンテンツ)

### 設問: アプリ自体に、ユーザーが他のユーザーと交流したり、コンテンツ(テキスト・画像・音声)をやり取りしたりできる機能がありますか?
- **回答: いいえ**
- 根拠: アカウント、投稿、コメント、チャット、共有サーバーの機能はない。ユーザー固有の状態は既読・ブックマーク(`data/UserData.kt`)とカメラ位置(`ui/MainViewModel.kt`)で、端末内のみ。`docs/PLAY_STORE.md` も「ユーザー生成コンテンツなし」で回答する方針。
- 補足(判断メモ): アプリ内ブラウザで表示する YouTube には、YouTube 側のコメント等の機能が存在しますが、それは第三者サイトの機能であってアプリが提供するものではありません。この設問は「アプリがネイティブに提供する機能」を問うものとして「いいえ」で回答します。審査で指摘された場合は「はい」に変えても年齢区分は変わらず、インタラクティブ要素の表示(「ユーザーが交流」)が付くだけです。

## 9. 位置情報の共有

### 設問: アプリは、ユーザーの現在の物理的な位置情報を他のユーザーと共有しますか?
- **回答: いいえ**
- 根拠: 位置情報権限を要求していない(`AndroidManifest.xml` は `INTERNET` のみ)。位置情報 API の使用箇所もない。地球儀上の「生息地」は同梱データの座標であり、ユーザーの位置ではない(`NOTICE.md` 「ピンの座標」)。

## 10. 個人情報の共有

### 設問: アプリは、ユーザーが提供した個人情報を第三者と共有しますか?
- **回答: いいえ**
- 根拠: 個人情報を入力する機能がなく、外部へ送信するコードも SDK もない(`docs/PRIVACY.md`、`gradle/libs.versions.toml`)。

## 11. デジタル商品の購入

### 設問: アプリ内でデジタル商品を購入できますか?(アプリ内購入、サブスクリプション等)
- **回答: いいえ**
- 根拠: 有料アプリとして Play で購入するのみで、アプリ内購入はない(`docs/PLAY_STORE.md` 「有料アプリの設定」)。Play Billing ライブラリは依存関係にない(`gradle/libs.versions.toml`)。

## 12. 外部リンク・インターネットへのアクセス

### 設問: アプリに外部のウェブサイトへのリンク、またはインターネット上のコンテンツへのアクセスが含まれていますか?
- **回答: はい**
- 根拠: 詳細画面の「Wikipedia」「動画」「写真の出典」がアプリ内ブラウザ(WebView)で Wikipedia / YouTube 検索結果 / Wikimedia Commons を開く(`README.md`、`ui/WebScreen.kt`、`data/Entry.kt`)。ページ内の http(s) リンクもアプリ内ブラウザで開けるため、開ける範囲はあらかじめ固定されていない(`WebScreen.kt` の `shouldOverrideUrlLoading` は http/https で `false` を返し、そのまま読み込む)。`docs/PLAY_STORE.md` も「外部リンク(Wikipedia / YouTube / Wikimedia Commons)あり」で回答する方針。
- 派生設問「無制限のインターネットアクセス(ブラウザ機能)を提供しますか?」の趣旨で聞かれた場合も、上記のとおりリンク先を制限していないため **はい** と答えておくのが安全です(年齢区分ではなくインタラクティブ要素の表示に影響するのみ)。

## 13. 広告

### 設問(表示される場合): アプリに広告(第三者の広告ネットワークを含む)が含まれていますか?
- **回答: いいえ**
- 根拠: 広告 SDK なし(`gradle/libs.versions.toml`)、`AD_ID` 権限なし(マージ済みマニフェスト確認済み)、`docs/PRIVACY.md` 「広告 SDK・解析 SDK を組み込んでいません」。

## 14. その他(表示される場合)

- **生成 AI 機能をユーザーに提供しますか?** → いいえ(アプリにコンテンツ生成機能はない。収録テキストは同梱の固定データ)。
- **実在の人物・事件・団体に関する内容?** → 該当なし(IUCN カテゴリの説明と動物の解説のみ: `ui/AboutDialog.kt`)。

---

## 想定される結果

- 上記の回答であれば、各レーティング機関で最も低い年齢区分(例: IARC 3+ / ESRB Everyone / PEGI 3 / USK 0 / CERO 相当の全年齢)になる見込みです。外部リンクの回答により「インターネットへのアクセス」「外部リンク」などのインタラクティブ要素の注記が付く可能性があります。
- 対象ユーザー層の設定(13 歳以上)はコンテンツレーティングとは別の項目で、レーティングが全年齢でも 13 歳以上を対象にできます(理由は「アプリのコンテンツ」の回答案を参照)。

## 回答前の確認事項

- 同梱写真 1,009 枚はパイプラインで Wikimedia Commons から取得したものです。「暴力・流血・死体」の設問に「いいえ」で答える前提として、標本や死骸の写真が紛れていないかを `tools/validate.py` の実行時や目視で確認しておくと安心です(リポジトリの文書には「各種の写真」とあるのみで、写真の被写体の種別までは明記されていません)。
- 「主な脅威」の説明文は事実の記述に留まる想定ですが、極端に生々しい記述がないことを一覧(`docs/CREDITS.md` ではなく `species.json` の `threats`)でざっと確認しておくと、審査で指摘されたときに説明しやすくなります。

## 3. アプリのコンテンツ(その他の申告)

# アプリのコンテンツ(App content)申告 回答案

Play Console 左メニュー「ポリシー → アプリのコンテンツ」に並ぶ各申告を、表示順に「設問 → 回答 → 根拠」で示します。項目名は日本語 UI を想定しています(版により表示順・名称が多少異なります)。

---

## 1. プライバシーポリシー

### 設問: プライバシーポリシーの URL を入力してください
- **回答: `https://hatake716.github.io/animal_planet/PRIVACY/`**(`docs/PRIVACY.md` を GitHub Pages で公開した URL)
- 根拠: `docs/PLAY_STORE.md` 「プライバシーポリシー」節。内容は `docs/PRIVACY.md`(個人情報を収集・送信しない / 端末内保存のみ / WebView の通信先と各サービスのポリシー適用 / `INTERNET` 権限の用途 / 削除方法 / 問い合わせ先)で、データセーフティの申告と整合している。
- 注意: 送信前に URL が実際に公開されていることを確認(リポジトリ Settings → Pages → `main` / `/docs`)。`docs/PLAY_STORE.md` の提出前チェックにも「プライバシーポリシー URL を公開して登録」がある。

## 2. 広告

### 設問: アプリに広告は含まれていますか?
- **回答: いいえ、アプリに広告は含まれていません**
- 根拠: 依存ライブラリに広告 SDK がない(`gradle/libs.versions.toml`: AndroidX / Compose / DataStore / junit のみ)。`docs/PRIVACY.md` 「広告 SDK・解析 SDK(トラッキング)を組み込んでいません」。`docs/PLAY_STORE.md` の方針(広告なし)。

## 3. アプリのアクセス権

### 設問: アプリの一部または全部の機能が、ログイン・会員資格・地域などによって制限されていますか?
- **回答: 特別なアクセス権なしで、すべての機能を利用できます**
- 根拠: ログイン・アカウント機能がない(認証 SDK なし、`data/UserData.kt` と `ui/MainViewModel.kt` の状態はすべて端末内)。地球儀・図鑑・写真・解説は同梱データでオフラインでも動作し(`README.md`)、審査担当者はインストール後すぐに全機能を確認できる。Wikipedia / YouTube / Commons の表示はインターネット接続のみ必要で、アカウントは不要。
- 補足: 有料アプリであること自体は「アクセス権の制限」には当たりません(Play での購入後に全機能が使える)。審査用のテスト用認証情報の入力は不要です。

## 4. コンテンツのレーティング

- **回答: IARC アンケートに回答**(内容は「コンテンツレーティング」の回答案を参照)。カテゴリは「参考資料、ニュース、または教育」相当、暴力・性的・薬物・ギャンブルすべて「いいえ」、ユーザー交流「いいえ」、外部リンク「はい」、アプリ内購入「いいえ」。
- 根拠: `docs/PLAY_STORE.md` 「コンテンツのレーティング」節。

## 5. ターゲット ユーザーおよびコンテンツ

### 設問 5-1: アプリの対象年齢層を選択してください
- **回答: 「13〜15 歳」「16〜17 歳」「18 歳以上」を選択し、12 歳以下の区分は選ばない(= 13 歳以上)**
- 根拠(13 歳以上を推奨する理由):
  1. アプリ内ブラウザで **YouTube の検索結果** と Wikipedia / Wikimedia Commons を表示し、ページ内のリンクもそのまま開けるため(`ui/WebScreen.kt` は http/https のリンクを制限せず読み込む)、表示される内容を開発者が管理できない。13 歳未満を対象に含めると Google Play の **ファミリー ポリシー**(子ども向けコンテンツ・広告・外部リンク等の制約、Designed for Families の要件)の対象になり、この構成では要件を満たせない(`docs/PLAY_STORE.md` 「対象ユーザー層は 13 歳以上を選ぶ」)。
  2. データセーフティ上は収集なしなので子ども向けでも問題ないが、上記のコンテンツ管理の観点だけで 13 歳以上にする。コンテンツレーティング自体は全年齢相当で矛盾しない(レーティングと対象年齢は別項目)。
  3. `docs/PLAY_STORE.md` 提出前チェック「対象ユーザー層を 13 歳以上に設定」。

### 設問 5-2(12 歳以下を含めない場合に表示): ストア掲載情報が意図せず子どもの興味を引く可能性がありますか?
- **回答(推奨): はい(意図せず子どもの興味を引く可能性がある)**
- 根拠: 動物の写真と地球儀を前面に出した教育カテゴリの図鑑であり、掲載情報(スクリーンショット `docs/images/01_globe.png`〜`06_photo.png`、説明文)が子どもの興味を引く可能性は否定しにくい。「はい」と答えても子ども向けアプリとして扱われるわけではなく、Play が掲載に「お子様向けではありません」等の注記を付ける場合がある程度で、ポリシー違反にはならない。「いいえ」と答えて Play 側が子どもの興味を引くと判断すると再申告を求められる可能性があるため、「はい」が安全。
- 補足: 掲載情報(説明文・スクリーンショット)を子ども向けに見せる表現(キャラクター・「こども」「キッズ」等の語)にしないこと。`docs/PLAY_STORE.md` の説明文案はその点で問題ない。

### 設問 5-3(12 歳以下を含めた場合のみ表示される設問: 個人情報の収集・広告・第三者 SDK 等)
- **回答: 表示されない(13 歳以上のみを選択するため)**

## 6. ニュースアプリ

### 設問: アプリはニュースアプリですか?
- **回答: いいえ**
- 根拠: 絶滅危惧種の図鑑(教育)であり、ニュース記事の配信・集約は行わない(`README.md`、ストアカテゴリ「教育」: `docs/PLAY_STORE.md`)。Wikipedia を表示するが百科事典であり、ニュースではない。

## 7. COVID-19 接触追跡アプリおよびステータスアプリ

### 設問: アプリは COVID-19 の接触追跡アプリまたはステータスアプリですか?
- **回答: 公開している COVID-19 接触追跡アプリまたはステータスアプリではありません**
- 根拠: 該当機能なし。

## 8. データセーフティ

- **回答: 「ユーザーデータを収集・共有しない」で申告**(詳細は「データセーフティ」の回答案を参照)。
- 根拠: `AndroidManifest.xml`(`INTERNET` のみ、`allowBackup="false"`)、`gradle/libs.versions.toml`(外部 SDK なし)、`data/UserData.kt` / `ui/MainViewModel.kt`(端末内保存のみ)、`ui/WebScreen.kt`(第三者サイトの表示のみ)、`docs/PLAY_STORE.md` 「データセーフティの記入」。

## 9. 政府アプリ

### 設問: アプリは政府機関によって、または政府機関のために開発されたものですか?
- **回答: いいえ**
- 根拠: 個人開発(GitHub `hatake716/animal_planet`、MIT License: `README.md`、`ui/AboutDialog.kt`)。NASA の地球画像や IUCN の評価区分を利用しているが、公的機関との関係はなく、素材の出典として表示しているだけ(`NOTICE.md`)。

## 10. 金融機能

### 設問: アプリは金融機能(個人向け融資、銀行、暗号資産、投資、送金など)を提供しますか?
- **回答: 金融機能を提供していません**
- 根拠: 該当機能なし。有料アプリの購入は Google Play の決済で、アプリ内には決済・課金機能がない(課金 SDK なし: `gradle/libs.versions.toml`)。

## 11. 健康

### 設問: アプリに健康関連の機能(医療、フィットネス、健康記録、Health Connect など)がありますか?
- **回答: 健康機能はありません**
- 根拠: 内容は野生動物の保全状況(IUCN カテゴリ)であり、人の健康・医療に関する機能や情報はない(`README.md`、`ui/AboutDialog.kt`)。

## 12. 広告 ID

### 設問: アプリは広告 ID を使用しますか?(Android 13 以上をターゲットとするアプリで表示)
- **回答: いいえ、アプリは広告 ID を使用しません**
- 根拠: targetSdk 36(`app/build.gradle.kts`)のため設問が表示される。マニフェストに `com.google.android.gms.permission.AD_ID` がなく(ソースおよびマージ済みマニフェストで確認)、広告・解析 SDK もないため、Play 側の自動チェックと矛盾しない。

## 13. その他の申告(該当する場合のみ表示される項目)

以下はいずれも **該当なし**。権限が `INTERNET` のみであることが共通の根拠(`AndroidManifest.xml`)。

| 項目 | 回答 | 根拠 |
|---|---|---|
| 写真と動画の権限 | 該当なし(`READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` を要求しない) | 写真はアプリ同梱 assets のみ |
| フォアグラウンド サービスの権限 | 該当なし | サービスを宣言していない |
| 正確なアラームの権限 | 該当なし | 使用しない |
| 全画面インテントの権限 | 該当なし | 使用しない |
| Health Connect | 該当なし | 健康機能なし |
| VPN サービス | 該当なし | 使用しない |
| ユーザー補助サービス | 該当なし | 使用しない |
| ファミリー(Designed for Families)への参加 | 参加しない | 対象年齢を 13 歳以上にするため |

---

## ストア掲載情報との整合(参考)

- アプリ名「地球儀で見る絶滅危惧種生物図鑑」、カテゴリ「教育」、説明文・スクリーンショット・アイコン・フィーチャーグラフィックは `docs/PLAY_STORE.md` の内容を使用。
- 説明文の数値(1,063 種、CR / EN / VU)は `README.md` および `ui/AboutDialog.kt` の実データ表示と一致させること(`docs/PLAY_STORE.md` 提出前チェック「species.json の種数・写真枚数がストア文言と一致」)。
- 有料アプリの価格・販売国の設定は「収益化」側で行い、無料から有料へは後から変更できないため初回から有料で公開する(`docs/PLAY_STORE.md` 「有料アプリの設定」)。

## 補足

## 確認した事実の出典(すべて絶対パス)

- `/home/takeshi/StudioProjects/animal_planet/README.md`、`NOTICE.md`、`docs/PLAY_STORE.md`、`docs/PRIVACY.md`
- `/home/takeshi/StudioProjects/animal_planet/app/src/main/AndroidManifest.xml`(権限は `INTERNET` のみ、`allowBackup="false"`、`glEsVersion 2.0` 必須)
- `/home/takeshi/StudioProjects/animal_planet/app/build.gradle.kts`(applicationId `io.github.hatake716.endangeredglobe`、versionCode 2 / 1.0.1、minSdk 30 / targetSdk 36、R8 有効)
- `/home/takeshi/StudioProjects/animal_planet/gradle/libs.versions.toml`(AndroidX / Compose / DataStore / junit のみ。広告・解析・課金・認証 SDK なし)
- `/home/takeshi/StudioProjects/animal_planet/app/src/main/kotlin/io/github/hatake716/animalplanet/ui/AboutDialog.kt`
- `/home/takeshi/StudioProjects/animal_planet/app/src/main/kotlin/io/github/hatake716/animalplanet/data/UserData.kt`(DataStore `user_data`: `read_ids` / `bookmark_ids`、ブックマーク上限 1,000)
- `/home/takeshi/StudioProjects/animal_planet/app/src/main/kotlin/io/github/hatake716/animalplanet/ui/MainViewModel.kt`(SharedPreferences `globe`: `lat` / `lon` / `alt` は地球儀カメラ位置)
- `/home/takeshi/StudioProjects/animal_planet/app/src/main/kotlin/io/github/hatake716/animalplanet/ui/WebScreen.kt`(WebView。JS / DOM storage 有効、http(s) リンクはアプリ内で読み込み、それ以外は外部へ、`addJavascriptInterface` なし)
- `/home/takeshi/StudioProjects/animal_planet/app/src/main/kotlin/io/github/hatake716/animalplanet/data/Entry.kt`(URL: `ja.m.wikipedia.org` / `en.m.wikipedia.org` / `m.youtube.com/results?search_query=<種名 絶滅危惧種>` / `commons.m.wikimedia.org/wiki/File:...`、共有用 `ja.wikipedia.org`)
- `/home/takeshi/StudioProjects/animal_planet/app/src/main/kotlin/io/github/hatake716/animalplanet/ui/App.kt` の `shareEntry`(`ACTION_SEND` text/plain: 和名(学名) [カテゴリ] 生息地 + Wikipedia URL)
- grep による確認: 位置情報 API・`HttpURLConnection` / OkHttp・クリップボード・カメラ・連絡先・電話・広告 ID の使用箇所なし
- マージ済みマニフェスト `/home/takeshi/StudioProjects/animal_planet/app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml`: `INTERNET` と AndroidX 自動生成の `io.github.hatake716.endangeredglobe.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`(アプリ内部用の署名権限、Play の申告対象ではない)のみ。`AD_ID` なし
- Google Play Console ヘルプ「データ セーフティ セクションの情報を提供する」(https://support.google.com/googleplay/android-developer/answer/10787469)で WebView の扱い(アプリが WebView のコード/挙動を制御している場合のみ収集に含める)と端末内処理の除外を確認

## 判断が必要な点・文書との差異

1. `docs/PLAY_STORE.md` 49 行目に「WebView の通信は『アプリの機能』欄で説明できる」とあるが、データセーフティ フォームに自由記述欄はない。説明はプライバシーポリシー(`docs/PRIVACY.md` の「通信」節、記載済み)とストアの説明文で行う前提で回答案を書いた。必要なら PLAY_STORE.md のこの行を修正するとよい。
2. 「ストア掲載情報が意図せず子どもの興味を引く可能性」の設問は文書に方針がないため、私の推奨(「はい」)として書いた。掲載情報を子ども向けに見せない前提であれば、どちらを選んでもポリシー上の問題はない。
3. IARC の「ユーザー交流」は、YouTube 側のコメント機能を「アプリの機能」とみなすかで解釈が分かれる。回答案は「アプリがネイティブに提供する機能ではない」として「いいえ」。
4. 「無制限のインターネットアクセス / 外部リンク」は、WebView がリンク先を制限していない(`WebScreen.kt`)ことから「はい」で統一した。年齢区分には影響しない。
5. 設問文は Play Console / IARC の版で変わるため、すべて「想定設問」として書いた。実際の画面では趣旨で対応付けること。
6. 同梱写真 1,009 枚の被写体(生体か標本かなど)は文書に明記がないため、「暴力・流血」設問の前提として目視確認を推奨事項に留めた。文書にない事実は断定していない。
7. 提出前に必須: プライバシーポリシー URL(`https://hatake716.github.io/animal_planet/PRIVACY/`)の公開、IARC 連絡用メールアドレスの用意、対象年齢 13 歳以上の設定、有料価格の設定(いずれも `docs/PLAY_STORE.md` の提出前チェックと一致)。
