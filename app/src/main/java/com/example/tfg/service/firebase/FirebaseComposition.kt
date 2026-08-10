package com.example.tfg.service.firebase

import android.app.Application
import com.example.tfg.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

enum class FirebaseMode { EMULATOR, RELEASE }

data class CompositionContext(
    val mode: FirebaseMode,
    val host: String,
    val projectId: String,
    val applicationId: String
)

/** The only place where TeamTask creates Firebase SDK clients. */
object FirebaseComposition {
    private const val APP_NAME = "teamtask-composed"
    private const val EMULATOR_HOST = "10.0.2.2"
    private const val AUTH_PORT = 9099
    private const val FIRESTORE_PORT = 8080
    private const val STORAGE_PORT = 9199
    private const val RELEASE_PROJECT = "teamtask-3a855"
    private const val RELEASE_APPLICATION_ID = "1:680542959178:android:4e4a8b88dffdec5df918b3"
    private const val EMULATOR_PROJECT = "teamtask-emulator"
    private const val EMULATOR_APPLICATION_ID = "1:000000000000:android:teamtask-emulator"
    private const val EMULATOR_API_KEY = "teamtask-emulator-api-key"
    private const val EMULATOR_STORAGE_BUCKET = "teamtask-emulator.appspot.com"
    private var context: CompositionContext? = null
    private var authClient: FirebaseAuth? = null
    private var firestoreClient: FirebaseFirestore? = null
    private var storageClient: FirebaseStorage? = null

    @Synchronized
    fun init(application: Application, modeName: String, host: String) {
        check(context == null) { "FirebaseComposition ya fue inicializada" }
        val mode = runCatching { FirebaseMode.valueOf(modeName) }
            .getOrElse { error("Modo Firebase inválido: $modeName") }
        val source = FirebaseApp.getApps(application).firstOrNull()
            ?: error("FirebaseApp no está configurada")
        check(source.options.apiKey.isNotBlank()) {
            "La configuración Firebase no contiene una API key válida"
        }
        check(source.options.projectId == RELEASE_PROJECT) {
            "La configuración Firebase no corresponde al proyecto de producción"
        }
        check(source.options.applicationId == RELEASE_APPLICATION_ID) {
            "La configuración Firebase no corresponde a la aplicación de producción"
        }
        check(!source.options.storageBucket.isNullOrBlank()) {
            "La configuración Firebase no contiene un bucket de producción"
        }
        val expectedProject = BuildConfig.FIREBASE_PROJECT_ID
        val expectedApplicationId = if (mode == FirebaseMode.EMULATOR) {
            check(BuildConfig.FIREBASE_APPLICATION_ID == EMULATOR_APPLICATION_ID) {
                "Identidad emulator inválida"
            }
            EMULATOR_APPLICATION_ID
        } else {
            check(BuildConfig.FIREBASE_APPLICATION_ID == RELEASE_APPLICATION_ID) {
                "Identidad applicationId release inválida"
            }
            RELEASE_APPLICATION_ID
        }

        when (mode) {
            FirebaseMode.EMULATOR -> {
                check(host == EMULATOR_HOST) { "El build emulator requiere host $EMULATOR_HOST" }
                check(expectedProject == EMULATOR_PROJECT) { "Identidad de proyecto emulator inválida" }
            }
            FirebaseMode.RELEASE -> {
                check(host.isBlank()) { "release no puede declarar un endpoint de emulador" }
                check(expectedProject == RELEASE_PROJECT) { "Identidad release inválida" }
            }
        }

        val options = FirebaseOptions.Builder()
            .setApplicationId(expectedApplicationId)
            .setApiKey(if (mode == FirebaseMode.EMULATOR) EMULATOR_API_KEY else source.options.apiKey)
            .setProjectId(if (mode == FirebaseMode.EMULATOR) expectedProject else source.options.projectId)
            .apply {
                if (mode == FirebaseMode.EMULATOR) {
                    setStorageBucket(EMULATOR_STORAGE_BUCKET)
                } else {
                    source.options.storageBucket?.let(::setStorageBucket)
                }
            }
            .build()
        val app = FirebaseApp.initializeApp(application, options, APP_NAME)
            ?: error("No se pudo inicializar FirebaseComposition")
        val auth = FirebaseAuth.getInstance(app)
        val firestore = FirebaseFirestore.getInstance(app)
        val storage = FirebaseStorage.getInstance(app)
        if (mode == FirebaseMode.EMULATOR) {
            auth.useEmulator(host, AUTH_PORT)
            firestore.useEmulator(host, FIRESTORE_PORT)
            storage.useEmulator(host, STORAGE_PORT)
        }
        context = CompositionContext(mode, host, expectedProject, expectedApplicationId)
        authClient = auth
        firestoreClient = firestore
        storageClient = storage
    }

    fun requireContext(): CompositionContext = context ?: error("FirebaseComposition no inicializada")
    fun auth(): FirebaseAuth = authClient ?: error("Firebase Auth no está configurado")
    fun firestore(): FirebaseFirestore = firestoreClient ?: error("Firestore no está configurado")
    fun storage(): FirebaseStorage = storageClient ?: error("Storage no está configurado")
    fun assertEmulator() = check(requireContext().mode == FirebaseMode.EMULATOR) { "Se requiere build emulator" }
    fun assertRelease() = check(requireContext().mode == FirebaseMode.RELEASE) { "Se requiere build release" }
}
