package com.example.hospitalmanagement.DAO
import androidx.room.*
import com.example.hospitalmanagement.AiExtraction

@Dao
interface AiExtractionDao {
    @Insert
    suspend fun insert(extraction: AiExtraction): Long

    @Update
    suspend fun update(extraction: AiExtraction)

    @Query("SELECT * FROM ai_extractions WHERE sessionId = :sessionId")
    suspend fun getBySession(sessionId: Int): List<AiExtraction>
}