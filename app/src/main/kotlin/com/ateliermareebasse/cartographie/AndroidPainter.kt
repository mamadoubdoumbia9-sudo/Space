package com.ateliermareebasse.cartographie

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.LruCache
import com.ateliermareebasse.cartographie.core.platform.Align
import com.ateliermareebasse.cartographie.core.platform.Font
import com.ateliermareebasse.cartographie.core.platform.Painter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Peintre Canvas : images décodées en arrière-plan (cache LRU borné par la mémoire),
 * polices du jeu, dégradés, clips. Les coordonnées reçues sont des pixels écran.
 */
class AndroidPainter(private val assets: AssetManager, private val imageScale: () -> Float) : Painter {
    var canvas: Canvas? = null
    override var width = 1f
    override var height = 1f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val fonts = HashMap<Font, Typeface>()
    private val path = Path()
    private val rectF = RectF()
    private val src = Rect()
    private val dst = RectF()
    private val alphaStack = ArrayList<Float>()
    private var alpha = 1f
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private val missing = ConcurrentHashMap.newKeySet<String>()
    private val executor = Executors.newFixedThreadPool(2)
    private val maxKb = ((Runtime.getRuntime().maxMemory() / 1024) / 5).toInt().coerceIn(24 * 1024, 160 * 1024)
    private val cache = object : LruCache<String, Bitmap>(maxKb) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    init {
        fun load(name: String, fallback: Typeface): Typeface = try { Typeface.createFromAsset(assets, "fonts/$name.ttf") } catch (e: Exception) { fallback }
        fonts[Font.BODY] = load("body", Typeface.SERIF)
        fonts[Font.HAND] = load("hand", Typeface.SERIF)
        fonts[Font.TITLE] = load("title", Typeface.create(Typeface.SERIF, Typeface.BOLD))
        fonts[Font.MONO] = load("mono", Typeface.MONOSPACE)
    }

    private fun c(color: Int): Int = if (alpha >= 1f) color else Color.argb((Color.alpha(color) * alpha).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    override fun clear(color: Int) { canvas?.drawColor(color) }
    override fun fillRect(x: Float, y: Float, w: Float, h: Float, color: Int) { paint.style = Paint.Style.FILL; paint.shader = null; paint.color = c(color); canvas?.drawRect(x, y, x + w, y + h, paint) }
    override fun strokeRect(x: Float, y: Float, w: Float, h: Float, color: Int, stroke: Float) { paint.style = Paint.Style.STROKE; paint.shader = null; paint.strokeWidth = stroke; paint.color = c(color); canvas?.drawRect(x, y, x + w, y + h, paint) }
    override fun fillRoundRect(x: Float, y: Float, w: Float, h: Float, r: Float, color: Int) { paint.style = Paint.Style.FILL; paint.shader = null; paint.color = c(color); rectF.set(x, y, x + w, y + h); canvas?.drawRoundRect(rectF, r, r, paint) }
    override fun strokeRoundRect(x: Float, y: Float, w: Float, h: Float, r: Float, color: Int, stroke: Float) { paint.style = Paint.Style.STROKE; paint.shader = null; paint.strokeWidth = stroke; paint.color = c(color); rectF.set(x, y, x + w, y + h); canvas?.drawRoundRect(rectF, r, r, paint) }
    override fun fillCircle(cx: Float, cy: Float, r: Float, color: Int) { paint.style = Paint.Style.FILL; paint.shader = null; paint.color = c(color); canvas?.drawCircle(cx, cy, r, paint) }
    override fun strokeCircle(cx: Float, cy: Float, r: Float, color: Int, stroke: Float) { paint.style = Paint.Style.STROKE; paint.shader = null; paint.strokeWidth = stroke; paint.color = c(color); canvas?.drawCircle(cx, cy, r, paint) }
    override fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, stroke: Float) { paint.style = Paint.Style.STROKE; paint.shader = null; paint.strokeWidth = stroke; paint.strokeCap = Paint.Cap.ROUND; paint.color = c(color); canvas?.drawLine(x1, y1, x2, y2, paint) }
    override fun polyline(pts: FloatArray, color: Int, stroke: Float) {
        if (pts.size < 4) return
        path.reset(); path.moveTo(pts[0], pts[1]); var i = 2; while (i + 1 < pts.size) { path.lineTo(pts[i], pts[i + 1]); i += 2 }
        paint.style = Paint.Style.STROKE; paint.shader = null; paint.strokeWidth = stroke; paint.strokeJoin = Paint.Join.ROUND; paint.strokeCap = Paint.Cap.ROUND; paint.color = c(color); canvas?.drawPath(path, paint)
    }
    override fun fillPolygon(pts: FloatArray, color: Int) {
        if (pts.size < 6) return
        path.reset(); path.moveTo(pts[0], pts[1]); var i = 2; while (i + 1 < pts.size) { path.lineTo(pts[i], pts[i + 1]); i += 2 }; path.close()
        paint.style = Paint.Style.FILL; paint.shader = null; paint.color = c(color); canvas?.drawPath(path, paint)
    }

    private fun bitmap(p: String): Bitmap? {
        cache.get(p)?.let { return it }
        if (p in missing) return null
        if (pending.add(p)) executor.execute {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                assets.open(p).use { BitmapFactory.decodeStream(it, null, bounds) }
                val scale = imageScale()
                val target = max(1, (bounds.outWidth * scale).toInt())
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= target && sample < 8) sample *= 2
                val o = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = if (p.endsWith(".png")) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565 }
                val bmp = assets.open(p).use { BitmapFactory.decodeStream(it, null, o) }
                if (bmp != null) cache.put(p, bmp) else missing.add(p)
            } catch (e: Exception) { missing.add(p) } finally { pending.remove(p) }
        }
        return null
    }

    override fun image(path: String, x: Float, y: Float, w: Float, h: Float, alpha: Float, tint: Int): Boolean {
        val b = bitmap(path) ?: return false
        paint.style = Paint.Style.FILL; paint.shader = null; paint.color = Color.WHITE; paint.alpha = (alpha * this.alpha * 255).toInt().coerceIn(0, 255)
        paint.isFilterBitmap = true
        dst.set(x, y, x + w, y + h)
        canvas?.drawBitmap(b, null, dst, paint)
        paint.alpha = 255
        return true
    }
    override fun imageRegion(path: String, sx: Float, sy: Float, sw: Float, sh: Float, x: Float, y: Float, w: Float, h: Float, alpha: Float): Boolean {
        val b = bitmap(path) ?: return false
        val k = b.width / max(1f, imageSizeOf(path)?.first?.toFloat() ?: b.width.toFloat())
        src.set((sx * k).toInt(), (sy * k).toInt(), ((sx + sw) * k).toInt(), ((sy + sh) * k).toInt()); dst.set(x, y, x + w, y + h)
        paint.alpha = (alpha * this.alpha * 255).toInt().coerceIn(0, 255); paint.isFilterBitmap = true
        canvas?.drawBitmap(b, src, dst, paint); paint.alpha = 255
        return true
    }
    private val sizes = HashMap<String, Pair<Int, Int>?>()
    private fun imageSizeOf(p: String): Pair<Int, Int>? = sizes.getOrPut(p) { try { val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; assets.open(p).use { BitmapFactory.decodeStream(it, null, o) }; o.outWidth to o.outHeight } catch (e: Exception) { null } }
    override fun imageRotated(path: String, cx: Float, cy: Float, w: Float, h: Float, degrees: Float, alpha: Float): Boolean {
        val b = bitmap(path) ?: return false
        val cv = canvas ?: return false
        cv.save(); cv.rotate(degrees, cx, cy)
        paint.alpha = (alpha * this.alpha * 255).toInt().coerceIn(0, 255); paint.isFilterBitmap = true
        dst.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2); cv.drawBitmap(b, null, dst, paint); paint.alpha = 255
        cv.restore(); return true
    }
    override fun gradientV(x: Float, y: Float, w: Float, h: Float, top: Int, bottom: Int) {
        paint.style = Paint.Style.FILL; paint.shader = LinearGradient(x, y, x, y + h, c(top), c(bottom), Shader.TileMode.CLAMP)
        canvas?.drawRect(x, y, x + w, y + h, paint); paint.shader = null
    }
    override fun gradientRadial(cx: Float, cy: Float, r: Float, inner: Int, outer: Int) {
        if (r <= 0f) return
        paint.style = Paint.Style.FILL; paint.shader = RadialGradient(cx, cy, r, c(inner), c(outer), Shader.TileMode.CLAMP)
        canvas?.drawCircle(cx, cy, r, paint); paint.shader = null
    }
    override fun text(s: String, x: Float, y: Float, size: Float, color: Int, font: Font, align: Align, alpha: Float) {
        if (s.isEmpty()) return
        textPaint.typeface = fonts[font]; textPaint.textSize = size; textPaint.color = c(color)
        if (alpha < 1f) textPaint.alpha = (textPaint.alpha * alpha).toInt()
        textPaint.textAlign = when (align) { Align.LEFT -> Paint.Align.LEFT; Align.CENTER -> Paint.Align.CENTER; Align.RIGHT -> Paint.Align.RIGHT }
        canvas?.drawText(s, x, y, textPaint)
    }
    private val measureCache = HashMap<String, Float>()
    override fun measure(s: String, size: Float, font: Font): Float {
        val key = "$font|${size.toInt()}|$s"
        measureCache[key]?.let { return it }
        if (measureCache.size > 4000) measureCache.clear()
        textPaint.typeface = fonts[font]; textPaint.textSize = size
        val w = textPaint.measureText(s); measureCache[key] = w; return w
    }
    override fun pushClip(x: Float, y: Float, w: Float, h: Float) { canvas?.save(); canvas?.clipRect(x, y, x + w, y + h) }
    override fun popClip() { canvas?.restore() }
    override fun pushAlpha(a: Float) { alphaStack.add(alpha); alpha *= a }
    override fun popAlpha() { if (alphaStack.isNotEmpty()) alpha = alphaStack.removeAt(alphaStack.size - 1) }
    override fun isLoaded(path: String): Boolean = cache.get(path) != null
    override fun preload(path: String) { bitmap(path) }
    override fun evictAll() { cache.evictAll(); missing.clear() }
    fun trim() { cache.trimToSize(cache.maxSize() / 3) }
}
