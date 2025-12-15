plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt") // Required for Room
}

android {
    namespace = "com.example.hospitalmanagement"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        applicationId = "com.example.hospitalmanagement"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                buildConfigField(
                    "String",
                    "GEMINI_API_KEY",
                    "\"${project.properties["GEMINI_API_KEY"]}\""
                )

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // ✅ RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // ✅ Room (Database)
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // ✅ Coroutines (for Room operations)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // ✅ Lifecycle (so we can use lifecycleScope)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // ... your existing dependencies (core, appcompat, material, room)
// For ViewModel and LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.5.1")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.5.1")
// For Networking (to call Gemini API)
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
// For JSON Parsing (to handle Gemini response)
    implementation("com.google.code.gson:gson:2.9.0")
}
