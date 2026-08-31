// Top-level build file
plugins {
    id("com.android.application") version "8.5.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
    // Cloud Sync (Firestore): reads app/google-services.json and wires up FirebaseApp
    // auto-init. See SYNC_SETUP.md for the one-time Firebase Console steps this needs.
    id("com.google.gms.google-services") version "4.4.2" apply false
}
