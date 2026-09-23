package com.ateliermareebasse.cartographie

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.os.Bundle
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.os.Build
import com.ateliermareebasse.cartographie.core.engine.Game
import com.ateliermareebasse.cartographie.core.platform.Input

/** Activité unique : plein écran immersif, une vue de jeu, boucle Choreographer. */
class MainActivity : Activity() {
    private lateinit var view: GameView
    private lateinit var audio: AndroidAudio

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(resources.getIdentifier("Theme.Cartographie", "style", packageName))
        audio = AndroidAudio(assets)
        view = GameView(this, Game(AndroidPlatform(this), audio))
        setContentView(view)
        hideSystemBars()
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { it.hide(WindowInsets.Type.systemBars()); it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) hideSystemBars() }
    override fun onResume() { super.onResume(); view.resume() }
    override fun onPause() { view.pause(); super.onPause() }
    override fun onDestroy() { audio.release(); super.onDestroy() }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { view.game.input(Input.Back) }
    override fun onTrimMemory(level: Int) { super.onTrimMemory(level); if (level >= TRIM_MEMORY_RUNNING_LOW) view.painter.trim() }
}

class GameView(context: Context, val game: Game) : View(context), Choreographer.FrameCallback {
    val painter = AndroidPainter(context.assets) { game.settings.imageScale }
    private var last = 0L
    private var running = false
    private var started = false

    init { isFocusable = true; isFocusableInTouchMode = true; game.painter = painter; setLayerType(LAYER_TYPE_HARDWARE, null) }

    fun resume() { running = true; last = 0L; Choreographer.getInstance().postFrameCallback(this); if (started) game.onAppResume() }
    fun pause() { running = false; Choreographer.getInstance().removeFrameCallback(this); if (started) game.onAppPause() }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val dt = if (last == 0L) 1f / 60f else ((frameTimeNanos - last) / 1e9).toFloat()
        last = frameTimeNanos
        if (width > 0 && height > 0) {
            painter.width = width.toFloat(); painter.height = height.toFloat()
            if (!started) { started = true; game.start() }
            game.update(dt)
            invalidate()
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDraw(canvas: Canvas) {
        painter.canvas = canvas
        painter.width = width.toFloat(); painter.height = height.toFloat()
        if (started) game.render()
        painter.canvas = null
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val idx = e.actionIndex
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> if (idx == 0 || e.pointerCount == 1) game.input(Input.Down(e.getX(idx), e.getY(idx), e.getPointerId(idx)))
            MotionEvent.ACTION_MOVE -> game.input(Input.Move(e.getX(0), e.getY(0), e.getPointerId(0)))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> if (idx == 0 || e.pointerCount == 1) game.input(Input.Up(e.getX(idx), e.getY(idx), e.getPointerId(idx)))
            MotionEvent.ACTION_CANCEL -> game.input(Input.Up(e.getX(0), e.getY(0), 0))
        }
        return true
    }
}
