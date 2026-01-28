package com.example.tfg.vista

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.navigation.Navigation
import com.example.tfg.databinding.ActivityMainBinding
import com.example.tfg.service.NotificationScheduler
import com.example.tfg.viewmodel.ParejaViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val parejaVM: ParejaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Dejar que el sistema gestione los insets y el redimensionado por el teclado (adjustResize)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Al iniciar la app: si hay usuario autenticado, solicitar carga del grupo asociado
        try {
            val uid = com.example.tfg.service.LocalizadorServicios.repositorioAuth.usuarioActual()?.id
            if (!uid.isNullOrBlank()) {
                parejaVM.cargarGrupoPorUsuario(uid)
            }
        } catch (e: Exception) {
            // ignore
        }

        val navHostFragment = supportFragmentManager.findFragmentById(com.example.tfg.R.id.nav_host_fragment) as? NavHostFragment
        val navController = navHostFragment?.navController ?: run {
            val found = try {
                Navigation.findNavController(this, com.example.tfg.R.id.nav_host_fragment)
            } catch (e: IllegalStateException) {
                null
            }
            found ?: throw IllegalStateException("No se encontró NavHostFragment o NavController con id nav_host_fragment. Revisa activity_main.xml y que el id coincida.")
        }

        // Configurar BottomNavigation
        binding.bottomNavigation.setupWithNavController(navController)

        // Ajustar insets: añadir el bottom inset como padding al contenedor y al navHostFragment
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            // sólo usar navigationBars para evitar conflictos con IME (el sistema gestionará el resize por windowSoftInputMode)
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(bottom = navInsets.bottom)
            binding.navHostFragment?.updatePadding(bottom = navInsets.bottom)
            // mantener bottomNavigation en su sitio (el nav host ya tiene padding)
            insets
        }

        // Observar notificaciones para el usuario actual y mostrar locales
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val uid = com.example.tfg.service.LocalizadorServicios.repositorioAuth.usuarioActual()?.id
                if (!uid.isNullOrBlank()) {
                    val repoNot = com.example.tfg.repositorio.RepositorioNotificaciones()
                    // usar flow observer
                    repoNot.observarNotificaciones(uid).collect { lista ->
                        lista.filter { !it.visto }.forEach { not ->
                            // mostrar notificación local
                            val title = when (not.tipo) { "asignacion" -> "Tarea asignada"; else -> "Notificación" }
                            val message = (not.contenido["titulo"] as? String) ?: (not.contenido["texto"] as? String) ?: "Tienes una notificación"
                            val tareaId = (not.contenido["tareaId"] as? String)
                            NotificationScheduler.showImmediateNotification(this@MainActivity, (not.id.hashCode()), title, message, tareaId)
                            // marcar como vista
                            CoroutineScope(Dispatchers.IO).launch {
                                repoNot.marcarNotificacionVista(not.id)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        // manejar si la activity fue lanzada con openTaskId
        intent?.getStringExtra("openTaskId")?.let { tid ->
            handleOpenTaskId(tid, navController)
        }

        // Ocultar la barra inferior en pantallas de presentación y login
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                com.example.tfg.R.id.fragment_Presentacion,
                com.example.tfg.R.id.fragment_Login -> binding.bottomNavigation.visibility = View.GONE
                else -> binding.bottomNavigation.visibility = View.VISIBLE
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("openTaskId")?.let { tid ->
            val navHostFragment = supportFragmentManager.findFragmentById(com.example.tfg.R.id.nav_host_fragment) as? NavHostFragment
            val navController = navHostFragment?.navController
            if (navController != null) handleOpenTaskId(tid, navController)
        }
    }

    private fun handleOpenTaskId(taskId: String, navController: androidx.navigation.NavController) {
        try {
            val bundle = android.os.Bundle().apply { putString("taskId", taskId) }
            navController.navigate(com.example.tfg.R.id.fragment_Tareas, bundle)
        } catch (e: Exception) {
            // ignore navigation errors
        }
    }
}
