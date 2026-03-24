package com.example.hospitalmanagement

import android.content.Context
import android.util.Log
import androidx.lifecycle.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainViewModel(
        val repository: HospitalRepository,
        val userId: String,
        private val userRole: String
) : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()

    // Firestore real-time listener references (cleaned up in onCleared)
    private var appointmentListener: ListenerRegistration? = null
    private var notificationListener: ListenerRegistration? = null

    // -----------------------------------------------------------------------------
    // LIVE DATA PROPERTIES
    // -----------------------------------------------------------------------------
    private val _currentDoctor = MutableLiveData<Doctor?>()
    val currentDoctor: LiveData<Doctor?> = _currentDoctor

    private val _currentPatient = MutableLiveData<Patient?>()
    val currentPatient: LiveData<Patient?> = _currentPatient

    val upcomingAppointments: LiveData<List<Appointment>> =
            if (userRole == "DOCTOR") {
                repository.getDoctorUpcomingAppointments(userId).asLiveData()
            } else {
                liveData { emitSource(repository.getPatientAppointments(userId).asLiveData()) }
            }

    val allAppointments: LiveData<List<Appointment>> =
            if (userRole == "DOCTOR") {
                repository.getDoctorAppointments(userId).asLiveData()
            } else {
                repository.getPatientAppointments(userId).asLiveData()
            }

    val prescriptions: LiveData<List<Prescription>> =
            if (userRole == "DOCTOR") {
                repository.getDoctorPrescriptions(userId).asLiveData()
            } else {
                repository.getPatientPrescriptions(userId).asLiveData()
            }

    val notifications: LiveData<List<NotificationEntity>> =
            repository.getUserNotifications(userId).asLiveData()

    val unreadNotificationCount: LiveData<Int> = repository.getUnreadCount(userId).asLiveData()

    private val _currentSessionId = MutableLiveData<Int?>()
    val currentSessionId: LiveData<Int?> = _currentSessionId

    private val _consultationTranscript = MutableLiveData<String>()
    val consultationTranscript: LiveData<String> = _consultationTranscript

    private val _searchResults = MutableLiveData<List<Any>>()
    val searchResults: LiveData<List<Any>> = _searchResults

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _messages = MutableLiveData<String>()
    val messages: LiveData<String> = _messages

    // -----------------------------------------------------------------------------
    // INITIALIZATION
    // -----------------------------------------------------------------------------
    init {
        loadUserData()
        startRealtimeListeners()   // ← real-time instead of one-shot sync
    }

    private fun loadUserData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (userRole == "DOCTOR") {
                    val localDoctor = repository.getDoctor(userId)
                    if (localDoctor != null) {
                        _currentDoctor.value = localDoctor
                    }
                } else {
                    val localPatient = repository.getPatient(userId)
                    if (localPatient != null) {
                        _currentPatient.value = localPatient
                    } else {
                        fetchPatientFromFirebase()
                    }
                }
            } catch (e: Exception) {
                _messages.value = "Error loading profile: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadDoctorById(id: String) {
        viewModelScope.launch {
            val doctor = repository.getDoctor(id)
            if (doctor != null) _currentDoctor.value = doctor
        }
    }

    fun loadPatientById(id: String) {
        viewModelScope.launch {
            val patient = repository.getPatient(id)
            if (patient != null) _currentPatient.value = patient
        }
    }

    private fun fetchPatientFromFirebase() {
        firestore
                .collection("patients")
                .document(userId)
                .get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        try {
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
                                                    (doc.get("allergies") as? List<*>)?.mapNotNull {
                                                        it as? String
                                                    }
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
                            _currentPatient.value = patient
                            viewModelScope.launch { repository.insertPatient(patient) }
                        } catch (e: Exception) {
                            Log.e("ViewModel", "Failed to map patient", e)
                            _messages.value = "Failed to sync profile: ${e.message}"
                        }
                    }
                }
                .addOnFailureListener { _messages.value = "Failed to sync profile: ${it.message}" }
    }

    // -----------------------------------------------------------------------------
    // REAL-TIME FIRESTORE LISTENERS (replaces one-shot syncData)
    // -----------------------------------------------------------------------------

    private fun startRealtimeListeners() {
        startAppointmentListener()
        startNotificationListener()
        // Also do a one-time sync of doctors/patients catalogue
        viewModelScope.launch {
            try {
                repository.syncDoctorsFromFirebase()
            } catch (e: Exception) {
                Log.e("REPO_SYNC", "Doctor sync failed: ${e.message}")
            }
        }
    }

    /**
     * Attaches a Firestore snapshot listener on the 'appointments' collection.
     * When any appointment for this user is added or updated remotely (e.g., by the
     * other device), the change is immediately upserted into Room, which triggers
     * the Room Flow → allAppointments LiveData → UI refreshes automatically.
     */
    private fun startAppointmentListener() {
        val field = if (userRole == "DOCTOR") "doctorId" else "patientId"

        appointmentListener = firestore
                .collection("appointments")
                .whereEqualTo(field, userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("REALTIME", "Appointment listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    snapshot ?: return@addSnapshotListener

                    viewModelScope.launch(Dispatchers.IO) {
                        Log.d("REALTIME", "Appointment snapshot: ${snapshot.documents.size} docs for $userRole/$userId")
                        if (snapshot.documents.isEmpty()) {
                            Log.d("REALTIME", "No appointments in Firestore for this $userRole yet.")
                        }
                        snapshot.documents.forEach { doc ->
                            try {
                                // Use appId field; fall back to document ID if field is missing
                                val rawAppId = (doc.get("appId") as? Number)?.toInt()
                                    ?: doc.id.toIntOrNull()
                                    ?: 0

                                val doctorId  = doc.getString("doctorId")  ?: ""
                                val patientId = doc.getString("patientId") ?: ""

                                Log.d("REALTIME", "  doc=${doc.id} appId=$rawAppId doctorId=$doctorId patientId=$patientId status=${doc.getString("status")}")

                                if (rawAppId == 0 || doctorId.isEmpty() || patientId.isEmpty()) {
                                    Log.w("REALTIME", "  Skipping invalid doc: appId=$rawAppId doctorId=$doctorId patientId=$patientId")
                                    return@forEach
                                }

                                val appointment = Appointment(
                                    appId         = rawAppId,
                                    doctorId      = doctorId,
                                    patientId     = patientId,
                                    dateTime      = (doc.get("dateTime") as? Number)?.toLong() ?: 0L,
                                    chiefComplaint = doc.getString("chiefComplaint") ?: "",
                                    status = AppointmentStatus.valueOf(
                                        doc.getString("status") ?: "PENDING"
                                    )
                                )

                                // Ensure FK entities exist before upsert
                                if (userRole == "DOCTOR") {
                                    val patient = repository.getPatient(appointment.patientId)
                                    if (patient == null) {
                                        Log.d("REALTIME", "  Fetching missing patient ${appointment.patientId}")
                                        repository.fetchAndSavePatientPublic(appointment.patientId)
                                    }
                                } else {
                                    val doctor = repository.getDoctor(appointment.doctorId)
                                    if (doctor == null) {
                                        Log.d("REALTIME", "  Fetching missing doctor ${appointment.doctorId}")
                                        repository.fetchAndSaveDoctorPublic(appointment.doctorId)
                                    }
                                }

                                repository.insertAppointmentDirect(appointment)
                                Log.d("REALTIME", "  Upserted appointment appId=$rawAppId into Room")
                            } catch (e: Exception) {
                                Log.e("REALTIME", "Error processing appointment doc ${doc.id}: ${e.message}", e)
                            }
                        }
                    }
                }
    }

    /**
     * Attaches a Firestore snapshot listener on the 'notifications' collection
     * for this user. New notifications (e.g., doctor accepting appointment) are
     * immediately inserted into Room and the badge/list auto-updates.
     */
    private fun startNotificationListener() {
        notificationListener = firestore
                .collection("notifications")
                .whereEqualTo("userId", userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("REALTIME", "Notification listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    snapshot ?: return@addSnapshotListener

                    viewModelScope.launch(Dispatchers.IO) {
                        snapshot.documentChanges.forEach { change ->
                            try {
                                val doc = change.document
                                val notification = NotificationEntity(
                                    userId = doc.getString("userId") ?: return@forEach,
                                    userType = doc.getString("userType") ?: "PATIENT",
                                    title = doc.getString("title") ?: "",
                                    message = doc.getString("message") ?: "",
                                    type = NotificationType.valueOf(
                                        doc.getString("type") ?: "INFO"
                                    ),
                                    relatedId = (doc.get("relatedId") as? Number)?.toInt(),
                                    isRead = doc.getBoolean("isRead") ?: false,
                                    timestamp = (doc.get("timestamp") as? Number)?.toLong()
                                        ?: System.currentTimeMillis()
                                )
                                repository.upsertNotification(notification)
                            } catch (e: Exception) {
                                Log.e("REALTIME", "Error parsing notification: ${e.message}")
                            }
                        }
                    }
                }
    }

    override fun onCleared() {
        super.onCleared()
        appointmentListener?.remove()
        notificationListener?.remove()
    }

    // -----------------------------------------------------------------------------
    // APPOINTMENT CREATION
    // -----------------------------------------------------------------------------

    suspend fun createAppointment(
            doctorId: String,
            patientId: String,
            dateTime: Long,
            chiefComplaint: String,
            context: Context
    ) {
        _isLoading.postValue(true)
        try {
            val randomAppId = (100000..999999).random()
            
            val appointment =
                    Appointment(
                            appId = randomAppId,
                            doctorId = doctorId,
                            patientId = patientId,
                            dateTime = dateTime,
                            chiefComplaint = chiefComplaint,
                            status = AppointmentStatus.PENDING
                    )

            val newAppId = repository.createAppointment(appointment)
            repository.sendAppointmentRequestNotification(context, newAppId.toInt())

            _messages.postValue("Appointment requested successfully")
        } catch (e: Exception) {
            throw e
        } finally {
            _isLoading.postValue(false)
        }
    }

    // -----------------------------------------------------------------------------
    // ACCEPT / REJECT (now also syncs to Firestore + FCM push to patient)
    // -----------------------------------------------------------------------------

    fun acceptAppointment(appointment: Appointment, context: Context? = null) {
        viewModelScope.launch {
            try {
                val updated = appointment.copy(status = AppointmentStatus.SCHEDULED)
                // Update Room + Firestore
                repository.updateAppointmentWithSync(updated)

                // FCM push to patient
                if (context != null) {
                    FcmNotificationSender.sendToUser(
                        context = context,
                        targetUserId = appointment.patientId,
                        title = "Appointment Accepted ✅",
                        body = "Your appointment has been confirmed by the doctor.",
                        data = mapOf(
                            "type" to "APPOINTMENT_ACCEPTED",
                            "appointmentId" to appointment.appId.toString(),
                            "navigateTo" to "appointments"
                        )
                    )
                }

                // In-app notification for patient
                repository.createNotification(
                        NotificationEntity(
                                userId = appointment.patientId,
                                userType = "PATIENT",
                                title = "Appointment Accepted",
                                message = "Your appointment has been confirmed by the doctor.",
                                type = NotificationType.APPOINTMENT_ACCEPTED,
                                relatedId = appointment.appId
                        )
                )
                // Sync that notification to Firestore too so patient's listener picks it up
                repository.syncNotificationToFirebasePublic(
                    NotificationEntity(
                        userId = appointment.patientId,
                        userType = "PATIENT",
                        title = "Appointment Accepted",
                        message = "Your appointment has been confirmed by the doctor.",
                        type = NotificationType.APPOINTMENT_ACCEPTED,
                        relatedId = appointment.appId
                    )
                )

                _messages.value = "Appointment Accepted"
            } catch (e: Exception) {
                _messages.value = "Failed: ${e.message}"
            }
        }
    }

    fun rejectAppointment(appointment: Appointment, context: Context? = null) {
        viewModelScope.launch {
            try {
                val updated = appointment.copy(status = AppointmentStatus.CANCELLED)
                repository.updateAppointmentWithSync(updated)

                if (context != null) {
                    FcmNotificationSender.sendToUser(
                        context = context,
                        targetUserId = appointment.patientId,
                        title = "Appointment Declined ❌",
                        body = "The doctor is unavailable for this appointment.",
                        data = mapOf(
                            "type" to "APPOINTMENT_DECLINED",
                            "appointmentId" to appointment.appId.toString(),
                            "navigateTo" to "appointments"
                        )
                    )
                }

                repository.createNotification(
                        NotificationEntity(
                                userId = appointment.patientId,
                                userType = "PATIENT",
                                title = "Appointment Declined",
                                message = "The doctor is unavailable for this appointment.",
                                type = NotificationType.APPOINTMENT_CANCELLED,
                                relatedId = appointment.appId
                        )
                )
                repository.syncNotificationToFirebasePublic(
                    NotificationEntity(
                        userId = appointment.patientId,
                        userType = "PATIENT",
                        title = "Appointment Declined",
                        message = "The doctor is unavailable for this appointment.",
                        type = NotificationType.APPOINTMENT_CANCELLED,
                        relatedId = appointment.appId
                    )
                )

                _messages.value = "Appointment Rejected"
            } catch (e: Exception) {
                _messages.value = "Failed: ${e.message}"
            }
        }
    }

    fun updateAppointmentStatus(appointmentId: Int, status: AppointmentStatus) {
        viewModelScope.launch {
            try {
                val appointment = repository.getAppointment(appointmentId)
                appointment?.let {
                    repository.updateAppointmentWithSync(it.copy(status = status))
                }
            } catch (e: Exception) {
                _messages.value = "Failed to update: ${e.message}"
            }
        }
    }

    fun handleAppointmentAction(notification: NotificationEntity, isAccepted: Boolean, context: Context? = null) {
        viewModelScope.launch {
            try {
                repository.markNotificationRead(notification.notificationId)
                val appointmentId = notification.relatedId ?: return@launch
                val appointment = repository.getAppointment(appointmentId) ?: return@launch

                if (isAccepted) {
                    acceptAppointment(appointment, context)
                } else {
                    rejectAppointment(appointment, context)
                }
            } catch (e: Exception) {
                _messages.value = "Action failed: ${e.message}"
            }
        }
    }

    // -----------------------------------------------------------------------------
    // CONSULTATION, AI, MESSAGES
    // -----------------------------------------------------------------------------

    fun startConsultation(appointmentId: Int) {
        viewModelScope.launch {
            try {
                val sessionId = repository.startConsultation(appointmentId)
                _currentSessionId.value = sessionId.toInt()
                _consultationTranscript.value = ""
            } catch (e: Exception) {
                _messages.value = "Failed to start: ${e.message}"
            }
        }
    }

    fun addToTranscript(text: String) {
        val current = _consultationTranscript.value ?: ""
        _consultationTranscript.value = "$current $text"
    }

    fun endConsultation(finalTranscript: String) {
        viewModelScope.launch {
            try {
                _currentSessionId.value?.let { sessionId ->
                    repository.endConsultation(sessionId, finalTranscript)
                    _currentSessionId.value = null
                    _consultationTranscript.value = ""
                }
            } catch (e: Exception) {
                _messages.value = "Failed to end: ${e.message}"
            }
        }
    }

    fun extractMedicalInfo(transcript: String, callback: (MedicalExtractionResult) -> Unit) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val result = repository.extractMedicalInfo(transcript)
                callback(result)
            } catch (e: Exception) {
                _messages.value = "AI extraction failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getLaymanExplanation(medicalTerm: String, callback: (String) -> Unit) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val explanation = repository.getLaymanExplanation(medicalTerm)
                callback(explanation)
            } catch (e: Exception) {
                callback("Error: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createPrescription(
            appointmentId: Int,
            diagnosis: String,
            medications: List<MedicationSchedule>,
            instructions: String,
            labTests: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val prescription =
                        Prescription(
                                appId = appointmentId,
                                diagnosis = diagnosis,
                                medications = medications,
                                instructions = instructions,
                                labTests = labTests
                        )
                repository.createPrescription(prescription)
                _messages.value = "Prescription created"
            } catch (e: Exception) {
                _messages.value = "Failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createPrescription(prescription: Prescription) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.createPrescription(prescription)
                _messages.value = "Prescription created"
            } catch (e: Exception) {
                _messages.value = "Failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private val _medicationRefreshTrigger = MutableLiveData<Unit>(Unit)

    val todayMedications:
            LiveData<List<com.example.hospitalmanagement.ADAPTER.MedicationTrackerItem>> =
            _medicationRefreshTrigger.switchMap {
                androidx.lifecycle.liveData(Dispatchers.IO) {
                    val prescriptions =
                            if (userRole == "PATIENT") {
                                repository.getPatientPrescriptions(userId).first()
                            } else {
                                emptyList()
                            }

                    val todayItems =
                            mutableListOf<
                                    com.example.hospitalmanagement.ADAPTER.MedicationTrackerItem>()
                    prescriptions.forEach { script ->
                        if (script.isActive) {
                            script.medications.forEach { medSchedule ->
                                val isTaken =
                                        repository.isMedicationTakenToday(
                                                userId,
                                                medSchedule.medicationName
                                        )
                                todayItems.add(
                                        com.example.hospitalmanagement.ADAPTER
                                                .MedicationTrackerItem(medSchedule, isTaken)
                                )
                            }
                        }
                    }
                    emit(todayItems)
                }
            }

    fun markMedicationTaken(item: com.example.hospitalmanagement.ADAPTER.MedicationTrackerItem) {
        viewModelScope.launch {
            try {
                val log =
                        MedicationLog(
                                patientId = userId,
                                medicationName = item.schedule.medicationName,
                                scheduledFor = System.currentTimeMillis()
                        )
                repository.logMedicationTaken(log)
                _medicationRefreshTrigger.value = Unit
                _messages.value = "Marked as taken"
            } catch (e: Exception) {
                _messages.value = "Failed: ${e.message}"
            }
        }
    }

    fun searchDoctors(query: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val results = repository.searchDoctors(query).first()
                _searchResults.value = results
            } catch (e: Exception) {
                _messages.value = "Local search failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchPatients(query: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val results = repository.searchPatients(query).first()
                _searchResults.value = results
            } catch (e: Exception) {
                _messages.value = "Search failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun markNotificationAsRead(notificationId: Int) {
        viewModelScope.launch { repository.markNotificationRead(notificationId) }
    }

    fun markAllNotificationsAsRead() {
        viewModelScope.launch { repository.markAllNotificationsRead(userId) }
    }

    fun sendMessage(appId: Int, content: String, type: MessageType = MessageType.TEXT) {
        viewModelScope.launch {
            try {
                repository.sendMessage(
                        Message(
                                appId = appId,
                                senderId = userId,
                                senderType = userRole,
                                content = content,
                                messageType = type
                        )
                )
            } catch (e: Exception) {
                _messages.value = "Message failed: ${e.message}"
            }
        }
    }

    fun recordVitalSigns(appId: Int, vitals: VitalSigns) {
        viewModelScope.launch {
            try {
                repository.recordVitals(vitals.copy(appId = appId, recordedBy = userId))
                _messages.value = "Vitals recorded"
            } catch (e: Exception) {
                _messages.value = "Failed: ${e.message}"
            }
        }
    }

    class Factory(
            private val repository: HospitalRepository,
            private val userId: String,
            private val userRole: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(repository, userId, userRole) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
