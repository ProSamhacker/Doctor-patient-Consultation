package com.example.hospitalmanagement

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.hospitalmanagement.DAO.AiExtractionDao
import com.example.hospitalmanagement.DAO.AppointmentDao
import com.example.hospitalmanagement.DAO.ConsultationSessionDao
import com.example.hospitalmanagement.DAO.DoctorDao
import com.example.hospitalmanagement.DAO.EmergencyContactDao
import com.example.hospitalmanagement.DAO.MedicalReportDao
import com.example.hospitalmanagement.DAO.MedicationDao
import com.example.hospitalmanagement.DAO.MessageDao
import com.example.hospitalmanagement.DAO.NotificationDao
import com.example.hospitalmanagement.DAO.PatientDao
import com.example.hospitalmanagement.DAO.PrescriptionDao
import com.example.hospitalmanagement.DAO.VitalSignsDao

@Database(
    entities = [
        Doctor::class,
        Patient::class,
        Appointment::class,
        ConsultationSession::class,
        AiExtraction::class,
        Prescription::class,
        Medication::class,
        Message::class,
        MedicalReport::class,
        VitalSigns::class,
        NotificationEntity::class,
        EmergencyContact::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    // DAOs
    abstract fun doctorDao(): DoctorDao
    abstract fun patientDao(): PatientDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun prescriptionDao(): PrescriptionDao
    abstract fun messageDao(): MessageDao
    abstract fun consultationSessionDao(): ConsultationSessionDao
    abstract fun aiExtractionDao(): AiExtractionDao
    abstract fun medicalReportDao(): MedicalReportDao
    abstract fun vitalSignsDao(): VitalSignsDao
    abstract fun notificationDao(): NotificationDao
    abstract fun emergencyContactDao(): EmergencyContactDao
    abstract fun medicationDao(): MedicationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new tables
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `messages` (
                        `messageId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `appId` INTEGER NOT NULL,
                        `senderId` TEXT NOT NULL,
                        `senderType` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `messageType` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `isRead` INTEGER NOT NULL,
                        `audioFilePath` TEXT NOT NULL,
                        FOREIGN KEY(`appId`) REFERENCES `appointments`(`appId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `medical_reports` (
                        `reportId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `patientId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `reportType` TEXT NOT NULL,
                        `filePath` TEXT NOT NULL,
                        `uploadedBy` TEXT NOT NULL,
                        `uploadDate` INTEGER NOT NULL,
                        `notes` TEXT NOT NULL,
                        FOREIGN KEY(`patientId`) REFERENCES `patients`(`patientId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `vital_signs` (
                        `vitalId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `appId` INTEGER NOT NULL,
                        `temperature` REAL,
                        `bloodPressureSystolic` INTEGER,
                        `bloodPressureDiastolic` INTEGER,
                        `heartRate` INTEGER,
                        `respiratoryRate` INTEGER,
                        `oxygenSaturation` INTEGER,
                        `weight` REAL,
                        `height` REAL,
                        `recordedAt` INTEGER NOT NULL,
                        `recordedBy` TEXT NOT NULL,
                        FOREIGN KEY(`appId`) REFERENCES `appointments`(`appId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `notifications` (
                        `notificationId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `userType` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `message` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `relatedId` INTEGER,
                        `isRead` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                """.trimIndent())

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `emergency_contacts` (
                        `contactId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `patientId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `relationship` TEXT NOT NULL,
                        `phone` TEXT NOT NULL,
                        `isPrimary` INTEGER NOT NULL,
                        FOREIGN KEY(`patientId`) REFERENCES `patients`(`patientId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hospital_management_db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration() // For development only
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Insert sample data
                            insertSampleData(context)
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private fun insertSampleData(context: Context) {
            // Insert sample data in background
            kotlin.concurrent.thread {
                val db = getDatabase(context)

                // Sample Doctors
                val doctors = listOf(
                    Doctor(
                        doctorId = "DOC001",
                        name = "Dr. Amit Kumar",
                        specialization = "Cardiologist",
                        phone = "+91 98765 43210",
                        email = "amit.kumar@hospital.com",
                        hospitalName = "Apollo Hospital",
                        experienceYears = 15,
                        rating = 4.8f,
                        consultationFee = 1000.0
                    ),
                    Doctor(
                        doctorId = "DOC002",
                        name = "Dr. Priya Sharma",
                        specialization = "Cardiologist",
                        phone = "+91 98765 43211",
                        email = "priya.sharma@hospital.com",
                        hospitalName = "Fortis Hospital",
                        experienceYears = 12,
                        rating = 4.7f,
                        consultationFee = 950.0
                    ),
                    Doctor(
                        doctorId = "DOC003",
                        name = "Dr. Rajesh Verma",
                        specialization = "Pediatrician",
                        phone = "+91 98765 43212",
                        email = "rajesh.verma@hospital.com",
                        hospitalName = "Max Hospital",
                        experienceYears = 10,
                        rating = 4.9f,
                        consultationFee = 800.0
                    )
                )

                // Sample Patients
                val patients = listOf(
                    Patient(
                        patientId = "PAT001",
                        name = "Rahul Singh",
                        age = 35,
                        gender = "Male",
                        phone = "+91 98765 12345",
                        email = "rahul.singh@email.com",
                        bloodGroup = "O+",
                        allergies = listOf("Penicillin"),
                        chronicConditions = listOf("Hypertension")
                    ),
                    Patient(
                        patientId = "PAT002",
                        name = "John Doe",
                        age = 42,
                        gender = "Male",
                        phone = "+91 98765 12346",
                        email = "john.doe@email.com",
                        bloodGroup = "A+",
                        allergies = emptyList(),
                        chronicConditions = emptyList()
                    )
                )

                kotlinx.coroutines.runBlocking {
                    // Insert doctors
                    doctors.forEach { db.doctorDao().insert(it) }

                    // Insert patients
                    patients.forEach { db.patientDao().insert(it) }

                    // Insert sample appointments
                    val now = System.currentTimeMillis()
                    val oneDayMillis = 24 * 60 * 60 * 1000L

                    db.appointmentDao().insert(
                        Appointment(
                            doctorId = "DOC001",
                            patientId = "PAT001",
                            dateTime = now + oneDayMillis,
                            status = AppointmentStatus.SCHEDULED,
                            chiefComplaint = "Chest pain",
                            tokenNumber = 1
                        )
                    )

                    db.appointmentDao().insert(
                        Appointment(
                            doctorId = "DOC001",
                            patientId = "PAT002",
                            dateTime = now + (2 * oneDayMillis),
                            status = AppointmentStatus.SCHEDULED,
                            chiefComplaint = "Regular checkup",
                            tokenNumber = 2
                        )
                    )
                }
            }
        }

        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}