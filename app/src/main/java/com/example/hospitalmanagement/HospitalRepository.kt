package com.example.hospitalmanagement

import android.util.Log
import com.example.hospitalmanagement.DAO.*
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

    // Get API key from BuildConfig (generated during build)
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

    // ===== AI Operations =====

    /**
     * Extract medical information from consultation transcript
     */
    suspend fun extractMedicalInfo(transcript: String): MedicalExtractionResult {
        if (transcript.isBlank()) {
            return MedicalExtractionResult(
                symptoms = "No symptoms recorded",
                diagnosis = "Consultation incomplete",
                severity = "NORMAL",
                medications = emptyList(),
                labTests = emptyList(),
                instructions = "Please complete consultation",
                followUpDays = null
            )
        }

        val prompt = """
            You are a medical AI assistant. Analyze this doctor-patient conversation and extract key information.
            
            Conversation: "$transcript"
            
            Provide a JSON response with this EXACT structure (no markdown, no backticks):
            {
                "symptoms": "comma-separated list of symptoms mentioned",
                "diagnosis": "most likely diagnosis based on symptoms",
                "severity": "LOW or NORMAL or HIGH or CRITICAL",
                "medications": [
                    {
                        "name": "medication name",
                        "dosage": "e.g., 500mg",
                        "frequency": "e.g., Twice daily",
                        "duration": "e.g., 7 days",
                        "timing": "e.g., After food",
                        "instructions": "additional notes"
                    }
                ],
                "labTests": ["test name 1", "test name 2"],
                "instructions": "general care instructions for patient",
                "followUpDays": 7
            }
            
            Important:
            - If symptoms unclear, list "symptoms require clarification"
            - If no diagnosis clear, say "pending further evaluation"
            - Only suggest common, safe medications
            - Always include follow-up recommendation
        """.trimIndent()

        return try {
            val responseText = queryGemini(prompt, summarize = false)
            parseMedicalExtraction(responseText)
        } catch (e: Exception) {
            Log.e("REPO_ERROR", "Medical extraction failed", e)
            MedicalExtractionResult(
                symptoms = "Error processing consultation",
                diagnosis = "Please review manually: ${e.message}",
                severity = "NORMAL",
                medications = emptyList(),
                labTests = emptyList(),
                instructions = "Manual review required",
                followUpDays = 7
            )
        }
    }

    /**
     * Get layman explanation of medical terms
     */
    suspend fun getLaymanExplanation(query: String): String {
        if (query.isBlank()) {
            return "Please ask a specific medical question."
        }

        val prompt = """
            You are explaining medical concepts to a patient with no medical background.
            
            Question: "$query"
            
            Provide a clear, simple explanation in 2-3 sentences using everyday language.
            Avoid medical jargon. Be empathetic and reassuring.
            If it's a serious condition, gently suggest consulting a doctor.
        """.trimIndent()

        return try {
            queryGemini(prompt, summarize = true)
        } catch (e: Exception) {
            Log.e("REPO_ERROR", "Layman explanation failed", e)
            "I'm having trouble explaining that right now. Please ask your doctor for clarification."
        }
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
        if (geminiApiKey.isBlank() || geminiApiKey == "null") {
            throw IllegalStateException(
                "Gemini API key not configured. Please add GEMINI_API_KEY to gradle.properties"
            )
        }

        val enhancedPrompt = if (summarize) {
            "Provide a concise, clear answer: $prompt"
        } else {
            prompt
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key=$geminiApiKey"

        val safePrompt = enhancedPrompt
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

        val requestBody = """
        {
          "contents": [{
            "parts":[{
              "text": "$safePrompt"
            }]
          }],
          "generationConfig": {
            "temperature": 0.7,
            "topK": 40,
            "topP": 0.95,
            "maxOutputTokens": 2048
          }
        }
        """.trimIndent()

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .addHeader("Content-Type", "application/json")
                    .build()

                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    Log.e("GEMINI_API", "Error ${response.code}: $errorBody")
                    throw IOException("API request failed: ${response.code}")
                }

                val responseBody = response.body?.string() 
                    ?: throw IOException("Empty response from API")
                
                Log.d("GEMINI_API", "Success response received")
                extractTextFromGemini(responseBody)
            } catch (e: IOException) {
                Log.e("GEMINI_API", "Network error", e)
                throw IOException("Network error: Please check your internet connection")
            } catch (e: Exception) {
                Log.e("GEMINI_API", "Unexpected error", e)
                throw Exception("Failed to process request: ${e.message}")
            }
        }
    }

    private fun extractTextFromGemini(jsonString: String?): String {
        if (jsonString.isNullOrBlank()) {
            throw IllegalArgumentException("Empty response from API")
        }

        return try {
            val gson = Gson()
            @Suppress("UNCHECKED_CAST")
            val responseMap = gson.fromJson(jsonString, Map::class.java) as Map<String, Any>

            if (responseMap.containsKey("error")) {
                val error = responseMap["error"] as? Map<*, *>
                val message = error?.get("message") ?: "Unknown error"
                throw IOException("API Error: $message")
            }

            @Suppress("UNCHECKED_CAST")
            val candidates = responseMap["candidates"] as? List<Map<String, Any>>
            if (candidates.isNullOrEmpty()) {
                throw IllegalArgumentException("No content in API response")
            }

            @Suppress("UNCHECKED_CAST")
            val content = candidates[0]["content"] as? Map<String, Any>
                ?: throw IllegalArgumentException("Invalid response structure")
            
            @Suppress("UNCHECKED_CAST")
            val parts = content["parts"] as? List<Map<String, String>>
                ?: throw IllegalArgumentException("Invalid response structure")
                
            val text = parts.firstOrNull()?.get("text")
                ?: throw IllegalArgumentException("No text in response")

            text.replace("```json", "")
                .replace("```", "")
                .trim()
        } catch (e: Exception) {
            Log.e("GEMINI_PARSE", "Parse error: $jsonString", e)
            throw Exception("Failed to parse API response: ${e.message}")
        }
    }

    private fun parseMedicalExtraction(jsonString: String): MedicalExtractionResult {
        return try {
            val gson = Gson()
            val type = object : TypeToken<MedicalExtractionResult>() {}.type
            val result: MedicalExtractionResult = gson.fromJson(jsonString, type)
            
            if (result.symptoms.isBlank()) {
                throw IllegalArgumentException("Invalid extraction: no symptoms")
            }
            
            result
        } catch (e: Exception) {
            Log.e("JSON_PARSE", "Failed to parse: $jsonString", e)
            
            val diagnosisMatch = Regex("\"diagnosis\"\\s*:\\s*\"([^\"]+)\"").find(jsonString)
            val symptomsMatch = Regex("\"symptoms\"\\s*:\\s*\"([^\"]+)\"").find(jsonString)
            
            MedicalExtractionResult(
                symptoms = symptomsMatch?.groupValues?.get(1) ?: "Could not extract symptoms",
                diagnosis = diagnosisMatch?.groupValues?.get(1) ?: "Analysis incomplete",
                severity = "NORMAL",
                medications = emptyList(),
                labTests = emptyList(),
                instructions = "Please review consultation notes manually. AI extraction incomplete.",
                followUpDays = 7
            )
        }
    }

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