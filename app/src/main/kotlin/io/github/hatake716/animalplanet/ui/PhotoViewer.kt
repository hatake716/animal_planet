package io.github.hatake716.animalplanet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.hatake716.animalplanet.data.Entry

/** 写真の全画面表示(ピンチで拡大)。出典(作者・ライセンス)と Commons のファイルページへのリンクを示す。 */
@Composable
fun PhotoViewer(entry: Entry, onClose: () -> Unit, onCommons: () -> Unit) {
    val credit = entry.image
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val state = rememberTransformableState { zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, 6f)
            offset = if (scale <= 1f) Offset.Zero else offset + pan
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            SpeciesImage(
                entry, maxSide = 1024,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
                    .transformable(state),
                contentScale = ContentScale.Fit,
                tintBackground = false,
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.statusBars).padding(6.dp),
            ) { Icon(Icons.Default.Close, contentDescription = "閉じる", tint = Color.White) }
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f))
                    .windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(entry.name, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (entry.sci.isNotBlank()) Text(entry.sci, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                if (credit != null) {
                    Text("写真: ${credit.line}", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    Text(credit.file, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    TextButton(onClick = onCommons, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text("出典ページを開く(Wikimedia Commons)", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
