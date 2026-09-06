package io.github.hatake716.animalplanet.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.hatake716.animalplanet.data.Entry
import io.github.hatake716.animalplanet.data.TaxonGroup
import io.github.hatake716.animalplanet.globe.GlobeView

@Composable
fun App(vm: MainViewModel = viewModel()) {
    val catalog = vm.catalog
    val error = vm.loadError
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            error != null -> Text("データの読み込みに失敗しました\n$error", Modifier.align(Alignment.Center).padding(24.dp))
            catalog == null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("地球儀を準備しています…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> GlobeScreen(vm)
        }
        val url = vm.webUrl
        if (url != null) {
            WebScreen(url = url, title = vm.webTitle, onClose = { vm.closeWeb() })
        }
    }
}

@Composable
private fun BoxScope.GlobeScreen(vm: MainViewModel) {
    val catalog = vm.catalog ?: return
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var globe by remember { mutableStateOf<GlobeView?>(null) }

    val selected = vm.selectedEntry

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            GlobeView(
                ctx,
                catalog.entries,
                onTap = { ids ->
                    when (ids.size) {
                        0 -> vm.clearSelection()
                        1 -> vm.select(ids[0])
                        else -> vm.pickCandidates = ids.mapNotNull { catalog.byId(it) }
                    }
                },
                onCameraIdle = { lat, lon, alt ->
                    vm.saveCamera(Math.toDegrees(lat), Math.toDegrees(lon), alt)
                },
            ).also {
                it.setCamera(vm.savedLat, vm.savedLon, vm.savedAlt)
                globe = it
            }
        },
        update = { view ->
            view.setFilter(vm.filter)
            view.setSelected(vm.selectedId)
        },
    )

    // ライフサイクル: GLSurfaceView の pause/resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, globe) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> globe?.onResume()
                Lifecycle.Event.ON_PAUSE -> globe?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ---- 上部: 検索バー ----
    Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 12.dp, vertical = 8.dp)) {
        SearchBar(
            query = vm.query,
            onQueryChange = { vm.updateQuery(it) },
            onFilter = { vm.showFilter = true },
            filterActive = vm.isFilterActive(),
            onClear = { vm.updateQuery(""); focus.clearFocus() },
            total = catalog.entries.size,
        )
        AnimatedVisibility(visible = vm.query.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
            Surface(
                Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(max = 380.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
            ) {
                val results = vm.searchResults
                // 退場フェード中(query が空)に「該当なし」がちらつかないよう query 非空を条件に含める
                if (results.isEmpty()) {
                    if (vm.query.isNotBlank()) {
                        Text("該当する種がありません", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn {
                        items(results, key = { it.id }) { e ->
                            SearchResultRow(e, onClick = {
                                focus.clearFocus()
                                vm.updateQuery("")
                                vm.select(e.id)
                                globe?.flyToEntry(e, GlobeView.ENTRY_ALTITUDE)
                            })
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }
    }

    // ---- 左下: 情報・一覧・ブックマーク・ランダム・全体・ズームのボタン列 ----
    Column(
        Modifier.align(Alignment.BottomStart).windowInsetsPadding(WindowInsets.navigationBars).padding(start = 12.dp, bottom = if (selected != null) 8.dp else 40.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedVisibility(visible = selected == null, enter = fadeIn(), exit = fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.Start) {
                SmallFloatingActionButton(onClick = { vm.showAbout = true }, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Icon(Icons.Default.Info, contentDescription = "このアプリについて")
                }
                SmallFloatingActionButton(onClick = { vm.showList = true }, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "生物一覧")
                }
                SmallFloatingActionButton(onClick = { vm.showBookmarks = true }, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Icon(Icons.Default.Bookmark, contentDescription = "ブックマーク一覧")
                }
                SmallFloatingActionButton(
                    onClick = {
                        vm.randomEntry()?.let { e ->
                            vm.select(e.id)
                            globe?.flyToEntry(e, GlobeView.ENTRY_ALTITUDE)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ) { Icon(Icons.Default.Casino, contentDescription = "ランダムに表示") }
                SmallFloatingActionButton(onClick = { globe?.fitWorld() }, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Icon(Icons.Default.Public, contentDescription = "地球全体を表示")
                }
                SmallFloatingActionButton(onClick = { globe?.zoomBy(2.0) }, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Icon(Icons.Default.Add, contentDescription = "拡大")
                }
                SmallFloatingActionButton(onClick = { globe?.zoomBy(0.5) }, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Icon(Icons.Default.Remove, contentDescription = "縮小")
                }
            }
        }
    }

    // ---- 右下: 凡例(分類ごとのピンの色)と表示件数 ----
    AnimatedVisibility(
        visible = selected == null,
        modifier = Modifier.align(Alignment.BottomEnd),
        enter = fadeIn(), exit = fadeOut(),
    ) {
        val shown = remember(vm.filter, catalog) { catalog.entries.count { vm.filter.accepts(it) } }
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
            tonalElevation = 3.dp,
            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(end = 10.dp, bottom = 24.dp).clickable { vm.showFilter = true },
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (g in TaxonGroup.entries) {
                    val on = vm.filter.groups.isEmpty() || g in vm.filter.groups
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(if (on) g.color else g.color.copy(alpha = 0.25f), CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            g.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }
                Text(
                    "${"%,d".format(shown)} 種を表示",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = if (vm.isFilterActive()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }

    // ---- 最下部: 出典 ----
    AnimatedVisibility(
        visible = selected == null,
        modifier = Modifier.align(Alignment.BottomStart),
        enter = fadeIn(), exit = fadeOut(),
    ) {
        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(start = 84.dp, end = 12.dp, bottom = 6.dp),
        ) {
            Text(
                "写真: Wikimedia Commons ／ 解説: Wikipedia ／ 評価: Wikidata ／ 地図: NASA",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 9.sp,
            )
        }
    }

    // ---- 詳細カード ----
    // 退場アニメーション中も内容を保つため、直近の非 null の種を覚えておく。
    var lastShown by remember { mutableStateOf<Entry?>(null) }
    if (selected != null) lastShown = selected
    AnimatedVisibility(
        visible = selected != null,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        val e = selected ?: lastShown ?: return@AnimatedVisibility
        DetailCard(
            entry = e,
            catalog = catalog,
            expanded = vm.detailExpanded,
            onToggleExpand = { vm.detailExpanded = !vm.detailExpanded },
            onClose = { vm.clearSelection() },
            onWikipedia = { vm.openWikipedia(e) },
            onYoutube = { vm.openYoutube(e) },
            onZoom = { globe?.flyToEntry(e, 0.012) },
            onShare = { shareEntry(context, e) },
            onPhoto = { if (e.image != null) vm.photoOfId = e.id },
            onCommons = { e.image?.let { vm.openUrl("写真の出典: ${e.name}", it.commonsUrl) } },
            bookmarked = e.id in vm.bookmarkIds,
            onToggleBookmark = { vm.toggleBookmark(e.id) },
        )
    }
    if (selected != null) {
        BackHandler { vm.clearSelection() }
    }

    // ---- 写真の全画面表示 ----
    val photoEntry = catalog.byId(vm.photoOfId)
    if (photoEntry != null) {
        PhotoViewer(
            entry = photoEntry,
            onClose = { vm.photoOfId = -1 },
            onCommons = {
                photoEntry.image?.let { vm.openUrl("写真の出典: ${photoEntry.name}", it.commonsUrl) }
                vm.photoOfId = -1
            },
        )
    }

    // ---- 複数ヒット時の選択 ----
    val candidates = vm.pickCandidates
    if (candidates.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { vm.pickCandidates = emptyList() },
            confirmButton = { TextButton(onClick = { vm.pickCandidates = emptyList() }) { Text("閉じる") } },
            title = { Text("この付近の種 (${candidates.size})") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(candidates, key = { it.id }) { e ->
                        SearchResultRow(e, onClick = {
                            vm.pickCandidates = emptyList()
                            vm.select(e.id)
                        })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }
                }
            },
        )
    }

    if (vm.showFilter) {
        FilterSheet(vm = vm, catalog = catalog, onDismiss = { vm.showFilter = false })
    }
    if (vm.showList) {
        EntryListScreen(
            catalog = catalog,
            source = catalog.entries,
            title = "生物一覧",
            emptyMessage = "種がありません",
            readIds = vm.readIds,
            bookmarkIds = vm.bookmarkIds,
            onClose = { vm.showList = false },
            onSelect = { e ->
                vm.showList = false
                vm.select(e.id)
                globe?.flyToEntry(e, GlobeView.ENTRY_ALTITUDE)
            },
            onToggleBookmark = { vm.toggleBookmark(it) },
        )
    }
    if (vm.showBookmarks) {
        val bookmarked = remember(vm.bookmarkIds, catalog) {
            vm.bookmarkIds.mapNotNull { catalog.byId(it) }
        }
        EntryListScreen(
            catalog = catalog,
            source = bookmarked,
            title = "ブックマーク",
            emptyMessage = "ブックマークはまだありません。種の詳細から☆で追加できます。",
            readIds = vm.readIds,
            bookmarkIds = vm.bookmarkIds,
            onClose = { vm.showBookmarks = false },
            onSelect = { e ->
                vm.showBookmarks = false
                vm.select(e.id)
                globe?.flyToEntry(e, GlobeView.ENTRY_ALTITUDE)
            },
            onToggleBookmark = { vm.toggleBookmark(it) },
        )
    }
    if (vm.showCredits) {
        CreditsScreen(catalog = catalog, onClose = { vm.showCredits = false }, onOpen = { t, u -> vm.openUrl(t, u) })
    }
    if (vm.showAbout) {
        AboutDialog(catalog = catalog, onDismiss = { vm.showAbout = false }, onCredits = { vm.showAbout = false; vm.showCredits = true })
    }

    // 選択が変わったら地球儀側にも反映(検索/一覧以外からの選択時はカメラを動かさない)
    LaunchedEffect(vm.selectedId) { globe?.setSelected(vm.selectedId) }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onFilter: () -> Unit,
    filterActive: Boolean,
    onClear: () -> Unit,
    total: Int,
) {
    val focus = LocalFocusManager.current
    Surface(
        Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("絶滅危惧種を検索(${"%,d".format(total)} 種・和名/学名)", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) { Icon(Icons.Default.Close, contentDescription = "クリア") }
            }
            IconButton(onClick = onFilter) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = "絞り込み",
                    tint = if (filterActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
fun SearchResultRow(
    e: Entry,
    onClick: () -> Unit,
    read: Boolean = false,
    bookmarked: Boolean? = null,
    onToggleBookmark: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpeciesImage(e, maxSide = 120, modifier = Modifier.size(46.dp), cornerRadius = 8.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (read) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "既読",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    e.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                StatusBadge(e.status)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(e.group.color, CircleShape))
                Spacer(Modifier.width(4.dp))
                val sub = listOf(e.group.label, e.place).filter { it.isNotBlank() }.joinToString(" ・ ")
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (e.sci.isNotBlank()) {
                Text(e.sci, style = MaterialTheme.typography.labelSmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (bookmarked != null && onToggleBookmark != null) {
            IconButton(onClick = onToggleBookmark) {
                Icon(
                    if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = if (bookmarked) "ブックマーク解除" else "ブックマーク",
                    tint = if (bookmarked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun shareEntry(context: android.content.Context, e: Entry) {
    val text = buildString {
        append(e.name)
        if (e.sci.isNotBlank()) append("(${e.sci})")
        append(" [${e.status.code}]")
        if (e.place.isNotBlank()) append(" ${e.place}")
        append("\n")
        if (e.hasWikipedia) append(e.wikipediaDesktopUrl)
    }
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    context.startActivity(android.content.Intent.createChooser(intent, e.name))
}
