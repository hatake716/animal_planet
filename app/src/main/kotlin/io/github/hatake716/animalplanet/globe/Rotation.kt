package io.github.hatake716.animalplanet.globe

import kotlin.math.*

/** 地球座標から画面向きへの単位四元数。極・日付変更線にも回転の境界を作らない。 */
@ConsistentCopyVisibility
data class Rotation private constructor(val w: Double, val x: Double, val y: Double, val z: Double) {
    operator fun times(b: Rotation): Rotation = of(
        w * b.w - x * b.x - y * b.y - z * b.z,
        w * b.x + x * b.w + y * b.z - z * b.y,
        w * b.y - x * b.z + y * b.w + z * b.x,
        w * b.z + x * b.y - y * b.x + z * b.w,
    )

    fun inverse() = Rotation(w, -x, -y, -z)

    fun apply(v: DoubleArray): DoubleArray {
        val tx = 2 * (y * v[2] - z * v[1])
        val ty = 2 * (z * v[0] - x * v[2])
        val tz = 2 * (x * v[1] - y * v[0])
        return doubleArrayOf(v[0] + w * tx + y * tz - z * ty,
            v[1] + w * ty + z * tx - x * tz, v[2] + w * tz + x * ty - y * tx)
    }

    fun matrix(out: FloatArray) {
        Mat4.identity(out)
        for (column in 0..2) {
            val axis = DoubleArray(3).also { it[column] = 1.0 }
            val rotated = apply(axis)
            for (row in 0..2) out[column * 4 + row] = rotated[row].toFloat()
        }
    }

    /** 最短回転を軸 × ラジアンで返す。慣性にも同じ画面座標系を使う。 */
    fun vector(): DoubleArray {
        val s = if (w < 0) -1.0 else 1.0
        val length = sqrt(x * x + y * y + z * z)
        if (length < 1e-12) return DoubleArray(3)
        val scale = 2 * atan2(length, abs(w)) * s / length
        return doubleArrayOf(x * scale, y * scale, z * scale)
    }

    fun interpolate(to: Rotation, fraction: Double): Rotation {
        val dot = w * to.w + x * to.x + y * to.y + z * to.z
        val sign = if (dot < 0) -1.0 else 1.0
        val angle = acos(abs(dot).coerceIn(0.0, 1.0))
        val a: Double
        val b: Double
        if (angle < 1e-6) { a = 1 - fraction; b = fraction * sign }
        else { a = sin((1 - fraction) * angle) / sin(angle); b = sin(fraction * angle) / sin(angle) * sign }
        return of(w * a + to.w * b, x * a + to.x * b, y * a + to.y * b, z * a + to.z * b)
    }

    companion object {
        val IDENTITY = Rotation(1.0, 0.0, 0.0, 0.0)

        fun of(w: Double, x: Double, y: Double, z: Double): Rotation {
            val length = sqrt(w * w + x * x + y * y + z * z)
            require(length.isFinite() && length > 1e-12)
            return Rotation(w / length, x / length, y / length, z / length)
        }

        fun vector(x: Double, y: Double, z: Double): Rotation {
            val angle = sqrt(x * x + y * y + z * z)
            if (angle < 1e-12) return IDENTITY
            val scale = sin(angle / 2) / angle
            return of(cos(angle / 2), x * scale, y * scale, z * scale)
        }

        fun northUp(lat: Double, lon: Double): Rotation = vector(lat, 0.0, 0.0) * vector(0.0, -lon, 0.0)

        /** 正規化済みの球面点 a を b へ運ぶ最小回転。反対側の点も有限な回転になる。 */
        fun between(a: DoubleArray, b: DoubleArray): Rotation {
            val dot = (a[0] * b[0] + a[1] * b[1] + a[2] * b[2]).coerceIn(-1.0, 1.0)
            val cx = a[1] * b[2] - a[2] * b[1]
            val cy = a[2] * b[0] - a[0] * b[2]
            val cz = a[0] * b[1] - a[1] * b[0]
            if (dot < -1 + 1e-12) {
                // a と平行にならない基底を選ぶ。
                return if (abs(a[0]) < abs(a[1])) of(0.0, 0.0, -a[2], a[1])
                else of(0.0, -a[2], 0.0, a[0])
            }
            return of(1 + dot, cx, cy, cz)
        }
    }
}
