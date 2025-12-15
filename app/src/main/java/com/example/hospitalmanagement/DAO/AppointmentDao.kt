package com.example.hospitalmanagement.DAO
import androidx.room.*
import com.example.hospitalmanagement.Appointment
import com.example.hospitalmanagement.AppointmentStatus
import com.example.hospitalmanagement.AppointmentWithDetails
import kotlinx.coroutines.flow.Flow
@Dao
interface AppointmentDao {
    @Insert
    suspend fun insert(appointment: Appointment): Long

    @Update
    suspend fun update(appointment: Appointment)

    @Delete
    suspend fun delete(appointment: Appointment)

    @Query("SELECT * FROM appointments WHERE appId = :id")
    suspend fun getById(id: Int): Appointment?

    @Query("SELECT * FROM appointments WHERE doctorId = :doctorId ORDER BY dateTime DESC")
    fun getByDoctor(doctorId: String): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments WHERE patientId = :patientId ORDER BY dateTime DESC")
    fun getByPatient(patientId: String): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments WHERE doctorId = :doctorId AND status = :status ORDER BY dateTime ASC")
    fun getDoctorAppointmentsByStatus(doctorId: String, status: AppointmentStatus): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments WHERE patientId = :patientId AND status = :status ORDER BY dateTime ASC")
    fun getPatientAppointmentsByStatus(patientId: String, status: AppointmentStatus): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments WHERE doctorId = :doctorId AND dateTime >= :startDate AND dateTime <= :endDate ORDER BY dateTime ASC")
    fun getDoctorAppointmentsByDateRange(doctorId: String, startDate: Long, endDate: Long): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments WHERE doctorId = :doctorId AND status = 'SCHEDULED' AND dateTime >= :today ORDER BY dateTime ASC LIMIT :limit")
    fun getUpcomingAppointments(doctorId: String, today: Long, limit: Int): Flow<List<Appointment>>

    @Transaction
    @Query("SELECT * FROM appointments WHERE appId = :appId")
    suspend fun getAppointmentWithDetails(appId: Int): AppointmentWithDetails?
}
