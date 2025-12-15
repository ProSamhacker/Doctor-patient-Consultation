package com.example.hospitalmanagement.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.hospitalmanagement.databinding.ActivityRoleSelectionNewBinding
import com.example.hospitalmanagement.models.User
import com.example.hospitalmanagement.models.UserType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RoleSelectionActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityRoleSelectionNewBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var selectedRole: UserType? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoleSelectionNewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        setupUI()
    }

    private fun setupUI() {
        // Doctor Card Click
        binding.cardDoctor.setOnClickListener {
            selectRole(UserType.DOCTOR)
        }

        // Patient Card Click
        binding.cardPatient.setOnClickListener {
            selectRole(UserType.PATIENT)
        }

        // Continue Button
        binding.btnContinue.setOnClickListener {
            if (selectedRole != null) {
                saveUserRole(selectedRole!!)
            } else {
                Toast.makeText(this, "Please select a role", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun selectRole(role: UserType) {
        selectedRole = role

        // Update UI to show selection
        if (role == UserType.DOCTOR) {
            binding.cardDoctor.strokeWidth = 8
            binding.cardPatient.strokeWidth = 0
            binding.ivCheckDoctor.visibility = android.view.View.VISIBLE
            binding.ivCheckPatient.visibility = android.view.View.GONE
        } else {
            binding.cardPatient.strokeWidth = 8
            binding.cardDoctor.strokeWidth = 0
            binding.ivCheckPatient.visibility = android.view.View.VISIBLE
            binding.ivCheckDoctor.visibility = android.view.View.GONE
        }

        binding.btnContinue.isEnabled = true
    }

    private fun saveUserRole(role: UserType) {
        val userId = auth.currentUser?.uid ?: return
        val email = auth.currentUser?.email ?: ""

        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.btnContinue.isEnabled = false

        val user = User(
            uid = userId,
            email = email,
            userType = role,
            profileComplete = false
        )

        firestore.collection("users").document(userId)
            .set(user)
            .addOnSuccessListener {
                // Navigate to profile setup
                val intent = Intent(this, ProfileSetupActivity::class.java)
                intent.putExtra("USER_TYPE", role.name)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = android.view.View.GONE
                binding.btnContinue.isEnabled = true
                Toast.makeText(
                    this,
                    "Failed to save role: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}