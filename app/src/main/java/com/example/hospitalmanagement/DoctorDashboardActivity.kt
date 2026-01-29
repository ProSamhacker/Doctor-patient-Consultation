package com.example.hospitalmanagement

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hospitalmanagement.ADAPTER.NotificationAdapter
import com.example.hospitalmanagement.FRAGMENTS.AppointmentsFragment
import com.example.hospitalmanagement.FRAGMENTS.DoctorHomeFragment
import com.example.hospitalmanagement.FRAGMENTS.MessagesFragment
import com.example.hospitalmanagement.FRAGMENTS.ProfileFragment
import com.example.hospitalmanagement.auth.AuthActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DoctorDashboardActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var repository: HospitalRepository

    // Voice & AI Components
    private var voiceService: VoiceRecognitionService? = null
    private var aiDialog: AlertDialog? = null

    // User Data
    private var userId: String = ""
    private var userRole: String = "DOCTOR"

    // Permission Launcher for Microphone
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showAiAssistantDialog()
        } else {
            Toast.makeText(this, "Microphone permission is required for the AI Assistant.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_doctor_dashboard)

        // 1. Get User Data
        userId = intent.getStringExtra("USER_ID") ?: ""
        userRole = intent.getStringExtra("USER_ROLE") ?: "DOCTOR"

        // 2. Setup Database & ViewModel
        val database = AppDatabase.getDatabase(this)
        repository = HospitalRepository(
            database.doctorDao(), database.patientDao(), database.appointmentDao(),
            database.prescriptionDao(), database.messageDao(), database.consultationSessionDao(),
            database.aiExtractionDao(), database.medicalReportDao(), database.vitalSignsDao(),
            database.notificationDao(), database.emergencyContactDao(), database.medicationDao()
        )
        val factory = MainViewModel.Factory(repository, userId, userRole)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        // 3. Initialize Voice Service
        setupVoiceService()

        // 4. Start Appointment Monitoring
        AppointmentScheduler.startMonitoring(
            context = this,
            scope = lifecycleScope,
            repository = viewModel.repository,
            userId = userId,
            userRole = "DOCTOR"
        )

        // 5. Setup UI Components
        setupUI(savedInstanceState)
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceService?.shutdown()
        AppointmentScheduler.stopMonitoring()
        aiDialog?.dismiss()
    }

    private fun setupVoiceService() {
        voiceService = VoiceRecognitionService(
            context = this,
            onResult = { text -> handleAiQuery(text) },
            onError = { error ->
                runOnUiThread {
                    if (aiDialog?.isShowing == true) {
                        val tvQuery = aiDialog?.findViewById<TextView>(R.id.tvAiQuery)
                        val progressBar = aiDialog?.findViewById<ProgressBar>(R.id.progressBarAi)
                        tvQuery?.text = "Didn't catch that. Tap 'Try Again'."
                        progressBar?.visibility = View.INVISIBLE
                    } else {
                        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    private fun setupUI(savedInstanceState: Bundle?) {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        val fabMic = findViewById<FloatingActionButton>(R.id.fabMicDoctor)

        // AI Assistant Button Logic
        fabMic.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                showAiAssistantDialog()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        val btnSearch = findViewById<ImageButton>(R.id.btnSearchDoctor)
        btnSearch.setOnClickListener {
            val intent = Intent(this, SearchActivity::class.java)
            intent.putExtra("USER_ID", userId)
            intent.putExtra("USER_ROLE", userRole)
            startActivity(intent)
        }

        val btnNotifications = findViewById<ImageButton>(R.id.btnNotifications)
        btnNotifications.setOnClickListener {
            showNotificationsBottomSheet()
        }

        val btnLogout = findViewById<ImageButton>(R.id.btnLogoutDoctor)
        btnLogout.setOnClickListener {
            performLogout()
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, DoctorHomeFragment())
                .commit()
        }

        bottomNav.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_home -> DoctorHomeFragment()
                R.id.nav_appointments -> AppointmentsFragment.newInstance(userId, userRole)
                R.id.nav_messages -> MessagesFragment.newInstance(userId, userRole)
                R.id.nav_profile -> ProfileFragment.newInstance(userId, userRole)
                else -> null
            }

            if (fragment != null) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit()
                true
            } else {
                false
            }
        }
    }

    private fun showAiAssistantDialog() {
        val dialogView = layoutInflater.inflate(R.layout.layout_ai_assistant, null)

        aiDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        aiDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnClose = dialogView.findViewById<View>(R.id.btnCloseAi)
        val btnTryAgain = dialogView.findViewById<View>(R.id.btnAiTryAgain)
        val tvQuery = dialogView.findViewById<TextView>(R.id.tvAiQuery)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressBarAi)
        val layoutResponse = dialogView.findViewById<View>(R.id.layoutAiResponse)
        val tvResponse = dialogView.findViewById<TextView>(R.id.tvAiResponse)

        tvQuery.text = "Listening..."
        tvResponse.text = ""
        progressBar.visibility = View.VISIBLE
        layoutResponse.visibility = View.GONE

        btnClose.setOnClickListener {
            voiceService?.stopSpeaking()
            voiceService?.stopListening()
            aiDialog?.dismiss()
        }

        btnTryAgain.setOnClickListener {
            voiceService?.stopSpeaking()
            tvQuery.text = "Listening..."
            progressBar.visibility = View.VISIBLE
            layoutResponse.visibility = View.GONE
            voiceService?.startListening()
        }

        aiDialog?.setOnDismissListener {
            voiceService?.stopListening()
            voiceService?.stopSpeaking()
        }

        aiDialog?.show()
        voiceService?.startListening()
    }

    private fun handleAiQuery(query: String) {
        runOnUiThread {
            val tvQuery = aiDialog?.findViewById<TextView>(R.id.tvAiQuery)
            tvQuery?.text = "\"$query\""
        }

        lifecycleScope.launch {
            try {
                // Call the Repository for GENERAL response (supports all questions)
                val result = viewModel.repository.getGeneralAiResponse(query)

                runOnUiThread {
                    val progressBar = aiDialog?.findViewById<ProgressBar>(R.id.progressBarAi)
                    val layoutResponse = aiDialog?.findViewById<View>(R.id.layoutAiResponse)
                    val tvResponse = aiDialog?.findViewById<TextView>(R.id.tvAiResponse)

                    progressBar?.visibility = View.GONE
                    layoutResponse?.visibility = View.VISIBLE
                    tvResponse?.text = result

                    voiceService?.speak(result)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@DoctorDashboardActivity, "AI Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showNotificationsBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_notifications, null)

        val rvNotifications = view.findViewById<RecyclerView>(R.id.rvNotificationsSheet)
        val tvEmpty = view.findViewById<TextView>(R.id.tvNoNotifications)

        rvNotifications.layoutManager = LinearLayoutManager(this)

        viewModel.notifications.observe(this) { notifications ->
            if (notifications.isNullOrEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                rvNotifications.visibility = View.GONE
            } else {
                tvEmpty.visibility = View.GONE
                rvNotifications.visibility = View.VISIBLE
                rvNotifications.adapter = NotificationAdapter(
                    notifications,
                    onNotificationClick = { notification ->
                        viewModel.markNotificationAsRead(notification.notificationId)
                    },
                    onActionClick = { notification, isAccepted ->
                        viewModel.handleAppointmentAction(notification, isAccepted)
                    }
                )
            }
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun performLogout() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)

        googleSignInClient.signOut().addOnCompleteListener(this) {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, AuthActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}