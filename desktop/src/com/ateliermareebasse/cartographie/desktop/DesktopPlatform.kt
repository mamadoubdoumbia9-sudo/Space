package com.ateliermareebasse.cartographie.desktop

import com.ateliermareebasse.cartographie.core.platform.Align
import com.ateliermareebasse.cartographie.core.platform.Audio
import com.ateliermareebasse.cartographie.core.platform.Font
import com.ateliermareebasse.cartographie.core.platform.Painter
import com.ateliermareebasse.cartographie.core.platform.Platform
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Plateforme bureau (harnais de test et QA) : assets lus sur disque, stockage dans build/desktop_data. */
class DesktopPlatform(val assetsRoot: File, val dataDir: File) : Platform {
    init { dataDir.mkdirs() }
    val logs = ArrayList<String>()
    var textInputProvider: ((String, String) -> String?)? = null
    override fun readAsset(path: String): ByteArray? { val f = File(assetsRoot, path); return if (f.isFile) f.readBytes() else null }
    override fun listAssets(dir: String): List<String> { val f = File(assetsRoot, dir); return if (f.isDirectory) (f.list()?.sorted() ?: emptyList()) else emptyList() }
    override fun writeFile(name: String, bytes: ByteArray) { File(dataDir, name).writeBytes(bytes) }
    override fun readFile(name: String): ByteArray? { val f = File(dataDir, name); return if (f.isFile) f.readBytes() else null }
    override fun deleteFile(name: String) { File(dataDir, name).delete() }
    override fun listFiles(prefix: String): List<String> = dataDir.list()?.filter { it.startsWith(prefix) }?.sorted() ?: emptyList()
    override fun vibrate(ms: Int, amplitude: Float) {}
    override fun systemLanguage(): String = java.util.Locale.getDefault().language
    override fun requestTextInput(title: String, initial: String, maxChars: Int, cb: (String?) -> Unit) { cb(textInputProvider?.invoke(title, initial) ?: "Je t'ai lu. Je reviens par l'ouest. — Lo") }
    override fun keepScreenOn(on: Boolean) {}
    override fun quit() { quitRequested = true }
    var quitRequested = false
    override fun log(msg: String) { logs.add(msg); System.err.println("[game] $msg") }
    override fun imageSize(path: String): Pair<Int, Int>? = try { val f = File(assetsRoot, path); if (!f.isFile) null else ImageIO.getImageReaders(ImageIO.createImageInputStream(f)).next().let { r -> r.input = ImageIO.createImageInputStream(f); r.getWidth(0) to r.getHeight(0) } } catch (e: Exception) { null }
}

class DesktopAudio : Audio {
    val played = ArrayList<String>()
    var music: String? = null
    var ambience: String? = null
    override fun playSfx(id: String, volume: Float, pitch: Float) { played.add(id) }
    override fun playMusic(id: String?, fadeMs: Int, loop: Boolean) { music = id }
    override fun playAmbience(id: String?, fadeMs: Int) { ambience = id }
    override fun playVoice(id: String?) { id?.let { played.add("voice:$it") } }
    override fun setVolumes(master: Float, music: Float, ambience: Float, sfx: Float) {}
    override fun pauseAll() {}
    override fun resumeAll() {}
    override fun stopAll() {}
    override fun sfxExists(id: String): Boolean = true
}

/** Peintre Java2D : même API que le peintre Android. */
class DesktopPainter(val platform: DesktopPlatform, var w: Int, var h: Int) : Painter {
    var image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    var g: Graphics2D = image.createGraphics()
    private val cache get() = sharedCache
    private val fonts = HashMap<Font, java.awt.Font>()
    companion object { val sharedCache = HashMap<String, BufferedImage?>(); @JvmStatic var decodeImages = true }
    private val clipStack = ArrayList<java.awt.Shape?>()
    private val alphaStack = ArrayList<Float>()
    private var alpha = 1f

    init { loadFonts(); begin() }

    private fun loadFonts() {
        fun load(name: String, fallback: String): java.awt.Font {
            val b = platform.readAsset("fonts/$name.ttf")
            return try { if (b != null) java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, b.inputStream()) else java.awt.Font(fallback, java.awt.Font.PLAIN, 12) } catch (e: Exception) { java.awt.Font(fallback, java.awt.Font.PLAIN, 12) }
        }
        fonts[Font.BODY] = load("body", "Serif"); fonts[Font.HAND] = load("hand", "Serif"); fonts[Font.TITLE] = load("title", "Serif"); fonts[Font.MONO] = load("mono", "Monospaced")
    }

    fun resize(nw: Int, nh: Int) { w = nw; h = nh; image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB); begin() }
    fun begin() { g = image.createGraphics(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON); g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR); clipStack.clear(); alphaStack.clear(); alpha = 1f }

    override val width: Float get() = w.toFloat()
    override val height: Float get() = h.toFloat()
    private fun c(color: Int, a: Float = 1f): Color { val aa = (((color ushr 24) and 255) * a * alpha).toInt().coerceIn(0, 255); return Color((color shr 16) and 255, (color shr 8) and 255, color and 255, aa) }
    override fun clear(color: Int) { g.color = c(color); g.fillRect(0, 0, w, h) }
    override fun fillRect(x: Float, y: Float, w: Float, h: Float, color: Int) { g.color = c(color); g.fill(Rectangle2D.Float(x, y, w, h)) }
    override fun strokeRect(x: Float, y: Float, w: Float, h: Float, color: Int, stroke: Float) { g.color = c(color); g.stroke = BasicStroke(stroke); g.draw(Rectangle2D.Float(x, y, w, h)) }
    override fun fillRoundRect(x: Float, y: Float, w: Float, h: Float, r: Float, color: Int) { g.color = c(color); g.fill(RoundRectangle2D.Float(x, y, w, h, r * 2, r * 2)) }
    override fun strokeRoundRect(x: Float, y: Float, w: Float, h: Float, r: Float, color: Int, stroke: Float) { g.color = c(color); g.stroke = BasicStroke(stroke); g.draw(RoundRectangle2D.Float(x, y, w, h, r * 2, r * 2)) }
    override fun fillCircle(cx: Float, cy: Float, r: Float, color: Int) { g.color = c(color); g.fill(Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2)) }
    override fun strokeCircle(cx: Float, cy: Float, r: Float, color: Int, stroke: Float) { g.color = c(color); g.stroke = BasicStroke(stroke); g.draw(Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2)) }
    override fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, stroke: Float) { g.color = c(color); g.stroke = BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); g.draw(java.awt.geom.Line2D.Float(x1, y1, x2, y2)) }
    override fun polyline(pts: FloatArray, color: Int, stroke: Float) { if (pts.size < 4) return; val p = Path2D.Float(); p.moveTo(pts[0], pts[1]); var i = 2; while (i + 1 < pts.size) { p.lineTo(pts[i], pts[i + 1]); i += 2 }; g.color = c(color); g.stroke = BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); g.draw(p) }
    override fun fillPolygon(pts: FloatArray, color: Int) { if (pts.size < 6) return; val p = Path2D.Float(); p.moveTo(pts[0], pts[1]); var i = 2; while (i + 1 < pts.size) { p.lineTo(pts[i], pts[i + 1]); i += 2 }; p.closePath(); g.color = c(color); g.fill(p) }
    private fun img(path: String): BufferedImage? = if (!decodeImages) null else cache.getOrPut(path) { try { platform.readAsset(path)?.let { ImageIO.read(it.inputStream()) } } catch (e: Exception) { null } }
    override fun image(path: String, x: Float, y: Float, w: Float, h: Float, alpha: Float, tint: Int): Boolean {
        val im = img(path) ?: return false
        val old = g.composite; g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (alpha * this.alpha).coerceIn(0f, 1f))
        g.drawImage(im, x.toInt(), y.toInt(), w.toInt(), h.toInt(), null); g.composite = old; return true
    }
    override fun imageRegion(path: String, sx: Float, sy: Float, sw: Float, sh: Float, x: Float, y: Float, w: Float, h: Float, alpha: Float): Boolean {
        val im = img(path) ?: return false
        val old = g.composite; g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (alpha * this.alpha).coerceIn(0f, 1f))
        g.drawImage(im, x.toInt(), y.toInt(), (x + w).toInt(), (y + h).toInt(), sx.toInt(), sy.toInt(), (sx + sw).toInt(), (sy + sh).toInt(), null); g.composite = old; return true
    }
    override fun imageRotated(path: String, cx: Float, cy: Float, w: Float, h: Float, degrees: Float, alpha: Float): Boolean {
        val im = img(path) ?: return false
        val old = g.transform; val oc = g.composite
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (alpha * this.alpha).coerceIn(0f, 1f))
        g.transform(AffineTransform.getRotateInstance(Math.toRadians(degrees.toDouble()), cx.toDouble(), cy.toDouble()))
        g.drawImage(im, (cx - w / 2).toInt(), (cy - h / 2).toInt(), w.toInt(), h.toInt(), null)
        g.transform = old; g.composite = oc; return true
    }
    override fun gradientV(x: Float, y: Float, w: Float, h: Float, top: Int, bottom: Int) { g.paint = GradientPaint(x, y, c(top), x, y + h, c(bottom)); g.fill(Rectangle2D.Float(x, y, w, h)); g.paint = null }
    override fun gradientRadial(cx: Float, cy: Float, r: Float, inner: Int, outer: Int) { if (r <= 0f) return; g.paint = RadialGradientPaint(cx, cy, r, floatArrayOf(0f, 1f), arrayOf(c(inner), c(outer))); g.fill(Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2)); g.paint = null }
    private fun font(f: Font, size: Float) = fonts[f]!!.deriveFont(size)
    override fun text(s: String, x: Float, y: Float, size: Float, color: Int, font: Font, align: Align, alpha: Float) {
        if (s.isEmpty()) return
        g.font = font(font, size); g.color = c(color, alpha)
        val wdt = g.fontMetrics.stringWidth(s)
        val xx = when (align) { Align.LEFT -> x; Align.CENTER -> x - wdt / 2f; Align.RIGHT -> x - wdt }
        g.drawString(s, xx, y)
    }
    override fun measure(s: String, size: Float, font: Font): Float { g.font = font(font, size); return g.fontMetrics.stringWidth(s).toFloat() }
    override fun pushClip(x: Float, y: Float, w: Float, h: Float) { clipStack.add(g.clip); g.clip(Rectangle2D.Float(x, y, w, h)) }
    override fun popClip() { if (clipStack.isNotEmpty()) g.clip = clipStack.removeAt(clipStack.size - 1) }
    override fun pushAlpha(a: Float) { alphaStack.add(alpha); alpha *= a }
    override fun popAlpha() { if (alphaStack.isNotEmpty()) alpha = alphaStack.removeAt(alphaStack.size - 1) }
    override fun isLoaded(path: String): Boolean = cache.containsKey(path)
    override fun preload(path: String) { img(path) }
    override fun evictAll() { cache.clear() }
}
