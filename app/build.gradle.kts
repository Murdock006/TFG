plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.navigation.safeargs)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.tfg"

    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.tfg"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "FIREBASE_MODE", "\"RELEASE\"")
        buildConfigField("String", "FIREBASE_HOST", "\"\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"teamtask-3a855\"")
        buildConfigField("String", "FIREBASE_APPLICATION_ID", "\"1:680542959178:android:4e4a8b88dffdec5df918b3\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        create("emulator") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            buildConfigField("String", "FIREBASE_MODE", "\"EMULATOR\"")
            buildConfigField("String", "FIREBASE_HOST", "\"10.0.2.2\"")
            buildConfigField("String", "FIREBASE_PROJECT_ID", "\"teamtask-emulator\"")
            buildConfigField("String", "FIREBASE_APPLICATION_ID", "\"1:000000000000:android:teamtask-emulator\"")
        }
        release {
            isMinifyEnabled = true
            buildConfigField("String", "FIREBASE_MODE", "\"RELEASE\"")
            buildConfigField("String", "FIREBASE_HOST", "\"\"")
            buildConfigField("String", "FIREBASE_PROJECT_ID", "\"teamtask-3a855\"")
            buildConfigField("String", "FIREBASE_APPLICATION_ID", "\"1:680542959178:android:4e4a8b88dffdec5df918b3\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
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

    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))

    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // Await para Tasks de Firebase desde coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.legacy.support.v4)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.navigation.fragment)
    implementation("androidx.navigation:navigation-ui-ktx:2.9.6")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.swiperefreshlayout)

    // WorkManager (KTX)
    implementation("androidx.work:work-runtime-ktx:2.8.1")

    // MPAndroidChart para gráficos circulares
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Glide para cargar imágenes desde URLs
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
