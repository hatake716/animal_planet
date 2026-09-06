# リリース手順(Google Play)

## 署名鍵

リリース署名はローカルの鍵で行います(**リポジトリには含めない**。紛失すると Play への更新ができなくなるため厳重に保管)。

- 鍵: `animal-planet-release.jks`(プロジェクト直下、`.gitignore` 済み)
- 設定: `keystore.properties`(同、`.gitignore` 済み)
  ```
  storeFile=animal-planet-release.jks
  storePassword=...
  keyAlias=animalplanet
  keyPassword=...
  ```
- 鍵の再生成が必要な場合(初回公開前のみ可):
  ```bash
  keytool -genkeypair -v -keystore animal-planet-release.jks \
    -alias animalplanet -keyalg RSA -keysize 4096 -validity 10000 \
    -storepass <pass> -keypass <pass> -dname "CN=hatake716, C=JP"
  ```

Google Play アプリ署名(Play App Signing)を利用する場合、この鍵は「アップロード鍵」として使えます。

## ビルド

```bash
# バージョンを更新(app/build.gradle.kts の versionCode/versionName)
./gradlew bundleRelease      # 署名済み AAB: app/build/outputs/bundle/release/app-release.aab
./gradlew assembleRelease    # 動作確認用の署名済み APK
```

R8 縮小・リソース縮小を有効にしています(`isMinifyEnabled = true` / `isShrinkResources = true`)。
JSON は `android.util.JsonReader` の手書きパーサのみ使用しており、リフレクション依存の keep ルールは不要です。

同梱アセット(地球タイル約 42MB、写真約 1,000 枚)により AAB は 100MB を超えます。Google Play の AAB 上限(基本モジュール 200MB)の範囲内です。

## Play Console 提出

### アプリの内容
- アプリ名: 地球儀で見る絶滅危惧種生物図鑑
- カテゴリ: 教育
- 対象年齢: 全年齢(暴力・性的表現なし)。ただし Play の「対象ユーザー層」は **13 歳以上**にする(YouTube をアプリ内ブラウザで表示するため、13 歳未満を含めると Families ポリシーの対象になる)
- 有料アプリとして販売する場合は、Play Console の「有料」設定と販売国・価格を設定する。

### データセーフティ(Data safety)の記入
- **データ収集: なし**(個人情報・識別子・位置情報の収集・送信をしない)
- **データ共有: なし**
- 端末内保存(既読・ブックマーク)は「収集」に該当しない(端末外へ出ないため)
- Wikipedia / YouTube / Wikimedia Commons のページはアプリ内ブラウザで表示するため、それらの閲覧は各サービスへの通信になる旨を「アプリの機能」欄で説明できる

### プライバシーポリシー
- `docs/PRIVACY.md` の内容を公開 URL(GitHub Pages: `https://hatake716.github.io/animal_planet/PRIVACY`)に置き、その URL を Play Console に登録する。

### 権限
- `INTERNET` のみ。記事・動画・出典ページのアプリ内表示に必要。機微な権限(位置情報・連絡先・カメラ等)は使用しない。

### コンテンツの出典(知的財産に関する審査への備え)
- 写真: Wikimedia Commons の自由ライセンス画像のみ(CC0 / PD / CC BY / CC BY-SA)。作者・ライセンスはアプリ内と `docs/CREDITS.md` に明記。
- 解説: Wikipedia(CC BY-SA 4.0、本文は同梱せずブラウザ表示)。分類・評価: Wikidata(CC0)。地図: NASA Blue Marble(パブリックドメイン)。
- 詳細は `NOTICE.md` を参照。

## 動作確認

- `assembleRelease` の APK を実機・エミュレータへインストールし、R8 縮小後もクラッシュしないこと、地球儀・検索・絞り込み・詳細・写真・ブックマーク・Wikipedia/動画の表示が動くことを確認する。
