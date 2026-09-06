package io.github.hatake716.animalplanet.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.hatake716.animalplanet.data.Entry
import io.github.hatake716.animalplanet.data.ImageStore
import io.github.hatake716.animalplanet.data.RedListStatus

/**
 * 同梱写真(assets/img/{id}.jpg)を表示する。写真がない種は分類色の薄い背景にアイコンを出す。
 * @param maxSide デコード時の長辺上限(px)。一覧は小さく、詳細は大きく。
 */
@Composable
fun SpeciesImage(
    entry: Entry,
    maxSide: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    cornerRadius: Dp = 0.dp,
    tintBackground: Boolean = true,
) {
    val context = LocalContext.current
    val path = entry.imageAsset
    val store = ImageStore.get(context)
    val bitmap by produceState<Bitmap?>(initialValue = if (path == null) null else store.peek(path, maxSide), key1 = path, key2 = maxSide) {
        if (path != null && value == null) value = store.load(path, maxSide)
    }
    Box(
        modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(if (tintBackground) entry.group.color.copy(alpha = 0.16f) else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp.asImageBitmap(), contentDescription = entry.name, modifier = Modifier.fillMaxSize(), contentScale = contentScale)
        } else if (path == null) {
            Icon(Icons.Default.Pets, contentDescription = "写真なし", tint = entry.group.color.copy(alpha = 0.75f), modifier = Modifier.fillMaxSize(0.45f))
        }
    }
}

/** IUCN カテゴリのバッジ(CR/EN/VU)。 */
@Composable
fun StatusBadge(status: RedListStatus, modifier: Modifier = Modifier, showLabel: Boolean = false) {
    val fg = if (status == RedListStatus.CR) Color.White else Color(0xFF1B1B1B)
    Text(
        if (showLabel) "${status.code} ${status.label}" else status.code,
        color = fg,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = modifier.background(status.color, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}
