package com.ateliermareebasse.cartographie.core.puzzles

import com.ateliermareebasse.cartographie.core.engine.Game
import com.ateliermareebasse.cartographie.core.engine.Ui
import com.ateliermareebasse.cartographie.core.platform.Align
import com.ateliermareebasse.cartographie.core.platform.Colors
import com.ateliermareebasse.cartographie.core.platform.Font
import com.ateliermareebasse.cartographie.core.platform.Input
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Clavier alphabétique partagé (E07, S08). */
class AlphaKeyboard(val game: Game) {
    var typed = ""
    fun render(btns: ArrayList<Ui.Btn>, x: Float, y: Float, w: Float, pressed: String?) {
        val ui = game.ui; val p = game.painter; val s = ui.s
        val rows = listOf("ABCDEFGHIJ", "KLMNOPQRST", "UVWXYZ' <")
        val kw = w / 10.5f; val kh = kw.coerceAtMost(36 * s)
        rows.forEachIndexed { r, row ->
            row.forEachIndexed { c, ch ->
                val id = "key:$ch"
                val b = Ui.Btn(id, x + c * (kw + 2 * s), y + r * (kh + 4 * s), kw, kh, ch.toString(), small = true)
                btns.add(b)
                p.fillRoundRect(b.x, b.y, b.w, b.h, 4 * s, Colors.withAlpha(Colors.INK, if (pressed == id) 0.95f else 0.75f))
                when (ch) {
                    ' ' -> ui.icon("space", b.x + b.w / 2, b.y + b.h / 2, 6 * s, Colors.PAPER)
                    '<' -> ui.icon("backspace", b.x + b.w / 2, b.y + b.h / 2, 7 * s, Colors.PAPER)
                    else -> p.text(ch.toString(), b.x + b.w / 2, b.y + b.h / 2 + ui.font(13f) * 0.38f, ui.font(13f), Colors.PAPER, Font.MONO, Align.CENTER)
                }
            }
        }
    }
    fun handle(id: String): Boolean {
        if (!id.startsWith("key:")) return false
        val ch = id.substring(4)
        when (ch) { "<" -> if (typed.isNotEmpty()) typed = typed.dropLast(1); else -> if (typed.length < 40) typed += ch }
        game.sfx("quill_scratch_0${(typed.length % 9) + 1}")
        return true
    }
}

// ───────────────────────────── E07 — L'ÉCRITURE DES NŒUDS ─────────────────────────────
class E07Noeuds(game: Game) : PuzzleScreen(game, "E07") {
    private val phrases = listOf("BONJOUR", "ELLE TIENT LA NUIT", "CE QUE JE N'AI PAS PU DIRE")
    private var palier = (st.puzzleState["E07"]?.toIntOrNull() ?: 0).coerceIn(0, 2)
    private val kb = AlphaKeyboard(game)
    private val notes = listOf("harpe_01", "harpe_02", "harpe_03", "harpe_04", "harpe_05", "harpe_06", "harpe_07", "harpe_08", "harpe_09", "harpe_10", "harpe_11", "harpe_12")
    override val boardAspect = 1.35f
    private fun ghost(ch: Char, i: Int): Boolean = palier == 2 && (i == 8 || i == 10 || i == 15 || i == 16)  // A, I, P, U arrachés
    override fun renderBoard(x: Float, y: Float, w: Float, h: Float) {
        p.fillRect(x, y, w, h, 0xFFD9C9A8.toInt())
        val phrase = phrases[palier]
        p.text(game.str("e07.palier", palier + 1) + " — " + game.str(if (palier == 0) "e07.p1" else if (palier == 1) "e07.p2" else "e07.p3"), x + w / 2, y + 18 * s, ui.font(12f), Colors.INK, Font.TITLE, Align.CENTER)
        // cordes : une par mot (max 6), nœuds aux positions alphabétiques
        val words = phrase.split(' ')
        val cordTop = y + 34 * s; val cordH = min(26 * s, (h * 0.42f) / words.size)
        val cx0 = x + 30 * s; val cw = w - 60 * s
        var gi = 0
        words.forEachIndexed { wi, word ->
            val cy = cordTop + wi * cordH + cordH / 2
            p.line(cx0, cy, cx0 + cw, cy, Colors.withAlpha(0xFF6B5A3A.toInt(), 0.9f), 2.2f * s)
            for (k in 0 until 26) p.line(cx0 + cw * (k + 0.5f) / 26f, cy - 3 * s, cx0 + cw * (k + 0.5f) / 26f, cy + 3 * s, Colors.withAlpha(Colors.INK, 0.15f), 1f)
            for ((ci, ch) in word.withIndex()) {
                val pos = if (ch == '\'') 26.5f else (ch - 'A' + 0.5f)
                val nx = cx0 + cw * pos / 26f
                val idx = phrase.indexOf(word) + ci
                val isGhost = ghost(ch, idx)
                val yOff = ci * 4f * s - word.length * 2f * s
                if (isGhost) { p.strokeCircle(nx, cy + yOff, 5 * s, Colors.withAlpha(Colors.INK, 0.35f), 1.2f * s); p.line(nx, cy + yOff + 5 * s, nx + 4 * s, cy + yOff + 14 * s, Colors.withAlpha(Colors.INK, 0.3f), 1.5f * s) }
                else p.fillCircle(nx, cy + yOff, if (ch == '\'') 3 * s else 5 * s, Colors.INK)
                gi++
            }
            // trois nœuds serrés = fin de mot
            for (k in 0..2) p.fillCircle(cx0 + cw + 4 * s + k * 5 * s, cy, 2.5f * s, Colors.INK_SOFT)
        }
        // règle des rangs
        for (k in 0 until 26) p.text(('A' + k).toString(), cx0 + cw * (k + 0.5f) / 26f, cordTop + words.size * cordH + 12 * s, ui.font(8f), Colors.INK_SOFT, Font.MONO, Align.CENTER)
        // saisie
        val ty = cordTop + words.size * cordH + 24 * s
        p.fillRoundRect(x + 20 * s, ty, w - 40 * s, 30 * s, 4 * s, Colors.withAlpha(Colors.PAPER, 0.9f))
        p.text(kb.typed + (if ((time * 2).toInt() % 2 == 0) "|" else ""), x + 30 * s, ty + 21 * s, ui.font(15f), Colors.INK, Font.MONO)
        kb.render(btns, x + 20 * s, ty + 38 * s, w - 40 * s, pressed)
        boardBtn("valid", x + w - 130 * s, y + h - 40 * s, 110 * s, 32 * s, game.str("ui.validate"))
        boardBtn("listen", x + 20 * s, y + h - 40 * s, 110 * s, 32 * s, game.str("e07.listen"))
    }
    override fun onBoardInput(e: Input, x: Float, y: Float, w: Float, h: Float): Boolean {
        if (e is Input.Down) {
            // toucher une corde la fait sonner : chaque rang a une note
            val (ex, ey) = inBoard(e, x, y, w, h) ?: return false
            val cx0 = x + 30 * s; val cw = w - 60 * s
            if (ey > y + 30 * s && ey < y + h * 0.5f && ex > cx0 && ex < cx0 + cw && btns.none { ui.hit(it, ex, ey) }) { val k = ((ex - cx0) / cw * 26).toInt().coerceIn(0, 25); game.sfx(notes[k % 12]) }
            return true
        }
        if (e !is Input.Up) return false
        val b = btns.firstOrNull { ui.hit(it, e.x, e.y) } ?: return false
        if (kb.handle(b.id)) return true
        when (b.id) {
            "listen" -> { val phrase = phrases[palier]; var d = 0f; for (ch in phrase) { if (ch.isLetter()) { game.sfx(notes[(ch - 'A') % 12]) } }; say(game.str("e07.listen_msg")) }
            "valid" -> {
                val want = phrases[palier].replace("'", "").replace(" ", "")
                val got = kb.typed.replace("'", "").replace(" ", "")
                val errors = if (got.length != want.length) 99 else got.indices.count { got[it] != want[it] }
                val tol = if (palier == 0) 0 else 1
                if (errors <= tol) {
                    game.sfx("harpe_09")
                    if (palier < 2) { palier++; st.puzzleState["E07"] = palier.toString(); kb.typed = ""; say(game.str("e07.next_palier", palier + 1)) }
                    else solve()
                } else fail(game.str("e07.wrong"))
            }
        }
        return true
    }
}

// ───────────────────────────── E08 — LA CHAMBRE NOIRE ─────────────────────────────
class E08ChambreNoire(game: Game) : PuzzleScreen(game, "E08") {
    private var bath = -1           // -1 hors bain ; 0 révélateur 1 stop 2 fixateur
    private var beats = 0
    private var beatT = 0f
    private val beatDur = 0.33f     // métronome accéléré : 1 coup = 0,33 s réel
    private var stage = 0           // prochain bain attendu
    private var sheets = 5
    private var density = 0f
    private var done = booleanArrayOf(false, false, false)
    private var agitate = 0f
    private val targets = intArrayOf(90, 15, 60)
    private val tol = intArrayOf(12, 6, 12)
    override fun update(dt: Float) {
        super.update(dt)
        if (bath >= 0) { beatT += dt; if (beatT >= beatDur) { beatT -= beatDur; beats++; val acc = beats % 30 == 0; game.audio.playSfx(if (acc) "metronome_grave" else "metronome", 0.7f); if (bath == 0) density = (beats / 90f).coerceIn(0f, 1.3f) } }
        if (agitate > 0) agitate -= dt
    }
    private fun enter(b: Int) {
        if (b != stage) { fail(game.str("e08.order")); return }
        bath = b; beats = 0; beatT = 0f; game.sfx("eau_ride_1")
    }
    private fun exit() {
        val b = bath; bath = -1
        val diff = abs(beats - targets[b])
        if (diff <= tol[b]) { done[b] = true; stage = b + 1; game.sfx("egouttoir"); if (stage == 3) { game.sfx("pince_photo"); solve() } else say(game.str("e08.good", beats)) }
        else {
            sheets--; st.photosRatees.add(if (beats < targets[b]) "pale_${st.photosRatees.size}" else "dense_${st.photosRatees.size}")
            stage = 0; done.fill(false); density = 0f
            fail(if (beats < targets[b]) game.str("e08.too_early") else game.str("e08.too_late"))
            if (sheets <= 0) { sheets = 5; say(game.str("e08.new_pack")) }
        }
    }
    override fun renderBoard(x: Float, y: Float, w: Float, h: Float) {
        p.fillRect(x, y, w, h, 0xFF2A1416.toInt())   // lampe inactinique
        // feuille en cours
        val fx = x + w * 0.62f; val fy = y + h * 0.12f; val fw = w * 0.32f; val fh = h * 0.5f
        p.fillRoundRect(fx, fy, fw, fh, 3 * s, Colors.withAlpha(Colors.PAPER, 0.9f))
        val d = density.coerceIn(0f, 1f)
        if (d > 0.05f) { if (!p.image("art/items/photo_pointe.png", fx + 6 * s, fy + 6 * s, fw - 12 * s, fh - 12 * s, d)) p.fillRoundRect(fx + 6 * s, fy + 6 * s, fw - 12 * s, fh - 12 * s, 2 * s, Colors.withAlpha(Colors.INK, d * 0.8f)) }
        if (density > 1.1f) p.fillRoundRect(fx + 6 * s, fy + 6 * s, fw - 12 * s, fh - 12 * s, 2 * s, Colors.withAlpha(Colors.BLACK, (density - 1f) * 2f))
        p.text(game.str("e08.sheets", sheets), fx + fw / 2, fy + fh + 16 * s, ui.font(11f), Colors.PAPER_DARK, Font.MONO, Align.CENTER)
        // métronome
        p.text(game.str("e08.beats", beats), x + w * 0.3f, y + 26 * s, ui.font(16f), Colors.PAPER, Font.MONO, Align.CENTER)
        val ma = sin(beatT / beatDur * Math.PI.toFloat()) * 20f
        p.line(x + w * 0.3f, y + 90 * s, x + w * 0.3f + ma * s * 0.5f, y + 40 * s, Colors.LAITON, 3f * s)
        // note d'Esteban
        p.text("90 · 15 · 60", x + w * 0.3f, y + 112 * s, ui.font(12f), Colors.withAlpha(Colors.PAPER, 0.7f), Font.HAND, Align.CENTER)
        // bains
        val names = listOf(game.str("e08.dev"), game.str("e08.stop"), game.str("e08.fix"))
        for (i in 0..2) {
            val bx = x + w * 0.06f + i * (w * 0.18f); val by = y + h * 0.5f
            p.fillRoundRect(bx, by, w * 0.16f, h * 0.22f, 6 * s, Colors.withAlpha(if (done[i]) Colors.VERT_ALGUE else if (bath == i) Colors.INDIGO else 0xFF3A3A48.toInt(), 0.9f))
            p.text(names[i], bx + w * 0.08f, by + 20 * s, ui.font(11f), Colors.PAPER, Font.BODY, Align.CENTER)
            if (bath == i) { for (k in 0..3) p.strokeCircle(bx + w * 0.08f + sin(time * 4 + k) * 8 * s, by + h * 0.13f, (6 + k * 4) * s, Colors.withAlpha(Colors.PAPER, 0.3f), 1f) }
            if (bath == -1) boardBtn("in$i", bx, by + h * 0.23f, w * 0.16f, 30 * s, game.str("e08.dip"), enabled = !solved)
        }
        if (bath >= 0) { boardBtn("out", x + w * 0.06f, y + h * 0.82f, w * 0.26f, 32 * s, game.str("e08.lift")); boardBtn("agit", x + w * 0.36f, y + h * 0.82f, w * 0.2f, 32 * s, game.str("e08.agitate"), active = agitate > 0) }
    }
    override fun onBoardInput(e: Input, x: Float, y: Float, w: Float, h: Float): Boolean {
        if (e !is Input.Up) return false
        val b = btns.firstOrNull { ui.hit(it, e.x, e.y) } ?: return false
        when { b.id.startsWith("in") -> enter(b.id.substring(2).toInt()); b.id == "out" -> exit(); b.id == "agit" -> { agitate = 0.6f; game.sfx("eau_ride_2") } }
        return true
    }
}

// ───────────────────────────── E09 — LES CARILLONS ─────────────────────────────
class E09Carillons(game: Game) : PuzzleScreen(game, "E09") {
    private val names = listOf("Marguerite", "Jeanne", "Philomène", "Auguste", "la Fêlée", "Céline", "Joséphine", "Raoul", "Odette", "Anatole", "Fanny", "Théo")
    private val sizes = listOf(1.0f, 0.55f, 0.92f, 0.6f, 0.78f, 0.5f, 0.84f, 0.62f, 0.7f, 0.58f, 0.48f, 0.66f)
    private val solution = listOf(0, 2, 4, 6)
    private val played = ArrayList<Int>()
    private var listenT = -1f
    private val drops = ArrayList<Triple<Float, Float, Float>>()
    override fun update(dt: Float) {
        super.update(dt)
        if (listenT >= 0f) { val prev = listenT; listenT += dt; for ((i, n) in listOf(0.2f, 0.9f, 1.6f, 2.3f).withIndex()) if (prev < n && listenT >= n) { game.sfx("petrel_note_${i + 1}"); drops.add(Triple(0.25f + i * 0.16f, listOf(0.6f, 0.75f, 0.5f, 0.85f)[i], time)) }; if (listenT > 3.2f) listenT = -1f }
    }
    override fun renderBoard(x: Float, y: Float, w: Float, h: Float) {
        p.gradientV(x, y, w, h, 0xFFE9C7A1.toInt(), 0xFF8FA86A.toInt())   // crépuscule sur le verger
        // notation en gouttes d'encre (VAGUE-BASSE-HAUTE-BASSE)
        for (d in drops) { val age = time - d.third; if (age < 4f) p.fillCircle(x + w * d.first, y + h * 0.12f + (d.second - 0.5f) * 40 * s + age * 6 * s, (6f - age) * s, Colors.withAlpha(Colors.INDIGO, 0.8f - age * 0.2f)) }
        p.text(game.str("e09.notation"), x + w * 0.5f, y + 18 * s, ui.font(11f), Colors.INK, Font.HAND, Align.CENTER)
        val cols = 6
        val cw = (w - 40 * s) / cols
        for (i in 0 until 12) {
            val cx = x + 20 * s + (i % cols) * cw + cw / 2; val cy = y + h * 0.36f + (i / cols) * (h * 0.3f)
            val r = 14 * s + sizes[i] * 16 * s
            val active = played.contains(i) && played.indexOf(i) == played.size - 1 && time % 1f < 0.5f
            p.fillPolygon(floatArrayOf(cx - r * 0.75f, cy + r * 0.6f, cx - r * 0.45f, cy - r * 0.6f, cx, cy - r, cx + r * 0.45f, cy - r * 0.6f, cx + r * 0.75f, cy + r * 0.6f), if (active) Colors.OR_ENCRE else 0xFFB08D3C.toInt())
            if (i == 4) p.line(cx - r * 0.2f, cy - r * 0.5f, cx + r * 0.1f, cy + r * 0.4f, Colors.INK, 1.5f * s)  // la fêlure
            p.text(names[i], cx, cy + r * 0.6f + ui.font(10f) * 1.4f, ui.font(10f), Colors.INK, Font.HAND, Align.CENTER)
            btns.add(Ui.Btn("bell$i", cx - cw / 2, cy - r - 10 * s, cw, r * 2 + 30 * s, ""))
        }
        // séquence jouée
        p.text(played.joinToString(" · ") { names[it] }, x + w / 2, y + h - 48 * s, ui.font(11f), Colors.INK, Font.MONO, Align.CENTER)
        boardBtn("listen", x + 16 * s, y + h - 38 * s, 150 * s, 30 * s, game.str("e09.listen"))
        boardBtn("clear", x + w - 120 * s, y + h - 38 * s, 104 * s, 30 * s, game.str("ui.clear"))
    }
    override fun onBoardInput(e: Input, x: Float, y: Float, w: Float, h: Float): Boolean {
        if (e !is Input.Up) return false
        val b = btns.firstOrNull { ui.hit(it, e.x, e.y) } ?: return false
        when {
            b.id == "listen" -> { listenT = 0f; drops.clear() }
            b.id == "clear" -> played.clear()
            b.id.startsWith("bell") -> {
                val i = b.id.substring(4).toInt()
                game.sfx("cloche_${(i + 1).toString().padStart(2, '0')}"); game.haptic(10)
                played.add(i)
                if (played.size >= 4) {
                    if (played.takeLast(4) == solution) { game.sfx("verger_toutes_cloches"); solve() }
                    else if (played.size >= 4 && played.takeLast(4) != solution.take(played.size.coerceAtMost(4))) { fail(null); played.clear() }
                }
            }
        }
        return true
    }
}

// ───────────────────────────── E10 — LA LENTILLE DU PHARE ─────────────────────────────
class E10Lentille(game: Game) : PuzzleScreen(game, "E10") {
    private val pr = intArrayOf(0, 0, 0)
    private var weight = 1f      // 1 = remonté, 0 = au sol
    private var beam = 0f
    private var stable = 0f
    override val allowReset = true
    override fun onReset() { pr.fill(0) }
    private fun attard() = pr[0] + pr[1] - pr[2]
    override fun update(dt: Float) {
        super.update(dt)
        weight = (weight - dt * 0.015f).coerceAtLeast(0f)
        val speed = if (weight <= 0f) 0f else 0.6f
        val slow = if (attard() == 12 && abs(sin(beam)) > 0.92f) 0.15f else 1f
        beam += dt * speed * slow
        if (attard() == 12 && pr.distinct().size == 3 && weight > 0f) { stable += dt; if (stable > 3f && !solved) solve() } else stable = 0f
    }
    override fun renderBoard(x: Float, y: Float, w: Float, h: Float) {
        p.fillRect(x, y, w, h, 0xFF0B1224.toInt())
        // horizon, mer, pointe à droite
        p.fillRect(x, y + h * 0.5f, w, h * 0.5f, 0xFF10203A.toInt())
        p.fillPolygon(floatArrayOf(x + w * 0.78f, y + h * 0.5f, x + w, y + h * 0.42f, x + w, y + h * 0.5f), 0xFF1E2A3A.toInt())
        p.fillCircle(x + w * 0.9f, y + h * 0.47f, 3 * s, Colors.GARANCE) // la boîte
        // faisceau
        val bx = x + w * 0.5f + sin(beam) * w * 0.45f
        val onPointe = bx > x + w * 0.8f
        p.fillPolygon(floatArrayOf(x + w * 0.5f, y + h * 0.2f, bx - 30 * s, y + h * 0.5f, bx + 30 * s, y + h * 0.5f), Colors.withAlpha(0xFFFFF0C0.toInt(), if (onPointe && attard() == 12) 0.5f else 0.25f))
        p.text(game.str("e10.attard", attard()), x + w / 2, y + 22 * s, ui.font(13f), Colors.PAPER, Font.MONO, Align.CENTER)
        if (stable > 0f) p.text(game.str("e10.holding"), x + w / 2, y + 40 * s, ui.font(11f), Colors.LAITON, Font.HAND, Align.CENTER)
        // trois molettes
        for (i in 0..2) {
            val cx = x + w * (0.25f + 0.25f * i); val cy = y + h * 0.74f
            knob(cx, cy, 26 * s, pr[i] * 30f, "p${i + 1} = ${pr[i]}°")
            btns.add(Ui.Btn("k$i-", cx - 60 * s, cy - 26 * s, 34 * s, 52 * s, "")); btns.add(Ui.Btn("k$i+", cx + 26 * s, cy - 26 * s, 34 * s, 52 * s, ""))
            ui.icon("minus", cx - 43 * s, cy, 6 * s, Colors.PAPER); ui.icon("plus", cx + 43 * s, cy, 6 * s, Colors.PAPER)
            if (assist() && i == 1) p.text("4 · 9 · 1", cx, cy - 40 * s, ui.font(10f), Colors.withAlpha(Colors.LAITON, 0.6f), Font.MONO, Align.CENTER)
        }
        // poids d'horlogerie
        p.fillRect(x + 20 * s, y + 30 * s, 6 * s, h * 0.5f, Colors.withAlpha(Colors.PAPER, 0.3f))
        p.fillRoundRect(x + 12 * s, y + 30 * s + (1f - weight) * h * 0.45f, 22 * s, 24 * s, 3 * s, Colors.LAITON)
        boardBtn("wind", x + 12 * s, y + h - 36 * s, 120 * s, 28 * s, game.str("e10.wind"), active = weight < 0.3f)
        if (weight <= 0f) p.text(game.str("e10.sleeps"), x + w / 2, y + h * 0.58f, ui.font(12f), Colors.PAPER_DARK, Font.HAND, Align.CENTER)
    }
    override fun onBoardInput(e: Input, x: Float, y: Float, w: Float, h: Float): Boolean {
        if (e !is Input.Up) return false
        val b = btns.firstOrNull { ui.hit(it, e.x, e.y) } ?: return false
        if (b.id == "wind") { weight = 1f; game.sfx("levier_3"); return true }
        if (b.id.startsWith("k")) { val i = b.id[1] - '0'; val d = if (b.id.endsWith("+")) 1 else -1; pr[i] = (pr[i] + d).coerceIn(0, 12); game.sfx("molette_cran"); if (attard() == 12) game.sfx("souffle_grave") }
        return true
    }
}

// ───────────────────────────── E11 — LE CADRAN DE LA TERRASSE ─────────────────────────────
class E11Cadran(game: Game) : PuzzleScreen(game, "E11") {
    private var dalle = -1
    private var hour = 6
    private var minutes = 0
    private val roman = listOf("VII", "VIII", "IX", "X", "XI", "XII", "I", "II", "III")
    override fun renderBoard(x: Float, y: Float, w: Float, h: Float) {
        p.gradientV(x, y, w, h, 0xFFF0D9B5.toInt(), 0xFFC9B48E.toInt())
        // mât et ombre (respire au vent)
        val mx = x + w * 0.5f; val my = y + h * 0.45f
        val wind = sin(time * 1.3f) * 4 * s
        p.line(mx, my, mx - w * 0.28f + wind, my + h * 0.32f, Colors.withAlpha(Colors.INK, 0.45f), 10f * s)
        p.fillCircle(mx, my, 6 * s, Colors.INK)
        // dalles gravées en arc
        for (i in roman.indices) {
            val a = Math.toRadians((200 + i * 17.5).toDouble())
            val dx = (mx + w * 0.38f * cos(a)).toFloat(); val dy = (my - h * 0.34f * sin(a)).toFloat()
            val ok = i == 4
            p.fillRoundRect(dx - 22 * s, dy - 12 * s, 44 * s, 24 * s, 3 * s, Colors.withAlpha(if (dalle == i) Colors.LAITON else Colors.PAPER_SHADE, 0.9f))
            if (assist() && ok) p.strokeRoundRect(dx - 24 * s, dy - 14 * s, 48 * s, 28 * s, 4 * s, Colors.withAlpha(Colors.GARANCE, 0.6f), 1.5f * s)
            p.text(roman[i], dx, dy + 5 * s, ui.font(11f), Colors.withAlpha(Colors.INK, 0.8f), Font.MONO, Align.CENTER)
            btns.add(Ui.Btn("dalle$i", dx - 26 * s, dy - 18 * s, 52 * s, 36 * s, ""))
        }
        p.text("ANTE MERIDIEM", mx, y + 20 * s, ui.font(10f), Colors.INK_SOFT, Font.MONO, Align.CENTER)
        // plaque de correction
        p.fillRoundRect(x + 12 * s, y + 12 * s, 90 * s, 36 * s, 3 * s, Colors.LAITON)
        p.text("+7 min", x + 57 * s, y + 35 * s, ui.font(12f), Colors.INK, Font.MONO, Align.CENTER)
        // couronne de laiton : heures + minutes
        val kx = x + w * 0.5f; val ky = y + h * 0.86f
        p.text(game.str("e11.crown", hour, minutes.toString().padStart(2, '0')), kx, ky - 30 * s, ui.font(14f), Colors.INK, Font.MONO, Align.CENTER)
        boardBtn("h-", kx - 170 * s, ky - 16 * s, 40 * s, 32 * s, "h−"); boardBtn("h+", kx - 125 * s, ky - 16 * s, 40 * s, 32 * s, "h+")
        boardBtn("m-", kx - 60 * s, ky - 16 * s, 40 * s, 32 * s, "m−"); boardBtn("m+", kx - 15 * s, ky - 16 * s, 40 * s, 32 * s, "m+")
        boardBtn("set", kx + 50 * s, ky - 16 * s, 110 * s, 32 * s, game.str("e11.pose"))
    }
    override fun onBoardInput(e: Input, x: Float, y: Float, w: Float, h: Float): Boolean {
        if (e !is Input.Up) return false
        val b = btns.firstOrNull { ui.hit(it, e.x, e.y) } ?: return false
        when (b.id) {
            "h-" -> hour = (hour + 11) % 12; "h+" -> hour = (hour + 1) % 12
            "m-" -> minutes = (minutes + 59) % 60; "m+" -> minutes = (minutes + 1) % 60
            "set" -> if (dalle == 4 && hour == 11 && minutes == 7) { game.sfx("laiton_assemble_2"); solve() } else fail(if (dalle != 4) game.str("e11.read_first") else game.str("e11.not_exact"))
            else -> if (b.id.startsWith("dalle")) { dalle = b.id.substring(5).toInt(); game.sfx("pierre_glisse"); if (dalle == 4) say(game.str("e11.dalle_xi")) else say(game.str("e11.dalle_other")) }
        }
        if (b.id.startsWith("h") || b.id.startsWith("m")) game.sfx("molette_cran")
        return true
    }
}
