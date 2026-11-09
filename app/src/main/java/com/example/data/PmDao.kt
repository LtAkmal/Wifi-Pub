package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PmDao {
    @Query("SELECT * FROM pm_reports WHERE dateKey = :dateKey ORDER BY timestamp ASC")
    fun getPmReportsForDate(dateKey: String): Flow<List<PmReportEntity>>

    @Query("SELECT * FROM pm_reports ORDER BY timestamp DESC")
    fun getAllPmReports(): Flow<List<PmReportEntity>>

    @Query("SELECT * FROM pm_reports WHERE id = :id LIMIT 1")
    suspend fun getPmReportById(id: Int): PmReportEntity?

    @Query("SELECT * FROM pm_reports WHERE uploadStatus IN ('PENDING', 'FAILED') ORDER BY timestamp ASC")
    suspend fun getPendingOrFailedPmReports(): List<PmReportEntity>

    @Query(
        "UPDATE pm_reports SET uploadClientRef = :clientRef, uploadStatus = :status, uploadedReportId = :uploadedId, uploadError = :error WHERE id = :id"
    )
    suspend fun updatePmUploadStatus(
        id: Int,
        clientRef: String?,
        status: String,
        uploadedId: String?,
        error: String?
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPmReport(report: PmReportEntity): Long

    @Update
    suspend fun updatePmReport(report: PmReportEntity)

    @Query("DELETE FROM pm_reports WHERE id = :id")
    suspend fun deletePmReportById(id: Int)

    @Query("SELECT * FROM corrective_maintenance WHERE dateKey = :dateKey ORDER BY timestamp ASC")
    fun getCmForDate(dateKey: String): Flow<List<CorrectiveMaintenanceEntity>>

    @Query("SELECT * FROM corrective_maintenance WHERE dateKey = :dateKey ORDER BY timestamp ASC")
    suspend fun getCmListForDateSync(dateKey: String): List<CorrectiveMaintenanceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCm(cm: CorrectiveMaintenanceEntity): Long

    @Update
    suspend fun updateCm(cm: CorrectiveMaintenanceEntity)

    @Query("DELETE FROM corrective_maintenance WHERE id = :id")
    suspend fun deleteCmById(id: Int)
}

