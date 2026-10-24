plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.shawnrodis.photocraftexpress"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shawnrodis.photocraftexpress"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.2.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/kotlin-project-structure-metadata.json"
            // Kotlin multiplatform metadata duplicated between lifecycle-common
            // and annotation jars (both ship the same androidx knm/manifest files).
            // These are Kotlin/Native compiler metadata, never read at Android
            // runtime, so dropping the whole trees from the APK is safe.
            excludes += "nonJvmMain/**"
            excludes += "commonMain/**"
            excludes += "**/*.knm"
        }
    }
}

// androidx.collection:collection-ktx was folded into androidx.collection:collection
// in 1.4.0, but an older transitive dep still pulls collection-ktx 1.2.0, whose
// classes duplicate collection 1.4.0's. Excluding the old module is safe:
// everything it provided now lives in collection itself.
configurations.all {
    exclude(group = "androidx.collection", module = "collection-ktx")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // EXIF orientation for photo loading (same version as the Toolbox app).
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // System print UI for the export sheet's Print action.
    implementation("androidx.print:print:1.0.0")

    // On-device ML Kit (bundled models, no Play Services dependency):
    // face detection for Face / Makeup / Eyes, selfie segmentation for
    // Background (people-only in v0.2.0).
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
}
