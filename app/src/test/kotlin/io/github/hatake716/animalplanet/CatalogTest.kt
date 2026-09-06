package io.github.hatake716.animalplanet

import io.github.hatake716.animalplanet.data.Catalog
import io.github.hatake716.animalplanet.data.Entry
import io.github.hatake716.animalplanet.data.ImageCredit
import io.github.hatake716.animalplanet.data.RedListStatus
import io.github.hatake716.animalplanet.data.TaxonGroup
import io.github.hatake716.animalplanet.globe.MarkerFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {
    private fun e(
        id: Int, name: String, aliases: List<String> = emptyList(), sci: String = "", title: String = name,
        place: String = "", group: TaxonGroup = TaxonGroup.MAMMAL, status: RedListStatus = RedListStatus.EN,
        regionMask: Int = 1, imp: Int = 2, family: String = "", image: ImageCredit? = null,
    ) = Entry(id, name, aliases, sci, title, "", 0.0, 0.0, place, group, status, regionMask, imp, "", "", "", "", family, image)

    private val regions = listOf("日本", "東アジア", "アフリカ")
    private val catalog = Catalog(
        regions,
        listOf(
            e(0, "ジャイアントパンダ", listOf("パンダ", "大熊猫"), "Ailuropoda melanoleuca", place = "中国・四川省", status = RedListStatus.VU, regionMask = 0b010, imp = 3, family = "クマ科"),
            e(1, "レッサーパンダ", emptyList(), "Ailurus fulgens", place = "ヒマラヤ", regionMask = 0b010, family = "レッサーパンダ科"),
            e(2, "イリオモテヤマネコ", emptyList(), "Prionailurus bengalensis iriomotensis", place = "西表島", status = RedListStatus.CR, regionMask = 0b001, family = "ネコ科"),
            e(3, "ヤンバルクイナ", emptyList(), "Hypotaenidia okinawae", place = "沖縄島北部", group = TaxonGroup.BIRD, regionMask = 0b001, family = "クイナ科"),
            e(4, "マウンテンゴリラ", listOf("ヴィルンガのゴリラ"), "Gorilla beringei beringei", place = "ヴィルンガ山地", regionMask = 0b100, family = "ヒト科"),
            e(5, "ニシアフリカマナティー", emptyList(), "Trichechus senegalensis", place = "セネガル", group = TaxonGroup.MAMMAL, regionMask = 0b100, family = "マナティー科"),
        ),
    )

    @Test
    fun normalizeUnifiesKanaAndSeparators() {
        assertEquals(Catalog.normalize("ぱんだ"), Catalog.normalize("パンダ"))
        assertEquals(Catalog.normalize("ジャイアント・パンダ"), Catalog.normalize("ジャイアントパンダ"))
        assertEquals("abc", Catalog.normalize("ＡＢＣ"))
        assertEquals(Catalog.normalize("ヴィルンガ"), Catalog.normalize("ビルンガ"))
    }

    @Test
    fun searchRanksNameFirstThenAliasThenSciThenFamilyThenPlace() {
        val r = catalog.search("ぱんだ")
        assertEquals(listOf(0, 1), r.map { it.id }.take(2))     // 和名一致(知名度の高い方が先)
        val g = catalog.search("gorilla")
        assertEquals(4, g.first().id)                               // 学名
        val f = catalog.search("ネコ科")
        assertEquals(2, f.first().id)                               // 科
        val p = catalog.search("西表")
        assertEquals(2, p.first().id)                               // 地名
        assertTrue(catalog.search("存在しない語").isEmpty())
        assertTrue(catalog.search("").isEmpty())
    }

    @Test
    fun filterEmptyMeansAll() {
        val f = MarkerFilter()
        assertTrue(catalog.entries.all { f.accepts(it) })
        assertFalse(f.groupFilterActive())
        assertFalse(f.regionFilterActive(regions.size))
    }

    @Test
    fun filterByGroupAndRegionUsesBitmask() {
        val birds = MarkerFilter(groups = setOf(TaxonGroup.BIRD))
        assertEquals(listOf(3), catalog.entries.filter { birds.accepts(it) }.map { it.id })
        val japan = MarkerFilter(regions = setOf(0))
        assertEquals(listOf(2, 3), catalog.entries.filter { japan.accepts(it) }.map { it.id })
        val africaMammals = MarkerFilter(groups = setOf(TaxonGroup.MAMMAL), regions = setOf(2))
        assertEquals(listOf(4, 5), catalog.entries.filter { africaMammals.accepts(it) }.map { it.id })
        // 複数地域の種はどれか 1 つでもチェックされていれば表示
        val multi = catalog.entries[0].copy(regionMask = 0b011)
        assertTrue(MarkerFilter(regions = setOf(0)).accepts(multi))
        assertTrue(MarkerFilter(regions = setOf(1)).accepts(multi))
        assertFalse(MarkerFilter(regions = setOf(2)).accepts(multi))
    }

    @Test
    fun selectAllIsSameAsNoFilter() {
        val all = MarkerFilter(groups = TaxonGroup.entries.toSet(), regions = regions.indices.toSet())
        assertFalse(all.groupFilterActive())
        assertFalse(all.regionFilterActive(regions.size))
        assertTrue(catalog.entries.all { all.accepts(it) })
    }

    @Test
    fun imageCreditAndRegionsHelpers() {
        // JSON パーサ(android.util.JsonReader)は JVM 単体テストでは使えないため、モデルの補助関数だけ検証する
        val credit = ImageCredit("Grosser Panda.JPG", "J. Patrick Fischer", "CC BY-SA 3.0", "https://creativecommons.org/licenses/by-sa/3.0/")
        val panda = catalog.byId(0)!!.copy(image = credit, regionMask = 0b010)
        assertEquals("img/0.jpg", panda.imageAsset)
        assertEquals("J. Patrick Fischer / CC BY-SA 3.0", credit.line)
        assertEquals("https://commons.m.wikimedia.org/wiki/File:Grosser_Panda.JPG", credit.commonsUrl)
        assertTrue(panda.inRegion(1))
        assertFalse(panda.inRegion(0))
        assertEquals("Wikimedia Commons / CC0 1.0", ImageCredit("a.jpg", "", "CC0 1.0", "").line)
        val kuina = catalog.byId(3)!!
        assertNull(kuina.image)
        assertNull(kuina.imageAsset)
        assertNull(kuina.enWikipediaUrl)
        assertEquals("", kuina.yomi)
        assertEquals("クイナ科", kuina.taxonomyLine)
    }

    @Test
    fun urlsAreEncoded() {
        val e = catalog.byId(0)!!
        assertEquals("https://ja.m.wikipedia.org/wiki/%E3%82%B8%E3%83%A3%E3%82%A4%E3%82%A2%E3%83%B3%E3%83%88%E3%83%91%E3%83%B3%E3%83%80", e.wikipediaUrl)
        assertTrue(e.youtubeSearchUrl.startsWith("https://m.youtube.com/results?search_query="))
        assertFalse(e.youtubeSearchUrl.contains(" "))
    }
}
