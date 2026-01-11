package com.example.hospitalmanagement

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object AppointmentScheduler {

    private var isRunning = false

    /**
     * Starts monitoring for upcoming appointments.
     * Call this in onCreate() of your Doctor/Patient Dashboard.
     */
    fun startMonitoring(
        context: Context,
        scope: CoroutineScope,
        repository: HospitalRepository,
        userId: String,
        userRole: String
    ) {
        if (isRunning) return // Prevent duplicate runners
        isRunning = true
        Log.d("Scheduler", "Started monitoring for $userRole : $userId")

        scope.launch(Dispatchers.IO) {
            while (isRunning) {
                try {
                    val now = System.currentTimeMillis()

                    // Fetch all appointments (One-shot fetch)
                    // Note: We use first() to get the current snapshot from the Flow
                    val appointments = if (userRole == "DOCTOR") {
                        repository.getDoctorAppointments(userId).first()
                    } else {
                        repository.getPatientAppointments(userId).first()
                    }

                    // Find a match: Status SCHEDULED and Time is within +/- 1 minute window
                    val liveAppointment = appointments.find {
                        val diff = Math.abs(it.dateTime - now)
                        it.status == AppointmentStatus.SCHEDULED && diff < 60000 // 60 seconds tolerance
                    }

                    if (liveAppointment != null) {
                        launch(Dispatchers.Main) {
                            triggerMeetingNotification(context, liveAppointment.appId, userId, userRole)
                        }
                        // Wait 2 mins before checking again to avoid spamming for the same meeting
                        delay(120000)
                    } else {
                        // No meeting now, check again in 30 seconds
                        delay(30000)
                    }

                } catch (e: Exception) {
                    Log.e("Scheduler", "Error checking appointments", e)
                    delay(30000) // Retry later on error
                }
            }
        }
    }

    fun stopMonitoring() {
        isRunning = false
    }

    /**
     * Triggers a High-Priority Notification that opens ConsultationActivity
     */
    fun triggerMeetingNotification(
        context: Context,
        appointmentId: Int,
        userId: String,
        userRole: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "consultation_channel"

        // 1. Create Channel (Required for Android O+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Consultation Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming consultation alerts"
                enableVibration(true)
                setBypassDnd(true) // Critical alert
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 2. Intent to open the "Meeting Room"
        val fullScreenIntent = Intent(context, ConsultationActivity::class.java).apply {
            putExtra("APP_ID", appointmentId)
            putExtra("USER_ID", userId)
            putExtra("USER_ROLE", userRole)
            // Flags to clear history and start fresh
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            appointmentId,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Build Notification
        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_phone) // Ensure you have this icon
            .setContentTitle("Consultation Starting")
            .setContentText("Your appointment is scheduled for NOW. Tap to join.")
            .setPriority(NotificationCompat.PRIORITY_MAX) // MAX for heads-up
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setFullScreenIntent(fullScreenPendingIntent, true) // THIS is the key for full-screen pop-up
            .setContentIntent(fullScreenPendingIntent)

            // Add action button "Join"
            .addAction(
                R.drawable.ic_check,
                "Join Now",
                fullScreenPendingIntent
            )

        // 4. Show it
        notificationManager.notify(appointmentId, notificationBuilder.build())
        Log.d("Scheduler", "Notification triggered for Appointment $appointmentId")

        // OPTIONAL: If app is visibly foreground, you might want to auto-launch:
        // context.startActivity(fullScreenIntent)
    }
}