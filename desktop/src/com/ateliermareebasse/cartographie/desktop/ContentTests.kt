package com.ateliermareebasse.cartographie.desktop

import com.ateliermareebasse.cartographie.core.data.Content
import com.ateliermareebasse.cartographie.core.data.ContentLoader
import com.ateliermareebasse.cartographie.core.data.Line
import com.ateliermareebasse.cartographie.core.data.Parsers
import com.ateliermareebasse.cartographie.core.engine.Game
import com.ateliermareebasse.cartographie.core.platform.Input
import com.ateliermareebasse.cartographie.core.puzzles.E05Aiguillage
import com.ateliermareebasse.cartographie.core.puzzles.PuzzleFactory
import com.ateliermareebasse.cartographie.core.screens.DialogueScreen
import com.ateliermareebasse.cartographie.core.state.GameState
import java.io.File

/**
 * Suite de tests de contenu et de moteur (audit pré-build, 12.10 / 15.7).
 * Chaque test écrit ses erreurs ; la suite échoue si au moins une erreur.
 */
class ContentTests(val root: File) {
    private val errors = ArrayList<String>()
    private val warns = ArrayList<String>()
    private fun err(m: String) = errors.add(m)
    private fun warn(m: String) = warns.add(m)
    private val srcDirs = listOf(File(System.getProperty("src.dir") ?: "core/src"), File("app/src/main/kotlin"))

    private val stateEffectKeys = setOf("flag", "unflag", "give", "take", "page", "echo", "echo_available", "secret", "releve", "seq", "decision", "solve", "set", "count", "filou_join", "filou_wait", "filou_react", "borne", "murmure", "bell", "label", "archive", "chocolat", "sfx", "music", "ambience", "haptic", "thought", "say", "toast", "cin", "travel", "puzzle", "scene", "save", "checkpoint", "letter", "epilogue", "end_chapter", "credits", "carnet", "tuto", "act_card", "quit_to_title", "retentissement", "letter_read", "ending", "goto", "wait", "cooldown", "CLARTE", "FILOU")

    var only: String? = null
    fun runAll(): Boolean {
        val platform = DesktopPlatform(root, File("build/test_data").also { it.deleteRecursively() })
        val audio = DesktopAudio()
        DesktopPainter.decodeImages = false   // les tests ne décodent pas les images (vitesse) ; leur présence est vérifiée à part
        println("── Tests de contenu : ${root.absolutePath}")
        val content = ContentLoader(platform).load("fr")
        section("1. Analyse des fichiers") { Parsers.warnings.forEach { err("parser: $it") } ; println("   zones=${content.zones.size} scènes=${content.scenes.size} énigmes=${content.puzzles.size} objets=${content.items.size} pages=${content.pages.size} échos=${content.echoes.size} cloches=${content.bells.size} bornes=${content.bornes.size} murmures=${content.murmures.size} étiquettes=${content.labels.size} pensées=${content.thoughts.size} chaînes=${content.strings.size}") }
        section("2. Volumes attendus (GDD)") {
            if (content.zones.size < 18) err("zones: ${content.zones.size} < 18")
            if (content.pages.size != 24) err("pages: ${content.pages.size} ≠ 24")
            if (content.echoes.size < 12) err("échos: ${content.echoes.size} < 12")
            if (content.bells.size != 43) err("cloches: ${content.bells.size} ≠ 43")
            if (content.bornes.size != 40) err("bornes: ${content.bornes.size} ≠ 40")
            if (content.murmures.size < 18) err("murmures: ${content.murmures.size} < 18")
            if (content.labels.size < 50) err("étiquettes: ${content.labels.size} < 50")
            for (e in 1..16) if (!content.puzzles.containsKey("E%02d".format(e))) err("fiche d'énigme manquante E%02d".format(e))
            if (content.letter.count { it.first.startsWith("PLI") } != 11) err("lettre: ${content.letter.size} plis ≠ 11")
            if (content.credits.isEmpty()) err("crédits vides")
            if (content.retentissements.size < 12) err("retentissements: ${content.retentissements.size} < 12")
            if (content.characters.size < 15) err("personnages: ${content.characters.size} < 15")
            if (content.cinematics.size < 8) err("cinématiques: ${content.cinematics.size} < 8")
            for (t in listOf("explore", "carnet", "sacoche", "filou", "filou_join", "gold", "puzzle", "bornes", "epilogue")) if (!content.tutorials.containsKey(t)) err("tutoriel manquant: $t")
            for (m in listOf("A", "B", "C")) if (!content.epilogueModels.containsKey(m)) err("modèle d'épilogue manquant: $m")
        }
        section("3. Zones : images, chemins, points d'intérêt") { checkZones(content, platform) }
        section("4. Dialogues : étiquettes, effets, conditions, références") { checkScenes(content, platform) }
        section("5. Tables de conversation et énigmes") {
            for ((npc, entries) in content.talk) for (e in entries) if (!content.scenes.containsKey(e.scene)) err("talk $npc: scène inconnue ${e.scene}")
            for (pz in content.puzzles.values) { checkEffects("énigme ${pz.id}", pz.effects, content, platform); pz.after?.split('|')?.map { it.trim() }?.forEach { if (!content.scenes.containsKey(it)) err("énigme ${pz.id}: scène after inconnue $it") }; if (pz.hints.size != 3) err("énigme ${pz.id}: ${pz.hints.size} indices ≠ 3"); if (pz.success.isEmpty()) err("énigme ${pz.id}: pas de conclusion") }
            for (id in PuzzleFactory.all) if (!content.puzzles.containsKey(id)) err("fiche manquante pour l'écran d'énigme $id")
        }
        section("6. Cinématiques, personnages, objets") {
            for (c in content.cinematics.values) { if (c.shots.isEmpty()) err("cin ${c.id}: aucun plan"); for (sh in c.shots) sh.image?.let { if (!platform.assetExists(it)) err("cin ${c.id}: image manquante $it") } }
            for (id in listOf("CIN-01", "CIN-07", "CIN-08", "CIN-08b")) if (!content.cinematics.containsKey(id)) err("cinématique requise absente: $id")
            for (ch in content.characters.values) if (!platform.assetExists(ch.portrait)) warn("portrait manquant ${ch.portrait}")
            for (it in content.items.values) { if (!it.glyph && !platform.assetExists("art/items/${it.id}.png")) err("icône manquante art/items/${it.id}.png"); if (it.glyph && platform.assetExists("art/items/${it.id}.png")) warn("objet ${it.id} marqué glyph mais une icône existe"); if (it.thoughts["1"] == null) err("objet ${it.id}: pas de pensée 1") }
            if (!platform.assetExists("art/ui/map_world.jpg")) err("carte du monde manquante art/ui/map_world.jpg")
        }
        section("7. Chaînes de l'interface (fr/en)") { checkStrings(content, platform) }
        section("8. Sauvegarde : aller-retour, somme de contrôle, versions") { checkSave() }
        section("9. Conditions et effets (unités)") { checkConditions(platform, audio, content) }
        section("10. E05 : aucun état atteignable n'est bloquant (+ levier maître)") { if (!E05Aiguillage.reachabilityCheck()) err("E05: un état atteignable ne rejoint pas la cible") }
        section("11. Parcours simulé : toutes les scènes, toutes les énigmes, chaîne du final") { simulate(platform, audio, content) }
        section("12. Graphe des zones : tout est joignable depuis le Cabinet") { checkGraph(content) }
        section("13. Audio référencé") { checkAudio(content, platform) }
        section("14. Anglais : chaque scène, énigme, pensée a un repli") { val en = ContentLoader(platform).load("en"); if (en.scenes.size < content.scenes.size) err("en: ${en.scenes.size} scènes < ${content.scenes.size}"); if (en.strings.size < content.strings.size) err("en: chaînes manquantes") }
        println()
        warns.forEach { println("   ⚠ $it") }
        errors.forEach { println("   ✗ $it") }
        println("── ${errors.size} erreur(s), ${warns.size} avertissement(s)")
        return errors.isEmpty()
    }

    private fun section(title: String, body: () -> Unit) {
        if (only != null && !title.startsWith("$only.")) return
        val before = errors.size; print("▶ $title … "); System.out.flush()
        try { body() } catch (e: Throwable) { err("$title: exception $e"); e.printStackTrace() }
        println(if (errors.size == before) "ok" else "${errors.size - before} erreur(s)")
        for (i in before until errors.size) println("   ✗ ${errors[i]}")
    }

    private fun checkEffects(where: String, effects: List<String>, content: Content, platform: DesktopPlatform) {
        for (e in effects) {
            val k = e.substringBefore(' ').trim(); val arg = e.substringAfter(' ', "").trim()
            when {
                k.startsWith("+") || k.startsWith("-") -> if (k.substring(1) !in setOf("DOUCEUR", "COURAGE", "RUSE", "HUMEUR")) err("$where: trait inconnu $e")
                k.startsWith("LIEN-") -> if (k.removePrefix("LIEN-") !in setOf("OMBELINE", "ANSELME", "SIDONIE", "BAZ", "YSOLDE", "MAREK", "EMERIC", "ADELE", "JUMEAUX", "MARTA", "ROSA", "ARISTIDE")) err("$where: PNJ de lien inconnu $k")
                k !in stateEffectKeys -> err("$where: effet inconnu '$e'")
                k == "give" || k == "take" -> if (!content.items.containsKey(arg.substringBefore(' '))) err("$where: objet inconnu ${arg.substringBefore(' ')}")
                k == "page" -> if ((arg.toIntOrNull() ?: 0) !in 1..24) err("$where: page invalide $arg")
                k == "echo" || k == "echo_available" -> if (!content.echoes.containsKey(arg)) err("$where: écho inconnu $arg")
                k == "scene" -> if (!content.scenes.containsKey(arg)) err("$where: scène inconnue $arg")
                k == "cin" -> if (!content.cinematics.containsKey(arg)) err("$where: cinématique inconnue $arg")
                k == "travel" -> { val z = arg.substringBefore(' '); val t = arg.substringAfter(' ', ""); if (!content.zones.containsKey(z)) err("$where: zone inconnue $z") else if (t.isNotEmpty() && content.zones[z]!!.tableaux.none { it.id == t }) err("$where: tableau inconnu $arg") }
                k == "puzzle" -> if (!content.puzzles.containsKey(arg)) err("$where: énigme inconnue $arg")
                k == "thought" -> if (!content.thoughts.containsKey(arg) && !content.thoughts.containsKey("$arg.DOUCEUR")) err("$where: pensée inconnue $arg")
                k == "tuto" -> if (!content.tutorials.containsKey(arg)) err("$where: tutoriel inconnu $arg")
                k == "filou_react" -> if (!content.filou.containsKey(arg)) err("$where: réaction Filou inconnue $arg")
                k == "borne" -> if (!content.bornes.containsKey(arg)) err("$where: borne inconnue $arg")
                k == "murmure" -> if (!content.murmures.containsKey(arg)) err("$where: murmure inconnu $arg")
                k == "sfx" -> if (!platform.assetExists("audio/sfx/$arg.ogg")) warn("$where: sfx manquant $arg")
                k == "music" -> if (arg != "off" && arg != "none" && !platform.assetExists("audio/music/$arg.ogg")) warn("$where: musique manquante $arg")
                k == "ambience" -> if (arg != "off" && arg != "none" && !platform.assetExists("audio/ambience/$arg.ogg")) warn("$where: ambiance manquante $arg")
            }
        }
    }

    private val condAtomRe = Regex("""[A-Za-z_+][A-Za-z0-9_+:\-.=<>! ]*""")
    private fun checkCond(where: String, cond: String?, content: Content) {
        if (cond.isNullOrBlank()) return
        for (raw in cond.split('&', '|', '(', ')')) {
            val a = raw.trim().removePrefix("!").trim()
            if (a.isEmpty()) continue
            val colon = a.indexOf(':')
            if (colon > 0 && !a.contains(">") && !a.contains("<")) {
                val k = a.substring(0, colon); val v = a.substring(colon + 1).substringBefore('=').trim()
                when (k) {
                    "has" -> if (!content.items.containsKey(v)) err("$where: condition has: objet inconnu $v")
                    "echo", "echo_available" -> if (!content.echoes.containsKey(v)) err("$where: condition écho inconnu $v")
                    "puzzle", "solved" -> if (!content.puzzles.containsKey(v)) err("$where: condition énigme inconnue $v")
                    "zone", "visited" -> if (!content.zones.containsKey(v)) err("$where: condition zone inconnue $v")
                    "talked", "seen" -> if (!content.scenes.containsKey(v)) err("$where: condition scène inconnue $v")
                    "page" -> if ((v.toIntOrNull() ?: 0) !in 1..24) err("$where: condition page invalide $v")
                    "npc_here" -> if (!content.characters.containsKey(v)) err("$where: npc inconnu $v")
                    "flag", "secret", "tableau", "tide", "decision", "cin", "borne", "murmure", "bell", "lang", "ending", "trait", "var" -> {}
                    else -> if (k !in setOf("examined", "count")) err("$where: condition inconnue '$a'")
                }
            } else if (colon < 0 && !a.contains(">") && !a.contains("<") && !a.contains("=")) {
                if (a !in setOf("ng+", "ng", "letter_read", "chapter_done", "low_tide", "hb", "filou_here", "true", "false")) err("$where: condition atomique inconnue '$a'")
            }
        }
    }

    private fun checkZones(content: Content, platform: DesktopPlatform) {
        val seenImages = HashSet<String>()
        for (z in content.zones.values) {
            if (z.name.isEmpty()) err("zone ${z.id} sans nom")
            checkCond("zone ${z.id}", z.openCond, content)
            z.music?.let { if (!platform.assetExists("audio/music/$it.ogg")) warn("zone ${z.id}: musique manquante $it") }
            z.ambience?.let { if (!platform.assetExists("audio/ambience/$it.ogg")) warn("zone ${z.id}: ambiance manquante $it") }
            for (t in z.tableaux) {
                val where = "tableau ${z.id}:${t.id}"
                if (t.image.isEmpty()) err("$where: pas d'image") else if (!platform.assetExists(t.image)) err("$where: image manquante ${t.image}") else seenImages.add(t.image)
                for ((k, c) in t.enterThoughts) { val key = k.removeSuffix("!"); if (!content.thoughts.containsKey(key) && !content.thoughts.containsKey("$key.act1") && !content.thoughts.containsKey("$key.DOUCEUR")) err("$where: pensée d'entrée inconnue $key"); checkCond(where, c, content) }
                for ((sc, c) in t.enterScenes) { if (!content.scenes.containsKey(sc)) err("$where: scène d'entrée inconnue $sc"); checkCond(where, c, content) }
                for (h in t.hotspots) {
                    checkCond("$where hot ${h.id}", h.cond, content)
                    checkEffects("$where hot ${h.id}", h.effects, content, platform)
                    if (h.x !in 0f..1f || h.y !in 0f..1f) err("$where hot ${h.id}: position hors cadre")
                    h.params["think"]?.let { if (!content.thoughts.containsKey(it) && !content.thoughts.containsKey("$it.act1") && !content.thoughts.containsKey("$it.DOUCEUR")) err("$where hot ${h.id}: pensée inconnue $it") }
                    h.params["sniff"]?.let { if (!content.filou.containsKey(it)) err("$where hot ${h.id}: sniff inconnu $it") }
                    h.params["scene"]?.let { if (!content.scenes.containsKey(it)) err("$where hot ${h.id}: scène inconnue $it") }
                    h.params["need"]?.let { if (!content.thoughts.containsKey(it)) err("$where hot ${h.id}: pensée need inconnue $it") }
                    h.params["done"]?.let { if (!content.thoughts.containsKey(it)) err("$where hot ${h.id}: pensée done inconnue $it") }
                    h.params["text"]?.let { if (!content.thoughts.containsKey(it)) err("$where hot ${h.id}: texte inconnu $it") }
                    h.params["item"]?.let { if (!content.items.containsKey(it)) err("$where hot ${h.id}: objet inconnu $it") }
                    h.params["archive"]?.let { if (!content.archives.containsKey(it)) err("$where hot ${h.id}: archive inconnue $it") }
                    when (h.kind) {
                        "EXAMINE", "LISTEN" -> { val key = h.params["think"] ?: "${z.id}.${h.id}"; if (!content.thoughts.containsKey(key) && !content.thoughts.containsKey("$key.act1") && !content.thoughts.containsKey("$key.DOUCEUR")) err("$where hot ${h.id}: pensée d'examen inconnue $key") }
                        "TAKE" -> if (!content.items.containsKey(h.params["item"] ?: h.id)) err("$where hot ${h.id}: objet inconnu")
                        "PUZZLE" -> if (!content.puzzles.containsKey(h.id)) err("$where hot ${h.id}: énigme inconnue") else if (PuzzleFactory.create(Game(platform, DesktopAudio()).also { it.painter = DesktopPainter(platform, 320, 180); it.content = content }, h.id) == null) err("$where: pas d'écran pour l'énigme ${h.id}")
                        "TALK" -> if (!content.talk.containsKey(h.id) && !content.scenes.containsKey(h.id)) err("$where hot ${h.id}: aucune table de conversation")
                        "READ", "PAGE" -> { val pg = (h.params["page"] ?: if (h.kind == "PAGE") h.id.removePrefix("page") else null)?.toIntOrNull(); if (pg != null && pg !in 1..24) err("$where hot ${h.id}: page invalide") }
                        "BELL" -> if (!content.bells.containsKey(h.id.toIntOrNull() ?: -1)) err("$where hot ${h.id}: cloche inconnue")
                        "BORNE" -> if (!content.bornes.containsKey(h.id)) err("$where hot ${h.id}: borne inconnue")
                        "MURMURE" -> if (!content.murmures.containsKey(h.id)) err("$where hot ${h.id}: murmure inconnu")
                        "LABEL" -> if (!content.labels.containsKey(h.id.toIntOrNull() ?: -1)) err("$where hot ${h.id}: étiquette inconnue")
                        "ECHO" -> if (!content.echoes.containsKey(h.id)) err("$where hot ${h.id}: écho inconnu")
                        "FILOU" -> if (!content.filou.containsKey(h.id)) err("$where hot ${h.id}: réaction inconnue")
                        "CIN" -> if (!content.cinematics.containsKey(h.id)) err("$where hot ${h.id}: cinématique inconnue")
                        "SECRET", "ACTION", "SIT", "USE" -> {}
                        else -> err("$where hot ${h.id}: type inconnu ${h.kind}")
                    }
                }
                for (pth in t.paths) {
                    val (zz, tt) = when { pth.target.contains(':') -> pth.target.substringBefore(':') to pth.target.substringAfter(':'); pth.target.startsWith("t") && pth.target.length <= 3 -> z.id to pth.target; else -> pth.target to null }
                    val zone = content.zones[zz]
                    if (zone == null) err("$where path: zone inconnue ${pth.target}") else if (tt != null && zone.tableaux.none { it.id == tt }) err("$where path: tableau inconnu ${pth.target}")
                    checkCond("$where path ${pth.target}", pth.cond, content)
                    checkEffects("$where path ${pth.target}", pth.effects, content, platform)
                    pth.lockedKey?.let { if (!content.thoughts.containsKey(it)) err("$where path ${pth.target}: pensée locked inconnue $it") }
                    pth.cin?.let { if (!content.cinematics.containsKey(it)) err("$where path: cinématique inconnue $it") }
                    if (pth.label.isEmpty()) err("$where path ${pth.target}: sans libellé")
                }
                for (n in t.npcs) { if (!content.characters.containsKey(n.id)) err("$where npc ${n.id}: personnage inconnu"); if (!content.talk.containsKey(n.id)) err("$where npc ${n.id}: pas de table de conversation"); checkCond("$where npc ${n.id}", n.cond, content) }
                if (t.hotspots.isEmpty() && t.paths.isEmpty()) err("$where: tableau vide")
            }
        }
        val imgs = platform.listAssets("art/zones").filter { it.endsWith(".jpg") }
        for (i in imgs) if ("art/zones/$i" !in seenImages) warn("image de zone non référencée art/zones/$i")
    }

    private fun checkScenes(content: Content, platform: DesktopPlatform) {
        for (sc in content.scenes.values) {
            val where = "scène ${sc.id}"
            checkCond(where, sc.cond, content)
            for (l in sc.lines) when (l) {
                is Line.Goto -> { if (!sc.labels.containsKey(l.label)) err("$where: label inconnu ${l.label}"); checkCond(where, l.cond, content) }
                is Line.Effects -> checkEffects(where, l.effects, content, platform)
                is Line.Choice -> for (o in l.options) { checkCond(where, o.cond, content); checkEffects(where, o.effects, content, platform); o.goto?.let { if (!sc.labels.containsKey(it)) err("$where: label inconnu $it") } }
                is Line.Cue -> when (l.cue) { "sfx" -> if (!platform.assetExists("audio/sfx/${l.arg}.ogg")) warn("$where: sfx manquant ${l.arg}"); "music" -> if (l.arg != "off" && !platform.assetExists("audio/music/${l.arg}.ogg")) warn("$where: musique manquante ${l.arg}"); "voice" -> if (!platform.assetExists("audio/voice/${l.arg}.ogg")) warn("$where: voix manquante ${l.arg}"); "wait", "haptic", "ambience", "shake" -> {}; else -> err("$where: cue inconnu ${l.cue}") }
                is Line.Say -> { val k = l.speaker.lowercase().replace('é', 'e'); if (!content.characters.containsKey(k) && !content.strings.containsKey("speaker.$k")) err("$where: locuteur inconnu ${l.speaker}"); if (l.text.contains("[[") && !Regex("""\[\[[^|\]]*\|[^\]]*]]""").containsMatchIn(l.text)) err("$where: substitution malformée") }
                else -> {}
            }
            if (sc.lines.none { it is Line.Say || it is Line.Thought || it is Line.Stage || it is Line.Cue || it is Line.Effects }) err("$where: scène sans contenu")
        }
    }

    private fun checkStrings(content: Content, platform: DesktopPlatform) {
        val fr = Parsers.parseKeyValues(platform.readAssetText("data/strings/fr.txt") ?: "")
        val en = Parsers.parseKeyValues(platform.readAssetText("data/strings/en.txt") ?: "")
        val used = HashSet<String>()
        val re = Regex("""str\("([a-zA-Z0-9_.]+)"""")
        val re2 = Regex("""game\.str\("([a-zA-Z0-9_.]+)"""")
        for (d in srcDirs) if (d.isDirectory) d.walkTopDown().filter { it.extension == "kt" }.forEach { f -> val t = f.readText(); re.findAll(t).forEach { used.add(it.groupValues[1]) }; re2.findAll(t).forEach { used.add(it.groupValues[1]) } }
        for (k in used) { if (!fr.containsKey(k)) err("chaîne fr manquante: $k"); if (!en.containsKey(k)) err("chaîne en manquante: $k") }
        for (k in fr.keys) if (!en.containsKey(k)) err("chaîne en manquante (présente en fr): $k")
        // clés dynamiques
        for (t in listOf("PM1", "BM", "PM2", "PM3", "PM4", "PM5")) for (m in listOf(fr, en)) if (!m.containsKey("tide.$t")) err("chaîne manquante tide.$t")
        for (a in 1..5) for (m in listOf(fr, en)) if (!m.containsKey("act.$a")) err("chaîne manquante act.$a")
        for (a in 1..4) for (m in listOf(fr, en)) if (!m.containsKey("act.card_$a")) err("chaîne manquante act.card_$a")
        for (t in listOf("carte", "sacoche", "pages", "echos", "gens", "journal")) for (m in listOf(fr, en)) if (!m.containsKey("carnet.tab_$t")) err("chaîne manquante carnet.tab_$t")
        for (e in listOf("lettre", "poursuite", "boussole", "carte")) for (m in listOf(fr, en)) if (!m.containsKey("end.fe_$e")) err("chaîne manquante end.fe_$e")
        for (q in listOf("LOW", "MEDIUM", "HIGH", "ULTRA")) for (m in listOf(fr, en)) if (!m.containsKey("set.q_$q")) err("chaîne manquante set.q_$q")
        for (q in listOf("STANDARD", "PURISTE", "ASSURE")) for (m in listOf(fr, en)) if (!m.containsKey("set.hint_$q")) err("chaîne manquante set.hint_$q")
        for (i in 1..4) if (!fr.containsKey("world.pet_$i")) err("chaîne manquante world.pet_$i")
        for (i in 1..11) if (!content.thoughts.containsKey("z03.chocolat_$i")) err("pensée manquante z03.chocolat_$i")
        for (sid in listOf("S03", "S04", "S05", "S07", "S09", "S10", "S11", "S12")) for (m in listOf(fr, en)) if (!m.containsKey("secret.$sid")) err("chaîne manquante secret.$sid")
        println("   ${used.size} clés utilisées dans le code, ${fr.size} fr, ${en.size} en")
    }

    private fun checkSave() {
        val s = GameState.newGame("fr", 1000L)
        s.flags.add("test"); s.give("boussole_esteban"); s.pages.add(3); s.addLien("MAREK", 4); s.addTrait("DOUCEUR", 3); s.clarte = 42; s.decisions["D-01"] = "a"; s.responseText = "Réponse — accents éèà « » ✓"; s.puzzleState["E07"] = "2"; s.zone = "z13"; s.tableau = "t02"; s.ngPlus = true
        val bytes = s.toBytes()
        val r = GameState.fromBytes(bytes)
        if (r.flags != s.flags || r.inventory != s.inventory || r.pages != s.pages || r.lien("MAREK") != 4 || r.trait("DOUCEUR") != 3 || r.clarte != 42 || r.decisions["D-01"] != "a" || r.responseText != s.responseText || r.puzzleState["E07"] != "2" || r.zone != "z13" || r.tableau != "t02" || !r.ngPlus) err("sauvegarde: aller-retour incohérent")
        val corrupt = bytes.copyOf(); corrupt[corrupt.size / 2] = (corrupt[corrupt.size / 2] + 1).toByte()
        try { GameState.fromBytes(corrupt); err("sauvegarde: corruption non détectée") } catch (e: IllegalStateException) {}
        try { GameState.fromBytes(ByteArray(10)); err("sauvegarde: fichier tronqué accepté") } catch (e: Exception) {}
        if (String(bytes, 0, 4, Charsets.US_ASCII) != "LCDA") err("sauvegarde: magic")
    }

    private fun checkConditions(platform: DesktopPlatform, audio: DesktopAudio, content: Content) {
        val g = Game(platform, audio); g.content = content; g.painter = DesktopPainter(platform, 320, 180)
        val st = g.state
        st.flags.add("nom_repeint"); st.give("lampe_tempete"); st.pages.add(5); st.addLien("MAREK", 3); st.seq = 12; st.decisions["D-01"] = "b"; st.addTrait("RUSE", 4); st.echoesSeen.add("E-05")
        val cases = mapOf("flag:nom_repeint" to true, "FLAG-NOM-REPEINT" to true, "!flag:nom_repeint" to false, "has:lampe_tempete" to true, "has:boussole_esteban" to false, "page:5" to true, "LIEN-MAREK>=3" to true, "LIEN-MAREK>=4" to false, "LIEN-MAREK<4" to true, "seq>=SQ-12" to true, "seq>=SQ-13" to false, "act>=3" to true, "act>=4" to false, "decision:D-01=b" to true, "decision:D-01=a" to false, "decision:D-01" to true, "decision:D-02" to false, "RUSE>=4" to true, "echo:E-05" to true, "flag:nom_repeint & has:lampe_tempete" to true, "flag:x | has:lampe_tempete" to true, "!(flag:x | flag:y)" to true, "trait:RUSE" to true, "ng+" to false, "letter_read" to false, "(LIEN-MAREK>=3 & !decision:D-03) | ng+" to true)
        for ((c, exp) in cases) if (g.conditions.eval(c) != exp) err("condition '$c' attendu $exp")
        g.effects.apply(listOf("+DOUCEUR", "LIEN-YSOLDE +2", "CLARTE +3", "flag FLAG-CLOCHE-SONNÉE", "give ruban_memoire", "page 13", "decision D-05=esteban", "seq SQ-13"))
        if (st.trait("DOUCEUR") != 1 || st.lien("YSOLDE") != 2 || st.clarte != 3 || "cloche_sonnee" !in st.flags || !st.has("ruban_memoire") || 13 !in st.pages || st.decisions["D-05"] != "esteban" || st.seq != 13) err("effets: application incorrecte (${st.flags})")
    }

    private fun simulate(platform: DesktopPlatform, audio: DesktopAudio, content: Content) {
        // 11a. chaque scène se déroule jusqu'à la fin, pour chaque branche de premier choix
        var scenesRun = 0
        for (sc in content.scenes.values) {
            val choiceCount = sc.lines.filterIsInstance<Line.Choice>().maxOfOrNull { it.options.size } ?: 1
            for (branch in 0 until choiceCount.coerceAtMost(4)) {
                val g = Game(platform, audio); g.content = content; g.painter = DesktopPainter(platform, 640, 360); g.contentReady = true
                g.state.give("feutre_ysolde"); g.state.give("colis_adele"); g.state.give("crayon_marek"); g.state.give("journal_vieux_quai"); g.state.give("ressort_boite")
                val w = com.ateliermareebasse.cartographie.core.screens.WorldScreen(g); g.world = w; g.screens.add(w); w.enterTableau("z01", "t01", first = true, silent = true)
                g.push(DialogueScreen(g, sc))
                var steps = 0
                try {
                    while (g.screens.any { it is DialogueScreen && it.scene.id == sc.id } && steps++ < 300) {
                        g.update(0.5f)
                        val top = g.top()
                        if (top is DialogueScreen) {
                            // choix : prend l'option `branch` si elle existe
                            val f = DialogueScreen::class.java.getDeclaredField("options"); f.isAccessible = true
                            @Suppress("UNCHECKED_CAST") val opts = f.get(top) as List<com.ateliermareebasse.cartographie.core.data.Option>
                            if (opts.isNotEmpty()) { val m = DialogueScreen::class.java.getDeclaredMethod("choose", com.ateliermareebasse.cartographie.core.data.Option::class.java); m.isAccessible = true; m.invoke(top, opts[branch.coerceAtMost(opts.size - 1)]) }
                            else { top.onInput(Input.Up(320f, 340f)) }
                        } else { top?.onInput(Input.Up(320f, 340f)); if (top != null && top !is DialogueScreen && g.top() === top) g.pop() }
                    }
                    if (steps >= 300) err("scène ${sc.id}: ne se termine pas (branche $branch)")
                } catch (e: Throwable) { err("scène ${sc.id}: exception $e") }
                scenesRun++
            }
        }
        // 11b. chaque énigme : instanciation, rendu paysage et portrait, résolution
        for (id in PuzzleFactory.all) {
            try {
                val g = Game(platform, audio); g.content = content; g.painter = DesktopPainter(platform, 1280, 720); g.contentReady = true
                val w = com.ateliermareebasse.cartographie.core.screens.WorldScreen(g); g.world = w; g.screens.add(w); w.enterTableau("z01", "t01", first = true, silent = true)
                val pz = PuzzleFactory.create(g, id)!!
                g.push(pz); g.update(0.1f); g.render()
                g.painter = DesktopPainter(platform, 720, 1280); g.update(0.1f); g.render()
                g.input(Input.Down(360f, 640f)); g.input(Input.Move(400f, 640f)); g.input(Input.Up(400f, 640f)); g.update(0.1f); g.render()
                val m = com.ateliermareebasse.cartographie.core.puzzles.PuzzleScreen::class.java.getDeclaredMethod("solve"); m.isAccessible = true; m.invoke(pz)
                g.update(2f); g.render()
                if (id !in g.state.puzzlesSolved) err("énigme $id: non marquée résolue")
            } catch (e: Throwable) { err("énigme $id: exception $e"); e.printStackTrace() }
        }
        // 11c. chaîne du final : lettre → épilogue → fin de chapitre → NG+
        try {
            val g = Game(platform, audio); g.content = content; g.painter = DesktopPainter(platform, 1280, 720); g.contentReady = true
            val w = com.ateliermareebasse.cartographie.core.screens.WorldScreen(g); g.world = w; g.screens.add(w); w.enterTableau("z17", "t01", first = true, silent = true)
            g.openLetter(); repeat(3) { g.update(0.5f); g.render() }
            val ls = g.top() as com.ateliermareebasse.cartographie.core.screens.LetterScreen
            var guard = 0
            while (g.top() === ls && guard++ < 200) { g.input(Input.Down(1200f, 700f)); g.input(Input.Up(1200f, 700f)); repeat(4) { g.update(0.4f); g.render() }; if (guard > 30) { g.update(12f); g.input(Input.Down(640f, 660f)); g.input(Input.Up(640f, 660f)); g.update(0.2f) } }
            if (!g.state.letterRead) err("final: la lettre n'est pas marquée lue")
            // passe CIN-08b puis le voyage
            repeat(40) { g.update(0.5f); g.render(); g.top()?.let { if (it is com.ateliermareebasse.cartographie.core.screens.CinematicScreen) { val m = it.javaClass.getDeclaredMethod("finish"); m.isAccessible = true; m.invoke(it) } } }
            if (g.state.zone != "z18") err("final: pas de retour à la terrasse (zone=${g.state.zone})")
            g.openEpilogue(); g.update(0.1f); g.render()
            while (g.top() is com.ateliermareebasse.cartographie.core.screens.TutorialScreen) g.pop()
            val ep = g.screens.last { it is com.ateliermareebasse.cartographie.core.screens.EpilogueScreen } as com.ateliermareebasse.cartographie.core.screens.EpilogueScreen
            val mm = com.ateliermareebasse.cartographie.core.screens.EpilogueScreen::class.java.getDeclaredMethod("onButton", String::class.java); mm.isAccessible = true
            mm.invoke(ep, "opt:lettre"); g.update(0.1f); g.render(); mm.invoke(ep, "model:A"); g.update(0.1f); g.render(); mm.invoke(ep, "deposit")
            repeat(60) { g.update(0.5f); g.render(); g.top()?.let { if (it is com.ateliermareebasse.cartographie.core.screens.CinematicScreen) { val m = it.javaClass.getDeclaredMethod("finish"); m.isAccessible = true; m.invoke(it) }; if (it is DialogueScreen) it.onInput(Input.Up(640f, 680f)) } }
            if (g.top() !is com.ateliermareebasse.cartographie.core.screens.EndCardScreen) err("final: pas de carton de fin (top=${g.top()?.javaClass?.simpleName})")
            if (!g.state.chapterDone || g.state.ending != "lettre" || g.state.responseText.isEmpty()) err("final: état de fin incorrect")
            val saved = platform.readFile("checkpoint.sav"); if (saved == null) err("final: pas de sauvegarde de fin")
            g.newGamePlus(g.state); repeat(5) { g.update(0.5f); g.render() }
            if (!g.state.ngPlus) err("NG+: état non marqué")
        } catch (e: Throwable) { err("final: exception $e"); e.printStackTrace() }
        // 11d. nouvelle partie complète : entrée dans chaque tableau, sans exception, en paysage et portrait
        try {
            val g = Game(platform, audio); g.content = content; g.painter = DesktopPainter(platform, 1280, 720); g.contentReady = true
            val w = com.ateliermareebasse.cartographie.core.screens.WorldScreen(g); g.world = w; g.screens.add(w)
            for (z in content.zones.values) for (t in z.tableaux) {
                w.enterTableau(z.id, t.id, first = true); repeat(3) { g.update(0.3f) }; g.render()
                g.painter = DesktopPainter(platform, 720, 1280); g.update(0.1f); g.render(); g.painter = DesktopPainter(platform, 1280, 720)
                drain(g, w, "tableau ${z.id}:${t.id}")
                for (h in t.hotspots) {
                    try { w.act(h) } catch (e: Throwable) { err("tableau ${z.id}:${t.id} hot ${h.id}: exception $e") }
                    drain(g, w, "tableau ${z.id}:${t.id} hot ${h.id}")
                }
                // si un effet a voyagé ailleurs, on revient
                if (g.world !== w) { g.world = w }
            }
            g.saveSlot(2); if (!g.loadSave("slot_2.sav")) err("chargement du slot 2 échoué")
            g.painter = DesktopPainter(platform, 1280, 720); repeat(3) { g.update(0.3f) }; g.render()
        } catch (e: Throwable) { err("parcours des tableaux: exception $e"); e.printStackTrace() }
        println("   $scenesRun branches de scènes jouées")
    }

    /** Referme tout ce qui est au-dessus du monde (dialogues : premier choix ; cinématiques : passage ; autres : retour), sans jamais boucler. */
    private fun drain(g: Game, w: com.ateliermareebasse.cartographie.core.screens.WorldScreen, where: String) {
        var guard = 0
        while (g.top() !== w && g.screens.isNotEmpty() && guard++ < 80) {
            val top = g.top()!!
            when (top) {
                is DialogueScreen -> {
                    val f = DialogueScreen::class.java.getDeclaredField("options"); f.isAccessible = true
                    @Suppress("UNCHECKED_CAST") val opts = f.get(top) as List<com.ateliermareebasse.cartographie.core.data.Option>
                    if (opts.isNotEmpty()) { val m = DialogueScreen::class.java.getDeclaredMethod("choose", com.ateliermareebasse.cartographie.core.data.Option::class.java); m.isAccessible = true; m.invoke(top, opts[0]) }
                    else top.onInput(Input.Up(640f, 680f))
                }
                is com.ateliermareebasse.cartographie.core.screens.CinematicScreen -> { val m = top.javaClass.getDeclaredMethod("finish"); m.isAccessible = true; m.invoke(top) }
                is com.ateliermareebasse.cartographie.core.screens.LetterScreen -> { g.screens.remove(top) }
                is com.ateliermareebasse.cartographie.core.screens.EndCardScreen -> { g.screens.remove(top) }
                else -> { top.onBack(); if (g.top() === top) g.pop() }
            }
            repeat(3) { g.update(0.3f) }
        }
        if (g.top() !== w) { err("$where: écran bloqué ${g.top()?.javaClass?.simpleName}"); while (g.top() !== w && g.screens.size > 1) g.screens.removeAt(g.screens.size - 1) }
    }

    private fun checkGraph(content: Content) {
        val adj = HashMap<String, MutableSet<String>>()
        for (z in content.zones.values) for (t in z.tableaux) for (p in t.paths) { val zz = if (p.target.contains(':')) p.target.substringBefore(':') else if (p.target.startsWith("t") && p.target.length <= 3) z.id else p.target; adj.getOrPut(z.id) { HashSet() }.add(zz) }
        val seen = HashSet<String>(); val q = ArrayDeque<String>(); q.add("z01"); seen.add("z01")
        while (q.isNotEmpty()) { val z = q.removeFirst(); for (n in adj[z] ?: emptySet()) if (seen.add(n)) q.add(n) }
        for (z in content.zones.values) if (!z.hidden && z.id !in seen) err("zone ${z.id} injoignable depuis z01 par les chemins")
        // et dans chaque zone, chaque tableau est joignable depuis le tableau de départ
        for (z in content.zones.values) {
            val local = HashMap<String, MutableSet<String>>()
            for (t in z.tableaux) for (p in t.paths) { if (!p.target.contains(':') && p.target.startsWith("t") && p.target.length <= 3) local.getOrPut(t.id) { HashSet() }.add(p.target) else if (p.target.startsWith("${z.id}:")) local.getOrPut(t.id) { HashSet() }.add(p.target.substringAfter(':')) }
            val s2 = HashSet<String>(); val q2 = ArrayDeque<String>(); q2.add(z.start); s2.add(z.start)
            while (q2.isNotEmpty()) { val t = q2.removeFirst(); for (n in local[t] ?: emptySet()) if (s2.add(n)) q2.add(n) }
            for (t in z.tableaux) if (t.id !in s2) warn("zone ${z.id}: tableau ${t.id} joignable seulement par effet/voyage")
        }
    }

    private fun checkAudio(content: Content, platform: DesktopPlatform) {
        val used = HashSet<String>()
        val re = Regex("""sfx\("([a-z0-9_]+)"""")
        for (d in srcDirs) if (d.isDirectory) d.walkTopDown().filter { it.extension == "kt" }.forEach { f -> re.findAll(f.readText()).forEach { used.add(it.groupValues[1]) } }
        for (id in used) if (!platform.assetExists("audio/sfx/$id.ogg")) err("sfx manquant (code): $id")
        for (i in 1..12) if (!platform.assetExists("audio/sfx/cloche_%02d.ogg".format(i))) err("sfx manquant cloche_%02d".format(i))
        for (i in 1..12) if (!platform.assetExists("audio/sfx/harpe_%02d.ogg".format(i))) err("sfx manquant harpe_%02d".format(i))
        for (i in 1..4) if (!platform.assetExists("audio/sfx/petrel_note_$i.ogg")) err("sfx manquant petrel_note_$i")
        for (i in 1..9) if (!platform.assetExists("audio/sfx/quill_scratch_0$i.ogg")) err("sfx manquant quill_scratch_0$i")
        for (t in listOf("track_01", "track_02", "track_12", "track_16", "track_22", "track_23", "track_24", "track_lettre", "track_echo")) if (!platform.assetExists("audio/music/$t.ogg")) err("musique manquante $t")
        for (z in content.zones.values) { z.music?.let { if (!platform.assetExists("audio/music/$it.ogg")) err("musique manquante ${z.id}: $it") }; z.ambience?.let { if (!platform.assetExists("audio/ambience/$it.ogg")) err("ambiance manquante ${z.id}: $it") } }
        val n = platform.listAssets("audio/sfx").size + platform.listAssets("audio/music").size + platform.listAssets("audio/ambience").size + platform.listAssets("audio/voice").size
        println("   $n fichiers audio, ${used.size} sfx référencés par le code")
    }
}
