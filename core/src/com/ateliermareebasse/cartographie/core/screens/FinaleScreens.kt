package com.ateliermareebasse.cartographie.core.screens

import com.ateliermareebasse.cartographie.core.engine.Game
import com.ateliermareebasse.cartographie.core.engine.Screen
import com.ateliermareebasse.cartographie.core.engine.Ui
import com.ateliermareebasse.cartographie.core.platform.Align
import com.ateliermareebasse.cartographie.core.platform.Colors
import com.ateliermareebasse.cartographie.core.platform.Font
import com.ateliermareebasse.cartographie.core.platform.Input
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * CIN-07 / BLOC 14 : la lettre se déplie en onze plis, au doigt, sans timer.
 * Première lecture : aucune interruption possible ; le bouton « se lever » n'apparaît qu'après 10 s sur le dernier pli.
 */
class LetterScreen(game: Game) : Screen(game) {
    private val plis = game.content.letter.filter { it.first.startsWith("PLI") }
    private val pps = game.content.letter.firstOrNull { !it.first.startsWith("PLI") }
    private val firstRead = !game.state.letterRead
    private var idx = 0
    private var turn = 0f          // 0..1 animation de tournage
    private var turning = false
    private var scroll = 0f
    private var scrollMax = 0f
    private var downY = 0f; private var downScroll = 0f; private var downX = 0f; private var dragged = false
    private var lastPliTime = 0f
    private var ribbonPhase = if ("ruban_noue" in game.state.flags && firstRead) 0f else 2f   // dénouer le ruban avant le premier pli
    private var breathing = 0f
    private val stainVariant = (game.state.createdAt % 4).toInt()
    private val ui get() = game.ui
    private val p get() = game.painter
    private var btn: Ui.Btn? = null
    private var pressed = false

    override fun onEnter() {
        game.platform.keepScreenOn(true)
        game.music("track_lettre")
        game.ambience(null)
        game.sfx("pli_depli_1")
    }
    override fun onExit() {
        game.platform.keepScreenOn(false)
        if (firstRead) {
            val st = game.state
            st.letterRead = true; st.letterOpenedAt = game.platform.nowMillis()
            st.flags.add("lettre_lue"); st.flags.add("velune")
            st.clarte = (st.clarte + 5).coerceAtMost(100)
            st.inc("ng_letters_read")
            game.saveTo("checkpoint.sav", game.str("save.letter"))
            game.music(null)
            // retour à la terrasse du phare : CIN-08b puis l'épilogue
            game.cinematic("CIN-08b") { game.state.flags.add("retour_terrasse"); game.travel("z18", "t01") }
        }
    }

    private fun totalPlis() = plis.size + (if (game.state.ngPlus && pps != null) 1 else 0)
    private fun pli(i: Int) = if (i < plis.size) plis[i] else pps!!

    override fun update(dt: Float) {
        super.update(dt)
        lastPliTime += dt
        breathing += dt * (if (lastPliTime > 20f) 0.6f else 1f)
        if (ribbonPhase < 2f && ribbonPhase > 0f) ribbonPhase = min(2f, ribbonPhase + dt)
        if (turning) { turn += dt * 3.2f; if (turn >= 1f) { turning = false; turn = 0f; idx++; scroll = 0f; lastPliTime = 0f; game.sfx("pli_depli_${(idx % 4) + 1}") } }
    }

    override fun render() {
        val s = ui.s
        p.gradientV(0f, 0f, p.width, p.height, 0xFF0B1224.toInt(), 0xFF05070E.toInt())
        // lueur de la lampe-tempête
        p.gradientRadial(p.width * 0.5f, p.height * 0.35f, p.height * 0.9f, Colors.withAlpha(0xFFFFE0A0.toInt(), 0.10f + 0.02f * sin(breathing * 1.2f)), Colors.TRANSPARENT)
        if (ribbonPhase < 2f) { renderRibbon(); return }
        val w = min(700 * s, p.width - 36 * s)
        val h = p.height - 30 * s - ui.safeTop
        val x = (p.width - w) / 2; val y = ui.safeTop + 15 * s
        val (title, paras) = pli(idx)
        // feuille (papier léger, capitales encre noire profonde)
        p.fillRoundRect(x + 4 * s, y + 5 * s, w, h, 3 * s, Colors.withAlpha(Colors.BLACK, 0.5f))
        p.fillRoundRect(x, y, w, h, 3 * s, 0xFFF4ECDC.toInt())
        if (game.settings.grain) p.image("art/ui/grain.png", x, y, w, h, 0.16f)
        // pli en cours de tournage (page qui se lève)
        if (turning) { val tw = w * (1f - turn); p.fillRect(x + w - tw, y, tw, h, Colors.withAlpha(0xFFE4D9C2.toInt(), 0.9f)); p.line(x + w - tw, y, x + w - tw, y + h, Colors.withAlpha(Colors.INK, 0.25f), 1f) }
        val fs = ui.font(15.5f)
        val tx = x + 34 * s; val tw = w - 68 * s
        val body = paras.joinToString("\n\n")
        val isCaps = true
        val text = if (isCaps) body.uppercase() else body
        val bodyH = ui.paragraphHeight(text, tw, fs, Font.TITLE, 1.55f)
        scrollMax = (bodyH + 120 * s - h).coerceAtLeast(0f)
        p.pushClip(x, y + 10 * s, w, h - 20 * s)
        var yy = y + 40 * s - scroll
        p.text(if (idx < plis.size) game.str("letter.pli", idx + 1, plis.size) else game.str("letter.pps"), x + w / 2, yy, ui.font(11f), Colors.withAlpha(Colors.INK, 0.45f), Font.MONO, Align.CENTER)
        yy += 24 * s
        val lines = ui.wrap(text, fs, tw, Font.TITLE)
        for ((li, l) in lines.withIndex()) {
            val la = min(1f, (lastPliTime - li * 0.06f) * 2.5f).coerceIn(0f, 1f)
            p.text(l, tx, yy + fs, fs, Colors.withAlpha(0xFF141414.toInt(), la), Font.TITLE)
            // Pli 10 : la tache d'eau de mer sur « pour toujours »
            if (idx == 9 && l.contains("POUR TOUJOURS")) {
                val i0 = l.indexOf("POUR TOUJOURS"); val px0 = tx + p.measure(l.substring(0, i0), fs, Font.TITLE); val pw = p.measure("POUR TOUJOURS", fs, Font.TITLE)
                val ox = floatArrayOf(0f, 6f, -5f, 3f)[stainVariant] * s
                p.fillCircle(px0 + pw / 2 + ox, yy + fs * 0.6f, pw * 0.62f, Colors.withAlpha(0xFF9FB3C8.toInt(), 0.35f))
                p.fillCircle(px0 + pw / 2 + ox + 10 * s, yy + fs * 1.4f + 6 * s, pw * 0.25f, Colors.withAlpha(0xFF9FB3C8.toInt(), 0.22f))
                p.strokeCircle(px0 + pw / 2 + ox, yy + fs * 0.6f, pw * 0.62f, Colors.withAlpha(0xFF6F8AA8.toInt(), 0.35f), 1f * s)
            }
            yy += fs * 1.55f
        }
        p.popClip()
        // coin du pli à tourner
        val last = idx >= totalPlis() - 1
        if (!last) {
            val cx = x + w - 34 * s; val cy = y + h - 34 * s
            p.fillPolygon(floatArrayOf(x + w, y + h - 70 * s, x + w, y + h, x + w - 70 * s, y + h), Colors.withAlpha(0xFFDDD0B8.toInt(), 0.95f))
            p.line(x + w, y + h - 70 * s, x + w - 70 * s, y + h, Colors.withAlpha(Colors.INK, 0.35f), 1f * s)
            val fh = ui.font(11f)
            p.text(game.str("letter.turn"), cx - 8 * s, cy + 12 * s, fh, Colors.withAlpha(Colors.INK, 0.5f + 0.3f * sin(time * 3)), Font.HAND, Align.RIGHT)
        } else {
            btn = null
            if (lastPliTime > 10f || !firstRead) {
                val b = Ui.Btn("up", x + w / 2 - 80 * s, y + h - 56 * s, 160 * s, 40 * s, game.str(if (firstRead) "letter.get_up" else "ui.close"), small = true)
                btn = b; ui.button(b, pressed)
            } else {
                p.text(game.str("letter.stay"), x + w / 2, y + h - 30 * s, ui.font(12f), Colors.withAlpha(Colors.INK, 0.4f), Font.HAND, Align.CENTER)
            }
        }
    }

    private fun renderRibbon() {
        val s = ui.s
        val f = (ribbonPhase / 2f).coerceIn(0f, 1f)
        val cx = p.width / 2; val cy = p.height / 2
        val fs = ui.font(16f)
        p.text(game.str("letter.ribbon"), cx, cy - 80 * s, fs, Colors.withAlpha(Colors.PAPER, 0.85f), Font.HAND, Align.CENTER)
        // ruban garance qui se dénoue (deux boucles qui s'écartent)
        val spread = f * 120 * s
        p.polyline(floatArrayOf(cx - 40 * s - spread, cy + 30 * s, cx - 10 * s, cy - 10 * s, cx + 10 * s, cy + 10 * s, cx + 40 * s + spread, cy - 30 * s), Colors.GARANCE, 8f * s)
        p.polyline(floatArrayOf(cx - 40 * s - spread, cy - 30 * s, cx - 10 * s, cy + 10 * s, cx + 10 * s, cy - 10 * s, cx + 40 * s + spread, cy + 30 * s), Colors.withAlpha(Colors.GARANCE, 0.8f), 8f * s)
        if (ribbonPhase == 0f) p.text(game.str("letter.ribbon_hint"), cx, cy + 90 * s, ui.font(12f), Colors.withAlpha(Colors.PAPER, 0.5f + 0.3f * sin(time * 3)), Font.HAND, Align.CENTER)
    }

    override fun onInput(e: Input): Boolean {
        val s = ui.s
        when (e) {
            is Input.Down -> { downY = e.y; downX = e.x; downScroll = scroll; dragged = false; pressed = btn?.let { ui.hit(it, e.x, e.y) } == true }
            is Input.Move -> { if (abs(e.y - downY) > 12 * s) { dragged = true; scroll = (downScroll + (downY - e.y)).coerceIn(0f, scrollMax) } }
            is Input.Up -> {
                if (ribbonPhase == 0f) { ribbonPhase = 0.01f; game.sfx("ruban_denoue"); return true }
                if (ribbonPhase < 2f) return true
                if (pressed && btn != null && ui.hit(btn!!, e.x, e.y)) { pressed = false; game.pop(); return true }
                pressed = false
                if (dragged) return true
                val last = idx >= totalPlis() - 1
                if (!last && !turning) {
                    val w = min(700 * s, p.width - 36 * s); val x = (p.width - w) / 2
                    val h = p.height - 30 * s - ui.safeTop; val y = ui.safeTop + 15 * s
                    if (e.x > x + w - 110 * s && e.y > y + h - 110 * s) { turning = true; turn = 0f }
                    else if (scrollMax > 0 && scroll < scrollMax) { scroll = min(scrollMax, scroll + p.height * 0.5f) }
                    else { turning = true; turn = 0f }
                }
            }
            else -> {}
        }
        return true
    }
    override fun onBack(): Boolean { if (!firstRead) game.pop(); return true }
}

/**
 * SQ-20 / D-06 : la réponse. Quatre options (3 + NG+) ; l'écriture propose trois modèles d'encre ou l'écriture libre.
 */
class EpilogueScreen(game: Game) : MenuScreen(game) {
    private var phase = 0     // 0 choix ; 1 modèles ; 2 texte affiché ; 3 dépôt
    private var model = ""
    private var text = ""
    private val st get() = game.state
    init { scrollable = true }
    override fun onEnter() { game.music("track_22"); game.tutorial("epilogue") }

    override fun render() {
        val s = ui.s
        btns.clear()
        p.gradientV(0f, 0f, p.width, p.height, 0xFF0C1530.toInt(), 0xFF05070E.toInt())
        val w = min(700 * s, p.width - 36 * s)
        val x = (p.width - w) / 2
        when (phase) {
            0 -> {
                var y = ui.safeTop + 30 * s
                ui.engraved(game.str("epi.title"), p.width / 2, y + ui.font(24f), ui.font(24f), Colors.PAPER); y += ui.font(24f) * 2f
                y += ui.paragraph(game.str("epi.intro"), x, y, w, ui.font(15f), Colors.PAPER_DARK, Font.HAND, Align.CENTER) + 20 * s
                val opts = ArrayList<Pair<String, String>>()
                opts.add("lettre" to game.str("epi.opt_letter"))
                opts.add("poursuite" to game.str("epi.opt_pursuit"))
                if (st.has("boussole_esteban")) opts.add("boussole" to game.str("epi.opt_compass"))
                if (st.ngPlus && game.cartoPercent() >= 100) opts.add("carte" to game.str("epi.opt_map"))
                for ((id, label) in opts) {
                    val hh = ui.paragraphHeight(label, w - 60 * s, ui.font(15f), Font.BODY, 1.25f) + 22 * s
                    val b = Ui.Btn("opt:$id", x, y - scroll, w, hh, label, gold = true)
                    btns.add(b); ui.button(b, pressed == b.id); y += hh + 10 * s
                }
                scrollMax = (y - p.height + 20 * s).coerceAtLeast(0f)
            }
            1 -> {
                var y = ui.safeTop + 30 * s
                ui.engraved(game.str("epi.write_title"), p.width / 2, y + ui.font(22f), ui.font(22f), Colors.PAPER); y += ui.font(22f) * 2f
                for (m in listOf("A", "B", "C")) {
                    val lines = game.content.epilogueModels[m] ?: continue
                    val label = lines.firstOrNull() ?: m
                    val b = Ui.Btn("model:$m", x, y - scroll, w, 48 * s, label)
                    btns.add(b); ui.button(b, pressed == b.id); y += 58 * s
                }
                val bf = Ui.Btn("free", x, y - scroll, w, 48 * s, game.str("epi.free"), gold = true); btns.add(bf); ui.button(bf, pressed == "free"); y += 58 * s
                val bb = Ui.Btn("backchoice", x, y - scroll, w, 40 * s, game.str("ui.back"), small = true); btns.add(bb); ui.button(bb, pressed == "backchoice")
            }
            2 -> {
                val fs = ui.font(16f)
                val h = p.height - ui.safeTop - 30 * s
                val y = ui.safeTop + 15 * s
                ui.paper(x, y, w, h, 0.97f)
                p.text(game.str("epi.my_letter"), x + w / 2, y + 28 * s, ui.font(13f), Colors.INK_SOFT, Font.MONO, Align.CENTER)
                p.pushClip(x, y + 40 * s, w, h - 110 * s)
                val bh = ui.paragraph(text, x + 30 * s, y + 50 * s - scroll, w - 60 * s, fs, Colors.INK, Font.HAND, lineHeight = 1.5f)
                p.popClip()
                scrollMax = (bh - (h - 120 * s)).coerceAtLeast(0f)
                val b1 = Ui.Btn("deposit", x + w / 2 - 150 * s, y + h - 56 * s, 140 * s, 40 * s, game.str("epi.deposit"), gold = true, small = true)
                val b2 = Ui.Btn("rewrite", x + w / 2 + 10 * s, y + h - 56 * s, 140 * s, 40 * s, game.str("epi.rewrite"), small = true)
                btns.add(b1); btns.add(b2); ui.button(b1, pressed == "deposit"); ui.button(b2, pressed == "rewrite")
            }
        }
    }

    override fun onButton(id: String) {
        when {
            id.startsWith("opt:") -> {
                val o = id.substring(4)
                if (o == "lettre") { phase = 1; scroll = 0f }
                else finish(o)
            }
            id.startsWith("model:") -> { model = id.substring(6); text = (game.content.epilogueModels[model] ?: emptyList()).drop(1).joinToString("\n\n"); phase = 2; scroll = 0f; game.sfx("quill_scratch_03") }
            id == "free" -> game.platform.requestTextInput(game.str("epi.free_prompt"), st.responseText, 1200) { t -> if (!t.isNullOrBlank()) { model = "libre"; text = t.trim(); phase = 2; scroll = 0f; game.sfx("quill_scratch_05") } }
            id == "backchoice" -> { phase = 0; scroll = 0f }
            id == "rewrite" -> { phase = 1; scroll = 0f }
            id == "deposit" -> { st.responseText = text; st.responseModel = model; finish("lettre") }
        }
    }

    private fun finish(ending: String) {
        st.ending = ending
        st.decisions["D-06"] = ending
        st.flags.add("reponse_$ending")
        game.emotionalCooldown = 30f
        game.pop()
        val scene = "depot_reponse_$ending"
        game.runScene(scene)
        game.cinematic("CIN-08") { game.endChapter() }
    }
    override fun onBack(): Boolean { if (phase > 0) { phase = 0; scroll = 0f }; return true }
}

/** Carton « FIN DU CHAPITRE 1 » + proposition NG+ / souvenir libre. */
class EndCardScreen(game: Game) : MenuScreen(game) {
    private val st get() = game.state
    override fun onEnter() {
        st.chapterDone = true; st.seq = 20
        st.flags.add("chapitre_1_fini")
        game.saveTo("slot_${game.settings.lastSlot}.sav", game.str("save.end"))
        game.saveTo("checkpoint.sav", game.str("save.end"))
        game.music("track_23")
    }
    override fun render() {
        val s = ui.s
        btns.clear()
        p.gradientV(0f, 0f, p.width, p.height, 0xFF0B1224.toInt(), 0xFF05070E.toInt())
        val a = min(1f, time * 0.8f)
        ui.engraved(game.str("end.title"), p.width / 2, p.height * 0.28f, ui.font(32f), Colors.PAPER, a)
        p.text(game.str("end.subtitle"), p.width / 2, p.height * 0.28f + ui.font(26f), ui.font(16f), Colors.withAlpha(Colors.LAITON, a), Font.HAND, Align.CENTER)
        val fe = game.str("end.fe_${st.ending.ifEmpty { "poursuite" }}")
        p.text(fe, p.width / 2, p.height * 0.28f + ui.font(48f), ui.font(14f), Colors.withAlpha(Colors.PAPER_DARK, a), Font.HAND, Align.CENTER)
        val stats = game.str("end.stats", game.cartoPercent(), st.pages.size, st.echoesSeen.size, st.secrets.size, st.clarte, game.formatDuration(st.playSeconds))
        ui.paragraph(stats, p.width * 0.15f, p.height * 0.28f + ui.font(64f), p.width * 0.7f, ui.font(13f), Colors.withAlpha(Colors.PAPER_DARK, a), Font.MONO, Align.CENTER)
        if (time > 3f) {
            val bw = min(320 * s, p.width - 60 * s); var y = p.height * 0.62f
            for ((id, label) in listOf("souvenir" to game.str("end.free_memory"), "ng" to game.str("end.ng_plus"), "credits" to game.str("menu.credits"), "title" to game.str("pause.title_screen"))) {
                val b = Ui.Btn(id, p.width / 2 - bw / 2, y, bw, 42 * s, label, gold = id == "ng"); btns.add(b); ui.button(b, pressed == id); y += 50 * s
            }
        }
    }
    override fun onButton(id: String) {
        when (id) {
            "souvenir" -> { game.pop(); game.toast(game.str("end.free_memory_toast")) }
            "ng" -> game.fadeThen { game.newGamePlus(st) }
            "credits" -> game.push(CreditsScreen(game))
            "title" -> game.fadeThen { game.quitToTitle() }
        }
    }
    override fun onBack(): Boolean = true
}
