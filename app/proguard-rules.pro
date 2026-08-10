# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ==========================================
# Kotlin & Coroutines
# ==========================================
-keepattributes *Annotation*
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ==========================================
# Firebase Auth
# ==========================================
-keep class com.google.firebase.auth.** { *; }
-keep class com.google.android.gms.auth.** { *; }

# ==========================================
# Firebase Firestore — keep model classes
# ==========================================
-keep class com.example.tfg.modelo.** { *; }
-keep class com.example.tfg.modelo.**$* { *; }

# ==========================================
# Firebase Realtime Database
# ==========================================
-keep class com.google.firebase.database.** { *; }
-keep class com.google.firebase.database.android.** { *; }

# ==========================================
# Firebase Storage
# ==========================================
-keep class com.google.firebase.storage.** { *; }

# ==========================================
# Google Sign-In / Play Services
# ==========================================
-keep class com.google.android.gms.** { *; }

# ==========================================
# Glide
# ==========================================
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep class com.bumptech.glide.** { *; }
-dontwarn com.bumptech.glide.**

# ==========================================
# MPAndroidChart
# ==========================================
-keep class com.github.mikephil.charting.** { *; }

# ==========================================
# AndroidX & Material
# ==========================================
-keep class androidx.** { *; }
-keep class com.google.android.material.** { *; }

# ==========================================
# ViewModel & LiveData
# ==========================================
-keep class * extends androidx.lifecycle.ViewModel {
    <init>();
}
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>();
}
-keepclassmembers class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}

# ==========================================
# Navigation Component
# ==========================================
-keep class androidx.navigation.** { *; }

# ==========================================
# WorkManager
# ==========================================
-keep class androidx.work.** { *; }

# ==========================================
# Keep all custom Android classes (ViewModels, Services, etc.)
# ==========================================
-keep class com.example.tfg.viewmodel.** { *; }
-keep class com.example.tfg.service.** { *; }
-keep class com.example.tfg.vista.** { *; }
-keep class com.example.tfg.repositorio.** { *; }
