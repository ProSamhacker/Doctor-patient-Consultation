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
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.hospitalmanagement.AppDatabase
import com.example.hospitalmanagement.Doctor
import com.example.hospitalmanagement.DoctorDashboardActivity
import com.example.hospitalmanagement.Patient
import com.example.hospitalmanagement.PatientDashboardActivity
import com.example.hospitalmanagement.databinding.ActivityProfileSetupBinding
import com.example.hospitalmanagement.models.UserType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

class ProfileSetupActivity : AppCompatActivity() {

    private val CLOUD_NAME = "df69wb6vh"
    private val UPLOAD_PRESET = "unsigned_preset"

    private lateinit var binding: ActivityProfileSetupBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var userType: UserType
    private lateinit var database: AppDatabase

    private var selectedImageUri: Uri? = null
    private lateinit var imagePickerLauncher: ActivityResultLauncher<String>
    private lateinit var cropImageLauncher: ActivityResultLauncher<Intent>
    private var isEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDatabase.getDatabase(this)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        val userTypeString = intent.getStringExtra("USER_TYPE") ?: "PATIENT"
        userType = try {
            UserType.valueOf(userTypeString)
        } catch (e: Exception) {
            UserType.PATIENT
        }

        isEditMode = intent.getBooleanExtra("IS_EDIT_MODE", false)

        setupImagePickers()
        setupUI()

        if (isEditMode) {
            binding.tvTitle.text = "Edit Profile"
            binding.btnSave.text = "Update Profile"
            loadExistingData()
        }
    }

    private fun initCloudinary() {
        try {
            MediaManager.get()
        } catch (e: Exception) {
            val config = HashMap<String, String>()
            config["cloud_name"] = CLOUD_NAME
            MediaManager.init(this, config)
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

                    if (userType == UserType.DOCTOR) {
                        binding.etSpecialization.setText(document.getString("specialization"))
                        binding.etHospital.setText(document.getString("hospitalName"))
                        binding.etExperience.setText(document.getLong("experience")?.toString())
                        binding.etLicense.setText(document.getString("licenseNumber"))
                        binding.etFee.setText(document.getDouble("consultationFee")?.toString())
                        binding.etBio.setText(document.getString("bio"))

                        val quals = (document.get("qualifications") as? List<*>)?.filterIsInstance<String>()
                        binding.etQualifications.setText(quals?.joinToString(", "))
                    } else {
                        binding.etAge.setText(document.getLong("age")?.toString())
                        binding.etAddress.setText(document.getString("address"))
                        binding.etBloodGroup.setText(document.getString("bloodGroup"))

                        val genderStr = document.getString("gender")
                        if (genderStr == "Female") binding.rbFemale.isChecked = true
                        else binding.rbMale.isChecked = true

                        // Safe Cast for List<String>
                        val allergies = (document.get("allergies") as? List<*>)?.filterIsInstance<String>()
                        binding.etAllergies.setText(allergies?.joinToString(", "))

                        val conditions = (document.get("chronicConditions") as? List<*>)?.filterIsInstance<String>()
                        binding.etConditions.setText(conditions?.joinToString(", "))
                    }
                }
            }
            .addOnFailureListener { binding.progressBar.visibility = View.GONE }
    }

    private fun setupImagePickers() {
        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { startCrop(it) }
        }

        cropImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val resultUri = UCrop.getOutput(result.data!!)
                resultUri?.let {
                    selectedImageUri = it
                    displaySelectedImage(it)
                }
            }
        }
    }

    private fun startCrop(sourceUri: Uri) {
        val destinationFileName = "cropped_profile_${System.currentTimeMillis()}.jpg"
        val destinationUri = Uri.fromFile(File(cacheDir, destinationFileName))
        val uCrop = UCrop.of(sourceUri, destinationUri)
        uCrop.withAspectRatio(1f, 1f)
        uCrop.withMaxResultSize(1080, 1080)
        uCrop.withOptions(UCrop.Options().apply {
            setCompressionQuality(90)
            setToolbarColor(Color.parseColor("#121212"))
            setStatusBarColor(Color.parseColor("#121212"))
            setToolbarWidgetColor(Color.WHITE)
            setActiveControlsWidgetColor(Color.parseColor("#4A90E2"))
        })
        cropImageLauncher.launch(uCrop.getIntent(this))
    }

    private fun displaySelectedImage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            binding.ivProfileImage.setImageBitmap(bitmap)
            binding.ivProfileImage.setPadding(0, 0, 0, 0)
            inputStream?.close()
        } catch (e: Exception) {
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
            binding.rbMale.isChecked = true
        }

        binding.layoutSelectImage.setOnClickListener { imagePickerLauncher.launch("image/*") }

        binding.btnSave.setOnClickListener {
            if (validateInputs()) {
                uploadImageAndSave()
            }
        }
    }

    private fun validateInputs(): Boolean {
        binding.tilName.error = null
        binding.tilPhone.error = null
        if (binding.etName.text.toString().isBlank()) {
            binding.tilName.error = "Name is required"
            return false
        }
        if (binding.etPhone.text.toString().trim().length != 10) {
            binding.tilPhone.error = "Phone number must be exactly 10 digits"
            return false
        }
        return true
    }

    private fun uploadImageAndSave() {
        val userId = auth.currentUser?.uid ?: return
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSave.isEnabled = false

        if (selectedImageUri == null) {
            saveProfileData(userId, "")
            return
        }

        MediaManager.get().upload(selectedImageUri)
            .unsigned(UPLOAD_PRESET)
            .option("folder", "hospital_profiles")
            .option("resource_type", "image")
            .callback(object : UploadCallback {
                override fun onStart(requestId: String) {}
                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                    val imageUrl = resultData["secure_url"] as? String ?: ""
                    runOnUiThread { saveProfileData(userId, imageUrl) }
                }
                override fun onError(requestId: String, error: ErrorInfo) {
                    runOnUiThread {
                        binding.progressBar.visibility = View.GONE
                        binding.btnSave.isEnabled = true
                        Toast.makeText(this@ProfileSetupActivity, "Upload failed: ${error.description}", Toast.LENGTH_LONG).show()
                    }
                }
                override fun onReschedule(requestId: String, error: ErrorInfo) {}
            })
            .dispatch()
    }

    private fun saveProfileData(userId: String, imageUrl: String) {
        val email = auth.currentUser?.email ?: ""
        if (userType == UserType.DOCTOR) {
            saveDoctorProfile(userId, imageUrl, email)
        } else {
            savePatientProfile(userId, imageUrl, email)
        }
    }

    private fun saveDoctorProfile(userId: String, imageUrl: String, email: String) {
        val name = binding.etName.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val spec = binding.etSpecialization.text.toString().trim()
        val hospital = binding.etHospital.text.toString().trim()
        val exp = binding.etExperience.text.toString().toIntOrNull() ?: 0
        val fee = binding.etFee.text.toString().toDoubleOrNull() ?: 0.0
        val qualificationsList = binding.etQualifications.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }

        val updates = hashMapOf<String, Any>(
            "uid" to userId, "name" to name, "email" to email, "phone" to phone,
            "specialization" to spec, "hospitalName" to hospital,
            "experience" to exp, "qualifications" to qualificationsList,
            "licenseNumber" to binding.etLicense.text.toString().trim(),
            "consultationFee" to fee, "bio" to binding.etBio.text.toString().trim(),
            "isActive" to true, "updatedAt" to Date()
        )
        if (imageUrl.isNotEmpty()) updates["profileImageUrl"] = imageUrl
        if (!isEditMode) updates["createdAt"] = Date()

        firestore.collection("doctors").document(userId).set(updates, SetOptions.merge())
            .addOnSuccessListener {
                CoroutineScope(Dispatchers.IO).launch {
                    val doctor = Doctor(
                        doctorId = userId,
                        name = name,
                        specialization = spec,
                        phone = phone,
                        email = email,
                        hospitalName = hospital,
                        profileImageUrl = imageUrl,
                        experienceYears = exp,
                        consultationFee = fee,
                        rating = 0f,
                        isActive = true
                    )
                    database.doctorDao().insert(doctor)
                    withContext(Dispatchers.Main) { updateUserProfileStatus(userId) }
                }
            }
            .addOnFailureListener { e -> handleError(e) }
    }

    private fun savePatientProfile(userId: String, imageUrl: String, email: String) {
        val name = binding.etName.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val age = binding.etAge.text.toString().toIntOrNull() ?: 0
        val gender = if (binding.rgGender.checkedRadioButtonId != -1) {
            findViewById<RadioButton>(binding.rgGender.checkedRadioButtonId)?.text?.toString() ?: "Male"
        } else "Male"
        val blood = binding.etBloodGroup.text.toString().trim()
        val address = binding.etAddress.text.toString().trim()
        val allergies = binding.etAllergies.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val conditions = binding.etConditions.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }

        val updates = hashMapOf<String, Any>(
            "uid" to userId, "name" to name, "email" to email, "phone" to phone,
            "age" to age, "gender" to gender, "bloodGroup" to blood, "address" to address,
            "allergies" to allergies, "chronicConditions" to conditions, "updatedAt" to Date()
        )
        if (imageUrl.isNotEmpty()) updates["profileImageUrl"] = imageUrl
        if (!isEditMode) updates["createdAt"] = Date()

        firestore.collection("patients").document(userId).set(updates, SetOptions.merge())
            .addOnSuccessListener {
                CoroutineScope(Dispatchers.IO).launch {
                    val patient = Patient(
                        patientId = userId,
                        name = name,
                        age = age,
                        gender = gender,
                        phone = phone,
                        email = email,
                        bloodGroup = blood,
                        address = address,
                        emergencyContact = "",
                        allergies = allergies,
                        chronicConditions = conditions,
                        profileImageUrl = imageUrl
                    )
                    database.patientDao().insert(patient)
                    withContext(Dispatchers.Main) { updateUserProfileStatus(userId) }
                }
            }
            .addOnFailureListener { e -> handleError(e) }
    }

    private fun updateUserProfileStatus(userId: String) {
        firestore.collection("users").document(userId).update("profileComplete", true)
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Profile Saved Successfully!", Toast.LENGTH_SHORT).show()
                val targetActivity = if (userType == UserType.DOCTOR) DoctorDashboardActivity::class.java else PatientDashboardActivity::class.java
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
        runOnUiThread {
            binding.progressBar.visibility = View.GONE
            binding.btnSave.isEnabled = true
            Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}