package com.ateliermareebasse.cartographie.core.data

import com.ateliermareebasse.cartographie.core.platform.Platform

/**
 * Charge tout le contenu textuel d'une langue depuis assets/data.
 * Repli : si un fichier n'existe pas dans data/<type>/<lang>/, on prend data/<type>/fr/.
 */
class ContentLoader(private val platform: Platform) {

    private fun dir(type: String, lang: String): String {
        val d = "data/$type/$lang"
        return if (platform.listAssets(d).isNotEmpty()) d else "data/$type/fr"
    }

    private fun readAll(type: String, lang: String, ext: String): List<Pair<String, String>> {
        val d = dir(type, lang)
        return platform.listAssets(d).filter { it.endsWith(ext) }.sorted().mapNotNull { f ->
            platform.readAssetText("$d/$f")?.let { "$d/$f" to it }
        }
    }

    private fun read(type: String, lang: String, file: String): String? {
        val p = "data/$type/$lang/$file"
        return platform.readAssetText(p) ?: platform.readAssetText("data/$type/fr/$file")
    }

    fun load(lang: String, progress: ((Float, String) -> Unit)? = null): Content {
        Parsers.warnings.clear()
        progress?.invoke(0.05f, "zones")
        val zones = LinkedHashMap<String, Zone>()
        for ((f, t) in readAll("zones", lang, ".tab")) Parsers.parseZone(f, t)?.let { zones[it.id] = it }

        progress?.invoke(0.25f, "dialogues")
        val scenes = LinkedHashMap<String, Scene>()
        for ((f, t) in readAll("dialogues", lang, ".dlg")) Parsers.parseDialogues(f, t, scenes)
        val talk = LinkedHashMap<String, MutableList<TalkEntry>>()
        for ((f, t) in readAll("dialogues", lang, ".tbl")) Parsers.parseTalk(f, t, talk)

        progress?.invoke(0.45f, "énigmes")
        val puzzles = LinkedHashMap<String, PuzzleSheet>()
        for ((f, t) in readAll("puzzles", lang, ".pzl")) puzzles.putAll(Parsers.parsePuzzles(f, t))
        val items = LinkedHashMap<String, ItemDef>()
        for ((f, t) in readAll("items", lang, ".itm")) items.putAll(Parsers.parseItems(f, t))

        progress?.invoke(0.6f, "pages")
        val pages = read("pages", lang, "pages.pg")?.let { Parsers.parsePages(it) } ?: emptyMap()
        val echoes = read("echoes", lang, "echoes.ec")?.let { Parsers.parseEchoes("echoes.ec", it) } ?: emptyMap()
        val barks = read("barks", lang, "barks.brk")?.let { Parsers.parseBarks(it) } ?: emptyList()

        progress?.invoke(0.75f, "tables")
        val bells = read("tables", lang, "bells.txt")?.let { Parsers.parseBells(it) } ?: emptyMap()
        val bornes = read("tables", lang, "bornes.txt")?.let { Parsers.parseBornes(it) } ?: emptyMap()
        val murmures = read("tables", lang, "murmures.txt")?.let { Parsers.parseMurmures(it) } ?: emptyMap()
        val labels = read("tables", lang, "labels.txt")?.let { Parsers.parseLabels(it) } ?: emptyMap()
        val archives = read("tables", lang, "archives.txt")?.let { Parsers.parseArchives(it) } ?: emptyMap()
        val filou = read("tables", lang, "filou.txt")?.let { Parsers.parseFilou(it) } ?: emptyMap()
        val characters = read("tables", lang, "characters.txt")?.let { Parsers.parseCharacters(it) } ?: emptyMap()
        val retent = read("tables", lang, "retentissements.txt")?.let { Parsers.parseRetentissements(it) } ?: emptyList()
        val credits = read("tables", lang, "credits.txt")?.let { Parsers.parseCredits(it) } ?: emptyList()
        val letter = read("tables", lang, "letter.txt")?.let { Parsers.parseLetter(it) } ?: emptyList()
        val tutorials = read("tables", lang, "tutorials.txt")?.let { Parsers.parseBlocks(it) } ?: emptyMap()
        val epilogue = read("tables", lang, "epilogue_models.txt")?.let { Parsers.parseBlocks(it) } ?: emptyMap()
        val cinematics = read("cinematics", lang, "cinematics.cin")?.let { Parsers.parseCinematics("cinematics.cin", it) } ?: emptyMap()

        progress?.invoke(0.9f, "pensées")
        val thoughts = LinkedHashMap<String, String>()
        // Les pensées FR servent de base ; la langue demandée écrase.
        for ((_, t) in readAll("thoughts", "fr", ".txt")) thoughts.putAll(Parsers.parseKeyValues(t))
        if (lang != "fr") for ((_, t) in readAll("thoughts", lang, ".txt")) thoughts.putAll(Parsers.parseKeyValues(t))
        val strings = LinkedHashMap<String, String>()
        platform.readAssetText("data/strings/fr.txt")?.let { strings.putAll(Parsers.parseKeyValues(it)) }
        if (lang != "fr") platform.readAssetText("data/strings/$lang.txt")?.let { strings.putAll(Parsers.parseKeyValues(it)) }

        progress?.invoke(1f, "")
        return Content(lang, zones, scenes, talk, puzzles, items, pages, echoes, barks, bells, bornes, murmures, labels,
            archives, filou, thoughts, strings, characters, cinematics, retent, letter, credits, tutorials, epilogue)
    }
}
