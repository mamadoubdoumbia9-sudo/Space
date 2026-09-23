package com.ateliermareebasse.cartographie.core.engine

import com.ateliermareebasse.cartographie.core.platform.Platform

/** Options persistantes (fichier settings.txt : clé=valeur). */
class Settings(private val platform: Platform) {
    enum class Quality { LOW, MEDIUM, HIGH, ULTRA }
    enum class HintMode { STANDARD, PURISTE, ASSURE }

    var quality = Quality.HIGH
    var master = 1f
    var music = 0.8f
    var ambience = 0.8f
    var sfx = 1f
    var language = "fr"
    var sensitivity = 1f        // vitesse de défilement / drag
    var vibration = true
    var textScale = 1f
    var hintMode = HintMode.STANDARD
    var subtitles = true
    var reduceMotion = false
    var highContrast = false
    var leftHanded = false
    var autosave = true
    var firstRun = true
    var lastSlot = 1
    var uiScale = 1f

    fun load() {
        val t = platform.readFile("settings.txt")?.toString(Charsets.UTF_8) ?: run { language = platform.systemLanguage().takeIf { it == "en" } ?: "fr"; return }
        for (line in t.lines()) {
            val k = line.substringBefore('=').trim(); val v = line.substringAfter('=', "").trim()
            when (k) {
                "quality" -> quality = runCatching { Quality.valueOf(v) }.getOrDefault(Quality.HIGH)
                "master" -> master = v.toFloatOrNull() ?: 1f
                "music" -> music = v.toFloatOrNull() ?: 0.8f
                "ambience" -> ambience = v.toFloatOrNull() ?: 0.8f
                "sfx" -> sfx = v.toFloatOrNull() ?: 1f
                "language" -> language = v.ifEmpty { "fr" }
                "sensitivity" -> sensitivity = v.toFloatOrNull() ?: 1f
                "vibration" -> vibration = v == "true"
                "textScale" -> textScale = v.toFloatOrNull() ?: 1f
                "hintMode" -> hintMode = runCatching { HintMode.valueOf(v) }.getOrDefault(HintMode.STANDARD)
                "subtitles" -> subtitles = v != "false"
                "reduceMotion" -> reduceMotion = v == "true"
                "highContrast" -> highContrast = v == "true"
                "leftHanded" -> leftHanded = v == "true"
                "autosave" -> autosave = v != "false"
                "firstRun" -> firstRun = v != "false"
                "lastSlot" -> lastSlot = v.toIntOrNull() ?: 1
                "uiScale" -> uiScale = v.toFloatOrNull() ?: 1f
            }
        }
    }

    fun save() {
        val sb = StringBuilder()
        sb.append("quality=$quality\nmaster=$master\nmusic=$music\nambience=$ambience\nsfx=$sfx\nlanguage=$language\n")
        sb.append("sensitivity=$sensitivity\nvibration=$vibration\ntextScale=$textScale\nhintMode=$hintMode\nsubtitles=$subtitles\n")
        sb.append("reduceMotion=$reduceMotion\nhighContrast=$highContrast\nleftHanded=$leftHanded\nautosave=$autosave\nfirstRun=$firstRun\nlastSlot=$lastSlot\nuiScale=$uiScale\n")
        platform.writeFile("settings.txt", sb.toString().toByteArray(Charsets.UTF_8))
    }

    /** Budget d'effets selon la qualité (particules, grain, vignette, parallaxe). */
    val particles: Int get() = when (quality) { Quality.LOW -> 0; Quality.MEDIUM -> 24; Quality.HIGH -> 60; Quality.ULTRA -> 120 }
    val grain: Boolean get() = quality >= Quality.HIGH
    val vignette: Boolean get() = quality >= Quality.MEDIUM
    val parallax: Boolean get() = quality >= Quality.MEDIUM && !reduceMotion
    val imageScale: Float get() = when (quality) { Quality.LOW -> 0.5f; Quality.MEDIUM -> 0.75f; else -> 1f }
}
