package com.ateliermareebasse.cartographie.core.state

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** État complet d'une partie. Sérialisable (Annexe G : magic LCDA, version, blocs, FNV-1a-64). */
class GameState {
    // Progression
    var seq = 1                          // SQ-nn courant (1..20)
    var zone = "z01"
    var tableau = "t01"
    var prevZone = ""
    var prevTableau = ""
    var tidePhase = 0                    // 0..5 : PM1 BM PM2 PM3 PM4 PM5 (BM = marée basse)
    var tideTimer = 0f                   // secondes dans la phase (12 min par phase)
    var playSeconds = 0f
    var chapterDone = false
    var ngPlus = false
    var ngCount = 0
    var letterRead = false
    var letterOpenedAt = 0L
    var ending = ""                      // D-06 : lettre | poursuite | boussole | carte
    var responseText = ""                // réponse libre écrite par le joueur
    var responseModel = ""               // A/B/C ou "libre"
    var createdAt = 0L
    var savedAt = 0L
    var slotName = ""
    var lang = "fr"

    // Sentiments
    val traits = HashMap<String, Int>()  // DOUCEUR COURAGE RUSE HUMEUR
    val liens = HashMap<String, Int>()   // OMBELINE ANSELME SIDONIE BAZ YSOLDE MAREK EMERIC ADELE JUMEAUX MARTA ROSA ARISTIDE
    var clarte = 0
    var filou = 3                        // affinité 0..10
    var filouWithLohen = true

    // Collections
    val flags = HashSet<String>()
    val inventory = LinkedHashMap<String, Int>()
    val pages = HashSet<Int>()
    val echoesSeen = HashSet<String>()
    val echoesAvailable = HashSet<String>()
    val secrets = HashSet<String>()
    val puzzlesSolved = HashSet<String>()
    val puzzleAttempts = HashMap<String, Int>()
    val puzzleState = HashMap<String, String>()   // état persistant par énigme (paliers)
    val bornes = HashSet<String>()
    val murmures = HashSet<String>()
    val bells = HashSet<Int>()
    val labelsRead = HashSet<Int>()
    val archivesRead = HashSet<String>()
    var releves = 0
    val zonesVisited = LinkedHashSet<String>()
    val tableauxVisited = HashSet<String>()
    val scenesSeen = HashSet<String>()          // scènes jouées au moins une fois
    val counters = HashMap<String, Int>()       // compteurs libres : examined:<id>, talked:<npc>, chocolat…
    val decisions = HashMap<String, String>()   // D-01..D-06
    val vars = HashMap<String, String>()        // set clé=valeur
    val cinSeen = HashSet<String>()
    val tutosSeen = HashSet<String>()
    val thoughtsShown = HashSet<String>()
    val photosRatees = ArrayList<String>()
    var chocolats = 0
    var wordDictionary = false

    fun trait(name: String) = traits[name] ?: 0
    fun lien(name: String) = liens[name.uppercase()] ?: 0
    fun addLien(name: String, d: Int) { val k = name.uppercase(); liens[k] = (liens[k] ?: 0).plus(d).coerceIn(0, 5) }
    fun addTrait(name: String, d: Int = 1) { traits[name] = (traits[name] ?: 0) + d }
    fun has(item: String) = (inventory[item] ?: 0) > 0
    fun give(item: String, n: Int = 1) { inventory[item] = (inventory[item] ?: 0) + n }
    fun take(item: String, n: Int = 1): Boolean {
        val c = inventory[item] ?: 0
        if (c <= 0) return false
        if (c - n <= 0) inventory.remove(item) else inventory[item] = c - n
        return true
    }
    fun count(key: String) = counters[key] ?: 0
    fun inc(key: String, d: Int = 1): Int { val v = (counters[key] ?: 0) + d; counters[key] = v; return v }
    fun dominantTrait(): String {
        val order = listOf("DOUCEUR", "COURAGE", "RUSE")
        var best = "DOUCEUR"; var bv = -1
        for (t in order) { val v = trait(t); if (v > bv) { bv = v; best = t } }
        return best
    }
    fun act(): Int = when {
        chapterDone -> 5
        seq >= 16 -> 4
        seq >= 10 -> 3
        seq >= 6 -> 2
        else -> 1
    }
    fun isLowTide() = tidePhase == 1 || act() >= 4
    fun tideName() = arrayOf("PM1", "BM", "PM2", "PM3", "PM4", "PM5")[tidePhase.coerceIn(0, 5)]
    fun cartoPercent(totalTableaux: Int): Int = if (totalTableaux == 0) 0 else (tableauxVisited.size * 100 / totalTableaux).coerceIn(0, 100)

    fun copy(): GameState = fromBytes(toBytes())

    // ───────────────────────────── sérialisation ─────────────────────────────
    fun toBytes(): ByteArray {
        val body = ByteArrayOutputStream()
        val d = DataOutputStream(body)
        fun block(tag: Int, w: (DataOutputStream) -> Unit) {
            val b = ByteArrayOutputStream(); val bd = DataOutputStream(b); w(bd); bd.flush()
            d.writeByte(tag); d.writeInt(b.size()); d.write(b.toByteArray())
        }
        fun strs(o: DataOutputStream, c: Collection<String>) { o.writeInt(c.size); c.forEach { o.writeUTF(it) } }
        fun ints(o: DataOutputStream, c: Collection<Int>) { o.writeInt(c.size); c.forEach { o.writeInt(it) } }
        fun smap(o: DataOutputStream, m: Map<String, String>) { o.writeInt(m.size); m.forEach { (k, v) -> o.writeUTF(k); writeLong(o, v) } }
        fun imap(o: DataOutputStream, m: Map<String, Int>) { o.writeInt(m.size); m.forEach { (k, v) -> o.writeUTF(k); o.writeInt(v) } }
        block(1) { o -> // META
            o.writeUTF(slotName); o.writeLong(createdAt); o.writeLong(savedAt); o.writeUTF(lang)
            o.writeFloat(playSeconds); o.writeInt(seq); o.writeUTF(zone); o.writeUTF(tableau); o.writeUTF(prevZone); o.writeUTF(prevTableau)
            o.writeBoolean(chapterDone); o.writeBoolean(ngPlus); o.writeInt(ngCount); o.writeBoolean(letterRead); o.writeLong(letterOpenedAt)
            o.writeUTF(ending); o.writeUTF(responseModel); writeLong(o, responseText)
        }
        block(2) { o -> // STATE
            imap(o, traits); imap(o, liens); o.writeInt(clarte); o.writeInt(filou); o.writeBoolean(filouWithLohen)
            strs(o, flags); imap(o, inventory); imap(o, counters); smap(o, decisions); smap(o, vars)
            o.writeInt(chocolats); o.writeBoolean(wordDictionary)
        }
        block(3) { o -> // WORLD
            o.writeInt(tidePhase); o.writeFloat(tideTimer); strs(o, zonesVisited); strs(o, tableauxVisited); strs(o, scenesSeen); strs(o, cinSeen); strs(o, tutosSeen); strs(o, thoughtsShown)
        }
        block(4) { o -> // COLLECT
            ints(o, pages); strs(o, echoesSeen); strs(o, echoesAvailable); strs(o, secrets); strs(o, bornes); strs(o, murmures); ints(o, bells); ints(o, labelsRead); strs(o, archivesRead); o.writeInt(releves); strs(o, photosRatees)
        }
        block(5) { o -> // PUZZLES
            strs(o, puzzlesSolved); imap(o, puzzleAttempts); smap(o, puzzleState)
        }
        d.flush()
        val payload = body.toByteArray()
        val out = ByteArrayOutputStream(); val od = DataOutputStream(out)
        od.writeBytes("LCDA"); od.writeShort(SAVE_VERSION); od.writeShort(GAME_VERSION); od.writeLong(savedAt)
        od.writeLong(fnv1a64(payload)); od.writeInt(payload.size); od.write(payload); od.flush()
        return out.toByteArray()
    }

    companion object {
        const val SAVE_VERSION = 3
        const val GAME_VERSION = 100

        private fun writeLong(o: DataOutputStream, s: String) { val b = s.toByteArray(Charsets.UTF_8); o.writeInt(b.size); o.write(b) }
        private fun readLong(i: DataInputStream): String { val n = i.readInt(); val b = ByteArray(n); i.readFully(b); return String(b, Charsets.UTF_8) }

        fun fnv1a64(b: ByteArray): Long {
            var h = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
            for (x in b) { h = h xor (x.toLong() and 0xff); h *= 0x100000001b3L }
            return h
        }

        /** Lit une sauvegarde ; lève IllegalStateException si corrompue. */
        fun fromBytes(bytes: ByteArray): GameState {
            val i = DataInputStream(ByteArrayInputStream(bytes))
            val magic = ByteArray(4); i.readFully(magic)
            check(String(magic, Charsets.US_ASCII) == "LCDA") { "bad magic" }
            val sv = i.readUnsignedShort(); i.readUnsignedShort(); i.readLong()
            check(sv in 1..SAVE_VERSION) { "unsupported save version $sv" }
            val sum = i.readLong(); val n = i.readInt()
            val payload = ByteArray(n); i.readFully(payload)
            check(fnv1a64(payload) == sum) { "checksum mismatch" }
            val s = GameState()
            val p = DataInputStream(ByteArrayInputStream(payload))
            fun strs(o: DataInputStream): List<String> { val c = o.readInt(); return List(c) { o.readUTF() } }
            fun ints(o: DataInputStream): List<Int> { val c = o.readInt(); return List(c) { o.readInt() } }
            fun smap(o: DataInputStream): Map<String, String> { val c = o.readInt(); val m = LinkedHashMap<String, String>(); repeat(c) { val k = o.readUTF(); m[k] = readLong(o) }; return m }
            fun imap(o: DataInputStream): Map<String, Int> { val c = o.readInt(); val m = LinkedHashMap<String, Int>(); repeat(c) { val k = o.readUTF(); m[k] = o.readInt() }; return m }
            while (p.available() > 0) {
                val tag = p.readUnsignedByte(); val len = p.readInt()
                val blk = ByteArray(len); p.readFully(blk)
                val o = DataInputStream(ByteArrayInputStream(blk))
                when (tag) {
                    1 -> {
                        s.slotName = o.readUTF(); s.createdAt = o.readLong(); s.savedAt = o.readLong(); s.lang = o.readUTF()
                        s.playSeconds = o.readFloat(); s.seq = o.readInt(); s.zone = o.readUTF(); s.tableau = o.readUTF(); s.prevZone = o.readUTF(); s.prevTableau = o.readUTF()
                        s.chapterDone = o.readBoolean(); s.ngPlus = o.readBoolean(); s.ngCount = o.readInt(); s.letterRead = o.readBoolean(); s.letterOpenedAt = o.readLong()
                        s.ending = o.readUTF(); s.responseModel = o.readUTF(); s.responseText = readLong(o)
                    }
                    2 -> {
                        s.traits.putAll(imap(o)); s.liens.putAll(imap(o)); s.clarte = o.readInt(); s.filou = o.readInt(); s.filouWithLohen = o.readBoolean()
                        s.flags.addAll(strs(o)); s.inventory.putAll(imap(o)); s.counters.putAll(imap(o)); s.decisions.putAll(smap(o)); s.vars.putAll(smap(o))
                        s.chocolats = o.readInt(); s.wordDictionary = o.readBoolean()
                    }
                    3 -> {
                        s.tidePhase = o.readInt(); s.tideTimer = o.readFloat(); s.zonesVisited.addAll(strs(o)); s.tableauxVisited.addAll(strs(o)); s.scenesSeen.addAll(strs(o)); s.cinSeen.addAll(strs(o)); s.tutosSeen.addAll(strs(o)); s.thoughtsShown.addAll(strs(o))
                    }
                    4 -> {
                        s.pages.addAll(ints(o)); s.echoesSeen.addAll(strs(o)); s.echoesAvailable.addAll(strs(o)); s.secrets.addAll(strs(o)); s.bornes.addAll(strs(o)); s.murmures.addAll(strs(o)); s.bells.addAll(ints(o)); s.labelsRead.addAll(ints(o)); s.archivesRead.addAll(strs(o)); s.releves = o.readInt(); s.photosRatees.addAll(strs(o))
                    }
                    5 -> { s.puzzlesSolved.addAll(strs(o)); s.puzzleAttempts.putAll(imap(o)); s.puzzleState.putAll(smap(o)) }
                    else -> {} // bloc inconnu (version future) : ignoré
                }
            }
            return s
        }

        /** Nouvelle partie : valeurs initiales (Lohen, matin bleu). */
        fun newGame(lang: String, now: Long): GameState = GameState().apply {
            this.lang = lang; createdAt = now; savedAt = now
            give("lentille_arpenteur"); give("gomme_rouge", 0); inventory.remove("gomme_rouge")
            liens["OMBELINE"] = 2; liens["ANSELME"] = 1; liens["SIDONIE"] = 1; liens["MAREK"] = 0
            tidePhase = 0
            zone = "z00"; tableau = "t01"
        }
    }
}

/** Métadonnées lues rapidement pour l'écran de chargement (sans reconstruire tout l'état). */
class SaveMeta(val file: String, val name: String, val savedAt: Long, val playSeconds: Float, val zone: String, val seq: Int, val act: Int, val percent: Int, val corrupt: Boolean, val chapterDone: Boolean)
