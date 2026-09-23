package com.ateliermareebasse.cartographie.core.puzzles

import com.ateliermareebasse.cartographie.core.engine.Game
import com.ateliermareebasse.cartographie.core.engine.Ui
import com.ateliermareebasse.cartographie.core.platform.Align
import com.ateliermareebasse.cartographie.core.platform.Colors
import com.ateliermareebasse.cartographie.core.platform.Font
import com.ateliermareebasse.cartographie.core.platform.Input
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ───────────────────────────── E12 — LA GROTTE DES MARÉES ─────────────────────────────
class E12Grotte(game: Game) : PuzzleScreen(game, "E12") {
    private var repere = 0            // 0 trident, 1 sirène, 2 ancre, 3 silence, 4 arrivé
    private var claps = 0
    private var clapT = 0f
    private val clapDur = 1.4f        // 4 s dans le lore, accéléré
    private val targets = intArrayOf(3, 5, 2)
    private val dirs = listOf("est", "descente", "droit")
    private var silence = 0f
    private var lampX = 0.5f; private var lampY = 0.5f
    override val boardAspect = 1.5f
    override fun update(dt: Float) {
        super.update(dt)
        if (repere < 3) { clapT += dt; if (clapT >= clapDur) { clapT -= clapDur; claps++; game.audio.playSfx("clapotis", 0.8f) } }
        else if (repere == 3) { silence += dt }
    }
    private fun choose(dir: String) {
        val target = targets[repere]
        if (dir == dirs[repere] && claps == target) { repere++; claps = 0; clapT = 0f; silence = 0f; game.sfx("pas_eau_${(repere % 3) + 1}"); say(game.str("e12.good_turn", repere)) }
        else { fail(if (dir != dirs[repere]) game.str("e12.wrong_dir") else game.str("e12.wrong_count")); repere = (repere - 1).coerceAtLeast(0); claps = 0; clapT = 0f }
    }
    override fun renderBoard(x: Float, y: Float, w: Float, h: Float) {
        p.fillRect(x, y, w, h, 0xFF06101E.toInt())
        // halo de la lampe-tempête
        p.gradientRadial(x + lampX * w, y + lampY * h, w * 0.28f, Colors.withAlpha(0xFFFFE0A0.toInt(), 0.32f), Colors.TRANSPARENT)
        // parois
        for (k in 0 until 14) { val px = x + w * k / 14f; p.line(px, y, px + 20 * s, y + h * 0.18f + sin(k * 2f) * 10 * s, Colors.withAlpha(0xFF2A3A55.toInt(), 0.7f), 3f * s) }
        p.fillRect(x, y + h * 0.82f, w, h * 0.18f, Colors.withAlpha(0xFF1A3A66.toInt(), 0.7f))
        // repère gravé
        val glyph = listOf("trident", "siren", "anchor", "silence", "table")[repere.coerceAtMost(4)]
        val name = listOf(game.str("e12.trident"), game.str("e12.sirene"), game.str("e12.ancre"), game.str("e12.silence"), game.str("e12.table"))[repere.coerceAtMost(4)]
        ui.icon(glyph, x + w * 0.5f, y + h * 0.36f, ui.font(22f), Colors.withAlpha(Colors.PAPER, 0.85f))
        p.text(name, x + w * 0.5f, y + h * 0.5f, ui.font(13f), Colors.PAPER_DARK, Font.HAND, Align.CENTER)
        if (repere < 3) {
            val rule = listOf(game.str("e12.rule1"), game.str("e12.rule2"), game.str("e12.rule3"))[repere]
            p.text(rule, x + w * 0.5f, y + h * 0.58f, ui.font(12f), Colors.withAlpha(Colors.PAPER, 0.8f), Font.MONO, Align.CENTER)
            // compte des clapotis en points d'encre
            val gap = if (assist() && claps == targets[repere] - 1) 22 * s else 16 * s
            for (k in 0 until claps.coerceAtMost(12)) p.fillCircle(x + w * 0.5f - (claps.coerceAtMost(12) - 1) * gap / 2 + k * gap, y + h * 0.66f, (5f + (if (k == claps - 1) 2f * (1f - clapT / clapDur) else 0f)) * s, Colors.withAlpha(0xFF9FB8FF.toInt(), 0.9f))
            p.text(game.str("e12.count", claps), x + w * 0.5f, y + h * 0.74f, ui.font(12f), Colors.PAPER, Font.MONO, Align.CENTER)
            val bw = min(120 * s, w / 3.4f)
            boardBtn("nord", x + w * 0.5f - bw * 1.6f, y + h * 0.86f, bw, 30 * s, game.str("e12.nord"))
            boardBtn("est", x + w * 0.5f - bw * 0.5f, y + h * 0.86f, bw, 30 * s, game.str("e12.est"))
            boardBtn("descente", x + w * 0.5f + bw * 0.6f, y + h * 0.86f, bw, 30 * s, game.str("e12.descente"))
            boardBtn("droit", x + w * 0.5f - bw * 0.5f, y + h * 0.86f - 36 * s, bw, 30 * s, game.str("e12.droit"))
        } else if (repere == 3) {
            p.text(game.str("e12.silence_hint"), x + w * 0.5f, y + h * 0.66f, ui.font(12f), Colors.PAPER_DARK, Font.HAND, Align.CENTER)
            boardBtn("walk", x + w * 0.5f - 70 * s, y + h * 0.86f, 140 * s, 30 * s, game.str("e12.walk"))
        }
    }
    override fun onBoardInput(e: Input, x: Float, y: Float, w: Float, h: Float): Boolean {
        if (e is Input.Move || e is Input.Down) { inBoard(e, x, y, w, h)?.let { (ex, ey) -> lampX = (ex - x) / w; lampY = (ey - y) / h }; if (e is Input.Move) return true }
        if (e !is Input.Up) return false
        val b = btns.firstOrNull { ui.hit(it, e.x, e.y) } ?: return false
        if (b.id == "walk") { if (silence > 5f) { repere = 4; game.sfx("eau_ride_3"); solve() } else fail(game.str("e12.too_soon")); return true }
        choose(b.id)
        return true
    }
}

// ───────────────────────────── E13 — LES TROIS SŒURS ─────────────────────────────
class E13TroisSoeurs(game: Game) : PuzzleScreen(game, "E13") {
    private var az = 180f
    private var downX = 0f; private var downAz = 180f
    private var grande: Float? = null
    private var petite: Float? = null
    private var mapMode = false
    private val repèresOk = st.zonesVisited.size >= 7 || st.ngPlus
    override val boardAspect = 1.5f
    private fun snap(target: Float): Float { val d = abs(az - target); return if (assist() && d < 6f) target else az }
    override fun renderBoard(x: Float, y: Float, w: Float, h: Float) {
        if (!mapMode) {
            // lunette du théodolite
            p.fillRect(x, y, w, h, 0xFF0A1020.toInt())
            p.fillRect(x, y + h * 0.55f, w, h * 0.45f, 0xFF0E1A2E.toInt())
            // silhouettes selon l'azimut (champ 60°)
            fun sil(target: Float, hgt: Float) { val d = target - az; if (abs(d) < 30f) { val sx = x + w * (0.5f + d / 60f); p.fillRect(sx - 6 * s, y + h * 0.55f - hgt, 12 * s, hgt, Colors.withAlpha(Colors.BLACK, 0.9f)); if (assist()) p.strokeCircle(sx, y + h * 0.55f - hgt / 2, 26 * s + 4 * s * sin(time * 4), Colors.withAlpha(Colors.LAITON, 0.5f), 1f * s) } }
            sil(262f, 70 * s); sil(118f, 46 * s)
            // réticule
            p.line(x + w / 2, y + 10 * s, x + w / 2, y + h - 10 * s, Colors.withAlpha(Colors.LAITON, 0.7f), 1f * s)
            p.line(x + 10 * s, y + h * 0.5f, x + w - 10 * s, y + h * 0.5f, Colors.withAlpha(Colors.LAITON, 0.7f), 1f * s)
            p.strokeCircle(x + w / 2, y + h * 0.5f, 40 * s, Colors.withAlpha(Colors.LAITON, 0.7f), 1f * s)
            p.text(game.str("e13.azimut", az.toInt()), x + w / 2, y + 26 * s, ui.font(15f), Colors.PAPER, Font.MONO, Align.CENTER)
            p.text(game.str("e13.drag"), x + w / 2, y + h - 48 * s, ui.font(11f), Colors.PAPER_DARK, Font.HAND, Align.CENTER)
            boardBtn("shoot", x + w / 2 - 60 * s, y + h - 38 * s, 120 * s, 30 * s, game.str("e13.shoot"))
            grande?.let { p.text("${game.str("e13.grande")} : ${it.toInt()}°", x + 12 * s, y + 22 * s, ui.font(11f), Colors.LAITON, Font.MONO) }
            petite?.let { p.text("${game.str("e13.petite")} : ${it.toInt()}°", x + w - 12 * s, y + 22 * s, ui.font(11f), Colors.LAITON, Font.MONO, Align.RIGHT) }
            if (grande != null && petite != null) boardBtn("map", x + w - 130 * s, y + h - 38 * s, 116 * s, 30 * s, game.str("e13.to_map"), active = true)
            if (!repèresOk) ui.paragraph(game.str("e13.missing"), x + 20 * s, y + h * 0.62f, w - 40 * s, ui.font(12f), Colors.PAPER_DARK, Font.HAND, Align.CENTER)
        } else {
            if (!p.image("art/ui/map_world.jpg", x, y, w, h)) p.fillRect(x, y, w, h, Colors.PAPER_SHADE)
            // repères-phares sur la carte (positions relatives) et droites de relèvement
            val g = 0.12f to 0.62f; val pt = 0.70f to 0.86f; val terrace = 0.58f to 0.38f
            fun lineFrom(o: Pair<Float, Float>, azi: Float) { val a = Math.toRadians((azi + 180).toDouble()); val len = 1.2f; p.line(x + o.first * w, y + o.second * h, x + (o.first + len * sin(a).toFloat()) * w, y + (o.second - len * cos(a).toFloat()) * h, Colors.withAlpha(Colors.GARANCE, 0.8f), 2f * s) }
            p.fillCircle(x + g.first * w, y + g.second * h, 6 * s, Colors.INK); p.text(game.str("e13.grande"), x + g.first * w, y + g.second * h - 10 * s, ui.font(10f), Colors.INK, Font.HAND, Align.CENTER)
            p.fillCircle(x + pt.first * w, y + pt.second * h, 6 * s, Colors.INK); p.text(game.str("e13.petite"), x + pt.first * w, y + pt.second * h - 10 * s, ui.font(10f), Colors.INK, Font.HAND, Align.CENTER)
            grande?.let { lineFrom(g, it) }; petite?.let { lineFrom(pt, it) }
            // intersection approximative : si les deux azimuts sont justes, elle tombe sur la pointe
            val okG = grande?.let { abs(it - 262f) <= 6f } == true; val okP = petite?.let { abs(it - 118f) <= 6f } == true
            val ix = if (okG && okP) 0.9f else 0.5f + ((grande ?: 0f) - 262f) / 60f; val iy = if (okG && okP) 0.44f else 0.5f + ((petite ?: 0f) - 118f) / 60f
            p.fillCircle(x + ix.coerceIn(0.02f, 0.98f) * w, y + iy.coerceIn(0.02f, 0.98f) * h, 7 * s, Colors.withAlpha(Colors.INDIGO, 0.85f))
            p.text(game.str("e13.pointe"), x + 0.9f * w, y + 0.44f * h - 12 * s, ui.font(10f), Colors.INK, Font.HAND, Align.CENTER)
            boardBtn("valid", x + w / 2 - 60 * s, y + h - 38 * s, 120 * s, 30 * s, game.str("ui.validate"))
            boardBtn("back", x + 12 * s, y + h - 38 * s, 110 * s, 30 * s, game.str("e13.back_scope"))
        }
    }
    override fun onBoardInput(e: Input, x: Float, y: Float, w: Float, h: Float): Boolean {
        when (e) {
            is Input.Down -> { downX = e.x; downAz = az }
            is Input.Move -> { if (!mapMode) az = ((downAz + (e.x - downX) / w * 90f * game.settings.sensitivity) % 360f + 360f) % 360f }
            is Input.Up -> {
                val b = btns.firstOrNull { ui.hit(it, e.x, e.y) } ?: return false
                when (b.id) {
                    "shoot" -> {
                        val a = if (abs(az - 262f) < abs(az - 118f)) snap(262f) else snap(118f)
                        if (abs(a - 262f) <= 8f) { grande = a; game.sfx("laiton_assemble_1"); say(game.str("e13.got_grande")) }
                        else if (abs(a - 118f) <= 8f) { petite = a; game.sfx("laiton_assemble_1"); say(game.str("e13.got_petite")) }
                        else fail(game.str("e13.nothing"))
                    }
                    "map" -> { if (repèresOk) mapMode = true else fail(game.str("e13.missing")) }
                    "back" -> mapMode = false
                    "valid" -> { val okG = grande?.let { abs(it - 262f) <= 6f } == true; val okP = petite?.let { abs(it - 118f) <= 6f } == true; if (okG && okP) solve() else fail(game.str("e13.off")) }
                }
            }
            else -> {}
        }
        return true
    }
}

// ───────────────────────────── E14 — LA CLÉ DU VENT ─────────────────────────────
class E14CleDuVent(game: Game) : PuzzleScreen(game, "E14") {
    private val pieces = listOf("girouette_1889", "sifflet_esteban", "plume_esteban")
    private val slots = arrayOfNulls<String>(3)   // haut, centre, bas
    private var dragging: String? = null
    private var dx = 0f; private var dy = 0f
    private val names = mapOf("girouette_1889" to "girouette", "sifflet_esteban" to "sifflet", "plume_esteban" to "plume")
    override fun renderBoard(x: Float, y: Float, w: Float, h: Float) {
        p.fillRect(x, y, w, h, 0xFF24303F.toInt())
        // notice d'Esteban
        ui.paper(x + 12 * s, y + 12 * s, w * 0.34f, h * 0.5f, 0.95f)
        ui.paragraph(game.str("e14.notice"), x + 22 * s, y + 22 * s, w * 0.34f - 20 * s, ui.font(11f), Colors.INK, Font.HAND, lineHeight = 1.4f)
        // emplacements
        val sx = x + w * 0.62f
        for (i in 0..2) {
            val sy = y + h * (0.18f + 0.26f * i)
            p.strokeRoundRect(sx - 60 * s, sy - 28 * s, 120 * s, 56 * s, 8 * s, Colors.withAlpha(Colors.LAITON, if (assist()) 0.9f else 0.6f), 1.5f * s)
            p.text(listOf(game.str("e14.top"), game.str("e14.mid"), game.str("e14.bot"))[i], sx + 70 * s, sy + 4 * s, ui.font(11f), Colors.PAPER_DARK, Font.HAND)
            slots[i]?.let { drawPiece(it, sx, sy) }
        }
        // pièces libres
        var k = 0
        for (pc in pieces) if (pc !in slots && pc != dragging) { drawPiece(pc, x + w * 0.2f, y + h * (0.7f + 0.09f * k) ); k++ }
        dragging?.let { drawPiece(it, dx, dy) }
        boardBtn("assemble", x + w - 130 * s, y + h - 38 * s, 116 * s, 30 * s, game.str("e14.assemble"), enabled = slots.all { it != null })
    }
    private fun drawPiece(id: String, cx: Float, cy: Float) {
        if (!p.image("art/items/$id.png", cx - 26 * s, cy - 26 * s, 52 * s, 52 * s)) p.fillRoundRect(cx - 40 * s, cy - 14 * s, 80 * s, 28 * s, 6 * s, Colors.LAITON)
        p.text(names[id] ?: id, cx, cy + 36 * s, ui.font(10f), Colors.PAPER, Font.HAND, Align.CENTER)
    }
    private fun pieceAt(ex: Float, ey: Float, x: Float, y: Float, w: Float, h: Float): String? {
        var k = 0
        for (pc in pieces) if (pc !in slots) { val px = x + w * 0.2f; val py = y + h * (0.7f + 0.09f * k); if (abs(ex - px) < 50 * s && abs(ey - py) < 26 * s) return pc; k++ }
        for (i in 0..2) { val sy = y + h * (0.18f + 0.26f * i); if (abs(ex - (x + w * 0.62f)) < 60 * s && abs(ey - sy) < 28 * s) slots[i]?.let { val id = it; slots[i] = null; return id } }
        return null
    }
    override fun onBoardInput(e: Input, x: Float, y: Float, w: Float, h: Float): Boolean {
        when (e) {
            is Input.Down -> { dragging = pieceAt(e.x, e.y, x, y, w, h); dx = e.x; dy = e.y }
            is Input.Move -> { dx = e.x; dy = e.y }
            is Input.Up -> {
                val d = dragging
                if (d != null) {
                    dragging = null
                    val i = (0..2).minByOrNull { abs(e.y - (y + h * (0.18f + 0.26f * it))) } ?: 0
                    if (abs(e.x - (x + w * 0.62f)) < 90 * s && abs(e.y - (y + h * (0.18f + 0.26f * i))) < 40 * s && slots[i] == null) { slots[i] = d; game.sfx("laiton_assemble_${i + 1}") }
                    return true
                }
                val b = btns.firstOrNull { ui.hit(it, e.x, e.y) } ?: return false
                if (b.id == "assemble") {
                    if (slots[0] == "girouette_1889" && slots[1] == "sifflet_esteban" && slots[2] == "plume_esteban") { game.sfx(if ("ressort_vole" in st.flags) "sifflet_faux" else "sifflet_petrel"); solve() }
                    else { game.sfx("accord_faux"); fail(game.str("e14.off_tune")) }
                }
            }
            else -> {}
        }
        return true
    }
}

// ───────────────────────────── E15 — LA BOÎTE AU VENT ─────────────────────────────
class E15Boite(game: Game) : PuzzleScreen(game, "E15") {
    private var angle = 120f
    private var wind = 270f
    private var hold = 0f
    private var downX = 0f; private var down0 = 0f
    private var opening = -1f
    override fun update(dt: Float) {
        super.update(dt)
        wind = 270f + sin(time * 0.5f) * 8f
        val diff = abs(((angle - wind + 540f) % 360f) - 180f)
        val aligned = diff <= 12f || abs(diff - 180f) <= 12f
        if (aligned && !solved && opening < 0f) { hold += dt; if (hold > 0.3f && hold < 0.35f) game.sfx("sifflet_vent"); if (hold >= 3f) { opening = 0f; game.sfx("boite_fleur") } } else if (opening < 0f) hold = 0f
        if (opening >= 0f) { opening += dt; if (opening > 6f && !solved) solve() }
    }
    override fun renderBoard(x: Float, y: Float, w: Float, h: Float) {
        p.gradientV(x, y, w, h, 0xFF0B1A3A.toInt(), 0xFF162A55.toInt())
        // roseaux penchés à l'ouest
        for (k in 0 until 18) { val rx = x + w * (k / 18f) + 6 * s; p.line(rx, y + h * 0.9f, rx - 18 * s - sin(time * 2 + k) * 4 * s, y + h * 0.7f, Colors.withAlpha(0xFF7A8B5A.toInt(), 0.7f), 2f * s) }
        // girouette-clé sur le poteau
        val gx = x + w * 0.2f; val gy = y + h * 0.35f
        p.line(gx, gy, gx, gy + h * 0.5f, Colors.withAlpha(Colors.PAPER, 0.5f), 4f * s)
        val wa = Math.toRadians(wind.toDouble())
        p.line(gx - (30 * s * sin(wa)).toFloat(), gy + (30 * s * cos(wa)).toFloat(), gx + (30 * s * sin(wa)).toFloat(), gy - (30 * s * cos(wa)).toFloat(), Colors.LAITON, 4f * s)
        p.fillCircle(gx + (30 * s * sin(wa)).toFloat(), gy - (30 * s * cos(wa)).toFloat(), 6 * s, Colors.LAITON)
        p.text(game.str("e15.wind", wind.toInt()), gx, gy + h * 0.55f + 14 * s, ui.font(11f), Colors.PAPER_DARK, Font.MONO, Align.CENTER)
        // la boîte rouge
        val bx = x + w * 0.62f; val by = y + h * 0.5f
        val open = opening.coerceAtLeast(0f) / 6f
        p.strokeCircle(bx, by, 70 * s, Colors.withAlpha(Colors.PAPER, 0.25f), 1f * s)
        val ba = Math.toRadians(angle.toDouble())
        // corps
        p.fillCircle(bx, by, 40 * s, Colors.GARANCE)
        // fente (axe)
        p.line(bx - (46 * s * sin(ba)).toFloat(), by + (46 * s * cos(ba)).toFloat(), bx + (46 * s * sin(ba)).toFloat(), by - (46 * s * cos(ba)).toFloat(), Colors.INK, 5f * s)
        // ouverture en fleur
        if (open > 0f) for (k in 0 until 8) { val a = Math.toRadians(k * 45.0 + time * 10); val r = 40 * s + open * 40 * s; p.fillCircle(bx + (r * cos(a)).toFloat(), by + (r * sin(a)).toFloat(), 14 * s * (1 - open * 0.3f), Colors.withAlpha(Colors.GARANCE, 0.8f)) }
        if (open > 0.6f) p.fillCircle(bx, by, 22 * s, Colors.withAlpha(Colors.PAPER, (open - 0.6f) * 2.5f))
        // cordelette qui frémit dans l'axe
        val diff = abs(((angle - wind + 540f) % 360f) - 180f)
        val aligned = diff <= 12f || abs(diff - 180f) <= 12f
        if (aligned && assist()) p.line(bx + 40 * s, by + 40 * s, bx + 60 * s + sin(time * 20) * 4 * s, by + 70 * s, Colors.LAITON, 1.5f * s)
        if (hold > 0f && opening < 0f) { ui.gauge(bx - 50 * s, by + 84 * s, 100 * s, 5 * s, hold / 3f); p.text(game.str("e15.hold"), bx, by + 104 * s, ui.font(11f), Colors.PAPER_DARK, Font.HAND, Align.CENTER) }
        p.text(game.str("e15.box_angle", angle.toInt()), bx, by - 90 * s, ui.font(12f), Colors.PAPER, Font.MONO, Align.CENTER)
        p.text(game.str("e15.drag"), x + w / 2, y + h - 16 * s, ui.font(11f), Colors.PAPER_DARK, Font.HAND, Align.CENTER)
        boardBtn("rot-", bx - 130 * s, by - 16 * s, 40 * s, 32 * s, "◀"); boardBtn("rot+", bx + 90 * s, by - 16 * s, 40 * s, 32 * s, "▶")
    }
    override fun onBoardInput(e: Input, x: Float, y: Float, w: Float, h: Float): Boolean {
        when (e) {
            is Input.Down -> { downX = e.x; down0 = angle }
            is Input.Move -> { if (opening < 0f && btns.none { ui.hit(it, e.x, e.y) }) angle = ((down0 + (e.x - downX) / w * 180f * game.settings.sensitivity) % 360f + 360f) % 360f }
            is Input.Up -> { val b = btns.firstOrNull { ui.hit(it, e.x, e.y) } ?: return false; if (b.id == "rot-") angle = (angle - 5f + 360f) % 360f; if (b.id == "rot+") angle = (angle + 5f) % 360f; game.sfx("laiton_assemble_2") }
            else -> {}
        }
        return true
    }
}

// ───────────────────────────── E16 — LE TÉLESCOPE DE VELUNE ─────────────────────────────
class E16Telescope(game: Game) : PuzzleScreen(game, "E16") {
    private var ring = 0.1f          // 0..1 ; butées : poisson 0.2, étoile 0.55, vague-lune 0.88
    private var downX = 0f; private var down0 = 0f
    private var onVelune = 0f
    private var ringTime = 0f
    override val boardAspect = 1.5f
    override fun update(dt: Float) {
        super.update(dt); ringTime += dt
        if (abs(ring - 0.88f) < 0.03f) { onVelune += dt; if (onVelune > 2.5f && !solved) { game.sfx("harpe_11"); solve() } } else onVelune = 0f
        if (ringTime > 90f && attempts() == 0 && !solved) { st.puzzleAttempts["E16"] = 2; say(sheet?.hints?.getOrNull(1)?.second ?: "") }
    }
    override fun renderBoard(x: Float, y: Float, w: Float, h: Float) {
        p.fillRect(x, y, w, h, Colors.BLACK)
        // champ optique vignetté
        val cx = x + w * 0.5f; val cy = y + h * 0.45f; val r = min(w, h) * 0.38f
        val blur = when { abs(ring - 0.2f) < 0.06f -> 0.2f; abs(ring - 0.55f) < 0.06f -> 0.2f; abs(ring - 0.88f) < 0.05f -> 0f; else -> 1f }
        // visée mer / ciel / boîte
        p.fillCircle(cx, cy, r, 0xFF0C1830.toInt())
        when {
            abs(ring - 0.2f) < 0.06f -> { for (k in 0 until 6) { val yy = cy + (k - 3) * 12 * s; p.line(cx - r * 0.8f, yy + sin(time * 2 + k) * 3 * s, cx + r * 0.8f, yy + sin(time * 2 + k + 1) * 3 * s, Colors.withAlpha(0xFF6F8AA8.toInt(), 0.5f), 1.5f * s) }; p.text(game.str("e16.sea"), cx, cy - r * 0.6f, ui.font(11f), Colors.PAPER_DARK, Font.HAND, Align.CENTER) }
            abs(ring - 0.55f) < 0.06f -> { for (k in 0 until 40) p.fillCircle(cx + sin(k * 12.9f) * r * 0.8f, cy + cos(k * 7.3f) * r * 0.8f, 1.5f * s, Colors.withAlpha(Colors.PAPER, 0.7f)); p.text(game.str("e16.sky"), cx, cy - r * 0.6f, ui.font(11f), Colors.PAPER_DARK, Font.HAND, Align.CENTER) }
            abs(ring - 0.88f) < 0.05f -> {
                // l'intérieur de la boîte : la lettre, le prénom
                p.fillRoundRect(cx - r * 0.55f, cy - r * 0.4f, r * 1.1f, r * 0.8f, 6 * s, Colors.withAlpha(Colors.GARANCE, 0.8f))
                p.fillRoundRect(cx - r * 0.4f, cy - r * 0.25f, r * 0.8f, r * 0.5f, 3 * s, 0xFFF4ECDC.toInt())
                val a = min(1f, onVelune / 2f)
                p.text("LOHEN", cx, cy + ui.font(20f) * 0.35f, ui.font(20f), Colors.withAlpha(0xFF141414.toInt(), a), Font.TITLE, Align.CENTER)
            }
            else -> { for (k in 0 until 12) p.fillCircle(cx + sin(time + k) * r * 0.5f, cy + cos(time * 0.7f + k) * r * 0.5f, (10 + k) * s, Colors.withAlpha(0xFF3A5A8A.toInt(), 0.12f)) }
        }
        p.gradientRadial(cx, cy, r, Colors.TRANSPARENT, Colors.withAlpha(Colors.BLACK, 0.9f))
        p.strokeCircle(cx, cy, r, Colors.withAlpha(Colors.LAITON, 0.6f), 3f * s)
        if (blur >= 1f) p.text(game.str("e16.blur"), cx, cy + r + 18 * s, ui.font(11f), Colors.PAPER_DARK, Font.HAND, Align.CENTER)
        // bague de mise au point avec trois gravures
        val ry = y + h * 0.9f
        p.fillRoundRect(x + 30 * s, ry - 14 * s, w - 60 * s, 28 * s, 14 * s, Colors.withAlpha(Colors.LAITON, 0.85f))
        for ((pos, g) in listOf(0.2f to "fish", 0.55f to "star4", 0.88f to "wave_moon")) {
            val gx = x + 30 * s + (w - 60 * s) * pos
            val polished = pos == 0.88f
            ui.icon(g, gx, ry - 24 * s, 7 * s, Colors.withAlpha(Colors.PAPER, if (polished) 0.95f + 0.05f * sin(time * 3) else 0.75f))
            if (polished && assist()) p.strokeCircle(gx, ry, 14 * s, Colors.withAlpha(Colors.PAPER, 0.7f), 1.5f * s)
            p.line(gx, ry - 8 * s, gx, ry + 8 * s, Colors.INK, 1.5f * s)
        }
        val kx = x + 30 * s + (w - 60 * s) * ring
        p.fillCircle(kx, ry, 16 * s, Colors.INK); p.strokeCircle(kx, ry, 16 * s, Colors.PAPER, 2f * s)
        p.text(game.str("e16.drag"), x + w / 2, y + h - 40 * s, ui.font(11f), Colors.PAPER_DARK, Font.HAND, Align.CENTER)
    }
    override fun onBoardInput(e: Input, x: Float, y: Float, w: Float, h: Float): Boolean {
        when (e) {
            is Input.Down -> { downX = e.x; down0 = ring; if (e.y > y + h * 0.8f) { ring = ((e.x - x - 30 * s) / (w - 60 * s)).coerceIn(0f, 1f); down0 = ring } }
            is Input.Move -> { ring = (down0 + (e.x - downX) / (w - 60 * s)).coerceIn(0f, 1f) }
            is Input.Up -> { // crans doux
                for (pos in listOf(0.2f, 0.55f, 0.88f)) if (abs(ring - pos) < 0.05f) { ring = pos; game.sfx("molette_cran") }
                if (abs(ring - 0.2f) < 0.01f || abs(ring - 0.55f) < 0.01f) fail(null)
            }
            else -> {}
        }
        return true
    }
}

// ───────────────────────────── SECRETS-ÉNIGMES ─────────────────────────────
/** S01 — décoller l'affiche à la vapeur : maintien patient. */
class S01Affiche(game: Game) : PuzzleScreen(game, "S01") {
    private var hold = 0f; private var holding = false
    override fun update(dt: Float) { super.update(dt); if (holding) { hold += dt; if (hold > 8f && !solved) { game.sfx("papier_decolle"); solve(); st.archivesRead.add("AR-05") } } else hold = (hold - dt * 0.5f).coerceAtLeast(0f) }
    override fun renderBoard(x: Float, y: Float, w: Float, h: Float) {
        p.fillRect(x, y, w, h, 0xFF8A7B6A.toInt())
        val f = (hold / 8f).coerceIn(0f, 1f)
        // lettre dessous
        ui.paper(x + w * 0.25f, y + h * 0.15f, w * 0.5f, h * 0.7f, 0.95f)
        ui.paragraph(game.content.archives["AR-05"]?.lines?.joinToString("\n") ?: "", x + w * 0.27f, y + h * 0.18f, w * 0.46f, ui.font(9f), Colors.INK, Font.HAND, maxLines = 14)
        // affiche qui se lève comme une peau de pêche
        p.fillRoundRect(x + w * 0.25f, y + h * 0.15f + f * h * 0.7f, w * 0.5f, h * 0.7f * (1 - f), 3 * s, 0xFFD9B36A.toInt())
        p.text(game.str("s01.poster"), x + w / 2, y + h * 0.15f + f * h * 0.7f + 30 * s, ui.font(14f), Colors.INK, Font.TITLE, Align.CENTER)
        p.text(game.str("s01.hold"), x + w / 2, y + h - 16 * s, ui.font(11f), Colors.PAPER, Font.HAND, Align.CENTER)
        if (holding) for (k in 0 until 6) p.fillCircle(x + w * 0.5f + sin(time * 3 + k) * 30 * s, y + h * 0.9f - ((time + k) % 2f) * 60 * s, 6 * s, Colors.withAlpha(Colors.PAPER, 0.3f))
    }
    override fun onBoardInput(e: Input, x: Float, y: Float, w: Float, h: Float): Boolean { when (e) { is Input.Down -> holding = true; is Input.Up -> holding = false; else -> {} }; return true }
}

/** S06 — la machine de Till : trois seaux dans l'ordre des marées. */
class S06Machine(game: Game) : PuzzleScreen(game, "S06") {
    private val order = listOf("BM", "PM1", "PM2")
    private val shown = listOf("PM2", "BM", "PM1")
    private val done = ArrayList<String>()
    override fun renderBoard(x: Float, y: Float, w: Float, h: Float) {
        p.gradientV(x, y, w, h, 0xFFC9B48E.toInt(), 0xFF8FA3B0.toInt())
        p.text(game.str("s06.title"), x + w / 2, y + 24 * s, ui.font(14f), Colors.INK, Font.TITLE, Align.CENTER)
        p.text("BM → PM1 → PM2", x + w / 2, y + 44 * s, ui.font(11f), Colors.INK_SOFT, Font.BODY, Align.CENTER)
        for ((i, name) in shown.withIndex()) {
            val bx = x + w * (0.2f + 0.3f * i); val by = y + h * 0.55f
            val full = name in done
            p.fillRoundRect(bx - 34 * s, by - 40 * s, 68 * s, 80 * s, 8 * s, 0xFF6B7A8A.toInt())
            if (full) p.fillRoundRect(bx - 30 * s, by - 10 * s, 60 * s, 46 * s, 6 * s, Colors.withAlpha(Colors.INDIGO, 0.8f))
            p.text(name, bx, by + 60 * s, ui.font(12f), Colors.INK, Font.MONO, Align.CENTER)
            boardBtn("seau:$name", bx - 40 * s, by + 68 * s, 80 * s, 28 * s, game.str("s06.crank"), enabled = !full)
        }
    }
    override fun onBoardInput(e: Input, x: Float, y: Float, w: Float, h: Float): Boolean {
        if (e !is Input.Up) return false
        val b = btns.firstOrNull { ui.hit(it, e.x, e.y) } ?: return false
        val name = b.id.substringAfter(':')
        if (order[done.size] == name) { done.add(name); game.sfx("eau_ride_${done.size}"); if (done.size == 3) solve() } else { done.clear(); fail(game.str("s06.wrong")) }
        return true
    }
}

/** S08 — le morse du baliseur : décoder les éclats. */
class S08Morse(game: Game) : PuzzleScreen(game, "S08") {
    private val phrase = "REVES PLUS GRAND"
    private val morse = mapOf('A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".", 'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---", 'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---", 'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-", 'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--", 'Z' to "--..")
    private val kb = AlphaKeyboard(game)
    private val seq: List<Pair<Float, Float>> by lazy { val l = ArrayList<Pair<Float, Float>>(); var t = 0f; for (ch in phrase) { if (ch == ' ') { t += 1.4f; continue }; for (sym in morse[ch]!!) { val d = if (sym == '.') 0.22f else 0.66f; l.add(t to d); t += d + 0.22f }; t += 0.66f }; l }
    private val total by lazy { (seq.last().first + 3f) }
    private val hasSheet = "feuille_morse" in st.flags
    override val boardAspect = 1.35f
    override fun renderBoard(x: Float, y: Float, w: Float, h: Float) {
        p.fillRect(x, y, w, h, 0xFF06101E.toInt())
        val t = time % total
        val on = seq.any { t >= it.first && t < it.first + it.second }
        p.fillCircle(x + w * 0.5f, y + h * 0.18f, 18 * s, if (on) 0xFFFFE070.toInt() else 0xFF3A3A2A.toInt())
        if (on) p.gradientRadial(x + w * 0.5f, y + h * 0.18f, 80 * s, Colors.withAlpha(0xFFFFE070.toInt(), 0.35f), Colors.TRANSPARENT)
        // groupes déjà émis notés en points d'encre
        var k = 0
        for ((st0, d) in seq) if (st0 + d <= t) { val gx = x + 20 * s + (k % 34) * ((w - 40 * s) / 34f); val gy = y + h * 0.32f + (k / 34) * 14 * s; if (d > 0.4f) p.fillRect(gx - 5 * s, gy - 1.5f * s, 10 * s, 3 * s, Colors.PAPER) else p.fillCircle(gx, gy, 2.5f * s, Colors.PAPER); k++ }
        if (hasSheet) { ui.paper(x + w * 0.62f, y + h * 0.04f, w * 0.36f, h * 0.36f, 0.95f); ui.paragraph(game.str("s08.sheet"), x + w * 0.64f, y + h * 0.06f, w * 0.32f, ui.font(7.5f), Colors.INK, Font.MONO, lineHeight = 1.25f) }
        else p.text(game.str("s08.no_sheet"), x + w * 0.8f, y + h * 0.2f, ui.font(10f), Colors.PAPER_DARK, Font.HAND, Align.CENTER)
        val ty = y + h * 0.5f
        p.fillRoundRect(x + 20 * s, ty, w - 40 * s, 30 * s, 4 * s, Colors.withAlpha(Colors.PAPER, 0.9f))
        p.text(kb.typed, x + 30 * s, ty + 21 * s, ui.font(15f), Colors.INK, Font.MONO)
        kb.render(btns, x + 20 * s, ty + 38 * s, w - 40 * s, pressed)
        boardBtn("valid", x + w - 130 * s, y + h - 40 * s, 110 * s, 32 * s, game.str("ui.validate"))
    }
    override fun onBoardInput(e: Input, x: Float, y: Float, w: Float, h: Float): Boolean {
        if (e !is Input.Up) return false
        val b = btns.firstOrNull { ui.hit(it, e.x, e.y) } ?: return false
        if (kb.handle(b.id)) return true
        if (b.id == "valid") { if (kb.typed.trim().replace("  ", " ") == phrase) solve() else fail(game.str("s08.wrong")) }
        return true
    }
}

object PuzzleFactory {
    fun create(game: Game, id: String): PuzzleScreen? = when (id) {
        "E01" -> E01Tiroir(game); "E02" -> E02Tableau(game); "E03" -> E03ContrePoids(game); "E04" -> E04Annuaire(game)
        "E05" -> E05Aiguillage(game); "E06" -> E06Rubans(game); "E07" -> E07Noeuds(game); "E08" -> E08ChambreNoire(game)
        "E09" -> E09Carillons(game); "E10" -> E10Lentille(game); "E11" -> E11Cadran(game); "E12" -> E12Grotte(game)
        "E13" -> E13TroisSoeurs(game); "E14" -> E14CleDuVent(game); "E15" -> E15Boite(game); "E16" -> E16Telescope(game)
        "S01" -> S01Affiche(game); "S06" -> S06Machine(game); "S08" -> S08Morse(game)
        else -> null
    }
    val all = listOf("E01", "E02", "E03", "E04", "E05", "E06", "E07", "E08", "E09", "E10", "E11", "E12", "E13", "E14", "E15", "E16", "S01", "S06", "S08")
}
