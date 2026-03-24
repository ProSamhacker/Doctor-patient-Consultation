package com.example.hospitalmanagement.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

class CloudinaryStorage(context: Context) {

    // TODO: Configure these in your local.properties or directly here
    private val cloudName = "YOUR_CLOUD_NAME"
    private val uploadPreset = "YOUR_UNSIGNED_UPLOAD_PRESET"

    init {
        try {
            val config = HashMap<String, String>()
            config["cloud_name"] = cloudName
            MediaManager.init(context, config)
        } catch (e: IllegalStateException) {
            // MediaManager already initialized
        }
    }

    // Overload for Uri
    private suspend fun uploadToCloudinary(
        uri: Uri,
        folder: String,
        publicId: String? = null
    ): Result<String> = suspendCancellableCoroutine { continuation ->

        val request = MediaManager.get().upload(uri)
            .unsigned(uploadPreset)
            .option("folder", folder)
            .option("resource_type", "auto")

        if (publicId != null) {
            request.option("public_id", publicId)
        }

        request.callback(createCallback(continuation)).dispatch()
    }

    // Overload for File Path (String)
    private suspend fun uploadToCloudinary(
        path: String,
        folder: String,
        publicId: String? = null
    ): Result<String> = suspendCancellableCoroutine { continuation ->

        val request = MediaManager.get().upload(path)
            .unsigned(uploadPreset)
            .option("folder", folder)
            .option("resource_type", "auto")

        if (publicId != null) {
            request.option("public_id", publicId)
        }

        request.callback(createCallback(continuation)).dispatch()
    }

    // Helper to create the callback
    private fun createCallback(continuation: kotlinx.coroutines.CancellableContinuation<Result<String>>) = object : UploadCallback {
        override fun onStart(requestId: String) {}
        override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}

        override fun onSuccess(requestId: String, resultData: Map<*, *>) {
            val url = resultData["secure_url"] as? String ?: ""
            if (continuation.isActive) {
                continuation.resume(Result.success(url))
            }
        }

        override fun onError(requestId: String, error: ErrorInfo) {
            if (continuation.isActive) {
                continuation.resume(Result.failure(Exception(error.description)))
            }
        }

        override fun onReschedule(requestId: String, error: ErrorInfo) {}
    }

    // --- Interface Implementation ---

    suspend fun uploadVoiceMessage(file: File, appointmentId: Int): Result<String> {
        val timestamp = System.currentTimeMillis()
        return uploadToCloudinary(
            path = file.absolutePath,
            folder = "messages/appointment_$appointmentId",
            publicId = "voice_$timestamp"
        )
    }

    suspend fun uploadFile(uri: Uri, path: String): Result<String> {
        val folder = path.substringBeforeLast("/", "uploads")
        val fileName = path.substringAfterLast("/").substringBeforeLast(".")
        return uploadToCloudinary(uri, folder, fileName)
    }

    suspend fun uploadProfileImage(uri: Uri, userId: String): Result<String> {
        return uploadToCloudinary(
            uri = uri,
            folder = "profiles/$userId",
            publicId = "avatar"
        )
    }

    suspend fun uploadMedicalReport(uri: Uri, patientId: String, fileName: String): Result<String> {
        return uploadToCloudinary(
            uri = uri,
            folder = "medical-reports/$patientId",
            publicId = fileName.substringBeforeLast(".")
        )
    }

    suspend fun uploadPrescription(uri: Uri, consultationId: String, fileName: String): Result<String> {
        return uploadToCloudinary(
            uri = uri,
            folder = "prescriptions/$consultationId",
            publicId = fileName.substringBeforeLast(".")
        )
    }

    suspend fun uploadVoiceRecording(uri: Uri, consultationId: String): Result<String> {
        val timestamp = System.currentTimeMillis()
        return uploadToCloudinary(
            uri = uri,
            folder = "consultations/$consultationId",
            publicId = "recording_$timestamp"
        )
    }

    suspend fun deleteFile(url: String): Result<Unit> {
        Log.w("CloudinaryStorage", "Delete requested for $url. Client-side deletion skipped for security.")
        return Result.success(Unit)
    }
}