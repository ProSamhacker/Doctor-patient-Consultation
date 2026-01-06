package com.example.hospitalmanagement

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.hospitalmanagement.databinding.ActivityBookAppointmentBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class BookAppointmentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookAppointmentBinding
    private lateinit var viewModel: MainViewModel
    private var selectedDateTime: Calendar = Calendar.getInstance()
    private var doctorId: String = ""
    private var doctorName: String = ""
    private var userId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookAppointmentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        doctorId = intent.getStringExtra("DOCTOR_ID") ?: ""
        doctorName = intent.getStringExtra("DOCTOR_NAME") ?: ""
        userId = intent.getStringExtra("USER_ID") ?: ""

        val database = AppDatabase.getDatabase(this)
        val repository = HospitalRepository(
            database.doctorDao(), database.patientDao(), database.appointmentDao(),
            database.prescriptionDao(), database.messageDao(), database.consultationSessionDao(),
            database.aiExtractionDao(), database.medicalReportDao(), database.vitalSignsDao(),
            database.notificationDao(), database.emergencyContactDao(), database.medicationDao()
        )
        val factory = MainViewModel.Factory(repository, userId, "PATIENT")
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        setupUI()
    }

    private fun setupUI() {
        binding.tvDoctorName.text = "Dr. $doctorName"
        binding.btnBack.setOnClickListener { finish() }

        // Date picker
        binding.btnSelectDate.setOnClickListener {
            val datePicker = DatePickerDialog(
                this,
                { _, year, month, day ->
                    selectedDateTime.set(Calendar.YEAR, year)
                    selectedDateTime.set(Calendar.MONTH, month)
                    selectedDateTime.set(Calendar.DAY_OF_MONTH, day)
                    updateDateTimeDisplay()
                },
                selectedDateTime.get(Calendar.YEAR),
                selectedDateTime.get(Calendar.MONTH),
                selectedDateTime.get(Calendar.DAY_OF_MONTH)
            )
            datePicker.datePicker.minDate = System.currentTimeMillis()
            datePicker.show()
        }

        // Time picker
        binding.btnSelectTime.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    selectedDateTime.set(Calendar.HOUR_OF_DAY, hour)
                    selectedDateTime.set(Calendar.MINUTE, minute)
                    updateDateTimeDisplay()
                },
                selectedDateTime.get(Calendar.HOUR_OF_DAY),
                selectedDateTime.get(Calendar.MINUTE),
                false
            ).show()
        }

        // Book appointment
        binding.btnBookAppointment.setOnClickListener {
            bookAppointment()
        }

        updateDateTimeDisplay()
    }

    private fun updateDateTimeDisplay() {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        binding.tvSelectedDate.text = dateFormat.format(selectedDateTime.time)
        binding.tvSelectedTime.text = timeFormat.format(selectedDateTime.time)
    }

    private fun bookAppointment() {
        val complaint = binding.etComplaint.text.toString().trim()

        if (complaint.isBlank()) {
            binding.tilComplaint.error = "Please describe your concern"
            return
        }

        if (selectedDateTime.timeInMillis < System.currentTimeMillis()) {
            Toast.makeText(this, "Please select a future date/time", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnBookAppointment.isEnabled = false
        binding.progressBar.visibility = android.view.View.VISIBLE

        lifecycleScope.launch {
            try {
                viewModel.createAppointment(
                    doctorId = doctorId,
                    patientId = userId,
                    dateTime = selectedDateTime.timeInMillis,
                    chiefComplaint = complaint
                )

                binding.progressBar.visibility = android.view.View.GONE
                Toast.makeText(this@BookAppointmentActivity, "Appointment booked successfully!", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                binding.progressBar.visibility = android.view.View.GONE
                binding.btnBookAppointment.isEnabled = true
                Toast.makeText(this@BookAppointmentActivity, "Booking failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}