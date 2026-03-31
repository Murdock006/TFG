package com.example.tfg.vista

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.tfg.service.LocalizadorServicios
import com.example.tfg.viewmodel.ParejaViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FragmentPerfil : Fragment() {

    private val parejaVM: ParejaViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(com.example.tfg.R.layout.fragment_perfil, container, false)
        val tvInfo = root.findViewById<TextView>(com.example.tfg.R.id.tv_perfil_info)

        // Función auxiliar para construir texto de usuario
        fun construirInfoUsuario(usuarioId: String?, lista: List<com.example.tfg.modelo.Usuario>?): String {
            if (usuarioId == null) return "No hay usuario autenticado"
            val u = lista?.find { it.id == usuarioId }
            return if (u != null) {
                "Nombre: ${u.nombre}\nEmail: ${u.email}\nPuntos: ${u.puntos}\nPuntos reservados: ${u.puntosReservados}\nRacha: ${u.rachaDias}"
            } else {
                val usuarioLocal = LocalizadorServicios.repositorioAuth.usuarioActual()
                if (usuarioLocal != null) "Nombre: ${usuarioLocal.nombre}\nEmail: ${usuarioLocal.email}\nPuntos: ${usuarioLocal.puntos}" else "No hay usuario autenticado"
            }
        }

        // Inicializar con usuario actual (rápido)
        val usuarioInicial = LocalizadorServicios.repositorioAuth.usuarioActual()
        tvInfo.text = if (usuarioInicial != null) "Nombre: ${usuarioInicial.nombre}\nEmail: ${usuarioInicial.email}\nPuntos: ${usuarioInicial.puntos}" else "No hay usuario autenticado"

        // Observar cambios en usuarios para refrescar datos del usuario actual (actualiza solo la parte de usuario)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                LocalizadorServicios.repositorioAuth.observarUsuarios().collect { lista ->
                    val id = LocalizadorServicios.repositorioAuth.usuarioActual()?.id ?: return@collect
                    val baseUsuario = construirInfoUsuario(id, lista)
                    // Si ya hay grupo mostrado, mantendremos su info (se actualiza en el siguiente bloque)
                    tvInfo.text = baseUsuario
                }
            }
        }

        // Observar grupo activo y mostrar información del grupo en el perfil
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                parejaVM.grupo.collect { g ->
                // obtener snapshot de usuarios una vez
                val listaUsuarios = try { LocalizadorServicios.repositorioAuth.observarUsuarios().first() } catch (_: Exception) { emptyList<com.example.tfg.modelo.Usuario>() }
                val usuarioId = LocalizadorServicios.repositorioAuth.usuarioActual()?.id
                val baseUsuario = construirInfoUsuario(usuarioId, listaUsuarios)

                if (g != null) {
                    val miembrosTexto = g.miembros.map { (uid, rol) ->
                        val nombre = listaUsuarios.find { it.id == uid }?.nombre ?: uid
                        "- $nombre ($rol)"
                    }.joinToString("\n")
                    val groupInfo = "\n\nGrupo activo:\nNombre: ${g.nombre}\nMiembros (${g.miembros.size}):\n$miembrosTexto"
                    tvInfo.text = baseUsuario + groupInfo
                } else {
                    // solo usuario
                    tvInfo.text = baseUsuario
                }
                }
            }
        }

        return root
    }
}
