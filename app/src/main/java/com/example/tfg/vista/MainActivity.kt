package com.example.tfg.vista

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.navigation.Navigation
import com.example.tfg.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // No dejamos que el sistema haga fit automáticamente; gestionamos insets.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
            val ime = insets.getInsets(android.view.WindowInsets.Type.ime())
            val nav = insets.getInsets(android.view.WindowInsets.Type.navigationBars())
            // Aplicar padding bottom al contenedor principal para evitar que el contenido quede oculto
            v.updatePadding(bottom = nav.bottom)
            // Ajustar padding del NavHostFragment (por si el nav host no ocupa todo)
            binding.navHostFragment?.let { it.updatePadding(bottom = nav.bottom) }
            // Ajustar padding de la bottom navigation (si se usa gestos, puede necesitar extra)
            binding.bottomNavigation.updatePadding(bottom = 0)
            insets
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
}
