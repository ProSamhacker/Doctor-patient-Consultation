package com.example.hospitalmanagement

import androidx.lifecycle.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: HospitalRepository,
    private val userId: String,
    private val userRole: String
) : ViewModel() {

    // Current user data
    private val _currentDoctor = MutableLiveData<Doctor?>()
    val currentDoctor: LiveData<Doctor?> = _currentDoctor

    private val _currentPatient = MutableLiveData<Patient?>()
    val currentPatient: LiveData<Patient?> = _currentPatient

    // Appointments
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

    // Prescriptions
    val prescriptions: LiveData<List<Prescription>> =
        if (userRole == "DOCTOR") {
            repository.getDoctorPrescriptions(userId).asLiveData()
        } else {
            repository.getPatientPrescriptions(userId).asLiveData()
        }

    // Notifications
    val notifications: LiveData<List<NotificationEntity>> =
        repository.getUserNotifications(userId).asLiveData()

    val unreadNotificationCount: LiveData<Int> =
        repository.getUnreadCount(userId).asLiveData()

    // Current consultation session
    private val _currentSessionId = MutableLiveData<Int?>()
    val currentSessionId: LiveData<Int?> = _currentSessionId

    private val _consultationTranscript = MutableLiveData<String>()
    val consultationTranscript: LiveData<String> = _consultationTranscript

    // Search results
    private val _searchResults = MutableLiveData<List<Any>>()
    val searchResults: LiveData<List<Any>> = _searchResults

    // Loading state
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // Messages
    private val _messages = MutableLiveData<String>()
    val messages: LiveData<String> = _messages

    init {
        loadUserData()
    }

    private fun loadUserData() {
        viewModelScope.launch {
            if (userRole == "DOCTOR") {
                _currentDoctor.value = repository.getDoctor(userId)
            } else {
                _currentPatient.value = repository.getPatient(userId)
            }
        }
    }

    // Appointment operations
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

    // Consultation operations
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

    // Prescription operations
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

    // Search operations
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

    // Notification operations
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

    // AI operations
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

    // Get next patient (for doctors)
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

    // Send message
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

    // Record vital signs
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