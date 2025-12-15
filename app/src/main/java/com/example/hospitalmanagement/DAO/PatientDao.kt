package com.example.hospitalmanagement.DAO
import androidx.room.*
import com.example.hospitalmanagement.Patient
import kotlinx.coroutines.flow.Flow
@Dao
interface PatientDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(patient: Patient)

    @Update
    suspend fun update(patient: Patient)

    @Delete
    suspend fun delete(patient: Patient)

    @Query("SELECT * FROM patients WHERE patientId = :id")
    suspend fun getById(id: String): Patient?

    @Query("SELECT * FROM patients")
    fun getAll(): Flow<List<Patient>>

    @Query("SELECT * FROM patients WHERE name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%'")
    fun searchPatients(query: String): Flow<List<Patient>>
}
