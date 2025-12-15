package com.example.hospitalmanagement.DAO

import androidx.room.*
import com.example.hospitalmanagement.Doctor
import kotlinx.coroutines.flow.Flow

// Doctor DAO
@Dao
interface DoctorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(doctor: Doctor)

    @Update
    suspend fun update(doctor: Doctor)

    @Delete
    suspend fun delete(doctor: Doctor)

    @Query("SELECT * FROM doctors WHERE doctorId = :id")
    suspend fun getById(id: String): Doctor?

    @Query("SELECT * FROM doctors WHERE isActive = 1")
    fun getAllActive(): Flow<List<Doctor>>

    @Query("SELECT * FROM doctors WHERE specialization = :specialization AND isActive = 1")
    fun getBySpecialization(specialization: String): Flow<List<Doctor>>

    @Query("SELECT * FROM doctors WHERE name LIKE '%' || :query || '%' OR specialization LIKE '%' || :query || '%'")
    fun searchDoctors(query: String): Flow<List<Doctor>>
}