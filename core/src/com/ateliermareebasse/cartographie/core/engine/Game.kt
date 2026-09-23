package com.ateliermareebasse.cartographie.core.engine

import com.ateliermareebasse.cartographie.core.data.Content
import com.ateliermareebasse.cartographie.core.data.ContentLoader
import com.ateliermareebasse.cartographie.core.data.Tableau
import com.ateliermareebasse.cartographie.core.platform.Audio
import com.ateliermareebasse.cartographie.core.platform.Colors
import com.ateliermareebasse.cartographie.core.platform.Input
import com.ateliermareebasse.cartographie.core.platform.Painter
import com.ateliermareebasse.cartographie.core.platform.Platform
import com.ateliermareebasse.cartographie.core.screens.*
import com.ateliermareebasse.cartographie.core.state.Conditions
import com.ateliermareebasse.cartographie.core.state.Effects
import com.ateliermareebasse.cartographie.core.state.GameState
import com.ateliermareebasse.cartographie.core.state.SaveMeta
import kotlin.math.min

/**
 * Cœur du jeu : pile d'écrans, horloge du monde, effets, sauvegardes.
 * Indépendant de la plateforme (Android / bureau).
 */
class Game(val platform: Platform, val audio: Audio) : Effects.Host, Conditions.Ctx {
    lateinit var painter: Painter
    val settings = Settings(platform).also { it.load() }
    val ui = Ui(this)
    var content: Content = ContentLoader(platform).let { l -> Content("fr", emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyList(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyList(), emptyList(), emptyList(), emptyMap(), emptyMap()) }
    override var state: GameState = GameState.newGame(settings.language, platform.nowMillis())
    val conditions = Conditions(this)
    val effects = Effects(this)
    val screens = ArrayList<Screen>()
    var world: WorldScreen? = null
    var frame = 0L
    var elapsed = 0f
    private var fade = 0f            // 1 = noir
    private var fadeTarget = 0f
    private var fadeSpeed = 2.5f
    private var afterFade: (() -> Unit)? = null
    private var autosaveTimer = 0f
    private var lastInputAt = 0f
    val toasts = ArrayList<Pair<String, Float>>()
    var contentReady = false
    var currentMusic: String? = null
    var currentAmbience: String? = null
    var emotionalCooldown = 0f       // 20 s après un choix encre-or : pas de bark
    var inputBlockedUntil = 0f
    val hooks = Hooks(this)
    var lastTouchX = 0f
    var lastTouchY = 0f

    val str: (String) -> String get() = { k -> content.str(k) }
    fun str(k: String, vararg a: Any) = content.str(k, *a)

    // ───────────────────────────── cycle de vie ─────────────────────────────
    fun start() {
        applyVolumes()
        push(LoadingScreen(this) { onContentLoaded(it) })
    }

    private fun onContentLoaded(c: Content) {
        content = c
        contentReady = true
        replaceAll(TitleScreen(this))
    }

    fun reloadContent(lang: String, then: () -> Unit) {
        settings.language = lang; settings.save()
        Thread {
            val c = ContentLoader(platform).load(lang)
            post { content = c; state.lang = lang; then() }
        }.start()
    }

    private val posted = ArrayList<() -> Unit>()
    fun post(r: () -> Unit) { synchronized(posted) { posted.add(r) } }

    fun update(dt0: Float) {
        val dt = min(dt0, 0.1f)
        frame++; elapsed += dt
        synchronized(posted) { if (posted.isNotEmpty()) { val l = ArrayList(posted); posted.clear(); l.forEach { it() } } }
        if (fade != fadeTarget) {
            val d = fadeSpeed * dt
            fade = if (fade < fadeTarget) min(fadeTarget, fade + d) else maxOf(fadeTarget, fade - d)
            if (fade >= 1f && afterFade != null) { val f = afterFade; afterFade = null; f?.invoke(); fadeTarget = 0f }
        }
        val top = screens.lastOrNull() ?: return
        // met à jour les écrans visibles (le monde continue sous les superpositions non bloquantes)
        var i = screens.size - 1
        val toUpdate = ArrayList<Screen>()
        while (i >= 0) { toUpdate.add(0, screens[i]); if (screens[i].opaque) break; i-- }
        for (sc in toUpdate) sc.update(dt)
        if (contentReady && world != null && !top.pausesWorld) worldClock(dt)
        if (emotionalCooldown > 0) emotionalCooldown -= dt
        if (toasts.isNotEmpty()) { for (k in toasts.indices) toasts[k] = toasts[k].first to toasts[k].second - dt; toasts.removeAll { it.second <= 0 } }
    }

    private fun worldClock(dt: Float) {
        val st = state
        st.playSeconds += dt
        st.tideTimer += dt
        if (st.tideTimer >= TIDE_PHASE_SECONDS) { st.tideTimer -= TIDE_PHASE_SECONDS; st.tidePhase = (st.tidePhase + 1) % 6; world?.onTideChanged() }
        if (settings.autosave) { autosaveTimer += dt; if (autosaveTimer > 90f) { autosaveTimer = 0f; autosave() } }
    }

    fun render() {
        val p = painter
        p.clear(Colors.INK)
        // trouve le premier écran opaque depuis le haut
        var i = screens.size - 1
        while (i > 0 && !screens[i].opaque) i--
        for (k in i until screens.size) screens[k].render()
        // toasts
        if (toasts.isNotEmpty()) {
            var y = ui.safeTop + 12 * ui.s
            for ((t, life) in toasts) {
                val a = min(1f, life)
                val fs = ui.font(15f)
                val w = min(p.width - 40 * ui.s, p.measure(t, fs) + 30 * ui.s)
                ui.inkPanel(p.width / 2 - w / 2, y, w, fs * 2.1f, 0.8f * a)
                p.text(t, p.width / 2, y + fs * 1.45f, fs, Colors.withAlpha(Colors.PAPER, a), align = com.ateliermareebasse.cartographie.core.platform.Align.CENTER)
                y += fs * 2.5f
            }
        }
        if (fade > 0f) p.fillRect(0f, 0f, p.width, p.height, Colors.withAlpha(Colors.BLACK, fade))
    }

    fun input(e: Input): Boolean {
        if (elapsed < inputBlockedUntil) return true
        if (e is Input.Down) { lastTouchX = e.x; lastTouchY = e.y; lastInputAt = elapsed }
        if (fade > 0.6f && fadeTarget > 0f) return true
        if (e is Input.Back) { onBack(); return true }
        var i = screens.size - 1
        while (i >= 0) {
            val sc = screens[i]
            if (sc.onInput(e)) return true
            if (sc.opaque) return false
            i--
        }
        return false
    }

    fun onBack() {
        val top = screens.lastOrNull() ?: return
        if (top.onBack()) return
        when (top) {
            is TitleScreen -> platform.quit()
            is WorldScreen -> push(PauseScreen(this))
            is LoadingScreen -> {}
            else -> pop()
        }
    }

    fun onAppPause() {
        screens.lastOrNull()?.onPause()
        if (world != null && contentReady && !state.chapterDone.and(false)) autosave()
        settings.save()
        audio.pauseAll()
    }
    fun onAppResume() { audio.resumeAll() }

    // ───────────────────────────── pile d'écrans ─────────────────────────────
    fun push(s: Screen) { screens.lastOrNull()?.onPause(); screens.add(s); s.onEnter() }
    fun pop() { val s = screens.removeLastOrNull() ?: return; s.onExit(); screens.lastOrNull()?.onResume() }
    fun popUntil(pred: (Screen) -> Boolean) { while (screens.isNotEmpty() && !pred(screens.last())) pop() }
    fun replaceAll(s: Screen) { while (screens.isNotEmpty()) pop(); push(s) }
    fun replaceTop(s: Screen) { pop(); push(s) }
    fun top(): Screen? = screens.lastOrNull()
    fun isTop(s: Screen) = screens.lastOrNull() === s

    /** Fondu au noir puis action, puis fondu d'ouverture. */
    fun fadeThen(speed: Float = 2.5f, action: () -> Unit) {
        fadeSpeed = speed; fadeTarget = 1f; afterFade = action
        if (fade >= 1f) { fade = 1f; afterFade = null; action(); fadeTarget = 0f }
    }
    fun blockInput(sec: Float) { inputBlockedUntil = elapsed + sec }

    // ───────────────────────────── partie ─────────────────────────────
    fun newGame() {
        state = GameState.newGame(settings.language, platform.nowMillis())
        startWorld(fresh = true)
    }

    fun newGamePlus(from: GameState) {
        val s = GameState.newGame(settings.language, platform.nowMillis())
        s.ngPlus = true; s.ngCount = from.ngCount + 1
        s.flags.add("ng_plus")
        // NG+ : le carnet garde la mémoire (pages, échos, secrets, bornes) et les liens gardent une trace
        s.pages.addAll(from.pages); s.echoesSeen.addAll(from.echoesSeen); s.secrets.addAll(from.secrets); s.bornes.addAll(from.bornes); s.bells.addAll(from.bells); s.labelsRead.addAll(from.labelsRead); s.archivesRead.addAll(from.archivesRead)
        s.decisions["D-06-prev"] = from.ending
        s.vars["prev_ending"] = from.ending
        s.responseText = from.responseText
        s.counters["ng_letters_read"] = from.count("ng_letters_read") + 1
        state = s
        startWorld(fresh = true)
    }

    fun startWorld(fresh: Boolean) {
        val w = WorldScreen(this)
        world = w
        replaceAll(w)
        if (fresh) {
            w.enterTableau(state.zone, state.tableau, first = true)
        } else {
            w.enterTableau(state.zone, state.tableau, first = true, silent = true)
        }
    }

    fun continueGame(): Boolean {
        val meta = listSaves().filter { !it.corrupt }.maxByOrNull { it.savedAt } ?: return false
        return loadSave(meta.file)
    }

    // ───────────────────────────── sauvegardes ─────────────────────────────
    fun saveTo(file: String, name: String) {
        state.savedAt = platform.nowMillis(); state.slotName = name
        platform.writeFile(file, state.toBytes())
    }
    fun autosave() { if (world != null && contentReady) saveTo("auto.sav", str("save.auto")) }
    override fun checkpoint() { saveTo("checkpoint.sav", str("save.checkpoint")); toast(str("toast.checkpoint")) }
    override fun save(name: String) { saveTo("checkpoint.sav", name.ifEmpty { str("save.checkpoint") }); toast(str("toast.saved_named", name)) }
    fun saveSlot(n: Int) { saveTo("slot_$n.sav", str("save.slot", n)); settings.lastSlot = n; settings.save(); toast(str("toast.saved")) }
    fun loadSave(file: String): Boolean {
        val b = platform.readFile(file) ?: return false
        val s = try { GameState.fromBytes(b) } catch (e: Exception) { platform.log("save corrompue $file: $e"); return false }
        state = s
        if (s.lang != settings.language) { reloadContent(s.lang) { startWorld(fresh = false) }; return true }
        startWorld(fresh = false)
        return true
    }
    fun deleteSave(file: String) = platform.deleteFile(file)
    fun listSaves(): List<SaveMeta> {
        val files = (platform.listFiles("slot_") + platform.listFiles("auto") + platform.listFiles("checkpoint")).filter { it.endsWith(".sav") }.distinct()
        return files.map { f ->
            val b = platform.readFile(f)
            val s = try { if (b == null) null else GameState.fromBytes(b) } catch (e: Exception) { null }
            if (s == null) SaveMeta(f, f, 0L, 0f, "", 0, 0, 0, true, false)
            else SaveMeta(f, s.slotName.ifEmpty { f }, s.savedAt, s.playSeconds, s.zone, s.seq, s.act(), s.cartoPercent(totalTableaux()), false, s.chapterDone)
        }
    }
    fun totalTableaux() = content.zones.values.filter { !it.hidden }.sumOf { it.tableaux.size }
    override fun cartoPercent() = state.cartoPercent(totalTableaux())

    // ───────────────────────────── audio ─────────────────────────────
    fun applyVolumes() = audio.setVolumes(settings.master, settings.music, settings.ambience, settings.sfx)
    override fun sfx(id: String) { audio.playSfx(id, 1f) }
    override fun music(id: String?) { if (id != currentMusic) { currentMusic = id; audio.playMusic(id) } }
    override fun ambience(id: String?) { if (id != currentAmbience) { currentAmbience = id; audio.playAmbience(id) } }
    override fun haptic(ms: Int) { if (settings.vibration) platform.vibrate(ms) }

    // ───────────────────────────── effets de présentation ─────────────────────────────
    override fun thought(key: String) { world?.showThought(resolveThought(key) ?: return) }
    override fun thoughtText(text: String) { world?.showThought(text) }
    override fun toast(text: String) { toasts.add(text to 3.2f) }
    override fun cinematic(id: String, then: (() -> Unit)?) {
        val def = content.cinematics[id]
        if (def == null) { platform.log("cinématique inconnue $id"); then?.invoke(); return }
        push(CinematicScreen(this, def) { state.cinSeen.add(id); then?.invoke() })
    }
    override fun travel(zone: String, tableau: String?) { world?.travel(zone, tableau) }
    override fun openPuzzle(id: String) { val sc = com.ateliermareebasse.cartographie.core.puzzles.PuzzleFactory.create(this, id) ?: run { platform.log("énigme inconnue $id"); return }; push(sc) }
    override fun runScene(id: String) {
        val sc = content.scenes[id] ?: run { platform.log("scène inconnue $id"); return }
        push(DialogueScreen(this, sc))
    }
    override fun openLetter() { push(LetterScreen(this)) }
    override fun openEpilogue() { push(EpilogueScreen(this)) }
    override fun endChapter() { push(EndCardScreen(this)) }
    override fun credits() { push(CreditsScreen(this)) }
    override fun openCarnet(tab: String?) { push(CarnetScreen(this, tab)) }
    override fun tutorial(id: String) { if (state.tutosSeen.add(id)) content.tutorials[id]?.let { push(TutorialScreen(this, id, it)) } }
    override fun actCard(act: Int) { if (act in 1..4) push(ActCardScreen(this, act)) }
    override fun quitToTitle() { autosave(); world = null; audio.playMusic(null); audio.playAmbience(null); currentMusic = null; currentAmbience = null; replaceAll(TitleScreen(this)) }
    override fun playEcho(id: String) {
        val e = content.echoes[id] ?: return
        state.echoesAvailable.add(id)
        val first = state.echoesSeen.add(id)
        if (first) state.clarte = (state.clarte + 2).coerceAtMost(100)
        push(EchoScreen(this, e, first))
    }
    override fun filouReact(key: String) { world?.filouReact(key) }
    override fun onPageFound(n: Int) { push(PageScreen(this, n, true)) }
    override fun onSecret(id: String) { state.clarte = (state.clarte + 1).coerceAtMost(100); toast(str("toast.secret")); sfx("harpe_03"); haptic(30) }
    override fun onPuzzleSolved(id: String) {}
    override fun onItemGiven(id: String) { val d = content.items[id]; if (d != null) { toast(str("toast.item", d.name)); sfx("pli_depli_2") } }
    override fun onLienChanged(npc: String, delta: Int) { if (delta > 0) sfx("harpe_01") }
    override fun retentissement() { push(RetentissementScreen(this)) }
    override fun chocolat() {
        state.chocolats++
        val k = "z03.chocolat_${state.chocolats.coerceAtMost(11)}"
        thought(k)
        sfx("tasse_pose")
    }
    override fun cooldown(seconds: Float) { emotionalCooldown = maxOf(emotionalCooldown, seconds) }
    override fun log(msg: String) = platform.log(msg)
    override fun npcHere(id: String): Boolean = world?.npcHere(id) ?: false

    /** Résout une clé de pensée avec variantes : .ng (NG+), .lens, .actN, .trait (DOUCEUR…). */
    fun resolveThought(key: String): String? {
        val th = content.thoughts
        val st = state
        if (st.ngPlus) th["$key.ng"]?.let { return it }
        if (st.letterRead) th["$key.after"]?.let { return it }
        th["$key.act${st.act()}"]?.let { return it }
        th["$key.${st.dominantTrait()}"]?.let { return it }
        return th[key]
    }

    /** Conversation : première entrée de la table dont la condition est vraie (et non déjà jouée si `once`). */
    fun talkTo(npc: String): Boolean {
        val entries = content.talk[npc] ?: return false
        for (e in entries) {
            val sc = content.scenes[e.scene] ?: continue
            if (sc.once && e.scene in state.scenesSeen) continue
            if (!conditions.eval(e.cond)) continue
            if (!conditions.eval(sc.cond)) continue
            state.inc("talked:$npc")
            runScene(e.scene)
            return true
        }
        return false
    }

    fun currentTableau(): Tableau? = content.tableau(state.zone, state.tableau)

    fun formatDuration(sec: Float): String { val m = (sec / 60).toInt(); return if (m < 60) str("time.min", m) else str("time.h", m / 60, m % 60) }

    companion object { const val TIDE_PHASE_SECONDS = 12f * 60f }
}
