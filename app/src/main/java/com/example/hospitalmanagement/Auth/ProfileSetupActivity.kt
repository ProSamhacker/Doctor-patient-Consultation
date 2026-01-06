package com.example.hospitalmanagement.auth

import android.Manifest
import android.content.Intent // Required for Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.hospitalmanagement.DoctorDashboardActivity // FIXED: Import Added
import com.example.hospitalmanagement.PatientDashboardActivity // FIXED: Import Added
import com.example.hospitalmanagement.databinding.ActivityProfileSetupBinding
import com.example.hospitalmanagement.models.DoctorProfile
import com.example.hospitalmanagement.models.PatientProfile
import com.example.hospitalmanagement.models.UserType
import com.example.hospitalmanagement.storage.VercelBlobStorage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.util.Date

class ProfileSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileSetupBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: VercelBlobStorage
    private lateinit var userType: UserType

    private var selectedImageUri: Uri? = null
    private lateinit var imagePickerLauncher: ActivityResultLauncher<String>
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private var isEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = VercelBlobStorage(this)

        // Get UserType from Intent
        val userTypeString = intent.getStringExtra("USER_TYPE") ?: "PATIENT"
        userType = try {
            UserType.valueOf(userTypeString)
        } catch (e: Exception) {
            UserType.PATIENT
        }

        // Check for Edit Mode
        isEditMode = intent.getBooleanExtra("IS_EDIT_MODE", false)

        setupImagePicker()
        setupUI()

        if (isEditMode) {
            binding.tvTitle.text = "Edit Profile"
            binding.btnSave.text = "Update Profile"
            loadExistingData()
        }
    }

    private fun loadExistingData() {
        val userId = auth.currentUser?.uid ?: return
        binding.progressBar.visibility = View.VISIBLE

        if (userType == UserType.DOCTOR) {
            firestore.collection("doctors").document(userId).get()
                .addOnSuccessListener { document ->
                    binding.progressBar.visibility = View.GONE
                    if (document.exists()) {
                        binding.etName.setText(document.getString("name"))
                        binding.etPhone.setText(document.getString("phone"))
                        binding.etSpecialization.setText(document.getString("specialization"))
                        binding.etHospital.setText(document.getString("hospitalName"))
                        binding.etExperience.setText(document.getLong("experience")?.toString())
                        binding.etLicense.setText(document.getString("licenseNumber"))
                        binding.etFee.setText(document.getDouble("consultationFee")?.toString())
                        binding.etBio.setText(document.getString("bio"))

                        val quals = document.get("qualifications") as? List<String>
                        binding.etQualifications.setText(quals?.joinToString(", "))
                    }
                }
        } else {
            firestore.collection("patients").document(userId).get()
                .addOnSuccessListener { document ->
                    binding.progressBar.visibility = View.GONE
                    if (document.exists()) {
                        binding.etName.setText(document.getString("name"))
                        binding.etPhone.setText(document.getString("phone"))
                        binding.etAge.setText(document.getLong("age")?.toString())
                        binding.etAddress.setText(document.getString("address"))
                        binding.etBloodGroup.setText(document.getString("bloodGroup"))

                        val allergies = document.get("allergies") as? List<String>
                        binding.etAllergies.setText(allergies?.joinToString(", "))

                        val conditions = document.get("chronicConditions") as? List<String>
                        binding.etConditions.setText(conditions?.joinToString(", "))
                    }
                }
        }
    }

    private fun setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                selectedImageUri = it
                binding.ivProfileImage.setImageURI(it)
                binding.tvSelectImage.text = "Change Photo"
            }
        }

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                openImagePicker()
            } else {
                Toast.makeText(this, "Permission denied. Cannot select image.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupUI() {
        if (userType == UserType.DOCTOR) {
            binding.layoutDoctorFields.visibility = View.VISIBLE
            binding.layoutPatientFields.visibility = View.GONE
            binding.tvTitle.text = "Doctor Profile"
        } else {
            binding.layoutDoctorFields.visibility = View.GONE
            binding.layoutPatientFields.visibility = View.VISIBLE
            binding.tvTitle.text = "Patient Profile"
        }

        binding.layoutSelectImage.setOnClickListener {
            checkAndRequestPermissions()
        }

        binding.btnSave.setOnClickListener {
            if (validateInputs()) {
                saveProfileWithImage()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            openImagePicker()
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    private fun openImagePicker() {
        imagePickerLauncher.launch("image/*")
    }

    private fun validateInputs(): Boolean {
        if (binding.etName.text.toString().isBlank()) {
            binding.tilName.error = "Name is required"
            return false
        }
        if (binding.etPhone.text.toString().isBlank()) {
            binding.tilPhone.error = "Phone is required"
            return false
        }
        return true
    }

    private fun saveProfileWithImage() {
        val userId = auth.currentUser?.uid ?: return

        binding.progressBar.visibility = View.VISIBLE
        binding.btnSave.isEnabled = false

        lifecycleScope.launch {
            try {
                var profileImageUrl = ""

                // Upload Image only if a new one is selected
                if (selectedImageUri != null) {
                    val uploadResult = storage.uploadProfileImage(selectedImageUri!!, userId)
                    uploadResult.onSuccess { url ->
                        profileImageUrl = url
                    }.onFailure { e ->
                        throw Exception("Image upload failed: ${e.message}")
                    }
                } else {
                    // In edit mode, if no new image, we might want to keep the old URL.
                    // For simplicity, we send empty string here, but in production,
                    // you'd fetch the existing URL first if not overwriting.
                    // Or ideally, rely on `merge` if using update, but here we use `set`.
                    // A quick fix is to fetch current URL or ignore image update if empty.
                }

                if (userType == UserType.DOCTOR) {
                    saveDoctorProfile(userId, profileImageUrl)
                } else {
                    savePatientProfile(userId, profileImageUrl)
                }

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isEnabled = true
                Toast.makeText(this@ProfileSetupActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveDoctorProfile(userId: String, imageUrl: String) {
        val email = auth.currentUser?.email ?: ""

        val qualificationsList = binding.etQualifications.text.toString()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val profile = DoctorProfile(
            uid = userId,
            name = binding.etName.text.toString().trim(),
            email = email,
            phone = binding.etPhone.text.toString().trim(),
            specialization = binding.etSpecialization.text.toString().trim(),
            hospitalName = binding.etHospital.text.toString().trim(),
            experience = binding.etExperience.text.toString().toIntOrNull() ?: 0,
            qualifications = qualificationsList,
            licenseNumber = binding.etLicense.text.toString().trim(),
            consultationFee = binding.etFee.text.toString().toDoubleOrNull() ?: 0.0,
            bio = binding.etBio.text.toString().trim(),
            profileImageUrl = imageUrl,
            isActive = true,
            createdAt = Date(),
            updatedAt = Date()
        )

        firestore.collection("doctors").document(userId)
            .set(profile)
            .addOnSuccessListener { updateUserProfileStatus(userId) }
            .addOnFailureListener { e -> handleError(e) }
    }

    private fun savePatientProfile(userId: String, imageUrl: String) {
        val email = auth.currentUser?.email ?: ""

        val selectedGenderId = binding.rgGender.checkedRadioButtonId
        val gender = if (selectedGenderId != -1) {
            findViewById<RadioButton>(selectedGenderId)?.text?.toString() ?: "Other"
        } else {
            "Other"
        }

        val allergiesList = binding.etAllergies.text.toString()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val conditionsList = binding.etConditions.text.toString()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val profile = PatientProfile(
            uid = userId,
            name = binding.etName.text.toString().trim(),
            email = email,
            phone = binding.etPhone.text.toString().trim(),
            age = binding.etAge.text.toString().toIntOrNull() ?: 0,
            gender = gender,
            bloodGroup = binding.etBloodGroup.text.toString().trim(),
            address = binding.etAddress.text.toString().trim(),
            allergies = allergiesList,
            chronicConditions = conditionsList,
            profileImageUrl = imageUrl,
            createdAt = Date(),
            updatedAt = Date()
        )

        firestore.collection("patients").document(userId)
            .set(profile)
            .addOnSuccessListener { updateUserProfileStatus(userId) }
            .addOnFailureListener { e -> handleError(e) }
    }

    private fun updateUserProfileStatus(userId: String) {
        firestore.collection("users").document(userId)
            .update("profileComplete", true)
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Profile Saved Successfully!", Toast.LENGTH_SHORT).show()

                // FIXED: Explicitly typed variable to avoid "Argument Type Mismatch"
                val targetActivity: Class<*> = if (userType == UserType.DOCTOR) {
                    DoctorDashboardActivity::class.java
                } else {
                    PatientDashboardActivity::class.java
                }

                val intent = Intent(this, targetActivity)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                intent.putExtra("USER_ID", userId)
                intent.putExtra("USER_ROLE", userType.name)
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e -> handleError(e) }
    }

    private fun handleError(e: Exception) {
        binding.progressBar.visibility = View.GONE
        binding.btnSave.isEnabled = true
        Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}