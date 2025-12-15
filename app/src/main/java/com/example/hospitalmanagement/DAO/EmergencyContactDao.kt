package com.example.hospitalmanagement.DAO
import androidx.room.*
import com.example.hospitalmanagement.EmergencyContact
import kotlinx.coroutines.flow.Flow
@Dao
interface EmergencyContactDao {
    @Insert
    suspend fun insert(contact: EmergencyContact): Long

    @Update
    suspend fun update(contact: EmergencyContact)

    @Delete
    suspend fun delete(contact: EmergencyContact)

    @Query("SELECT * FROM emergency_contacts WHERE patientId = :patientId")
    fun getByPatient(patientId: String): Flow<List<EmergencyContact>>

    @Query("SELECT * FROM emergency_contacts WHERE patientId = :patientId AND isPrimary = 1 LIMIT 1")
    suspend fun getPrimaryContact(patientId: String): EmergencyContact?
}