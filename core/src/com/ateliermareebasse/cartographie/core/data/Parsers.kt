package com.ateliermareebasse.cartographie.core.data

/** Analyseurs des formats texte du jeu. Tous tolérants : une ligne invalide est signalée, jamais fatale. */
object Parsers {
    val warnings = ArrayList<String>()
    private fun warn(file: String, n: Int, msg: String) { warnings.add("$file:$n: $msg") }

    private val SI = Regex("""\[SI:\s*([^\]]*)]""")

    /** Extrait une condition [SI: …] d'une ligne ; retourne (ligne sans la condition, condition|null). */
    fun splitCond(line: String): Pair<String, String?> {
        val m = SI.find(line) ?: return line to null
        val cond = m.groupValues[1].trim().ifEmpty { null }
        return (line.substring(0, m.range.first) + line.substring(m.range.last + 1)).trim() to cond
    }

    /** Découpe "a ; b ; c" en effets. */
    fun effects(s: String?): List<String> =
        s?.split(';')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    /** Tokenise en respectant les guillemets "…". */
    fun tokens(s: String): List<String> {
        val out = ArrayList<String>(); val sb = StringBuilder(); var q = false
        for (c in s) {
            when {
                c == '"' -> { q = !q; }
                c.isWhitespace() && !q -> { if (sb.isNotEmpty()) { out.add(sb.toString()); sb.clear() } }
                else -> sb.append(c)
            }
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }

    // ───────────────────────────── ZONES (.tab) ─────────────────────────────
    fun parseZone(file: String, text: String): Zone? {
        var id = ""; var name = ""; var subtitle = ""; var act = 1; var palette = "papier"
        var music: String? = null; var ambience: String? = null; var open: String? = null
        var mapX = 0.5f; var mapY = 0.5f; var hidden = false; var start = ""
        val tableaux = ArrayList<Tableau>()
        // état du tableau courant
        var tId = ""; var tName = ""; var tImage = ""; var props = LinkedHashMap<String, String>()
        var thoughts = ArrayList<Pair<String, String?>>(); var scenes = ArrayList<Pair<String, String?>>()
        var hots = ArrayList<Hotspot>(); var paths = ArrayList<PathLink>(); var npcs = ArrayList<NpcSpot>()
        var barkZone = ""
        fun flush() {
            if (tId.isNotEmpty()) {
                tableaux.add(Tableau(tId, id, tName, tImage, props, thoughts, scenes, hots, paths, npcs, barkZone.ifEmpty { id }))
            }
            tId = ""; tName = ""; tImage = ""; props = LinkedHashMap(); thoughts = ArrayList(); scenes = ArrayList()
            hots = ArrayList(); paths = ArrayList(); npcs = ArrayList(); barkZone = ""
        }
        text.lines().forEachIndexed { n0, raw ->
            val n = n0 + 1
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val (body, cond) = splitCond(line)
            val arrow = body.indexOf("->")
            val head = if (arrow >= 0) body.substring(0, arrow).trim() else body
            val eff = if (arrow >= 0) effects(body.substring(arrow + 2)) else emptyList()
            val tk = tokens(head)
            if (tk.isEmpty()) return@forEachIndexed
            val rest = head.substringAfter(' ', "").trim()
            when (tk[0]) {
                "zone" -> id = tk.getOrElse(1) { "" }
                "name" -> name = rest
                "subtitle" -> subtitle = rest
                "act" -> act = tk.getOrNull(1)?.toIntOrNull() ?: 1
                "palette" -> palette = tk.getOrElse(1) { "papier" }
                "music" -> if (tId.isEmpty()) music = tk.getOrNull(1) else props["music"] = tk.getOrElse(1) { "" }
                "ambience" -> if (tId.isEmpty()) ambience = tk.getOrNull(1) else props["ambience"] = tk.getOrElse(1) { "" }
                "open" -> open = rest.ifEmpty { null }
                "map" -> { mapX = tk.getOrNull(1)?.toFloatOrNull() ?: 0.5f; mapY = tk.getOrNull(2)?.toFloatOrNull() ?: 0.5f }
                "hidden" -> hidden = true
                "start" -> start = tk.getOrElse(1) { "" }
                "tableau" -> { flush(); tId = tk.getOrElse(1) { "t01" }; tName = tk.getOrElse(2) { tId } }
                "image" -> tImage = tk.getOrElse(1) { "" }
                "prop" -> props[tk.getOrElse(1) { "" }] = tk.getOrElse(2) { "1" }
                "bark" -> barkZone = tk.getOrElse(1) { "" }
                "enter_thought" -> thoughts.add(tk.getOrElse(1) { "" } to cond)
                "enter_scene" -> scenes.add(tk.getOrElse(1) { "" } to cond)
                "hot" -> {
                    if (tk.size < 5) { warn(file, n, "hot incomplet"); return@forEachIndexed }
                    val kind = tk[1]; val hid = tk[2]
                    val x = tk[3].toFloatOrNull() ?: 0.5f; val y = tk[4].toFloatOrNull() ?: 0.5f
                    var label = ""; val params = LinkedHashMap<String, String>()
                    var radius = 0.055f
                    for (t in tk.drop(5)) {
                        val eq = t.indexOf('=')
                        if (eq > 0 && !t.startsWith("«")) {
                            val k = t.substring(0, eq); val v = t.substring(eq + 1)
                            if (k == "r") radius = v.toFloatOrNull() ?: radius else params[k] = v
                        } else if (label.isEmpty()) label = t else label += " $t"
                    }
                    hots.add(Hotspot(kind, hid, x, y, label, params, cond, eff, radius))
                }
                "path" -> {
                    if (tk.size < 3) { warn(file, n, "path incomplet"); return@forEachIndexed }
                    val target = tk[1]; var label = ""; var locked: String? = null; var hid = false; var strict = false
                    var back = false; var cin: String? = null; var x = -1f; var y = -1f; var kind = "arrow"
                    val nums = ArrayList<Float>()
                    for (t in tk.drop(2)) {
                        when {
                            t.startsWith("locked=") -> locked = t.substring(7)
                            t.startsWith("cin=") -> cin = t.substring(4)
                            t.startsWith("kind=") -> kind = t.substring(5)
                            t == "hidden" -> hid = true
                            t == "strict" -> strict = true
                            t == "back" -> back = true
                            t.toFloatOrNull() != null && label.isNotEmpty() -> nums.add(t.toFloat())
                            else -> label = if (label.isEmpty()) t else "$label $t"
                        }
                    }
                    if (nums.size >= 2) { x = nums[0]; y = nums[1] }
                    paths.add(PathLink(target, label, cond, locked, hid, strict, back, cin, eff, x, y, kind))
                }
                "npc" -> {
                    if (tk.size < 4) { warn(file, n, "npc incomplet"); return@forEachIndexed }
                    val scale = tk.drop(4).firstOrNull { it.startsWith("scale=") }?.substring(6)?.toFloatOrNull() ?: 1f
                    npcs.add(NpcSpot(tk[1], tk[2].toFloatOrNull() ?: 0.5f, tk[3].toFloatOrNull() ?: 0.6f, cond, scale))
                }
                else -> warn(file, n, "mot-clé inconnu '${tk[0]}'")
            }
        }
        flush()
        if (id.isEmpty()) { warn(file, 0, "zone sans id"); return null }
        if (tableaux.isEmpty()) { warn(file, 0, "zone sans tableau"); return null }
        return Zone(id, name, subtitle, act, palette, music, ambience, open, mapX, mapY, hidden, tableaux, start.ifEmpty { tableaux.first().id })
    }

    // ───────────────────────────── DIALOGUES (.dlg) ─────────────────────────
    fun parseDialogues(file: String, text: String, into: MutableMap<String, Scene>) {
        var id = ""; var cond: String? = null; var once = false
        var lines = ArrayList<Line>()
        var pendingChoice: ArrayList<Option>? = null
        fun flushChoice() { pendingChoice?.let { if (it.isNotEmpty()) lines.add(Line.Choice(it)) }; pendingChoice = null }
        fun flush() {
            flushChoice()
            if (id.isNotEmpty()) {
                if (lines.lastOrNull() != Line.End) lines.add(Line.End)
                if (into.containsKey(id)) warn(file, 0, "scène dupliquée $id")
                into[id] = Scene(id, cond, once, lines, file)
            }
            id = ""; cond = null; once = false; lines = ArrayList()
        }
        text.lines().forEachIndexed { n0, raw ->
            val n = n0 + 1
            val line = raw.trimEnd()
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) return@forEachIndexed
            when {
                t.startsWith("~ ") -> {
                    flush()
                    val (h, c) = splitCond(t.substring(2))
                    val tk = h.split(Regex("\\s+"))
                    id = tk[0]; cond = c; once = tk.contains("once")
                }
                id.isEmpty() -> warn(file, n, "ligne hors scène")
                t.startsWith("+") -> {
                    if (pendingChoice == null) pendingChoice = ArrayList()
                    val gold = t.startsWith("+*")
                    var body = t.substring(if (gold) 2 else 1).trim()
                    val (b, c) = splitCond(body)
                    body = b
                    val arrow = body.indexOf("->")
                    var txt = body; var effs = emptyList<String>(); var goto: String? = null
                    if (arrow >= 0) {
                        txt = body.substring(0, arrow).trim()
                        val all = effects(body.substring(arrow + 2)).toMutableList()
                        val g = all.firstOrNull { it.startsWith("goto ") }
                        if (g != null) { goto = g.substring(5).trim(); all.remove(g) }
                        effs = all
                    }
                    txt = txt.removeSurrounding("«", "»").removeSurrounding("\"").trim()
                    pendingChoice!!.add(Option(txt, gold, c, effs, goto))
                }
                else -> {
                    flushChoice()
                    when {
                        t == "end" -> lines.add(Line.End)
                        t.startsWith("= ") -> lines.add(Line.Label(t.substring(2).trim()))
                        t.startsWith("> ") -> { val (l, c) = splitCond(t.substring(2)); lines.add(Line.Goto(l.trim(), c)) }
                        t.startsWith("-> ") || t == "->" -> lines.add(Line.Effects(effects(t.substring(2))))
                        t.startsWith("! ") -> { val p = t.substring(2).trim(); lines.add(Line.Cue(p.substringBefore(' '), p.substringAfter(' ', ""))) }
                        t.startsWith("* ") -> lines.add(Line.Stage(t.substring(2).trim()))
                        t.startsWith("PENSEE:") || t.startsWith("PENSÉE:") -> lines.add(Line.Thought(t.substringAfter(':').trim()))
                        else -> {
                            val colon = t.indexOf(':')
                            if (colon in 1..24 && t.substring(0, colon).all { it.isUpperCase() || it == '_' || it == '-' || it == '&' || it.isDigit() || it == ' ' || it == 'É' || it == 'È' }) {
                                lines.add(Line.Say(t.substring(0, colon).trim(), t.substring(colon + 1).trim()))
                            } else warn(file, n, "ligne non reconnue: ${t.take(40)}")
                        }
                    }
                }
            }
        }
        flush()
    }

    /** Tables de conversation : `npc id` puis lignes `scene [SI: cond]`. */
    fun parseTalk(file: String, text: String, into: MutableMap<String, MutableList<TalkEntry>>) {
        var npc = ""
        text.lines().forEachIndexed { n0, raw ->
            val t = raw.trim()
            if (t.isEmpty() || t.startsWith("#")) return@forEachIndexed
            if (t.startsWith("npc ")) { npc = t.substring(4).trim(); into.getOrPut(npc) { ArrayList() }; return@forEachIndexed }
            if (npc.isEmpty()) { warn(file, n0 + 1, "scène hors npc"); return@forEachIndexed }
            val (s, c) = splitCond(t)
            into[npc]!!.add(TalkEntry(s.trim(), c))
        }
    }

    // ───────────────────────────── ÉNIGMES (.pzl) ───────────────────────────
    fun parsePuzzles(file: String, text: String): Map<String, PuzzleSheet> {
        val out = LinkedHashMap<String, PuzzleSheet>()
        var id = ""; var name = ""; var zone = ""; var phrase = ""; var intro: String? = null
        var hints = ArrayList<Pair<String, String>>(); var success = ""; var eff = emptyList<String>(); var after: String? = null; var sfx: String? = null
        fun flush() {
            if (id.isNotEmpty()) out[id] = PuzzleSheet(id, name, zone, phrase, intro, hints, success, eff, after, sfx)
            id = ""; name = ""; zone = ""; phrase = ""; intro = null; hints = ArrayList(); success = ""; eff = emptyList(); after = null; sfx = null
        }
        for (raw in text.lines()) {
            val t = raw.trim()
            if (t.isEmpty() || t.startsWith("#")) continue
            if (t.startsWith("@ ")) {
                flush()
                val p = t.substring(2).split('|').map { it.trim() }
                id = p[0]; name = p.getOrElse(1) { id }; zone = p.getOrElse(2) { "" }
                continue
            }
            val k = t.substringBefore(':').trim(); val v = t.substringAfter(':', "").trim()
            when (k) {
                "phrase" -> phrase = v
                "intro" -> intro = v
                "hint1", "hint2", "hint3" -> hints.add(v.substringBefore('|').trim() to v.substringAfter('|', v).trim())
                "success" -> success = v
                "effects" -> eff = effects(v)
                "after" -> after = v
                "sfx" -> sfx = v
                else -> warn(file, 0, "clé inconnue $k")
            }
        }
        flush()
        return out
    }

    // ───────────────────────────── OBJETS (.itm) ────────────────────────────
    fun parseItems(file: String, text: String): Map<String, ItemDef> {
        val out = LinkedHashMap<String, ItemDef>()
        var id = ""; var name = ""; var cat = "souvenir"; var sniff: String? = null; var note: String? = null; var hold = false; var glyph = false
        var th = LinkedHashMap<String, String>()
        fun flush() { if (id.isNotEmpty()) out[id] = ItemDef(id, name, cat, sniff, note, th, hold, glyph); id = ""; th = LinkedHashMap(); sniff = null; note = null; hold = false; glyph = false }
        for (raw in text.lines()) {
            val t = raw.trim()
            if (t.isEmpty() || t.startsWith("#")) continue
            if (t.startsWith("@ ")) {
                flush()
                val p = t.substring(2).split('|').map { it.trim() }
                id = p[0]; name = p.getOrElse(1) { id }; cat = p.getOrElse(2) { "souvenir" }
                for (x in p.drop(3)) { if (x.startsWith("sniff=")) sniff = x.substring(6) else if (x == "hold") hold = true else if (x == "glyph") glyph = true }
                continue
            }
            val k = t.substringBefore(':').trim(); val v = t.substringAfter(':', "").trim()
            if (k == "note") note = v else th[k] = v
        }
        flush()
        return out
    }

    // ───────────────────────────── PAGES (.pg) ──────────────────────────────
    fun parsePages(text: String): Map<Int, PageDef> {
        val out = LinkedHashMap<Int, PageDef>()
        var n = 0; var where = ""; var date = ""; var birds = 0; var lines = ArrayList<String>()
        fun flush() { if (n > 0) out[n] = PageDef(n, where, date, birds, lines); n = 0; lines = ArrayList() }
        for (raw in text.lines()) {
            val t = raw.trim()
            if (t.startsWith("#")) continue
            if (t.startsWith("@ ")) {
                flush()
                val p = t.substring(2).split('|').map { it.trim() }
                n = p[0].toIntOrNull() ?: 0; where = p.getOrElse(1) { "" }; date = p.getOrElse(2) { "" }; birds = p.getOrElse(3) { "0" }.toIntOrNull() ?: 0
                continue
            }
            if (n > 0 && t.isNotEmpty()) lines.add(t)
        }
        flush()
        return out
    }

    // ───────────────────────────── ÉCHOS (.ec) ──────────────────────────────
    fun parseEchoes(file: String, text: String): Map<String, EchoDef> {
        val out = LinkedHashMap<String, EchoDef>()
        var id = ""; var title = ""; var year = ""; var sec = 60; var lines = ArrayList<Line>()
        fun flush() { if (id.isNotEmpty()) out[id] = EchoDef(id, title, year, sec, lines); id = ""; lines = ArrayList() }
        for (raw in text.lines()) {
            val t = raw.trim()
            if (t.isEmpty() || t.startsWith("#")) continue
            if (t.startsWith("@ ")) {
                flush()
                val p = t.substring(2).split('|').map { it.trim() }
                id = p[0]; title = p.getOrElse(1) { id }; year = p.getOrElse(2) { "" }; sec = p.getOrElse(3) { "60" }.toIntOrNull() ?: 60
                continue
            }
            when {
                t == "end" -> {}
                t.startsWith("* ") -> lines.add(Line.Stage(t.substring(2)))
                t.contains(':') -> lines.add(Line.Say(t.substringBefore(':').trim(), t.substringAfter(':').trim()))
                else -> warn(file, 0, "écho: ligne inconnue $t")
            }
        }
        flush()
        return out
    }

    // ───────────────────────────── TABLES « a | b | c » ─────────────────────
    fun rows(text: String): List<List<String>> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { l -> l.split('|').map { it.trim() } }

    fun parseBarks(text: String) = rows(text).filter { it.size >= 3 }.map { Bark(it[0], it[1], it[2]) }
    fun parseBells(text: String) = rows(text).filter { it.size >= 3 }.mapNotNull { r -> r[0].toIntOrNull()?.let { BellDef(it, r[1], r[2], r.getOrElse(3) { "" }) } }.associateBy { it.n }
    fun parseBornes(text: String) = rows(text).filter { it.size >= 4 }.map { BorneDef(it[0], it[1], it[2], it[3]) }.associateBy { it.id }
    fun parseMurmures(text: String) = rows(text).filter { it.size >= 4 }.map { MurmureDef(it[0], it[1], it[2], it[3]) }.associateBy { it.id }
    fun parseLabels(text: String) = rows(text).filter { it.size >= 2 }.mapNotNull { r -> r[0].toIntOrNull()?.let { LabelDef(it, r[1]) } }.associateBy { it.n }
    fun parseFilou(text: String) = rows(text).filter { it.size >= 3 }.map { FilouReaction(it[0], it[1], it[2]) }.associateBy { it.key }
    fun parseKeyValues(text: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (raw in text.lines()) {
            val t = raw.trim()
            if (t.isEmpty() || t.startsWith("#")) continue
            val i = t.indexOf('|')
            if (i <= 0) continue
            out[t.substring(0, i).trim()] = t.substring(i + 1).trim()
        }
        return out
    }
    fun parseArchives(text: String): Map<String, ArchiveDef> {
        val out = LinkedHashMap<String, ArchiveDef>()
        var id = ""; var title = ""; var lines = ArrayList<String>()
        fun flush() { if (id.isNotEmpty()) out[id] = ArchiveDef(id, title, lines); id = ""; lines = ArrayList() }
        for (raw in text.lines()) {
            val t = raw.trim()
            if (t.startsWith("#")) continue
            if (t.startsWith("@ ")) { flush(); val p = t.substring(2).split('|').map { it.trim() }; id = p[0]; title = p.getOrElse(1) { id }; continue }
            if (id.isNotEmpty() && t.isNotEmpty()) lines.add(t)
        }
        flush()
        return out
    }
    fun parseCharacters(text: String): Map<String, CharacterDef> =
        rows(text).filter { it.size >= 5 }.map { CharacterDef(it[0], it[1], it[2], it[3], it[4].removePrefix("#").toLongOrNull(16)?.toInt() ?: 0xFF3A404F.toInt(), it.getOrElse(5) { "" }) }.associateBy { it.id }
    fun parseRetentissements(text: String) = rows(text).filter { it.size >= 2 }.map { Retentissement(it[0], it[1]) }
    fun parseCredits(text: String) = rows(text).filter { it.size >= 2 }.map { CreditsLine(it[0], it[1]) }

    /** Cinématiques : `@ CIN-01 | titre | music=… | ambience=… | noskip` puis `shot image | secondes | légende | speaker | effet | sfx`. */
    fun parseCinematics(file: String, text: String): Map<String, CinematicDef> {
        val out = LinkedHashMap<String, CinematicDef>()
        var id = ""; var title = ""; var music: String? = null; var amb: String? = null; var skippable = true; var shots = ArrayList<CinShot>()
        fun flush() { if (id.isNotEmpty()) out[id] = CinematicDef(id, title, shots, music, amb, skippable); id = ""; shots = ArrayList(); music = null; amb = null; skippable = true }
        for (raw in text.lines()) {
            val t = raw.trim()
            if (t.isEmpty() || t.startsWith("#")) continue
            if (t.startsWith("@ ")) {
                flush()
                val p = t.substring(2).split('|').map { it.trim() }
                id = p[0]; title = p.getOrElse(1) { id }
                for (x in p.drop(2)) when {
                    x.startsWith("music=") -> music = x.substring(6)
                    x.startsWith("ambience=") -> amb = x.substring(9)
                    x == "noskip" -> skippable = false
                }
                continue
            }
            val p = t.split('|').map { it.trim() }
            val img = p[0].ifEmpty { null }?.takeIf { it != "-" }
            shots.add(CinShot(img, p.getOrElse(1) { "4" }.toFloatOrNull() ?: 4f, p.getOrElse(2) { "" }.ifEmpty { null }, p.getOrElse(3) { "" }.ifEmpty { null }, p.getOrElse(4) { "" }.ifEmpty { null }, p.getOrElse(5) { "" }.ifEmpty { null }))
        }
        flush()
        return out
    }

    /** Lettre : `@ pli n | titre` puis paragraphes séparés par lignes vides. */
    fun parseLetter(text: String): List<Pair<String, List<String>>> {
        val out = ArrayList<Pair<String, List<String>>>()
        var title = ""; var paras = ArrayList<String>(); val sb = StringBuilder()
        fun endPara() { if (sb.isNotBlank()) paras.add(sb.toString().trim()); sb.clear() }
        fun flush() { endPara(); if (title.isNotEmpty() || paras.isNotEmpty()) out.add(title to paras); title = ""; paras = ArrayList() }
        for (raw in text.lines()) {
            val t = raw.trimEnd()
            if (t.trim().startsWith("#")) continue
            if (t.startsWith("@ ")) { flush(); title = t.substring(2).substringAfter('|', t.substring(2)).trim(); continue }
            if (t.isBlank()) { endPara(); continue }
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(t.trim())
        }
        flush()
        return out
    }

    /** Blocs `@ id` puis lignes (tutoriels, modèles d'épilogue). */
    fun parseBlocks(text: String): Map<String, List<String>> {
        val out = LinkedHashMap<String, MutableList<String>>()
        var id = ""
        for (raw in text.lines()) {
            val t = raw.trim()
            if (t.startsWith("#")) continue
            if (t.startsWith("@ ")) { id = t.substring(2).trim(); out[id] = ArrayList(); continue }
            if (id.isNotEmpty() && t.isNotEmpty()) out[id]!!.add(t)
        }
        return out
    }
}
