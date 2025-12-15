package com.example.hospitalmanagement

import android.util.Log
import com.example.hospitalmanagement.DAO.AiExtractionDao
import com.example.hospitalmanagement.DAO.AppointmentDao
import com.example.hospitalmanagement.DAO.ConsultationSessionDao
import com.example.hospitalmanagement.DAO.DoctorDao
import com.example.hospitalmanagement.DAO.EmergencyContactDao
import com.example.hospitalmanagement.DAO.MedicalReportDao
import com.example.hospitalmanagement.DAO.MedicationDao
import com.example.hospitalmanagement.DAO.MessageDao
import com.example.hospitalmanagement.DAO.NotificationDao
import com.example.hospitalmanagement.DAO.PatientDao
import com.example.hospitalmanagement.DAO.PrescriptionDao
import com.example.hospitalmanagement.DAO.VitalSignsDao
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

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

    private val geminiApiKey = BuildConfig.GEMINI_API_KEY
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

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

        // Send notifications
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

        // Send notification to patient
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

        // Notify recipient
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

    // ===== AI Operations =====

    /**
     * Extract medical information from consultation transcript
     */
    suspend fun extractMedicalInfo(transcript: String): MedicalExtractionResult {
        val prompt = """
            Analyze this doctor-patient conversation and extract medical information.
            
            Conversation: "$transcript"
            
            Return ONLY a valid JSON object (no markdown formatting):
            {
                "symptoms": "comma-separated list of symptoms",
                "diagnosis": "potential diagnosis",
                "severity": "LOW|NORMAL|HIGH|CRITICAL",
                "medications": [
                    {
                        "name": "medication name",
                        "dosage": "dosage amount",
                        "frequency": "how often",
                        "duration": "how long",
                        "timing": "when to take",
                        "instructions": "additional notes"
                    }
                ],
                "labTests": ["list of recommended tests"],
                "instructions": "general care instructions",
                "followUpDays": 7
            }
        """.trimIndent()

        val responseText = queryGemini(prompt, summarize = false)
        return parseMedicalExtraction(responseText)
    }

    /**
     * Get layman explanation of medical terms
     */
    suspend fun getLaymanExplanation(query: String): String {
        val prompt = """
            You are a helpful medical assistant explaining to a patient.
            Explain this in very simple language (max 3 sentences):
            
            "$query"
            
            Use everyday words, avoid jargon, and be empathetic.
        """.trimIndent()

        return queryGemini(prompt, summarize = false)
    }

    /**
     * Correct medication spelling
     */
    suspend fun correctMedicationSpelling(name: String): String {
        val prompt = """
            Correct this medication name (reply with ONLY the corrected name):
            "$name"
        """.trimIndent()

        return queryGemini(prompt, summarize = false).trim()
    }

    /**
     * Query Gemini AI
     */
    private suspend fun queryGemini(prompt: String, summarize: Boolean = true): String {
        val enhancedPrompt = if (summarize) {
            "Provide a concise answer (2-3 sentences max): $prompt"
        } else {
            prompt
        }

        val url = "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent?key=$geminiApiKey"

        val safePrompt = enhancedPrompt.replace("\"", "\\\"").replace("\n", " ")

        val requestBody = """
        {
          "contents": [{
            "parts":[{
              "text": "$safePrompt"
            }]
          }]
        }
        """.trimIndent()

        val request = Request.Builder()
            .url(url)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        return withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                Log.d("GEMINI_API", "Response: $responseBody")
                extractTextFromGemini(responseBody)
            } catch (e: IOException) {
                Log.e("GEMINI_API", "Error: ${e.message}", e)
                "Network error: ${e.message}"
            }
        }
    }

    private fun extractTextFromGemini(jsonString: String?): String {
        if (jsonString.isNullOrBlank()) return "Error: Empty response"

        return try {
            val gson = Gson()
            val responseMap = gson.fromJson(jsonString, Map::class.java)

            if (responseMap.containsKey("error")) {
                return "API Error: ${responseMap["error"]}"
            }

            val candidates = responseMap["candidates"] as? List<Map<String, Any>>
            if (candidates.isNullOrEmpty()) return "Error: No content"

            val content = candidates[0]["content"] as Map<String, Any>
            val parts = content["parts"] as List<Map<String, String>>
            val text = parts[0]["text"] ?: ""

            text.replace("```json", "").replace("```", "").trim()
        } catch (e: Exception) {
            Log.e("GEMINI_PARSE", "Error parsing: $jsonString", e)
            "Error parsing response: ${e.localizedMessage}"
        }
    }

    private fun parseMedicalExtraction(jsonString: String): MedicalExtractionResult {
        return try {
            val gson = Gson()
            val type = object : TypeToken<MedicalExtractionResult>() {}.type
            gson.fromJson(jsonString, type)
        } catch (e: Exception) {
            Log.e("JSON_PARSE", "Failed to parse: $jsonString", e)
            MedicalExtractionResult(
                symptoms = "Could not extract",
                diagnosis = "Analysis failed",
                severity = "NORMAL",
                medications = emptyList(),
                labTests = emptyList(),
                instructions = "Please review consultation manually",
                followUpDays = null
            )
        }
    }

    // ===== Vital Signs Operations =====
    suspend fun recordVitals(vitals: VitalSigns) = vitalSignsDao.insert(vitals)
    fun getAppointmentVitals(appId: Int) = vitalSignsDao.getByAppointment(appId)

    // ===== Notification Operations =====
    suspend fun createNotification(notification: NotificationEntity) = notificationDao.insert(notification)
    fun getUserNotifications(userId: String) = notificationDao.getByUser(userId)
    fun getUnreadNotifications(userId: String) = notificationDao.getUnread(userId)
    fun getUnreadCount(userId: String) = notificationDao.getUnreadCount(userId)
    suspend fun markNotificationRead(id: Int) = notificationDao.markAsRead(id)
    suspend fun markAllNotificationsRead(userId: String) = notificationDao.markAllAsRead(userId)

    // ===== Emergency Contact Operations =====
    suspend fun addEmergencyContact(contact: EmergencyContact) = emergencyContactDao.insert(contact)
    fun getPatientEmergencyContacts(patientId: String) = emergencyContactDao.getByPatient(patientId)
    suspend fun getPrimaryEmergencyContact(patientId: String) = emergencyContactDao.getPrimaryContact(patientId)

    // ===== Medication Operations =====
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