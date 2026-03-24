package com.example.hospitalmanagement.DAO

import androidx.room.*
import com.example.hospitalmanagement.Medication

@Dao
interface MedicationDao {
    @Insert suspend fun insert(medication: Medication)

    @Update suspend fun update(medication: Medication)

    @Delete suspend fun delete(medication: Medication)

    @Query("SELECT * FROM medications") suspend fun getAll(): List<Medication>

    @Query("DELETE FROM medications") suspend fun deleteAll()

    @Query("SELECT * FROM medications WHERE section = :section LIMIT 1")
    suspend fun findBySection(section: String): Medication?

    // --- Medication Log Methods ---
    @Insert suspend fun insertLog(log: com.example.hospitalmanagement.MedicationLog)

    @Query(
            "SELECT * FROM medication_logs WHERE patientId = :patientId AND takenAt >= :startTime AND takenAt <= :endTime"
    )
    suspend fun getLogsForDate(
            patientId: String,
            startTime: Long,
            endTime: Long
    ): List<com.example.hospitalmanagement.MedicationLog>

    @Query(
            "SELECT COUNT(*) FROM medication_logs WHERE patientId = :patientId AND medicationName = :medName AND takenAt >= :startTime AND takenAt <= :endTime"
    )
    suspend fun isTakenToday(
            patientId: String,
            medName: String,
            startTime: Long,
            endTime: Long
    ): Int
}
