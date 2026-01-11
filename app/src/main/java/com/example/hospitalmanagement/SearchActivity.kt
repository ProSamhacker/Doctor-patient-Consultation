package com.example.hospitalmanagement

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.hospitalmanagement.ADAPTER.DoctorSearchAdapter
import com.example.hospitalmanagement.ADAPTER.PatientSearchAdapter
import com.example.hospitalmanagement.databinding.ActivitySearchBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var viewModel: MainViewModel

    // Adapters
    private lateinit var doctorAdapter: DoctorSearchAdapter
    private lateinit var patientAdapter: PatientSearchAdapter

    private var searchJob: Job? = null
    private var userRole: String = "PATIENT"
    private var userId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getStringExtra("USER_ID") ?: ""
        userRole = intent.getStringExtra("USER_ROLE") ?: "PATIENT"

        val database = AppDatabase.getDatabase(this)
        val repository = HospitalRepository(
            database.doctorDao(), database.patientDao(), database.appointmentDao(),
            database.prescriptionDao(), database.messageDao(), database.consultationSessionDao(),
            database.aiExtractionDao(), database.medicalReportDao(), database.vitalSignsDao(),
            database.notificationDao(), database.emergencyContactDao(), database.medicationDao()
        )
        val factory = MainViewModel.Factory(repository, userId, userRole)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        setupUI()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        binding.rvSearchResults.layoutManager = LinearLayoutManager(this)

        if (userRole == "PATIENT") {
            // Patient searching for Doctors
            doctorAdapter = DoctorSearchAdapter(emptyList()) { doctor ->
                val intent = Intent(this, BookAppointmentActivity::class.java)
                intent.putExtra("DOCTOR_ID", doctor.doctorId)
                intent.putExtra("DOCTOR_NAME", doctor.name)
                intent.putExtra("USER_ID", userId)
                startActivity(intent)
            }
            binding.rvSearchResults.adapter = doctorAdapter
            binding.etSearch.hint = "Search doctors, specialization..."
            loadAllDoctors() // Show initial list
        } else {
            // Doctor searching for Patients
            patientAdapter = PatientSearchAdapter(emptyList()) { patient ->
                Toast.makeText(this, "Selected: ${patient.name}", Toast.LENGTH_SHORT).show()
                // You can add navigation to Patient Details here if needed
            }
            binding.rvSearchResults.adapter = patientAdapter
            binding.etSearch.hint = "Search patients by name..."
        }

        // Search input listener
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(500) // Debounce
                    performSearch(s.toString())
                }
            }
        })
    }

    private fun loadAllDoctors() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val doctors = viewModel.repository.getAllActiveDoctors().first()
                binding.progressBar.visibility = View.GONE

                if (doctors.isEmpty()) {
                    showEmptyState(true)
                } else {
                    showEmptyState(false)
                    doctorAdapter.updateData(doctors)
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@SearchActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) {
            if (userRole == "PATIENT") {
                loadAllDoctors()
            } else {
                // Clear list for doctors if search is empty
                patientAdapter.updateData(emptyList())
                showEmptyState(false)
            }
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                // Polymorphic search based on Role
                if (userRole == "PATIENT") {
                    val results = viewModel.repository.searchDoctors(query).first()
                    binding.progressBar.visibility = View.GONE

                    if (results.isEmpty()) {
                        showEmptyState(true)
                    } else {
                        showEmptyState(false)
                        doctorAdapter.updateData(results)
                    }
                } else {
                    val results = viewModel.repository.searchPatients(query).first()
                    binding.progressBar.visibility = View.GONE

                    if (results.isEmpty()) {
                        showEmptyState(true)
                    } else {
                        showEmptyState(false)
                        patientAdapter.updateData(results)
                    }
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@SearchActivity, "Search failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEmptyState(show: Boolean) {
        if (show) {
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.rvSearchResults.visibility = View.GONE
        } else {
            binding.tvEmptyState.visibility = View.GONE
            binding.rvSearchResults.visibility = View.VISIBLE
        }
    }
}