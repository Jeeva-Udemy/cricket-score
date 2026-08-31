plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") // Used for Room's annotation processing
    id("com.google.gms.google-services") // Cloud Sync: reads google-services.json below
}

android {
    namespace = "com.example.cricketscorer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.cricketscorer"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Google Sign-In's DEVELOPER_ERROR (Backup & Resync, req #1) was caused by this: every
    // debug build was signed with whatever throwaway ~/.android/debug.keystore happened to
    // exist on that machine — and the GitHub Actions runner generates a brand new one on
    // every single run, since nothing persists it between jobs. That means the app's SHA-1
    // fingerprint (which Google Cloud Console's OAuth client is matched against) changed
    // on every build, so no registered fingerprint could ever stay valid.
    // Fix: sign every debug build with this one fixed, repo-committed keystore instead, so
    // the SHA-1 is identical on every machine/CI run forever. See README.md for the
    // one-time Google Cloud Console setup this still requires.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // The Google Drive API client libraries transitively pull in Apache HttpClient/HttpCore
    // jars that duplicate META-INF metadata files, which breaks packaging even with the
    // dependency-level excludes below. Drop the duplicated files explicitly.
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/INDEX.LIST"
            )
        }
    }
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Lifecycle / ViewModel
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Google Sign-In + Drive (Backup & Resync — req #1: match history synced to the user's
    // Google Drive App Data folder so it survives a reinstall)
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.api-client:google-api-client-android:2.2.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.apis:google-api-services-drive:v3-rev20220815-2.0.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.http-client:google-http-client-gson:1.44.1") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.http-client:google-http-client:1.44.1") {
        exclude(group = "org.apache.httpcomponents")
    }

    // Cloud Sync (Firestore) — mirrors a live match between Mobile1 (TeamA) and Mobile2
    // (TeamB) so both can update the same score. See SYNC_SETUP.md.
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
