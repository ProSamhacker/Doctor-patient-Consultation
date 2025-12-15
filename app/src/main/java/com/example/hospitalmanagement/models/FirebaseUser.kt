package com.example.hospitalmanagement.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Base User Model
data class User(
    @DocumentId
    val uid: String = "",
    val email: String = "",
    val userType: UserType = UserType.PATIENT,
    val profileComplete: Boolean = false,
    @ServerTimestamp
    val createdAt: Date? = null,
    val profileImageUrl: String = ""
)

enum class UserType {
    DOCTOR, PATIENT
}

// Doctor Profile
data class DoctorProfile(
    @DocumentId
    val uid: String = "",
    val name: String = "",
    val specialization: String = "",
    val qualifications: List<String> = emptyList(),
    val experience: Int = 0, // years
    val hospitalName: String = "",
    val phone: String = "",
    val email: String = "",
    val licenseNumber: String = "",
    val consultationFee: Double = 0.0,
    val availableDays: List<String> = listOf("Mon", "Tue", "Wed", "Thu", "Fri"),
    val availableTimeSlots: Map<String, TimeSlot> = emptyMap(), // day -> timeSlot
    val rating: Float = 0f,
    val totalRatings: Int = 0,
    val bio: String = "",
    val languages: List<String> = listOf("English"),
    val isActive: Boolean = true,
    val profileImageUrl: String = "",
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
)

data class TimeSlot(
    val startTime: String = "09:00",
    val endTime: String = "17:00"
)

// Patient Profile
data class PatientProfile(
    @DocumentId
    val uid: String = "",
    val name: String = "",
    val age: Int = 0,
    val gender: String = "",
    val phone: String = "",
    val email: String = "",
    val bloodGroup: String = "",
    val address: String = "",
    val emergencyContact: EmergencyContactInfo? = null,
    val allergies: List<String> = emptyList(),
    val chronicConditions: List<String> = emptyList(),
    val medications: List<String> = emptyList(), // Current medications
    val height: Float = 0f, // in cm
    val weight: Float = 0f, // in kg
    val profileImageUrl: String = "",
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
)

data class EmergencyContactInfo(
    val name: String = "",
    val relationship: String = "",
    val phone: String = ""
)

// Appointment Request Model
data class AppointmentRequest(
    @DocumentId
    val id: String = "",
    val patientId: String = "",
    val patientName: String = "",
    val doctorId: String = "",
    val doctorName: String = "",
    val requestedDate: String = "", // Format: "2025-12-15"
    val requestedTime: String = "", // Format: "10:00 AM"
    val problemDescription: String = "",
    val medicalReports: List<String> = emptyList(), // URLs to uploaded files
    val status: AppointmentRequestStatus = AppointmentRequestStatus.PENDING,
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null,
    val rejectionReason: String = ""
)

enum class AppointmentRequestStatus {
    PENDING, ACCEPTED, REJECTED, COMPLETED, CANCELLED
}

// Consultation Model
data class Consultation(
    @DocumentId
    val id: String = "",
    val appointmentId: String = "",
    val patientId: String = "",
    val patientName: String = "",
    val doctorId: String = "",
    val doctorName: String = "",
    val startTime: Date? = null,
    val endTime: Date? = null,
    val transcript: String = "",
    val summary: String = "",
    val diagnosis: String = "",
    val symptoms: List<String> = emptyList(),
    val prescriptionId: String = "",
    val status: ConsultationStatus = ConsultationStatus.SCHEDULED,
    @ServerTimestamp
    val createdAt: Date? = null
)

enum class ConsultationStatus {
    SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED
}

// Prescription Model
data class PrescriptionFirebase(
    @DocumentId
    val id: String = "",
    val consultationId: String = "",
    val patientId: String = "",
    val patientName: String = "",
    val doctorId: String = "",
    val doctorName: String = "",
    val diagnosis: String = "",
    val medications: List<MedicationItem> = emptyList(),
    val labTests: List<String> = emptyList(),
    val instructions: String = "",
    val followUpDate: String = "",
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
)

data class MedicationItem(
    val name: String = "",
    val dosage: String = "",
    val frequency: String = "",
    val duration: String = "",
    val timing: String = "", // Before/After food
    val instructions: String = ""
)

// Chat Message Model
data class ChatMessage(
    @DocumentId
    val id: String = "",
    val chatRoomId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderType: UserType = UserType.PATIENT,
    val message: String = "",
    val messageType: MessageType = MessageType.TEXT,
    val attachmentUrl: String = "",
    val isRead: Boolean = false,
    @ServerTimestamp
    val timestamp: Date? = null
)

enum class MessageType {
    TEXT, IMAGE, DOCUMENT, VOICE
}

// Chat Room Model
data class ChatRoom(
    @DocumentId
    val id: String = "",
    val patientId: String = "",
    val patientName: String = "",
    val doctorId: String = "",
    val doctorName: String = "",
    val lastMessage: String = "",
    @ServerTimestamp
    val lastMessageTime: Date? = null,
    val unreadCountPatient: Int = 0,
    val unreadCountDoctor: Int = 0,
    @ServerTimestamp
    val createdAt: Date? = null
)

// Doctor-Patient Connection
data class DoctorPatientConnection(
    @DocumentId
    val id: String = "",
    val doctorId: String = "",
    val patientId: String = "",
    @ServerTimestamp
    val connectedAt: Date? = null,
    val totalConsultations: Int = 0,
    val lastConsultationDate: Date? = null
)