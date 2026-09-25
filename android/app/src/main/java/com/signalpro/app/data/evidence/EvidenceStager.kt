package com.signalpro.app.data.evidence

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.signalpro.app.domain.Validation
import java.io.File
import java.util.UUID

/**
 * Mise en attente locale d'une preuve choisie par l'utilisateur.
 *
 * Le fichier est copié dans le stockage privé de l'application
 * (`filesDir/preuves/`), jamais ailleurs, puis supprimé dès qu'il a été
 * transmis au serveur avec succès. Aucun accès au stockage global n'est
 * demandé : l'utilisateur choisit le fichier via le sélecteur système.
 */
object EvidenceStager {

    data class Staged(val file: File, val displayName: String, val sizeBytes: Long)

    fun folder(context: Context): File = File(context.filesDir, "preuves").apply { mkdirs() }

    fun displayName(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull() ?: (uri.lastPathSegment ?: "preuve")

    fun fileSize(context: Context, uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index) else 0L
        }
    }.getOrNull() ?: 0L

    /** Contrôle préalable : taille, extension et type attendus pour la catégorie de preuve. */
    fun preCheck(context: Context, uri: Uri, kind: String): String? {
        val name = displayName(context, uri)
        val size = fileSize(context, uri)
        return Validation.evidencePreCheck(name, size, kind)
    }

    fun stage(context: Context, uri: Uri, kind: String): Result<Staged> = runCatching {
        val name = displayName(context, uri)
        val target = File(folder(context), "${UUID.randomUUID()}_${name.replace('/', '_')}")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
        } ?: error("Impossible d'ouvrir le fichier sélectionné.")

        val check = Validation.evidencePreCheck(name, target.length(), kind)
        if (check != null) {
            target.delete()
            error(check)
        }
        Staged(file = target, displayName = name, sizeBytes = target.length())
    }

    fun discard(staged: Staged) {
        runCatching { staged.file.delete() }
    }

    /** Purge totale (suppression de compte, ou fichier transmis). */
    fun purge(context: Context) {
        runCatching { folder(context).listFiles()?.forEach { it.delete() } }
    }
}
