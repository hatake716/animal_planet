package io.github.hatake716.animalplanet.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.hatake716.animalplanet.BuildConfig
import io.github.hatake716.animalplanet.data.Catalog
import io.github.hatake716.animalplanet.data.RedListStatus

/** 公開しているプライバシーポリシー(GitHub Pages)。Play のポリシーでアプリ内からも参照できる必要がある。 */
const val PRIVACY_POLICY_URL = "https://hatake716.github.io/animal_planet/PRIVACY/"

@Composable
fun AboutDialog(catalog: Catalog, onDismiss: () -> Unit, onCredits: () -> Unit, onPrivacy: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
        dismissButton = { TextButton(onClick = onCredits) { Text("写真の出典一覧") } },
        title = { Text("地球儀で見る絶滅危惧種生物図鑑") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("バージョン ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                val counts = RedListStatus.entries.joinToString("、") { s -> "${s.code} ${"%,d".format(catalog.entries.count { it.status == s })}" }
                Text(
                    "IUCN レッドリストで絶滅のおそれがあると評価された動物 ${"%,d".format(catalog.entries.size)} 種($counts)を、" +
                        "代表的な生息地にピンとして地球儀に配置しました。ピンをタップすると写真と解説が表示され、" +
                        "Wikipedia の記事と関連する YouTube 動画の検索結果をアプリ内で開けます。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text("操作", fontWeight = FontWeight.Bold)
                Text("ドラッグ: 回転 ／ ピンチ: 拡大縮小 ／ ダブルタップ: 拡大 ／ ピンをタップ: 詳細 ／ 写真をタップ: 拡大表示", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Text("絶滅危惧のカテゴリ", fontWeight = FontWeight.Bold)
                Text(
                    "CR: 絶滅危惧IA類(ごく近い将来における野生での絶滅の危険性が極めて高い)\n" +
                        "EN: 絶滅危惧IB類(近い将来における野生での絶滅の危険性が高い)\n" +
                        "VU: 絶滅危惧II類(絶滅の危険が増大している)",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                Text("データと出典", fontWeight = FontWeight.Bold)
                Text(
                    "・写真: Wikimedia Commons の自由ライセンス(CC0 / パブリックドメイン / CC BY / CC BY-SA)の画像。作者とライセンスは各写真と「写真の出典一覧」に表示します。\n" +
                        "・解説・生息地・脅威: Wikipedia 日本語版・英語版の記事冒頭をもとに要約したものです(記事本文は CC BY-SA 4.0。本文はアプリ内ブラウザで表示)。\n" +
                        "・和名・学名・分類・IUCN カテゴリ: Wikidata(CC0)。評価は Wikidata に記録された時点のもので、最新の IUCN レッドリストと異なる場合があります。\n" +
                        "・ピンの位置: 各種の代表的な生息地 1 か所です。広く分布する種は代表地点に置いています。位置の推定には GBIF の出現記録(CC0 / CC BY 4.0)も参考にしました。\n" +
                        "・地球画像: NASA Blue Marble: Next Generation(パブリックドメイン)。\n" +
                        "・動画: 種名で YouTube を検索した結果をアプリ内ブラウザで表示します。\n" +
                        "誤りにお気づきの場合は GitHub の Issue でお知らせください。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                Text("ライセンス", fontWeight = FontWeight.Bold)
                Text("アプリ本体: MIT License\nソースコード: github.com/hatake716/animal_planet", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Text("プライバシー", fontWeight = FontWeight.Bold)
                Text("個人情報を収集・送信しません。既読・ブックマークは端末内にのみ保存されます。", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onPrivacy, contentPadding = PaddingValues(0.dp)) { Text("プライバシーポリシーを開く") }
            }
        },
    )
}
