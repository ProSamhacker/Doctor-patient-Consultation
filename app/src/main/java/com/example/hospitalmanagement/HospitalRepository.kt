package com.example.hospitalmanagement

import android.util.Log
import com.example.hospitalmanagement.DAO.*
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
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

        // Ensure BuildConfig.GEMINI_API_KEY is set in your build.gradle
        private val geminiApiKey = BuildConfig.GEMINI_API_KEY

        // Model for structured data extraction
        private val extractionModel =
                GenerativeModel(
                        modelName = "gemini-2.5-flash-lite",
                        apiKey = geminiApiKey,
                        generationConfig =
                                generationConfig {
                                        responseMimeType = "application/json"
                                        temperature = 0.7f
                                }
                )

        // Model for chat/explanation
        private val chatModel =
                GenerativeModel(
                        modelName = "gemini-2.5-flash-lite",
                        apiKey = geminiApiKey,
                        generationConfig = generationConfig { temperature = 0.7f }
                )

        private val gson = Gson()

        // ===== NEW: General AI Assistant (Any Topic) =====
        suspend fun getGeneralAiResponse(query: String): String {
                if (query.isBlank()) return "Please ask a question."

                return withContext(Dispatchers.IO) {
                        try {
                                // Prompt designed to handle any query politely
                                val prompt =
                                        """
                    You are a smart, helpful assistant in a hospital management app.
                    
                    USER QUERY: "$query"
                    
                    INSTRUCTIONS:
                    1. If the user asks about medical terms, symptoms, or drugs, provide a clear, accurate, and simple explanation.
                    2. If the user asks a general question (e.g., "How are you?", "Write a poem", "What is 2+2"), answer it helpfully and politely.
                    3. Keep responses concise (max 3 sentences) suitable for reading aloud.
                """.trimIndent()

                                val response = chatModel.generateContent(prompt)
                                response.text ?: "I couldn't generate a response."
                        } catch (e: Exception) {
                                Log.e("REPO_ERROR", "AI Request failed", e)
                                "I'm having trouble connecting to the network right now. Error: ${e.message}"
                        }
                }
        }

        // ===== Doctor Operations =====
        suspend fun insertDoctor(doctor: Doctor) = doctorDao.insert(doctor)
        suspend fun updateDoctor(doctor: Doctor) = doctorDao.update(doctor)
        suspend fun getDoctor(id: String) = doctorDao.getById(id)
        fun getAllActiveDoctors() = doctorDao.getAllActive()
        fun searchDoctors(query: String) = doctorDao.searchDoctors(query)
        fun getDoctorsBySpecialization(spec: String) = doctorDao.getBySpecialization(spec)
        suspend fun getAllDoctors(): List<Doctor> = doctorDao.getAll()

        // ===== Patient Operations =====
        suspend fun insertPatient(patient: Patient) = patientDao.insert(patient)
        suspend fun updatePatient(patient: Patient) = patientDao.update(patient)
        suspend fun getPatient(id: String) = patientDao.getById(id)
        fun getAllPatients() = patientDao.getAll()
        fun searchPatients(query: String) = patientDao.searchPatients(query)

        // ===== Appointment Operations =====
        suspend fun createAppointment(appointment: Appointment): Long {
                val appId = appointmentDao.insert(appointment)

                // Sync appointment to Firebase for cross-device access ✨
                try {
                        val firebaseAppointment =
                                hashMapOf(
                                        "doctorId" to appointment.doctorId,
                                        "patientId" to appointment.patientId,
                                        "dateTime" to appointment.dateTime,
                                        "chiefComplaint" to appointment.chiefComplaint,
                                        "status" to appointment.status.name,
                                        "appId" to appId.toInt()
                                )

                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .collection("appointments")
                                .document(appId.toString())
                                .set(firebaseAppointment)
                                .await()

                        Log.d("REPO_SYNC", "Appointment synced to Firebase: $appId")
                } catch (e: Exception) {
                        Log.e("REPO_SYNC", "Failed to sync appointment to Firebase", e)
                }

                // NOTE: Notifications are now created separately to avoid duplicates
                // Call sendAppointmentRequestNotification() after creating appointment

                return appId
        }

        /**
         * Send appointment request notification to doctor and confirmation to patient This should
         * be called after creating the appointment
         */
        suspend fun sendAppointmentRequestNotification(
                context: android.content.Context,
                appointmentId: Int
        ) {
                val appointment = appointmentDao.getById(appointmentId) ?: return
                val doctor = doctorDao.getById(appointment.doctorId) ?: return
                val patient = patientDao.getById(appointment.patientId) ?: return

                // ── 1. Show LOCAL notification on the PATIENT'S device (booking confirmation) ──
                val notificationHelper = NotificationHelper(context)
                notificationHelper.showAppointmentNotification(
                        title = "Request Sent",
                        message = "Your appointment request has been sent to Dr. ${doctor.name}.",
                        appointmentId = appointmentId,
                        userRole = "PATIENT"
                )

                // ── 2. Send REAL cross-device FCM push to the DOCTOR'S device ──
                FcmNotificationSender.sendToUser(
                        context = context,
                        targetUserId = doctor.doctorId,
                        title = "New Appointment Request",
                        body = "${patient.name} is requesting an appointment: ${appointment.chiefComplaint}",
                        data = mapOf(
                                "type" to "APPOINTMENT_REQUEST",
                                "appointmentId" to appointmentId.toString(),
                                "patientId" to patient.patientId,
                                "patientName" to patient.name,
                                "navigateTo" to "appointments"
                        )
                )

                // ── 3. Store doctor's notification in Firestore (for in-app badge / list) ──
                val doctorNotification =
                        NotificationEntity(
                                userId = doctor.doctorId,
                                userType = "DOCTOR",
                                title = "New Appointment Request",
                                message =
                                        "${patient.name} requested appointment for: ${appointment.chiefComplaint}",
                                type = NotificationType.APPOINTMENT_REQUEST,
                                relatedId = appointmentId
                        )
                notificationDao.insert(doctorNotification)
                syncNotificationToFirebase(doctorNotification)

                // ── 4. Store patient's confirmation notification ──
                val patientNotification =
                        NotificationEntity(
                                userId = patient.patientId,
                                userType = "PATIENT",
                                title = "Request Sent",
                                message =
                                        "Your appointment request has been sent to Dr. ${doctor.name}.",
                                type = NotificationType.INFO,
                                relatedId = appointmentId
                        )
                notificationDao.insert(patientNotification)
                syncNotificationToFirebase(patientNotification)
        }

        /** Update Room only — used internally */
        suspend fun updateAppointment(appointment: Appointment) = appointmentDao.update(appointment)

        /**
         * Update appointment in Room AND push the new status to Firestore immediately.
         * Called by acceptAppointment/rejectAppointment so the other device's Firestore
         * listener sees the change within seconds.
         */
        suspend fun updateAppointmentWithSync(appointment: Appointment) {
                appointmentDao.update(appointment)
                try {
                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .collection("appointments")
                                .document(appointment.appId.toString())
                                .update("status", appointment.status.name)
                                .await()
                        Log.d("REPO_SYNC", "Appointment status synced to Firestore: ${appointment.appId} → ${appointment.status}")
                } catch (e: Exception) {
                        Log.e("REPO_SYNC", "Failed to sync appointment status to Firestore", e)
                }
        }

        /**
         * Direct insert or replace for real-time listener use.
         * Uses Room's REPLACE conflict strategy to handle both insert & update.
         */
        suspend fun insertAppointmentDirect(appointment: Appointment) {
                appointmentDao.insertOrReplace(appointment)
        }

        /**
         * Fetches a patient document from Firestore by ID and saves them to Room.
         * Called by the real-time appointment listener when a doctor gets a new request
         * from a patient not yet in their local Room database.
         */
        suspend fun fetchAndSavePatientPublic(patientId: String) {
                try {
                        val doc = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .collection("patients")
                                .document(patientId)
                                .get()
                                .await()
                        if (doc.exists()) {
                                val patient = Patient(
                                        patientId = doc.getString("uid") ?: doc.id,
                                        name = doc.getString("name") ?: "",
                                        age = (doc.get("age") as? Number)?.toInt() ?: 0,
                                        gender = doc.getString("gender") ?: "Unknown",
                                        phone = doc.getString("phone") ?: "",
                                        email = doc.getString("email") ?: "",
                                        bloodGroup = doc.getString("bloodGroup") ?: "",
                                        address = doc.getString("address") ?: "",
                                        emergencyContact = doc.getString("emergencyContact") ?: "",
                                        allergies = (doc.get("allergies") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                                        chronicConditions = (doc.get("chronicConditions") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                                        profileImageUrl = doc.getString("profileImageUrl") ?: "",
                                        registrationDate = (doc.get("registrationDate") as? Number)?.toLong() ?: System.currentTimeMillis()
                                )
                                patientDao.insert(patient)
                                Log.d("REPO_SYNC", "Patient $patientId fetched and saved from Firestore")
                        }
                } catch (e: Exception) {
                        Log.e("REPO_SYNC", "Failed to fetch patient $patientId from Firestore", e)
                }
        }

        /**
         * Fetches a doctor document from Firestore by ID and saves them to Room.
         * Called by the real-time appointment listener when a patient gets an appointment
         * from a doctor not yet in their local Room database.
         */
        suspend fun fetchAndSaveDoctorPublic(doctorId: String) {
                try {
                        val doc = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .collection("doctors")
                                .document(doctorId)
                                .get()
                                .await()
                        if (doc.exists()) {
                                val doctor = Doctor(
                                        doctorId = doc.getString("uid") ?: doc.id,
                                        name = doc.getString("name") ?: "",
                                        specialization = doc.getString("specialization") ?: "",
                                        phone = doc.getString("phone") ?: "",
                                        email = doc.getString("email") ?: "",
                                        hospitalName = doc.getString("hospitalName") ?: "",
                                        profileImageUrl = doc.getString("profileImageUrl") ?: "",
                                        experienceYears = (doc.get("experience") as? Number)?.toInt() ?: 0,
                                        rating = (doc.get("rating") as? Number)?.toFloat() ?: 0f,
                                        consultationFee = (doc.get("consultationFee") as? Number)?.toDouble() ?: 0.0,
                                        availableFrom = doc.getString("availableFrom") ?: "09:00",
                                        availableTo = doc.getString("availableTo") ?: "18:00",
                                        isActive = doc.getBoolean("isActive") ?: true
                                )
                                doctorDao.insert(doctor)
                                Log.d("REPO_SYNC", "Doctor $doctorId fetched and saved from Firestore")
                        }
                } catch (e: Exception) {
                        Log.e("REPO_SYNC", "Failed to fetch doctor $doctorId from Firestore", e)
                }
        }

        /**
         * Upsert a notification from the Firestore listener (avoid duplicates).
         * Only inserts if there isn't already a record for this user + type + relatedId.
         */
        suspend fun upsertNotification(notification: NotificationEntity) {
                val existing = notificationDao.getByUser(notification.userId).first()
                val alreadyExists = existing.any {
                        it.type == notification.type && it.relatedId == notification.relatedId
                }
                if (!alreadyExists) {
                        notificationDao.insert(notification)
                }
        }

        /**
         * Writes a notification to Firestore so the other user's real-time listener
         * picks it up immediately (e.g., patient sees doctor's accept/reject in real-time).
         */
        suspend fun syncNotificationToFirebasePublic(notification: NotificationEntity) {
                try {
                        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        val data = hashMapOf(
                                "userId" to notification.userId,
                                "userType" to notification.userType,
                                "title" to notification.title,
                                "message" to notification.message,
                                "type" to notification.type.name,
                                "relatedId" to notification.relatedId,
                                "isRead" to false,
                                "timestamp" to notification.timestamp
                        )
                        db.collection("notifications")
                                .add(data)
                                .await()
                        Log.d("REPO_SYNC", "Notification pushed to Firestore for ${notification.userId}")
                } catch (e: Exception) {
                        Log.e("REPO_SYNC", "Failed to push notification to Firestore", e)
                }
        }

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
                                        message =
                                                "Your prescription is ready. Check your appointments.",
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
                        val recipientId =
                                if (message.senderType == "DOCTOR") it.patientId else it.doctorId
                        val recipientType =
                                if (message.senderType == "DOCTOR") "PATIENT" else "DOCTOR"

                        notificationDao.insert(
                                NotificationEntity(
                                        userId = recipientId,
                                        userType = recipientType,
                                        title = "New Message",
                                        message =
                                                message.content.take(50) +
                                                        if (message.content.length > 50) "..."
                                                        else "",
                                        type = NotificationType.MESSAGE_RECEIVED,
                                        relatedId = message.appId
                                )
                        )
                }
                return msgId
        }

        fun getAppointmentMessages(appId: Int) = messageDao.getByAppointment(appId)
        fun getUnreadMessageCount(appId: Int, userId: String) =
                messageDao.getUnreadCount(appId, userId)
        suspend fun markMessagesAsRead(appId: Int, senderId: String) =
                messageDao.markAsRead(appId, senderId)

        /** Inserts an incoming message only if it doesn't already exist (by messageId or timestamp+sender). */
        suspend fun insertMessageIfAbsent(message: Message) {
            messageDao.insertOrIgnore(message)
        }

        suspend fun getLatestMessage(appId: Int): Message? =
                withContext(Dispatchers.IO) {
                        val messages = messageDao.getByAppointment(appId).first()
                        messages.lastOrNull()
                }

        // ===== Consultation Session Operations =====
        suspend fun startConsultation(appId: Int): Long {
                val session =
                        ConsultationSession(
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

        // ===== Video Consultation Operations =====
        suspend fun getActiveConsultationSession(appId: Int): ConsultationSession? =
                consultationSessionDao.getActiveSession(appId)

        suspend fun updateConsultationJoinTime(sessionId: Int, userId: String, userRole: String) {
                val session = consultationSessionDao.getById(sessionId)
                session?.let {
                        val updatedSession =
                                when (userRole) {
                                        "DOCTOR" ->
                                                it.copy(doctorJoinedAt = System.currentTimeMillis())
                                        "PATIENT" ->
                                                it.copy(
                                                        patientJoinedAt = System.currentTimeMillis()
                                                )
                                        else -> it
                                }
                        consultationSessionDao.update(updatedSession)
                }
        }

        suspend fun updateConsultationLeaveTime(sessionId: Int, userId: String, userRole: String) {
                val session = consultationSessionDao.getById(sessionId)
                session?.let {
                        val updatedSession =
                                when (userRole) {
                                        "DOCTOR" ->
                                                it.copy(doctorLeftAt = System.currentTimeMillis())
                                        "PATIENT" ->
                                                it.copy(patientLeftAt = System.currentTimeMillis())
                                        else -> it
                                }
                        consultationSessionDao.update(updatedSession)
                        // Calculate duration after someone leaves
                        calculateAndUpdateDuration(sessionId)
                }
        }

        suspend fun calculateAndUpdateDuration(sessionId: Int) {
                val session = consultationSessionDao.getById(sessionId) ?: return

                val doctorJoined = session.doctorJoinedAt ?: return
                val patientJoined = session.patientJoinedAt ?: return

                // Calculate overlapping time
                val startTime = maxOf(doctorJoined, patientJoined)
                val doctorLeft = session.doctorLeftAt ?: System.currentTimeMillis()
                val patientLeft = session.patientLeftAt ?: System.currentTimeMillis()
                val endTime = minOf(doctorLeft, patientLeft)

                val actualDuration =
                        if (endTime > startTime) {
                                ((endTime - startTime) / 1000).toInt() // Convert to seconds
                        } else {
                                0
                        }

                consultationSessionDao.update(session.copy(actualDuration = actualDuration))
        }

        suspend fun closeConsultationSession(sessionId: Int, closedBy: String) {
                val session = consultationSessionDao.getById(sessionId)
                session?.let {
                        consultationSessionDao.update(
                                it.copy(
                                        isRecording = false,
                                        endTime = System.currentTimeMillis(),
                                        closedBy = closedBy
                                )
                        )
                        calculateAndUpdateDuration(sessionId)
                }
        }

        suspend fun createVideoConsultationSession(appId: Int, meetingRoomId: String): Long {
                val session =
                        ConsultationSession(
                                appId = appId,
                                isRecording = true,
                                startTime = System.currentTimeMillis(),
                                meetingRoomId = meetingRoomId
                        )
                return consultationSessionDao.insert(session)
        }

        // ===== AI Operations (Medical Extraction) =====
        suspend fun extractMedicalInfo(transcript: String): MedicalExtractionResult {
                if (transcript.isBlank()) return createEmptyExtractionResult()

                return withContext(Dispatchers.IO) {
                        try {
                                val prompt =
                                        """
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

                                val response = extractionModel.generateContent(prompt)
                                val jsonString =
                                        response.text
                                                ?.replace("```json", "")
                                                ?.replace("```", "")
                                                ?.trim()
                                                ?: throw Exception("Empty AI response")

                                parseMedicalExtraction(jsonString)
                        } catch (e: Exception) {
                                Log.e("REPO_ERROR", "Medical extraction failed", e)
                                createErrorExtractionResult(e.message)
                        }
                }
        }

        // Old method for medical terms (kept for backward compatibility, but getGeneralAiResponse
        // is better)
        suspend fun getLaymanExplanation(query: String): String {
                return getGeneralAiResponse(query)
        }

        suspend fun correctMedicationSpelling(name: String): String {
                return withContext(Dispatchers.IO) {
                        try {
                                val prompt =
                                        "Correct the spelling of this medication. Return ONLY the corrected name: $name"
                                val response = chatModel.generateContent(prompt)
                                response.text?.trim() ?: name
                        } catch (e: Exception) {
                                name
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

        private fun createEmptyExtractionResult() =
                MedicalExtractionResult(
                        symptoms = "No symptoms recorded",
                        diagnosis = "Consultation incomplete",
                        severity = "NORMAL",
                        medications = emptyList(),
                        labTests = emptyList(),
                        instructions = "Please complete consultation",
                        followUpDays = null
                )

        private fun createErrorExtractionResult(errorMsg: String?) =
                MedicalExtractionResult(
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

        suspend fun createNotification(notification: NotificationEntity) =
                notificationDao.insert(notification)
        fun getUserNotifications(userId: String) = notificationDao.getByUser(userId)
        fun getUnreadNotifications(userId: String) = notificationDao.getUnread(userId)
        fun getUnreadCount(userId: String) = notificationDao.getUnreadCount(userId)
        suspend fun markNotificationRead(id: Int) = notificationDao.markAsRead(id)
        suspend fun markAllNotificationsRead(userId: String) = notificationDao.markAllAsRead(userId)

        /**
         * Mark notification as read by appointment ID and user ID. Used when doctor accepts/rejects
         * appointment from system notification.
         */
        suspend fun markNotificationReadByAppointment(appointmentId: Int, userId: String) {
                // Get all notifications for this user
                val notifications = notificationDao.getByUser(userId).first()
                // Find the one related to this appointment
                val notification =
                        notifications.firstOrNull {
                                it.relatedId == appointmentId &&
                                        it.type == NotificationType.APPOINTMENT_REQUEST
                        }
                // Mark it as read
                notification?.let { notificationDao.markAsRead(it.notificationId) }
        }

        suspend fun addEmergencyContact(contact: EmergencyContact) =
                emergencyContactDao.insert(contact)
        fun getPatientEmergencyContacts(patientId: String) =
                emergencyContactDao.getByPatient(patientId)
        suspend fun getPrimaryEmergencyContact(patientId: String) =
                emergencyContactDao.getPrimaryContact(patientId)

        suspend fun getAllMedications() = medicationDao.getAll()
        suspend fun insertMedication(medication: Medication) = medicationDao.insert(medication)
        suspend fun updateMedication(medication: Medication) = medicationDao.update(medication)
        suspend fun deleteMedication(medication: Medication) = medicationDao.delete(medication)

        suspend fun logMedicationTaken(log: com.example.hospitalmanagement.MedicationLog) =
                medicationDao.insertLog(log)
        suspend fun isMedicationTakenToday(patientId: String, medName: String): Boolean {
                val startOfDay =
                        java.util.Calendar.getInstance()
                                .apply {
                                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                                        set(java.util.Calendar.MINUTE, 0)
                                        set(java.util.Calendar.SECOND, 0)
                                        set(java.util.Calendar.MILLISECOND, 0)
                                }
                                .timeInMillis
                val endOfDay = startOfDay + (24 * 60 * 60 * 1000) - 1
                return medicationDao.isTakenToday(patientId, medName, startOfDay, endOfDay) > 0
        }

        suspend fun createMedicalReport(report: MedicalReport) = medicalReportDao.insert(report)
        fun getPatientReports(patientId: String) = medicalReportDao.getByPatient(patientId)
        suspend fun syncDoctorsFromFirebase() {
                try {
                        val snapshot =
                                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                        .collection("doctors")
                                        .whereEqualTo("isActive", true)
                                        .get()
                                        .await()

                        val doctors =
                                snapshot.documents.mapNotNull { doc ->
                                        // Robust Mapping
                                        try {
                                                Doctor(
                                                        doctorId = doc.getString("uid") ?: doc.id,
                                                        name = doc.getString("name") ?: "",
                                                        specialization =
                                                                doc.getString("specialization")
                                                                        ?: "",
                                                        phone = doc.getString("phone") ?: "",
                                                        email = doc.getString("email") ?: "",
                                                        hospitalName = doc.getString("hospitalName")
                                                                        ?: "",
                                                        profileImageUrl =
                                                                doc.getString("profileImageUrl")
                                                                        ?: "",
                                                        experienceYears =
                                                                (doc.get("experience") as? Number)
                                                                        ?.toInt()
                                                                        ?: 0,
                                                        rating =
                                                                (doc.get("rating") as? Number)
                                                                        ?.toFloat()
                                                                        ?: 0f,
                                                        consultationFee =
                                                                (doc.get("consultationFee") as?
                                                                                Number)
                                                                        ?.toDouble()
                                                                        ?: 0.0,
                                                        availableFrom =
                                                                doc.getString("availableFrom")
                                                                        ?: "09:00",
                                                        availableTo = doc.getString("availableTo")
                                                                        ?: "18:00",
                                                        isActive = true
                                                )
                                        } catch (e: Exception) {
                                                null
                                        }
                                }

                        if (doctors.isNotEmpty()) {
                                doctorDao.insertDoctors(doctors) // Save to Room
                        }
                } catch (e: Exception) {
                        e.printStackTrace()
                }
        }

        suspend fun syncPatientsFromFirebase() {
                try {
                        val snapshot =
                                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                        .collection("patients")
                                        .get()
                                        .await()

                        val patients =
                                snapshot.documents.mapNotNull { doc ->
                                        try {
                                                Patient(
                                                        patientId = doc.getString("uid") ?: doc.id,
                                                        name = doc.getString("name") ?: "",
                                                        age = (doc.get("age") as? Number)?.toInt()
                                                                        ?: 0,
                                                        gender = doc.getString("gender")
                                                                        ?: "Unknown",
                                                        phone = doc.getString("phone") ?: "",
                                                        email = doc.getString("email") ?: "",
                                                        bloodGroup = doc.getString("bloodGroup")
                                                                        ?: "",
                                                        address = doc.getString("address") ?: "",
                                                        emergencyContact =
                                                                doc.getString("emergencyContact")
                                                                        ?: "",
                                                        allergies =
                                                                (doc.get("allergies") as? List<*>)
                                                                        ?.mapNotNull {
                                                                                it as? String
                                                                        }
                                                                        ?: emptyList(),
                                                        chronicConditions =
                                                                (doc.get("chronicConditions") as?
                                                                                List<*>)
                                                                        ?.mapNotNull {
                                                                                it as? String
                                                                        }
                                                                        ?: emptyList(),
                                                        profileImageUrl =
                                                                doc.getString("profileImageUrl")
                                                                        ?: "",
                                                        registrationDate =
                                                                (doc.get("registrationDate") as?
                                                                                Number)
                                                                        ?.toLong()
                                                                        ?: System.currentTimeMillis()
                                                )
                                        } catch (e: Exception) {
                                                Log.e(
                                                        "REPO_SYNC",
                                                        "Failed to parse patient: ${doc.id}",
                                                        e
                                                )
                                                null
                                        }
                                }

                        if (patients.isNotEmpty()) {
                                patientDao.insertPatients(patients)
                        }
                } catch (e: Exception) {
                        Log.e("REPO_SYNC", "Failed to sync patients", e)
                }
        }

        // ===== Firebase Sync Methods for Appointments & Notifications =====

        private suspend fun syncNotificationToFirebase(notification: NotificationEntity) {
                try {
                        val firebaseNotif =
                                hashMapOf(
                                        "userId" to notification.userId,
                                        "userType" to notification.userType,
                                        "title" to notification.title,
                                        "message" to notification.message,
                                        "type" to notification.type.name,
                                        "relatedId" to notification.relatedId,
                                        "isRead" to notification.isRead,
                                        "timestamp" to notification.timestamp
                                )

                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .collection("notifications")
                                .add(firebaseNotif)
                                .await()

                        Log.d(
                                "REPO_SYNC",
                                "Notification synced to Firebase for user: ${notification.userId}"
                        )
                } catch (e: Exception) {
                        Log.e("REPO_SYNC", "Failed to sync notification to Firebase", e)
                }
        }

        suspend fun syncAppointmentsFromFirebase(userId: String, userRole: String) {
                try {
                        val field = if (userRole == "DOCTOR") "doctorId" else "patientId"

                        val snapshot =
                                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                        .collection("appointments")
                                        .whereEqualTo(field, userId)
                                        .get()
                                        .await()

                        val appointments =
                                snapshot.documents.mapNotNull { doc ->
                                        try {
                                                Appointment(
                                                        appId =
                                                                (doc.get("appId") as? Number)
                                                                        ?.toInt()
                                                                        ?: 0,
                                                        doctorId = doc.getString("doctorId") ?: "",
                                                        patientId = doc.getString("patientId")
                                                                        ?: "",
                                                        dateTime =
                                                                (doc.get("dateTime") as? Number)
                                                                        ?.toLong()
                                                                        ?: 0L,
                                                        chiefComplaint =
                                                                doc.getString("chiefComplaint")
                                                                        ?: "",
                                                        status =
                                                                AppointmentStatus.valueOf(
                                                                        doc.getString("status")
                                                                                ?: "PENDING"
                                                                )
                                                )
                                        } catch (e: Exception) {
                                                Log.e(
                                                        "REPO_SYNC",
                                                        "Error parsing appointment: ${doc.id}",
                                                        e
                                                )
                                                null
                                        }
                                }

                        // Upsert to local DB (insert if new, update if exists)
                        appointments.forEach { appointment ->
                                // FOREIGN KEY FIX: Ensure patient exists before inserting
                                // appointment
                                if (userRole == "DOCTOR") {
                                        val patient = patientDao.getById(appointment.patientId)
                                        if (patient == null) {
                                                Log.d(
                                                        "REPO_SYNC",
                                                        "Fetching missing patient: ${appointment.patientId}"
                                                )
                                                fetchAndSavePatient(appointment.patientId)
                                        }
                                }

                                val existing = appointmentDao.getById(appointment.appId)
                                if (existing == null) {
                                        appointmentDao.insert(appointment)
                                } else {
                                        appointmentDao.update(appointment)
                                }
                        }

                        Log.d(
                                "REPO_SYNC",
                                "Synced ${appointments.size} appointments for $userRole: $userId"
                        )
                } catch (e: Exception) {
                        Log.e("REPO_SYNC", "Failed to sync appointments from Firebase", e)
                }
        }

        private suspend fun fetchAndSavePatient(patientId: String) {
                try {
                        val doc =
                                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                        .collection("patients")
                                        .document(patientId)
                                        .get()
                                        .await()

                        if (doc.exists()) {
                                val patient =
                                        Patient(
                                                patientId = doc.getString("uid") ?: doc.id,
                                                name = doc.getString("name") ?: "",
                                                age = (doc.get("age") as? Number)?.toInt() ?: 0,
                                                gender = doc.getString("gender") ?: "Unknown",
                                                phone = doc.getString("phone") ?: "",
                                                email = doc.getString("email") ?: "",
                                                bloodGroup = doc.getString("bloodGroup") ?: "",
                                                address = doc.getString("address") ?: "",
                                                emergencyContact = doc.getString("emergencyContact")
                                                                ?: "",
                                                allergies =
                                                        (doc.get("allergies") as? List<*>)
                                                                ?.mapNotNull { it as? String }
                                                                ?: emptyList(),
                                                chronicConditions =
                                                        (doc.get("chronicConditions") as? List<*>)
                                                                ?.mapNotNull { it as? String }
                                                                ?: emptyList(),
                                                profileImageUrl = doc.getString("profileImageUrl")
                                                                ?: "",
                                                registrationDate =
                                                        (doc.get("registrationDate") as? Number)
                                                                ?.toLong()
                                                                ?: System.currentTimeMillis()
                                        )
                                patientDao.insert(patient)
                                Log.d("REPO_SYNC", "Saved missing patient: ${patient.name}")
                        }
                } catch (e: Exception) {
                        Log.e("REPO_SYNC", "Failed to fetch patient: $patientId", e)
                }
        }

        suspend fun syncNotificationsFromFirebase(userId: String) {
                try {
                        val snapshot =
                                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                        .collection("notifications")
                                        .whereEqualTo("userId", userId)
                                        .get()
                                        .await()

                        val notifications =
                                snapshot.documents.mapNotNull { doc ->
                                        try {
                                                NotificationEntity(
                                                        userId = doc.getString("userId") ?: "",
                                                        userType = doc.getString("userType") ?: "",
                                                        title = doc.getString("title") ?: "",
                                                        message = doc.getString("message") ?: "",
                                                        type =
                                                                NotificationType.valueOf(
                                                                        doc.getString("type")
                                                                                ?: "INFO"
                                                                ),
                                                        relatedId =
                                                                (doc.get("relatedId") as? Number)
                                                                        ?.toInt()
                                                                        ?: 0,
                                                        isRead = doc.getBoolean("isRead") ?: false,
                                                        timestamp =
                                                                (doc.get("timestamp") as? Number)
                                                                        ?.toLong()
                                                                        ?: System.currentTimeMillis()
                                                )
                                        } catch (e: Exception) {
                                                Log.e(
                                                        "REPO_SYNC",
                                                        "Error parsing notification: ${doc.id}",
                                                        e
                                                )
                                                null
                                        }
                                }

                        notifications.forEach { notificationDao.insert(it) }
                        Log.d(
                                "REPO_SYNC",
                                "Synced ${notifications.size} notifications for user: $userId"
                        )
                } catch (e: Exception) {
                        Log.e("REPO_SYNC", "Failed to sync notifications from Firebase", e)
                }
        }   // end syncNotificationsFromFirebase

        suspend fun getAppointmentById(appId: Int): Appointment? =
                withContext(Dispatchers.IO) { appointmentDao.getById(appId) }

        suspend fun updateConsultationTranscript(sessionId: Int, transcript: String) {
                withContext(Dispatchers.IO) {
                        consultationSessionDao.updateTranscript(sessionId, transcript)
                        Log.d("REPO_SYNC", "Saved consultation transcript (${transcript.length} chars) for session $sessionId")
                }
        }

        suspend fun insertPrescriptionFromSummary(
                appointmentId: Int,
                sessionId: Int,
                summary: GeminiConversationAssistant.PostConsultationSummary
        ): Long {
                return withContext(Dispatchers.IO) {
                        val medications = summary.prescriptionSuggestions.map { m ->
                                MedicationSchedule(
                                        medicationName = m.medicationName,
                                        dosage         = m.dosage,
                                        frequency      = m.frequency,
                                        duration       = m.duration,
                                        timing         = m.timing,
                                        instructions   = m.instructions
                                )
                        }
                        val prescription = Prescription(
                                appId      = appointmentId,
                                diagnosis  = summary.diagnosis,
                                medications = medications,
                                labTests   = summary.recommendedTests,
                                instructions = summary.followUpRecommendations,
                                sessionId  = sessionId,
                                status     = PrescriptionStatus.DRAFT,
                                isDraft    = true
                        )
                        val id = prescriptionDao.insert(prescription)
                        Log.d("REPO_SYNC", "Inserted AI prescription #$id for appt $appointmentId")
                        id
                }
        }

        suspend fun sendPrescriptionToPatient(
                prescriptionId: Int,
                appointmentId: Int,
                diagnosis: String,
                medications: List<MedicationSchedule>,
                labTests: List<String>,
                instructions: String,
                doctorId: String,
                patientId: String
        ) {
                withContext(Dispatchers.IO) {
                        // 1. Mark as APPROVED in Room
                        val existing = prescriptionDao.getById(prescriptionId)
                        if (existing != null) {
                                prescriptionDao.update(existing.copy(
                                        diagnosis    = diagnosis,
                                        medications  = medications,
                                        labTests     = labTests,
                                        instructions = instructions,
                                        status       = PrescriptionStatus.APPROVED,
                                        isDraft      = false,
                                        approvedBy   = doctorId,
                                        approvedAt   = System.currentTimeMillis()
                                ))
                        }

                        // 2. Push to Firestore so patient can see it
                        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        val prescData = hashMapOf(
                                "prescriptionId" to prescriptionId,
                                "appointmentId"  to appointmentId,
                                "patientId"      to patientId,
                                "doctorId"       to doctorId,
                                "diagnosis"      to diagnosis,
                                "medications"    to medications.map { med ->
                                        hashMapOf(
                                                "medicationName" to med.medicationName,
                                                "dosage"         to med.dosage,
                                                "frequency"      to med.frequency,
                                                "duration"       to med.duration,
                                                "timing"         to med.timing,
                                                "instructions"   to med.instructions
                                        )
                                },
                                "labTests"       to labTests,
                                "instructions"   to instructions,
                                "status"         to "APPROVED",
                                "sentAt"         to System.currentTimeMillis()
                        )
                        firestore.collection("prescriptions")
                                .document(appointmentId.toString())
                                .set(prescData)
                                .await()

                        // 3. Notify patient
                        val notification = NotificationEntity(
                                userId   = patientId,
                                userType = "PATIENT",
                                title    = "Prescription Ready",
                                message  = "Your doctor has sent a prescription for your recent consultation.",
                                type     = NotificationType.INFO,
                                relatedId = appointmentId
                        )
                        createNotification(notification)
                        syncNotificationToFirebasePublic(notification)

                        Log.d("REPO_SYNC", "Prescription #$prescriptionId sent to patient $patientId")
                }
        }
}


// Data classes
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
