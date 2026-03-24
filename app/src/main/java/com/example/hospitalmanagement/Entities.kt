package com.example.hospitalmanagement

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Type Converters for complex data types
class Converters {
        private val gson = Gson()

        @TypeConverter fun fromStringList(value: List<String>): String = gson.toJson(value)

        @TypeConverter
        fun toStringList(value: String): List<String> {
                val type = object : TypeToken<List<String>>() {}.type
                return gson.fromJson(value, type)
        }

        @TypeConverter
        fun fromMedicationList(value: List<MedicationSchedule>): String = gson.toJson(value)

        @TypeConverter
        fun toMedicationList(value: String): List<MedicationSchedule> {
                val type = object : TypeToken<List<MedicationSchedule>>() {}.type
                return gson.fromJson(value, type)
        }
}

// Doctor Entity
@Entity(tableName = "doctors")
data class Doctor(
        @PrimaryKey val doctorId: String,
        val name: String,
        val specialization: String,
        val phone: String,
        val email: String,
        val hospitalName: String = "",
        val profileImageUrl: String = "",
        val experienceYears: Int = 0,
        val rating: Float = 0f,
        val consultationFee: Double = 0.0,
        val availableFrom: String = "09:00",
        val availableTo: String = "18:00",
        val isActive: Boolean = true
)

// Patient Entity
@Entity(tableName = "patients")
@TypeConverters(Converters::class)
data class Patient(
        @PrimaryKey val patientId: String,
        val name: String,
        val age: Int,
        val gender: String,
        val phone: String,
        val email: String = "",
        val bloodGroup: String = "",
        val address: String = "",
        val emergencyContact: String = "",
        val allergies: List<String> = emptyList(),
        val chronicConditions: List<String> = emptyList(),
        val profileImageUrl: String = "",
        val registrationDate: Long = System.currentTimeMillis()
)

// Appointment Entity — FK constraints removed so real-time Firestore inserts
// succeed even when the Doctor/Patient profile isn't cached in Room yet.
@Entity(
        tableName = "appointments",
        indices = [androidx.room.Index("doctorId"), androidx.room.Index("patientId")]
)
data class Appointment(
        @PrimaryKey val appId: Int = 0,
        val doctorId: String,
        val patientId: String,
        val dateTime: Long,
        val status: AppointmentStatus = AppointmentStatus.PENDING, // Changed default to PENDING
        val type: AppointmentType = AppointmentType.REGULAR,
        val chiefComplaint: String = "",
        val notes: String = "",
        val tokenNumber: Int = 0,
        val estimatedDuration: Int = 30, // minutes
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis()
)

enum class AppointmentStatus {
        PENDING, // Added for acceptance flow
        SCHEDULED, // Accepted
        IN_PROGRESS,
        COMPLETED,
        CANCELLED,
        NO_SHOW,
        EXPIRED // Not accepted before scheduled time
}

enum class AppointmentType {
        REGULAR,
        EMERGENCY,
        FOLLOW_UP,
        TELEMEDICINE
}

// Consultation Session Entity
@Entity(
        tableName = "consultation_sessions",
        foreignKeys =
                [
                        ForeignKey(
                                entity = Appointment::class,
                                parentColumns = ["appId"],
                                childColumns = ["appId"],
                                onDelete = ForeignKey.CASCADE
                        )]
)
data class ConsultationSession(
        @PrimaryKey(autoGenerate = true) val sessionId: Int = 0,
        val appId: Int,
        val audioFilePath: String = "",
        val fullTranscript: String = "",
        val startTime: Long = System.currentTimeMillis(),
        val endTime: Long? = null,
        val duration: Int = 0, // seconds
        val isRecording: Boolean = false,
        // Video meeting tracking
        val meetingRoomId: String = "",
        val doctorJoinedAt: Long? = null,
        val patientJoinedAt: Long? = null,
        val doctorLeftAt: Long? = null,
        val patientLeftAt: Long? = null,
        val actualDuration: Int = 0, // Actual overlapping duration in seconds
        val closedBy: String = "" // "DOCTOR" or "PATIENT" or "SYSTEM"
)

// AI Extraction Entity
@Entity(
        tableName = "ai_extractions",
        foreignKeys =
                [
                        ForeignKey(
                                entity = ConsultationSession::class,
                                parentColumns = ["sessionId"],
                                childColumns = ["sessionId"],
                                onDelete = ForeignKey.CASCADE
                        )]
)
data class AiExtraction(
        @PrimaryKey(autoGenerate = true) val extractId: Int = 0,
        val sessionId: Int,
        val detectedSymptoms: String,
        val suggestedDiagnosis: String,
        val severity: String = "NORMAL", // LOW, NORMAL, HIGH, CRITICAL
        val extractedAt: Long = System.currentTimeMillis()
)

// Prescription Entity
@Entity(
        tableName = "prescriptions",
        foreignKeys =
                [
                        ForeignKey(
                                entity = Appointment::class,
                                parentColumns = ["appId"],
                                childColumns = ["appId"],
                                onDelete = ForeignKey.CASCADE
                        )]
)
@TypeConverters(Converters::class)
data class Prescription(
        @PrimaryKey(autoGenerate = true) val scriptId: Int = 0,
        val appId: Int,
        val diagnosis: String,
        val medications: List<MedicationSchedule> = emptyList(),
        val labTests: List<String> = emptyList(),
        val instructions: String = "",
        val followUpDate: Long? = null,
        val createdAt: Long = System.currentTimeMillis(),
        val isActive: Boolean = true,
        // Post-meeting prescription fields
        val sessionId: Int? = null, // Link to consultation session
        val status: PrescriptionStatus = PrescriptionStatus.DRAFT,
        val isDraft: Boolean = true, // For easy queries
        val approvedBy: String? = null, // Doctor ID who approved
        val approvedAt: Long? = null // Approval timestamp
)

enum class PrescriptionStatus {
        DRAFT, // AI-generated, awaiting doctor review
        APPROVED, // Doctor approved, sent to patient
        REJECTED // Doctor rejected, not sent
}

// Medication Schedule
data class MedicationSchedule(
        val medicationName: String,
        val dosage: String,
        val frequency: String, // "Once daily", "Twice daily", etc.
        val duration: String, // "7 days", "14 days", etc.
        val timing: String, // "After food", "Before food", "Empty stomach"
        val startDate: Long = System.currentTimeMillis(),
        val instructions: String = ""
)

// Message/Chat Entity
@Entity(
        tableName = "messages",
        foreignKeys =
                [
                        ForeignKey(
                                entity = Appointment::class,
                                parentColumns = ["appId"],
                                childColumns = ["appId"],
                                onDelete = ForeignKey.CASCADE
                        )]
)
data class Message(
        @PrimaryKey(autoGenerate = true) val messageId: Int = 0,
        val appId: Int,
        val senderId: String, // doctorId or patientId
        val senderType: String, // "DOCTOR" or "PATIENT"
        val content: String,
        val messageType: MessageType = MessageType.TEXT,
        val timestamp: Long = System.currentTimeMillis(),
        val isRead: Boolean = false,
        val audioFilePath: String = ""
)

enum class MessageType {
        TEXT,
        VOICE,
        IMAGE,
        DOCUMENT
}

// Medical Report Entity
@Entity(
        tableName = "medical_reports",
        foreignKeys =
                [
                        ForeignKey(
                                entity = Patient::class,
                                parentColumns = ["patientId"],
                                childColumns = ["patientId"],
                                onDelete = ForeignKey.CASCADE
                        )]
)
data class MedicalReport(
        @PrimaryKey(autoGenerate = true) val reportId: Int = 0,
        val patientId: String,
        val title: String,
        val reportType: String, // "Lab Test", "X-Ray", "MRI", etc.
        val filePath: String,
        val uploadedBy: String, // doctorId
        val uploadDate: Long = System.currentTimeMillis(),
        val notes: String = ""
)

// Vital Signs Entity
@Entity(
        tableName = "vital_signs",
        foreignKeys =
                [
                        ForeignKey(
                                entity = Appointment::class,
                                parentColumns = ["appId"],
                                childColumns = ["appId"],
                                onDelete = ForeignKey.CASCADE
                        )]
)
data class VitalSigns(
        @PrimaryKey(autoGenerate = true) val vitalId: Int = 0,
        val appId: Int,
        val temperature: Float? = null, // Celsius
        val bloodPressureSystolic: Int? = null,
        val bloodPressureDiastolic: Int? = null,
        val heartRate: Int? = null, // bpm
        val respiratoryRate: Int? = null,
        val oxygenSaturation: Int? = null, // SpO2 %
        val weight: Float? = null, // kg
        val height: Float? = null, // cm
        val recordedAt: Long = System.currentTimeMillis(),
        val recordedBy: String = "" // doctorId or nurseId
)

// Notification Entity
@Entity(tableName = "notifications")
data class NotificationEntity(
        @PrimaryKey(autoGenerate = true) val notificationId: Int = 0,
        val userId: String, // MUST BE STRING (to match Firebase UIDs)
        val userType: String, // "DOCTOR" or "PATIENT"
        val title: String,
        val message: String,
        val timestamp: Long = System.currentTimeMillis(),
        val isRead: Boolean = false,
        val type: NotificationType = NotificationType.INFO,
        val relatedId: Int? = null // MUST BE INT (to match Appointment IDs)
)

enum class NotificationType {
        APPOINTMENT_REQUEST,
        APPOINTMENT_ACCEPTED,
        APPOINTMENT_CANCELLED,
        PRESCRIPTION_READY,
        LAB_RESULT_READY,
        MESSAGE_RECEIVED,
        EMERGENCY,
        INFO
}
// Emergency Contact Entity
@Entity(
        tableName = "emergency_contacts",
        foreignKeys =
                [
                        ForeignKey(
                                entity = Patient::class,
                                parentColumns = ["patientId"],
                                childColumns = ["patientId"],
                                onDelete = ForeignKey.CASCADE
                        )]
)
data class EmergencyContact(
        @PrimaryKey(autoGenerate = true) val contactId: Int = 0,
        val patientId: String,
        val name: String,
        val relationship: String,
        val phone: String,
        val isPrimary: Boolean = false
)

// Medication Log Entity
@Entity(tableName = "medication_logs")
data class MedicationLog(
        @PrimaryKey(autoGenerate = true) val logId: Int = 0,
        val patientId: String,
        val medicationName: String,
        val scheduledFor: Long, // Generic timestamp for the day or specific time
        val takenAt: Long = System.currentTimeMillis(),
        val status: String = "TAKEN" // TAKEN, SKIPPED
)
