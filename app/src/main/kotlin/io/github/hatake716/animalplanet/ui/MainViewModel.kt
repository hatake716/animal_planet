package io.github.hatake716.animalplanet.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.hatake716.animalplanet.data.Catalog
import io.github.hatake716.animalplanet.data.Entry
import io.github.hatake716.animalplanet.data.TaxonGroup
import io.github.hatake716.animalplanet.data.UserDataRepository
import io.github.hatake716.animalplanet.globe.MarkerFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 画面全体の状態。回転などで Activity が再生成されても保持される。 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    var catalog by mutableStateOf<Catalog?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set

    var selectedId by mutableStateOf(-1)
    var query by mutableStateOf("")
    var searchResults by mutableStateOf<List<Entry>>(emptyList())
        private set

    /**
     * 地球儀の絞り込み。起動時は空集合＝全件表示(「全選択」相当)。
     * 絞り込みシートを開いたときの初期状態も、この空集合＝全チップ未選択から始まる。
     */
    var filter by mutableStateOf(MarkerFilter())
    var pickCandidates by mutableStateOf<List<Entry>>(emptyList())
    var webUrl by mutableStateOf<String?>(null)
    var webTitle by mutableStateOf("")
    var showFilter by mutableStateOf(false)
    var showList by mutableStateOf(false)
    var showBookmarks by mutableStateOf(false)
    var showAbout by mutableStateOf(false)
    var showCredits by mutableStateOf(false)

    /** 写真の全画面表示中の種 id(-1 なら非表示)。 */
    var photoOfId by mutableStateOf(-1)

    /** 既読(一度でも詳細を開いた)種の id 集合。 */
    var readIds by mutableStateOf<Set<Int>>(emptySet())
        private set

    /** ブックマークした種の id リスト(追加順)。 */
    var bookmarkIds by mutableStateOf<List<Int>>(emptyList())
        private set

    private val userData = UserDataRepository(app)

    var detailExpanded by mutableStateOf(false)

    /** カメラの復元用(度、高度)。 */
    var savedLat = 20.0
    var savedLon = 100.0
    var savedAlt = 0.0

    private val prefs = app.getSharedPreferences("globe", Context.MODE_PRIVATE)

    init {
        savedLat = prefs.getFloat("lat", 20f).toDouble()
        savedLon = prefs.getFloat("lon", 100f).toDouble()
        savedAlt = prefs.getFloat("alt", 0f).toDouble()
        viewModelScope.launch {
            try {
                val c = withContext(Dispatchers.IO) { Catalog.load(getApplication()) }
                catalog = c
            } catch (e: Exception) {
                loadError = e.toString()
            }
        }
        // 既読・ブックマークを DataStore から監視
        userData.readIds.onEach { readIds = it }.launchIn(viewModelScope)
        userData.bookmarkIds.onEach { bookmarkIds = it }.launchIn(viewModelScope)
    }

    fun isRead(id: Int): Boolean = id in readIds
    fun isBookmarked(id: Int): Boolean = id in bookmarkIds

    fun toggleBookmark(id: Int) {
        viewModelScope.launch { userData.toggleBookmark(id) }
    }

    fun removeBookmark(id: Int) {
        viewModelScope.launch { userData.removeBookmark(id) }
    }

    val selectedEntry: Entry? get() = catalog?.byId(selectedId)

    fun select(id: Int) {
        selectedId = id
        detailExpanded = false
        // 詳細を開いた(地球儀・一覧・検索いずれからでも)時点で既読にする
        if (id >= 0 && id !in readIds) viewModelScope.launch { userData.markRead(id) }
    }

    fun clearSelection() {
        selectedId = -1
        detailExpanded = false
    }

    fun updateQuery(q: String) {
        query = q
        val c = catalog
        searchResults = if (c == null || q.isBlank()) emptyList() else c.search(q)
    }

    fun saveCamera(latDeg: Double, lonDeg: Double, alt: Double) {
        savedLat = latDeg; savedLon = lonDeg; savedAlt = alt
        prefs.edit().putFloat("lat", latDeg.toFloat()).putFloat("lon", lonDeg.toFloat()).putFloat("alt", alt.toFloat()).apply()
    }

    fun openWikipedia(e: Entry) {
        webTitle = e.name
        webUrl = e.wikipediaUrl
    }

    fun openEnglishWikipedia(e: Entry) {
        val u = e.enWikipediaUrl ?: return
        webTitle = e.enTitle
        webUrl = u
    }

    fun openYoutube(e: Entry) {
        webTitle = "YouTube: ${e.name}"
        webUrl = e.youtubeSearchUrl
    }

    fun openUrl(title: String, url: String) {
        webTitle = title
        webUrl = url
    }

    fun closeWeb() {
        webUrl = null
    }

    fun randomEntry(): Entry? {
        val c = catalog ?: return null
        val f = filter
        val pool = c.entries.filter { f.accepts(it) }
        if (pool.isEmpty()) return null
        return pool.random()
    }

    /** 絞り込みシートの「全選択」。分類・生息地を全項目チェック状態にする。 */
    fun selectAllFilter() {
        val regionCount = catalog?.regions?.size ?: 0
        filter = MarkerFilter(groups = TaxonGroup.entries.toSet(), regions = (0 until regionCount).toSet())
    }

    /** 絞り込みシートの「全解除」。分類・生息地のチェックを全部外す。 */
    fun deselectAllFilter() {
        filter = MarkerFilter()
    }

    fun isFilterActive(): Boolean {
        val f = filter
        return f.groupFilterActive() || f.regionFilterActive(catalog?.regions?.size ?: 0)
    }
}
