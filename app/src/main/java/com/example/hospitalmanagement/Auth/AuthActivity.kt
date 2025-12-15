package com.example.hospitalmanagement.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
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

        // Check if user is already logged in
        if (auth.currentUser != null) {
            checkUserProfile()
            return
        }

        setupGoogleSignIn()
        setupUI()
    }

    private fun setupGoogleSignIn() {
        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Register launcher for Google Sign In
        googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account)
            } catch (e: ApiException) {
                Toast.makeText(
                    this,
                    "Google sign in failed: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setupUI() {
        // Toggle between login and signup
        binding.tvToggleMode.setOnClickListener {
            isLoginMode = !isLoginMode
            updateUI()
        }

        // Primary button (Login/Signup)
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

        // Google Sign In button
        binding.btnGoogleSignIn.setOnClickListener {
            signInWithGoogle()
        }

        // Text change listeners for validation
        binding.etEmail.addTextChangedListener { 
            binding.tilEmail.error = null 
        }
        binding.etPassword.addTextChangedListener { 
            binding.tilPassword.error = null 
        }

        updateUI()
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        binding.progressBar.visibility = android.view.View.VISIBLE

        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                binding.progressBar.visibility = android.view.View.GONE

                if (task.isSuccessful) {
                    // Check if this is a new user
                    val isNewUser = task.result?.additionalUserInfo?.isNewUser ?: false
                    
                    if (isNewUser) {
                        // Navigate to role selection
                        val intent = Intent(this, RoleSelectionActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } else {
                        checkUserProfile()
                    }
                } else {
                    Toast.makeText(
                        this,
                        "Authentication failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
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
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.btnPrimary.isEnabled = false

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                binding.progressBar.visibility = android.view.View.GONE
                binding.btnPrimary.isEnabled = true

                if (task.isSuccessful) {
                    checkUserProfile()
                } else {
                    Toast.makeText(
                        this,
                        "Login failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun signupUser(email: String, password: String) {
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.btnPrimary.isEnabled = false

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                binding.progressBar.visibility = android.view.View.GONE
                binding.btnPrimary.isEnabled = true

                if (task.isSuccessful) {
                    // Navigate to role selection
                    val intent = Intent(this, RoleSelectionActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(
                        this,
                        "Signup failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun checkUserProfile() {
        val userId = auth.currentUser?.uid ?: return

        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val profileComplete = document.getBoolean("profileComplete") ?: false
                    val userType = document.getString("userType") ?: "PATIENT"

                    if (profileComplete) {
                        // Navigate to main app
                        val intent = Intent(this, com.example.hospitalmanagement.MainActivity::class.java)
                        intent.putExtra("USER_ID", userId)
                        intent.putExtra("USER_ROLE", userType)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } else {
                        // Navigate to profile setup
                        val intent = Intent(this, ProfileSetupActivity::class.java)
                        intent.putExtra("USER_TYPE", userType)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                } else {
                    // No user document, navigate to role selection
                    val intent = Intent(this, RoleSelectionActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error checking profile", Toast.LENGTH_SHORT).show()
            }
    }
}