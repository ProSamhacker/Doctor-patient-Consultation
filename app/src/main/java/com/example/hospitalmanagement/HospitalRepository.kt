package com.example.hospitalmanagement

import android.util.Log
import com.example.hospitalmanagement.DAO.*
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HospitalRepository(
    private val doctorDao: DoctorDao,
    private val patientDao: PatientDao,
    private val appointmentDao: AppointmentDao,
    private val prescriptionDao: PrescriptionDao,
    private val messageDao: MessageDao,
    private val consultationSessionDao: ConsultationSessionDao,
    private val aiExtractionDao: AiExtractionDao,
    private val medicalReportDao: MedicalReportDao,
    private val vitalSignsDao: VitalSignsDao,
    private val notificationDao: NotificationDao,
    private val emergencyContactDao: EmergencyContactDao,
    private val medicationDao: MedicationDao
) {

    // Initialize the official Gemini Model
    // Ensure BuildConfig.GEMINI_API_KEY is set in your local.properties
    private val geminiApiKey = BuildConfig.GEMINI_API_KEY

    // Model for structured data extraction (Forces JSON output)
    private val extractionModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = geminiApiKey,
        generationConfig = generationConfig {
            responseMimeType = "application/json"
            temperature = 0.7f
        }
    )

    // Model for plain text chat/explanation
    private val chatModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = geminiApiKey,
        generationConfig = generationConfig {
            temperature = 0.7f
        }
    )

    private val gson = Gson()

    // ===== Doctor Operations =====
    suspend fun insertDoctor(doctor: Doctor) = doctorDao.insert(doctor)
    suspend fun updateDoctor(doctor: Doctor) = doctorDao.update(doctor)
    suspend fun getDoctor(id: String) = doctorDao.getById(id)
    fun getAllActiveDoctors() = doctorDao.getAllActive()
    fun searchDoctors(query: String) = doctorDao.searchDoctors(query)
    fun getDoctorsBySpecialization(spec: String) = doctorDao.getBySpecialization(spec)

    // ===== Patient Operations =====
    suspend fun insertPatient(patient: Patient) = patientDao.insert(patient)
    suspend fun updatePatient(patient: Patient) = patientDao.update(patient)
    suspend fun getPatient(id: String) = patientDao.getById(id)
    fun getAllPatients() = patientDao.getAll()
    fun searchPatients(query: String) = patientDao.searchPatients(query)

    // ===== Appointment Operations =====
    suspend fun createAppointment(appointment: Appointment): Long {
        val appId = appointmentDao.insert(appointment)
        val doctor = doctorDao.getById(appointment.doctorId)
        val patient = patientDao.getById(appointment.patientId)

        doctor?.let {
            notificationDao.insert(
                NotificationEntity(
                    userId = it.doctorId,
                    userType = "DOCTOR",
                    title = "New Appointment",
                    message = "New appointment with ${patient?.name ?: "patient"}",
                    type = NotificationType.APPOINTMENT_CONFIRMED,
                    relatedId = appId.toInt()
                )
            )
        }

        patient?.let {
            notificationDao.insert(
                NotificationEntity(
                    userId = it.patientId,
                    userType = "PATIENT",
                    title = "Appointment Confirmed",
                    message = "Your appointment with ${doctor?.name ?: "doctor"} is confirmed",
                    type = NotificationType.APPOINTMENT_CONFIRMED,
                    relatedId = appId.toInt()
                )
            )
        }
        return appId
    }

    suspend fun updateAppointment(appointment: Appointment) = appointmentDao.update(appointment)
    suspend fun getAppointment(id: Int) = appointmentDao.getById(id)
    fun getDoctorAppointments(doctorId: String) = appointmentDao.getByDoctor(doctorId)
    fun getPatientAppointments(patientId: String) = appointmentDao.getByPatient(patientId)
    fun getDoctorUpcomingAppointments(doctorId: String, limit: Int = 10) =
        appointmentDao.getUpcomingAppointments(doctorId, System.currentTimeMillis(), limit)

    // ===== Prescription Operations =====
    suspend fun createPrescription(prescription: Prescription): Long {
        val scriptId = prescriptionDao.insert(prescription)
        val appointment = appointmentDao.getById(prescription.appId)
        appointment?.let {
            notificationDao.insert(
                NotificationEntity(
                    userId = it.patientId,
                    userType = "PATIENT",
                    title = "Prescription Ready",
                    message = "Your prescription is ready. Check your appointments.",
                    type = NotificationType.PRESCRIPTION_READY,
                    relatedId = scriptId.toInt()
                )
            )
        }
        return scriptId
    }

    suspend fun getPrescription(appId: Int) = prescriptionDao.getByAppointment(appId)
    fun getPatientPrescriptions(patientId: String) = prescriptionDao.getByPatient(patientId)
    fun getDoctorPrescriptions(doctorId: String) = prescriptionDao.getByDoctor(doctorId)

    // ===== Message Operations =====
    suspend fun sendMessage(message: Message): Long {
        val msgId = messageDao.insert(message)
        val appointment = appointmentDao.getById(message.appId)
        appointment?.let {
            val recipientId = if (message.senderType == "DOCTOR") it.patientId else it.doctorId
            val recipientType = if (message.senderType == "DOCTOR") "PATIENT" else "DOCTOR"

            notificationDao.insert(
                NotificationEntity(
                    userId = recipientId,
                    userType = recipientType,
                    title = "New Message",
                    message = message.content.take(50) + if (message.content.length > 50) "..." else "",
                    type = NotificationType.MESSAGE_RECEIVED,
                    relatedId = message.appId
                )
            )
        }
        return msgId
    }

    fun getAppointmentMessages(appId: Int) = messageDao.getByAppointment(appId)
    suspend fun markMessagesAsRead(appId: Int, senderId: String) = messageDao.markAsRead(appId, senderId)

    // ===== Consultation Session Operations =====
    suspend fun startConsultation(appId: Int): Long {
        val session = ConsultationSession(
            appId = appId,
            isRecording = true,
            startTime = System.currentTimeMillis()
        )
        return consultationSessionDao.insert(session)
    }

    suspend fun endConsultation(sessionId: Int, transcript: String) {
        val session = consultationSessionDao.getById(sessionId)
        session?.let {
            val duration = ((System.currentTimeMillis() - it.startTime) / 1000).toInt()
            consultationSessionDao.update(
                it.copy(
                    isRecording = false,
                    endTime = System.currentTimeMillis(),
                    duration = duration,
                    fullTranscript = transcript
                )
            )
        }
    }

    fun getSessionsByAppointment(appId: Int) = consultationSessionDao.getByAppointment(appId)

    // ===== AI Operations (Refactored to use SDK) =====

    /**
     * Extract medical information from consultation transcript
     */
    suspend fun extractMedicalInfo(transcript: String): MedicalExtractionResult {
        if (transcript.isBlank()) return createEmptyExtractionResult()

        return withContext(Dispatchers.IO) {
            try {
                val prompt = """
                    Analyze this doctor-patient conversation and extract key information.
                    TRANSCRIPT: "$transcript"
                    
                    Return ONLY a JSON object with this structure:
                    {
                        "symptoms": "comma-separated list",
                        "diagnosis": "likely diagnosis",
                        "severity": "NORMAL",
                        "medications": [{"name": "...", "dosage": "...", "frequency": "...", "duration": "...", "timing": "...", "instructions": "..."}],
                        "labTests": ["test1", "test2"],
                        "instructions": "care instructions",
                        "followUpDays": 7
                    }
                """.trimIndent()

                // Use the JSON-configured model
                val response = extractionModel.generateContent(prompt)
                val jsonString = response.text?.replace("```json", "")?.replace("```", "")?.trim()
                    ?: throw Exception("Empty AI response")

                parseMedicalExtraction(jsonString)

            } catch (e: Exception) {
                Log.e("REPO_ERROR", "Medical extraction failed", e)
                createErrorExtractionResult(e.message)
            }
        }
    }

    /**
     * Get layman explanation of medical terms
     */
    suspend fun getLaymanExplanation(query: String): String {
        if (query.isBlank()) return "Please ask a specific medical question."

        return withContext(Dispatchers.IO) {
            try {
                val prompt = """
                    Explain this medical concept to a patient in simple language (max 2 sentences):
                    "$query"
                """.trimIndent()

                val response = chatModel.generateContent(prompt)
                response.text ?: "I couldn't generate an explanation at this time."
            } catch (e: Exception) {
                Log.e("REPO_ERROR", "Layman explanation failed", e)
                "I'm having trouble connecting to the AI assistant right now."
            }
        }
    }

    /**
     * Correct medication spelling
     */
    suspend fun correctMedicationSpelling(name: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = "Correct the spelling of this medication. Return ONLY the corrected name: $name"
                val response = chatModel.generateContent(prompt)
                response.text?.trim() ?: name
            } catch (e: Exception) {
                name // Return original on error
            }
        }
    }

    private fun parseMedicalExtraction(jsonString: String): MedicalExtractionResult {
        return try {
            val type = object : TypeToken<MedicalExtractionResult>() {}.type
            gson.fromJson(jsonString, type)
        } catch (e: Exception) {
            Log.e("JSON_PARSE", "Failed to parse: $jsonString", e)
            createErrorExtractionResult("JSON Parsing Error")
        }
    }

    private fun createEmptyExtractionResult() = MedicalExtractionResult(
        symptoms = "No symptoms recorded",
        diagnosis = "Consultation incomplete",
        severity = "NORMAL",
        medications = emptyList(),
        labTests = emptyList(),
        instructions = "Please complete consultation",
        followUpDays = null
    )

    private fun createErrorExtractionResult(errorMsg: String?) = MedicalExtractionResult(
        symptoms = "Error processing consultation",
        diagnosis = "Manual review required: $errorMsg",
        severity = "NORMAL",
        medications = emptyList(),
        labTests = emptyList(),
        instructions = "Manual review required",
        followUpDays = 7
    )

    // ===== Other Operations =====
    suspend fun recordVitals(vitals: VitalSigns) = vitalSignsDao.insert(vitals)
    fun getAppointmentVitals(appId: Int) = vitalSignsDao.getByAppointment(appId)

    suspend fun createNotification(notification: NotificationEntity) = notificationDao.insert(notification)
    fun getUserNotifications(userId: String) = notificationDao.getByUser(userId)
    fun getUnreadNotifications(userId: String) = notificationDao.getUnread(userId)
    fun getUnreadCount(userId: String) = notificationDao.getUnreadCount(userId)
    suspend fun markNotificationRead(id: Int) = notificationDao.markAsRead(id)
    suspend fun markAllNotificationsRead(userId: String) = notificationDao.markAllAsRead(userId)

    suspend fun addEmergencyContact(contact: EmergencyContact) = emergencyContactDao.insert(contact)
    fun getPatientEmergencyContacts(patientId: String) = emergencyContactDao.getByPatient(patientId)
    suspend fun getPrimaryEmergencyContact(patientId: String) = emergencyContactDao.getPrimaryContact(patientId)

    suspend fun getAllMedications() = medicationDao.getAll()
    suspend fun insertMedication(medication: Medication) = medicationDao.insert(medication)
    suspend fun updateMedication(medication: Medication) = medicationDao.update(medication)
    suspend fun deleteMedication(medication: Medication) = medicationDao.delete(medication)
}

// Data classes for AI responses
data class MedicalExtractionResult(
    val symptoms: String,
    val diagnosis: String,
    val severity: String,
    val medications: List<MedicationInfo>,
    val labTests: List<String>,
    val instructions: String,
    val followUpDays: Int?
)

data class MedicationInfo(
    val name: String,
    val dosage: String,
    val frequency: String,
    val duration: String,
    val timing: String,
    val instructions: String
)