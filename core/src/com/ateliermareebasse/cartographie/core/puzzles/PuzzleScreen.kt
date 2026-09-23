package com.ateliermareebasse.cartographie.core.puzzles

import com.ateliermareebasse.cartographie.core.data.PuzzleSheet
import com.ateliermareebasse.cartographie.core.engine.Game
import com.ateliermareebasse.cartographie.core.engine.Screen
import com.ateliermareebasse.cartographie.core.engine.Settings
import com.ateliermareebasse.cartographie.core.engine.Ui
import com.ateliermareebasse.cartographie.core.platform.Align
import com.ateliermareebasse.cartographie.core.platform.Colors
import com.ateliermareebasse.cartographie.core.platform.Font
import com.ateliermareebasse.cartographie.core.platform.Input
import kotlin.math.min

/**
 * Base des énigmes : cadre papier, fiche (intro / indices opt-in / conclusion offerte),
 * comptage des échecs, résolution → effets + scène « after ».
 */
abstract class PuzzleScreen(game: Game, val id: String) : Screen(game) {
    protected val ui get() = game.ui
    protected val p get() = game.painter
    protected val st get() = game.state
    protected val sheet: PuzzleSheet? = game.content.puzzles[id]
    protected val btns = ArrayList<Ui.Btn>()
    protected var pressed: String? = null
    protected var message: String? = null
    private var messageAge = 0f
    protected var solved = false
    private var solvedAt = 0f
    private var showIntro = true
    private var hintLevel = 0
    private var hintOpen = false
    protected var idle = 0f
    protected val s get() = ui.s
    protected var board = FloatArray(4)
    protected var stateKey = "pz:$id"

    abstract fun renderBoard(x: Float, y: Float, w: Float, h: Float)
    abstract fun onBoardInput(e: Input, x: Float, y: Float, w: Float, h: Float): Boolean
    open fun onReset() {}
    open val allowReset = false
    open val boardAspect = 1.6f

    override fun onEnter() {
        game.sfx("pli_depli_2")
        if (st.count("puzzle_tuto") == 0) { st.inc("puzzle_tuto"); game.tutorial("puzzle") }
    }

    protected fun say(text: String, sfx: String? = null) { message = text; messageAge = 0f; sfx?.let { game.sfx(it) } }
    protected fun fail(text: String? = null) {
        st.puzzleAttempts[id] = (st.puzzleAttempts[id] ?: 0) + 1
        game.sfx("bois_grince")
        text?.let { say(it) }
        // Assuré : Ysolde propose l'indice après un échec ; Standard : la synthèse après 2
        val attempts = st.puzzleAttempts[id] ?: 0
        if (game.settings.hintMode == Settings.HintMode.ASSURE && attempts >= 1 && hintLevel == 0) say(game.str("puzzle.hint_offer"))
    }
    protected fun attempts() = st.puzzleAttempts[id] ?: 0
    /** Le niveau 3 (aide visuelle) s'active après 2 échecs (ou 1 en mode Assuré), jamais en Puriste. */
    protected fun assist(): Boolean = when (game.settings.hintMode) { Settings.HintMode.PURISTE -> false; Settings.HintMode.ASSURE -> attempts() >= 1 || hintLevel >= 3; Settings.HintMode.STANDARD -> attempts() >= 2 || hintLevel >= 3 }

    protected fun solve() {
        if (solved) return
        solved = true; solvedAt = time
        st.puzzlesSolved.add(id)
        val sh = sheet
        game.sfx(sh?.sfx ?: "harpe_07"); game.haptic(40)
        if (sh != null) game.effects.apply(sh.effects)
        game.hooks.onPuzzleSolved(id)
        message = sh?.success
        messageAge = 0f
    }

    private fun close() {
        game.pop()
        if (solved) {
            // plusieurs scènes possibles « a | b » : la première dont la condition tient
            sheet?.after?.split('|')?.map { it.trim() }?.firstOrNull { id -> game.content.scenes[id]?.let { sc -> game.conditions.eval(sc.cond) && !(sc.once && id in st.scenesSeen) } == true }?.let { game.runScene(it) }
        }
    }

    override fun update(dt: Float) { super.update(dt); messageAge += dt; idle += dt }

    override fun render() {
        btns.clear()
        p.gradientV(0f, 0f, p.width, p.height, 0xFF2A2F3E.toInt(), 0xFF12151E.toInt())
        if (game.settings.grain) p.image("art/ui/grain.png", 0f, 0f, p.width, p.height, 0.06f)
        val top = ui.safeTop + 54 * s
        val bw = min(p.width - 24 * s, (p.height - top - 110 * s) * boardAspect)
        val bh = bw / boardAspect
        val bx = (p.width - bw) / 2; val by = top
        board = floatArrayOf(bx, by, bw, bh)
        // titre
        p.text(sheet?.name ?: id, p.width / 2, ui.safeTop + 34 * s, ui.font(20f), Colors.PAPER, Font.TITLE, Align.CENTER)
        // cadre
        ui.paper(bx, by, bw, bh, 0.97f)
        p.pushClip(bx, by, bw, bh)
        renderBoard(bx, by, bw, bh)
        p.popClip()
        // boutons
        val close = Ui.Btn("close", 12 * s, ui.safeTop + 10 * s, 44 * s, 44 * s, "", icon = "close")
        btns.add(close); p.fillRoundRect(close.x, close.y, close.w, close.h, 10 * s, Colors.withAlpha(Colors.INK, 0.6f)); ui.icon("close", close.x + 22 * s, close.y + 22 * s, 8 * s, Colors.PAPER)
        if (game.settings.hintMode != Settings.HintMode.PURISTE && !solved) {
            val hb = Ui.Btn("hint", p.width - 56 * s, ui.safeTop + 10 * s, 44 * s, 44 * s, "", icon = "hint")
            btns.add(hb); p.fillRoundRect(hb.x, hb.y, hb.w, hb.h, 10 * s, Colors.withAlpha(Colors.INK, 0.6f)); ui.icon("hint", hb.x + 22 * s, hb.y + 22 * s, 8 * s, Colors.LAITON)
        }
        if (allowReset && !solved) {
            val rb = Ui.Btn("reset", p.width - 110 * s, ui.safeTop + 10 * s, 44 * s, 44 * s, "", icon = "gear")
            btns.add(rb); p.fillRoundRect(rb.x, rb.y, rb.w, rb.h, 10 * s, Colors.withAlpha(Colors.INK, 0.6f)); ui.icon("gear", rb.x + 22 * s, rb.y + 22 * s, 8 * s, Colors.PAPER)
        }
        // message (intro / pensée / conclusion)
        val msg = message ?: (if (showIntro) sheet?.intro else null)
        if (msg != null) {
            val fs = ui.font(15f)
            val mw = min(p.width - 30 * s, 820 * s)
            val mh = ui.paragraphHeight(msg, mw - 40 * s, fs, Font.HAND, 1.35f) + 26 * s
            val my = p.height - mh - 12 * s
            val a = min(1f, messageAge * 3f)
            ui.paper((p.width - mw) / 2, my, mw, mh, 0.95f * a)
            p.fillRect((p.width - mw) / 2 + 10 * s, my + 10 * s, 2 * s, mh - 20 * s, Colors.withAlpha(if (solved) Colors.LAITON else Colors.GARANCE, 0.7f * a))
            ui.paragraph(msg, (p.width - mw) / 2 + 22 * s, my + 12 * s, mw - 40 * s, fs, Colors.withAlpha(Colors.INK, a), Font.HAND, lineHeight = 1.35f)
        }
        if (solved && time - solvedAt > 1.2f) {
            val b = Ui.Btn("done", p.width / 2 - 80 * s, ui.safeTop + 8 * s, 160 * s, 40 * s, game.str("ui.continue"), gold = true, small = true)
            btns.add(b); ui.button(b, pressed == "done")
        }
        if (hintOpen) renderHints()
    }

    private fun renderHints() {
        val sh = sheet ?: return
        ui.scrim(0.5f)
        val w = min(560 * s, p.width - 40 * s)
        val fs = ui.font(15f)
        val visible = sh.hints.take(hintLevel)
        val bodyH = visible.sumOf { (ui.paragraphHeight(hintText(it), w - 50 * s, fs, Font.HAND, 1.35f) + 14 * s).toDouble() }.toFloat()
        val h = bodyH + 150 * s
        val x = (p.width - w) / 2; val y = (p.height - h) / 2
        ui.paper(x, y, w, h)
        p.text(game.str("puzzle.hints_title"), x + w / 2, y + 28 * s, ui.font(18f), Colors.INK, Font.TITLE, Align.CENTER)
        var yy = y + 46 * s
        for ((i, hnt) in visible.withIndex()) {
            p.text(game.str("puzzle.hint_level", i + 1) + (if (hnt.first != "auto" && hnt.first != "PENSEE") " — ${speaker(hnt.first)}" else ""), x + 25 * s, yy + fs * 0.9f, ui.font(12f), Colors.INK_SOFT, Font.MONO)
            yy += fs * 1.2f
            yy += ui.paragraph(hintText(hnt), x + 25 * s, yy, w - 50 * s, fs, Colors.INK, Font.HAND, lineHeight = 1.35f) + 10 * s
        }
        val canMore = hintLevel < sh.hints.size && (game.settings.hintMode != Settings.HintMode.STANDARD || hintLevel < 2 || attempts() >= 2)
        val b1 = Ui.Btn("more", x + 20 * s, y + h - 56 * s, w / 2 - 30 * s, 40 * s, if (hintLevel == 0) game.str("puzzle.ask_hint") else game.str("puzzle.more_hint"), enabled = canMore, small = true)
        val b2 = Ui.Btn("hclose", x + w / 2 + 10 * s, y + h - 56 * s, w / 2 - 30 * s, 40 * s, game.str("ui.close"), small = true)
        btns.clear(); btns.add(b1); btns.add(b2); ui.button(b1, pressed == "more"); ui.button(b2, pressed == "hclose")
        if (!canMore && hintLevel < sh.hints.size) p.text(game.str("puzzle.hint_locked"), x + w / 2, y + h - 66 * s, ui.font(11f), Colors.INK_SOFT, Font.HAND, Align.CENTER)
    }
    private fun speaker(id: String) = game.content.characters[id.lowercase()]?.name ?: id
    private fun hintText(h: Pair<String, String>) = h.second

    override fun onInput(e: Input): Boolean {
        when (e) {
            is Input.Down -> { pressed = btns.firstOrNull { ui.hit(it, e.x, e.y) }?.id; idle = 0f; showIntro = false; if (pressed == null && !hintOpen) onBoardInput(e, board[0], board[1], board[2], board[3]); return true }
            is Input.Move -> { if (pressed == null && !hintOpen) onBoardInput(e, board[0], board[1], board[2], board[3]); return true }
            is Input.Up -> {
                val pr = pressed; pressed = null
                if (pr != null && btns.any { it.id == pr && ui.hit(it, e.x, e.y) }) { onButton(pr); return true }
                if (!hintOpen) onBoardInput(e, board[0], board[1], board[2], board[3])
                return true
            }
            else -> return false
        }
    }

    private fun onButton(id: String) {
        game.sfx("ui_tap")
        when (id) {
            "close" -> close()
            "done" -> close()
            "hint" -> { hintOpen = true; if (hintLevel == 0) hintLevel = 1 }
            "more" -> { hintLevel = min((sheet?.hints?.size ?: 3), hintLevel + 1); if (hintLevel == 2) game.state.addLien(hintCarrier(), 0) }
            "hclose" -> hintOpen = false
            "reset" -> { onReset(); game.sfx("levier_2") }
        }
    }
    private fun hintCarrier() = sheet?.hints?.getOrNull(1)?.first ?: "YSOLDE"

    override fun onBack(): Boolean { if (hintOpen) { hintOpen = false; return true }; close(); return true }

    // ─── aides de dessin communes ───
    protected fun knob(cx: Float, cy: Float, r: Float, angle: Float, label: String, color: Int = Colors.LAITON) {
        p.fillCircle(cx, cy, r, Colors.withAlpha(Colors.INK, 0.85f)); p.strokeCircle(cx, cy, r, color, 2f * s)
        val a = Math.toRadians(angle.toDouble())
        p.line(cx, cy, (cx + r * 0.8f * Math.sin(a)).toFloat(), (cy - r * 0.8f * Math.cos(a)).toFloat(), color, 3f * s)
        p.text(label, cx, cy + r + ui.font(13f) * 1.2f, ui.font(13f), Colors.INK, Font.MONO, Align.CENTER)
    }
    protected fun smallBtn(id: String, x: Float, y: Float, w: Float, h: Float, label: String, gold: Boolean = false, enabled: Boolean = true): Ui.Btn {
        val b = Ui.Btn(id, x, y, w, h, label, enabled = enabled, gold = gold, small = true); btns.add(b); ui.button(b, pressed == id); return b
    }
    protected fun boardBtn(id: String, x: Float, y: Float, w: Float, h: Float, label: String, active: Boolean = false, enabled: Boolean = true): Ui.Btn {
        val b = Ui.Btn(id, x, y, w, h, label, enabled = enabled, small = true); btns.add(b)
        p.fillRoundRect(x, y, w, h, 6 * s, Colors.withAlpha(if (active) Colors.LAITON else Colors.INK, if (enabled) (if (active) 0.85f else 0.75f) else 0.25f))
        val fs = ui.font(13f)
        val lines = ui.wrap(label, fs, w - 8 * s)
        var yy = y + (h - lines.size * fs * 1.2f) / 2
        for (l in lines) { p.text(l, x + w / 2, yy + fs, fs, if (active) Colors.INK else Colors.PAPER, Font.BODY, Align.CENTER); yy += fs * 1.2f }
        return b
    }
    protected fun inBoard(e: Input, x: Float, y: Float, w: Float, h: Float): Pair<Float, Float>? {
        val (ex, ey) = when (e) { is Input.Down -> e.x to e.y; is Input.Move -> e.x to e.y; is Input.Up -> e.x to e.y; else -> return null }
        return if (ex >= x && ex <= x + w && ey >= y && ey <= y + h) ex to ey else null
    }
}
