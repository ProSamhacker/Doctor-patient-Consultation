package com.example.hospitalmanagement.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.example.hospitalmanagement.DoctorDashboardActivity
import com.example.hospitalmanagement.PatientDashboardActivity
import com.example.hospitalmanagement.R
import com.example.hospitalmanagement.databinding.ActivityAuthBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>
    private var isLoginMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        setupGoogleSignIn()
        setupUI()

        // STARTUP CHECK:
        setLoadingState(true)

        if (auth.currentUser != null) {
            checkUserProfileOnStartup()
        } else {
            setLoadingState(false)
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        if (isLoading) {
            binding.progressBar.visibility = View.VISIBLE
            binding.tilEmail.visibility = View.GONE
            binding.tilPassword.visibility = View.GONE
            binding.btnPrimary.visibility = View.GONE
            binding.btnGoogleSignIn.visibility = View.GONE
            binding.tvToggleMode.visibility = View.GONE
            binding.tvTitle.visibility = View.GONE
            binding.tvSubtitle.visibility = View.GONE
            // binding.layoutDivider.visibility = View.GONE
        } else {
            binding.progressBar.visibility = View.GONE
            binding.tilEmail.visibility = View.VISIBLE
            binding.tilPassword.visibility = View.VISIBLE
            binding.btnPrimary.visibility = View.VISIBLE
            binding.btnGoogleSignIn.visibility = View.VISIBLE
            binding.tvToggleMode.visibility = View.VISIBLE
            binding.tvTitle.visibility = View.VISIBLE
            binding.tvSubtitle.visibility = View.VISIBLE
            // binding.layoutDivider.visibility = View.VISIBLE
        }
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account)
            } catch (e: ApiException) {
                setLoadingState(false)
                Toast.makeText(this, "Google sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupUI() {
        binding.tvToggleMode.setOnClickListener {
            isLoginMode = !isLoginMode
            updateUI()
        }

        binding.btnPrimary.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (validateInput(email, password)) {
                if (isLoginMode) {
                    loginUser(email, password)
                } else {
                    signupUser(email, password)
                }
            }
        }

        binding.btnGoogleSignIn.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }

        binding.etEmail.addTextChangedListener { binding.tilEmail.error = null }
        binding.etPassword.addTextChangedListener { binding.tilPassword.error = null }

        updateUI()
    }

    private fun updateUI() {
        if (isLoginMode) {
            binding.tvTitle.text = "Welcome Back"
            binding.tvSubtitle.text = "Login to continue your healthcare journey"
            binding.btnPrimary.text = "Login"
            binding.tvToggleMode.text = "Don't have an account? Sign up"
        } else {
            binding.tvTitle.text = "Create Account"
            binding.tvSubtitle.text = "Join us to connect with healthcare professionals"
            binding.btnPrimary.text = "Sign Up"
            binding.tvToggleMode.text = "Already have an account? Login"
        }
    }

    private fun validateInput(email: String, password: String): Boolean {
        var isValid = true
        if (email.isEmpty()) {
            binding.tilEmail.error = "Email is required"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Invalid email format"
            isValid = false
        }
        if (password.isEmpty()) {
            binding.tilPassword.error = "Password is required"
            isValid = false
        } else if (!isLoginMode && password.length < 6) {
            binding.tilPassword.error = "Password must be at least 6 characters"
            isValid = false
        }
        return isValid
    }

    private fun loginUser(email: String, password: String) {
        setLoadingState(true)
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    checkUserProfileAfterLogin()
                } else {
                    setLoadingState(false)
                    Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun signupUser(email: String, password: String) {
        setLoadingState(true)
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    navigateToRoleSelection()
                } else {
                    setLoadingState(false)
                    Toast.makeText(this, "Signup failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        setLoadingState(true)
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val isNewUser = task.result?.additionalUserInfo?.isNewUser ?: false
                    if (isNewUser) navigateToRoleSelection() else checkUserProfileAfterLogin()
                } else {
                    setLoadingState(false)
                    Toast.makeText(this, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // ------------------------------------------------------------------------
    // NAVIGATION & PROFILE CHECKS
    // ------------------------------------------------------------------------

    private fun checkUserProfileOnStartup() {
        val userId = auth.currentUser?.uid ?: return

        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val profileComplete = document.getBoolean("profileComplete") ?: false
                    if (profileComplete) {
                        // FIXED: Redirect to specific Dashboard based on role
                        navigateToDashboard(document.getString("userType") ?: "PATIENT")
                    } else {
                        auth.signOut()
                        setLoadingState(false)
                    }
                } else {
                    auth.signOut()
                    setLoadingState(false)
                }
            }
            .addOnFailureListener {
                auth.signOut()
                setLoadingState(false)
                Toast.makeText(this, "Session check failed. Please login again.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun checkUserProfileAfterLogin() {
        val userId = auth.currentUser?.uid ?: return

        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val profileComplete = document.getBoolean("profileComplete") ?: false
                    val userType = document.getString("userType") ?: "PATIENT"

                    if (profileComplete) {
                        // FIXED: Redirect to specific Dashboard
                        navigateToDashboard(userType)
                    } else {
                        navigateToProfileSetup(userType)
                    }
                } else {
                    navigateToRoleSelection()
                }
            }
            .addOnFailureListener {
                setLoadingState(false)
                Toast.makeText(this, "Error fetching profile", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * UPDATED NAVIGATION LOGIC
     * Routes users to DoctorDashboardActivity or PatientDashboardActivity
     */
    private fun navigateToDashboard(userRole: String) {
        val userId = auth.currentUser?.uid ?: return

        // Select the correct Activity Class based on role
        val targetActivity = if (userRole == "DOCTOR") {
            DoctorDashboardActivity::class.java
        } else {
            PatientDashboardActivity::class.java
        }

        val intent = Intent(this, targetActivity)
        intent.putExtra("USER_ID", userId)
        intent.putExtra("USER_ROLE", userRole)

        // Clear back stack so user cannot return to login screen
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun navigateToRoleSelection() {
        val intent = Intent(this, RoleSelectionActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun navigateToProfileSetup(userType: String) {
        val intent = Intent(this, ProfileSetupActivity::class.java)
        intent.putExtra("USER_TYPE", userType)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}