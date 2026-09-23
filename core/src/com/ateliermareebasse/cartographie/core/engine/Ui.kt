package com.ateliermareebasse.cartographie.core.engine

import com.ateliermareebasse.cartographie.core.platform.Align
import com.ateliermareebasse.cartographie.core.platform.Colors
import com.ateliermareebasse.cartographie.core.platform.Font
import com.ateliermareebasse.cartographie.core.platform.Painter
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Aides de dessin : mise en page adaptative, texte, boutons « papier ». */
class Ui(val game: Game) {
    val p: Painter get() = game.painter
    val w: Float get() = p.width
    val h: Float get() = p.height
    /** Échelle UI : 1.0 pour 720 px de hauteur logique (paysage) ; bornée pour les grands écrans. */
    val s: Float get() = (min(w, h) / 720f).coerceIn(0.55f, 2.2f) * game.settings.uiScale
    val portrait: Boolean get() = h > w
    fun font(base: Float) = base * s * game.settings.textScale
    val margin: Float get() = 18f * s

    // Zones sûres (encoche) : marge haute légère
    val safeTop: Float get() = 10f * s

    private val wrapCache = HashMap<String, List<String>>()

    fun wrap(text: String, size: Float, maxWidth: Float, font: Font = Font.BODY): List<String> {
        val key = "$text|$size|${maxWidth.toInt()}|$font"
        wrapCache[key]?.let { return it }
        if (wrapCache.size > 600) wrapCache.clear()
        val out = ArrayList<String>()
        for (para in text.split('\n')) {
            val words = para.split(' ')
            var line = StringBuilder()
            for (word in words) {
                val test = if (line.isEmpty()) word else "$line $word"
                if (p.measure(test, size, font) <= maxWidth || line.isEmpty()) { line = StringBuilder(test) }
                else { out.add(line.toString()); line = StringBuilder(word) }
            }
            out.add(line.toString())
        }
        wrapCache[key] = out
        return out
    }

    /** Dessine un paragraphe ; retourne la hauteur occupée. */
    fun paragraph(text: String, x: Float, y: Float, maxWidth: Float, size: Float, color: Int, font: Font = Font.BODY, align: Align = Align.LEFT, lineHeight: Float = 1.32f, alpha: Float = 1f, maxLines: Int = 999): Float {
        val lines = wrap(text, size, maxWidth, font)
        var yy = y
        var n = 0
        for (l in lines) {
            if (n++ >= maxLines) break
            val xx = when (align) { Align.LEFT -> x; Align.CENTER -> x + maxWidth / 2; Align.RIGHT -> x + maxWidth }
            p.text(l, xx, yy + size, size, color, font, align, alpha)
            yy += size * lineHeight
        }
        return yy - y
    }

    fun paragraphHeight(text: String, maxWidth: Float, size: Float, font: Font = Font.BODY, lineHeight: Float = 1.32f): Float =
        wrap(text, size, maxWidth, font).size * size * lineHeight

    /** Panneau papier (fond crème, ombre, liseré encre). */
    fun paper(x: Float, y: Float, w: Float, h: Float, alpha: Float = 0.96f, dark: Boolean = false) {
        val bg = if (dark) 0xFF1E2230.toInt() else Colors.PAPER
        p.fillRoundRect(x + 3 * s, y + 4 * s, w, h, 6 * s, Colors.withAlpha(Colors.BLACK, 0.28f * alpha))
        p.fillRoundRect(x, y, w, h, 6 * s, Colors.withAlpha(bg, alpha))
        p.strokeRoundRect(x, y, w, h, 6 * s, Colors.withAlpha(if (dark) Colors.LAITON else Colors.INK, 0.55f * alpha), 1.2f * s)
        if (game.settings.grain) p.image("art/ui/grain.png", x, y, w, h, 0.12f * alpha)
    }

    /** Bandeau translucide sombre (dialogues). */
    fun inkPanel(x: Float, y: Float, w: Float, h: Float, alpha: Float = 0.82f) {
        p.fillRoundRect(x, y, w, h, 8 * s, Colors.withAlpha(0xFF141821.toInt(), alpha))
        p.strokeRoundRect(x, y, w, h, 8 * s, Colors.withAlpha(Colors.LAITON, 0.35f * alpha), 1f * s)
    }

    class Btn(val id: String, var x: Float, var y: Float, var w: Float, var h: Float, var label: String, var enabled: Boolean = true, var gold: Boolean = false, var icon: String? = null, var small: Boolean = false)

    fun button(b: Btn, pressed: Boolean = false) {
        val a = if (b.enabled) 1f else 0.45f
        val bg = if (b.gold) Colors.OR_ENCRE else Colors.PAPER
        p.fillRoundRect(b.x + 2 * s, b.y + 3 * s, b.w, b.h, 8 * s, Colors.withAlpha(Colors.BLACK, 0.25f * a))
        p.fillRoundRect(b.x, b.y + (if (pressed) 2 * s else 0f), b.w, b.h, 8 * s, Colors.withAlpha(bg, if (pressed) 0.85f * a else 0.97f * a))
        p.strokeRoundRect(b.x, b.y + (if (pressed) 2 * s else 0f), b.w, b.h, 8 * s, Colors.withAlpha(Colors.INK, 0.7f * a), 1.3f * s)
        val fs = font(if (b.small) 15f else 18f)
        var tx = b.x + b.w / 2
        if (b.icon != null) { icon(b.icon!!, b.x + 16 * s, b.y + b.h / 2, 10 * s, Colors.withAlpha(Colors.INK, a)); tx += 8 * s }
        val lines = wrap(b.label, fs, b.w - 24 * s)
        val total = lines.size * fs * 1.2f
        var yy = b.y + (b.h - total) / 2 + (if (pressed) 2 * s else 0f)
        for (l in lines) { p.text(l, tx, yy + fs * 0.95f, fs, Colors.withAlpha(Colors.INK, a), Font.BODY, Align.CENTER); yy += fs * 1.2f }
    }

    fun hit(b: Btn, x: Float, y: Float) = b.enabled && x >= b.x && x <= b.x + b.w && y >= b.y && y <= b.y + b.h
    fun inRect(x: Float, y: Float, rx: Float, ry: Float, rw: Float, rh: Float) = x >= rx && x <= rx + rw && y >= ry && y <= ry + rh

    /** Icônes vectorielles simples (pas d'images : net à toute résolution). */
    fun icon(name: String, cx: Float, cy: Float, r: Float, color: Int) {
        when (name) {
            "back" -> p.polyline(floatArrayOf(cx + r * 0.6f, cy - r, cx - r * 0.6f, cy, cx + r * 0.6f, cy + r), color, 2.2f * s)
            "close" -> { p.line(cx - r, cy - r, cx + r, cy + r, color, 2.2f * s); p.line(cx - r, cy + r, cx + r, cy - r, color, 2.2f * s) }
            "carnet" -> { p.strokeRoundRect(cx - r, cy - r * 1.2f, r * 2, r * 2.4f, r * 0.2f, color, 1.8f * s); p.line(cx - r * 0.5f, cy - r * 0.5f, cx + r * 0.5f, cy - r * 0.5f, color, 1.5f * s); p.line(cx - r * 0.5f, cy, cx + r * 0.5f, cy, color, 1.5f * s); p.line(cx - r * 0.5f, cy + r * 0.5f, cx + r * 0.2f, cy + r * 0.5f, color, 1.5f * s) }
            "sacoche" -> { p.strokeRoundRect(cx - r, cy - r * 0.6f, r * 2, r * 1.6f, r * 0.25f, color, 1.8f * s); p.polyline(floatArrayOf(cx - r * 0.5f, cy - r * 0.6f, cx - r * 0.5f, cy - r * 1.1f, cx + r * 0.5f, cy - r * 1.1f, cx + r * 0.5f, cy - r * 0.6f), color, 1.6f * s) }
            "map" -> { p.polyline(floatArrayOf(cx - r, cy - r * 0.7f, cx - r * 0.35f, cy - r, cx + r * 0.35f, cy - r * 0.6f, cx + r, cy - r, cx + r, cy + r * 0.7f, cx + r * 0.35f, cy + r, cx - r * 0.35f, cy + r * 0.6f, cx - r, cy + r, cx - r, cy - r * 0.7f), color, 1.6f * s); p.line(cx - r * 0.35f, cy - r, cx - r * 0.35f, cy + r * 0.6f, color, 1f * s); p.line(cx + r * 0.35f, cy - r * 0.6f, cx + r * 0.35f, cy + r, color, 1f * s) }
            "pause" -> { p.fillRect(cx - r * 0.7f, cy - r, r * 0.45f, r * 2, color); p.fillRect(cx + r * 0.25f, cy - r, r * 0.45f, r * 2, color) }
            "lens" -> { p.strokeCircle(cx - r * 0.2f, cy - r * 0.2f, r * 0.7f, color, 2f * s); p.line(cx + r * 0.3f, cy + r * 0.3f, cx + r, cy + r, color, 2.4f * s) }
            "dog" -> { p.fillCircle(cx, cy, r * 0.75f, color); p.fillCircle(cx - r * 0.8f, cy - r * 0.5f, r * 0.35f, color); p.fillCircle(cx + r * 0.8f, cy - r * 0.5f, r * 0.35f, color); p.fillCircle(cx, cy + r * 0.25f, r * 0.22f, Colors.PAPER) }
            "ear" -> { p.strokeCircle(cx, cy, r, color, 1.8f * s); p.strokeCircle(cx, cy, r * 0.55f, color, 1.5f * s); p.fillCircle(cx, cy, r * 0.18f, color) }
            "hand" -> { p.strokeRoundRect(cx - r * 0.7f, cy - r * 0.3f, r * 1.4f, r * 1.3f, r * 0.3f, color, 1.8f * s); for (k in 0..3) p.line(cx - r * 0.5f + k * r * 0.33f, cy - r * 0.3f, cx - r * 0.5f + k * r * 0.33f, cy - r * 1f, color, 1.8f * s) }
            "arrow" -> p.polyline(floatArrayOf(cx - r * 0.6f, cy - r, cx + r * 0.6f, cy, cx - r * 0.6f, cy + r), color, 2.2f * s)
            "arrow_l" -> p.polyline(floatArrayOf(cx + r * 0.6f, cy - r, cx - r * 0.6f, cy, cx + r * 0.6f, cy + r), color, 2.2f * s)
            "arrow_u" -> p.polyline(floatArrayOf(cx - r, cy + r * 0.6f, cx, cy - r * 0.6f, cx + r, cy + r * 0.6f), color, 2.2f * s)
            "arrow_d" -> p.polyline(floatArrayOf(cx - r, cy - r * 0.6f, cx, cy + r * 0.6f, cx + r, cy - r * 0.6f), color, 2.2f * s)
            "door" -> { p.strokeRoundRect(cx - r * 0.7f, cy - r, r * 1.4f, r * 2f, r * 0.15f, color, 1.8f * s); p.fillCircle(cx + r * 0.3f, cy, r * 0.12f, color) }
            "check" -> p.polyline(floatArrayOf(cx - r, cy, cx - r * 0.3f, cy + r * 0.7f, cx + r, cy - r * 0.7f), color, 2.4f * s)
            "star" -> { val pts = FloatArray(20); for (k in 0 until 10) { val rr = if (k % 2 == 0) r else r * 0.45f; val a = -Math.PI / 2 + k * Math.PI / 5; pts[k * 2] = (cx + rr * Math.cos(a)).toFloat(); pts[k * 2 + 1] = (cy + rr * Math.sin(a)).toFloat() }; p.fillPolygon(pts, color) }
            "bell" -> { p.fillPolygon(floatArrayOf(cx - r * 0.8f, cy + r * 0.5f, cx - r * 0.5f, cy - r * 0.6f, cx, cy - r, cx + r * 0.5f, cy - r * 0.6f, cx + r * 0.8f, cy + r * 0.5f), color); p.fillCircle(cx, cy + r * 0.8f, r * 0.22f, color) }
            "plus" -> { p.line(cx - r, cy, cx + r, cy, color, 2f * s); p.line(cx, cy - r, cx, cy + r, color, 2f * s) }
            "minus" -> p.line(cx - r, cy, cx + r, cy, color, 2f * s)
            "gear" -> { p.strokeCircle(cx, cy, r * 0.55f, color, 2f * s); for (k in 0 until 8) { val a = k * Math.PI / 4; p.line((cx + r * 0.6f * Math.cos(a)).toFloat(), (cy + r * 0.6f * Math.sin(a)).toFloat(), (cx + r * Math.cos(a)).toFloat(), (cy + r * Math.sin(a)).toFloat(), color, 2.2f * s) } }
            "page" -> { p.strokeRoundRect(cx - r * 0.75f, cy - r, r * 1.5f, r * 2, r * 0.1f, color, 1.6f * s); p.line(cx - r * 0.4f, cy - r * 0.4f, cx + r * 0.4f, cy - r * 0.4f, color, 1.2f * s); p.line(cx - r * 0.4f, cy, cx + r * 0.4f, cy, color, 1.2f * s); p.line(cx - r * 0.4f, cy + r * 0.4f, cx + r * 0.1f, cy + r * 0.4f, color, 1.2f * s) }
            "echo" -> { p.strokeCircle(cx, cy, r * 0.4f, color, 1.6f * s); p.strokeCircle(cx, cy, r * 0.75f, Colors.withAlpha(color, 0.6f), 1.4f * s); p.strokeCircle(cx, cy, r * 1.05f, Colors.withAlpha(color, 0.35f), 1.2f * s) }
            "seat" -> { p.strokeRoundRect(cx - r, cy - r * 0.2f, r * 2, r * 0.5f, r * 0.1f, color, 1.8f * s); p.line(cx - r * 0.8f, cy + r * 0.3f, cx - r * 0.8f, cy + r, color, 1.8f * s); p.line(cx + r * 0.8f, cy + r * 0.3f, cx + r * 0.8f, cy + r, color, 1.8f * s); p.line(cx - r, cy - r * 0.2f, cx - r, cy - r, color, 1.8f * s) }
            "save" -> { p.strokeRoundRect(cx - r, cy - r, r * 2, r * 2, r * 0.15f, color, 1.8f * s); p.fillRect(cx - r * 0.5f, cy - r, r, r * 0.6f, color); p.fillRect(cx - r * 0.6f, cy + r * 0.2f, r * 1.2f, r * 0.6f, Colors.withAlpha(color, 0.5f)) }
            "hint" -> { p.strokeCircle(cx, cy - r * 0.2f, r * 0.6f, color, 1.8f * s); p.fillRect(cx - r * 0.25f, cy + r * 0.45f, r * 0.5f, r * 0.35f, color) }
            "key" -> { p.strokeCircle(cx - r * 0.45f, cy, r * 0.45f, color, 2f * s); p.line(cx - r * 0.05f, cy, cx + r, cy, color, 2f * s); p.line(cx + r * 0.55f, cy, cx + r * 0.55f, cy + r * 0.45f, color, 2f * s); p.line(cx + r * 0.9f, cy, cx + r * 0.9f, cy + r * 0.35f, color, 2f * s) }
            "tool" -> { p.line(cx - r * 0.8f, cy + r * 0.8f, cx + r * 0.3f, cy - r * 0.3f, color, 2.6f * s); p.strokeCircle(cx + r * 0.5f, cy - r * 0.5f, r * 0.45f, color, 2f * s); p.fillRect(cx + r * 0.35f, cy - r * 0.65f, r * 0.3f, r * 0.3f, Colors.PAPER) }
            "heart" -> { p.fillCircle(cx - r * 0.42f, cy - r * 0.3f, r * 0.45f, color); p.fillCircle(cx + r * 0.42f, cy - r * 0.3f, r * 0.45f, color); p.fillPolygon(floatArrayOf(cx - r * 0.85f, cy - r * 0.15f, cx + r * 0.85f, cy - r * 0.15f, cx, cy + r * 0.9f), color) }
            "cup" -> { p.polyline(floatArrayOf(cx - r * 0.7f, cy - r * 0.6f, cx - r * 0.5f, cy + r * 0.7f, cx + r * 0.5f, cy + r * 0.7f, cx + r * 0.7f, cy - r * 0.6f, cx - r * 0.7f, cy - r * 0.6f), color, 1.8f * s); p.polyline(floatArrayOf(cx - r * 0.3f, cy - r, cx - r * 0.3f, cy - r * 0.75f), color, 1.4f * s); p.polyline(floatArrayOf(cx, cy - r * 1.1f, cx, cy - r * 0.75f), color, 1.4f * s); p.polyline(floatArrayOf(cx + r * 0.3f, cy - r, cx + r * 0.3f, cy - r * 0.75f), color, 1.4f * s) }
            "moon0" -> p.strokeCircle(cx, cy, r, color, 1.6f * s)                       // nouvelle lune
            "moon1" -> { p.strokeCircle(cx, cy, r, color, 1.6f * s); p.fillPolygon(halfDisc(cx, cy, r, left = true), color) }   // premier quartier
            "moon2" -> p.fillCircle(cx, cy, r, color)                                     // pleine lune
            "moon3" -> { p.strokeCircle(cx, cy, r, color, 1.6f * s); p.fillPolygon(halfDisc(cx, cy, r, left = false), color) } // dernier quartier
            "trident" -> { p.line(cx, cy - r, cx, cy + r, color, 2.2f * s); p.polyline(floatArrayOf(cx - r * 0.7f, cy - r * 0.9f, cx - r * 0.7f, cy - r * 0.2f, cx + r * 0.7f, cy - r * 0.2f, cx + r * 0.7f, cy - r * 0.9f), color, 2.2f * s); p.line(cx - r * 0.4f, cy + r, cx + r * 0.4f, cy + r, color, 2.2f * s) }
            "siren" -> { p.polyline(wave(cx - r, cy + r * 0.5f, r * 2, r * 0.3f), color, 2f * s); p.strokeCircle(cx, cy - r * 0.45f, r * 0.35f, color, 2f * s); p.line(cx - r * 0.5f, cy + r * 0.1f, cx + r * 0.5f, cy + r * 0.1f, color, 2f * s) }
            "anchor" -> { p.strokeCircle(cx, cy - r * 0.75f, r * 0.22f, color, 2f * s); p.line(cx, cy - r * 0.5f, cx, cy + r, color, 2.2f * s); p.line(cx - r * 0.6f, cy - r * 0.15f, cx + r * 0.6f, cy - r * 0.15f, color, 2f * s); p.polyline(floatArrayOf(cx - r, cy + r * 0.3f, cx - r * 0.6f, cy + r * 0.9f, cx, cy + r, cx + r * 0.6f, cy + r * 0.9f, cx + r, cy + r * 0.3f), color, 2.2f * s) }
            "silence" -> p.polyline(wave(cx - r, cy, r * 2, r * 0.35f), color, 2.2f * s)
            "table" -> { p.strokeRoundRect(cx - r, cy - r, r * 2, r * 2, r * 0.1f, color, 2f * s); p.fillRect(cx - r * 0.55f, cy - r * 0.55f, r * 1.1f, r * 1.1f, color) }
            "fish" -> { p.polyline(floatArrayOf(cx - r, cy, cx - r * 0.4f, cy - r * 0.55f, cx + r * 0.4f, cy - r * 0.4f, cx + r * 0.6f, cy, cx + r * 0.4f, cy + r * 0.4f, cx - r * 0.4f, cy + r * 0.55f, cx - r, cy), color, 1.8f * s); p.polyline(floatArrayOf(cx + r * 0.6f, cy, cx + r, cy - r * 0.5f, cx + r, cy + r * 0.5f, cx + r * 0.6f, cy), color, 1.8f * s); p.fillCircle(cx - r * 0.55f, cy - r * 0.15f, r * 0.1f, color) }
            "star4" -> p.fillPolygon(floatArrayOf(cx, cy - r, cx + r * 0.25f, cy - r * 0.25f, cx + r, cy, cx + r * 0.25f, cy + r * 0.25f, cx, cy + r, cx - r * 0.25f, cy + r * 0.25f, cx - r, cy, cx - r * 0.25f, cy - r * 0.25f), color)
            "wave_moon" -> { p.polyline(wave(cx - r, cy + r * 0.55f, r * 2, r * 0.25f), color, 1.8f * s); p.strokeCircle(cx + r * 0.1f, cy - r * 0.35f, r * 0.45f, color, 1.8f * s); p.fillCircle(cx + r * 0.32f, cy - r * 0.48f, r * 0.42f, Colors.TRANSPARENT) }
            "backspace" -> { p.polyline(floatArrayOf(cx + r, cy - r * 0.6f, cx - r * 0.3f, cy - r * 0.6f, cx - r, cy, cx - r * 0.3f, cy + r * 0.6f, cx + r, cy + r * 0.6f, cx + r, cy - r * 0.6f), color, 1.6f * s); p.line(cx - r * 0.1f, cy - r * 0.3f, cx + r * 0.5f, cy + r * 0.3f, color, 1.6f * s); p.line(cx - r * 0.1f, cy + r * 0.3f, cx + r * 0.5f, cy - r * 0.3f, color, 1.6f * s) }
            "space" -> p.polyline(floatArrayOf(cx - r, cy - r * 0.2f, cx - r, cy + r * 0.4f, cx + r, cy + r * 0.4f, cx + r, cy - r * 0.2f), color, 1.6f * s)
            "circle_o" -> p.strokeCircle(cx, cy, r * 0.7f, color, 1.5f * s)
            "tri_r" -> p.fillPolygon(floatArrayOf(cx - r * 0.6f, cy - r, cx + r * 0.8f, cy, cx - r * 0.6f, cy + r), color)
            "wind" -> { p.polyline(floatArrayOf(cx - r, cy - r * 0.4f, cx + r * 0.3f, cy - r * 0.4f, cx + r * 0.6f, cy - r * 0.8f), color, 1.8f * s); p.polyline(floatArrayOf(cx - r, cy + r * 0.2f, cx + r * 0.7f, cy + r * 0.2f, cx + r, cy + r * 0.6f), color, 1.8f * s) }
            else -> p.fillCircle(cx, cy, r * 0.5f, color)
        }
    }

    private fun halfDisc(cx: Float, cy: Float, r: Float, left: Boolean): FloatArray {
        val n = 14; val pts = FloatArray((n + 1) * 2)
        for (k in 0..n) { val a = -Math.PI / 2 + Math.PI * k / n; val sx = if (left) -1 else 1; pts[k * 2] = (cx + sx * r * Math.cos(a)).toFloat(); pts[k * 2 + 1] = (cy + r * Math.sin(a)).toFloat() }
        return pts
    }
    private fun wave(x: Float, y: Float, w: Float, amp: Float): FloatArray {
        val n = 16; val pts = FloatArray((n + 1) * 2)
        for (k in 0..n) { pts[k * 2] = x + w * k / n; pts[k * 2 + 1] = (y + amp * Math.sin(k * 2 * Math.PI / n * 2)).toFloat() }
        return pts
    }

    /** Point d'intérêt pulsant sur un tableau. */
    fun hotspotMarker(cx: Float, cy: Float, kind: String, t: Float, color: Int = Colors.PAPER, highlight: Boolean = false) {
        val r = (9f + 1.5f * sin(t * 3f)) * s
        val a = if (highlight) 0.95f else 0.75f
        p.fillCircle(cx, cy, r + 4 * s, Colors.withAlpha(Colors.INK, 0.35f * a))
        p.strokeCircle(cx, cy, r + 4 * s, Colors.withAlpha(color, 0.8f * a), 1.4f * s)
        val ic = when (kind) { "TALK" -> "dog"; "PUZZLE" -> "gear"; "TAKE" -> "hand"; "LISTEN" -> "ear"; "READ", "PAGE" -> "page"; "ECHO" -> "echo"; "SIT" -> "seat"; "BELL" -> "bell"; "SECRET", "MURMURE" -> "star"; else -> "lens" }
        if (kind == "TALK") p.fillCircle(cx, cy, r * 0.45f, Colors.withAlpha(color, a)) else icon(ic, cx, cy, r * 0.55f, Colors.withAlpha(color, a))
    }

    fun title(text: String, y: Float, size: Float = 30f, color: Int = Colors.INK, alpha: Float = 1f) {
        p.text(text, w / 2, y, font(size), Colors.withAlpha(color, alpha), Font.TITLE, Align.CENTER)
    }

    fun ornament(cx: Float, cy: Float, width: Float, color: Int = Colors.INK) {
        p.line(cx - width / 2, cy, cx - 12 * s, cy, Colors.withAlpha(color, 0.5f), 1f * s)
        p.line(cx + 12 * s, cy, cx + width / 2, cy, Colors.withAlpha(color, 0.5f), 1f * s)
        p.fillCircle(cx, cy, 2.2f * s, color)
    }

    /** Barre/jauge discrète. */
    fun gauge(x: Float, y: Float, w: Float, h: Float, v: Float, color: Int = Colors.LAITON) {
        p.fillRoundRect(x, y, w, h, h / 2, Colors.withAlpha(Colors.INK, 0.35f))
        p.fillRoundRect(x, y, max(h, w * v.coerceIn(0f, 1f)), h, h / 2, color)
    }

    fun scrim(alpha: Float = 0.55f) = p.fillRect(0f, 0f, w, h, Colors.withAlpha(Colors.BLACK, alpha))

    /** Grand titre gravé (écran-titre, cartons d'acte). */
    fun engraved(text: String, cx: Float, cy: Float, size: Float, color: Int, alpha: Float = 1f) {
        p.text(text, cx + 1.5f * s, cy + 1.5f * s, size, Colors.withAlpha(Colors.BLACK, 0.5f * alpha), Font.TITLE, Align.CENTER)
        p.text(text, cx, cy, size, Colors.withAlpha(color, alpha), Font.TITLE, Align.CENTER)
    }
}
