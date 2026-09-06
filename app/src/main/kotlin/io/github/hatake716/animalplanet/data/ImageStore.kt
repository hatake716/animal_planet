package io.github.hatake716.animalplanet.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * assets/img/ の写真をデコードして LRU キャッシュに保持する(外部ライブラリなし)。
 * サムネイル(一覧)用と詳細用でサイズを分ける。
 */
class ImageStore(context: Context) {
    private val assets = context.applicationContext.assets
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt().coerceIn(8 shl 20, 64 shl 20)) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** キャッシュにあれば同期的に返す(一覧のスクロール中のちらつき防止)。 */
    fun peek(path: String, maxSide: Int): Bitmap? = cache.get(key(path, maxSide))

    /** 同梱写真は長辺 MAX_ASSET_SIDE 以下なので、それ以上の要求は同じデコード結果になる。キャッシュキーも揃える。 */
    private fun key(path: String, maxSide: Int): String = "$path@${maxSide.coerceAtMost(MAX_ASSET_SIDE)}"

    /**
     * @param maxSide 長辺の上限(px)。元は長辺 640px 程度。
     * 失敗(アセットなし・OOM)時は null。
     */
    suspend fun load(path: String, maxSide: Int): Bitmap? {
        val key = key(path, maxSide)
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                val longest = maxOf(bounds.outWidth, bounds.outHeight)
                while (longest / (sample * 2) >= maxSide) sample *= 2
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = if (maxSide <= 160) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
                }
                val bmp = assets.open(path).use { BitmapFactory.decodeStream(it, null, opts) }
                if (bmp != null) cache.put(key, bmp)
                bmp
            } catch (e: Exception) {
                null
            } catch (e: OutOfMemoryError) {
                cache.evictAll()
                null
            }
        }
    }

    companion object {
        /** assemble.py が書き出す写真の長辺上限(px)。 */
        const val MAX_ASSET_SIDE = 640

        @Volatile private var instance: ImageStore? = null
        fun get(context: Context): ImageStore =
            instance ?: synchronized(this) { instance ?: ImageStore(context).also { instance = it } }
    }
}
