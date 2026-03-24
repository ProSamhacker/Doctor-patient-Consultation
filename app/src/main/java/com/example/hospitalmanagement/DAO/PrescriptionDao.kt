package com.example.hospitalmanagement.DAO

import androidx.room.*
import com.example.hospitalmanagement.Prescription
import kotlinx.coroutines.flow.Flow

@Dao
interface PrescriptionDao {
    @Insert suspend fun insert(prescription: Prescription): Long

    @Update suspend fun update(prescription: Prescription)

    @Delete suspend fun delete(prescription: Prescription)

    @Query("SELECT * FROM prescriptions WHERE scriptId = :id")
    suspend fun getById(id: Int): Prescription?

    @Query("SELECT * FROM prescriptions WHERE appId = :appId AND status = 'APPROVED'")
    suspend fun getByAppointment(appId: Int): Prescription?

    @Query("SELECT * FROM prescriptions WHERE sessionId = :sessionId")
    suspend fun getBySession(sessionId: Int): Prescription?

    @Query("SELECT * FROM prescriptions WHERE appId = :appId AND isDraft = 1")
    suspend fun getDraftByAppointment(appId: Int): Prescription?

    @Query(
            "SELECT p.* FROM prescriptions p INNER JOIN appointments a ON p.appId = a.appId WHERE a.patientId = :patientId AND p.status = 'APPROVED' ORDER BY p.createdAt DESC"
    )
    fun getByPatient(patientId: String): Flow<List<Prescription>>

    @Query(
            "SELECT p.* FROM prescriptions p INNER JOIN appointments a ON p.appId = a.appId WHERE a.doctorId = :doctorId ORDER BY p.createdAt DESC"
    )
    fun getByDoctor(doctorId: String): Flow<List<Prescription>>
}
