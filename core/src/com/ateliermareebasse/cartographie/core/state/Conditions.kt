package com.ateliermareebasse.cartographie.core.state

/**
 * Évaluateur de conditions : `a & b | !c`, parenthèses, atomes :
 * flag:x  has:item  page:n  echo:E-xx  puzzle:Exx  secret:Sxx  seq>=SQ-nn  seq<SQ-nn  LIEN-X>=n  LIEN-X<n
 * DOUCEUR>=n (traits)  CLARTE>=n  FILOU>=n  zone:zxx  tableau:txx  tide:BM  low_tide  talked:scene  seen:scene
 * decision:D-01=x  decision:D-03  npc_here:x  act>=n  act<n  ng+  letter_read  chapter_done  examined:id>=n  count:key>=n
 * var:key=value  visited:zxx  hb (heures bleues)  bornes>=n  pages>=n  echoes>=n  secrets>=n  carte>=n (pourcentage)
 */
class Conditions(private val ctx: Ctx) {
    interface Ctx {
        val state: GameState
        fun npcHere(id: String): Boolean
        fun cartoPercent(): Int
    }

    fun eval(cond: String?): Boolean {
        if (cond.isNullOrBlank()) return true
        return try { Parser(cond.trim()).parseOr() } catch (e: Exception) { false }
    }

    private inner class Parser(val s: String) {
        var i = 0
        fun peek(): Char = if (i < s.length) s[i] else '\u0000'
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun parseOr(): Boolean { var v = parseAnd(); ws(); while (peek() == '|') { i++; val r = parseAnd(); v = v || r; ws() }; return v }
        fun parseAnd(): Boolean { var v = parseNot(); ws(); while (peek() == '&') { i++; val r = parseNot(); v = v && r; ws() }; return v }
        fun parseNot(): Boolean { ws(); if (peek() == '!') { i++; return !parseNot() }; return parseAtom() }
        fun parseAtom(): Boolean {
            ws()
            if (peek() == '(') { i++; val v = parseOr(); ws(); if (peek() == ')') i++; return v }
            val st = i
            while (i < s.length && s[i] !in "&|()") i++
            return atom(s.substring(st, i).trim())
        }
    }

    private fun cmp(a: Int, op: String, b: Int) = when (op) { ">=" -> a >= b; "<=" -> a <= b; ">" -> a > b; "<" -> a < b; "=", "==" -> a == b; "!=" -> a != b; else -> false }
    private val opRe = Regex(">=|<=|==|!=|>|<")

    fun atom(a: String): Boolean {
        val st = ctx.state
        if (a.isEmpty()) return true
        when (a) {
            "ng+", "ng" -> return st.ngPlus
            "letter_read" -> return st.letterRead
            "chapter_done" -> return st.chapterDone
            "low_tide" -> return st.isLowTide()
            "hb" -> return st.act() == 4 && !st.letterRead
            "filou_here" -> return st.filouWithLohen
            "true" -> return true
            "false" -> return false
        }
        val colon = a.indexOf(':')
        val opM = opRe.find(a)
        val isKv = opM == null && colon > 0 && (a.startsWith("decision:") || a.startsWith("var:")) && a.indexOf('=') > colon
        if (opM != null || isKv) {
            val key: String; val op: String; val r: String
            if (opM != null) { key = a.substring(0, opM.range.first).trim(); op = opM.value; r = a.substring(opM.range.last + 1).trim() }
            else { val eq = a.indexOf('='); key = a.substring(0, eq).trim(); op = "="; r = a.substring(eq + 1).trim() }
            return when {
                key == "seq" -> cmp(st.seq, op, r.removePrefix("SQ-").toIntOrNull() ?: 0)
                key == "act" -> cmp(st.act(), op, r.toIntOrNull() ?: 0)
                key.startsWith("LIEN-") -> cmp(st.lien(key.removePrefix("LIEN-")), op, r.toIntOrNull() ?: 0)
                key == "CLARTE" -> cmp(st.clarte, op, r.toIntOrNull() ?: 0)
                key == "FILOU" -> cmp(st.filou, op, r.toIntOrNull() ?: 0)
                key in setOf("DOUCEUR", "COURAGE", "RUSE", "HUMEUR") -> cmp(st.trait(key), op, r.toIntOrNull() ?: 0)
                key == "pages" -> cmp(st.pages.size, op, r.toIntOrNull() ?: 0)
                key == "echoes" -> cmp(st.echoesSeen.size, op, r.toIntOrNull() ?: 0)
                key == "secrets" -> cmp(st.secrets.size, op, r.toIntOrNull() ?: 0)
                key == "bornes" -> cmp(st.bornes.size, op, r.toIntOrNull() ?: 0)
                key == "bells" -> cmp(st.bells.size, op, r.toIntOrNull() ?: 0)
                key == "chocolats" -> cmp(st.chocolats, op, r.toIntOrNull() ?: 0)
                key == "carte" -> cmp(ctx.cartoPercent(), op, r.toIntOrNull() ?: 0)
                key == "releves" -> cmp(st.releves, op, r.toIntOrNull() ?: 0)
                key.startsWith("examined:") -> cmp(st.count("examined:" + key.removePrefix("examined:")), op, r.toIntOrNull() ?: 0)
                key.startsWith("count:") -> cmp(st.count(key.removePrefix("count:")), op, r.toIntOrNull() ?: 0)
                key.startsWith("decision:") -> { val d = st.decisions[key.removePrefix("decision:")]; if (op == "!=") d != r else d == r }
                key.startsWith("var:") -> { val d = st.vars[key.removePrefix("var:")]; if (op == "!=") d != r else d == r }
                else -> false
            }
        }
        if (colon > 0) {
            val k = a.substring(0, colon); val v = a.substring(colon + 1).trim()
            return when (k) {
                "flag" -> Effects.norm(v) in st.flags
                "has" -> st.has(v)
                "page" -> (v.toIntOrNull() ?: -1) in st.pages
                "echo" -> v in st.echoesSeen
                "echo_available" -> v in st.echoesAvailable
                "puzzle", "solved" -> v in st.puzzlesSolved
                "secret" -> v in st.secrets
                "zone" -> st.zone == v
                "tableau" -> st.tableau == v
                "tide" -> st.tideName() == v
                "talked", "seen" -> v in st.scenesSeen
                "decision" -> st.decisions.containsKey(v)
                "npc_here" -> ctx.npcHere(v)
                "visited" -> v in st.zonesVisited
                "cin" -> v in st.cinSeen
                "borne" -> v in st.bornes
                "murmure" -> v in st.murmures
                "bell" -> (v.toIntOrNull() ?: -1) in st.bells
                "lang" -> st.lang == v
                "ending" -> st.ending == v
                "trait" -> st.dominantTrait() == v
                else -> false
            }
        }
        // FLAG-... nu
        return Effects.norm(a) in st.flags
    }
}
