package io.github.hatake716.animalplanet.data

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import java.io.InputStreamReader
import java.text.Normalizer

/**
 * assets/species.json を読み込んだ図鑑カタログ。
 *
 * JSON 形式(サイズ削減のため配列):
 * {
 *   "regions": ["日本", "東アジア", ...],
 *   "entries": [[id, name, "alias1|alias2", sci, wikiTitle, enTitle, lat, lon, place,
 *                group, "CR"|"EN"|"VU", regionMask, importance, desc, habitat, threats,
 *                orderJa, familyJa, imgFile, imgAuthor, imgLicense, imgLicenseUrl, yomi], ...]
 * }
 * imgFile が空なら写真なし。末尾の yomi は任意。
 */
class Catalog(
    val regions: List<String>,
    val entries: List<Entry>,
) {
    private val byId: Map<Int, Entry> = entries.associateBy { it.id }

    /** 検索キー: 各フィールドを SEP 区切りで連結。fieldStarts[i] は種 i のフィールド境界位置。 */
    private val searchKeys: List<String>
    private val fieldStarts: List<IntArray>

    init {
        val keys = ArrayList<String>(entries.size)
        val starts = ArrayList<IntArray>(entries.size)
        for (e in entries) {
            val sb = StringBuilder()
            val bounds = ArrayList<Int>()
            // フィールド 0=和名, 1=別名, 2=学名, 3=Wikipedia題名, 4=目・科, 5=生息地名
            bounds.add(0); sb.append(normalize(e.name)); sb.append(SEP)
            bounds.add(sb.length); e.aliases.forEach { sb.append(normalize(it)); sb.append(SEP) }
            bounds.add(sb.length); sb.append(normalize(e.sci)); sb.append(SEP)
            bounds.add(sb.length); sb.append(normalize(e.wikiTitle)); sb.append(SEP)
            bounds.add(sb.length); sb.append(normalize(e.orderJa)); sb.append(SEP); sb.append(normalize(e.familyJa)); sb.append(SEP)
            bounds.add(sb.length); sb.append(normalize(e.place))
            keys.add(sb.toString())
            starts.add(bounds.toIntArray())
        }
        searchKeys = keys
        fieldStarts = starts
    }

    fun byId(id: Int): Entry? = byId[id]

    private val indexById: Map<Int, Int> = entries.withIndex().associate { it.value.id to it.index }

    /**
     * 正規化済みの検索語 [normalizedQuery](normalize 済み)が種 [e] のいずれかのフィールドに含まれるか。
     * 一覧内検索用: 事前計算した検索キーを使うので、1 文字入力ごとに全件を正規化し直さない。
     */
    fun matches(e: Entry, normalizedQuery: String): Boolean {
        if (normalizedQuery.isEmpty()) return true
        val i = indexById[e.id] ?: return false
        return searchKeys[i].contains(normalizedQuery)
    }

    /** 部分一致検索。和名→別名→学名→題名→目・科→地名の順で一致したフィールドと知名度で並べる。 */
    fun search(query: String, limit: Int = 80): List<Entry> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        val scored = ArrayList<Pair<Int, Entry>>()
        for (i in entries.indices) {
            val key = searchKeys[i]
            val pos = key.indexOf(q)
            if (pos < 0) continue
            val e = entries[i]
            val bounds = fieldStarts[i]
            var field = 0
            for (b in bounds.indices) if (pos >= bounds[b]) field = b
            val atStart = pos == 0 || key[pos - 1] == SEP
            val score = field * 10 + (if (atStart) 0 else 5) + (3 - e.importance)
            scored.add(score to e)
        }
        return scored.sortedWith(compareBy({ it.first }, { it.second.name.length }, { it.second.id }))
            .map { it.second }.take(limit)
    }

    companion object {
        private const val SEP = ''

        fun load(context: Context): Catalog {
            context.assets.open("species.json").use { input ->
                return parse(JsonReader(InputStreamReader(input, Charsets.UTF_8)))
            }
        }

        fun parse(reader: JsonReader): Catalog {
            var regions: List<String> = emptyList()
            val entries = ArrayList<Entry>(1500)
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "regions" -> regions = readStringArray(reader)
                    "entries" -> {
                        reader.beginArray()
                        while (reader.hasNext()) entries.add(readEntry(reader))
                        reader.endArray()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            return Catalog(regions, entries)
        }

        private fun readStringArray(reader: JsonReader): List<String> {
            val out = ArrayList<String>()
            reader.beginArray()
            while (reader.hasNext()) out.add(reader.nextString())
            reader.endArray()
            return out
        }

        private fun readEntry(reader: JsonReader): Entry {
            reader.beginArray()
            val id = reader.nextInt()
            val name = reader.nextString()
            val aliases = reader.nextString().split('|').filter { it.isNotBlank() }
            val sci = reader.nextString()
            val wikiTitle = reader.nextString()
            val enTitle = reader.nextString()
            val lat = reader.nextDouble()
            val lon = reader.nextDouble()
            val place = reader.nextString()
            val group = TaxonGroup.fromCode(reader.nextInt())
            val status = RedListStatus.fromCode(reader.nextString())
            val regionMask = reader.nextInt()
            val importance = reader.nextInt()
            val desc = reader.nextString()
            val habitat = reader.nextString()
            val threats = reader.nextString()
            val orderJa = reader.nextString()
            val familyJa = reader.nextString()
            val imgFile = reader.nextString()
            val imgAuthor = reader.nextString()
            val imgLicense = reader.nextString()
            val imgLicenseUrl = reader.nextString()
            var yomi = ""
            while (reader.hasNext()) {
                if (reader.peek() == JsonToken.STRING) yomi = reader.nextString() else reader.skipValue()
            }
            reader.endArray()
            val image = if (imgFile.isBlank()) null else ImageCredit(imgFile, imgAuthor, imgLicense, imgLicenseUrl)
            return Entry(
                id, name, aliases, sci, wikiTitle, enTitle, lat, lon, place, group, status, regionMask, importance,
                desc, habitat, threats, orderJa, familyJa, image, yomi,
            )
        }

        /** 検索用正規化: NFKC、小文字、ひらがな→カタカナ、「ヴ」→バ行、区切り記号除去。 */
        fun normalize(s: String): String {
            val n = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase()
            val sb = StringBuilder(n.length)
            var i = 0
            while (i < n.length) {
                var c = n[i]
                if (c in 'ぁ'..'ゖ') c += 0x60 // ひらがな→カタカナ(ゔ→ヴ を含む)
                if (c == 'ヴ') {
                    val nx = if (i + 1 < n.length) n[i + 1] else ' '
                    val mapped = when (nx) {
                        'ァ', 'ぁ' -> 'バ'
                        'ィ', 'ぃ' -> 'ビ'
                        'ェ', 'ぇ' -> 'ベ'
                        'ォ', 'ぉ' -> 'ボ'
                        'ゥ', 'ぅ' -> 'ブ'
                        else -> ' '
                    }
                    if (mapped != ' ') { sb.append(mapped); i += 2; continue }
                    sb.append('ブ'); i++; continue
                }
                when (c) {
                    ' ', '　', '=', '・', '＝', '･', '-', '－', 'ー', '、', '，', ',', '.', '。',
                    '「', '」', '（', '）', '(', ')', '/', '／', '"', '\'' -> Unit
                    else -> sb.append(c)
                }
                i++
            }
            return sb.toString()
        }
    }
}
