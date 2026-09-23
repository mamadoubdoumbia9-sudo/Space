package com.ateliermareebasse.cartographie

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.util.Log
import android.view.WindowManager
import android.widget.EditText
import com.ateliermareebasse.cartographie.core.platform.Platform
import java.io.File
import java.io.FileNotFoundException

/** Implémentation Android de la plateforme : assets, stockage privé, vibreur, clavier. */
class AndroidPlatform(private val activity: Activity) : Platform {
    private val assets = activity.assets
    private val dir: File = File(activity.filesDir, "saves").also { it.mkdirs() }
    private val listCache = HashMap<String, List<String>>()

    override fun readAsset(path: String): ByteArray? = try { assets.open(path).use { it.readBytes() } } catch (e: FileNotFoundException) { null } catch (e: Exception) { null }
    override fun listAssets(dir: String): List<String> = listCache.getOrPut(dir) { try { assets.list(dir)?.toList()?.sorted() ?: emptyList() } catch (e: Exception) { emptyList() } }
    override fun assetExists(path: String): Boolean {
        val d = path.substringBeforeLast('/', ""); val f = path.substringAfterLast('/')
        return listAssets(d).contains(f)
    }

    override fun writeFile(name: String, bytes: ByteArray) {
        val tmp = File(dir, "$name.tmp"); tmp.writeBytes(bytes)
        if (!tmp.renameTo(File(dir, name))) { File(dir, name).writeBytes(bytes); tmp.delete() }
    }
    override fun readFile(name: String): ByteArray? { val f = File(dir, name); return if (f.isFile) try { f.readBytes() } catch (e: Exception) { null } else null }
    override fun deleteFile(name: String) { File(dir, name).delete() }
    override fun listFiles(prefix: String): List<String> = dir.list()?.filter { it.startsWith(prefix) && !it.endsWith(".tmp") }?.sorted() ?: emptyList()

    override fun vibrate(ms: Int, amplitude: Float) {
        try {
            val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) (activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else @Suppress("DEPRECATION") activity.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (v == null || !v.hasVibrator()) return
            val amp = (amplitude.coerceIn(0.05f, 1f) * 255).toInt()
            v.vibrate(VibrationEffect.createOneShot(ms.toLong().coerceIn(5, 2000), if (v.hasAmplitudeControl()) amp else VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) { }
    }

    override fun systemLanguage(): String = activity.resources.configuration.locales[0].language

    override fun requestTextInput(title: String, initial: String, maxChars: Int, cb: (String?) -> Unit) {
        activity.runOnUiThread {
            val edit = EditText(activity).apply {
                setText(initial); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                minLines = 5; maxLines = 12; setSelection(text.length)
                filters = arrayOf(android.text.InputFilter.LengthFilter(maxChars))
            }
            AlertDialog.Builder(activity).setTitle(title).setView(edit)
                .setPositiveButton(resId("text_input_ok")) { _, _ -> cb(edit.text.toString()) }
                .setNegativeButton(resId("text_input_cancel")) { _, _ -> cb(null) }
                .setOnCancelListener { cb(null) }
                .show()
        }
    }

    /** Identifiants de ressources sans classe R (le projet se compile sans javac : kotlinc + aapt2 + d8). */
    private fun resId(name: String, type: String = "string"): Int = activity.resources.getIdentifier(name, type, activity.packageName)

    override fun keepScreenOn(on: Boolean) {
        activity.runOnUiThread { if (on) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    override fun quit() { activity.runOnUiThread { activity.finishAndRemoveTask() } }
    override fun log(msg: String) { Log.i("Cartographie", msg) }

    override fun imageSize(path: String): Pair<Int, Int>? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        assets.open(path).use { BitmapFactory.decodeStream(it, null, o) }
        if (o.outWidth > 0) o.outWidth to o.outHeight else null
    } catch (e: Exception) { null }
}
