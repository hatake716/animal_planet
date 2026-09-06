package io.github.hatake716.animalplanet.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.hatake716.animalplanet.data.Catalog
import io.github.hatake716.animalplanet.data.Entry
import io.github.hatake716.animalplanet.data.RedListStatus
import io.github.hatake716.animalplanet.data.TaxonGroup

/** 並び順。 */
private enum class SortMode(val label: String) { GROUP("分類順"), KANA("五十音順"), STATUS("危機度順") }

/**
 * 種一覧の共通画面。全件表示にもブックマーク表示にも使う。
 * - 並び替え: 分類順(既定) / 五十音順 / 危機度順(CR→EN→VU)
 * - 絞り込み: 分類・生息地(地球儀と同じく、チェックした項目だけ表示。全選択・全解除あり)
 * - 各行に写真・既読マークとブックマークのトグルを表示
 *
 * @param source 表示対象(全件、またはブックマーク済み)
 * @param title 画面タイトル
 * @param emptyMessage source が空のときの案内
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryListScreen(
    catalog: Catalog,
    source: List<Entry>,
    title: String,
    emptyMessage: String,
    readIds: Set<Int>,
    bookmarkIds: List<Int>,
    onClose: () -> Unit,
    onSelect: (Entry) -> Unit,
    onToggleBookmark: (Int) -> Unit,
) {
    BackHandler(onBack = onClose)
    var sort by rememberSaveable { mutableStateOf(SortMode.GROUP) }
    var showFilterRow by rememberSaveable { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    // チェックした項目だけ表示。空(何もチェックしていない)は絞り込みなし＝全件。地球儀側の MarkerFilter と同じ意味。
    // Activity 再生成(フォントサイズ変更など)でも検索語・並び順と一緒に残るよう rememberSaveable にする。
    var groupOrdinals by rememberSaveable { mutableStateOf<List<Int>>(emptyList()) }
    var regionList by rememberSaveable { mutableStateOf<List<Int>>(emptyList()) }
    val groupFilter: Set<TaxonGroup> = remember(groupOrdinals) { groupOrdinals.map { TaxonGroup.entries[it] }.toSet() }
    val regionFilter: Set<Int> = remember(regionList) { regionList.toSet() }
    fun setGroups(set: Set<TaxonGroup>) { groupOrdinals = set.map { it.ordinal }.sorted() }
    fun setRegions(set: Set<Int>) { regionList = set.sorted() }
    val listState = rememberLazyListState()
    // 五十音順のキーはカタログ単位で 1 回だけ計算する
    val kanaKeys = remember(catalog) { catalog.entries.associate { it.id to sortKey(if (it.yomi.isNotEmpty()) it.yomi else it.name) } }

    val rows = remember(source, sort, groupFilter, regionFilter, query, catalog) {
        val q = Catalog.normalize(query)
        val regionBits = regionFilter.fold(0) { acc, r -> acc or (1 shl r) }
        val filtered = source.filter { e ->
            if (groupFilter.isNotEmpty() && e.group !in groupFilter) return@filter false
            if (regionFilter.isNotEmpty() && (e.regionMask and regionBits) == 0) return@filter false
            if (q.isNotEmpty() && !catalog.matches(e, q)) return@filter false
            true
        }
        buildRows(filtered, sort, kanaKeys)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("$title(${"%,d".format(source.size)})", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "閉じる") } },
                actions = {
                    IconButton(onClick = {
                        showSearch = !showSearch
                        if (!showSearch) query = ""
                    }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "この一覧内を検索",
                            tint = if (query.isNotEmpty()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = { showFilterRow = !showFilterRow }) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = "絞り込み",
                            // 一部だけ選択しているときだけ「絞り込み中」。空も全選択も絞っていない扱い。
                            tint = if (
                                (groupFilter.isNotEmpty() && groupFilter.size < TaxonGroup.entries.size) ||
                                (regionFilter.isNotEmpty() && regionFilter.size < catalog.regions.size)
                            ) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (showSearch) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "クリア") }
                        }
                    },
                    placeholder = { Text("この一覧内を検索(和名・学名・科)") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                for ((i, m) in SortMode.entries.withIndex()) {
                    SegmentedButton(selected = sort == m, onClick = { sort = m }, shape = SegmentedButtonDefaults.itemShape(i, SortMode.entries.size)) {
                        Text(m.label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (showFilterRow) {
                // 全選択の下に全解除(地球儀の絞り込みシートと同じ配置・同じ操作)
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (groupFilter.isEmpty() && regionFilter.isEmpty()) "見たい項目にチェックを入れてください(未選択の欄はすべて表示)"
                        else "チェックした項目だけを表示しています",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = {
                            setGroups(TaxonGroup.entries.toSet())
                            setRegions(catalog.regions.indices.toSet())
                        }) { Text("全選択", style = MaterialTheme.typography.labelMedium) }
                        TextButton(onClick = {
                            setGroups(emptySet())
                            setRegions(emptySet())
                        }) { Text("全解除", style = MaterialTheme.typography.labelMedium) }
                    }
                }
                // 分類チップ(横スクロール)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (g in TaxonGroup.entries) {
                        val on = g in groupFilter
                        FilterChip(selected = on, onClick = {
                            setGroups(groupFilter.toMutableSet().apply { if (on) remove(g) else add(g) })
                        }, label = { Text(g.label, style = MaterialTheme.typography.labelSmall) })
                    }
                }
                Spacer(Modifier.height(4.dp))
                // 生息地チップ(横スクロール)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for ((i, rg) in catalog.regions.withIndex()) {
                        val on = i in regionFilter
                        FilterChip(selected = on, onClick = {
                            setRegions(regionFilter.toMutableSet().apply { if (on) remove(i) else add(i) })
                        }, label = { Text(rg, style = MaterialTheme.typography.labelSmall) })
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            val shownCount = rows.count { it is ListRow.Item }
            Text(
                "${"%,d".format(shownCount)} 種",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
            )

            if (source.isEmpty()) {
                Text(emptyMessage, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (shownCount == 0) {
                Text(
                    if (query.isNotEmpty()) "「$query」に一致する種がありません" else "条件に一致する種がありません",
                    Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(
                        count = rows.size,
                        key = { i -> when (val r = rows[i]) { is ListRow.Item -> r.entry.id.toLong(); is ListRow.Header -> -(i.toLong()) - 1 } },
                    ) { i ->
                        when (val r = rows[i]) {
                            is ListRow.Header -> {
                                Text(
                                    r.text,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 6.dp),
                                )
                            }
                            is ListRow.Item -> {
                                SearchResultRow(
                                    r.entry,
                                    onClick = { onSelect(r.entry) },
                                    read = r.entry.id in readIds,
                                    bookmarked = r.entry.id in bookmarkIds,
                                    onToggleBookmark = { onToggleBookmark(r.entry.id) },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed class ListRow {
    class Header(val text: String) : ListRow()
    class Item(val entry: Entry) : ListRow()
}

private fun buildRows(entries: List<Entry>, sort: SortMode, kanaKeys: Map<Int, String>): List<ListRow> {
    val out = ArrayList<ListRow>()
    when (sort) {
        SortMode.GROUP -> {
            val sorted = entries.sortedWith(compareBy({ it.group.ordinal }, { it.orderJa }, { it.familyJa }, { it.name }, { it.id }))
            var last: TaxonGroup? = null
            for (e in sorted) {
                if (e.group != last) {
                    out.add(ListRow.Header(e.group.label))
                    last = e.group
                }
                out.add(ListRow.Item(e))
            }
        }
        SortMode.STATUS -> {
            val sorted = entries.sortedWith(compareBy({ it.status.ordinal }, { it.group.ordinal }, { it.name }, { it.id }))
            var last: RedListStatus? = null
            for (e in sorted) {
                if (e.status != last) {
                    out.add(ListRow.Header("${e.status.code} ${e.status.label}"))
                    last = e.status
                }
                out.add(ListRow.Item(e))
            }
        }
        SortMode.KANA -> {
            // 読み(yomi)があればそれを、なければ和名を正規化した読みキー(カタログ単位で計算済み)で並べ替える
            val keyed = entries.map { it to (kanaKeys[it.id] ?: sortKey(it.name)) }
                .sortedWith(compareBy({ it.second }, { it.first.id }))
            var last = ""
            for ((e, key) in keyed) {
                val ini = initialOfKey(key)
                if (ini != last) { out.add(ListRow.Header(ini)); last = ini }
                out.add(ListRow.Item(e))
            }
        }
    }
    return out
}

/** 並べ替え用の読みキー: NFKC + ひらがな→カタカナ。 */
private fun sortKey(s: String): String {
    val n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKC)
    val sb = StringBuilder(n.length)
    for (ch in n) sb.append(if (ch in 'ぁ'..'ゖ') ch + 0x60 else ch)
    return sb.toString()
}

/** 正規化済みの読みキーから行見出しを導く(sortKey と同じ正規化結果を使い回す)。 */
private fun initialOfKey(key: String): String {
    val k = key.firstOrNull() ?: return "その他"
    if (k in 'A'..'Z' || k in 'a'..'z' || k in '0'..'9') return "A〜Z・数字"
    return when (k) {
        in 'ァ'..'オ' -> "ア行"
        in 'カ'..'ゴ' -> "カ行"
        in 'サ'..'ゾ' -> "サ行"
        in 'タ'..'ド' -> "タ行"
        in 'ナ'..'ノ' -> "ナ行"
        in 'ハ'..'ポ' -> "ハ行"
        in 'マ'..'モ' -> "マ行"
        in 'ャ'..'ヨ' -> "ヤ行"
        in 'ラ'..'ロ' -> "ラ行"
        in 'ヮ'..'ヴ', 'ワ', 'ヰ', 'ヱ', 'ヲ', 'ン' -> "ワ行"
        else -> "漢字・その他"
    }
}
