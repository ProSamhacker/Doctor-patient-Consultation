package com.example.hospitalmanagement.storage

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject

/** Vercel Blob Storage Service - Enhanced for voice messages */
class VercelBlobStorage(private val context: Context) {

    private val blobToken = com.example.hospitalmanagement.BuildConfig.VERCEL_BLOB_TOKEN
    private val client =
            OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()

    companion object {
        private const val BLOB_API_URL = "https://blob.vercel-storage.com"
    }

    /**
     * Upload voice message from File
     * @param file Local audio file
     * @param appointmentId ID of the appointment
     * @return URL of uploaded file
     */
    suspend fun uploadVoiceMessage(file: File, appointmentId: Int): Result<String> =
            withContext(Dispatchers.IO) {
                try {
                    val timestamp = System.currentTimeMillis()
                    val fileName = "voice_${timestamp}.m4a"
                    val path = "messages/appointment_$appointmentId/$fileName"

                    val requestBody =
                            MultipartBody.Builder()
                                    .setType(MultipartBody.FORM)
                                    .addFormDataPart(
                                            "file",
                                            fileName,
                                            file.asRequestBody("audio/mp4".toMediaTypeOrNull())
                                    )
                                    .build()

                    val request =
                            Request.Builder()
                                    .url("$BLOB_API_URL/upload?pathname=$path")
                                    .addHeader("Authorization", "Bearer $blobToken")
                                    .post(requestBody)
                                    .build()

                    val response = client.newCall(request).execute()

                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        val json = JSONObject(responseBody ?: "{}")
                        val url = json.getString("url")
                        Result.success(url)
                    } else {
                        val error = response.body?.string() ?: "Upload failed: ${response.code}"
                        Result.failure(Exception(error))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

    /** Upload file to Vercel Blob Storage */
    suspend fun uploadFile(uri: Uri, path: String): Result<String> =
            withContext(Dispatchers.IO) {
                try {
                    val file = uriToFile(uri)

                    val requestBody =
                            MultipartBody.Builder()
                                    .setType(MultipartBody.FORM)
                                    .addFormDataPart(
                                            "file",
                                            file.name,
                                            file.asRequestBody(
                                                    getMimeType(file.name).toMediaTypeOrNull()
                                            )
                                    )
                                    .build()

                    val request =
                            Request.Builder()
                                    .url("$BLOB_API_URL/upload?pathname=$path")
                                    .addHeader("Authorization", "Bearer $blobToken")
                                    .post(requestBody)
                                    .build()

                    val response = client.newCall(request).execute()

                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        val json = JSONObject(responseBody ?: "{}")
                        val url = json.getString("url")

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

    /** Upload profile image */
    suspend fun uploadProfileImage(uri: Uri, userId: String): Result<String> {
        val path = "profiles/$userId/avatar.jpg"
        return uploadFile(uri, path)
    }

    /** Upload medical report */
    suspend fun uploadMedicalReport(uri: Uri, patientId: String, fileName: String): Result<String> {
        val extension = fileName.substringAfterLast(".", "pdf")
        val timestamp = System.currentTimeMillis()
        val path = "medical-reports/$patientId/$timestamp.$extension"
        return uploadFile(uri, path)
    }

    /** Upload prescription document */
    suspend fun uploadPrescription(
            uri: Uri,
            consultationId: String,
            fileName: String
    ): Result<String> {
        val path = "prescriptions/$consultationId/$fileName"
        return uploadFile(uri, path)
    }

    /** Upload voice recording (from URI) */
    suspend fun uploadVoiceRecording(uri: Uri, consultationId: String): Result<String> {
        val timestamp = System.currentTimeMillis()
        val path = "consultations/$consultationId/recording_$timestamp.m4a"
        return uploadFile(uri, path)
    }

    /** Delete file from Vercel Blob Storage */
    suspend fun deleteFile(url: String): Result<Unit> =
            withContext(Dispatchers.IO) {
                try {
                    val request =
                            Request.Builder()
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

    /** Convert URI to File */
    private fun uriToFile(uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri)
        val tempFile = File.createTempFile("upload", ".tmp", context.cacheDir)

        inputStream?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        }

        return tempFile
    }

    /** Get MIME type from file name */
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

/** Storage helper functions */
object StorageHelper {
    fun getFileNameFromUrl(url: String): String {
        return url.substringAfterLast("/")
    }

    fun isImageFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast(".", "").lowercase()
        return extension in listOf("jpg", "jpeg", "png", "gif", "webp")
    }

    fun isDocumentFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast(".", "").lowercase()
        return extension in listOf("pdf", "doc", "docx", "txt")
    }

    fun isAudioFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast(".", "").lowercase()
        return extension in listOf("mp3", "m4a", "wav", "aac")
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
}
