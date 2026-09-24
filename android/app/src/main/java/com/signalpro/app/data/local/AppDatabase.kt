package com.signalpro.app.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Base locale : elle rend l'application réellement utilisable hors ligne.
 *
 * Ce qui est stocké ici :
 *  - le cache des signalements (pour consultation sans réseau) ;
 *  - les signatures de détection (analyse 100 % locale des conversations) ;
 *  - les brouillons de signalement en attente d'envoi ;
 *  - la liste communautaire en cache.
 *
 * Ce qui n'y est JAMAIS stocké : contenu de conversations WhatsApp, jetons
 * (ils sont dans EncryptedSharedPreferences), pièces de preuve (les fichiers
 * restent chez l'utilisateur jusqu'à l'envoi qu'il déclenche).
 */

@Entity(tableName = "cached_reports")
data class CachedReport(
    @PrimaryKey val id: Int,
    val publicRef: String,
    val targetMasked: String,
    val targetPhone: String?,
    val category: String,
    val categoryLabel: String,
    val occurredAt: String,
    val description: String,
    val status: String,
    val statusLabel: String,
    val decisionReason: String?,
    val evidenceCount: Int,
    val updatedAt: Long,
)

@Entity(tableName = "spam_signatures")
data class CachedSignature(
    @PrimaryKey val key: String,
    val kind: String,
    val value: String,
    val severity: Int,
    val category: String,
    val version: String,
)

@Entity(tableName = "pending_reports", indices = [Index(value = ["localId"], unique = true)])
data class PendingReport(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val targetPhone: String,
    val category: String,
    val occurredAt: String,
    val description: String,
    val messageIds: String,
    val evidencePaths: String,
    val createdAt: Long,
    val lastError: String? = null,
    val attempts: Int = 0,
)

@Entity(tableName = "cached_blacklist")
data class CachedBlacklistEntry(
    @PrimaryKey val phoneMasked: String,
    val categoryLabel: String,
    val verifiedReports: Int,
    val distinctReporters: Int,
    val suspensionStatus: String,
    val updatedAt: Long,
)

@Entity(tableName = "cached_alerts")
data class CachedAlert(
    @PrimaryKey val targetId: Int,
    val phoneMasked: String,
    val categoryLabel: String,
    val verifiedReports: Int,
    val advice: String,
    val seen: Boolean = false,
    val updatedAt: Long,
)

@Dao
interface ReportDao {
    @Query("SELECT * FROM cached_reports ORDER BY id DESC")
    fun observeAll(): Flow<List<CachedReport>>

    @Query("SELECT * FROM cached_reports WHERE id = :id")
    fun observe(id: Int): Flow<CachedReport?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(reports: List<CachedReport>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(report: CachedReport)

    @Query("DELETE FROM cached_reports")
    suspend fun clear()
}

@Dao
interface PendingReportDao {
    @Query("SELECT * FROM pending_reports ORDER BY localId ASC")
    suspend fun all(): List<PendingReport>

    @Query("SELECT COUNT(*) FROM pending_reports")
    fun countFlow(): Flow<Int>

    @Insert
    suspend fun insert(report: PendingReport): Long

    @Query("UPDATE pending_reports SET attempts = attempts + 1, lastError = :error WHERE localId = :id")
    suspend fun markFailure(id: Long, error: String)

    @Query("UPDATE pending_reports SET evidencePaths = :paths WHERE localId = :id")
    suspend fun updateEvidence(id: Long, paths: String)

    @Query("DELETE FROM pending_reports WHERE localId = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SignatureDao {
    @Query("SELECT * FROM spam_signatures")
    suspend fun all(): List<CachedSignature>

    @Query("SELECT version FROM spam_signatures LIMIT 1")
    suspend fun currentVersion(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(signatures: List<CachedSignature>)

    @Query("DELETE FROM spam_signatures WHERE version != :version")
    suspend fun pruneOldVersions(version: String)

    @Query("SELECT COUNT(*) FROM spam_signatures")
    suspend fun count(): Int
}

@Dao
interface CommunityDao {
    @Query("SELECT * FROM cached_blacklist ORDER BY verifiedReports DESC")
    fun observeBlacklist(): Flow<List<CachedBlacklistEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBlacklist(entries: List<CachedBlacklistEntry>)

    @Query("DELETE FROM cached_blacklist")
    suspend fun clearBlacklist()

    @Query("SELECT * FROM cached_alerts ORDER BY updatedAt DESC")
    fun observeAlerts(): Flow<List<CachedAlert>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlerts(alerts: List<CachedAlert>)
}

@Database(
    entities = [
        CachedReport::class,
        PendingReport::class,
        CachedSignature::class,
        CachedBlacklistEntry::class,
        CachedAlert::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reportDao(): ReportDao
    abstract fun pendingReportDao(): PendingReportDao
    abstract fun signatureDao(): SignatureDao
    abstract fun communityDao(): CommunityDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "signalpro.db")
                // Le schéma est versionné ; toute évolution passe par une migration
                // explicite — jamais par un effacement silencieux des données.
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}
