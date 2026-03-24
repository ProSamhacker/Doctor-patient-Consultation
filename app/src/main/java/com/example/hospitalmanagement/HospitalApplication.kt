package com.example.hospitalmanagement

import android.app.Application
import com.cloudinary.android.MediaManager
import com.facebook.drawee.backends.pipeline.Fresco

class HospitalApplication : Application() {

    // 1. Initialize Database
    val database by lazy { AppDatabase.getDatabase(this) }

    // 2. Initialize Repository (FIXED: Passing individual DAOs)
    val repository by lazy {
        HospitalRepository(
            doctorDao = database.doctorDao(),
            patientDao = database.patientDao(),
            appointmentDao = database.appointmentDao(),
            prescriptionDao = database.prescriptionDao(),
            messageDao = database.messageDao(),
            consultationSessionDao = database.consultationSessionDao(),
            aiExtractionDao = database.aiExtractionDao(),
            medicalReportDao = database.medicalReportDao(),
            vitalSignsDao = database.vitalSignsDao(),
            notificationDao = database.notificationDao(),
            emergencyContactDao = database.emergencyContactDao(),
            medicationDao = database.medicationDao()
        )
    }

    override fun onCreate() {
        super.onCreate()

        // 3. Initialize Fresco
        Fresco.initialize(this)

        // 4. Initialize Cloudinary
        initCloudinary()
    }

    private fun initCloudinary() {
        try {
            val config = HashMap<String, String>()
            config["cloud_name"] = BuildConfig.CLOUDINARY_CLOUD_NAME
            config["upload_preset"] = BuildConfig.CLOUDINARY_UPLOAD_PRESET
            MediaManager.init(this, config)
        } catch (e: Exception) {
            // Already initialized or config error
        }
    }
}