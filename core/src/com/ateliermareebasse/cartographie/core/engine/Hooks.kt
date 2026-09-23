package com.ateliermareebasse.cartographie.core.engine

import com.ateliermareebasse.cartographie.core.data.Option

/**
 * Logique scénarisée qui ne tient pas dans les données : cooldowns émotionnels,
 * retentissement après SQ-15, chaîne du final, secrets à compteur, S11 (monsieur Éraflé).
 */
class Hooks(private val game: Game) {
    private val st get() = game.state

    fun onEnterTableau(zone: String, tab: String) {
        // Le monde entier veille aux Heures Bleues : ambiance spécifique hors intérieurs
        if (st.act() == 4 && !st.letterRead && game.content.tableau(zone, tab)?.props?.get("interior") == null) game.ambience("amb_pointe_nuit")
        // Filou revient après la lettre
        if (st.letterRead && !st.filouWithLohen && zone != "z17") st.filouWithLohen = true
        // premier tutoriel du carnet quand on quitte le Cabinet
        if (zone == "z02" && "carnet" !in st.tutosSeen) game.tutorial("carnet")
    }

    fun onSceneStart(id: String) {
        when (id) {
            "sq02_ombeline" -> game.music("track_02")
        }
    }

    fun onSay(scene: String, speaker: String) {}

    fun onChoice(scene: String, o: Option) {}

    fun onSceneEnd(id: String) {
        when (id) {
            // après G-15 (révélation D-01) : 60 s de retenue avant tout bark
            "sq05_revelation" -> game.emotionalCooldown = 60f
            // fin de l'acte III : feuillet « ce que le monde a retenu de toi »
            "sq16_marek_girouette" -> { game.retentissement(); game.checkpoint() }
            "sq20_marek_terrasse" -> { game.autosave(); game.openEpilogue() }
            "sq03_filou_muret" -> { st.filouWithLohen = true; game.tutorial("filou_join") }
            "pnr_passage" -> if ("passage_engage" in st.flags) game.cinematic("CIN-05") { game.travel("z17", "t01") }
            "cloche_sans_nom" -> if ("cloche_sonnee" in st.flags) st.flags.add("bark_cloche_marche")
        }
        // S02 : trois horloges
        if (st.count("s02") >= 3 && "S02" !in st.secrets) game.effects.apply(listOf("secret S02", "echo E-03", "CLARTE +1"))
        // S11 : monsieur Éraflé dans les 18 zones
        if (st.flags.count { it.startsWith("s11_") } >= 18 && "S11" !in st.secrets) game.effects.apply(listOf("secret S11", "thought secret.s11_done"))
        // S12 : les douze cailloux
        if (st.count("s12") >= 12 && "S12" !in st.secrets) game.effects.apply(listOf("secret S12", "thought secret.s12_done"))
    }

    /** Appelé quand une énigme est résolue (avant la scène « after »). */
    fun onPuzzleSolved(id: String) {
        when (id) {
            "E15" -> {}
            "E16" -> {}
        }
    }

    /** Lignes du feuillet de retentissement : les 7 premières vraies. */
    fun retentissementLines(): List<String> =
        game.content.retentissements.filter { game.conditions.eval(it.cond) }.map { it.text.replace("{0}", st.chocolats.toString()) }.take(7)
}
