package com.ateliermareebasse.cartographie.core.screens

import com.ateliermareebasse.cartographie.core.engine.Game
import com.ateliermareebasse.cartographie.core.engine.Ui
import com.ateliermareebasse.cartographie.core.platform.Align
import com.ateliermareebasse.cartographie.core.platform.Colors
import com.ateliermareebasse.cartographie.core.platform.Font
import kotlin.math.min

/** Le carnet double page de Lohen : carte, sacoche, pages, échos, personnages, journal. */
class CarnetScreen(game: Game, startTab: String?) : MenuScreen(game) {
    private val tabs = listOf("carte", "sacoche", "pages", "echos", "gens", "journal")
    private var tab = tabs.indexOf(startTab ?: "carte").coerceAtLeast(0)
    private var selected: String? = null
    private var detailText: String? = null
    private val st get() = game.state
    init { scrollable = true }

    override fun onEnter() { game.sfx("page_turn_02"); if (!game.state.filouWithLohen) {} }

    private fun frame(): FloatArray {
        val s = ui.s
        val w = min(p.width - 24 * s, 980 * s); val h = p.height - ui.safeTop - 24 * s
        return floatArrayOf((p.width - w) / 2, ui.safeTop + 12 * s, w, h)
    }

    override fun render() {
        val s = ui.s
        btns.clear()
        backdrop()
        val f = frame()
        ui.paper(f[0], f[1], f[2], f[3], 0.98f)
        // reliure centrale
        p.fillRect(f[0] + f[2] / 2 - 1 * s, f[1] + 10 * s, 2 * s, f[3] - 20 * s, Colors.withAlpha(Colors.INK, 0.15f))
        // onglets
        val tw = min(120 * s, (f[2] - 60 * s) / tabs.size)
        tabs.forEachIndexed { i, t ->
            val b = Ui.Btn("tab:$t", f[0] + 10 * s + i * tw, f[1] + 8 * s, tw - 4 * s, 30 * s, game.str("carnet.tab_$t"), small = true)
            btns.add(b)
            p.fillRoundRect(b.x, b.y, b.w, b.h, 5 * s, Colors.withAlpha(if (tab == i) Colors.INK else Colors.PAPER_SHADE, if (tab == i) 0.85f else 0.5f))
            p.text(b.label, b.x + b.w / 2, b.y + 21 * s, ui.font(12.5f), if (tab == i) Colors.PAPER else Colors.INK, Font.BODY, Align.CENTER)
        }
        val close = Ui.Btn("close", f[0] + f[2] - 40 * s, f[1] + 8 * s, 32 * s, 30 * s, "", icon = "close")
        btns.add(close); ui.icon("close", close.x + 16 * s, close.y + 15 * s, 7 * s, Colors.INK)
        val cx = f[0] + 10 * s; val cy = f[1] + 48 * s; val cw = f[2] - 20 * s; val ch = f[3] - 58 * s
        p.pushClip(cx, cy, cw, ch)
        when (tabs[tab]) {
            "carte" -> renderCarte(cx, cy, cw, ch)
            "sacoche" -> renderSacoche(cx, cy, cw, ch)
            "pages" -> renderPages(cx, cy, cw, ch)
            "echos" -> renderEchos(cx, cy, cw, ch)
            "gens" -> renderGens(cx, cy, cw, ch)
            "journal" -> renderJournal(cx, cy, cw, ch)
        }
        p.popClip()
    }

    // ── carte : la presqu'île, zones relevées, pourcentage
    private fun renderCarte(x: Float, y: Float, w: Float, h: Float) {
        val s = ui.s
        val mapW = if (ui.portrait) w else w * 0.62f
        val mapH = if (ui.portrait) h * 0.55f else h
        val ok = p.image("art/ui/map_world.jpg", x, y, mapW, mapH, 0.95f)
        if (!ok) p.fillRoundRect(x, y, mapW, mapH, 6 * s, Colors.PAPER_SHADE)
        for (z in game.content.zones.values) {
            if (z.hidden) continue
            val visited = z.id in st.zonesVisited
            val open = visited || game.conditions.eval(z.openCond)
            if (!open && !visited) continue
            val px = x + z.mapX * mapW; val py = y + z.mapY * mapH
            if (visited) { p.fillCircle(px, py, 6 * s, Colors.INK); p.strokeCircle(px, py, 10 * s, Colors.withAlpha(Colors.INK, 0.6f), 1.2f * s) } else p.strokeCircle(px, py, 6 * s, Colors.withAlpha(Colors.INK, 0.6f), 1.2f * s)
            if (z.id == st.zone) p.strokeCircle(px, py, 14 * s + kotlin.math.sin(time * 4) * 2 * s, Colors.GARANCE, 1.6f * s)
            p.text(z.name, px, py - 12 * s, ui.font(11f), Colors.withAlpha(Colors.INK, if (visited) 0.95f else 0.55f), Font.HAND, Align.CENTER)
        }
        val ix = if (ui.portrait) x else x + mapW + 14 * s; val iy = if (ui.portrait) y + mapH + 10 * s else y
        val iw = if (ui.portrait) w else w - mapW - 14 * s
        var yy = iy
        val fs = ui.font(14f)
        p.text(game.str("carnet.progress"), ix, yy + fs, ui.font(17f), Colors.INK, Font.TITLE); yy += fs * 2f
        val lines = listOf(
            game.str("carnet.carte_pct", game.cartoPercent()),
            game.str("carnet.releves", st.releves),
            game.str("carnet.bornes_n", st.bornes.size, 40),
            game.str("carnet.pages_n", st.pages.size, 24),
            game.str("carnet.echos_n", st.echoesSeen.size, 12),
            game.str("carnet.secrets_n", st.secrets.size, 12),
            game.str("carnet.murmures_n", st.murmures.size, 18),
            game.str("carnet.cloches_n", st.bells.size, 43),
            game.str("carnet.act", game.str("act.${st.act()}")),
            game.str("carnet.tide", game.str("tide.${st.tideName()}")),
            game.str("carnet.time", game.formatDuration(st.playSeconds)),
        )
        for (l in lines) { p.text(l, ix, yy + fs, fs, Colors.INK_SOFT, Font.HAND); yy += fs * 1.5f }
        yy += 6 * s
        val b = Ui.Btn("travel", ix, yy, min(200 * s, iw), 38 * s, game.str("carnet.open_map"), small = true)
        btns.add(b); ui.button(b, pressed == "travel")
    }

    // ── sacoche : grille d'objets + examen
    /** Glyphe d'encre par catégorie quand l'objet n'a pas d'icône dessinée. */
    private fun categoryIcon(cat: String): String = when (cat.trim()) {
        "outil" -> "tool"; "cle" -> "key"; "souvenir" -> "heart"; "consommable" -> "cup"; "document" -> "page"; else -> "hand"
    }

    private fun renderSacoche(x: Float, y: Float, w: Float, h: Float) {
        val s = ui.s
        val items = st.inventory.keys.mapNotNull { game.content.items[it] }
        val cell = 92 * s
        val cols = max(1, ((if (ui.portrait) w else w * 0.55f) / cell).toInt())
        var i = 0
        val fs = ui.font(11f)
        if (items.isEmpty()) p.text(game.str("carnet.empty_bag"), x + 10 * s, y + 30 * s, ui.font(15f), Colors.INK_SOFT, Font.HAND)
        for (it in items) {
            val cx = x + (i % cols) * cell; val cy = y + (i / cols) * cell - scroll
            val b = Ui.Btn("item:${it.id}", cx + 4 * s, cy + 4 * s, cell - 8 * s, cell - 8 * s, "")
            btns.add(b)
            p.fillRoundRect(b.x, b.y, b.w, b.h, 6 * s, Colors.withAlpha(if (selected == it.id) Colors.LAITON else Colors.PAPER_SHADE, if (selected == it.id) 0.5f else 0.35f))
            if (!p.image("art/items/${it.id}.png", b.x + 10 * s, b.y + 6 * s, b.w - 20 * s, b.w - 30 * s)) ui.icon(categoryIcon(it.category), b.x + b.w / 2, b.y + b.h * 0.4f, 12 * s, Colors.INK_SOFT)
            ui.paragraph(it.name, b.x + 2 * s, b.y + b.h - fs * 2.6f, b.w - 4 * s, fs, Colors.INK, Font.HAND, Align.CENTER, 1.1f, maxLines = 2)
            val n = st.inventory[it.id] ?: 1
            if (n > 1) p.text("×$n", b.x + b.w - 6 * s, b.y + 14 * s, fs, Colors.GARANCE, Font.MONO, Align.RIGHT)
            i++
        }
        scrollMax = ((i / cols + 1) * cell - h).coerceAtLeast(0f)
        if (!ui.portrait) {
            val dx = x + w * 0.58f; val dw = w * 0.42f
            val sel = items.firstOrNull { it.id == selected } ?: return
            p.text(sel.name, dx, y + ui.font(18f), ui.font(18f), Colors.INK, Font.TITLE)
            var yy = y + ui.font(18f) * 1.6f
            sel.note?.let { yy += ui.paragraph(it, dx, yy, dw - 10 * s, ui.font(13f), Colors.INK_SOFT, Font.BODY) + 8 * s }
            detailText?.let { yy += ui.paragraph(it, dx, yy, dw - 10 * s, ui.font(14f), Colors.INK, Font.HAND, lineHeight = 1.4f) + 10 * s }
            val b = Ui.Btn("examine", dx, yy + 4 * s, min(180 * s, dw), 36 * s, game.str("carnet.examine"), small = true)
            btns.add(b); ui.button(b, pressed == "examine")
            if (sel.hold) { val b2 = Ui.Btn("hold", dx + min(190 * s, dw), yy + 4 * s, min(160 * s, dw), 36 * s, game.str("carnet.hold_letter"), small = true); btns.add(b2); ui.button(b2, pressed == "hold") }
            if (sel.id == "lettre_esteban") { val b3 = Ui.Btn("reread", dx, yy + 48 * s, min(180 * s, dw), 36 * s, game.str("carnet.reread"), small = true); btns.add(b3); ui.button(b3, pressed == "reread") }
        }
    }

    private fun renderPages(x: Float, y: Float, w: Float, h: Float) {
        val s = ui.s
        val cell = 64 * s
        val cols = max(1, (w / cell).toInt())
        p.text(game.str("carnet.pages_intro", st.pages.size), x + 6 * s, y + ui.font(14f), ui.font(14f), Colors.INK_SOFT, Font.HAND)
        for (n in 1..24) {
            val i = n - 1
            val cx = x + (i % cols) * cell; val cy = y + 26 * s + (i / cols) * cell - scroll
            val has = n in st.pages
            val b = Ui.Btn("page:$n", cx + 4 * s, cy + 4 * s, cell - 8 * s, cell - 8 * s, "", enabled = has)
            btns.add(b)
            p.fillRoundRect(b.x, b.y, b.w, b.h, 4 * s, Colors.withAlpha(if (has) Colors.PAPER else Colors.PAPER_SHADE, if (has) 0.9f else 0.3f))
            p.strokeRoundRect(b.x, b.y, b.w, b.h, 4 * s, Colors.withAlpha(Colors.INK, if (has) 0.5f else 0.15f), 1f * s)
            p.text("$n", b.x + b.w / 2, b.y + b.h / 2 + ui.font(16f) * 0.35f, ui.font(16f), Colors.withAlpha(Colors.INK, if (has) 1f else 0.3f), Font.MONO, Align.CENTER)
            if (has) { val birds = game.content.pages[n]?.birds ?: 0; if (birds > 0) p.text("$birds", b.x + b.w - 4 * s, b.y + 12 * s, ui.font(9f), Colors.INK_SOFT, Font.HAND, Align.RIGHT) }
        }
        scrollMax = ((24 / cols + 1) * cell + 30 * s - h).coerceAtLeast(0f)
    }

    private fun renderEchos(x: Float, y: Float, w: Float, h: Float) {
        val s = ui.s
        var yy = y - scroll
        p.text(game.str("carnet.echos_intro"), x + 6 * s, yy + ui.font(14f), ui.font(14f), Colors.INK_SOFT, Font.HAND); yy += 28 * s
        for (e in game.content.echoes.values) {
            val seen = e.id in st.echoesSeen
            val b = Ui.Btn("echo:${e.id}", x + 4 * s, yy, w - 8 * s, 40 * s, "", enabled = seen)
            btns.add(b)
            p.fillRoundRect(b.x, b.y, b.w, b.h, 5 * s, Colors.withAlpha(Colors.PAPER_SHADE, if (seen) 0.5f else 0.2f))
            ui.icon("echo", b.x + 20 * s, b.y + 20 * s, 8 * s, Colors.withAlpha(Colors.INDIGO, if (seen) 1f else 0.3f))
            val label = if (seen) "${e.title} — ${e.year}" else game.str("carnet.echo_unknown")
            p.text(label, b.x + 40 * s, b.y + 26 * s, ui.font(14f), Colors.withAlpha(Colors.INK, if (seen) 1f else 0.45f), Font.HAND)
            yy += 46 * s
        }
        scrollMax = (yy + scroll - y - h).coerceAtLeast(0f)
    }

    private fun renderGens(x: Float, y: Float, w: Float, h: Float) {
        val s = ui.s
        val chars = game.content.characters.values.filter { it.role != "hidden" }
        val cell = if (ui.portrait) 110 * s else 128 * s
        val cols = max(1, (w / cell).toInt())
        var i = 0
        for (c in chars) {
            val cx = x + (i % cols) * cell; val cy = y + (i / cols) * (cell + 24 * s) - scroll
            val lien = st.lien(c.id.uppercase())
            val met = c.id == "lohen" || c.id == "filou" || c.id == "esteban" || st.count("talked:${c.id}") > 0 || "${c.id}_rencontre" in st.flags || "${c.id}_rencontree" in st.flags || lien >= 1 && c.id in setOf("ombeline", "anselme", "sidonie")
            val b = Ui.Btn("char:${c.id}", cx + 6 * s, cy + 4 * s, cell - 12 * s, cell - 12 * s, "", enabled = met)
            btns.add(b)
            // l'encre du portrait s'affirme avec le lien
            val alpha = if (!met) 0.15f else 0.45f + 0.11f * lien
            if (!p.image("art/characters/${c.id}.png", b.x, b.y, b.w, b.h, alpha)) p.fillCircle(b.x + b.w / 2, b.y + b.h / 2, b.w * 0.35f, Colors.withAlpha(c.palette, alpha))
            p.text(if (met) c.name else "?", b.x + b.w / 2, b.y + b.h + ui.font(13f), ui.font(13f), Colors.INK, Font.HAND, Align.CENTER)
            if (met && c.id !in setOf("lohen", "filou", "esteban")) { for (k in 0 until 5) p.fillCircle(b.x + b.w / 2 - 20 * s + k * 10 * s, b.y + b.h + ui.font(13f) + 10 * s, 2.6f * s, Colors.withAlpha(Colors.INK, if (k < lien) 0.9f else 0.2f)) }
            i++
        }
        scrollMax = ((i / cols + 1) * (cell + 24 * s) - h).coerceAtLeast(0f)
        if (selected?.startsWith("char:") == true && !ui.portrait) {
            val c = game.content.characters[selected!!.substring(5)] ?: return
            ui.scrim(0.3f)
            val dw = min(460 * s, p.width - 40 * s); val dh = 200 * s; val dx = (p.width - dw) / 2; val dy = (p.height - dh) / 2
            ui.paper(dx, dy, dw, dh)
            p.text(c.name, dx + 16 * s, dy + 28 * s, ui.font(18f), Colors.INK, Font.TITLE)
            p.text(c.role, dx + 16 * s, dy + 48 * s, ui.font(13f), Colors.INK_SOFT, Font.HAND)
            ui.paragraph(c.bio, dx + 16 * s, dy + 60 * s, dw - 32 * s, ui.font(13f), Colors.INK, Font.BODY, lineHeight = 1.3f)
        }
    }

    private fun renderJournal(x: Float, y: Float, w: Float, h: Float) {
        val s = ui.s
        var yy = y - scroll
        val fs = ui.font(14f)
        fun head(t: String) { yy += 8 * s; p.text(t, x + 6 * s, yy + ui.font(16f), ui.font(16f), Colors.INK, Font.TITLE); yy += ui.font(16f) * 1.6f }
        head(game.str("carnet.puzzles_solved", st.puzzlesSolved.count { it.startsWith("E") }, 16))
        for (pz in game.content.puzzles.values.filter { it.id.startsWith("E") }) {
            val done = pz.id in st.puzzlesSolved
            p.text((if (done) "✓ " else "○ ") + pz.name, x + 10 * s, yy + fs, fs, Colors.withAlpha(Colors.INK, if (done) 1f else 0.45f), Font.BODY)
            yy += fs * 1.35f
            if (done) yy += ui.paragraph("« ${pz.phrase} »", x + 26 * s, yy, w - 40 * s, ui.font(12.5f), Colors.INK_SOFT, Font.HAND, lineHeight = 1.3f) + 4 * s
        }
        head(game.str("carnet.secrets_found", st.secrets.size, 12))
        for (sec in game.content.puzzles.values.filter { it.id.startsWith("S") } + listOf()) {
            val done = sec.id in st.secrets
            p.text((if (done) "✓ " else "○ ") + (if (done) sec.name else game.str("carnet.secret_unknown")), x + 10 * s, yy + fs, fs, Colors.withAlpha(Colors.INK, if (done) 1f else 0.45f), Font.BODY); yy += fs * 1.35f
        }
        for (sid in listOf("S03", "S04", "S05", "S07", "S09", "S10", "S11", "S12")) {
            val done = sid in st.secrets
            p.text((if (done) "✓ " else "○ ") + (if (done) game.str("secret.$sid") else game.str("carnet.secret_unknown")), x + 10 * s, yy + fs, fs, Colors.withAlpha(Colors.INK, if (done) 1f else 0.45f), Font.BODY); yy += fs * 1.35f
        }
        head(game.str("carnet.bornes_title", st.bornes.size))
        for (bid in st.bornes.sorted()) {
            val b = game.content.bornes[bid] ?: continue
            yy += ui.paragraph("${b.id} — ${b.place} (${b.hand}) : ${b.text}", x + 10 * s, yy, w - 20 * s, ui.font(12.5f), Colors.INK_SOFT, Font.MONO, lineHeight = 1.3f) + 4 * s
        }
        head(game.str("carnet.murmures_title", st.murmures.size))
        for (mid in st.murmures.sorted()) { val m = game.content.murmures[mid] ?: continue; yy += ui.paragraph("${m.title} — ${m.text}", x + 10 * s, yy, w - 20 * s, ui.font(12.5f), Colors.INK_SOFT, Font.HAND, lineHeight = 1.3f) + 4 * s }
        head(game.str("carnet.archives_title", st.archivesRead.size))
        for (aid in st.archivesRead.sorted()) {
            val a = game.content.archives[aid] ?: continue
            val b = Ui.Btn("archive:$aid", x + 10 * s, yy, w - 20 * s, 30 * s, a.title, small = true); btns.add(b)
            p.text("▸ ${a.title}", x + 14 * s, yy + fs * 1.4f, fs, Colors.INDIGO, Font.BODY); yy += 32 * s
        }
        head(game.str("carnet.bells_title", st.bells.size))
        yy += ui.paragraph(st.bells.sorted().mapNotNull { game.content.bells[it]?.name }.joinToString(" · "), x + 10 * s, yy, w - 20 * s, ui.font(12f), Colors.INK_SOFT, Font.HAND, lineHeight = 1.3f) + 8 * s
        if (st.responseText.isNotEmpty()) { head(game.str("carnet.my_answer")); yy += ui.paragraph(st.responseText, x + 10 * s, yy, w - 20 * s, ui.font(13f), Colors.INK, Font.HAND, lineHeight = 1.4f) }
        scrollMax = (yy + scroll - y - h + 20 * s).coerceAtLeast(0f)
    }

    private fun max(a: Int, b: Int) = if (a > b) a else b

    override fun onButton(id: String) {
        when {
            id == "close" -> game.pop()
            id.startsWith("tab:") -> { tab = tabs.indexOf(id.substring(4)); scroll = 0f; selected = null; detailText = null; game.sfx("page_turn_03") }
            id == "travel" -> { game.pop(); game.push(MapScreen(game)) }
            id.startsWith("item:") -> { selected = id.substring(5); detailText = null; if (ui.portrait) examine() }
            id == "examine" -> examine()
            id == "hold" -> { game.haptic(1200); game.sfx("papier_serre"); detailText = game.str("carnet.letter_held") }
            id == "reread" -> game.openLetter()
            id.startsWith("page:") -> game.push(PageScreen(game, id.substring(5).toInt(), false))
            id.startsWith("echo:") -> game.content.echoes[id.substring(5)]?.let { game.push(EchoScreen(game, it, false)) }
            id.startsWith("char:") -> { selected = if (selected == id) null else id; if (ui.portrait) game.content.characters[id.substring(5)]?.let { c -> game.push(PaperScreen(game, c.name, "${c.role}\n\n${c.bio}")) } }
            id.startsWith("archive:") -> game.push(ArchiveScreen(game, id.substring(8)))
        }
    }

    private fun examine() {
        val it = game.content.items[selected ?: return] ?: return
        val n = st.inc("examined:item:${it.id}")
        val t = (if (n >= 10) (if (st.letterRead) it.thoughts["10+"] else null) ?: it.thoughts["10"] else null) ?: (if (n >= 2) it.thoughts["2"] else null) ?: it.thoughts["1"] ?: ""
        detailText = t
        game.sfx("pli_depli_4")
        if (ui.portrait) game.push(PaperScreen(game, it.name, t, Font.HAND))
        if (it.id == "sifflet_esteban") game.sfx(if ("ressort_vole" in st.flags) "sifflet_faux" else "sifflet_petrel")
    }

    override fun onTapEmpty(x: Float, y: Float) { if (selected?.startsWith("char:") == true) selected = null }
    override fun onBack(): Boolean { game.pop(); return true }
}
