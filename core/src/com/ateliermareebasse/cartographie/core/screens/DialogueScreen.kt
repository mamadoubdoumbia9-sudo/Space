package com.ateliermareebasse.cartographie.core.screens

import com.ateliermareebasse.cartographie.core.data.Line
import com.ateliermareebasse.cartographie.core.data.Option
import com.ateliermareebasse.cartographie.core.data.Scene
import com.ateliermareebasse.cartographie.core.engine.Game
import com.ateliermareebasse.cartographie.core.engine.Screen
import com.ateliermareebasse.cartographie.core.engine.Ui
import com.ateliermareebasse.cartographie.core.platform.Align
import com.ateliermareebasse.cartographie.core.platform.Colors
import com.ateliermareebasse.cartographie.core.platform.Font
import com.ateliermareebasse.cartographie.core.platform.Input
import kotlin.math.min

/** Lecture d'une scène de dialogue (superposition sur le monde). */
class DialogueScreen(game: Game, val scene: Scene) : Screen(game) {
    override val opaque = false
    private val ui get() = game.ui
    private val p get() = game.painter
    private var pc = 0                      // index de ligne
    private var current: Line? = null
    private var shown = 0f                  // caractères affichés (machine à écrire)
    private var waitLeft = 0f
    private var options: List<Option> = emptyList()
    private var btns = ArrayList<Ui.Btn>()
    private var pressed: String? = null
    private var finished = false
    private var lastSpeaker = ""
    private var appear = 0f
    private var autoAdvance = 0f
    private val inlineRe = Regex("""\[\[([^|\]]*)\|([^|\]]*)(?:\|([^\]]*))?]]""")

    override fun onEnter() {
        game.state.scenesSeen.add(scene.id)
        game.hooks.onSceneStart(scene.id)
        advance()
    }

    private fun substitute(t: String): String = inlineRe.replace(t) { m ->
        if (game.conditions.eval(m.groupValues[1])) m.groupValues[2] else m.groupValues[3]
    }

    /** Avance jusqu'à la prochaine ligne affichable. */
    private fun advance() {
        options = emptyList(); btns.clear(); autoAdvance = 0f
        while (true) {
            if (pc >= scene.lines.size) { finish(); return }
            val l = scene.lines[pc++]
            when (l) {
                is Line.Say -> { current = l; shown = 0f; appear = 0f; lastSpeaker = l.speaker; game.hooks.onSay(scene.id, l.speaker); return }
                is Line.Thought -> { current = l; shown = 0f; appear = 0f; return }
                is Line.Stage -> { current = l; shown = 0f; appear = 0f; return }
                is Line.Choice -> {
                    val visible = l.options.filter { game.conditions.eval(it.cond) }
                    if (visible.isEmpty()) continue
                    options = visible; current = l; appear = 0f
                    if (visible.size == 1 && visible[0].text.isEmpty()) { choose(visible[0]); return }
                    return
                }
                is Line.Effects -> { game.effects.apply(l.effects); if (l.effects.any { it.startsWith("cin ") || it.startsWith("travel ") || it == "letter" || it == "epilogue" || it == "end_chapter" || it.startsWith("puzzle ") }) { /* l'écran a changé : on continue après */ } }
                is Line.Label -> {}
                is Line.Goto -> { if (game.conditions.eval(l.cond)) { val idx = scene.labels[l.label]; if (idx != null) pc = idx + 1 else game.log("label inconnu ${l.label} dans ${scene.id}") } }
                is Line.Cue -> {
                    when (l.cue) {
                        "sfx" -> game.sfx(l.arg)
                        "music" -> game.music(l.arg.takeIf { it != "off" })
                        "ambience" -> game.ambience(l.arg.takeIf { it != "off" })
                        "voice" -> game.audio.playVoice(l.arg)
                        "haptic" -> game.haptic(l.arg.toIntOrNull() ?: 40)
                        "wait", "hold" -> { waitLeft = (l.arg.toFloatOrNull() ?: 1f).coerceAtMost(6f); current = null; return }
                        "shake" -> {}
                    }
                }
                Line.End -> { finish(); return }
            }
        }
    }

    private fun finish() {
        if (finished) return
        finished = true
        game.hooks.onSceneEnd(scene.id)
        if (game.isTop(this)) game.pop() else game.screens.remove(this)
    }

    private fun choose(o: Option) {
        if (o.gold) { game.emotionalCooldown = 20f; game.sfx("harpe_05"); game.haptic(25) } else game.sfx("ui_tap")
        game.effects.apply(o.effects)
        game.hooks.onChoice(scene.id, o)
        if (o.goto != null) { val idx = scene.labels[o.goto]; if (idx != null) pc = idx + 1 else game.log("label inconnu ${o.goto} dans ${scene.id}") }
        advance()
    }

    override fun update(dt: Float) {
        super.update(dt)
        appear += dt
        if (waitLeft > 0f) { waitLeft -= dt; if (waitLeft <= 0f) advance(); return }
        val c = current
        val len = textOf(c).length
        if (shown < len) shown = min(len.toFloat(), shown + dt * 46f * (if (game.settings.reduceMotion) 3f else 1f))
        else if (c is Line.Stage) { autoAdvance += dt; if (autoAdvance > 2.6f) advance() }
    }

    private fun textOf(l: Line?): String = when (l) { is Line.Say -> substitute(l.text); is Line.Thought -> substitute(l.text); is Line.Stage -> substitute(l.text); else -> "" }

    private fun speakerName(id: String): String {
        val key = id.lowercase().replace('é', 'e').replace('è', 'e')
        return game.content.characters[key]?.name ?: game.content.str("speaker.$key").takeIf { !it.startsWith("[") } ?: id.lowercase().replaceFirstChar { it.uppercase() }
    }
    private fun speakerColor(id: String): Int { val k = id.lowercase().replace('é', 'e'); val kk = if (k == "tom" || k == "till") "tom_till" else k; return game.content.characters[kk]?.palette ?: Colors.LAITON }

    override fun render() {
        val s = ui.s
        val c = current
        val a = min(1f, appear * 4f)
        if (waitLeft > 0f && c == null) return
        val margin = 16 * s
        val panelW = min(p.width - 2 * margin, 900 * s)
        val x = (p.width - panelW) / 2
        when (c) {
            is Line.Say -> {
                val fs = ui.font(18f)
                val text = substitute(c.text)
                val portraitW = if (ui.portrait) 0f else 120 * s
                val textW = panelW - 36 * s - portraitW
                val th = ui.paragraphHeight(text, textW, fs, Font.BODY, 1.36f)
                val h = th + fs * 2.2f + 30 * s
                val y = p.height - h - 14 * s
                ui.inkPanel(x, y, panelW, h, 0.86f * a)
                val speaker = c.speaker
                val key = speaker.lowercase().replace('é', 'e')
                if (portraitW > 0f) {
                    val ph = h - 20 * s
                    val path = "art/characters/$key.png"
                    if (!p.image(path, x + 10 * s, y + 10 * s, min(portraitW, ph), min(portraitW, ph), a)) {
                        p.fillCircle(x + 10 * s + portraitW / 2, y + h / 2, portraitW * 0.3f, Colors.withAlpha(speakerColor(speaker), 0.6f * a))
                    }
                }
                val tx = x + 18 * s + portraitW
                p.text(speakerName(speaker), tx, y + 14 * s + fs, fs * 0.95f, Colors.withAlpha(speakerColor(speaker), a), Font.TITLE)
                val visibleText = text.take(shown.toInt())
                ui.paragraph(visibleText, tx, y + 18 * s + fs * 1.5f, textW, fs, Colors.withAlpha(Colors.PAPER, a), Font.BODY, lineHeight = 1.36f)
                if (shown >= text.length && options.isEmpty()) ui.icon("arrow_d", x + panelW - 18 * s, y + h - 14 * s, 5 * s, Colors.withAlpha(Colors.LAITON, 0.6f + 0.4f * kotlin.math.sin(time * 4)))
            }
            is Line.Thought -> {
                val fs = ui.font(17f)
                val text = substitute(c.text)
                val th = ui.paragraphHeight(text, panelW - 44 * s, fs, Font.HAND, 1.4f)
                val h = th + 34 * s
                val y = p.height - h - 14 * s
                ui.paper(x, y, panelW, h, 0.95f * a)
                p.fillRect(x + 10 * s, y + 12 * s, 2 * s, h - 24 * s, Colors.withAlpha(Colors.GARANCE, 0.7f * a))
                ui.paragraph(text.take(shown.toInt()), x + 24 * s, y + 16 * s, panelW - 44 * s, fs, Colors.withAlpha(Colors.INK, a), Font.HAND, lineHeight = 1.4f)
            }
            is Line.Stage -> {
                val fs = ui.font(15f)
                val text = substitute(c.text)
                val th = ui.paragraphHeight(text, panelW - 60 * s, fs, Font.HAND, 1.35f)
                val h = th + 26 * s
                val y = p.height - h - 14 * s
                p.fillRoundRect(x + 20 * s, y, panelW - 40 * s, h, 6 * s, Colors.withAlpha(Colors.BLACK, 0.55f * a))
                ui.paragraph(text.take(shown.toInt()), x + 40 * s, y + 12 * s, panelW - 80 * s, fs, Colors.withAlpha(Colors.PAPER_DARK, a), Font.HAND, Align.CENTER, 1.35f)
            }
            is Line.Choice -> renderChoices(x, panelW, a)
            else -> {}
        }
        if (options.isNotEmpty() && c !is Line.Choice) renderChoices(x, panelW, a)
    }

    private fun renderChoices(x: Float, panelW: Float, a: Float) {
        val s = ui.s
        btns.clear()
        val fs = ui.font(16f)
        val maxTextW = panelW - 70 * s
        val heights = options.map { ui.paragraphHeight(it.text.ifEmpty { game.str("dlg.silent") }, maxTextW, fs, Font.BODY, 1.25f) + 20 * s }
        val total = heights.sum() + (options.size - 1) * 8 * s + 24 * s
        val y0 = p.height - total - 14 * s
        ui.inkPanel(x, y0, panelW, total, 0.9f * a)
        var y = y0 + 12 * s
        options.forEachIndexed { i, o ->
            val b = Ui.Btn("opt$i", x + 14 * s, y, panelW - 28 * s, heights[i], o.text)
            btns.add(b)
            val bg = if (o.gold) Colors.withAlpha(Colors.OR_ENCRE, if (pressed == b.id) 0.55f else 0.22f) else Colors.withAlpha(Colors.PAPER, if (pressed == b.id) 0.3f else 0.08f)
            p.fillRoundRect(b.x, b.y, b.w, b.h, 6 * s, bg)
            p.strokeRoundRect(b.x, b.y, b.w, b.h, 6 * s, Colors.withAlpha(if (o.gold) Colors.OR_ENCRE else Colors.PAPER, 0.5f * a), 1f * s)
            if (o.gold) ui.icon("star", b.x + 14 * s, b.y + b.h / 2, 5 * s, Colors.withAlpha(Colors.OR_ENCRE, a))
            val txt = if (o.text.isEmpty()) game.str("dlg.silent") else o.text
            ui.paragraph(txt, b.x + 28 * s, b.y + 10 * s, maxTextW, fs, Colors.withAlpha(if (o.gold) Colors.OR_ENCRE else Colors.PAPER, a), Font.BODY, lineHeight = 1.25f)
            y += heights[i] + 8 * s
        }
        if (game.state.count("gold_seen") == 0 && options.any { it.gold }) { game.state.inc("gold_seen"); game.tutorial("gold") }
    }

    override fun onInput(e: Input): Boolean {
        when (e) {
            is Input.Down -> { pressed = btns.firstOrNull { ui.hit(it, e.x, e.y) }?.id; return true }
            is Input.Up -> {
                val pr = pressed; pressed = null
                if (waitLeft > 0f) { waitLeft = 0f; advance(); return true }
                if (options.isNotEmpty()) {
                    val b = btns.firstOrNull { ui.hit(it, e.x, e.y) }
                    if (b != null && b.id == pr) { choose(options[btns.indexOf(b)]) }
                    return true
                }
                val len = textOf(current).length
                if (shown < len - 1) { shown = len.toFloat(); return true }
                advance()
                return true
            }
            is Input.Move -> return true
            else -> return false
        }
    }

    override fun onBack(): Boolean {
        // pas d'annulation d'un choix ; sinon avance
        if (options.isNotEmpty()) return true
        val len = textOf(current).length
        if (shown < len - 1) shown = len.toFloat() else advance()
        return true
    }
}
