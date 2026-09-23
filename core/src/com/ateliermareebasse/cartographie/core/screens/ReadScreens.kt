package com.ateliermareebasse.cartographie.core.screens

import com.ateliermareebasse.cartographie.core.data.BellDef
import com.ateliermareebasse.cartographie.core.data.CinematicDef
import com.ateliermareebasse.cartographie.core.data.EchoDef
import com.ateliermareebasse.cartographie.core.data.Line
import com.ateliermareebasse.cartographie.core.engine.Game
import com.ateliermareebasse.cartographie.core.engine.Screen
import com.ateliermareebasse.cartographie.core.engine.Ui
import com.ateliermareebasse.cartographie.core.platform.Align
import com.ateliermareebasse.cartographie.core.platform.Colors
import com.ateliermareebasse.cartographie.core.platform.Font
import com.ateliermareebasse.cartographie.core.platform.Input
import kotlin.math.min
import kotlin.math.sin

/** Feuille de papier plein écran (bornes, étiquettes, fiches, textes). */
open class PaperScreen(game: Game, val title: String, val body: String, val font: Font = Font.BODY) : MenuScreen(game) {
    override val opaque = false
    init { scrollable = true }
    override fun render() {
        val s = ui.s
        btns.clear()
        ui.scrim(0.55f)
        val w = min(640 * s, p.width - 30 * s)
        val fs = ui.font(16f)
        val bodyH = ui.paragraphHeight(body, w - 50 * s, fs, font, 1.42f)
        val h = min(p.height - 40 * s, bodyH + 110 * s)
        val x = (p.width - w) / 2; val y = (p.height - h) / 2
        ui.paper(x, y, w, h)
        p.text(title, x + w / 2, y + 30 * s, ui.font(18f), Colors.INK, Font.TITLE, Align.CENTER)
        ui.ornament(x + w / 2, y + 42 * s, 140 * s)
        p.pushClip(x, y + 52 * s, w, h - 100 * s)
        ui.paragraph(body, x + 25 * s, y + 58 * s - scroll, w - 50 * s, fs, Colors.INK, font, lineHeight = 1.42f)
        p.popClip()
        scrollMax = (bodyH - (h - 110 * s)).coerceAtLeast(0f)
        val b = Ui.Btn("ok", x + w / 2 - 60 * s, y + h - 44 * s, 120 * s, 34 * s, game.str("ui.close"), small = true)
        btns.add(b); ui.button(b, pressed == "ok")
    }
    override fun onButton(id: String) { game.pop() }
    override fun onBack(): Boolean { game.pop(); return true }
}

/** Page d'Esteban : feuille arrachée, minuscules, oiseaux en marge. */
class PageScreen(game: Game, val n: Int, val fresh: Boolean) : MenuScreen(game) {
    override val opaque = false
    private val page = game.content.pages[n]
    init { scrollable = true }
    override fun onEnter() { if (fresh) { game.sfx("page_turn_04"); game.haptic(20) } }
    override fun render() {
        val s = ui.s
        btns.clear()
        ui.scrim(0.6f)
        val pg = page
        val w = min(560 * s, p.width - 30 * s)
        val fs = ui.font(16f)
        val text = pg?.lines?.joinToString("\n") ?: ""
        val bodyH = ui.paragraphHeight(text, w - 90 * s, fs, Font.HAND, 1.5f)
        val h = min(p.height - 30 * s, bodyH + 150 * s)
        val x = (p.width - w) / 2; val y = (p.height - h) / 2
        // feuille bleu nuit arrachée au pli
        p.fillRoundRect(x + 3 * s, y + 4 * s, w, h, 3 * s, Colors.withAlpha(Colors.BLACK, 0.3f))
        p.fillRoundRect(x, y, w, h, 3 * s, 0xFFEFE6D2.toInt())
        // bord déchiré
        val n2 = 40; val pts = FloatArray(n2 * 2)
        for (k in 0 until n2) { pts[k * 2] = x + w * k / (n2 - 1f); pts[k * 2 + 1] = y + 2 * s + sin(k * 1.7f) * 2.2f * s }
        p.polyline(pts, Colors.withAlpha(Colors.PAPER_SHADE, 0.9f), 2f * s)
        if (game.settings.grain) p.image("art/ui/grain.png", x, y, w, h, 0.14f)
        // lignes du carnet
        var ly = y + 60 * s
        while (ly < y + h - 50 * s) { p.line(x + 14 * s, ly, x + w - 14 * s, ly, Colors.withAlpha(Colors.INDIGO, 0.08f), 1f); ly += fs * 1.5f }
        val head = if (pg != null) game.str("page.head", n, pg.date) else "$n"
        p.text(head, x + 24 * s, y + 30 * s, ui.font(13f), Colors.INDIGO, Font.MONO)
        pg?.let { p.text(it.where, x + w - 24 * s, y + 30 * s, ui.font(11f), Colors.INK_SOFT, Font.HAND, Align.RIGHT) }
        // oiseaux en marge (= jours depuis le diagnostic)
        val birds = pg?.birds ?: 0
        for (k in 0 until birds) {
            val bx = x + w - 30 * s - (k % 3) * 9 * s; val by = y + 56 * s + (k / 3) * 12 * s
            p.polyline(floatArrayOf(bx - 4 * s, by, bx, by - 2.5f * s, bx + 4 * s, by), Colors.withAlpha(Colors.INK, 0.7f), 1.1f * s)
        }
        p.pushClip(x, y + 48 * s, w, h - 100 * s)
        ui.paragraph(text, x + 26 * s, y + 58 * s - scroll, w - 90 * s, fs, Colors.INK, Font.HAND, lineHeight = 1.5f)
        p.popClip()
        scrollMax = (bodyH - (h - 150 * s)).coerceAtLeast(0f)
        if (fresh) p.text(game.str("page.found"), x + w / 2, y + h - 48 * s, ui.font(12f), Colors.INK_SOFT, Font.HAND, Align.CENTER)
        val b = Ui.Btn("ok", x + w / 2 - 60 * s, y + h - 40 * s, 120 * s, 32 * s, game.str("ui.close"), small = true)
        btns.add(b); ui.button(b, pressed == "ok")
    }
    override fun onButton(id: String) { game.pop() }
    override fun onBack(): Boolean { game.pop(); return true }
}

class ArchiveScreen(game: Game, val id: String) : PaperScreen(game, game.content.archives[id]?.title ?: id, game.content.archives[id]?.lines?.joinToString("\n") ?: "", Font.MONO)

/** Cloche nominative : carte-voix de 10 s. */
class BellScreen(game: Game, val bell: BellDef) : Screen(game) {
    override val opaque = false
    override fun onEnter() { if (bell.text.isNotEmpty()) game.audio.playVoice("bell_${bell.n}") }
    override fun update(dt: Float) { super.update(dt); if (time > 9f) game.pop() }
    override fun render() {
        val ui = game.ui; val p = game.painter; val s = ui.s
        val a = min(1f, time * 2f) * min(1f, (9f - time)).coerceIn(0f, 1f)
        val w = min(560 * s, p.width - 40 * s)
        val fs = ui.font(16f)
        val body = bell.text.ifEmpty { bell.note }
        val h = ui.paragraphHeight(body, w - 50 * s, fs, Font.HAND, 1.4f) + 80 * s
        val x = (p.width - w) / 2; val y = p.height * 0.18f
        ui.inkPanel(x, y, w, h, 0.88f * a)
        ui.icon("bell", x + 24 * s, y + 24 * s, 9 * s, Colors.withAlpha(Colors.LAITON, a))
        p.text("${bell.n} — ${bell.name}", x + 44 * s, y + 30 * s, ui.font(15f), Colors.withAlpha(Colors.LAITON, a), Font.TITLE)
        ui.paragraph(body, x + 25 * s, y + 46 * s, w - 50 * s, fs, Colors.withAlpha(Colors.PAPER, a), Font.HAND, lineHeight = 1.4f)
    }
    override fun onInput(e: Input): Boolean { if (e is Input.Up) game.pop(); return true }
    override fun onBack(): Boolean { game.pop(); return true }
}

/** Écho : théâtre d'ombres d'encre, voix « sous l'eau ». Non sautable au premier visionnage. */
class EchoScreen(game: Game, val echo: EchoDef, val first: Boolean) : Screen(game) {
    private var idx = -1
    private var lineTime = 0f
    private var lineDur = 3f
    private val lines = echo.lines
    override fun onEnter() { game.audio.playVoice(null); game.music("track_echo"); game.sfx("eau_ride_3"); next() }
    override fun onExit() { game.currentMusic = null; val z = game.content.zones[game.state.zone]; game.music(game.currentTableau()?.props?.get("music") ?: z?.music) }
    private fun next() {
        idx++
        lineTime = 0f
        if (idx >= lines.size) { if (time > 2f || !first) game.pop(); return }
        val l = lines[idx]
        val len = when (l) { is Line.Say -> l.text.length; is Line.Stage -> l.text.length; else -> 20 }
        lineDur = (1.6f + len * 0.05f).coerceIn(2f, 7f)
    }
    override fun update(dt: Float) { super.update(dt); lineTime += dt; if (lineTime > lineDur) next() }
    override fun render() {
        val ui = game.ui; val p = game.painter; val s = ui.s
        // fond aquarelle indigo + silhouettes
        p.gradientV(0f, 0f, p.width, p.height, 0xFF16213B.toInt(), 0xFF0A0E19.toInt())
        val a = min(1f, time * 1.5f)
        // vaguelettes d'encre
        for (k in 0 until 5) {
            val n = 50; val pts = FloatArray(n * 2)
            for (i in 0 until n) { val t = i / (n - 1f); pts[i * 2] = t * p.width; pts[i * 2 + 1] = p.height * (0.72f + k * 0.05f) + sin(t * 12 + time * 0.8f + k) * 5 * s }
            p.polyline(pts, Colors.withAlpha(0xFF7A8FC8.toInt(), 0.12f * a), 1.4f * s)
        }
        // deux silhouettes (deux traits) : à gauche et à droite du banc
        val cy = p.height * 0.7f
        fun silhouette(cx: Float, hgt: Float, lean: Float, alpha: Float) {
            p.fillCircle(cx + lean * 6 * s, cy - hgt, hgt * 0.16f, Colors.withAlpha(Colors.INK, alpha))
            p.polyline(floatArrayOf(cx, cy, cx + lean * 4 * s, cy - hgt * 0.5f, cx + lean * 6 * s, cy - hgt * 0.85f), Colors.withAlpha(Colors.INK, alpha), hgt * 0.16f)
            p.polyline(floatArrayOf(cx - hgt * 0.22f, cy - hgt * 0.45f, cx + hgt * 0.22f, cy - hgt * 0.5f), Colors.withAlpha(Colors.INK, alpha), hgt * 0.07f)
        }
        val cur = lines.getOrNull(idx)
        val spk = (cur as? Line.Say)?.speaker ?: ""
        silhouette(p.width * 0.36f, 150 * s, 0.4f, if (spk == "LOHEN" || spk == "TILL" || spk == "SIDONIE") 0.95f else 0.6f)
        silhouette(p.width * 0.62f, 165 * s, -0.4f, if (spk == "ESTEBAN" || spk == "MAREK" || spk == "BAZ" || spk == "MARTA" || spk == "YSOLDE" || spk == "TOM") 0.95f else 0.6f)
        p.fillRect(0f, cy, p.width, p.height - cy, Colors.withAlpha(Colors.BLACK, 0.35f))
        // titre
        p.text("${echo.title} — ${echo.year}", p.width / 2, ui.safeTop + 30 * s, ui.font(15f), Colors.withAlpha(0xFF9FB3E6.toInt(), a), Font.HAND, Align.CENTER)
        // ligne courante
        val la = min(1f, lineTime * 3f) * min(1f, (lineDur - lineTime) * 2f).coerceIn(0f, 1f)
        val fs = ui.font(19f)
        val w = min(760 * s, p.width - 60 * s); val x = (p.width - w) / 2
        when (cur) {
            is Line.Say -> {
                val name = game.content.characters[cur.speaker.lowercase()]?.name ?: cur.speaker.lowercase().replaceFirstChar { it.uppercase() }
                p.text(name, x, p.height * 0.78f, ui.font(14f), Colors.withAlpha(0xFF9FB3E6.toInt(), la), Font.TITLE)
                ui.paragraph(cur.text, x, p.height * 0.80f, w, fs, Colors.withAlpha(Colors.PAPER, la), Font.HAND, lineHeight = 1.35f)
            }
            is Line.Stage -> ui.paragraph(cur.text, x, p.height * 0.80f, w, ui.font(15f), Colors.withAlpha(Colors.PAPER_DARK, la * 0.8f), Font.HAND, Align.CENTER, 1.35f)
            else -> {}
        }
        if (!first) p.text(game.str("ui.tap_skip"), p.width - 16 * s, p.height - 12 * s, ui.font(11f), Colors.withAlpha(Colors.PAPER, 0.4f), Font.HAND, Align.RIGHT)
    }
    override fun onInput(e: Input): Boolean { if (e is Input.Up) { if (first) next() else game.pop() }; return true }
    override fun onBack(): Boolean { if (!first) game.pop(); return true }
}

/** Cinématique : plans illustrés, légendes, fondus. */
class CinematicScreen(game: Game, val def: CinematicDef, val onDone: () -> Unit) : Screen(game) {
    private var idx = 0
    private var shotTime = 0f
    private var done = false
    private var holdTouch = 0f
    private var touching = false
    override fun onEnter() {
        def.music?.let { game.music(it) }
        def.ambience?.let { game.ambience(it) }
        def.shots.forEach { it.image?.let { i -> game.painter.preload(i) } }
        playSfx()
    }
    private fun playSfx() { def.shots.getOrNull(idx)?.sfx?.let { game.sfx(it) } }
    override fun update(dt: Float) {
        super.update(dt)
        shotTime += dt
        if (touching) holdTouch += dt
        val shot = def.shots.getOrNull(idx)
        if (shot == null || (shotTime > shot.seconds && idx >= def.shots.size - 1)) { finish(); return }
        if (shotTime > shot.seconds) { idx++; shotTime = 0f; playSfx() }
        if (holdTouch > 1.2f && def.skippable) finish()
    }
    private fun finish() { if (done) return; done = true; game.pop(); onDone() }
    override fun render() {
        val ui = game.ui; val p = game.painter; val s = ui.s
        p.clear(Colors.BLACK)
        val shot = def.shots.getOrNull(idx) ?: return
        val fadeIn = min(1f, shotTime * 1.5f)
        val fadeOut = min(1f, (shot.seconds - shotTime) * 1.5f).coerceIn(0f, 1f)
        val a = min(fadeIn, fadeOut)
        shot.image?.let { img ->
            // lent travelling (effet Ken Burns)
            val z = 1f + 0.05f * (shotTime / shot.seconds)
            val iw = 16f; val ih = 9f
            val sc = kotlin.math.max(p.width / iw, p.height / ih) * z
            val w = iw * sc; val h = ih * sc
            val dx = when (shot.effect) { "pan_left" -> -(shotTime / shot.seconds) * 40 * s; "pan_right" -> (shotTime / shot.seconds) * 40 * s; else -> 0f }
            p.image(img, (p.width - w) / 2 + dx, (p.height - h) / 2, w, h, a)
        }
        if (shot.effect == "ink") { for (k in 0 until 30) { val t = (shotTime * 0.3f + k * 0.07f) % 1f; p.fillCircle(p.width * ((k * 0.37f) % 1f), p.height * t, (3f + k % 4) * s, Colors.withAlpha(Colors.INDIGO, 0.4f * (1 - t))) } }
        if (shot.effect == "blue") p.fillRect(0f, 0f, p.width, p.height, Colors.withAlpha(Colors.BLEU_HEURE, 0.3f))
        // bandes de cinéma
        p.fillRect(0f, 0f, p.width, p.height * 0.08f, Colors.BLACK); p.fillRect(0f, p.height * 0.92f, p.width, p.height * 0.08f, Colors.BLACK)
        shot.caption?.let { cap ->
            if (!game.settings.subtitles && shot.speaker != null) return@let
            val fs = ui.font(if (shot.speaker == null) 17f else 18f)
            val w = min(820 * s, p.width - 60 * s); val x = (p.width - w) / 2
            val h = ui.paragraphHeight(cap, w - 30 * s, fs, if (shot.speaker == null) Font.HAND else Font.BODY, 1.35f)
            val y = p.height * 0.9f - h - 24 * s
            p.fillRoundRect(x, y - 8 * s, w, h + 16 * s + (if (shot.speaker != null) fs * 1.3f else 0f), 6 * s, Colors.withAlpha(Colors.BLACK, 0.45f * a))
            var yy = y
            shot.speaker?.let { sp -> p.text(game.content.characters[sp.lowercase()]?.name ?: sp, x + 15 * s, yy + fs, fs * 0.9f, Colors.withAlpha(Colors.LAITON, a), Font.TITLE); yy += fs * 1.3f }
            ui.paragraph(cap, x + 15 * s, yy, w - 30 * s, fs, Colors.withAlpha(Colors.PAPER, a), if (shot.speaker == null) Font.HAND else Font.BODY, Align.CENTER, 1.35f)
        }
        if (def.skippable) { val ha = holdTouch.coerceIn(0f, 1.2f) / 1.2f; p.text(game.str("ui.hold_skip"), p.width - 16 * s, p.height - 12 * s, ui.font(11f), Colors.withAlpha(Colors.PAPER, 0.35f), Font.HAND, Align.RIGHT); if (ha > 0) ui.gauge(p.width - 130 * s, p.height - 8 * s, 114 * s, 2 * s, ha) }
    }
    override fun onInput(e: Input): Boolean {
        when (e) {
            is Input.Down -> { touching = true; holdTouch = 0f }
            is Input.Up -> { touching = false; if (holdTouch < 0.3f) { val shot = def.shots.getOrNull(idx); if (shot != null && shotTime > 1.2f) { if (idx >= def.shots.size - 1) finish() else { idx++; shotTime = 0f; playSfx() } } }; holdTouch = 0f }
            else -> {}
        }
        return true
    }
    override fun onBack(): Boolean { if (def.skippable) finish(); return true }
}

/** Carte du monde : voyage rapide entre zones ouvertes. */
class MapScreen(game: Game) : MenuScreen(game) {
    private var hover: String? = null
    override fun onEnter() { game.sfx("pli_depli_1") }
    override fun render() {
        val s = ui.s
        btns.clear()
        backdrop()
        backButton()
        val st = game.state
        val mw = min(p.width - 30 * s, (p.height - ui.safeTop - 90 * s) * 1.6f); val mh = mw / 1.6f
        val mx = (p.width - mw) / 2; val my = ui.safeTop + 60 * s
        if (!p.image("art/ui/map_world.jpg", mx, my, mw, mh)) { p.fillRoundRect(mx, my, mw, mh, 6 * s, Colors.PAPER_SHADE) }
        p.text(game.str("map.title"), p.width / 2, ui.safeTop + 40 * s, ui.font(22f), Colors.PAPER, Font.TITLE, Align.CENTER)
        for (z in game.content.zones.values) {
            if (z.hidden) continue
            val visited = z.id in st.zonesVisited
            val open = visited || game.conditions.eval(z.openCond)
            if (!open) continue
            val px = mx + z.mapX * mw; val py = my + z.mapY * mh
            val b = Ui.Btn("zone:${z.id}", px - 22 * s, py - 22 * s, 44 * s, 44 * s, "")
            btns.add(b)
            val cur = z.id == st.zone
            p.fillCircle(px, py, 8 * s, if (cur) Colors.GARANCE else if (visited) Colors.INK else Colors.withAlpha(Colors.INK, 0.4f))
            p.strokeCircle(px, py, 12 * s + (if (cur) sin(time * 4) * 2 * s else 0f), Colors.withAlpha(Colors.INK, 0.7f), 1.4f * s)
            val fs = ui.font(12f)
            val lw = p.measure(z.name, fs, Font.HAND) + 10 * s
            p.fillRoundRect(px - lw / 2, py - 32 * s, lw, fs * 1.5f, 3 * s, Colors.withAlpha(Colors.PAPER, 0.85f))
            p.text(z.name, px, py - 32 * s + fs * 1.1f, fs, Colors.INK, Font.HAND, Align.CENTER)
        }
        p.text(game.str("map.help"), p.width / 2, p.height - 12 * s, ui.font(12f), Colors.withAlpha(Colors.PAPER, 0.6f), Font.HAND, Align.CENTER)
    }
    override fun onButton(id: String) {
        if (id == "back") { game.pop(); return }
        if (id.startsWith("zone:")) {
            val z = id.substring(5)
            if (z == game.state.zone) { game.pop(); return }
            // le voyage rapide n'est pas possible pendant les Heures Bleues au-delà du passage (PNR)
            if (game.state.act() == 4 && !game.state.letterRead && game.state.zone in setOf("z16", "z17")) { game.toast(game.str("map.locked_tide")); return }
            game.pop()
            game.travel(z, null)
        }
    }
    override fun onBack(): Boolean { game.pop(); return true }
}
