package com.ateliermareebasse.cartographie.core.screens

import com.ateliermareebasse.cartographie.core.data.Hotspot
import com.ateliermareebasse.cartographie.core.data.PathLink
import com.ateliermareebasse.cartographie.core.data.Tableau
import com.ateliermareebasse.cartographie.core.engine.Game
import com.ateliermareebasse.cartographie.core.engine.Screen
import com.ateliermareebasse.cartographie.core.engine.Ui
import com.ateliermareebasse.cartographie.core.platform.Align
import com.ateliermareebasse.cartographie.core.platform.Colors
import com.ateliermareebasse.cartographie.core.platform.Font
import com.ateliermareebasse.cartographie.core.platform.Input
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** L'écran d'exploration : tableaux illustrés, points d'intérêt, chemins, Filou, pensées. */
class WorldScreen(game: Game) : Screen(game) {
    override val pausesWorld = false
    private val ui get() = game.ui
    private val p get() = game.painter
    private val st get() = game.state

    var tableau: Tableau? = null
    private var thoughtQueue = ArrayList<String>()
    private var thoughtText: String? = null
    private var thoughtAge = 0f
    private var thoughtMinTime = 0f
    private var zoneCard = 0f
    private var zoneCardText = ""
    private var barkTimer = 0f
    private var lastBarkKey = ""
    private var lensUntil = 0f
    private var filouReaction: Pair<String, String>? = null
    private var filouReactionAge = 0f
    private var filouSniffIndex = 0
    private var holdHot: Hotspot? = null
    private var holdTime = 0f
    private var holdNeeded = 0f
    private var waitAnim = 0f
    private var driftX = 0f
    private var driftY = 0f
    private var pressedBtn: String? = null
    private var enterCue = 0f
    private val rnd = Random(7)
    private val particles = FloatArray(4 * 128) { rnd.nextFloat() }
    private var lastHotspotHint = 0f
    private var downX = 0f; private var downY = 0f; private var downAt = 0f; private var dragged = false
    private var tapHint: Pair<Float, Float>? = null
    private var tapHintAge = 0f

    private val btns = ArrayList<Ui.Btn>()

    // ───────────────────────────── navigation ─────────────────────────────
    fun enterTableau(zoneId: String, tabId: String, first: Boolean = false, silent: Boolean = false) {
        val zone = game.content.zones[zoneId] ?: run { game.log("zone inconnue $zoneId"); return }
        val tab = zone.tableau(tabId)
        val zoneChanged = st.zone != zoneId || first
        st.prevZone = st.zone; st.prevTableau = st.tableau
        st.zone = zoneId; st.tableau = tab.id
        tableau = tab
        val firstVisit = st.tableauxVisited.add("$zoneId:${tab.id}")
        if (st.zonesVisited.add(zoneId) && !silent) { game.state.releves += 1 }
        game.music(tab.props["music"] ?: zone.music)
        game.ambience(tab.props["ambience"] ?: zone.ambience)
        thoughtText = null; thoughtQueue.clear(); filouReaction = null; holdHot = null; lensUntil = 0f
        barkTimer = 0f; enterCue = 0f
        preloadNeighbours(tab)
        if (zoneChanged) { zoneCard = 3.2f; zoneCardText = zone.name; if (!silent) game.autosave() }
        if (silent) return
        // pensées d'entrée : la première dont la condition tient ; une seule fois par tableau sauf « always »
        for ((key, cond) in tab.enterThoughts) {
            if (!game.conditions.eval(cond)) continue
            val always = key.endsWith("!")
            val k = key.removeSuffix("!")
            if (!always && !st.thoughtsShown.add("$zoneId:${tab.id}:$k")) continue
            game.resolveThought(k)?.let { queueThought(it) }
            break
        }
        for ((scene, cond) in tab.enterScenes) {
            val sc = game.content.scenes[scene] ?: continue
            if (sc.once && scene in st.scenesSeen) continue
            if (!game.conditions.eval(cond) || !game.conditions.eval(sc.cond)) continue
            game.runScene(scene)
            break
        }
        if (firstVisit && st.tableauxVisited.size == 2) game.tutorial("explore")
        game.hooks.onEnterTableau(zoneId, tab.id)
    }

    private fun preloadNeighbours(tab: Tableau) {
        for (path in tab.paths) {
            val (z, t) = resolveTarget(path.target)
            game.content.tableau(z, t ?: game.content.zones[z]?.start ?: "t01")?.let { if (it.image.isNotEmpty()) p.preload(it.image) }
        }
    }

    fun resolveTarget(target: String): Pair<String, String?> {
        return when {
            target.contains(':') -> target.substringBefore(':') to target.substringAfter(':')
            target.startsWith("t") && target.length <= 3 -> st.zone to target
            else -> target to null
        }
    }

    fun travel(zoneId: String, tabId: String?) {
        val zone = game.content.zones[zoneId] ?: return
        val tid = tabId ?: zone.start
        game.fadeThen(3f) { enterTableau(zoneId, tid) }
    }

    private fun followPath(path: PathLink) {
        if (!game.conditions.eval(path.cond)) {
            path.lockedKey?.let { k -> (game.resolveThought(k) ?: game.content.thoughts[k])?.let { showThought(it) } }
            game.sfx("verrou_doux")
            return
        }
        val (z, t) = resolveTarget(path.target)
        if (path.effects.isNotEmpty()) game.effects.apply(path.effects)
        game.sfx(if (z == st.zone) "pas_2" else "pas_1")
        if (path.cin != null && path.cin !in st.cinSeen) game.cinematic(path.cin) { travel(z, t) } else travel(z, t)
    }

    fun onTideChanged() { barkTimer = 0f }

    fun npcHere(id: String): Boolean = tableau?.npcs?.any { it.id == id && game.conditions.eval(it.cond) } == true

    // ───────────────────────────── pensées ─────────────────────────────
    fun showThought(text: String) { queueThought(text) }
    private fun queueThought(text: String) { if (thoughtText == null) { thoughtText = text; thoughtAge = 0f; thoughtMinTime = 0.7f } else thoughtQueue.add(text) }
    private fun dismissThought() { thoughtText = thoughtQueue.removeFirstOrNull(); thoughtAge = 0f; barkTimer = 0f }

    fun filouReact(key: String) {
        val r = game.content.filou[key] ?: return
        filouReaction = r.behaviour to r.thought; filouReactionAge = 0f
        game.sfx(listOf("filou_souffle", "filou_pattes", "filou_gemit").random())
    }

    // ───────────────────────────── mise à jour ─────────────────────────────
    override fun update(dt: Float) {
        super.update(dt)
        thoughtAge += dt; filouReactionAge += dt; tapHintAge += dt; enterCue += dt
        if (zoneCard > 0) zoneCard -= dt
        if (waitAnim > 0) waitAnim -= dt
        if (holdHot != null) {
            holdTime += dt
            if (holdTime >= holdNeeded) { val h = holdHot!!; holdHot = null; holdTime = 0f; completeHold(h) }
        }
        val tab = tableau ?: return
        if (game.settings.parallax) { driftX = sin(time * 0.11f) * 6f * ui.s; driftY = cos(time * 0.09f) * 4f * ui.s }
        // barks d'ambulation
        if (thoughtText == null && game.isTop(this) && game.emotionalCooldown <= 0f) {
            barkTimer += dt
            if (barkTimer > 28f + rnd.nextFloat() * 20f) { barkTimer = 0f; tryBark(tab) }
        }
        if (filouReaction != null && filouReactionAge > 7f) filouReaction = null
    }

    private fun tryBark(tab: Tableau) {
        val zoneKey = tab.barkZone
        val key = "$zoneKey:${st.tidePhase}"
        if (key == lastBarkKey) return
        val trait = st.dominantTrait().first().toString()
        val pool = game.content.barks.filter { it.zone == zoneKey && (it.trait == trait || (it.trait == "NG" && st.letterRead)) }
        if (pool.isEmpty()) return
        val b = pool[rnd.nextInt(pool.size)]
        lastBarkKey = key
        val hFreq = if (st.trait("HUMEUR") >= 3) 0.9f else 0.8f
        if (rnd.nextFloat() > hFreq) return
        queueThought(b.text)
    }

    private fun completeHold(h: Hotspot) {
        when (h.kind) {
            "ECHO" -> { game.sfx("eau_ride_2"); game.effects.apply(h.effects); game.playEcho(h.id) }
            "SIT" -> {
                waitAnim = 1.6f
                st.tideTimer = 0f; st.tidePhase = (st.tidePhase + 1) % 6
                game.sfx("pli_depli_1"); onTideChanged()
                (h.params["think"]?.let { game.resolveThought(it) } ?: game.str("world.wait_done", st.tideName()))?.let { showThought(it) }
                game.effects.apply(h.effects)
                st.inc("sat:${h.id}")
            }
            "SECRET" -> { game.effects.apply(h.effects); h.params["scene"]?.let { game.runScene(it) } }
            "ACTION" -> { game.effects.apply(h.effects); h.params["scene"]?.let { game.runScene(it) } }
        }
    }

    // ───────────────────────────── rendu ─────────────────────────────
    private fun imageRect(): FloatArray {
        // couvre l'écran (16:9 → toute résolution) en gardant le ratio 16:9 des tableaux
        val iw = 16f; val ih = 9f
        val scale = max(p.width / iw, p.height / ih) * (if (game.settings.parallax) 1.03f else 1f)
        val w = iw * scale; val h = ih * scale
        return floatArrayOf((p.width - w) / 2 + driftX, (p.height - h) / 2 + driftY, w, h)
    }

    override fun render() {
        val tab = tableau
        val s = ui.s
        if (tab == null) { p.clear(Colors.INK); return }
        val r = imageRect()
        val drawn = if (tab.image.isNotEmpty()) p.image(tab.image, r[0], r[1], r[2], r[3]) else false
        if (!drawn) paletteBackdrop(tab)
        renderAtmosphere(tab, r)
        // PNJ
        for (n in tab.npcs) {
            if (!game.conditions.eval(n.cond)) continue
            val cx = r[0] + n.x * r[2]; val cy = r[1] + n.y * r[3]
            val ph = 170f * s * n.scale
            val bob = sin(time * 1.3f + n.x * 10) * 2f * s
            val path = "art/characters/${n.id}.png"
            if (!p.image(path, cx - ph * 0.5f, cy - ph + bob, ph, ph, 1f)) {
                p.fillRoundRect(cx - ph * 0.2f, cy - ph * 0.9f + bob, ph * 0.4f, ph * 0.9f, ph * 0.1f, Colors.withAlpha(Colors.INK, 0.6f))
            }
            val name = game.content.characters[n.id]?.name ?: n.id
            val fs = ui.font(13f)
            p.text(name, cx, cy + fs * 1.4f, fs, Colors.withAlpha(Colors.PAPER, 0.85f), Font.HAND, Align.CENTER)
        }
        // Filou
        if (st.filouWithLohen) renderFilou(r)
        // points d'intérêt
        val lens = time < lensUntil
        for (h in tab.hotspots) {
            if (!visible(h, lens)) continue
            val cx = r[0] + h.x * r[2]; val cy = r[1] + h.y * r[3]
            if (h.kind == "TALK" && tab.npcs.any { it.id == h.id }) continue // le PNJ dessiné sert de point d'intérêt
            val col = if (h.kind == "ECHO" || h.kind == "SECRET" || h.kind == "MURMURE") Colors.LAITON else Colors.PAPER
            ui.hotspotMarker(cx, cy, h.kind, time + h.x * 7, col, lens || (holdHot === h))
            if (holdHot === h) {
                val f = (holdTime / holdNeeded).coerceIn(0f, 1f)
                p.strokeCircle(cx, cy, 22 * s, Colors.withAlpha(Colors.PAPER, 0.3f), 3f * s)
                arc(cx, cy, 22 * s, f, Colors.LAITON, 3f * s)
                val fs = ui.font(14f)
                p.text(h.params["hold_label"] ?: game.str("world.hold"), cx, cy - 30 * s, fs, Colors.PAPER, Font.HAND, Align.CENTER)
            }
        }
        // chemins
        for (path in tab.paths) {
            val ok = game.conditions.eval(path.cond)
            if (path.hidden || (path.strict && !ok)) continue
            val (cx, cy) = pathPos(path, r)
            renderPathMarker(path, cx, cy, ok)
        }
        renderHud(tab)
        renderThought()
        renderFilouReaction()
        if (zoneCard > 0f) renderZoneCard()
        if (waitAnim > 0f) p.fillRect(0f, 0f, p.width, p.height, Colors.withAlpha(Colors.BLEU_HEURE, min(0.6f, waitAnim * 0.5f)))
        tapHint?.let { (x, y) -> if (tapHintAge < 0.5f) p.strokeCircle(x, y, 14 * s + tapHintAge * 60 * s, Colors.withAlpha(Colors.PAPER, 0.6f - tapHintAge), 1.5f * s) }
    }

    private fun arc(cx: Float, cy: Float, r: Float, f: Float, color: Int, stroke: Float) {
        val n = (f * 40).toInt()
        if (n < 1) return
        val pts = FloatArray((n + 1) * 2)
        for (k in 0..n) { val a = -Math.PI / 2 + f * 2 * Math.PI * k / n; pts[k * 2] = (cx + r * cos(a)).toFloat(); pts[k * 2 + 1] = (cy + r * sin(a)).toFloat() }
        p.polyline(pts, color, stroke)
    }

    private fun paletteBackdrop(tab: Tableau) {
        val zone = game.content.zones[tab.zoneId]
        val top = when (zone?.palette) { "nuit", "bleu" -> Colors.BLEU_HEURE; "gare" -> 0xFF4A4A52.toInt(); "verger" -> 0xFF6E7B4E.toInt(); "phare" -> 0xFF2A3A55.toInt(); else -> Colors.PAPER_DARK }
        p.gradientV(0f, 0f, p.width, p.height, top, Colors.INK)
        val fs = ui.font(22f)
        p.text(tab.name, p.width / 2, p.height / 2, fs, Colors.withAlpha(Colors.PAPER, 0.5f), Font.TITLE, Align.CENTER)
    }

    private fun renderAtmosphere(tab: Tableau, r: FloatArray) {
        val s = ui.s
        val act = st.act()
        // teinte des actes : soir doré (III), Heures Bleues (IV)
        if (tab.props["tide_view"] != null && st.isLowTide()) p.fillRect(0f, 0f, p.width, p.height, Colors.withAlpha(0xFFB99B5A.toInt(), 0.06f))
        when {
            tab.props.containsKey("dark") -> p.fillRect(0f, 0f, p.width, p.height, Colors.withAlpha(Colors.BLACK, 0.35f))
            act == 3 && tab.props["interior"] == null -> p.fillRect(0f, 0f, p.width, p.height, Colors.withAlpha(0xFFD08A3A.toInt(), 0.10f))
            act == 4 && !st.letterRead -> p.fillRect(0f, 0f, p.width, p.height, Colors.withAlpha(Colors.BLEU_HEURE, if (tab.props["interior"] == null) 0.30f else 0.15f))
            act >= 4 && st.letterRead -> p.fillRect(0f, 0f, p.width, p.height, Colors.withAlpha(0xFF0E1630.toInt(), 0.25f))
        }
        // faisceau du phare / lampe
        tab.props["overlay"]?.let { ov ->
            if (ov == "beam") {
                val a = 0.10f + 0.06f * sin(time * 0.7f)
                val x = p.width * (0.5f + 0.45f * sin(time * 0.25f))
                p.fillPolygon(floatArrayOf(p.width * 0.5f, 0f, x - 120 * s, p.height, x + 120 * s, p.height), Colors.withAlpha(0xFFFFF2C0.toInt(), a))
            }
            if (ov == "lamp") p.gradientRadial(p.width * 0.5f, p.height * 0.55f, p.width * 0.7f, Colors.withAlpha(0xFFFFE0A0.toInt(), 0.10f), Colors.withAlpha(Colors.BLACK, 0.35f))
        }
        // particules : poussière / pétrels / gouttes d'encre / lucioles
        val n = game.settings.particles
        if (n > 0) {
            val kind = tab.props["particles"] ?: "dust"
            for (k in 0 until min(n, 128)) {
                val i = k * 4
                val x = ((particles[i] + time * (0.004f + particles[i + 2] * 0.01f) * (if (kind == "snow") 0.3f else 1f)) % 1f) * p.width
                val y = ((particles[i + 1] + (if (kind == "petrels") 0f else -time * 0.006f * particles[i + 3]) + 2f) % 1f) * p.height
                when (kind) {
                    "petrels" -> if (k < 6) { val yy = p.height * (0.12f + 0.08f * particles[i + 1]) + sin(time * 2 + k) * 6 * s; p.polyline(floatArrayOf(x - 6 * s, yy, x, yy - 3 * s, x + 6 * s, yy), Colors.withAlpha(Colors.INK, 0.6f), 1.5f * s) }
                    "fireflies" -> p.fillCircle(x, y, (1.5f + sin(time * 3 + k) * 1f) * s, Colors.withAlpha(0xFFFFE7A0.toInt(), 0.55f + 0.3f * sin(time * 2 + k)))
                    "ink" -> p.fillCircle(x, (particles[i + 1] * 1.4f + time * 0.05f * particles[i + 3] + 1f) % 1.2f * p.height, 2f * s, Colors.withAlpha(Colors.INDIGO, 0.35f))
                    "spray" -> p.fillCircle(x, p.height * (0.6f + 0.4f * ((particles[i + 1] + time * 0.2f) % 1f)), 1.2f * s, Colors.withAlpha(Colors.PAPER, 0.35f))
                    "bluehour" -> p.fillCircle(x, y, 1.6f * s, Colors.withAlpha(0xFF9FB8FF.toInt(), 0.25f + 0.2f * sin(time + k)))
                    else -> p.fillCircle(x, y, 1.2f * s, Colors.withAlpha(Colors.PAPER, 0.18f + 0.1f * sin(time + k)))
                }
            }
        }
        if (game.settings.vignette) p.image("art/ui/vignette.png", 0f, 0f, p.width, p.height, 0.55f)
        if (game.settings.grain) p.image("art/ui/grain.png", 0f, 0f, p.width, p.height, 0.07f)
        if (game.settings.highContrast) p.fillRect(0f, 0f, p.width, p.height, Colors.withAlpha(Colors.BLACK, 0.12f))
    }

    private fun renderFilou(r: FloatArray) {
        val s = ui.s
        val tab = tableau ?: return
        val fx = r[0] + (tab.props["filou_x"]?.toFloatOrNull() ?: 0.16f) * r[2]
        val fy = r[1] + (tab.props["filou_y"]?.toFloatOrNull() ?: 0.86f) * r[3]
        val size = 110f * s
        val bob = abs(sin(time * 2.1f)) * 2f * s
        if (!p.image("art/characters/filou.png", fx - size / 2, fy - size + bob, size, size)) {
            ui.icon("dog", fx, fy - size * 0.4f, size * 0.22f, Colors.withAlpha(Colors.INK, 0.8f))
        }
    }

    private fun renderPathMarker(path: PathLink, cx: Float, cy: Float, ok: Boolean) {
        val s = ui.s
        val a = if (ok) 0.9f else 0.5f
        val pulse = 1f + 0.06f * sin(time * 2.5f)
        val rr = 22f * s * pulse
        p.fillCircle(cx, cy, rr, Colors.withAlpha(Colors.INK, 0.45f * a))
        p.strokeCircle(cx, cy, rr, Colors.withAlpha(Colors.LAITON, 0.85f * a), 1.6f * s)
        val ic = when {
            path.kind == "door" -> "door"
            path.back -> "arrow_l"
            cx < p.width * 0.25f -> "arrow_l"
            cx > p.width * 0.75f -> "arrow"
            cy < p.height * 0.3f -> "arrow_u"
            else -> "arrow_d"
        }
        ui.icon(ic, cx, cy, 9 * s, Colors.withAlpha(Colors.PAPER, a))
        val fs = ui.font(13f)
        val lw = p.measure(path.label, fs, Font.HAND) + 14 * s
        val lx = (cx - lw / 2).coerceIn(4 * s, p.width - lw - 4 * s)
        val ly = if (cy > p.height * 0.75f) cy - rr - fs * 1.9f else cy + rr + 4 * s
        p.fillRoundRect(lx, ly, lw, fs * 1.6f, 4 * s, Colors.withAlpha(Colors.INK, 0.55f * a))
        p.text(path.label, lx + lw / 2, ly + fs * 1.18f, fs, Colors.withAlpha(Colors.PAPER, a), Font.HAND, Align.CENTER)
    }

    private fun pathPos(path: PathLink, r: FloatArray): Pair<Float, Float> {
        if (path.x >= 0f) return (r[0] + path.x * r[2]).coerceIn(30 * ui.s, p.width - 30 * ui.s) to (r[1] + path.y * r[3]).coerceIn(80 * ui.s, p.height - 40 * ui.s)
        // placement automatique : retour à gauche, autres à droite / bas
        val idx = tableau!!.paths.indexOf(path)
        return if (path.back) 34 * ui.s to p.height * 0.5f else (p.width - 34 * ui.s) to p.height * (0.35f + 0.15f * idx)
    }

    private fun renderHud(tab: Tableau) {
        val s = ui.s
        btns.clear()
        val zone = game.content.zones[tab.zoneId]
        // bandeau titre discret
        val fs = ui.font(15f)
        val title = "${zone?.name ?: ""} — ${tab.name}"
        val tw = p.measure(title, fs, Font.HAND) + 22 * s
        p.fillRoundRect(10 * s, ui.safeTop + 8 * s, tw, fs * 1.9f, 5 * s, Colors.withAlpha(Colors.INK, 0.55f))
        p.text(title, 21 * s, ui.safeTop + 8 * s + fs * 1.35f, fs, Colors.PAPER, Font.HAND)
        // marée
        val tideTxt = game.str("tide.${st.tideName()}")
        val ts = ui.font(12f)
        p.text(tideTxt, 21 * s, ui.safeTop + 8 * s + fs * 1.9f + ts * 1.3f, ts, Colors.withAlpha(Colors.PAPER, 0.75f), Font.HAND)
        ui.gauge(21 * s, ui.safeTop + 8 * s + fs * 1.9f + ts * 1.7f, 60 * s, 3 * s, st.tideTimer / Game.TIDE_PHASE_SECONDS, Colors.withAlpha(Colors.LAITON, 0.8f))
        // boutons à droite
        val bs = 46f * s
        val gap = 8f * s
        val ids = listOf("pause" to "pause", "carnet" to "carnet", "sacoche" to "sacoche", "map" to "map", "lens" to "lens")
        val left = game.settings.leftHanded
        var x = if (left) 10 * s else p.width - bs - 10 * s
        for ((id, ic) in ids) {
            val b = Ui.Btn(id, x, ui.safeTop + 8 * s, bs, bs, "", icon = ic)
            btns.add(b)
            p.fillRoundRect(b.x, b.y, bs, bs, 10 * s, Colors.withAlpha(Colors.INK, if (pressedBtn == id) 0.85f else 0.55f))
            p.strokeRoundRect(b.x, b.y, bs, bs, 10 * s, Colors.withAlpha(Colors.LAITON, 0.5f), 1f * s)
            ui.icon(ic, b.x + bs / 2, b.y + bs / 2, 11 * s, if (id == "lens" && time < lensUntil) Colors.LAITON else Colors.PAPER)
            x += if (left) bs + gap else -(bs + gap)
        }
        // bouton Filou (questionner)
        if (st.filouWithLohen) {
            val fb = Ui.Btn("filou", if (left) p.width - 150 * s else 10 * s, p.height - 46 * s, 140 * s, 36 * s, game.str("world.ask_filou"), icon = "dog", small = true)
            btns.add(fb)
            p.fillRoundRect(fb.x, fb.y, fb.w, fb.h, 8 * s, Colors.withAlpha(Colors.INK, 0.55f))
            p.strokeRoundRect(fb.x, fb.y, fb.w, fb.h, 8 * s, Colors.withAlpha(Colors.LAITON, 0.5f), 1f * s)
            ui.icon("dog", fb.x + 18 * s, fb.y + fb.h / 2, 8 * s, Colors.PAPER)
            p.text(fb.label, fb.x + 34 * s, fb.y + fb.h / 2 + ui.font(13f) * 0.38f, ui.font(13f), Colors.PAPER, Font.HAND)
        }
    }

    private fun renderThought() {
        val t = thoughtText ?: return
        // une scène de dialogue au-dessus occupe déjà le bas de l'écran : la pensée attend son tour
        if (game.screens.any { it !== this && it is DialogueScreen }) return
        val s = ui.s
        val fs = ui.font(17f)
        val maxW = min(p.width - 60 * s, 760 * s)
        val txtH = ui.paragraphHeight(t, maxW - 40 * s, fs, Font.HAND, 1.38f)
        val h = txtH + 34 * s
        val x = (p.width - maxW) / 2
        val y = p.height - h - 56 * s
        val a = min(1f, thoughtAge * 3f)
        ui.paper(x, y, maxW, h, 0.94f * a)
        // filet d'encre à gauche
        p.fillRect(x + 10 * s, y + 12 * s, 2 * s, h - 24 * s, Colors.withAlpha(Colors.GARANCE, 0.7f * a))
        ui.paragraph(t, x + 22 * s, y + 15 * s, maxW - 40 * s, fs, Colors.withAlpha(Colors.INK, a), Font.HAND, lineHeight = 1.38f)
        if (thoughtQueue.isNotEmpty()) ui.icon("arrow", x + maxW - 16 * s, y + h - 14 * s, 5 * s, Colors.withAlpha(Colors.INK, 0.5f * a))
    }

    private fun renderFilouReaction() {
        val r = filouReaction ?: return
        val s = ui.s
        val a = min(1f, filouReactionAge * 3f) * (if (filouReactionAge > 6f) (7f - filouReactionAge) else 1f)
        val fs = ui.font(14f)
        val maxW = min(p.width - 60 * s, 520 * s)
        val body = "${r.first}\n${r.second}"
        val h = ui.paragraphHeight(body, maxW - 30 * s, fs, Font.HAND, 1.35f) + 24 * s
        val x = 20 * s; val y = p.height - h - 96 * s - (if (thoughtText != null) 120 * s else 0f)
        ui.inkPanel(x, y, maxW, h, 0.85f * a)
        ui.icon("dog", x + 16 * s, y + 16 * s, 7 * s, Colors.withAlpha(Colors.LAITON, a))
        p.text(r.first, x + 30 * s, y + 12 * s + fs, fs, Colors.withAlpha(Colors.PAPER, a), Font.BODY)
        ui.paragraph(r.second, x + 14 * s, y + 14 * s + fs * 1.5f, maxW - 30 * s, fs, Colors.withAlpha(Colors.PAPER_DARK, a), Font.HAND, lineHeight = 1.35f)
    }

    private fun renderZoneCard() {
        val a = min(1f, zoneCard).coerceIn(0f, 1f) * min(1f, (3.2f - zoneCard) * 2f)
        val fs = ui.font(30f)
        ui.engraved(zoneCardText, p.width / 2, p.height * 0.22f, fs, Colors.PAPER, a)
        val zone = game.content.zones[st.zone]
        if (zone != null && zone.subtitle.isNotEmpty()) p.text(zone.subtitle, p.width / 2, p.height * 0.22f + fs * 0.9f, ui.font(15f), Colors.withAlpha(Colors.PAPER_DARK, a), Font.HAND, Align.CENTER)
    }

    // ───────────────────────────── entrées ─────────────────────────────
    private fun visible(h: Hotspot, lens: Boolean): Boolean {
        if (h.params["lens"] != null && !lens) return false
        if (!game.conditions.eval(h.cond)) return false
        if (h.kind == "TAKE" && (st.has(h.params["item"] ?: h.id) || "took_${h.id}" in st.flags)) return false
        if (h.kind == "PAGE" || (h.kind == "READ" && h.params["page"] != null)) { val n = (h.params["page"] ?: h.id.removePrefix("page")).toIntOrNull(); if (n != null && n in st.pages) return false }
        if (h.kind == "PUZZLE" && h.id in st.puzzlesSolved && h.params["replay"] == null) return false
        if (h.kind == "SECRET" && h.id in st.secrets) return false
        if (h.kind == "ECHO" && h.id in st.echoesSeen && h.params["replay"] == null) return false
        if (h.params["once"] != null && "hot_${h.id}" in st.flags) return false
        return true
    }

    override fun onInput(e: Input): Boolean {
        val tab = tableau ?: return false
        val s = ui.s
        when (e) {
            is Input.Down -> {
                downX = e.x; downY = e.y; downAt = time; dragged = false
                pressedBtn = btns.firstOrNull { ui.hit(it, e.x, e.y) }?.id
                if (pressedBtn == null) {
                    // maintien sur un point d'attente
                    val h = nearestHotspot(tab, e.x, e.y)
                    if (h != null && (h.kind == "ECHO" || h.kind == "SIT" || h.params["hold"] != null)) {
                        holdHot = h; holdTime = 0f
                        holdNeeded = h.params["hold"]?.toFloatOrNull() ?: (if (h.kind == "ECHO") 4f else 2.2f)
                        return true
                    }
                }
                return true
            }
            is Input.Move -> {
                if (abs(e.x - downX) > 18 * s || abs(e.y - downY) > 18 * s) { dragged = true; if (holdHot != null && holdTime < holdNeeded) { holdHot = null } }
                return true
            }
            is Input.Up -> {
                val pb = pressedBtn; pressedBtn = null
                if (holdHot != null) { holdHot = null; if (thoughtText == null) showThought(game.str("world.hold_hint")); return true }
                if (dragged) return true
                if (pb != null) { onButton(pb); return true }
                tap(tab, e.x, e.y)
                return true
            }
            else -> return false
        }
    }

    private fun onButton(id: String) {
        game.sfx("ui_tap")
        when (id) {
            "pause" -> game.push(PauseScreen(game))
            "carnet" -> game.push(CarnetScreen(game, null))
            "sacoche" -> game.push(CarnetScreen(game, "sacoche"))
            "map" -> game.push(MapScreen(game))
            "lens" -> { lensUntil = time + 8f; game.sfx("laiton_assemble_1"); if (!st.has("lentille_arpenteur")) return; if (!st.flags.contains("lens_tuto")) { st.flags.add("lens_tuto"); showThought(game.str("world.lens_hint")) } }
            "filou" -> askFilou()
        }
    }

    private fun askFilou() {
        val tab = tableau ?: return
        val keys = tab.hotspots.filter { it.params["sniff"] != null && game.conditions.eval(it.cond) }.map { it.params["sniff"]!! } + listOfNotNull(tab.props["sniff"])
        if (keys.isEmpty()) { filouReact(defaultFilouKey()); return }
        val k = keys[filouSniffIndex % keys.size]; filouSniffIndex++
        filouReact(k)
        st.inc("filou_asked")
        if (st.count("filou_asked") == 1) game.tutorial("filou")
    }

    private fun defaultFilouKey(): String = when (st.act()) { 4 -> "maree_montante"; else -> listOf("vent_est", "petrels", "pluie").random() }

    private fun nearestHotspot(tab: Tableau, x: Float, y: Float): Hotspot? {
        val r = imageRect(); val lens = time < lensUntil
        var best: Hotspot? = null; var bd = Float.MAX_VALUE
        for (h in tab.hotspots) {
            if (!visible(h, lens)) continue
            val cx = r[0] + h.x * r[2]; val cy = r[1] + h.y * r[3]
            val d = (x - cx) * (x - cx) + (y - cy) * (y - cy)
            val rad = max(30 * ui.s, h.radius * r[2])
            if (d < rad * rad && d < bd) { bd = d; best = h }
        }
        return best
    }

    private fun tap(tab: Tableau, x: Float, y: Float) {
        val s = ui.s
        val r = imageRect()
        // pensée affichée : un tap la ferme (sauf si le tap vise un chemin/point)
        if (thoughtText != null && thoughtAge > thoughtMinTime) {
            val fs = ui.font(17f); val maxW = min(p.width - 60 * s, 760 * s)
            val h = ui.paragraphHeight(thoughtText!!, maxW - 40 * s, fs, Font.HAND, 1.38f) + 34 * s
            if (y > p.height - h - 60 * s) { dismissThought(); return }
        }
        // PNJ
        for (n in tab.npcs) {
            if (!game.conditions.eval(n.cond)) continue
            val cx = r[0] + n.x * r[2]; val cy = r[1] + n.y * r[3]; val ph = 170f * s * n.scale
            if (x > cx - ph * 0.45f && x < cx + ph * 0.45f && y > cy - ph && y < cy + 20 * s) { talk(n.id); return }
        }
        // Filou (caresse)
        if (st.filouWithLohen) {
            val fx = r[0] + (tab.props["filou_x"]?.toFloatOrNull() ?: 0.16f) * r[2]; val fy = r[1] + (tab.props["filou_y"]?.toFloatOrNull() ?: 0.86f) * r[3]
            if (abs(x - fx) < 55 * s && y > fy - 110 * s && y < fy + 10 * s) { petFilou(); return }
        }
        nearestHotspot(tab, x, y)?.let { act(it); return }
        // chemins
        for (path in tab.paths) {
            val ok = game.conditions.eval(path.cond)
            if (path.hidden || (path.strict && !ok)) continue
            val (cx, cy) = pathPos(path, r)
            if (abs(x - cx) < 34 * s && abs(y - cy) < 34 * s) { followPath(path); return }
        }
        tapHint = x to y; tapHintAge = 0f
        if (thoughtText != null) dismissThought()
    }

    private fun petFilou() {
        game.sfx("filou_souffle")
        val n = st.inc("pet_filou")
        if (n % 5 == 1) { st.filou = (st.filou + 1).coerceAtMost(10) }
        val keys = listOf("world.pet_1", "world.pet_2", "world.pet_3", "world.pet_4")
        showThought(game.str(keys[n % keys.size]))
        game.haptic(15)
    }

    private fun talk(npc: String) {
        if (!game.talkTo(npc)) {
            val k = "npc.$npc.silent"
            (game.content.thoughts[k] ?: game.str("world.npc_busy")).let { showThought(it) }
        }
    }

    fun act(h: Hotspot) {
        val st = game.state
        val once = h.params["once"] != null
        if (once) st.flags.add("hot_${h.id}")
        when (h.kind) {
            "EXAMINE", "LISTEN" -> {
                val n = st.inc("examined:${h.id}")
                h.params["sfx"]?.let { game.sfx(it) } ?: if (h.kind == "LISTEN") game.sfx("eau_ride_1") else game.sfx("ui_tap")
                val key = h.params["think"] ?: "${tableau!!.zoneId}.${h.id}"
                val txt = (if (n >= 10) game.content.thoughts["$key.10"] else null) ?: (if (n >= 2) game.content.thoughts["$key.2"] else null) ?: game.resolveThought(key)
                txt?.let { showThought(it) }
                if (n == 1 || h.params["repeat"] != null) game.effects.apply(h.effects)
                if (n == 1) st.releves += 1
                h.params["sniff"]?.let { if (n == 1 && st.filouWithLohen && rnd.nextFloat() < 0.35f) filouReact(it) }
            }
            "TAKE" -> {
                val item = h.params["item"] ?: h.id
                st.give(item); st.flags.add("took_${h.id}")
                game.sfx("pli_depli_3")
                val key = h.params["think"] ?: "${tableau!!.zoneId}.${h.id}"
                (game.resolveThought(key) ?: game.content.items[item]?.thoughts?.get("1"))?.let { showThought(it) }
                game.content.items[item]?.let { game.toast(game.str("toast.item", it.name)) }
                game.effects.apply(h.effects)
                if (st.inventory.size == 2) game.tutorial("sacoche")
            }
            "USE" -> {
                val item = h.params["item"] ?: ""
                if (item.isEmpty() || st.has(item)) {
                    if (h.params["consume"] != null) st.take(item)
                    h.params["sfx"]?.let { game.sfx(it) }
                    (h.params["think"]?.let { game.resolveThought(it) })?.let { showThought(it) }
                    game.effects.apply(h.effects)
                    h.params["scene"]?.let { game.runScene(it) }
                } else {
                    (h.params["need"]?.let { game.resolveThought(it) } ?: game.str("world.need_item", game.content.items[item]?.name ?: item)).let { showThought(it) }
                }
            }
            "PUZZLE" -> {
                if (h.id in st.puzzlesSolved && h.params["replay"] == null) { h.params["done"]?.let { game.resolveThought(it) }?.let { showThought(it) }; return }
                game.effects.apply(h.effects)
                game.openPuzzle(h.id)
            }
            "TALK" -> talk(h.id)
            "READ", "PAGE" -> {
                val page = (h.params["page"] ?: if (h.kind == "PAGE") h.id.removePrefix("page") else null)?.toIntOrNull()
                when {
                    page != null -> { game.sfx("page_turn_01"); if (st.pages.add(page)) { st.clarte = min(100, st.clarte); game.onPageFound(page) } else game.push(PageScreen(game, page, false)); game.effects.apply(h.effects) }
                    h.params["archive"] != null -> { st.archivesRead.add(h.params["archive"]!!); game.push(ArchiveScreen(game, h.params["archive"]!!)); game.effects.apply(h.effects) }
                    h.params["text"] != null -> { game.push(PaperScreen(game, h.label, game.resolveThought(h.params["text"]!!) ?: "")); game.effects.apply(h.effects) }
                    else -> { (h.params["think"]?.let { game.resolveThought(it) })?.let { showThought(it) }; game.effects.apply(h.effects) }
                }
            }
            "BELL" -> {
                val n = h.id.toIntOrNull() ?: h.params["n"]?.toIntOrNull() ?: 0
                val bell = game.content.bells[n]
                game.sfx("cloche_${((n % 12) + 1).toString().padStart(2, '0')}")
                game.haptic(12)
                val first = st.bells.add(n)
                if (bell != null) game.push(BellScreen(game, bell))
                if (first) st.releves += 1
                game.effects.apply(h.effects)
            }
            "BORNE" -> {
                val b = game.content.bornes[h.id]
                game.sfx("laiton_assemble_2")
                val first = st.bornes.add(h.id)
                if (first) { st.releves += 2; st.clarte = min(100, st.clarte + 1) }
                if (b != null) game.push(PaperScreen(game, "${game.str("carnet.borne")} ${b.id} — ${b.place}", "${b.text}\n\n— ${b.hand}", Font.MONO))
                game.effects.apply(h.effects)
                if (first && st.bornes.size == 1) game.tutorial("bornes")
            }
            "MURMURE" -> {
                val m = game.content.murmures[h.id]
                val first = st.murmures.add(h.id)
                if (first) { st.clarte = min(100, st.clarte + 1); game.sfx("harpe_02") }
                m?.let { showThought(it.text) }
                game.effects.apply(h.effects)
            }
            "LABEL" -> {
                val n = h.id.toIntOrNull() ?: 0
                val l = game.content.labels[n]
                st.labelsRead.add(n)
                game.sfx("papier_etiquette")
                l?.let { game.push(PaperScreen(game, game.str("carnet.label"), it.text, Font.HAND)) }
                game.effects.apply(h.effects)
            }
            "SECRET", "ACTION" -> {
                if (h.params["hold"] != null) return // géré par le maintien
                game.effects.apply(h.effects)
                h.params["scene"]?.let { game.runScene(it) }
                h.params["think"]?.let { game.resolveThought(it) }?.let { showThought(it) }
                h.params["sfx"]?.let { game.sfx(it) }
            }
            "ECHO", "SIT" -> { showThought(game.str("world.hold_hint")) }
            "FILOU" -> filouReact(h.id)
            "CIN" -> game.cinematic(h.id)
        }
    }

    override fun onBack(): Boolean { if (thoughtText != null) { dismissThought(); return true }; return false }
}
