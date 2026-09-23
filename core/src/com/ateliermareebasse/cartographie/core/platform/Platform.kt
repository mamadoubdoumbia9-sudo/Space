package com.ateliermareebasse.cartographie.core.platform

/**
 * Abstraction de la plateforme : le cœur du jeu (core/) ne dépend d'aucune API Android.
 * Implémentations : AndroidPlatform (app/) et DesktopPlatform (desktop/, harnais de test).
 */
interface Platform {
    /** Lit un fichier d'assets (chemin relatif à assets/), null s'il n'existe pas. */
    fun readAsset(path: String): ByteArray?
    fun readAssetText(path: String): String? = readAsset(path)?.toString(Charsets.UTF_8)
    /** Liste les entrées (fichiers et dossiers) d'un dossier d'assets. */
    fun listAssets(dir: String): List<String>
    fun assetExists(path: String): Boolean = readAsset(path) != null

    /** Stockage privé persistant (sauvegardes, options). */
    fun writeFile(name: String, bytes: ByteArray)
    fun readFile(name: String): ByteArray?
    fun deleteFile(name: String)
    fun listFiles(prefix: String): List<String>

    fun vibrate(ms: Int, amplitude: Float = 1f)
    fun nowMillis(): Long = System.currentTimeMillis()
    /** Langue système (fr/en). */
    fun systemLanguage(): String
    /** Demande une saisie de texte à la plateforme (clavier). Le callback reçoit null si annulé. */
    fun requestTextInput(title: String, initial: String, maxChars: Int, cb: (String?) -> Unit)
    /** Garde l'écran allumé (lecture de la lettre). */
    fun keepScreenOn(on: Boolean)
    fun openUrl(url: String) {}
    fun quit()
    fun log(msg: String)
    /** Dimensions d'une image d'asset sans la décoder entièrement (w,h) ou null. */
    fun imageSize(path: String): Pair<Int, Int>?
}

/** Interface de dessin : coordonnées en pixels logiques (l'écran est mis à l'échelle par le moteur). */
interface Painter {
    val width: Float
    val height: Float
    fun clear(color: Int)
    fun fillRect(x: Float, y: Float, w: Float, h: Float, color: Int)
    fun strokeRect(x: Float, y: Float, w: Float, h: Float, color: Int, stroke: Float = 1f)
    fun fillRoundRect(x: Float, y: Float, w: Float, h: Float, r: Float, color: Int)
    fun strokeRoundRect(x: Float, y: Float, w: Float, h: Float, r: Float, color: Int, stroke: Float = 1f)
    fun fillCircle(cx: Float, cy: Float, r: Float, color: Int)
    fun strokeCircle(cx: Float, cy: Float, r: Float, color: Int, stroke: Float = 1f)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, stroke: Float = 1f)
    /** Polyligne (x0,y0,x1,y1,...) */
    fun polyline(pts: FloatArray, color: Int, stroke: Float = 1f)
    fun fillPolygon(pts: FloatArray, color: Int)
    /** Dessine une image d'asset (décodage/cache gérés par la plateforme). Retourne false si pas encore prête. */
    fun image(path: String, x: Float, y: Float, w: Float, h: Float, alpha: Float = 1f, tint: Int = 0): Boolean
    /** Portion source (sx,sy,sw,sh en pixels image) vers destination. */
    fun imageRegion(path: String, sx: Float, sy: Float, sw: Float, sh: Float, x: Float, y: Float, w: Float, h: Float, alpha: Float = 1f): Boolean
    fun imageRotated(path: String, cx: Float, cy: Float, w: Float, h: Float, degrees: Float, alpha: Float = 1f): Boolean
    /** Dégradé vertical. */
    fun gradientV(x: Float, y: Float, w: Float, h: Float, top: Int, bottom: Int)
    fun gradientRadial(cx: Float, cy: Float, r: Float, inner: Int, outer: Int)
    fun text(s: String, x: Float, y: Float, size: Float, color: Int, font: Font = Font.BODY, align: Align = Align.LEFT, alpha: Float = 1f)
    fun measure(s: String, size: Float, font: Font = Font.BODY): Float
    fun pushClip(x: Float, y: Float, w: Float, h: Float)
    fun popClip()
    fun pushAlpha(a: Float)
    fun popAlpha()
    /** Image chargée ? (pour la barre de chargement) */
    fun isLoaded(path: String): Boolean
    fun preload(path: String)
    fun evictAll()
}

enum class Font { BODY, HAND, TITLE, MONO }
enum class Align { LEFT, CENTER, RIGHT }

interface Audio {
    fun playSfx(id: String, volume: Float = 1f, pitch: Float = 1f)
    fun playMusic(id: String?, fadeMs: Int = 1500, loop: Boolean = true)
    fun playAmbience(id: String?, fadeMs: Int = 2000)
    /** Voix / échos : lecture unique non bouclée sur le canal musique secondaire. */
    fun playVoice(id: String?)
    fun setVolumes(master: Float, music: Float, ambience: Float, sfx: Float)
    fun pauseAll()
    fun resumeAll()
    fun stopAll()
    fun sfxExists(id: String): Boolean
}

/** Événement d'entrée normalisé. */
sealed class Input {
    data class Down(val x: Float, val y: Float, val id: Int = 0) : Input()
    data class Move(val x: Float, val y: Float, val id: Int = 0) : Input()
    data class Up(val x: Float, val y: Float, val id: Int = 0) : Input()
    object Back : Input()
    data class Key(val code: Int, val ch: Char) : Input()
}

object Colors {
    fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    fun withAlpha(c: Int, a: Float): Int = (c and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)
    fun lerp(a: Int, b: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        fun ch(s: Int) = (((a shr s) and 255) + ((((b shr s) and 255) - ((a shr s) and 255)) * tt)).toInt()
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
    // Palette du jeu (8.2 : encre de Chine, papier, garance, indigo, laiton)
    const val INK = 0xFF1B1F2A.toInt()
    const val INK_SOFT = 0xFF3A404F.toInt()
    const val PAPER = 0xFFF2E9D8.toInt()
    const val PAPER_DARK = 0xFFD9CDB6.toInt()
    const val PAPER_SHADE = 0xFFBFB198.toInt()
    const val GARANCE = 0xFFB6403A.toInt()
    const val INDIGO = 0xFF2E4A7A.toInt()
    const val BLEU_HEURE = 0xFF203A73.toInt()
    const val LAITON = 0xFFC9A24B.toInt()
    const val OR_ENCRE = 0xFFD8B25A.toInt()
    const val VERT_ALGUE = 0xFF5F7B5A.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF000000.toInt()
    const val TRANSPARENT = 0
}
