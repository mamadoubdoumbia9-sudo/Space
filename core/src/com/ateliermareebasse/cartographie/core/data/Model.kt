package com.ateliermareebasse.cartographie.core.data

/** Modèle de données du monde (chargé depuis assets/data/…). */

class Zone(
    val id: String,
    val name: String,
    val subtitle: String,
    val act: Int,
    val palette: String,
    val music: String?,
    val ambience: String?,
    val openCond: String?,      // condition d'ouverture sur la carte
    val mapX: Float, val mapY: Float,
    val hidden: Boolean,
    val tableaux: List<Tableau>,
    val start: String,          // tableau d'entrée par défaut
) {
    fun tableau(id: String) = tableaux.firstOrNull { it.id == id } ?: tableaux.first()
}

class Tableau(
    val id: String,
    val zoneId: String,
    val name: String,
    val image: String,
    val props: Map<String, String>,     // interior, tide_view, light, overlay, dark, particles, music, ambience…
    val enterThoughts: List<Pair<String, String?>>,   // (clé de pensée, condition)
    val enterScenes: List<Pair<String, String?>>,     // (scène de dialogue, condition)
    val hotspots: List<Hotspot>,
    val paths: List<PathLink>,
    val npcs: List<NpcSpot>,
    val barkZone: String,
)

class Hotspot(
    val kind: String,           // EXAMINE LISTEN TAKE USE PUZZLE TALK READ BELL BORNE MURMURE SECRET ECHO SIT LABEL ACTION FILOU PAGE
    val id: String,
    val x: Float, val y: Float,
    val label: String,
    val params: Map<String, String>,
    val cond: String?,
    val effects: List<String>,
    val radius: Float = 0.055f,
)

class PathLink(
    val target: String,         // "z05" (zone) ou "z05:t02" (zone:tableau) ou "t02" (même zone)
    val label: String,
    val cond: String?,
    val lockedKey: String?,     // pensée affichée si condition fausse
    val hidden: Boolean,
    val strict: Boolean,        // n'apparaît pas tant que la condition est fausse
    val back: Boolean,
    val cin: String?,
    val effects: List<String>,
    val x: Float, val y: Float,
    val kind: String,           // "arrow" | "door" | "map"
)

class NpcSpot(val id: String, val x: Float, val y: Float, val cond: String?, val scale: Float)

/** Nœud de dialogue. */
sealed class Line {
    data class Say(val speaker: String, val text: String) : Line()
    data class Stage(val text: String) : Line()
    data class Thought(val text: String) : Line()
    data class Choice(val options: List<Option>) : Line()
    data class Effects(val effects: List<String>) : Line()
    data class Label(val name: String) : Line()
    data class Goto(val label: String, val cond: String?) : Line()
    data class Cue(val cue: String, val arg: String) : Line()
    object End : Line()
}

class Option(val text: String, val gold: Boolean, val cond: String?, val effects: List<String>, val goto: String?)

class Scene(val id: String, val cond: String?, val once: Boolean, val lines: List<Line>, val file: String) {
    val labels: Map<String, Int> = HashMap<String, Int>().also { m ->
        lines.forEachIndexed { i, l -> if (l is Line.Label) m[l.name] = i }
    }
}

class TalkEntry(val scene: String, val cond: String?)

class PuzzleSheet(
    val id: String, val name: String, val zone: String, val phrase: String, val intro: String?,
    val hints: List<Pair<String, String>>, val success: String, val effects: List<String>, val after: String?, val sfx: String?,
)

class ItemDef(
    val id: String, val name: String, val category: String, val sniff: String?, val note: String?,
    val thoughts: Map<String, String>,   // "1","2","10","10+"
    val hold: Boolean,
)

class PageDef(val n: Int, val where: String, val date: String, val birds: Int, val lines: List<String>)

class EchoDef(val id: String, val title: String, val year: String, val seconds: Int, val lines: List<Line>)

class Bark(val zone: String, val trait: String, val text: String)

class BellDef(val n: Int, val name: String, val text: String, val note: String)
class BorneDef(val id: String, val place: String, val hand: String, val text: String)
class MurmureDef(val id: String, val title: String, val cond: String, val text: String)
class LabelDef(val n: Int, val text: String)
class ArchiveDef(val id: String, val title: String, val lines: List<String>)
class FilouReaction(val key: String, val behaviour: String, val thought: String)
class CharacterDef(val id: String, val name: String, val role: String, val portrait: String, val palette: Int, val bio: String)
class CinematicDef(val id: String, val title: String, val shots: List<CinShot>, val music: String?, val ambience: String?, val skippable: Boolean)
class CinShot(val image: String?, val seconds: Float, val caption: String?, val speaker: String?, val effect: String?, val sfx: String?)
class Retentissement(val cond: String, val text: String)
class CreditsLine(val kind: String, val text: String)

/** Contenu complet chargé pour une langue. */
class Content(
    val lang: String,
    val zones: Map<String, Zone>,
    val scenes: Map<String, Scene>,
    val talk: Map<String, List<TalkEntry>>,
    val puzzles: Map<String, PuzzleSheet>,
    val items: Map<String, ItemDef>,
    val pages: Map<Int, PageDef>,
    val echoes: Map<String, EchoDef>,
    val barks: List<Bark>,
    val bells: Map<Int, BellDef>,
    val bornes: Map<String, BorneDef>,
    val murmures: Map<String, MurmureDef>,
    val labels: Map<Int, LabelDef>,
    val archives: Map<String, ArchiveDef>,
    val filou: Map<String, FilouReaction>,
    val thoughts: Map<String, String>,
    val strings: Map<String, String>,
    val characters: Map<String, CharacterDef>,
    val cinematics: Map<String, CinematicDef>,
    val retentissements: List<Retentissement>,
    val letter: List<Pair<String, List<String>>>,   // (titre du pli, paragraphes)
    val credits: List<CreditsLine>,
    val tutorials: Map<String, List<String>>,
    val epilogueModels: Map<String, List<String>>,  // A/B/C : paragraphes de la réponse
) {
    fun str(key: String, vararg args: Any): String {
        var s = strings[key] ?: "[$key]"
        args.forEachIndexed { i, a -> s = s.replace("{$i}", a.toString()) }
        return s
    }
    fun thought(key: String): String? = thoughts[key]
    fun tableau(zoneId: String, tabId: String): Tableau? = zones[zoneId]?.tableaux?.firstOrNull { it.id == tabId }
}
