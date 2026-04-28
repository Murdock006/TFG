package com.example.tfg.vista

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.tfg.R
import com.example.tfg.modelo.Usuario
import com.example.tfg.service.LocalizadorServicios
import com.example.tfg.viewmodel.AvatarViewModel
import com.example.tfg.viewmodel.ParejaViewModel
import com.example.tfg.viewmodel.VistaModeloAuth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FragmentPerfil : Fragment() {

    private val parejaVM: ParejaViewModel by activityViewModels()
    private val vistaModeloAuth: VistaModeloAuth by viewModels()
    private val avatarVM: AvatarViewModel by viewModels()

    private lateinit var tvInfo: TextView
    private lateinit var btnEliminarCuenta: Button
    private lateinit var ivAvatarPerfil: ImageView
    private lateinit var btnSeleccionarAvatar: Button
    private lateinit var pbCargandoAvatar: ProgressBar
    private lateinit var tvErrorAvatar: TextView

    // Launcher para seleccionar imagen de la galería
    private val seleccionarImagenLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { imageUri ->
        if (imageUri != null) {
            // Mostrar preview mientras se sube
            Glide.with(this)
                .load(imageUri)
                .circleCrop()
                .into(ivAvatarPerfil)
            
            // Subir avatar a Firebase
            avatarVM.subirAvatar(imageUri)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_perfil, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvInfo = view.findViewById(R.id.tv_perfil_info)
        btnEliminarCuenta = view.findViewById(R.id.btnEliminarCuenta)
        ivAvatarPerfil = view.findViewById(R.id.ivAvatarPerfil)
        btnSeleccionarAvatar = view.findViewById(R.id.btnSeleccionarAvatar)
        pbCargandoAvatar = view.findViewById(R.id.pbCargandoAvatar)
        tvErrorAvatar = view.findViewById(R.id.tvErrorAvatar)

        mostrarUsuarioInicial()
        observarUsuarioYGrupo()
        configurarAvatarUI()
        configurarEliminacionCuenta()
        observarResultadoEliminacionCuenta()
        
        // Cargar avatar actual al iniciar
        avatarVM.cargarAvatarActual()
    }

    private fun construirInfoUsuario(usuarioId: String?, lista: List<Usuario>?): String {
        if (usuarioId == null) return "No hay usuario autenticado"
        val u = lista?.find { it.id == usuarioId }
        return if (u != null) {
            "Nombre: ${u.nombre}\nEmail: ${u.email}\nPuntos: ${u.puntos}\nPuntos reservados: ${u.puntosReservados}\nRacha: ${u.rachaDias}"
        } else {
            val usuarioLocal = LocalizadorServicios.repositorioAuth.usuarioActual()
            if (usuarioLocal != null) {
                "Nombre: ${usuarioLocal.nombre}\nEmail: ${usuarioLocal.email}\nPuntos: ${usuarioLocal.puntos}"
            } else {
                "No hay usuario autenticado"
            }
        }
    }

    private fun mostrarUsuarioInicial() {
        val usuarioInicial = LocalizadorServicios.repositorioAuth.usuarioActual()
        tvInfo.text = if (usuarioInicial != null) {
            "Nombre: ${usuarioInicial.nombre}\nEmail: ${usuarioInicial.email}\nPuntos: ${usuarioInicial.puntos}"
        } else {
            "No hay usuario autenticado"
        }
    }

    private fun observarUsuarioYGrupo() {
        // Datos del usuario actual
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                LocalizadorServicios.repositorioAuth.observarUsuarios().collect { lista ->
                    val id = LocalizadorServicios.repositorioAuth.usuarioActual()?.id ?: return@collect
                    val baseUsuario = construirInfoUsuario(id, lista)
                    tvInfo.text = baseUsuario
                }
            }
        }

        // Datos del grupo activo
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                parejaVM.grupo.collect { g ->
                    val listaUsuarios = try {
                        LocalizadorServicios.repositorioAuth.observarUsuarios().first()
                    } catch (_: Exception) {
                        emptyList<Usuario>()
                    }
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
                        tvInfo.text = baseUsuario
                    }
                }
            }
        }
    }

    private fun configurarAvatarUI() {
        // Click en botón para seleccionar imagen
        btnSeleccionarAvatar.setOnClickListener {
            seleccionarImagenLauncher.launch("image/*")
        }

        // Observar carga del avatar
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                avatarVM.cargando.collect { cargando ->
                    pbCargandoAvatar.visibility = if (cargando) View.VISIBLE else View.GONE
                    btnSeleccionarAvatar.isEnabled = !cargando
                }
            }
        }

        // Observar resultado de subida
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                avatarVM.avatarState.collect { resultado ->
                    if (resultado == null) return@collect

                    if (resultado.isSuccess) {
                        tvErrorAvatar.visibility = View.GONE
                        Toast.makeText(requireContext(), "Avatar actualizado ✓", Toast.LENGTH_SHORT).show()
                        avatarVM.resetAvatarState()
                    } else {
                        val msg = resultado.exceptionOrNull()?.message ?: "Error desconocido"
                        tvErrorAvatar.text = msg
                        tvErrorAvatar.visibility = View.VISIBLE
                        Toast.makeText(requireContext(), "Error: $msg", Toast.LENGTH_LONG).show()
                        avatarVM.resetAvatarState()
                    }
                }
            }
        }

        // Observar URL del avatar actual para mostrar en pantalla
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                avatarVM.avatarUrlActual.collect { url ->
                    if (url != null) {
                        Glide.with(this@FragmentPerfil)
                            .load(url)
                            .circleCrop()
                            .placeholder(R.drawable.perfil)
                            .into(ivAvatarPerfil)
                    } else {
                        Glide.with(this@FragmentPerfil).clear(ivAvatarPerfil)
                        ivAvatarPerfil.setImageResource(R.drawable.perfil)
                    }
                }
            }
        }
    }

    private fun configurarEliminacionCuenta() {
        btnEliminarCuenta.setOnClickListener {
            mostrarDialogoConfirmacionEliminacion()
        }
    }

    private fun mostrarDialogoConfirmacionEliminacion() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.eliminar_cuenta_hint)
            setSingleLine(true)
        }

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(
                input,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.eliminar_cuenta_titulo))
            .setMessage(getString(R.string.eliminar_cuenta_mensaje) + "\n\n" + getString(R.string.eliminar_cuenta_confirmacion))
            .setView(container)
            .setNegativeButton(getString(R.string.eliminar_cuenta_cancelar), null)
            .setPositiveButton(getString(R.string.eliminar_cuenta_confirmar), null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val texto = input.text?.toString()?.trim() ?: ""
                if (texto != "ELIMINAR") {
                    Toast.makeText(requireContext(), getString(R.string.eliminar_cuenta_texto_incorrecto), Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                btnEliminarCuenta.isEnabled = false
                vistaModeloAuth.eliminarCuentaActual()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun observarResultadoEliminacionCuenta() {
        vistaModeloAuth.eliminacionCuenta.observe(viewLifecycleOwner) { resultado ->
            if (resultado == null) return@observe

            btnEliminarCuenta.isEnabled = true

            if (resultado.isSuccess) {
                limpiarEstadoLocalPostEliminacion()
                Toast.makeText(requireContext(), getString(R.string.eliminar_cuenta_ok), Toast.LENGTH_LONG).show()
                navegarALoginLimpiandoBackstack()
            } else {
                val msg = resultado.exceptionOrNull()?.message
                    ?: getString(R.string.eliminar_cuenta_requiere_login_reciente)
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            }

            vistaModeloAuth.resetEliminacionCuentaState()
        }
    }

    private fun limpiarEstadoLocalPostEliminacion() {
        try {
            requireContext()
                .getSharedPreferences("tfg_prefs", Context.MODE_PRIVATE)
                .edit()
                .remove("grupoId")
                .apply()
        } catch (_: Exception) {
        }
        tvInfo.text = "No hay usuario autenticado"
    }

    private fun navegarALoginLimpiandoBackstack() {
        val opciones = NavOptions.Builder()
            .setPopUpTo(R.id.fragment_Presentacion, true)
            .build()
        findNavController().navigate(R.id.fragment_Login, null, opciones)
    }
}
