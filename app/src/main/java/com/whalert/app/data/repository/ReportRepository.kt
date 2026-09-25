package com.whalert.app.data.repository

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.whalert.app.data.model.AuditLogEntry
import com.whalert.app.data.model.DossierStatus
import com.whalert.app.data.model.ReportCategory
import com.whalert.app.data.model.ReportDossier
import com.whalert.app.data.security.SecureStorageManager
import com.whalert.app.domain.generator.ReportTextGenerator
import java.util.UUID

/**
 * Dépôt central pour la gestion des dossiers de signalement et de l'historique d'audit.
 *
 * Implémente la limitation stricte anti-abus (par défaut 3 dossiers authentiques max / 24 heures).
 */
class ReportRepository(private val secureStorage: SecureStorageManager) {

    private val moshi = Moshi.Builder().build()
    private val dossierListType = Types.newParameterizedType(List::class.java, ReportDossier::class.java)
    private val dossierAdapter = moshi.adapter<List<ReportDossier>>(dossierListType)

    private val auditListType = Types.newParameterizedType(List::class.java, AuditLogEntry::class.java)
    private val auditAdapter = moshi.adapter<List<AuditLogEntry>>(auditListType)

    fun getAllDossiers(): List<ReportDossier> {
        val json = secureStorage.getDossiersRaw() ?: return emptyList()
        return try {
            dossierAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveDossiers(list: List<ReportDossier>) {
        val json = dossierAdapter.toJson(list)
        secureStorage.saveDossiersRaw(json)
    }

    /**
     * Vérifie la règle de sécurité anti-abus de l'application :
     * Limite configurable (3 par défaut) dossiers créés par période glissante de 24 heures.
     */
    fun checkDailyLimitReached(): Pair<Boolean, String> {
        val now = System.currentTimeMillis()
        val dayAgo = now - 24 * 60 * 60 * 1000
        val countLast24h = getAllDossiers().count { it.createdAtMillis > dayAgo }
        val limit = secureStorage.getDailyLimit()

        return if (countLast24h >= limit) {
            Pair(
                true,
                "Limite de sécurité de l'application : $limit dossiers par 24 heures atteinte ($countLast24h/$limit). Cette limite est une règle de protection de l'application et n'est pas présentée comme une règle officielle de WhatsApp."
            )
        } else {
            Pair(false, "Quota disponible : ${limit - countLast24h}/$limit dossiers restants sur 24h.")
        }
    }

    fun createDossier(
        targetPhoneRaw: String,
        targetPhoneE164: String,
        targetCountryCode: String,
        category: ReportCategory,
        incidentTimestamp: String,
        description: String,
        contextNotes: String
    ): Result<ReportDossier> {
        val (limitReached, message) = checkDailyLimitReached()
        if (limitReached) {
            logAudit("CREATE_DOSSIER_REJECTED", "LIMIT_EXCEEDED", message)
            return Result.failure(IllegalStateException(message))
        }

        val id = "DOS-" + UUID.randomUUID().toString().take(8).uppercase()
        val dossier = ReportDossier(
            id = id,
            targetPhoneRaw = targetPhoneRaw,
            targetPhoneE164 = targetPhoneE164,
            targetCountryCode = targetCountryCode,
            category = category,
            incidentTimestamp = incidentTimestamp,
            description = description,
            contextNotes = contextNotes,
            status = DossierStatus.PREPARED
        )

        val finalizedDossier = ReportTextGenerator.updateDossierWithGeneratedText(dossier)
        val currentList = getAllDossiers().toMutableList()
        currentList.add(0, finalizedDossier)
        saveDossiers(currentList)

        logAudit("CREATE_DOSSIER", "SUCCESS", "Dossier $id créé pour $targetCountryCode (motif: ${category.name})")
        return Result.success(finalizedDossier)
    }

    fun updateDossier(dossier: ReportDossier) {
        val currentList = getAllDossiers().toMutableList()
        val index = currentList.indexOfFirst { it.id == dossier.id }
        if (index >= 0) {
            currentList[index] = dossier.copy(updatedAtMillis = System.currentTimeMillis())
            saveDossiers(currentList)
            logAudit("UPDATE_DOSSIER", "SUCCESS", "Dossier ${dossier.id} mis à jour, état: ${dossier.status.name}")
        }
    }

    fun getDossierById(id: String): ReportDossier? {
        return getAllDossiers().firstOrNull { it.id == id }
    }

    // --- Journaux d'audit pour la console privée ---

    fun getAllAuditLogs(): List<AuditLogEntry> {
        val json = secureStorage.getAuditLogsRaw() ?: return emptyList()
        return try {
            auditAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun logAudit(action: String, status: String, details: String) {
        val entry = AuditLogEntry(
            id = UUID.randomUUID().toString().take(8),
            timestampMillis = System.currentTimeMillis(),
            action = action,
            status = status,
            details = details
        )
        val logs = getAllAuditLogs().toMutableList()
        logs.add(0, entry)
        // Conserver les 100 derniers logs techniques
        if (logs.size > 100) {
            logs.removeAt(logs.lastIndex)
        }
        val json = auditAdapter.toJson(logs)
        secureStorage.saveAuditLogsRaw(json)
    }
}
