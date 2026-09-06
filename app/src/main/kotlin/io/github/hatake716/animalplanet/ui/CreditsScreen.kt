package io.github.hatake716.animalplanet.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hatake716.animalplanet.data.Catalog

/**
 * 写真の出典一覧。すべての同梱写真について作者・ライセンス・Commons のファイル名を示す
 * (CC BY / CC BY-SA の帰属表示要件を満たすため)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditsScreen(catalog: Catalog, onClose: () -> Unit, onOpen: (title: String, url: String) -> Unit) {
    BackHandler(onBack = onClose)
    val rows = remember(catalog) { catalog.entries.filter { it.image != null }.sortedBy { it.id } }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("写真の出典(${"%,d".format(rows.size)} 枚)", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "閉じる") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    "写真はすべて Wikimedia Commons で自由ライセンス(CC0 / パブリックドメイン / CC BY / CC BY-SA)により公開されているものを、" +
                        "縮小して同梱しています。各写真の作者とライセンスは次のとおりです。行をタップすると Commons のファイルページ(原典)を開きます。\n" +
                        "地球画像: NASA Blue Marble: Next Generation(パブリックドメイン)。解説: Wikipedia 日本語版(CC BY-SA 4.0)。分類・評価: Wikidata(CC0)。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(rows, key = { it.id }) { e ->
                val c = e.image ?: return@items
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(e.name, c.commonsUrl) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpeciesImage(e, maxSide = 120, modifier = Modifier.size(44.dp), cornerRadius = 8.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(c.file, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(c.line, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            }
        }
    }
}
