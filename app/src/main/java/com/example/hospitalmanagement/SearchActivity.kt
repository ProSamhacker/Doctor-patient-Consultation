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
import com.example.hospitalmanagement.databinding.ActivitySearchBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: DoctorSearchAdapter
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
        // Back button
        binding.btnBack.setOnClickListener { finish() }

        // Setup RecyclerView
        adapter = DoctorSearchAdapter(emptyList()) { doctor ->
            // Navigate to booking
            val intent = Intent(this, BookAppointmentActivity::class.java)
            intent.putExtra("DOCTOR_ID", doctor.doctorId)
            intent.putExtra("DOCTOR_NAME", doctor.name)
            intent.putExtra("USER_ID", userId)
            startActivity(intent)
        }
        binding.rvSearchResults.layoutManager = LinearLayoutManager(this)
        binding.rvSearchResults.adapter = adapter

        // Search input
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

        // Show initial list (if user is PATIENT, show all active doctors)
        if (userRole == "PATIENT") {
            loadAllDoctors()
        }
    }

    private fun loadAllDoctors() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val doctors = viewModel.repository.getAllActiveDoctors().first()
                binding.progressBar.visibility = View.GONE
                if (doctors.isEmpty()) {
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.rvSearchResults.visibility = View.GONE
                } else {
                    binding.tvEmptyState.visibility = View.GONE
                    binding.rvSearchResults.visibility = View.VISIBLE
                    adapter.updateData(doctors)
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
            }
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val results = if (userRole == "PATIENT") {
                    viewModel.repository.searchDoctors(query).first()
                } else {
                    viewModel.repository.searchPatients(query).first()
                }

                binding.progressBar.visibility = View.GONE

                if (results.isEmpty()) {
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.rvSearchResults.visibility = View.GONE
                } else {
                    binding.tvEmptyState.visibility = View.GONE
                    binding.rvSearchResults.visibility = View.VISIBLE

                    if (results.first() is Doctor) {
                        adapter.updateData(results.filterIsInstance<Doctor>())
                    }
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@SearchActivity, "Search failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}