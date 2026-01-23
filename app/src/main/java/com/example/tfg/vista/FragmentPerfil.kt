package com.example.tfg.vista

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.tfg.service.LocalizadorServicios
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class FragmentPerfil : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(com.example.tfg.R.layout.fragment_perfil, container, false)
        val tvInfo = root.findViewById<TextView>(com.example.tfg.R.id.tv_perfil_info)

        // Rellenar con el usuario actual (si está disponible)
        val usuario = LocalizadorServicios.repositorioAuth.usuarioActual()
        if (usuario != null) {
            tvInfo.text = "Nombre: ${usuario.nombre}\nEmail: ${usuario.email}\nPuntos: ${usuario.puntos}"
        }

        // Observar cambios en usuarios para refrescar datos del usuario actual
        viewLifecycleOwner.lifecycleScope.launch {
            LocalizadorServicios.repositorioAuth.observarUsuarios().collect { lista ->
                val id = LocalizadorServicios.repositorioAuth.usuarioActual()?.id ?: return@collect
                val u = lista.find { it.id == id }
                if (u != null) {
                    tvInfo.text = "Nombre: ${u.nombre}\nEmail: ${u.email}\nPuntos: ${u.puntos}\nPuntos reservados: ${u.puntosReservados}\nRacha: ${u.rachaDias}"
                }
            }
        }

        return root
    }
}
