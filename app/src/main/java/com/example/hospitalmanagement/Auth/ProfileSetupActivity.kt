package com.example.hospitalmanagement.auth

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.hospitalmanagement.databinding.ActivityProfileSetupBinding
import com.example.hospitalmanagement.models.DoctorProfile
import com.example.hospitalmanagement.models.PatientProfile
import com.example.hospitalmanagement.models.UserType
import com.example.hospitalmanagement.storage.VercelBlobStorage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class ProfileSetupActivityWithUpload : AppCompatActivity() {
    
    private lateinit var binding: ActivityProfileSetupBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: VercelBlobStorage
    private lateinit var userType: UserType
    
    private var selectedImageUri: Uri? = null
    private lateinit var imagePickerLauncher: ActivityResultLauncher<String>
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = VercelBlobStorage(this)

        val userTypeString = intent.getStringExtra("USER_TYPE") ?: "PATIENT"
        userType = UserType.valueOf(userTypeString)

        setupImagePicker()
        setupUI()
    }

    private fun setupImagePicker() {
        // Register image picker
        imagePickerLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                selectedImageUri = it
                binding.ivProfileImage.setImageURI(it)
                binding.tvSelectImage.text = "Change Photo"
            }
        }

        // Register permission launcher
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                openImagePicker()
            } else {
                Toast.makeText(
                    this,
                    "Permission denied. Cannot select image.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setupUI() {
        // Profile image selection
        binding.layoutSelectImage.setOnClickListener {
            checkAndRequestPermissions()
        }

        // Rest of your existing setup code...
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
        // Your existing validation code...
        return true
    }

    private fun saveProfileWithImage() {
        val userId = auth.currentUser?.uid ?: return
        
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.btnSave.isEnabled = false

        lifecycleScope.launch {
            try {
                // Upload profile image if selected
                var profileImageUrl = ""
                selectedImageUri?.let { uri ->
                    val uploadResult = storage.uploadProfileImage(uri, userId)
                    
                    uploadResult.onSuccess { url ->
                        profileImageUrl = url
                    }
                    
                    uploadResult.onFailure { error ->
                        Toast.makeText(
                            this@ProfileSetupActivityWithUpload,
                            "Image upload failed: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                // Save profile with image URL
                if (userType == UserType.DOCTOR) {
                    saveDoctorProfile(userId, profileImageUrl)
                } else {
                    savePatientProfile(userId, profileImageUrl)
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = android.view.View.GONE
                binding.btnSave.isEnabled = true
                Toast.makeText(
                    this@ProfileSetupActivityWithUpload,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun saveDoctorProfile(userId: String, imageUrl: String) {
        val email = auth.currentUser?.email ?: ""
        
        val profile = DoctorProfile(
            uid = userId,
            name = binding.etName.text.toString().trim(),
            specialization = binding.etSpecialization.text.toString().trim(),
            hospitalName = binding.etHospital.text.toString().trim(),
            phone = binding.etPhone.text.toString().trim(),
            email = email,
            profileImageUrl = imageUrl,
            // ... other fields
        )

        firestore.collection("doctors").document(userId)
            .set(profile)
            .addOnSuccessListener {
                updateUserProfileStatus(userId)
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = android.view.View.GONE
                binding.btnSave.isEnabled = true
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun savePatientProfile(userId: String, imageUrl: String) {
        val email = auth.currentUser?.email ?: ""
        
        val profile = PatientProfile(
            uid = userId,
            name = binding.etName.text.toString().trim(),
            age = binding.etAge.text.toString().trim().toIntOrNull() ?: 0,
            phone = binding.etPhone.text.toString().trim(),
            email = email,
            profileImageUrl = imageUrl,
            // ... other fields
        )

        firestore.collection("patients").document(userId)
            .set(profile)
            .addOnSuccessListener {
                updateUserProfileStatus(userId)
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = android.view.View.GONE
                binding.btnSave.isEnabled = true
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateUserProfileStatus(userId: String) {
        firestore.collection("users").document(userId)
            .update("profileComplete", true)
            .addOnSuccessListener {
                // Navigate to main app
                // ... your existing navigation code
            }
    }
}

// ===== File Picker for Medical Reports =====

class AppointmentRequestActivity : AppCompatActivity() {
    
    private lateinit var storage: VercelBlobStorage
    private val selectedFiles = mutableListOf<Uri>()
    private lateinit var filePickerLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storage = VercelBlobStorage(this)

        setupFilePicker()
    }

    private fun setupFilePicker() {
        // Register file picker for multiple files
        filePickerLauncher = registerForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris: List<Uri> ->
            selectedFiles.clear()
            selectedFiles.addAll(uris)
            updateFileList()
        }
    }

    private fun openFilePicker() {
        // Allow PDF, images
        filePickerLauncher.launch(arrayOf("application/pdf", "image/*"))
    }

    private fun updateFileList() {
        // Update UI to show selected files
        binding.tvSelectedFiles.text = "${selectedFiles.size} files selected"
    }

    private fun uploadAndSubmitRequest() {
        lifecycleScope.launch {
            binding.progressBar.visibility = android.view.View.VISIBLE
            
            val uploadedUrls = mutableListOf<String>()
            
            for (fileUri in selectedFiles) {
                val fileName = getFileName(fileUri)
                val result = storage.uploadMedicalReport(
                    fileUri,
                    patientId,
                    fileName
                )
                
                result.onSuccess { url ->
                    uploadedUrls.add(url)
                }
                
                result.onFailure { error ->
                    Toast.makeText(
                        this@AppointmentRequestActivity,
                        "Upload failed: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            
            // Submit appointment request with uploaded files
            submitRequest(uploadedUrls)
        }
    }

    private fun getFileName(uri: Uri): String {
        var fileName = "file"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(
                android.provider.OpenableColumns.DISPLAY_NAME
            )
            if (cursor.moveToFirst()) {
                fileName = cursor.getString(nameIndex)
            }
        }
        return fileName
    }

    private fun submitRequest(fileUrls: List<String>) {
        // Save to Firestore with file URLs
        // ... your Firestore code
    }
}