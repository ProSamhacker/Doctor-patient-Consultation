package com.example.hospitalmanagement.storage

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Vercel Blob Storage Service
 * 
 * Configuration:
 * 1. Create a Vercel Blob store at https://vercel.com/dashboard/stores
 * 2. Get your BLOB_READ_WRITE_TOKEN from environment variables
 * 3. Add to gradle.properties: VERCEL_BLOB_TOKEN=your_token_here
 */
class VercelBlobStorage(private val context: Context) {

    private val blobToken = com.example.hospitalmanagement.BuildConfig.VERCEL_BLOB_TOKEN
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val BLOB_API_URL = "https://blob.vercel-storage.com"
    }

    /**
     * Upload file to Vercel Blob Storage
     * 
     * @param uri Local file URI
     * @param path Remote path (e.g., "medical-reports/patient123/report.pdf")
     * @return URL of uploaded file
     */
    suspend fun uploadFile(uri: Uri, path: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Create temporary file from URI
            val file = uriToFile(uri)
            
            // Create multipart request
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    file.name,
                    file.asRequestBody(getMimeType(file.name).toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url("$BLOB_API_URL/upload?pathname=$path")
                .addHeader("Authorization", "Bearer $blobToken")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                val json = JSONObject(responseBody ?: "{}")
                val url = json.getString("url")
                
                // Clean up temporary file
                file.delete()
                
                Result.success(url)
            } else {
                val error = response.body?.string() ?: "Upload failed"
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Upload profile image
     */
    suspend fun uploadProfileImage(uri: Uri, userId: String): Result<String> {
        val path = "profiles/$userId/avatar.jpg"
        return uploadFile(uri, path)
    }

    /**
     * Upload medical report
     */
    suspend fun uploadMedicalReport(
        uri: Uri,
        patientId: String,
        fileName: String
    ): Result<String> {
        val extension = fileName.substringAfterLast(".", "pdf")
        val timestamp = System.currentTimeMillis()
        val path = "medical-reports/$patientId/$timestamp.$extension"
        return uploadFile(uri, path)
    }

    /**
     * Upload prescription document
     */
    suspend fun uploadPrescription(
        uri: Uri,
        consultationId: String,
        fileName: String
    ): Result<String> {
        val extension = fileName.substringAfterLast(".", "pdf")
        val path = "prescriptions/$consultationId/$fileName"
        return uploadFile(uri, path)
    }

    /**
     * Upload voice recording
     */
    suspend fun uploadVoiceRecording(
        uri: Uri,
        consultationId: String
    ): Result<String> {
        val timestamp = System.currentTimeMillis()
        val path = "consultations/$consultationId/recording_$timestamp.m4a"
        return uploadFile(uri, path)
    }

    /**
     * Delete file from Vercel Blob Storage
     */
    suspend fun deleteFile(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BLOB_API_URL/delete")
                .addHeader("Authorization", "Bearer $blobToken")
                .addHeader("Content-Type", "application/json")
                .post(
                    okhttp3.RequestBody.create(
                        "application/json".toMediaTypeOrNull(),
                        """{"urls":["$url"]}"""
                    )
                )
                .build()

            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Delete failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Convert URI to File
     */
    private fun uriToFile(uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri)
        val tempFile = File.createTempFile("upload", ".tmp", context.cacheDir)
        
        inputStream?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        
        return tempFile
    }

    /**
     * Get MIME type from file name
     */
    private fun getMimeType(fileName: String): String {
        return when (fileName.substringAfterLast(".", "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "m4a", "mp4" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            else -> "application/octet-stream"
        }
    }
}

/**
 * Storage helper functions
 */
object StorageHelper {
    /**
     * Get file name from URL
     */
    fun getFileNameFromUrl(url: String): String {
        return url.substringAfterLast("/")
    }

    /**
     * Check if file is image
     */
    fun isImageFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast(".", "").lowercase()
        return extension in listOf("jpg", "jpeg", "png", "gif", "webp")
    }

    /**
     * Check if file is document
     */
    fun isDocumentFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast(".", "").lowercase()
        return extension in listOf("pdf", "doc", "docx", "txt")
    }

    /**
     * Check if file is audio
     */
    fun isAudioFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast(".", "").lowercase()
        return extension in listOf("mp3", "m4a", "wav", "aac")
    }

    /**
     * Format file size
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
}