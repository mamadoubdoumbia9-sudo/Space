package com.ateliermareebasse.cartographie.core.screens

import com.ateliermareebasse.cartographie.core.data.Content
import com.ateliermareebasse.cartographie.core.data.ContentLoader
import com.ateliermareebasse.cartographie.core.engine.Game
import com.ateliermareebasse.cartographie.core.engine.Screen
import com.ateliermareebasse.cartographie.core.engine.Settings
import com.ateliermareebasse.cartographie.core.engine.Ui
import com.ateliermareebasse.cartographie.core.platform.Align
import com.ateliermareebasse.cartographie.core.platform.Colors
import com.ateliermareebasse.cartographie.core.platform.Font
import com.ateliermareebasse.cartographie.core.platform.Input
import com.ateliermareebasse.cartographie.core.state.SaveMeta
import kotlin.math.min
import kotlin.math.sin

/** Base commune : liste de boutons + gestion du tap. */
abstract class MenuScreen(game: Game) : Screen(game) {
    protected val ui get() = game.ui
    protected val p get() = game.painter
    protected val btns = ArrayList<Ui.Btn>()
    protected var pressed: String? = null
    protected var scroll = 0f
    protected var scrollMax = 0f
    private var downY = 0f; private var downScroll = 0f; private var dragged = false; private var downX = 0f
    protected var scrollable = false

    abstract fun onButton(id: String)

    override fun onInput(e: Input): Boolean {
        when (e) {
            is Input.Down -> { pressed = btns.firstOrNull { ui.hit(it, e.x, e.y) }?.id; downY = e.y; downX = e.x; downScroll = scroll; dragged = false; return true }
            is Input.Move -> {
                if (scrollable && kotlin.math.abs(e.y - downY) > 12 * ui.s) { dragged = true; pressed = null; scroll = (downScroll + (downY - e.y) * game.settings.sensitivity).coerceIn(0f, scrollMax) }
                return true
            }
            is Input.Up -> {
                val pr = pressed; pressed = null
                if (dragged) return true
                val b = btns.firstOrNull { ui.hit(it, e.x, e.y) }
                if (b != null && b.id == pr) { game.sfx("ui_tap"); onButton(b.id) } else onTapEmpty(e.x, e.y)
                return true
            }
            else -> return false
        }
    }
    open fun onTapEmpty(x: Float, y: Float) {}

    protected fun backButton(): Ui.Btn {
        val s = ui.s
        val b = Ui.Btn("back", 12 * s, ui.safeTop + 10 * s, 44 * s, 44 * s, "", icon = "back")
        btns.add(b)
        p.fillRoundRect(b.x, b.y, b.w, b.h, 10 * s, Colors.withAlpha(Colors.INK, 0.6f))
        p.strokeRoundRect(b.x, b.y, b.w, b.h, 10 * s, Colors.withAlpha(Colors.LAITON, 0.5f), 1f * s)
        ui.icon("back", b.x + b.w / 2, b.y + b.h / 2, 9 * s, Colors.PAPER)
        return b
    }

    protected fun backdrop(dark: Boolean = true) {
        if (dark) { p.gradientV(0f, 0f, p.width, p.height, 0xFF242A3A.toInt(), 0xFF12151E.toInt()) }
        else p.fillRect(0f, 0f, p.width, p.height, Colors.PAPER_DARK)
        if (game.settings.grain) p.image("art/ui/grain.png", 0f, 0f, p.width, p.height, 0.08f)
    }
}

// ───────────────────────────── CHARGEMENT ─────────────────────────────
class LoadingScreen(game: Game, val onDone: (Content) -> Unit) : Screen(game) {
    private var progress = 0f; private var label = ""; private var result: Content? = null; private var error: String? = null
    private var started = false; private var minTime = 1.2f
    override fun onEnter() {
        Thread {
            try {
                val c = ContentLoader(game.platform).load(game.settings.language) { f, l -> progress = f; label = l }
                result = c
            } catch (e: Throwable) { error = e.toString(); game.platform.log("chargement: $e") }
        }.start()
    }
    override fun update(dt: Float) {
        super.update(dt)
        val r = result
        if (r != null && time > minTime && !started) { started = true; onDone(r) }
    }
    override fun render() {
        val ui = game.ui; val p = game.painter; val s = ui.s
        p.gradientV(0f, 0f, p.width, p.height, 0xFF1C2233.toInt(), 0xFF0E1119.toInt())
        // ligne de côte qui se dessine
        val cx = p.width / 2; val cy = p.height * 0.42f
        val n = 60; val pts = FloatArray(n * 2)
        val f = min(1f, time / 1.6f)
        for (k in 0 until n) { val t = k / (n - 1f); pts[k * 2] = cx - 220 * s + 440 * s * t; pts[k * 2 + 1] = cy + (sin(t * 9f) * 14f + sin(t * 23f + 1) * 6f) * s * f }
        p.polyline(pts, Colors.withAlpha(Colors.PAPER, 0.7f), 1.6f * s)
        ui.engraved(game.content.str("app.title").takeIf { !it.startsWith("[") } ?: "La Cartographie des Absents", cx, cy - 40 * s, ui.font(30f), Colors.PAPER)
        p.text(label.ifEmpty { "…" }, cx, cy + 60 * s, ui.font(14f), Colors.withAlpha(Colors.PAPER_DARK, 0.8f), Font.HAND, Align.CENTER)
        ui.gauge(cx - 140 * s, cy + 80 * s, 280 * s, 4 * s, progress)
        error?.let { ui.paragraph(it, 30 * s, cy + 110 * s, p.width - 60 * s, ui.font(12f), Colors.GARANCE, Font.MONO) }
    }
}

// ───────────────────────────── TITRE ─────────────────────────────
class TitleScreen(game: Game) : MenuScreen(game) {
    private var hasSave = false
    private var confirmNew = false
    override fun onEnter() {
        hasSave = game.listSaves().any { !it.corrupt }
        game.music("track_01"); game.ambience("amb_mer_douce")
        if (game.settings.firstRun) { game.settings.firstRun = false; game.settings.save() }
    }
    override fun render() {
        val s = ui.s
        btns.clear()
        val bgOk = p.image("art/zones/z02_t02.jpg", -20 * s + sin(time * 0.1f) * 10 * s, -10 * s, p.width + 40 * s, p.height + 20 * s)
        if (!bgOk) backdrop()
        p.fillRect(0f, 0f, p.width, p.height, Colors.withAlpha(0xFF0E1426.toInt(), 0.45f))
        if (game.settings.vignette) p.image("art/ui/vignette.png", 0f, 0f, p.width, p.height, 0.7f)
        val cx = p.width / 2
        val ty = if (ui.portrait) p.height * 0.2f else p.height * 0.24f
        ui.engraved(game.str("app.title"), cx, ty, ui.font(if (ui.portrait) 30f else 40f), Colors.PAPER)
        p.text(game.str("app.chapter"), cx, ty + ui.font(24f), ui.font(17f), Colors.withAlpha(Colors.LAITON, 0.95f), Font.HAND, Align.CENTER)
        ui.ornament(cx, ty + ui.font(34f), 200 * s, Colors.PAPER)
        val bw = min(300 * s, p.width - 60 * s); val bh = 46 * s
        var y = if (ui.portrait) p.height * 0.42f else p.height * 0.47f
        val items = ArrayList<Pair<String, String>>()
        if (hasSave) items.add("continue" to game.str("menu.continue"))
        items.add("new" to game.str("menu.new"))
        if (hasSave) items.add("load" to game.str("menu.load"))
        items.add("settings" to game.str("menu.settings"))
        items.add("credits" to game.str("menu.credits"))
        items.add("quit" to game.str("menu.quit"))
        for ((id, label) in items) {
            val b = Ui.Btn(id, cx - bw / 2, y, bw, bh, label, gold = id == "continue")
            btns.add(b); ui.button(b, pressed == id); y += bh + 10 * s
        }
        p.text(game.str("app.version"), p.width - 12 * s, p.height - 10 * s, ui.font(11f), Colors.withAlpha(Colors.PAPER, 0.5f), Font.MONO, Align.RIGHT)
        p.text(game.str("app.studio"), 12 * s, p.height - 10 * s, ui.font(11f), Colors.withAlpha(Colors.PAPER, 0.5f), Font.HAND)
        if (confirmNew) renderConfirm()
    }
    private fun renderConfirm() {
        val s = ui.s
        ui.scrim(0.6f)
        val w = min(420 * s, p.width - 40 * s); val h = 170 * s
        val x = (p.width - w) / 2; val y = (p.height - h) / 2
        ui.paper(x, y, w, h)
        ui.paragraph(game.str("menu.confirm_new"), x + 20 * s, y + 18 * s, w - 40 * s, ui.font(16f), Colors.INK, Font.BODY, Align.CENTER)
        val b1 = Ui.Btn("confirm_yes", x + 20 * s, y + h - 60 * s, w / 2 - 30 * s, 42 * s, game.str("ui.yes"))
        val b2 = Ui.Btn("confirm_no", x + w / 2 + 10 * s, y + h - 60 * s, w / 2 - 30 * s, 42 * s, game.str("ui.no"))
        btns.clear(); btns.add(b1); btns.add(b2); ui.button(b1, pressed == b1.id); ui.button(b2, pressed == b2.id)
    }
    override fun onButton(id: String) {
        when (id) {
            "continue" -> game.fadeThen { if (!game.continueGame()) game.toast(game.str("toast.no_save")) }
            "new" -> if (hasSave) confirmNew = true else game.fadeThen { game.newGame() }
            "confirm_yes" -> { confirmNew = false; game.fadeThen { game.newGame() } }
            "confirm_no" -> confirmNew = false
            "load" -> game.push(SaveLoadScreen(game, load = true))
            "settings" -> game.push(SettingsScreen(game))
            "credits" -> game.push(CreditsScreen(game))
            "quit" -> game.platform.quit()
        }
    }
    override fun onBack(): Boolean { if (confirmNew) { confirmNew = false; return true }; return false }
}

// ───────────────────────────── PAUSE ─────────────────────────────
class PauseScreen(game: Game) : MenuScreen(game) {
    override val opaque = false
    override fun onEnter() { game.autosave() }
    override fun render() {
        val s = ui.s
        btns.clear()
        ui.scrim(0.62f)
        val cx = p.width / 2
        ui.engraved(game.str("pause.title"), cx, p.height * 0.18f, ui.font(30f), Colors.PAPER)
        val st = game.state
        val info = game.str("pause.info", game.formatDuration(st.playSeconds), game.cartoPercent(), st.pages.size, st.echoesSeen.size)
        p.text(info, cx, p.height * 0.18f + ui.font(22f), ui.font(13f), Colors.withAlpha(Colors.PAPER_DARK, 0.9f), Font.HAND, Align.CENTER)
        val bw = min(300 * s, p.width - 60 * s); val bh = 44 * s
        var y = p.height * 0.3f
        for ((id, label) in listOf("resume" to game.str("pause.resume"), "save" to game.str("pause.save"), "load" to game.str("pause.load"), "settings" to game.str("menu.settings"), "carnet" to game.str("pause.carnet"), "title" to game.str("pause.title_screen"))) {
            val b = Ui.Btn(id, cx - bw / 2, y, bw, bh, label, gold = id == "resume")
            btns.add(b); ui.button(b, pressed == id); y += bh + 9 * s
        }
    }
    override fun onButton(id: String) {
        when (id) {
            "resume" -> game.pop()
            "save" -> game.push(SaveLoadScreen(game, load = false))
            "load" -> game.push(SaveLoadScreen(game, load = true))
            "settings" -> game.push(SettingsScreen(game))
            "carnet" -> { game.pop(); game.openCarnet(null) }
            "title" -> game.fadeThen { game.quitToTitle() }
        }
    }
    override fun onBack(): Boolean { game.pop(); return true }
}

// ───────────────────────────── SAUVEGARDES ─────────────────────────────
class SaveLoadScreen(game: Game, val load: Boolean) : MenuScreen(game) {
    private var saves: List<SaveMeta> = emptyList()
    private var confirmDelete: String? = null
    override fun onEnter() { refresh() }
    private fun refresh() { saves = game.listSaves() }
    private fun meta(file: String) = saves.firstOrNull { it.file == file }
    private fun fmtDate(t: Long): String { if (t <= 0) return ""; val d = java.util.Date(t); val f = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.FRANCE); return f.format(d) }
    override fun render() {
        val s = ui.s
        btns.clear()
        backdrop()
        backButton()
        ui.engraved(game.str(if (load) "saves.load_title" else "saves.save_title"), p.width / 2, ui.safeTop + 40 * s, ui.font(26f), Colors.PAPER)
        val cardW = min(560 * s, p.width - 40 * s); val cardH = 74 * s
        var y = ui.safeTop + 70 * s
        val entries = ArrayList<Pair<String, String>>()
        for (i in 1..3) entries.add("slot_$i.sav" to game.str("saves.slot", i))
        if (load) { entries.add("auto.sav" to game.str("save.auto")); entries.add("checkpoint.sav" to game.str("save.checkpoint")) }
        for ((file, label) in entries) {
            val m = meta(file)
            val x = (p.width - cardW) / 2
            ui.paper(x, y, cardW, cardH, 0.95f)
            p.text(label, x + 16 * s, y + 24 * s, ui.font(16f), Colors.INK, Font.TITLE)
            val desc = when {
                m == null -> game.str("saves.empty")
                m.corrupt -> game.str("saves.corrupt")
                else -> game.str("saves.desc", game.content.zones[m.zone]?.name ?: m.zone, game.str("act.$${m.act}").takeIf { !it.startsWith("[") } ?: game.str("act.${m.act}"), m.percent, game.formatDuration(m.playSeconds), fmtDate(m.savedAt))
            }
            ui.paragraph(desc, x + 16 * s, y + 32 * s, cardW - 150 * s, ui.font(12.5f), Colors.INK_SOFT, Font.HAND, maxLines = 2)
            val canAct = if (load) (m != null && !m.corrupt) else true
            val b = Ui.Btn("act:$file", x + cardW - 120 * s, y + 16 * s, 104 * s, 40 * s, game.str(if (load) "saves.load" else "saves.save"), enabled = canAct, small = true)
            btns.add(b); ui.button(b, pressed == b.id)
            if (m != null && !file.startsWith("auto") ) {
                val d = Ui.Btn("del:$file", x + cardW - 24 * s, y + 4 * s, 20 * s, 20 * s, "", icon = "close")
                btns.add(d); ui.icon("close", d.x + 10 * s, d.y + 10 * s, 5 * s, Colors.withAlpha(Colors.GARANCE, 0.8f))
            }
            y += cardH + 10 * s
        }
        confirmDelete?.let { file ->
            ui.scrim(0.5f)
            val w = min(400 * s, p.width - 40 * s); val h = 150 * s; val x = (p.width - w) / 2; val yy = (p.height - h) / 2
            ui.paper(x, yy, w, h)
            ui.paragraph(game.str("saves.confirm_delete"), x + 20 * s, yy + 18 * s, w - 40 * s, ui.font(15f), Colors.INK, Font.BODY, Align.CENTER)
            val b1 = Ui.Btn("delyes", x + 20 * s, yy + h - 58 * s, w / 2 - 30 * s, 42 * s, game.str("ui.yes")); val b2 = Ui.Btn("delno", x + w / 2 + 10 * s, yy + h - 58 * s, w / 2 - 30 * s, 42 * s, game.str("ui.no"))
            btns.clear(); btns.add(b1); btns.add(b2); ui.button(b1, pressed == b1.id); ui.button(b2, pressed == b2.id)
        }
    }
    override fun onButton(id: String) {
        when {
            id == "back" -> game.pop()
            id == "delyes" -> { confirmDelete?.let { game.deleteSave(it) }; confirmDelete = null; refresh() }
            id == "delno" -> confirmDelete = null
            id.startsWith("del:") -> confirmDelete = id.substring(4)
            id.startsWith("act:") -> {
                val file = id.substring(4)
                if (load) game.fadeThen { if (!game.loadSave(file)) game.toast(game.str("saves.corrupt")) }
                else { val n = file.removePrefix("slot_").removeSuffix(".sav").toIntOrNull() ?: 1; game.saveSlot(n); refresh() }
            }
        }
    }
    override fun onBack(): Boolean { if (confirmDelete != null) { confirmDelete = null; return true }; game.pop(); return true }
}

// ───────────────────────────── OPTIONS ─────────────────────────────
class SettingsScreen(game: Game) : MenuScreen(game) {
    private val set get() = game.settings
    private var tab = 0
    init { scrollable = true }
    override fun onExit() { set.save(); game.applyVolumes() }
    private fun row(y: Float, label: String, value: String, id: String, dec: Boolean = true, inc: Boolean = true): Float {
        val s = ui.s
        val x = (p.width - min(600 * s, p.width - 40 * s)) / 2; val w = min(600 * s, p.width - 40 * s)
        p.text(label, x + 10 * s, y + 26 * s, ui.font(15f), Colors.PAPER, Font.BODY)
        val bm = Ui.Btn("$id:-", x + w - 210 * s, y + 4 * s, 40 * s, 36 * s, "", enabled = dec, icon = "minus")
        val bp = Ui.Btn("$id:+", x + w - 50 * s, y + 4 * s, 40 * s, 36 * s, "", enabled = inc, icon = "plus")
        btns.add(bm); btns.add(bp)
        for (b in listOf(bm, bp)) { p.fillRoundRect(b.x, b.y, b.w, b.h, 8 * s, Colors.withAlpha(Colors.PAPER, if (b.enabled) 0.15f else 0.05f)); ui.icon(b.icon!!, b.x + b.w / 2, b.y + b.h / 2, 6 * s, Colors.withAlpha(Colors.PAPER, if (b.enabled) 1f else 0.3f)) }
        p.text(value, x + w - 110 * s, y + 26 * s, ui.font(14f), Colors.LAITON, Font.HAND, Align.CENTER)
        return y + 46 * s
    }
    private fun toggle(y: Float, label: String, v: Boolean, id: String): Float = row(y, label, game.str(if (v) "ui.on" else "ui.off"), id, dec = v, inc = !v)
    private fun pct(v: Float) = "${(v * 100).toInt()} %"
    override fun render() {
        val s = ui.s
        btns.clear()
        backdrop()
        backButton()
        ui.engraved(game.str("menu.settings"), p.width / 2, ui.safeTop + 40 * s, ui.font(26f), Colors.PAPER)
        // onglets
        val tabs = listOf(game.str("set.tab_graphics"), game.str("set.tab_audio"), game.str("set.tab_game"), game.str("set.tab_access"))
        val tw = min(150 * s, (p.width - 40 * s) / 4)
        val tx0 = (p.width - tw * 4) / 2
        tabs.forEachIndexed { i, t ->
            val b = Ui.Btn("tab$i", tx0 + i * tw, ui.safeTop + 62 * s, tw - 4 * s, 34 * s, t, small = true)
            btns.add(b)
            p.fillRoundRect(b.x, b.y, b.w, b.h, 6 * s, Colors.withAlpha(if (tab == i) Colors.LAITON else Colors.PAPER, if (tab == i) 0.85f else 0.12f))
            p.text(t, b.x + b.w / 2, b.y + 23 * s, ui.font(13f), if (tab == i) Colors.INK else Colors.PAPER, Font.BODY, Align.CENTER)
        }
        p.pushClip(0f, ui.safeTop + 104 * s, p.width, p.height - ui.safeTop - 104 * s)
        var y = ui.safeTop + 112 * s - scroll
        when (tab) {
            0 -> {
                y = row(y, game.str("set.quality"), game.str("set.q_${set.quality.name}"), "quality", set.quality.ordinal > 0, set.quality.ordinal < 3)
                y = toggle(y, game.str("set.vignette_grain"), set.quality >= Settings.Quality.HIGH, "qgrain")
                y = toggle(y, game.str("set.reduce_motion"), set.reduceMotion, "motion")
                y = row(y, game.str("set.ui_scale"), pct(set.uiScale), "uiscale", set.uiScale > 0.8f, set.uiScale < 1.3f)
                ui.paragraph(game.str("set.quality_help"), (p.width - min(600 * s, p.width - 40 * s)) / 2 + 10 * s, y + 6 * s, min(600 * s, p.width - 40 * s) - 20 * s, ui.font(12f), Colors.PAPER_DARK, Font.HAND)
                y += 60 * s
            }
            1 -> {
                y = row(y, game.str("set.master"), pct(set.master), "master", set.master > 0f, set.master < 1f)
                y = row(y, game.str("set.music"), pct(set.music), "music", set.music > 0f, set.music < 1f)
                y = row(y, game.str("set.ambience"), pct(set.ambience), "ambience", set.ambience > 0f, set.ambience < 1f)
                y = row(y, game.str("set.sfx"), pct(set.sfx), "sfx", set.sfx > 0f, set.sfx < 1f)
                y = toggle(y, game.str("set.vibration"), set.vibration, "vib")
            }
            2 -> {
                y = row(y, game.str("set.language"), if (set.language == "fr") "Français" else "English", "lang")
                y = row(y, game.str("set.sensitivity"), pct(set.sensitivity), "sens", set.sensitivity > 0.5f, set.sensitivity < 2f)
                y = row(y, game.str("set.hints"), game.str("set.hint_${set.hintMode.name}"), "hint", set.hintMode.ordinal > 0, set.hintMode.ordinal < 2)
                y = toggle(y, game.str("set.autosave"), set.autosave, "autosave")
                y = toggle(y, game.str("set.left_handed"), set.leftHanded, "left")
                ui.paragraph(game.str("set.hints_help"), (p.width - min(600 * s, p.width - 40 * s)) / 2 + 10 * s, y + 6 * s, min(600 * s, p.width - 40 * s) - 20 * s, ui.font(12f), Colors.PAPER_DARK, Font.HAND)
                y += 70 * s
            }
            3 -> {
                y = row(y, game.str("set.text_size"), pct(set.textScale), "text", set.textScale > 0.8f, set.textScale < 1.5f)
                y = toggle(y, game.str("set.high_contrast"), set.highContrast, "contrast")
                y = toggle(y, game.str("set.subtitles"), set.subtitles, "subs")
                ui.paragraph(game.str("set.access_help"), (p.width - min(600 * s, p.width - 40 * s)) / 2 + 10 * s, y + 6 * s, min(600 * s, p.width - 40 * s) - 20 * s, ui.font(12f), Colors.PAPER_DARK, Font.HAND)
                y += 70 * s
            }
        }
        p.popClip()
        scrollMax = (y + scroll - p.height + 20 * s).coerceAtLeast(0f)
    }
    override fun onButton(id: String) {
        if (id == "back") { game.pop(); return }
        if (id.startsWith("tab")) { tab = id.substring(3).toInt(); scroll = 0f; return }
        val k = id.substringBefore(':'); val d = if (id.endsWith("+")) 1 else -1
        fun step(v: Float, st: Float, lo: Float, hi: Float) = (v + d * st).coerceIn(lo, hi)
        when (k) {
            "quality" -> set.quality = Settings.Quality.values()[(set.quality.ordinal + d).coerceIn(0, 3)]
            "qgrain" -> set.quality = if (d > 0) Settings.Quality.HIGH else Settings.Quality.MEDIUM
            "motion" -> set.reduceMotion = d > 0
            "uiscale" -> set.uiScale = step(set.uiScale, 0.1f, 0.8f, 1.3f)
            "master" -> { set.master = step(set.master, 0.1f, 0f, 1f); game.applyVolumes() }
            "music" -> { set.music = step(set.music, 0.1f, 0f, 1f); game.applyVolumes() }
            "ambience" -> { set.ambience = step(set.ambience, 0.1f, 0f, 1f); game.applyVolumes() }
            "sfx" -> { set.sfx = step(set.sfx, 0.1f, 0f, 1f); game.applyVolumes(); game.sfx("cloche_03") }
            "vib" -> { set.vibration = d > 0; if (set.vibration) game.platform.vibrate(30) }
            "lang" -> { val nl = if (set.language == "fr") "en" else "fr"; game.reloadContent(nl) { game.toast(game.str("toast.lang")) } }
            "sens" -> set.sensitivity = step(set.sensitivity, 0.25f, 0.5f, 2f)
            "hint" -> set.hintMode = Settings.HintMode.values()[(set.hintMode.ordinal + d).coerceIn(0, 2)]
            "autosave" -> set.autosave = d > 0
            "left" -> set.leftHanded = d > 0
            "text" -> set.textScale = step(set.textScale, 0.1f, 0.8f, 1.5f)
            "contrast" -> set.highContrast = d > 0
            "subs" -> set.subtitles = d > 0
        }
        set.save()
    }
    override fun onBack(): Boolean { game.pop(); return true }
}

// ───────────────────────────── TUTORIEL ─────────────────────────────
class TutorialScreen(game: Game, val id: String, val lines: List<String>) : MenuScreen(game) {
    override val opaque = false
    override fun render() {
        val s = ui.s
        btns.clear()
        ui.scrim(0.45f)
        val fs = ui.font(15f)
        val w = min(520 * s, p.width - 40 * s)
        val title = lines.firstOrNull() ?: ""
        val body = lines.drop(1).joinToString("\n")
        val h = ui.paragraphHeight(body, w - 40 * s, fs, Font.BODY, 1.35f) + fs * 3.4f + 70 * s
        val x = (p.width - w) / 2; val y = (p.height - h) / 2
        ui.paper(x, y, w, h)
        p.text(title, x + w / 2, y + 16 * s + fs * 1.3f, fs * 1.3f, Colors.INK, Font.TITLE, Align.CENTER)
        ui.ornament(x + w / 2, y + 22 * s + fs * 2f, 120 * s)
        ui.paragraph(body, x + 20 * s, y + 30 * s + fs * 2.4f, w - 40 * s, fs, Colors.INK, Font.BODY, lineHeight = 1.35f)
        val b = Ui.Btn("ok", x + w / 2 - 70 * s, y + h - 52 * s, 140 * s, 40 * s, game.str("ui.understood"))
        btns.add(b); ui.button(b, pressed == "ok")
    }
    override fun onButton(id: String) { game.pop() }
    override fun onBack(): Boolean { game.pop(); return true }
}

// ───────────────────────────── CARTON D'ACTE ─────────────────────────────
class ActCardScreen(game: Game, val act: Int) : Screen(game) {
    override val opaque = false
    private val dur = 4.2f
    override fun onEnter() { game.sfx("harpe_08") }
    override fun update(dt: Float) { super.update(dt); if (time > dur) game.pop() }
    override fun render() {
        val ui = game.ui; val p = game.painter
        val a = min(1f, time * 2f) * min(1f, (dur - time) * 1.5f).coerceIn(0f, 1f)
        p.fillRect(0f, 0f, p.width, p.height, Colors.withAlpha(Colors.BLACK, 0.85f * a))
        ui.engraved(game.str("act.card_$act"), p.width / 2, p.height * 0.46f, ui.font(30f), Colors.PAPER, a)
        p.text(game.str("act.$act"), p.width / 2, p.height * 0.46f + ui.font(30f), ui.font(16f), Colors.withAlpha(Colors.LAITON, a), Font.HAND, Align.CENTER)
    }
    override fun onInput(e: Input): Boolean { if (e is Input.Up && time > 1f) game.pop(); return true }
}

// ───────────────────────────── FEUILLET « CE QUE LE MONDE A RETENU DE TOI » ─────────────────────────────
class RetentissementScreen(game: Game) : MenuScreen(game) {
    private val lines by lazy { game.hooks.retentissementLines() }
    override fun onEnter() { game.music("track_16") }
    override fun render() {
        val s = ui.s
        btns.clear()
        backdrop()
        val w = min(620 * s, p.width - 40 * s)
        val fs = ui.font(16f)
        val bodyH = lines.sumOf { ui.paragraphHeight(it, w - 60 * s, fs, Font.HAND, 1.4f).toDouble() }.toFloat() + lines.size * 8 * s
        val h = bodyH + 140 * s
        val x = (p.width - w) / 2; val y = ((p.height - h) / 2).coerceAtLeast(ui.safeTop + 10 * s)
        ui.paper(x, y, w, h)
        p.text(game.str("retent.title"), x + w / 2, y + 30 * s, ui.font(20f), Colors.INK, Font.TITLE, Align.CENTER)
        ui.ornament(x + w / 2, y + 44 * s, 160 * s)
        var yy = y + 62 * s
        lines.forEachIndexed { i, l ->
            val a = min(1f, (time - i * 0.5f) * 1.5f).coerceIn(0f, 1f)
            yy += ui.paragraph(l, x + 30 * s, yy, w - 60 * s, fs, Colors.withAlpha(Colors.INK, a), Font.HAND, lineHeight = 1.4f) + 8 * s
        }
        val b = Ui.Btn("ok", x + w / 2 - 70 * s, y + h - 56 * s, 140 * s, 40 * s, game.str("ui.continue"))
        btns.add(b); ui.button(b, pressed == "ok")
    }
    override fun onButton(id: String) { game.pop() }
    override fun onBack(): Boolean { game.pop(); return true }
}

// ───────────────────────────── CRÉDITS ─────────────────────────────
class CreditsScreen(game: Game) : MenuScreen(game) {
    private var auto = 0f
    override fun onEnter() { game.music("track_24"); scrollable = true }
    override fun render() {
        val s = ui.s
        btns.clear()
        backdrop()
        backButton()
        auto += 0.016f * 22f * s
        val fsH = ui.font(20f); val fsB = ui.font(15f)
        var y = p.height - auto - scroll
        p.pushClip(0f, ui.safeTop + 60 * s, p.width, p.height - ui.safeTop - 60 * s)
        for (c in game.content.credits) {
            when (c.kind) {
                "title" -> { ui.engraved(c.text, p.width / 2, y, ui.font(28f), Colors.PAPER); y += ui.font(28f) * 1.6f }
                "h" -> { y += fsH; p.text(c.text, p.width / 2, y, fsH, Colors.LAITON, Font.TITLE, Align.CENTER); y += fsH * 1.5f }
                "p" -> { y += ui.paragraph(c.text, p.width * 0.15f, y, p.width * 0.7f, fsB, Colors.PAPER, Font.BODY, Align.CENTER, 1.35f) + fsB * 0.6f }
                "hand" -> { y += ui.paragraph(c.text, p.width * 0.15f, y, p.width * 0.7f, fsB, Colors.PAPER_DARK, Font.HAND, Align.CENTER, 1.35f) + fsB * 0.6f }
                "gap" -> y += fsB * 2f
            }
        }
        p.popClip()
        if (y < ui.safeTop) { auto = 0f; scroll = 0f }
        scrollMax = 100000f
    }
    override fun onButton(id: String) { if (id == "back") game.pop() }
    override fun onBack(): Boolean { game.pop(); return true }
}
