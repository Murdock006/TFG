plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.navigation.safeargs)
    id("com.google.gms.google-services")
}

android {
    namespace = "es.sintaxys.teamtask"

    compileSdk = 36

    defaultConfig {
        applicationId = "es.sintaxys.teamtask"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "FIREBASE_MODE", "\"RELEASE\"")
        buildConfigField("String", "FIREBASE_HOST", "\"\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"teamtask-3a855\"")
        buildConfigField("String", "FIREBASE_APPLICATION_ID", "\"1:680542959178:android:653f3c35ed1a10d2f918b3\"")

        // AdMob - IDs REALES usados en release (y como valor por defecto).
        // En debug/emulator se sobreescriben con los IDs de TEST de Google para
        // evitar baneos por clicks propios durante el desarrollo.
        buildConfigField("String", "AD_APP_ID", "\"ca-app-pub-9694061031182900~3914949375\"")
        buildConfigField("String", "AD_REWARDED_UNIT_ID", "\"ca-app-pub-9694061031182900/5383195280\"")
        manifestPlaceholders["admobAppId"] = "ca-app-pub-9694061031182900~3914949375"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // IDs de TEST de AdMob para desarrollo local.
        // Se define antes de "emulator" para que este herede los mismos valores via initWith(debug).
        getByName("debug") {
            buildConfigField("String", "AD_APP_ID", "\"ca-app-pub-3940256099942544~3347511713\"")
            buildConfigField("String", "AD_REWARDED_UNIT_ID", "\"ca-app-pub-3940256099942544/5224354917\"")
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
        }
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
            buildConfigField("String", "FIREBASE_APPLICATION_ID", "\"1:680542959178:android:653f3c35ed1a10d2f918b3\"")
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

    // Google Mobile Ads (AdMob) - anuncios recompensados
    implementation("com.google.android.gms:play-services-ads:25.4.0")

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
