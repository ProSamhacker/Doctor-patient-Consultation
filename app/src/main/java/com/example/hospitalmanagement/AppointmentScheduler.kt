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

    fun startMonitoring(
        context: Context,
        scope: CoroutineScope,
        repository: HospitalRepository,
        userId: String,
        userRole: String
    ) {
        if (isRunning) return
        isRunning = true
        Log.d("Scheduler", "Monitoring started for $userRole: $userId")

        scope.launch(Dispatchers.IO) {
            while (isRunning) {
                try {
                    val now = System.currentTimeMillis()

                    // 1. Fetch Current Appointments
                    val appointments = if (userRole == "DOCTOR") {
                        repository.getDoctorAppointments(userId).first()
                    } else {
                        repository.getPatientAppointments(userId).first()
                    }

                    appointments.forEach { appt ->
                        // 1. Handle PENDING appointments that have expired
                        if (appt.status == AppointmentStatus.PENDING) {
                            // If the appointment time has passed and it's still pending, mark as EXPIRED
                            if (appt.dateTime < now) {
                                handleAutoExpire(repository, appt)
                            }
                        }
                        
                        // 2. Handle SCHEDULED appointments
                        else if (appt.status == AppointmentStatus.SCHEDULED) {
                            val timeDiff = appt.dateTime - now

                            // Scenario A: 5 Minutes before (0 to 5 mins remaining)
                            if (timeDiff in 0..300000) {
                                val mins = (timeDiff / 60000) + 1
                                triggerMeetingNotification(
                                    context, appt.appId, userId, userRole,
                                    "Your appointment starts in $mins mins. Tap to prepare."
                                )
                            }
                            // Scenario B: Meeting Time / Late Join (-5 mins to 0 mins remaining)
                            else if (timeDiff in -300000..0) {
                                triggerMeetingNotification(
                                    context, appt.appId, userId, userRole,
                                    "Meeting in Progress! JOIN NOW."
                                )
                            }
                            // Scenario C: No Show (> 5 mins late)
                            else if (timeDiff < -300000) {
                                // Check if session started. If not, cancel.
                                val session = repository.getActiveConsultationSession(appt.appId)
                                // FIXED: session.duration is Int, so compared to 0 (Int), not 0L (Long)
                                if (session == null || (!session.isRecording && session.duration == 0)) {
                                    handleAutoCancel(repository, appt)
                                }
                            }
                        }
                    }

                    delay(60000)

                } catch (e: Exception) {
                    Log.e("Scheduler", "Error in loop", e)
                    delay(60000)
                }
            }
        }
    }

    private suspend fun handleAutoExpire(repository: HospitalRepository, appointment: Appointment) {
        Log.i("Scheduler", "Auto-expiring pending Appointment ${appointment.appId}")
        repository.updateAppointmentWithSync(appointment.copy(status = AppointmentStatus.EXPIRED))

        // Notify Patient
        val notification = NotificationEntity(
            userId = appointment.patientId,
            userType = "PATIENT",
            title = "Request Expired",
            message = "Your appointment request has expired because it was not accepted in time.",
            type = NotificationType.INFO,
            relatedId = appointment.appId
        )
        repository.createNotification(notification)
        repository.syncNotificationToFirebasePublic(notification)
    }

    private suspend fun handleAutoCancel(repository: HospitalRepository, appointment: Appointment) {
        Log.i("Scheduler", "Auto-cancelling Appointment ${appointment.appId} due to No-Show")
        repository.updateAppointmentWithSync(appointment.copy(status = AppointmentStatus.CANCELLED)) // Use CANCELLED like user requested

        // Notify Patient
        val notification = NotificationEntity(
            userId = appointment.patientId,
            userType = "PATIENT",
            title = "Appointment Cancelled",
            message = "Appointment cancelled as nobody joined within 5 minutes.",
            type = NotificationType.APPOINTMENT_CANCELLED,
            relatedId = appointment.appId
        )
        repository.createNotification(notification)
        repository.syncNotificationToFirebasePublic(notification)

        // Notify Doctor
        val docNotification = notification.copy(userId = appointment.doctorId, userType = "DOCTOR")
        repository.createNotification(docNotification)
        repository.syncNotificationToFirebasePublic(docNotification)
    }

    fun stopMonitoring() {
        isRunning = false
    }

    fun triggerMeetingNotification(
        context: Context,
        appointmentId: Int,
        userId: String,
        userRole: String,
        subText: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "consultation_urgent"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Urgent Consultations", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Pop-up alerts for video calls"
                enableVibration(true)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val fullScreenIntent = Intent(context, ConsultationActivity::class.java).apply {
            putExtra("APP_ID", appointmentId)
            putExtra("USER_ID", userId)
            putExtra("USER_ROLE", userRole)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context, appointmentId, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle("Video Consultation")
            .setContentText(subText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_check, "ENTER ROOM", pendingIntent)

        notificationManager.notify(appointmentId, builder.build())
    }
}