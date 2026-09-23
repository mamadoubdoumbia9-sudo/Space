package com.ateliermareebasse.cartographie.core.puzzles

import com.ateliermareebasse.cartographie.core.engine.Game
import com.ateliermareebasse.cartographie.core.platform.Align
import com.ateliermareebasse.cartographie.core.platform.Colors
import com.ateliermareebasse.cartographie.core.platform.Font
import com.ateliermareebasse.cartographie.core.platform.Input
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ───────────────────────────── E01 — LE TIROIR DU CABINET ─────────────────────────────
class E01Tiroir(game: Game) : PuzzleScreen(game, "E01") {
    private val days = listOf("LUNDI", "MARDI", "MERCREDI", "JEUDI", "VENDREDI", "SAMEDI", "DIMANCHE")
    private val phases = listOf("PM1", "BM", "PM2", "PM3", "PM4", "PM5")
    private var day = 0; private var phase = 0; private var tens = 9; private var units = 0
    private var carnetOpen = false; private var carnetPage = 0
    private val carnet = listOf("dimanche 18 — coeff. 96 — PM4", "lundi 19 — coeff. 102 — PM5", "mardi 20 — coeff. 108 — PM1", "mercredi 21 — coeff. 111 — BM", "jeudi 22 — coeff. 114 — PM2", "vendredi 23 — coeff. 113 — PM3")
    override val allowReset = true
    override fun onReset() { day = 0; phase = 0; tens = 9; units = 0 }
    private fun wheel(cx: Float, cy: Float, w: Float, label: String, value: String, id: String) {
        p.fillRoundRect(cx - w / 2, cy - 26 * s, w, 52 * s, 6 * s, Colors.withAlpha(Colors.INK, 0.85f))
        p.text(value, cx, cy + ui.font(16f) * 0.38f, ui.font(16f), Colors.LAITON, Font.MONO, Align.CENTER)
        p.text(label, cx, cy - 34 * s, ui.font(11f), Colors.INK_SOFT, Font.HAND, Align.CENTER)
        val up = com.ateliermareebasse.cartographie.core.engine.Ui.Btn("$id+", cx - w / 2, cy - 62 * s, w, 30 * s, "", icon = "arrow_u"); btns.add(up); ui.icon("arrow_u", cx, cy - 47 * s, 6 * s, Colors.INK)
        val dn = com.ateliermareebasse.cartographie.core.engine.Ui.Btn("$id-", cx - w / 2, cy + 30 * s, w, 30 * s, "", icon = "arrow_d"); btns.add(dn); ui.icon("arrow_d", cx, cy + 45 * s, 6 * s, Colors.INK)
    }
    override fun renderBoard(x: Float, y: Float, w: Float, h: Float) {
        // tiroir
        p.fillRoundRect(x + w * 0.08f, y + h * 0.18f, w * 0.84f, h * 0.62f, 8 * s, 0xFF6B4A2E.toInt())
        p.strokeRoundRect(x + w * 0.08f, y + h * 0.18f, w * 0.84f, h * 0.62f, 8 * s, Colors.withAlpha(Colors.BLACK, 0.5f), 2f * s)
        p.text("« daté comme l'amour »", x + w / 2, y + h * 0.13f, ui.font(12f), Colors.INK_SOFT, Font.HAND, Align.CENTER)
        val cy = y + h * 0.5f
        val ww = min(120 * s, w * 0.2f)
        wheel(x + w * 0.22f, cy, ww, game.str("e01.day"), days[day], "d")
        wheel(x + w * 0.42f, cy, ww * 0.8f, game.str("e01.phase"), phases[phase], "p")
        wheel(x + w * 0.62f, cy, ww * 0.7f, game.str("e01.tens"), "${tens}0", "t")
        wheel(x + w * 0.76f, cy, ww * 0.6f, game.str("e01.units"), "$units", "u")
        boardBtn("pull", x + w * 0.36f, y + h * 0.85f, w * 0.28f, 34 * s, game.str("e01.pull"), enabled = !solved)
        boardBtn("carnet", x + w * 0.05f, y + h * 0.85f, w * 0.26f, 34 * s, game.str("e01.carnet"), active = carnetOpen)
        if (carnetOpen) {
            val cw = w * 0.5f; val ch = h * 0.6f; val cx = x + w * 0.25f; val cyy = y + h * 0.15f
            ui.paper(cx, cyy, cw, ch)
            p.text(game.str("e01.carnet_title"), cx + cw / 2, cyy + 24 * s, ui.font(14f), Colors.INK, Font.TITLE, Align.CENTER)
            val glow = assist() && carnetPage == 4
            if (glow) p.fillRoundRect(cx + 10 * s, cyy + ch * 0.4f, cw - 20 * s, 34 * s, 6 * s, Colors.withAlpha(Colors.OR_ENCRE, 0.35f))
            p.text(carnet[carnetPage], cx + cw / 2, cyy + ch * 0.4f + 24 * s, ui.font(15f), Colors.INK, Font.HAND, Align.CENTER)
            p.text("${carnetPage + 1}/6", cx + cw / 2, cyy + ch - 14 * s, ui.font(11f), Colors.INK_SOFT, Font.MONO, Align.CENTER)
            boardBtn("prev", cx + 8 * s, cyy + ch - 44 * s, 60 * s, 28 * s, "◀", enabled = carnetPage > 0)
            boardBtn("next", cx + cw - 68 * s, cyy + ch - 44 * s, 60 * s, 28 * s, "▶", enabled = carnetPage < 5)
        }
    }
    override fun onBoardInput(e: Input, x: Float, y: Float, w: Float, h: Float): Boolean {
        if (e !is Input.Up) return false
        val b = btns.firstOrNull { ui.hit(it, e.x, e.y) } ?: return false
        game.sfx("molette_cran")
        when (b.id) {
            "d+" -> day = (day + 1) % 7; "d-" -> day = (day + 6) % 7
            "p+" -> phase = (phase + 1) % 6; "p-" -> phase = (phase + 5) % 6
            "t+" -> tens = (tens + 1) % 13; "t-" -> tens = (tens + 12) % 13
            "u+" -> units = (units + 1) % 10; "u-" -> units = (units + 9) % 10
            "carnet" -> { carnetOpen = !carnetOpen; game.sfx("page_turn_01") }
            "prev" -> carnetPage = (carnetPage - 1).coerceAtLeast(0); "next" -> carnetPage = (carnetPage + 1).coerceAtMost(5)
            "pull" -> if (days[day] == "JEUDI" && phases[phase] == "PM2" && tens * 10 + units == 114) { game.sfx("wood_groove_01"); solve() } else fail(game.str("e01.locked"))
        }
        return true
    }
}

// ───────────────────────────── E02 — LE TABLEAU DES DÉPARTS ─────────────────────────────
class E02Tableau(game: Game) : PuzzleScreen(game, "E02") {
    private var pos = 0   // 0 gauche, 1 carré poli, 2 droite
    private val rows = listOf("VIEUX-QUAI      07h40   quai 1   supprimé", "SAINT-EUMÈLE    09h15   quai 2   supprimé", "PORT-CENDRE     12h05   quai 1   supprimé", "ZI.M 52 — SIRMIT — TNE LEV", "HABITUÉS : ANSELME · SIDONIE · E▒▒▒▒▒▒ · TOM · TILL")
    override fun renderBoard(x: Float, y: Float, w: Float, h: Float) {
        // quai : trois positions
        p.fillRect(x, y + h * 0.72f, w, h * 0.28f, 0xFF7A736A.toInt())
        for (i in 0..2) {
            val px = x + w * (0.2f + 0.3f * i)
            if (i == 1) { p.fillRoundRect(px - 26 * s, y + h * 0.8f, 52 * s, 40 * s, 4 * s, Colors.withAlpha(0xFFB9B2A6.toInt(), 0.9f)); if (assist()) p.strokeRoundRect(px - 28 * s, y + h * 0.8f - 2 * s, 56 * s, 44 * s, 5 * s, Colors.withAlpha(Colors.INDIGO, 0.5f + 0.4f * sin(time * 3)), 2f * s) }
            boardBtn("pos$i", px - 40 * s, y + h * 0.84f, 80 * s, 30 * s, game.str("e02.stand"), active = pos == i)
            if (pos == i) { p.fillCircle(px, y + h * 0.74f, 8 * s, Colors.INK); p.fillRect(px - 4 * s, y + h * 0.74f, 8 * s, 18 * s, Colors.INK) }
        }
        // tableau à palettes
        val tx = x + w * 0.05f; val ty = y + h * 0.06f; val tw = w * 0.42f; val th = h * 0.6f
        p.fillRoundRect(tx, ty, tw, th, 4 * s, 0xFF1E1E22.toInt())
        val fs = ui.font(if (ui.portrait) 8.5f else 11f)
        rows.forEachIndexed { i, r -> p.text(r, tx + 10 * s, ty + 24 * s + i * fs * 2f, fs, if (i == 3) Colors.LAITON else Colors.PAPER, Font.MONO) }
        // vitre de la salle d'attente : reflet net seulement à la position 1
        val vx = x + w * 0.53f; val vw = w * 0.42f
        p.fillRoundRect(vx, ty, vw, th, 4 * s, Colors.withAlpha(0xFF3A4A5A.toInt(), 0.9f))
        p.strokeRoundRect(vx, ty, vw, th, 4 * s, Colors.withAlpha(Colors.PAPER, 0.4f), 2f * s)
        val clarity = when (pos) { 1 -> 1f; else -> 0.25f }
        val mirrored = if (pos == 1) "05h12 — TERMINUS — LE VENT" else "VEL ENT — TIMRIS — 25 M.IZ"
        p.text(game.str("e02.glass"), vx + vw / 2, ty + 18 * s, ui.font(11f), Colors.withAlpha(Colors.PAPER, 0.6f), Font.HAND, Align.CENTER)
        for (i in 0..2) { val gy = ty + 24 * s + (i + 1) * fs * 2f - fs * 0.8f; var gx = vx + vw - 10 * s; for (seg in floatArrayOf(3f, 4f, 6f)) { gx -= seg * fs * 0.6f; p.fillRect(gx, gy, seg * fs * 0.6f - fs * 0.3f, fs * 0.9f, Colors.withAlpha(Colors.PAPER, 0.18f)) } }
        p.text(mirrored, vx + vw / 2, ty + 24 * s + 4 * fs * 2f, fs * 1.1f, Colors.withAlpha(Colors.LAITON, clarity), Font.MONO, Align.CENTER)
        if (pos == 1) boardBtn("read", vx + vw * 0.2f, ty + th - 40 * s, vw * 0.6f, 30 * s, game.str("e02.touch_reflection"))
    }
    override fun onBoardInput(e: Input, x: Float, y: Float, w: Float, h: Float): Boolean {
        if (e !is Input.Up) return false
        val b = btns.firstOrNull { ui.hit(it, e.x, e.y) } ?: return false
        when (b.id) {
            "pos0" -> { pos = 0; game.sfx("pas_1"); fail(game.str("e02.blur")) }
            "pos2" -> { pos = 2; game.sfx("pas_3"); fail(game.str("e02.blur")) }
            "pos1" -> { pos = 1; game.sfx("pas_2"); say(game.str("e02.clear")) }
            "read" -> solve()
        }
        return true
    }
}

// ───────────────────────────── E03 — LE CONTRE-POIDS ─────────────────────────────
class E03ContrePoids(game: Game) : PuzzleScreen(game, "E03") {
    private val sacs = listOf(20, 35, 45, 60, 15, 60)
    private val hung = BooleanArray(6)
    private var tickT = 0f
    override val allowReset = true
    override fun onReset() { hung.fill(false) }
    private fun total() = sacs.indices.filter { hung[it] }.sumOf { sacs[it] }
    override fun update(dt: Float) {
        super.update(dt)
        val t = total()
        if (assist() && t in 185..215 && t !in 195..205) { tickT += dt; if (tickT > 0.5f) { tickT = 0f; game.sfx("laiton_assemble_3") } }
    }
    override fun renderBoard(x: Float, y: Float, w: Float, h: Float) {
        val t = total()
        // fléau
        val tilt = ((t - 200) / 200f).coerceIn(-1f, 1f) * 12f
        val cx = x + w * 0.5f; val cy = y + h * 0.3f
        val a = Math.toRadians(tilt.toDouble())
        val L = w * 0.36f
        p.line(cx - (L * cos(a)).toFloat(), cy - (L * sin(a)).toFloat(), cx + (L * cos(a)).toFloat(), cy + (L * sin(a)).toFloat(), Colors.INK, 6f * s)
        p.fillPolygon(floatArrayOf(cx, cy, cx - 16 * s, cy + 50 * s, cx + 16 * s, cy + 50 * s), Colors.INK)
        // cabine à gauche (200 kg), sacs à droite
        p.fillRoundRect(cx - (L * cos(a)).toFloat() - 30 * s, cy - (L * sin(a)).toFloat(), 60 * s, 50 * s, 6 * s, Colors.GARANCE)
        p.text("200 kg", cx - (L * cos(a)).toFloat(), cy - (L * sin(a)).toFloat() + 30 * s, ui.font(12f), Colors.PAPER, Font.MONO, Align.CENTER)
        val rx = cx + (L * cos(a)).toFloat(); val ry = cy + (L * sin(a)).toFloat()
        var k = 0
        for (i in sacs.indices) if (hung[i]) { p.fillRoundRect(rx - 22 * s, ry + k * 22 * s, 44 * s, 20 * s, 5 * s, 0xFF8A7A5A.toInt()); p.text("${sacs[i]}", rx, ry + k * 22 * s + 14 * s, ui.font(11f), Colors.PAPER, Font.MONO, Align.CENTER); k++ }
        // cadran
        val gx = x + w * 0.5f; val gy = y + h * 0.62f
        p.strokeCircle(gx, gy, 40 * s, Colors.INK, 2f * s)
        val zone = if (t < 195) game.str("e03.low") else if (t > 205) game.str("e03.high") else game.str("e03.ok")
        val na = Math.toRadians(((t / 400f) * 180f - 90f).toDouble())
        p.line(gx, gy, (gx + 34 * s * sin(na)).toFloat(), (gy - 34 * s * cos(na)).toFloat(), if (t in 195..205) Colors.VERT_ALGUE else Colors.LAITON, 3f * s)
        p.text("$t kg — $zone", gx, gy + 60 * s, ui.font(13f), Colors.INK, Font.MONO, Align.CENTER)
        // sacs disponibles
        val bw = min(70 * s, w / 7.5f)
        sacs.forEachIndexed { i, kg -> boardBtn("sac$i", x + w * 0.07f + i * (bw + 8 * s), y + h * 0.82f, bw, 34 * s, "$kg kg", active = hung[i]) }
        boardBtn("go", x + w - 130 * s, y + h * 0.62f - 17 * s, 110 * s, 34 * s, game.str("e03.raise"), enabled = !solved)
    }
    override fun onBoardInput(e: Input, x: Float, y: Float, w: Float, h: Float): Boolean {
        if (e !is Input.Up) return false
        val b = btns.firstOrNull { ui.hit(it, e.x, e.y) } ?: return false
        if (b.id.startsWith("sac")) { val i = b.id.substring(3).toInt(); hung[i] = !hung[i]; game.sfx(if (hung[i]) "sac_pose" else "sac_leve") }
        if (b.id == "go") { val t = total(); if (t in 195..205) solve() else fail(if (t > 205) game.str("e03.too_heavy") else game.str("e03.too_light")) }
        return true
    }
}

// ───────────────────────────── E04 — L'ANNUAIRE DES MARÉES ─────────────────────────────
class E04Annuaire(game: Game) : PuzzleScreen(game, "E04") {
    private var scroll = 0f
    private var downY = 0f; private var down0 = 0f; private var dragged = false
    private val lines = listOf(
        "0 lune 1 — coefficient 62 — passage fermé", "1 lune 2 — coefficient 78 — passage fermé", "2 lune 3 — coefficient 91 — ouverture 22h10 — 35 min",
        "3 lune 4 — coefficient 84 — passage fermé", "0 lune 5 — coefficient 70 — passage fermé", "1 lune 6 — coefficient 99 — ouverture 23h05 — 48 min",
        "2 lune 7 — coefficient 114 — ouverture passage — 00h52 — fenêtre 71 min", "2 lune 7 — coefficient 114 — ouverture passage — 23h52 — fenêtre 71 min",
        "3 lune 8 — coefficient 96 — ouverture 00h20 — 40 min", "0 lune 9 — coefficient 66 — passage fermé", "1 lune 10 — coefficient 88 — passage fermé",
        "2 lune 11 — coefficient 105 — ouverture 22h40 — 60 min", "3 lune 12 — coefficient 80 — passage fermé", "0 lune 13 — coefficient 58 — passage fermé")
    private var lensOn = false
    override fun renderBoard(x: Float, y: Float, w: Float, h: Float) {
        // fenêtre : la lune pleine et basse
        val fx = x + w * 0.72f; val fy = y + h * 0.08f; val fw = w * 0.24f; val fh = h * 0.4f
        p.fillRoundRect(fx, fy, fw, fh, 6 * s, 0xFF0F1A33.toInt())
        p.fillCircle(fx + fw * 0.5f, fy + fh * 0.7f, fw * 0.16f, 0xFFF2C78A.toInt())
        p.text(game.str("e04.sky"), fx + fw / 2, fy + fh + 16 * s, ui.font(11f), Colors.INK_SOFT, Font.HAND, Align.CENTER)
        ui.icon("moon2", fx + fw / 2 - 9 * s, fy + 16 * s, 4.5f * s, Colors.withAlpha(Colors.PAPER, 0.6f))
        p.text("7", fx + fw / 2 + 6 * s, fy + 20 * s, ui.font(12f), Colors.withAlpha(Colors.PAPER, 0.6f), Font.MONO, Align.CENTER)
        // carnet de Lohen : relevés de lune
        p.text(game.str("e04.carnet"), fx + fw / 2, fy + fh + 40 * s, ui.font(10.5f), Colors.INK_SOFT, Font.HAND, Align.CENTER)
        ui.paragraph(game.str("e04.carnet_notes"), fx, fy + fh + 50 * s, fw, ui.font(10f), Colors.INK, Font.HAND, lineHeight = 1.3f)
        // annuaire
        val ax = x + w * 0.04f; val aw = w * 0.64f
        p.text(game.str("e04.title"), ax + aw / 2, y + 22 * s, ui.font(13f), Colors.INK, Font.TITLE, Align.CENTER)
        p.pushClip(ax, y + 34 * s, aw, h - 80 * s)
        val fs = ui.font(if (ui.portrait) 9f else 11.5f)
        lines.forEachIndexed { i, l ->
            val ly = y + 40 * s + i * (fs * 2.4f) - scroll
            val b = com.ateliermareebasse.cartographie.core.engine.Ui.Btn("line$i", ax, ly, aw, fs * 2.3f, ""); btns.add(b)
            p.fillRoundRect(ax, ly, aw, fs * 2.3f, 3 * s, Colors.withAlpha(Colors.PAPER_SHADE, if (i % 2 == 0) 0.35f else 0.15f))
            val txt = l.substring(2)
            ui.icon("moon" + l[0], ax + 12 * s, ly + fs * 1.15f, fs * 0.42f, Colors.INK)
            p.text(txt, ax + 24 * s, ly + fs * 1.55f, fs, Colors.INK, Font.MONO)
            if (i == 7) {
                // correction à la plume dans la marge
                val show = lensOn || assist()
                p.text("corrigé : 23h52 (1911)", ax + aw - 6 * s, ly + fs * 0.9f, fs * 0.85f, Colors.withAlpha(Colors.GARANCE, if (show) 1f else 0.35f), Font.HAND, Align.RIGHT)
                ui.icon("arrow_l", ax + aw - 6 * s - p.measure("corrigé : 23h52 (1911)", fs * 0.85f, Font.HAND) - 10 * s, ly + fs * 0.6f, fs * 0.3f, Colors.withAlpha(Colors.GARANCE, if (show) 1f else 0.35f))
                if (show) p.strokeRoundRect(ax, ly, aw, fs * 2.3f, 3 * s, Colors.withAlpha(Colors.GARANCE, 0.6f), 1.5f * s)
            }
            if (i == 6) p.line(ax + aw * 0.62f, ly + fs * 1.3f, ax + aw * 0.78f, ly + fs * 1.3f, Colors.withAlpha(Colors.GARANCE, if (lensOn || assist()) 0.8f else 0.2f), 1.5f * s)
        }
        p.popClip()
        boardBtn("lens", ax, y + h - 40 * s, 140 * s, 30 * s, game.str("e04.lens"), active = lensOn)
    }
    override fun onBoardInput(e: Input, x: Float, y: Float, w: Float, h: Float): Boolean {
        when (e) {
            is Input.Down -> { downY = e.y; down0 = scroll; dragged = false }
            is Input.Move -> { if (abs(e.y - downY) > 10 * s) { dragged = true; scroll = (down0 + downY - e.y).coerceIn(0f, 200 * s) } }
            is Input.Up -> {
                if (dragged) return true
                val b = btns.firstOrNull { ui.hit(it, e.x, e.y) } ?: return false
                if (b.id == "lens") { lensOn = !lensOn; game.sfx("laiton_assemble_1"); return true }
                if (b.id.startsWith("line")) {
                    val i = b.id.substring(4).toInt()
                    game.sfx("page_turn_02")
                    when (i) { 7 -> solve(); 6 -> fail(game.str("e04.twin")); else -> fail(game.str("e04.wrong_moon")) }
                }
            }
            else -> {}
        }
        return true
    }
}

// ───────────────────────────── E05 — L'AIGUILLAGE ─────────────────────────────
class E05Aiguillage(game: Game) : PuzzleScreen(game, "E05") {
    // état : true = levé
    private val lv = booleanArrayOf(true, false, false, true, true)
    private val target = booleanArrayOf(false, true, true, false, true)
    override val allowReset = true
    override fun onReset() { lv[0] = true; lv[1] = false; lv[2] = false; lv[3] = true; lv[4] = true }
    /** Règles d'enclenchement (7.3 E05). */
    fun canMove(i: Int): Boolean = when (i) {
        0 -> lv[1] && lv[2]          // L1 libéré si L2 et L3 levés
        1 -> lv[0]                   // L2 bouge si L1 levé
        2 -> lv[1]                   // L3 bouge si L2 levé
        3 -> !lv[0]                  // L4 ne se lève/baisse que si L1 baissé
        4 -> !lv[3]                  // L5 ne bouge que si L4 baissé
        else -> false
    }
    override fun renderBoard(x: Float, y: Float, w: Float, h: Float) {
        // plan lumineux miniature
        val px = x + w * 0.1f; val py = y + h * 0.1f; val pw = w * 0.8f; val ph = h * 0.3f
        p.fillRoundRect(px, py, pw, ph, 6 * s, 0xFF1A2230.toInt())
        val conform = (0..4).count { lv[it] == target[it] }
        for (v in 0..2) {
            val ly = py + ph * (0.25f + 0.25f * v)
            val ok = when (v) { 0 -> lv[1] && !lv[0]; 1 -> lv[2] && lv[1]; else -> !lv[3] && lv[4] }
            p.line(px + 20 * s, ly, px + pw - 20 * s, ly, Colors.withAlpha(if (ok) Colors.LAITON else Colors.PAPER, if (ok) 0.95f else 0.3f), 3f * s)
            p.text(game.str("e05.track", v + 1), px + 26 * s, ly - 6 * s, ui.font(10f), Colors.withAlpha(Colors.PAPER, 0.7f), Font.MONO)
        }
        p.line(px + pw * 0.35f, py + ph * 0.75f, px + pw * 0.6f, py + ph * 0.25f, Colors.withAlpha(Colors.LAITON, if (lv[1] && !lv[0]) 0.95f else 0.25f), 3f * s)
        p.text(game.str("e05.sea"), px + pw - 26 * s, py + ph * 0.25f - 6 * s, ui.font(10f), Colors.withAlpha(Colors.PAPER, 0.7f), Font.MONO, Align.RIGHT)
        if (assist()) p.text(game.str("e05.conform", conform), px + pw / 2, py + ph - 8 * s, ui.font(10f), Colors.withAlpha(Colors.PAPER, 0.6f), Font.MONO, Align.CENTER)
        // table gravée (réveillée à la lentille)
        ui.paragraph(game.str("e05.rules"), x + w * 0.1f, y + h * 0.43f, w * 0.8f, ui.font(if (ui.portrait) 9f else 11f), Colors.INK_SOFT, Font.MONO, lineHeight = 1.3f)
        // leviers
        val lw = min(70 * s, w / 7f)
        for (i in 0..4) {
            val lx = x + w * 0.5f + (i - 2) * (lw + 14 * s) - lw / 2
            val ly = y + h * 0.66f
            val up = lv[i]
            val movable = canMove(i)
            p.fillRoundRect(lx + lw / 2 - 5 * s, ly, 10 * s, 70 * s, 4 * s, Colors.withAlpha(Colors.INK, 0.4f))
            val ky = if (up) ly + 8 * s else ly + 54 * s
            p.fillCircle(lx + lw / 2, ky, 14 * s, if (movable) Colors.LAITON else Colors.withAlpha(Colors.INK, 0.5f))
            p.text("L${i + 1}", lx + lw / 2, ly + 92 * s, ui.font(12f), Colors.INK, Font.MONO, Align.CENTER)
            ui.icon(if (up) "arrow_u" else "arrow_d", lx + lw / 2, ly + 104 * s, 5 * s, Colors.INK_SOFT)
            btns.add(com.ateliermareebasse.cartographie.core.engine.Ui.Btn("lev$i", lx, ly - 10 * s, lw, 90 * s, ""))
        }
    }
    override fun onBoardInput(e: Input, x: Float, y: Float, w: Float, h: Float): Boolean {
        if (e !is Input.Up) return false
        val b = btns.firstOrNull { it.id.startsWith("lev") && ui.hit(it, e.x, e.y) } ?: return false
        val i = b.id.substring(3).toInt()
        if (!canMove(i)) { fail(game.str("e05.locked", i + 1)); return true }
        lv[i] = !lv[i]
        game.sfx("levier_${(i % 3) + 1}")
        if ((0..4).all { lv[it] == target[it] }) { game.sfx("aiguillage_ok"); solve() }
        return true
    }

    companion object {
        /** Test d'accessibilité (12.10) : depuis chaque état ATTEIGNABLE en jeu (depuis l'état initial), la cible reste atteignable ;
         *  le levier maître ramène en outre à l'état initial depuis n'importe quel état. */
        fun reachabilityCheck(): Boolean {
            fun can(state: Int, i: Int): Boolean { fun b(k: Int): Boolean = ((state shr k) and 1) == 1
                return when (i) { 0 -> b(1) && b(2); 1 -> b(0); 2 -> b(1); 3 -> !b(0); 4 -> !b(3); else -> false } }
            val target = 0b10110 // L1=0 L2=1 L3=1 L4=0 L5=1 (bit k = Lk+1)
            val initial = 0b11001 // L1↑ L4↑ L5↑
            val reachable = HashSet<Int>(); val q0 = ArrayDeque<Int>(); q0.add(initial); reachable.add(initial)
            while (q0.isNotEmpty()) { val st = q0.removeFirst(); for (i in 0..4) if (can(st, i)) { val n = st xor (1 shl i); if (reachable.add(n)) q0.add(n) } }
            if (target !in reachable) return false
            for (start in reachable) {
                val seen = HashSet<Int>(); val q = ArrayDeque<Int>(); q.add(start); seen.add(start)
                var ok = false
                while (q.isNotEmpty()) { val st = q.removeFirst(); if (st == target) { ok = true; break }; for (i in 0..4) if (can(st, i)) { val n = st xor (1 shl i); if (seen.add(n)) q.add(n) } }
                if (!ok) return false
            }
            return true
        }
    }
}

// ───────────────────────────── E06 — LES RUBANS DU BOIS ─────────────────────────────
class E06Rubans(game: Game) : PuzzleScreen(game, "E06") {
    private val trees = ArrayList<Pair<Float, Float>>()
    private val known = listOf(Triple(0.42f, 0.55f, Colors.GARANCE), Triple(0.55f, 0.36f, Colors.INDIGO), Triple(0.72f, 0.58f, 0xFFD9B23C.toInt()))
    private val target = 0.86f to 0.30f   // mélèze 214, au bord du vide
    private var selectedLabel: String? = null
    private var trail = ArrayList<Pair<Float, Float>>()
    init {
        val r = java.util.Random(214)
        for (i in 0 until 260) trees.add(r.nextFloat() * 0.96f + 0.02f to r.nextFloat() * 0.85f + 0.08f)
    }
    override fun renderBoard(x: Float, y: Float, w: Float, h: Float) {
        p.fillRect(x, y, w, h, 0xFFE6E0CC.toInt())
        // le vide à droite
        p.fillRect(x + w * 0.92f, y, w * 0.08f, h, Colors.withAlpha(0xFF9FB3C8.toInt(), 0.5f))
        p.text(game.str("e06.void"), x + w * 0.96f, y + h * 0.5f, ui.font(9f), Colors.INK_SOFT, Font.HAND, Align.CENTER)
        for ((i, t) in trees.withIndex()) { val tx = x + t.first * w; val ty = y + t.second * h; p.fillCircle(tx, ty, 3.2f * s, Colors.withAlpha(0xFF4E6B3A.toInt(), 0.8f)); if (i % 40 == 0) p.fillCircle(tx, ty, 1.3f * s, Colors.PAPER) }
        // arbres connus (rubans)
        for ((i, k) in known.withIndex()) {
            val kx = x + k.first * w; val ky = y + k.second * h
            p.fillCircle(kx, ky, 6 * s, k.third)
            if (assist()) p.strokeCircle(kx, ky, 11 * s, Colors.withAlpha(Colors.INK, 0.6f + 0.3f * sin(time * 3)), 1.5f * s)
            p.text(listOf("2015", "2018", "2021")[i], kx, ky - 10 * s, ui.font(10f), Colors.INK, Font.MONO, Align.CENTER)
        }
        p.fillCircle(x + target.first * w, y + target.second * h, 3.2f * s, Colors.withAlpha(0xFF4E6B3A.toInt(), 0.9f))
        // tracé du joueur
        if (trail.size > 1) { val pts = FloatArray(trail.size * 2); trail.forEachIndexed { i, t -> pts[i * 2] = t.first; pts[i * 2 + 1] = t.second }; p.polyline(pts, Colors.withAlpha(Colors.INK, 0.5f), 2f * s) }
        p.text(game.str("e06.help"), x + w / 2, y + 16 * s, ui.font(11f), Colors.INK_SOFT, Font.HAND, Align.CENTER)
        selectedLabel?.let { ui.paper(x + 10 * s, y + h - 60 * s, w * 0.6f, 50 * s, 0.95f); ui.paragraph(it, x + 20 * s, y + h - 52 * s, w * 0.6f - 20 * s, ui.font(11f), Colors.INK, Font.HAND, maxLines = 2) }
    }
    override fun onBoardInput(e: Input, x: Float, y: Float, w: Float, h: Float): Boolean {
        when (e) {
            is Input.Down -> { trail.clear(); trail.add(e.x to e.y) }
            is Input.Move -> { if (trail.isNotEmpty()) trail.add(e.x to e.y) }
            is Input.Up -> {
                val (ex, ey) = inBoard(e, x, y, w, h) ?: return false
                val dragDist = if (trail.size > 1) abs(trail.first().first - ex) + abs(trail.first().second - ey) else 0f
                val tx = x + target.first * w; val ty = y + target.second * h
                if (abs(ex - tx) < 40 * s && abs(ey - ty) < 40 * s) { game.sfx("papier_etiquette"); solve(); return true }
                if (dragDist > 30 * s) return true // simple tracé
                // arbre inconnu : lire une étiquette
                val idx = trees.indices.minByOrNull { val t = trees[it]; abs(x + t.first * w - ex) + abs(y + t.second * h - ey) } ?: return true
                val labels = game.content.labels.values.toList()
                if (labels.isNotEmpty()) { val l = labels[idx % labels.size]; selectedLabel = l.text; st.labelsRead.add(l.n); game.sfx("papier_etiquette") }
                fail(null)
            }
            else -> {}
        }
        return true
    }
}
