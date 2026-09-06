package io.github.hatake716.animalplanet.data

import androidx.compose.ui.graphics.Color

/** 生物の分類(ピンの色・絞り込みの単位)。 */
enum class TaxonGroup(val label: String, val color: Color) {
    MAMMAL("哺乳類", Color(0xFFFF8A65)),
    BIRD("鳥類", Color(0xFFFFD54F)),
    REPTILE("爬虫類", Color(0xFF9CCC65)),
    AMPHIBIAN("両生類", Color(0xFFBA68C8)),
    FISH("魚類", Color(0xFF4FC3F7)),
    INVERTEBRATE("無脊椎動物", Color(0xFFF06292));

    companion object {
        fun fromCode(code: Int): TaxonGroup = entries.getOrElse(code) { INVERTEBRATE }
    }
}

/** IUCN レッドリストの絶滅危惧カテゴリ(Wikidata 由来)。 */
enum class RedListStatus(val code: String, val english: String, val label: String, val color: Color) {
    CR("CR", "Critically Endangered", "絶滅危惧IA類(近絶滅種)", Color(0xFFE53935)),
    EN("EN", "Endangered", "絶滅危惧IB類(絶滅危惧種)", Color(0xFFFB8C00)),
    VU("VU", "Vulnerable", "絶滅危惧II類(危急種)", Color(0xFFFDD835));

    companion object {
        fun fromCode(code: String): RedListStatus = entries.firstOrNull { it.code == code } ?: VU
    }
}

/**
 * 写真の出典情報。写真はすべて Wikimedia Commons の自由ライセンス(CC0 / パブリックドメイン / CC BY / CC BY-SA)の
 * 画像で、作者とライセンスをアプリ内に表示する。
 */
data class ImageCredit(
    /** Commons のファイル名(File: を除く)。 */
    val file: String,
    val author: String,
    val license: String,
    val licenseUrl: String,
) {
    private fun encodedFile(): String =
        java.net.URLEncoder.encode(file.replace(' ', '_'), "UTF-8").replace("+", "%20")

    /** Commons のファイルページ(出典・ライセンスの詳細)。 */
    val commonsUrl: String get() = "https://commons.m.wikimedia.org/wiki/File:" + encodedFile()

    /** 表示用の 1 行クレジット。 */
    val line: String get() = buildString {
        if (author.isNotBlank()) append(author) else append("Wikimedia Commons")
        append(" / ")
        append(license)
    }
}

/**
 * 絶滅危惧種 1 件。名称・学名・分類・評価は Wikidata、解説・生息地・脅威は Wikipedia の記事冒頭を要約したもの。
 * 座標は代表的な生息地の 1 点(広域に分布する種は代表地点)。
 */
data class Entry(
    val id: Int,
    /** 和名。 */
    val name: String,
    val aliases: List<String>,
    /** 学名。 */
    val sci: String,
    /** 日本語版 Wikipedia の記事名。 */
    val wikiTitle: String,
    /** 英語版 Wikipedia の記事名(なければ空)。 */
    val enTitle: String,
    val lat: Double,
    val lon: Double,
    /** 代表的な生息地の名前(例: 中国・四川省の山地)。 */
    val place: String,
    val group: TaxonGroup,
    val status: RedListStatus,
    /** 生息地域のビットマスク(Catalog.regions の添字)。複数地域にまたがる種は複数ビット。 */
    val regionMask: Int,
    /** 知名度(1〜3)。ラベル表示の優先度。 */
    val importance: Int,
    /** Wikipedia の記事冒頭を要約した解説。 */
    val desc: String,
    /** 生息環境・分布の説明(1〜2 文)。 */
    val habitat: String,
    /** 主な減少要因(1 文、なければ空)。 */
    val threats: String,
    /** 目(もく)の和名。 */
    val orderJa: String,
    /** 科の和名。 */
    val familyJa: String,
    /** 同梱写真(assets/img/{id}.jpg)の出典。写真がなければ null。 */
    val image: ImageCredit?,
    /** 読み(ひらがな/カタカナ)。五十音順の並べ替えに使う。空なら和名を使う。 */
    val yomi: String = "",
) {
    private fun encoded(s: String): String =
        java.net.URLEncoder.encode(s.replace(' ', '_'), "UTF-8").replace("+", "%20")

    /** モバイル版 Wikipedia(アプリ内 WebView 用)。 */
    val wikipediaUrl: String get() = "https://ja.m.wikipedia.org/wiki/" + encoded(wikiTitle)

    /** デスクトップ版 Wikipedia(共有用)。 */
    val wikipediaDesktopUrl: String get() = "https://ja.wikipedia.org/wiki/" + encoded(wikiTitle)

    /** 英語版 Wikipedia(あれば)。 */
    val enWikipediaUrl: String? get() = if (enTitle.isBlank()) null else "https://en.m.wikipedia.org/wiki/" + encoded(enTitle)

    /** この種に関連する YouTube 動画の検索結果(アプリ内 WebView で開く)。 */
    val youtubeSearchUrl: String
        get() {
            val q = java.net.URLEncoder.encode("$name 絶滅危惧種", "UTF-8")
            return "https://m.youtube.com/results?search_query=$q"
        }

    val hasWikipedia: Boolean get() = wikiTitle.isNotBlank()

    /** 同梱写真のアセットパス。 */
    val imageAsset: String? get() = if (image != null) "img/$id.jpg" else null

    fun inRegion(regionIndex: Int): Boolean = (regionMask shr regionIndex) and 1 == 1

    /** 目・科の表示(例: 食肉目 クマ科)。 */
    val taxonomyLine: String get() = listOf(orderJa, familyJa).filter { it.isNotBlank() }.joinToString(" ")
}
