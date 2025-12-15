package com.example.hospitalmanagement.DAO
import androidx.room.*
import com.example.hospitalmanagement.MedicalReport
import kotlinx.coroutines.flow.Flow
@Dao
interface MedicalReportDao {
    @Insert
    suspend fun insert(report: MedicalReport): Long

    @Update
    suspend fun update(report: MedicalReport)

    @Delete
    suspend fun delete(report: MedicalReport)

    @Query("SELECT * FROM medical_reports WHERE patientId = :patientId ORDER BY uploadDate DESC")
    fun getByPatient(patientId: String): Flow<List<MedicalReport>>
}