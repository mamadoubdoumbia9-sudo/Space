package com.ateliermareebasse.cartographie.core.state

/** Exécuteur d'effets. Les effets d'état modifient GameState ; les effets de présentation passent par Host. */
class Effects(private val host: Host) {
    interface Host {
        val state: GameState
        fun sfx(id: String)
        fun music(id: String?)
        fun ambience(id: String?)
        fun haptic(ms: Int)
        fun thought(key: String)
        fun thoughtText(text: String)
        fun toast(text: String)
        fun cinematic(id: String, then: (() -> Unit)? = null)
        fun travel(zone: String, tableau: String?)
        fun openPuzzle(id: String)
        fun runScene(id: String)
        fun save(name: String)
        fun checkpoint()
        fun openLetter()
        fun openEpilogue()
        fun endChapter()
        fun credits()
        fun openCarnet(tab: String?)
        fun tutorial(id: String)
        fun actCard(act: Int)
        fun quitToTitle()
        fun playEcho(id: String)
        fun filouReact(key: String)
        fun onPageFound(n: Int)
        fun onSecret(id: String)
        fun onPuzzleSolved(id: String)
        fun onItemGiven(id: String)
        fun onLienChanged(npc: String, delta: Int)
        fun retentissement()
        fun chocolat()
        fun cooldown(seconds: Float)
        fun log(msg: String)
    }

    /** Effets différés : ceux qui changent d'écran sont exécutés après les effets d'état. */
    fun apply(effects: List<String>) {
        val later = ArrayList<String>()
        for (e in effects) {
            val t = e.trim()
            if (t.isEmpty()) continue
            if (isDeferred(t)) later.add(t) else one(t)
        }
        // une cinématique séquence ce qui la suit (voyage, scène…) dans son rappel de fin
        val cinIdx = later.indexOfFirst { it.startsWith("cin ") }
        if (cinIdx >= 0) {
            val before = later.subList(0, cinIdx).toList()
            val after = later.subList(cinIdx + 1, later.size).toList()
            for (e in before) one(e)
            host.cinematic(later[cinIdx].substringAfter(' ').trim()) { for (e in after) one(e) }
            return
        }
        for (e in later) one(e)
    }

    private fun isDeferred(e: String): Boolean {
        val k = e.substringBefore(' ')
        return k in setOf("cin", "travel", "puzzle", "scene", "letter", "epilogue", "end_chapter", "credits", "carnet", "quit_to_title", "echo", "act_card", "retentissement")
    }

    fun one(e: String) {
        val st = host.state
        val k = e.substringBefore(' ')
        val arg = e.substringAfter(' ', "").trim()
        when {
            e.startsWith("+") && e.length > 1 && e[1].isUpperCase() -> { st.addTrait(e.substring(1).trim()) }
            e.startsWith("-") && e.length > 1 && e[1].isUpperCase() -> { st.addTrait(e.substring(1).trim(), -1) }
            k.startsWith("LIEN-") -> {
                val npc = k.removePrefix("LIEN-"); val d = arg.replace(" ", "").toIntOrNull() ?: 1
                if (d != 0) { st.addLien(npc, d); host.onLienChanged(npc, d) }
            }
            k == "CLARTE" -> st.clarte = (st.clarte + (arg.replace(" ", "").toIntOrNull() ?: 1)).coerceIn(0, 100)
            k == "FILOU" -> st.filou = (st.filou + (arg.replace(" ", "").toIntOrNull() ?: 1)).coerceIn(0, 10)
            k == "flag" -> st.flags.add(norm(arg))
            k == "unflag" -> st.flags.remove(norm(arg))
            k == "give" -> { val p = arg.split(' '); st.give(p[0], p.getOrNull(1)?.toIntOrNull() ?: 1); host.onItemGiven(p[0]) }
            k == "take" -> { val p = arg.split(' '); st.take(p[0], p.getOrNull(1)?.toIntOrNull() ?: 1) }
            k == "page" -> { val n = arg.toIntOrNull() ?: return; if (st.pages.add(n)) host.onPageFound(n) }
            k == "echo" -> host.playEcho(arg)
            k == "echo_available" -> st.echoesAvailable.add(arg)
            k == "secret" -> { if (st.secrets.add(arg)) host.onSecret(arg) }
            k == "releve" -> st.releves += arg.toIntOrNull() ?: 1
            k == "seq" -> { val n = arg.removePrefix("SQ-").toIntOrNull() ?: return; if (n > st.seq) { val oldAct = st.act(); st.seq = n; if (st.act() != oldAct) host.actCard(st.act()) } }
            k == "decision" -> { val p = arg.split('='); if (p.size == 2) st.decisions[p[0].trim()] = p[1].trim() }
            k == "solve" -> { if (st.puzzlesSolved.add(arg)) host.onPuzzleSolved(arg) }
            k == "set" -> { val p = arg.split('='); if (p.size == 2) st.vars[p[0].trim()] = p[1].trim() }
            k == "count" -> { val p = arg.split(' '); st.inc(p[0], p.getOrNull(1)?.replace(" ", "")?.toIntOrNull() ?: 1) }
            k == "filou_join" -> st.filouWithLohen = true
            k == "filou_wait" -> st.filouWithLohen = false
            k == "filou_react" -> host.filouReact(arg)
            k == "borne" -> { if (st.bornes.add(arg)) st.releves += 1 }
            k == "murmure" -> st.murmures.add(arg)
            k == "bell" -> arg.toIntOrNull()?.let { st.bells.add(it) }
            k == "label" -> arg.toIntOrNull()?.let { st.labelsRead.add(it) }
            k == "archive" -> st.archivesRead.add(arg)
            k == "chocolat" -> host.chocolat()
            k == "sfx" -> host.sfx(arg)
            k == "music" -> host.music(arg.takeIf { it != "none" && it != "off" })
            k == "ambience" -> host.ambience(arg.takeIf { it != "none" && it != "off" })
            k == "haptic" -> host.haptic(arg.toIntOrNull() ?: 40)
            k == "thought" -> host.thought(arg)
            k == "say" -> host.thoughtText(arg.removeSurrounding("\""))
            k == "toast" -> host.toast(arg.removeSurrounding("\""))
            k == "cin" -> host.cinematic(arg)
            k == "travel" -> { val p = arg.split(' '); host.travel(p[0], p.getOrNull(1)) }
            k == "puzzle" -> host.openPuzzle(arg)
            k == "scene" -> host.runScene(arg)
            k == "save" -> host.save(arg.removeSurrounding("\""))
            k == "checkpoint" -> host.checkpoint()
            k == "letter" -> host.openLetter()
            k == "epilogue" -> host.openEpilogue()
            k == "end_chapter" -> host.endChapter()
            k == "credits" -> host.credits()
            k == "carnet" -> host.openCarnet(arg.ifEmpty { null })
            k == "tuto" -> host.tutorial(arg)
            k == "act_card" -> host.actCard(arg.toIntOrNull() ?: st.act())
            k == "quit_to_title" -> host.quitToTitle()
            k == "retentissement" -> host.retentissement()
            k == "letter_read" -> st.letterRead = true
            k == "cooldown" -> host.cooldown(arg.split(' ').lastOrNull()?.toFloatOrNull() ?: 20f)
            k == "ending" -> st.ending = arg
            k == "goto" -> {} // géré par le dialogue
            k == "wait" -> {} // géré par le dialogue
            else -> host.log("effet inconnu: $e")
        }
    }

    companion object {
        /** Normalisation des noms de drapeaux : FLAG-NOM-REPEINT → nom_repeint. */
        fun norm(f: String) = f.trim().removePrefix("FLAG-").lowercase().replace('-', '_').replace("é", "e").replace("è", "e").replace("ê", "e").replace("à", "a")
    }
}
