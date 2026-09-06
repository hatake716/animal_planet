package io.github.hatake716.animalplanet.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hatake716.animalplanet.data.Catalog
import io.github.hatake716.animalplanet.data.Entry

/** 画面下部に出る種のカード。地球儀は背後で操作できる。 */
@Composable
fun DetailCard(
    entry: Entry,
    catalog: Catalog,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onClose: () -> Unit,
    onWikipedia: () -> Unit,
    onYoutube: () -> Unit,
    onZoom: () -> Unit,
    onShare: () -> Unit,
    onPhoto: () -> Unit,
    onCommons: () -> Unit,
    bookmarked: Boolean,
    onToggleBookmark: () -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 8.dp),
        shape = RoundedCornerShape(22.dp),
        // 半透明にすると背後の明るい地図が透けて文字が読みにくくなるため、少し濃い色で不透明にする
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
    ) {
        Column(Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 14.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.Top) {
                if (!expanded && entry.image != null) {
                    SpeciesImage(entry, maxSide = 240, modifier = Modifier.size(84.dp).clickable(onClick = onPhoto), cornerRadius = 12.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f).clickable(onClick = onToggleExpand).padding(start = if (expanded) 4.dp else 0.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(entry.status)
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(9.dp).background(entry.group.color, CircleShape))
                        Spacer(Modifier.width(5.dp))
                        Text(entry.group.label, style = MaterialTheme.typography.labelMedium, color = entry.group.color)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        entry.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (expanded) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (entry.sci.isNotBlank()) {
                        Text(entry.sci, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (!expanded && entry.place.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                            Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(entry.place, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                IconButton(onClick = onToggleBookmark) {
                    Icon(
                        if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (bookmarked) "ブックマーク解除" else "ブックマーク",
                        tint = if (bookmarked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onToggleExpand) {
                    Icon(if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess, contentDescription = if (expanded) "折りたたむ" else "詳細")
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "閉じる") }
            }
            if (expanded) {
                ExpandedBody(entry, catalog, onPhoto, onCommons)
            } else if (entry.desc.isNotBlank()) {
                Text(
                    entry.desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp, end = 10.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(Modifier.padding(top = 10.dp, end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onWikipedia,
                    enabled = entry.hasWikipedia,
                    modifier = Modifier.weight(1.35f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (entry.hasWikipedia) "Wikipedia" else "記事なし", maxLines = 1, softWrap = false)
                }
                FilledTonalButton(
                    onClick = onYoutube,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("動画", maxLines = 1, softWrap = false)
                }
                IconButton(onClick = onZoom) { Icon(Icons.Default.ZoomIn, contentDescription = "生息地を拡大") }
                IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = "共有") }
            }
        }
    }
}

@Composable
private fun ExpandedBody(entry: Entry, catalog: Catalog, onPhoto: () -> Unit, onCommons: () -> Unit) {
    Column(Modifier.padding(top = 8.dp, end = 8.dp)) {
        val credit = entry.image
        if (credit != null) {
            SpeciesImage(
                entry, maxSide = 640,
                modifier = Modifier.fillMaxWidth().height(190.dp).clickable(onClick = onPhoto),
                cornerRadius = 14.dp,
            )
            Text(
                "写真: ${credit.line}(Wikimedia Commons)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp).clickable(onClick = onCommons),
            )
        }
        Column(Modifier.padding(top = 6.dp).heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
            Text(entry.status.label, style = MaterialTheme.typography.labelMedium, color = entry.status.color, fontWeight = FontWeight.SemiBold)
            if (entry.aliases.isNotEmpty()) {
                Text("別名: " + entry.aliases.joinToString(" / "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (entry.taxonomyLine.isNotBlank()) {
                Text("分類: ${entry.group.label} ${entry.taxonomyLine}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (entry.place.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(entry.place, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val regions = catalog.regions.withIndex().filter { entry.inRegion(it.index) }.joinToString("・") { it.value }
            if (regions.isNotBlank()) {
                Text("生息地域: $regions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (entry.desc.isNotBlank()) {
                Text(entry.desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 6.dp))
            }
            if (entry.habitat.isNotBlank()) {
                Text("生息環境: ${entry.habitat}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 6.dp))
            }
            if (entry.threats.isNotBlank()) {
                Text("主な脅威: ${entry.threats}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 6.dp))
            }
            if (entry.hasWikipedia) {
                Text("Wikipedia 記事: ${entry.wikiTitle}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
            Text(
                "解説・生息地の出典: Wikipedia ／ 分類・評価(IUCN カテゴリ): Wikidata",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
