package com.signalpro.app.data.local

import com.signalpro.app.data.remote.ReportDto

/**
 * Conversions entre les DTO serveur et le cache local.
 *
 * Le cache ne conserve QUE ce qui est utile à l'utilisateur hors ligne : aucune
 * pièce de preuve, aucun contenu de conversation, aucune donnée d'un tiers.
 */
object ReportMapper {

    fun toCached(dto: ReportDto): CachedReport = CachedReport(
        id = dto.id,
        publicRef = dto.publicRef,
        targetMasked = dto.targetPhoneMasked,
        targetPhone = dto.targetPhone,
        category = dto.category,
        categoryLabel = dto.categoryLabel,
        occurredAt = dto.occurredAt,
        description = dto.description,
        status = dto.status,
        statusLabel = dto.statusLabel,
        decisionReason = dto.decisionReason,
        evidenceCount = dto.evidences.size,
        updatedAt = System.currentTimeMillis(),
    )
}
