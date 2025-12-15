package com.example.hospitalmanagement.DAO
import androidx.room.*
import com.example.hospitalmanagement.Message
import kotlinx.coroutines.flow.Flow
@Dao
interface MessageDao {
    @Insert
    suspend fun insert(message: Message): Long

    @Update
    suspend fun update(message: Message)

    @Delete
    suspend fun delete(message: Message)

    @Query("SELECT * FROM messages WHERE appId = :appId ORDER BY timestamp ASC")
    fun getByAppointment(appId: Int): Flow<List<Message>>

    @Query("UPDATE messages SET isRead = 1 WHERE appId = :appId AND senderId = :senderId")
    suspend fun markAsRead(appId: Int, senderId: String)

    @Query("SELECT COUNT(*) FROM messages WHERE appId = :appId AND senderId != :userId AND isRead = 0")
    fun getUnreadCount(appId: Int, userId: String): Flow<Int>
}
