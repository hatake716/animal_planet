package io.github.hatake716.animalplanet.globe

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import io.github.hatake716.animalplanet.data.Entry
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * 地球儀ビュー。タッチ操作(ドラッグ回転・慣性・ピンチズーム・ダブルタップ・タップ選択)を扱う。
 * カメラの読み書きはすべて GL スレッド(queueEvent)で行い、UI スレッドとの競合を避ける。
 */
@SuppressLint("ViewConstructor")
class GlobeView(
    context: Context,
    entries: List<Entry>,
    private val onTap: (ids: List<Int>) -> Unit,
    onCameraIdle: (lat: Double, lon: Double, alt: Double, rotation: Rotation) -> Unit,
) : GLSurfaceView(context) {

    val renderer: GlobeRenderer
    private val density = resources.displayMetrics.density

    private var mode = Mode.NONE
    // 掴んだ地表・角速度は GL スレッドだけで更新。UP も MOVE の後にキューで処理する。
    private var grab: DoubleArray? = null
    private var lastX = 0f
    private var lastY = 0f
    private var lastTime = 0L
    private val velocity = DoubleArray(3)
    private var pinchDist = 0f
    private var pinchFocalX = 0f
    private var pinchFocalY = 0f

    private enum class Mode { NONE, DRAG, PINCH }

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val ids = renderer.markers.snapshot.pick(e.x, e.y, 26f * density)
            onTap(ids)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val sx = e.x
            val sy = e.y
            queueEvent {
                renderer.camera.update()
                val point = renderer.camera.surfacePoint(sx, sy)
                val cam = renderer.camera
                if (point != null) renderer.focusOn(point, cam.altitude / 2.5, 550)
                else renderer.zoomTo(cam.altitude / 2.5, 550)
            }
            requestRender()
            return true
        }
    })

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 24, 0)
        preserveEGLContextOnPause = true
        renderer = GlobeRenderer(context, entries, { requestRender() }, onCameraIdle)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    // ------------------------------------------------------------ public API (UI thread)
    fun flyToEntry(e: Entry, altitude: Double = 0.05) {
        queueEvent { renderer.flyTo(Math.toRadians(e.lat), Math.toRadians(e.lon), altitude) }
        requestRender()
    }

    fun flyTo(latDeg: Double, lonDeg: Double, altitude: Double, durationMs: Long = 1400) {
        queueEvent { renderer.flyTo(Math.toRadians(latDeg), Math.toRadians(lonDeg), altitude, durationMs) }
        requestRender()
    }

    /** altitude <= 0 なら地球全体が収まる高度にする。 */
    fun setCamera(latDeg: Double, lonDeg: Double, altitude: Double, rotation: Rotation? = null) {
        queueEvent {
            renderer.stopAnimations()
            renderer.camera.rotation = rotation ?: Rotation.northUp(Math.toRadians(latDeg), Math.toRadians(lonDeg))
            if (altitude > 0) {
                renderer.camera.altitude = altitude
                renderer.pendingFit = false
            } else if (renderer.camera.viewportWidth > 1) {
                renderer.camera.altitude = renderer.camera.fitAltitude()
                renderer.pendingFit = false
            } else {
                renderer.pendingFit = true
            }
        }
        requestRender()
    }

    /** 地球全体が見える位置へ戻る。 */
    fun fitWorld() {
        queueEvent {
            val cam = renderer.camera
            renderer.zoomTo(cam.fitAltitude(), 900)
        }
        requestRender()
    }

    fun zoomBy(factor: Double) {
        queueEvent {
            val cam = renderer.camera
            renderer.zoomTo(cam.altitude / factor, 350)
        }
        requestRender()
    }

    fun setFilter(filter: MarkerFilter) {
        renderer.markers.filter = filter
        requestRender()
    }

    fun setSelected(id: Int) {
        renderer.markers.selectedId = id
        requestRender()
    }

    // ------------------------------------------------------------ touch
    override fun onDetachedFromWindow() {
        // ビューがツリーから外れたらタイルデコード用スレッドを止める(スレッド漏れ防止)
        renderer.releaseResources()
        super.onDetachedFromWindow()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 先に自前のドラッグ/ピンチ処理(ACTION_DOWN でアニメーション停止)を行い、その後に
        // GestureDetector を呼ぶ。逆順だとダブルタップの flyTo が直後の stopAnimations で消される。
        handleTouch(event)
        gestures.onTouchEvent(event)
        return true
    }

    private fun handleTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mode = Mode.DRAG
                lastX = event.x
                lastY = event.y
                lastTime = event.eventTime
                val sx = event.x
                val sy = event.y
                queueEvent {
                    renderer.stopAnimations()
                    velocity.fill(0.0)
                    renderer.camera.update()
                    grab = renderer.camera.surfacePoint(sx, sy)
                }
                requestRender()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    mode = Mode.PINCH
                    pinchDist = dist(event)
                    pinchFocalX = (event.getX(0) + event.getX(1)) / 2
                    pinchFocalY = (event.getY(0) + event.getY(1)) / 2
                    val fx = pinchFocalX
                    val fy = pinchFocalY
                    queueEvent {
                        velocity.fill(0.0)
                        renderer.camera.update()
                        grab = renderer.camera.surfacePoint(fx, fy)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == Mode.PINCH && event.pointerCount >= 2) {
                    val d = dist(event)
                    val fx = (event.getX(0) + event.getX(1)) / 2
                    val fy = (event.getY(0) + event.getY(1)) / 2
                    val scale = if (pinchDist > 0f) (d / pinchDist).toDouble() else 1.0
                    pinchDist = d
                    queueEvent { applyPinch(scale, fx, fy) }
                    requestRender()
                } else if (mode == Mode.DRAG && event.pointerCount == 1) {
                    val sx = event.x
                    val sy = event.y
                    val dx = sx - lastX
                    val dy = sy - lastY
                    val dt = max(1L, event.eventTime - lastTime)
                    lastX = sx
                    lastY = sy
                    lastTime = event.eventTime
                    queueEvent { applyDrag(sx, sy, dx, dy, dt) }
                    requestRender()
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount == 2) {
                    // 残る指でドラッグを続ける
                    val remaining = if (event.actionIndex == 0) 1 else 0
                    mode = Mode.DRAG
                    lastX = event.getX(remaining)
                    lastY = event.getY(remaining)
                    lastTime = event.eventTime
                    val sx = lastX
                    val sy = lastY
                    queueEvent {
                        velocity.fill(0.0)
                        renderer.camera.update()
                        grab = renderer.camera.surfacePoint(sx, sy)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                val allowFling = event.actionMasked == MotionEvent.ACTION_UP && mode == Mode.DRAG && event.eventTime - lastTime <= 80
                queueEvent {
                    if (allowFling && velocity.any { abs(it) > 0.02 }) renderer.setFling(velocity)
                    velocity.fill(0.0)
                    grab = null
                }
                requestRender()
                mode = Mode.NONE
            }
        }
    }

    private fun dist(e: MotionEvent): Float = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))

    /** GL スレッド: 指の下の地表点が指に追従するように回転する。 */
    private fun applyDrag(sx: Float, sy: Float, dx: Float, dy: Float, dtMs: Long) {
        val cam = renderer.camera
        cam.update()
        val g = grab
        val delta = g?.let { cam.moveSurfacePoint(it, sx, sy) } ?: run {
            val fallback = cam.dragOutside(dx, dy)
            grab = cam.surfacePoint(sx, sy)
            fallback
        }
        val movement = delta.vector()
        val dt = dtMs / 1000.0
        for (i in velocity.indices) velocity[i] = (velocity[i] + movement[i] / dt) * 0.5
    }

    /** GL スレッド: 焦点の下の地表点を固定したまま高度を変える。 */
    private fun applyPinch(scale: Double, fx: Float, fy: Float) {
        val cam = renderer.camera
        cam.update()
        val before = grab ?: cam.surfacePoint(fx, fy)
        cam.altitude = (cam.altitude / scale).coerceIn(Camera.MIN_ALT, Camera.MAX_ALT)
        cam.update()
        if (before != null) {
            cam.moveSurfacePoint(before, fx, fy)
            grab = before
        } else {
            grab = cam.surfacePoint(fx, fy)
        }
    }

    companion object {
        /** エントリを画面に収めるのに適した高度(地球半径単位)。 */
        const val ENTRY_ALTITUDE = 0.045
        fun deg2rad(d: Double) = d * PI / 180
    }
}
