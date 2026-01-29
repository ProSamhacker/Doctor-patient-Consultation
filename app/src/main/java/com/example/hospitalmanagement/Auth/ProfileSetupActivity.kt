package com.example.hospitalmanagement.auth

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.hospitalmanagement.DoctorDashboardActivity
import com.example.hospitalmanagement.PatientDashboardActivity
import com.example.hospitalmanagement.R
import com.example.hospitalmanagement.databinding.ActivityProfileSetupBinding
import com.example.hospitalmanagement.models.DoctorProfile
import com.example.hospitalmanagement.models.PatientProfile
import com.example.hospitalmanagement.models.UserType
import com.example.hospitalmanagement.storage.VercelBlobStorage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.yalantis.ucrop.UCrop // Import uCrop
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date

class ProfileSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileSetupBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: VercelBlobStorage
    private lateinit var userType: UserType

    private var selectedImageUri: Uri? = null

    // Launcher 1: Picks raw image from gallery
    private lateinit var imagePickerLauncher: ActivityResultLauncher<String>
    // Launcher 2: Handles the result from uCrop activity
    private lateinit var cropImageLauncher: ActivityResultLauncher<Intent>

    private var isEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = VercelBlobStorage(this)

        val userTypeString = intent.getStringExtra("USER_TYPE") ?: "PATIENT"
        userType = try {
            UserType.valueOf(userTypeString)
        } catch (e: Exception) {
            UserType.PATIENT
        }

        isEditMode = intent.getBooleanExtra("IS_EDIT_MODE", false)

        setupImagePickers() //Renamed
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

        val collection = if (userType == UserType.DOCTOR) "doctors" else "patients"

        firestore.collection(collection).document(userId).get()
            .addOnSuccessListener { document ->
                binding.progressBar.visibility = View.GONE
                if (document.exists()) {
                    binding.etName.setText(document.getString("name"))
                    binding.etPhone.setText(document.getString("phone"))

                    // TODO: Load existing Profile Image URL into ivProfileImage using Glide or Coil here

                    if (userType == UserType.DOCTOR) {
                        binding.etSpecialization.setText(document.getString("specialization"))
                        binding.etHospital.setText(document.getString("hospitalName"))
                        binding.etExperience.setText(document.getLong("experience")?.toString())
                        binding.etLicense.setText(document.getString("licenseNumber"))
                        binding.etFee.setText(document.getDouble("consultationFee")?.toString())
                        binding.etBio.setText(document.getString("bio"))

                        val quals = document.get("qualifications") as? List<String>
                        binding.etQualifications.setText(quals?.joinToString(", "))
                    } else {
                        binding.etAge.setText(document.getLong("age")?.toString())
                        binding.etAddress.setText(document.getString("address"))
                        binding.etBloodGroup.setText(document.getString("bloodGroup"))

                        // Load Gender
                        val genderStr = document.getString("gender")
                        if (genderStr == "Female") binding.rbFemale.isChecked = true else binding.rbMale.isChecked = true

                        val allergies = document.get("allergies") as? List<String>
                        binding.etAllergies.setText(allergies?.joinToString(", "))

                        val conditions = document.get("chronicConditions") as? List<String>
                        binding.etConditions.setText(conditions?.joinToString(", "))
                    }
                }
            }
            .addOnFailureListener {
                binding.progressBar.visibility = View.GONE
            }
    }

    private fun setupImagePickers() {
        // Step 1: Pick raw image
        imagePickerLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                // Once picked, immediately start cropping
                startCrop(it)
            }
        }

        // Step 2: Handle cropped result
        cropImageLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val resultUri = UCrop.getOutput(result.data!!)
                resultUri?.let {
                    selectedImageUri = it
                    displaySelectedImage(it)
                }
            } else if (result.resultCode == UCrop.RESULT_ERROR) {
                val cropError = UCrop.getError(result.data!!)
                Toast.makeText(this, "Crop error: ${cropError?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Function to configure and start uCrop
    private fun startCrop(sourceUri: Uri) {
        val destinationFileName = "cropped_profile_${System.currentTimeMillis()}.jpg"
        val destinationUri = Uri.fromFile(File(cacheDir, destinationFileName))

        val uCrop = UCrop.of(sourceUri, destinationUri)

        // Force 1:1 Square Aspect Ratio
        uCrop.withAspectRatio(1f, 1f)

        // Set max size for profile image to save bandwidth
        uCrop.withMaxResultSize(1080, 1080)

        // Style the Cropping Activity to match Dark Theme
        val options = UCrop.Options()
        options.setCompressionQuality(90)
        options.setToolbarColor(Color.parseColor("#121212")) // Match background
        options.setStatusBarColor(Color.parseColor("#121212"))
        options.setToolbarWidgetColor(Color.WHITE)
        options.setActiveControlsWidgetColor(Color.parseColor("#4A90E2")) // Accent color

        uCrop.withOptions(options)

        cropImageLauncher.launch(uCrop.getIntent(this))
    }


    private fun displaySelectedImage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            binding.ivProfileImage.setImageBitmap(bitmap)
            // Remove padding and tint so the cropped image fills the circle
            binding.ivProfileImage.setPadding(0, 0, 0, 0)
            binding.ivProfileImage.imageTintList = null
            inputStream?.close()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load image preview", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
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
            // Default gender selection
            binding.rbMale.isChecked = true
        }

        binding.layoutSelectImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        binding.btnSave.setOnClickListener {
            if (validateInputs()) {
                saveProfileWithImage()
            }
        }
    }

    // --- CHANGED: Phone Number Validation ---
    private fun validateInputs(): Boolean {
        binding.tilName.error = null
        binding.tilPhone.error = null

        if (binding.etName.text.toString().isBlank()) {
            binding.tilName.error = "Name is required"
            return false
        }

        val phoneInput = binding.etPhone.text.toString().trim()
        if (phoneInput.isBlank()) {
            binding.tilPhone.error = "Phone is required"
            return false
        }

        if (phoneInput.length != 10) {
            binding.tilPhone.error = "Phone number must be exactly 10 digits"
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

                if (selectedImageUri != null) {
                    val uploadResult = storage.uploadProfileImage(selectedImageUri!!, userId)
                    if (uploadResult.isSuccess) {
                        profileImageUrl = uploadResult.getOrNull() ?: ""
                    } else {
                        val error = uploadResult.exceptionOrNull()
                        Toast.makeText(this@ProfileSetupActivity, "Image upload warning: ${error?.message}", Toast.LENGTH_LONG).show()
                    }
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

        val updates = hashMapOf<String, Any>(
            "uid" to userId,
            "name" to binding.etName.text.toString().trim(),
            "email" to email,
            "phone" to binding.etPhone.text.toString().trim(),
            "specialization" to binding.etSpecialization.text.toString().trim(),
            "hospitalName" to binding.etHospital.text.toString().trim(),
            "experience" to (binding.etExperience.text.toString().toIntOrNull() ?: 0),
            "qualifications" to qualificationsList,
            "licenseNumber" to binding.etLicense.text.toString().trim(),
            "consultationFee" to (binding.etFee.text.toString().toDoubleOrNull() ?: 0.0),
            "bio" to binding.etBio.text.toString().trim(),
            "isActive" to true,
            "updatedAt" to Date()
        )

        if (imageUrl.isNotEmpty()) {
            updates["profileImageUrl"] = imageUrl
        }

        if (!isEditMode) {
            updates["createdAt"] = Date()
        }

        firestore.collection("doctors").document(userId)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener { updateUserProfileStatus(userId) }
            .addOnFailureListener { e -> handleError(e) }
    }

    private fun savePatientProfile(userId: String, imageUrl: String) {
        val email = auth.currentUser?.email ?: ""

        // --- CHANGED: Updated Gender Selection Logic (Safely handle missing Other) ---
        val selectedGenderId = binding.rgGender.checkedRadioButtonId
        val gender = if (selectedGenderId != -1) {
            findViewById<RadioButton>(selectedGenderId)?.text?.toString() ?: "Male"
        } else {
            // Default fallback if somehow nothing is checked (though we set Male default in setupUI)
            "Male"
        }

        val allergiesList = binding.etAllergies.text.toString()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val conditionsList = binding.etConditions.text.toString()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val updates = hashMapOf<String, Any>(
            "uid" to userId,
            "name" to binding.etName.text.toString().trim(),
            "email" to email,
            "phone" to binding.etPhone.text.toString().trim(),
            "age" to (binding.etAge.text.toString().toIntOrNull() ?: 0),
            "gender" to gender,
            "bloodGroup" to binding.etBloodGroup.text.toString().trim(),
            "address" to binding.etAddress.text.toString().trim(),
            "allergies" to allergiesList,
            "chronicConditions" to conditionsList,
            "updatedAt" to Date()
        )

        if (imageUrl.isNotEmpty()) {
            updates["profileImageUrl"] = imageUrl
        }

        if (!isEditMode) {
            updates["createdAt"] = Date()
        }

        firestore.collection("patients").document(userId)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener { updateUserProfileStatus(userId) }
            .addOnFailureListener { e -> handleError(e) }
    }

    private fun updateUserProfileStatus(userId: String) {
        firestore.collection("users").document(userId)
            .update("profileComplete", true)
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Profile Saved Successfully!", Toast.LENGTH_SHORT).show()

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