package com.example.hospitalmanagement

import androidx.room.Embedded
import androidx.room.Relation

data class AppointmentWithDetails(
    @Embedded val appointment: Appointment,
    @Relation(
        parentColumn = "doctorId",
        entityColumn = "doctorId"
    )
    val doctor: Doctor,
    @Relation(
        parentColumn = "patientId",
        entityColumn = "patientId"
    )
    val patient: Patient,
    @Relation(
        parentColumn = "appId",
        entityColumn = "appId"
    )
    val prescription: Prescription?
)

data class DoctorWithAppointments(
    @Embedded val doctor: Doctor,
    @Relation(
        parentColumn = "doctorId",
        entityColumn = "doctorId"
    )
    val appointments: List<Appointment>
)

data class PatientWithAppointments(
    @Embedded val patient: Patient,
    @Relation(
        parentColumn = "patientId",
        entityColumn = "patientId"
    )
    val appointments: List<Appointment>
)