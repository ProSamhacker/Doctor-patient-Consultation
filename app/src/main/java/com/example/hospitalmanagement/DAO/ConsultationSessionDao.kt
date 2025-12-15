package com.example.hospitalmanagement.DAO
import androidx.room.*
import com.example.hospitalmanagement.ConsultationSession
import kotlinx.coroutines.flow.Flow
@Dao
interface ConsultationSessionDao {
    @Insert
    suspend fun insert(session: ConsultationSession): Long

    @Update
    suspend fun update(session: ConsultationSession)

    @Delete
    suspend fun delete(session: ConsultationSession)

    @Query("SELECT * FROM consultation_sessions WHERE sessionId = :id")
    suspend fun getById(id: Int): ConsultationSession?

    @Query("SELECT * FROM consultation_sessions WHERE appId = :appId ORDER BY startTime DESC")
    fun getByAppointment(appId: Int): Flow<List<ConsultationSession>>

    @Query("SELECT * FROM consultation_sessions WHERE appId = :appId AND isRecording = 1")
    suspend fun getActiveSession(appId: Int): ConsultationSession?
}
