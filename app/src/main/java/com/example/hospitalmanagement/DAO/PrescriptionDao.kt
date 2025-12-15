package com.example.hospitalmanagement.DAO

import androidx.room.*
import com.example.hospitalmanagement.Prescription
import kotlinx.coroutines.flow.Flow
@Dao
interface PrescriptionDao {
    @Insert
    suspend fun insert(prescription: Prescription): Long

    @Update
    suspend fun update(prescription: Prescription)

    @Delete
    suspend fun delete(prescription: Prescription)

    @Query("SELECT * FROM prescriptions WHERE scriptId = :id")
    suspend fun getById(id: Int): Prescription?

    @Query("SELECT * FROM prescriptions WHERE appId = :appId")
    suspend fun getByAppointment(appId: Int): Prescription?

    @Query("SELECT p.* FROM prescriptions p INNER JOIN appointments a ON p.appId = a.appId WHERE a.patientId = :patientId ORDER BY p.createdAt DESC")
    fun getByPatient(patientId: String): Flow<List<Prescription>>

    @Query("SELECT p.* FROM prescriptions p INNER JOIN appointments a ON p.appId = a.appId WHERE a.doctorId = :doctorId ORDER BY p.createdAt DESC")
    fun getByDoctor(doctorId: String): Flow<List<Prescription>>
}