package com.example.hospitalmanagement.DAO
import androidx.room.*
import com.example.hospitalmanagement.VitalSigns
import kotlinx.coroutines.flow.Flow
@Dao
interface VitalSignsDao {
    @Insert
    suspend fun insert(vitalSigns: VitalSigns): Long

    @Update
    suspend fun update(vitalSigns: VitalSigns)

    @Query("SELECT * FROM vital_signs WHERE appId = :appId ORDER BY recordedAt DESC")
    fun getByAppointment(appId: Int): Flow<List<VitalSigns>>
}