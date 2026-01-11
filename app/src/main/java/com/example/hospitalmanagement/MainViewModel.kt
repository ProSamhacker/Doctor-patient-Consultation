package com.example.hospitalmanagement

import androidx.lifecycle.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainViewModel(
    val repository: HospitalRepository,
    private val userId: String,
    private val userRole: String
) : ViewModel() {

    // Firestore instance for data syncing
    private val firestore = FirebaseFirestore.getInstance()

    // Current user data
    private val _currentDoctor = MutableLiveData<Doctor?>()
    val currentDoctor: LiveData<Doctor?> = _currentDoctor

    private val _currentPatient = MutableLiveData<Patient?>()
    val currentPatient: LiveData<Patient?> = _currentPatient

    // --- APPOINTMENTS (Reactive Data Streams) ---
    val upcomingAppointments: LiveData<List<Appointment>> =
        if (userRole == "DOCTOR") {
            repository.getDoctorUpcomingAppointments(userId).asLiveData()
        } else {
            liveData {
                emitSource(repository.getPatientAppointments(userId).asLiveData())
            }
        }

    val allAppointments: LiveData<List<Appointment>> =
        if (userRole == "DOCTOR") {
            repository.getDoctorAppointments(userId).asLiveData()
        } else {
            repository.getPatientAppointments(userId).asLiveData()
        }

    // Compatibility for AppointmentsFragment (Aliases to allAppointments)
    val doctorAppointments: LiveData<List<Appointment>> get() = allAppointments
    val patientAppointments: LiveData<List<Appointment>> get() = allAppointments

    // --- PRESCRIPTIONS ---
    val prescriptions: LiveData<List<Prescription>> =
        if (userRole == "DOCTOR") {
            repository.getDoctorPrescriptions(userId).asLiveData()
        } else {
            repository.getPatientPrescriptions(userId).asLiveData()
        }

    // --- NOTIFICATIONS ---
    val notifications: LiveData<List<NotificationEntity>> =
        repository.getUserNotifications(userId).asLiveData()

    val unreadNotificationCount: LiveData<Int> =
        repository.getUnreadCount(userId).asLiveData()

    // --- CONSULTATION SESSION ---
    private val _currentSessionId = MutableLiveData<Int?>()
    val currentSessionId: LiveData<Int?> = _currentSessionId

    private val _consultationTranscript = MutableLiveData<String>()
    val consultationTranscript: LiveData<String> = _consultationTranscript

    // --- SEARCH RESULTS ---
    private val _searchResults = MutableLiveData<List<Any>>()
    val searchResults: LiveData<List<Any>> = _searchResults

    // --- LOADING & MESSAGES ---
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _messages = MutableLiveData<String>()
    val messages: LiveData<String> = _messages

    init {
        loadUserData()
    }

    /**
     * Smart Profile Loading: Local DB -> Firebase Sync
     */
    private fun loadUserData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (userRole == "DOCTOR") {
                    val localDoctor = repository.getDoctor(userId)
                    if (localDoctor != null) {
                        _currentDoctor.value = localDoctor
                    } else {
                        fetchDoctorFromFirebase()
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

    private fun fetchDoctorFromFirebase() {
        firestore.collection("doctors").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val doctor = Doctor(
                        doctorId = userId,
                        name = document.getString("name") ?: "N/A",
                        specialization = document.getString("specialization") ?: "General",
                        phone = document.getString("phone") ?: "",
                        email = document.getString("email") ?: "",
                        hospitalName = document.getString("hospitalName") ?: "",
                        profileImageUrl = document.getString("profileImageUrl") ?: "",
                        experienceYears = document.getLong("experience")?.toInt() ?: 0,
                        consultationFee = document.getDouble("consultationFee") ?: 0.0,
                        rating = 0f,
                        isActive = true
                    )
                    _currentDoctor.value = doctor
                    viewModelScope.launch { repository.insertDoctor(doctor) }
                }
            }
            .addOnFailureListener {
                _messages.value = "Failed to sync profile: ${it.message}"
            }
    }

    private fun fetchPatientFromFirebase() {
        firestore.collection("patients").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val allergies = document.get("allergies") as? List<String> ?: emptyList()
                    val conditions = document.get("chronicConditions") as? List<String> ?: emptyList()

                    val patient = Patient(
                        patientId = userId,
                        name = document.getString("name") ?: "N/A",
                        age = document.getLong("age")?.toInt() ?: 0,
                        gender = document.getString("gender") ?: "Other",
                        phone = document.getString("phone") ?: "",
                        email = document.getString("email") ?: "",
                        bloodGroup = document.getString("bloodGroup") ?: "",
                        address = document.getString("address") ?: "",
                        profileImageUrl = document.getString("profileImageUrl") ?: "",
                        allergies = allergies,
                        chronicConditions = conditions
                    )
                    _currentPatient.value = patient
                    viewModelScope.launch { repository.insertPatient(patient) }
                }
            }
            .addOnFailureListener {
                _messages.value = "Failed to sync profile: ${it.message}"
            }
    }

    // --- APPOINTMENT OPERATIONS ---

    fun loadDoctorAppointments(id: String) {
        // No-op: Data is automatically observed via 'allAppointments' LiveData
    }

    fun loadPatientAppointments(id: String) {
        // No-op: Data is automatically observed via 'allAppointments' LiveData
    }

    fun createAppointment(
        doctorId: String,
        patientId: String,
        dateTime: Long,
        chiefComplaint: String
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val appointment = Appointment(
                    doctorId = doctorId,
                    patientId = patientId,
                    dateTime = dateTime,
                    chiefComplaint = chiefComplaint,
                    status = AppointmentStatus.SCHEDULED
                )
                repository.createAppointment(appointment)
                _messages.value = "Appointment created successfully"
            } catch (e: Exception) {
                _messages.value = "Failed to create appointment: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateAppointmentStatus(appointmentId: Int, status: AppointmentStatus) {
        viewModelScope.launch {
            try {
                val appointment = repository.getAppointment(appointmentId)
                appointment?.let {
                    repository.updateAppointment(it.copy(status = status))
                }
            } catch (e: Exception) {
                _messages.value = "Failed to update appointment: ${e.message}"
            }
        }
    }

    /**
     * Handles Appointment Accept/Reject Actions from Notifications
     */
    fun handleAppointmentAction(notification: NotificationEntity, isAccepted: Boolean) {
        viewModelScope.launch {
            try {
                // 1. Mark Notification as Read (hides the buttons in UI)
                repository.markNotificationRead(notification.notificationId)

                val appointmentId = notification.relatedId ?: return@launch
                val appointment = repository.getAppointment(appointmentId) ?: return@launch

                if (isAccepted) {
                    // ACCEPT: Notify Patient
                    val patientId = appointment.patientId
                    repository.createNotification(
                        NotificationEntity(
                            userId = patientId,
                            userType = "PATIENT",
                            title = "Appointment Confirmed",
                            message = "Your appointment has been accepted by the doctor.",
                            type = NotificationType.APPOINTMENT_CONFIRMED,
                            relatedId = appointmentId
                        )
                    )
                    _messages.value = "Appointment Accepted"
                } else {
                    // REJECT: Update Status to CANCELLED & Notify Patient
                    repository.updateAppointment(appointment.copy(status = AppointmentStatus.CANCELLED))

                    val patientId = appointment.patientId
                    repository.createNotification(
                        NotificationEntity(
                            userId = patientId,
                            userType = "PATIENT",
                            title = "Appointment Declined",
                            message = "Your appointment request was declined.",
                            type = NotificationType.APPOINTMENT_CANCELLED,
                            relatedId = appointmentId
                        )
                    )
                    _messages.value = "Appointment Rejected"
                }
            } catch (e: Exception) {
                _messages.value = "Action failed: ${e.message}"
            }
        }
    }

    // --- CONSULTATION OPERATIONS ---
    fun startConsultation(appointmentId: Int) {
        viewModelScope.launch {
            try {
                val sessionId = repository.startConsultation(appointmentId)
                _currentSessionId.value = sessionId.toInt()
                _consultationTranscript.value = ""
            } catch (e: Exception) {
                _messages.value = "Failed to start consultation: ${e.message}"
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
                _messages.value = "Failed to end consultation: ${e.message}"
            }
        }
    }

    // --- PRESCRIPTION OPERATIONS ---

    // Overload 1: Individual fields (Existing)
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
                val prescription = Prescription(
                    appId = appointmentId,
                    diagnosis = diagnosis,
                    medications = medications,
                    instructions = instructions,
                    labTests = labTests
                )
                repository.createPrescription(prescription)
                _messages.value = "Prescription created successfully"
            } catch (e: Exception) {
                _messages.value = "Failed to create prescription: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Overload 2: Object (For AppointmentsFragment)
    fun createPrescription(prescription: Prescription) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.createPrescription(prescription)
                _messages.value = "Prescription created successfully"
            } catch (e: Exception) {
                _messages.value = "Failed to create prescription: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // --- SEARCH OPERATIONS ---
    fun searchDoctors(query: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val results = repository.searchDoctors(query).first()
                _searchResults.value = results
            } catch (e: Exception) {
                _messages.value = "Search failed: ${e.message}"
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

    // --- NOTIFICATION OPERATIONS ---
    fun markNotificationAsRead(notificationId: Int) {
        viewModelScope.launch {
            repository.markNotificationRead(notificationId)
        }
    }

    fun markAllNotificationsAsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsRead(userId)
        }
    }

    // --- AI OPERATIONS ---
    fun getLaymanExplanation(medicalTerm: String, callback: (String) -> Unit) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val explanation = repository.getLaymanExplanation(medicalTerm)
                callback(explanation)
            } catch (e: Exception) {
                callback("Sorry, I couldn't explain that: ${e.message}")
            } finally {
                _isLoading.value = false
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

    // --- MISC OPERATIONS ---
    fun getNextPatient() {
        viewModelScope.launch {
            try {
                val nextAppointment = upcomingAppointments.value?.firstOrNull {
                    it.status == AppointmentStatus.SCHEDULED
                }

                nextAppointment?.let { appointment ->
                    val patient = repository.getPatient(appointment.patientId)
                    _currentPatient.value = patient
                    _messages.value = "Next patient: ${patient?.name}"
                }
            } catch (e: Exception) {
                _messages.value = "Failed to get next patient: ${e.message}"
            }
        }
    }

    fun sendMessage(appointmentId: Int, content: String, messageType: MessageType = MessageType.TEXT) {
        viewModelScope.launch {
            try {
                val message = Message(
                    appId = appointmentId,
                    senderId = userId,
                    senderType = userRole,
                    content = content,
                    messageType = messageType
                )
                repository.sendMessage(message)
            } catch (e: Exception) {
                _messages.value = "Failed to send message: ${e.message}"
            }
        }
    }

    fun recordVitalSigns(appointmentId: Int, vitals: VitalSigns) {
        viewModelScope.launch {
            try {
                repository.recordVitals(vitals.copy(appId = appointmentId, recordedBy = userId))
                _messages.value = "Vital signs recorded"
            } catch (e: Exception) {
                _messages.value = "Failed to record vitals: ${e.message}"
            }
        }
    }

    // --- FACTORY ---
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