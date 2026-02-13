package com.example.tfg.vista

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tfg.modelo.Usuario
import com.example.tfg.service.LocalizadorServicios
import androidx.appcompat.app.AlertDialog
import com.example.tfg.viewmodel.ParejaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import android.util.TypedValue

class FragmentPareja : Fragment() {

    private val parejaVM: ParejaViewModel by activityViewModels()
    private lateinit var adapter: MiembrosAdapter

    // Vistas (reemplazan binding)
    private lateinit var rootView: View
    private lateinit var rvMiembros: RecyclerView
    private lateinit var bottomContainer: FrameLayout
    private lateinit var bottomBar: CardView
    private lateinit var btnSalirGrupoTop: Button
    private lateinit var tvTusPuntos: TextView
    private lateinit var tvPuntosReservados: TextView
    private lateinit var tvPuntosCompanero: TextView
    private lateinit var tvNombreGrupoSmall: TextView
    private lateinit var btnCrearGrupo: Button
    private lateinit var btnGenerarInvitacion: Button
    private lateinit var tvCodigo: TextView
    private lateinit var etCodigoAceptar: EditText
    private lateinit var btnAceptarInvitacion: Button
    private lateinit var btnAbrirGrupo: Button
    private lateinit var btnEditarNombre: Button
    private lateinit var tvGroupName: TextView
    private lateinit var tvGroupMembers: TextView
    private lateinit var tvMiembrosTitulo: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        rootView = inflater.inflate(com.example.tfg.R.layout.fragment_pareja, container, false)

        // Inicializar vistas con findViewById
        rvMiembros = rootView.findViewById(com.example.tfg.R.id.rvMiembros)
        bottomContainer = rootView.findViewById(com.example.tfg.R.id.bottomContainer)
        bottomBar = rootView.findViewById(com.example.tfg.R.id.bottomBar)
        btnSalirGrupoTop = rootView.findViewById(com.example.tfg.R.id.btnSalirGrupoTop)
        tvTusPuntos = rootView.findViewById(com.example.tfg.R.id.tvTusPuntos)
        tvPuntosReservados = rootView.findViewById(com.example.tfg.R.id.tvPuntosReservados)
        tvPuntosCompanero = rootView.findViewById(com.example.tfg.R.id.tvPuntosCompanero)
        tvNombreGrupoSmall = rootView.findViewById(com.example.tfg.R.id.tvNombreGrupoSmall)
        btnCrearGrupo = rootView.findViewById(com.example.tfg.R.id.btnCrearGrupo)
        btnGenerarInvitacion = rootView.findViewById(com.example.tfg.R.id.btnGenerarInvitacion)
        tvCodigo = rootView.findViewById(com.example.tfg.R.id.tvCodigo)
        etCodigoAceptar = rootView.findViewById(com.example.tfg.R.id.etCodigoAceptar)
        btnAceptarInvitacion = rootView.findViewById(com.example.tfg.R.id.btnAceptarInvitacion)
        btnAbrirGrupo = rootView.findViewById(com.example.tfg.R.id.btnAbrirGrupo)
        btnEditarNombre = rootView.findViewById(com.example.tfg.R.id.btnEditarNombre)
        tvGroupName = rootView.findViewById(com.example.tfg.R.id.tvGroupName)
        tvGroupMembers = rootView.findViewById(com.example.tfg.R.id.tvGroupMembers)
        tvMiembrosTitulo = rootView.findViewById(com.example.tfg.R.id.tvMiembrosTitulo)

        return rootView
    }

    override fun onResume() {
        super.onResume()
        // Forzar refresco de usuarios y grupo al volver a primer plano
        lifecycleScope.launch {
            try {
                val listado = LocalizadorServicios.repositorioAuth.observarUsuarios().first()
                android.util.Log.d("FragmentPareja", "onResume: usuarios cargados=${listado.size}")
                // actualizar vista si hay grupo
                val g = parejaVM.grupo.value
                if (g != null) {
                    actualizarListaMiembrosConUsuarios(g, listado)
                }
            } catch (e: Exception) {
                android.util.Log.w("FragmentPareja", "onResume: fallo cargando usuarios: ${e.message}")
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        android.util.Log.d("FragmentPareja", "onViewCreated: cargado")

        // Setup RecyclerView
        rvMiembros.layoutManager = LinearLayoutManager(requireContext())
        adapter = MiembrosAdapter()
        rvMiembros.adapter = adapter

        // Asegurar visibilidad inicial oculta para evitar solapamientos antes de cargar estado
        bottomContainer.visibility = View.GONE
        bottomBar.visibility = View.GONE
        btnSalirGrupoTop.visibility = View.GONE

        // Forzar carga del grupo asociado al usuario si estamos logueados (asegura que parejaVM emitirá estado)
        LocalizadorServicios.repositorioAuth.usuarioActual()?.id?.let { uid ->
            parejaVM.cargarGrupoPorUsuario(uid)
            // actualizar vista inmediatamente con estado actual (por si ya está cargado en vm)
            actualizarVistaGrupo(parejaVM.grupo.value)
        }

        // Valores por defecto visibles para evitar pantallas vacías
        tvTusPuntos.text = "0"
        tvPuntosReservados.text = getString(com.example.tfg.R.string.reservados_format, 0)
        tvPuntosCompanero.text = getString(com.example.tfg.R.string.cero)
        tvNombreGrupoSmall.text = getString(com.example.tfg.R.string.guion)

        // Observar usuarios y grupo para mapear uid -> nombre y actualizar puntos
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    LocalizadorServicios.repositorioAuth.observarUsuarios().collect { lista ->
                        android.util.Log.d("FragmentPareja", "observarUsuarios: recibidos ${lista.size} usuarios")
                        // actualizar miembros si hay grupo
                        val g = parejaVM.grupo.value
                        if (g != null) {
                            actualizarListaMiembrosConUsuarios(g, lista)
                        }

                        // actualizar puntos del propio usuario
                        val myId = LocalizadorServicios.repositorioAuth.usuarioActual()?.id
                        android.util.Log.d("FragmentPareja", "usuarioActual id=$myId")
                        val yo = lista.find { it.id == myId }
                        withContext(Dispatchers.Main) {
                            tvTusPuntos.text = (yo?.puntos ?: 0).toString()
                            tvPuntosReservados.text = getString(com.example.tfg.R.string.reservados_format, yo?.puntosReservados ?: 0)
                        }

                        // si hay grupo, actualizar puntos del compañero (primer distinto)
                        val g2 = parejaVM.grupo.value
                        android.util.Log.d("FragmentPareja", "grupo en observador usuarios = ${g2?.id}")
                        if (g2 != null) {
                            val otroUid = g2.miembros.keys.firstOrNull { it != myId }
                            val otro = otroUid?.let { uid -> lista.find { it.id == uid } }
                            withContext(Dispatchers.Main) {
                                tvPuntosCompanero.text = (otro?.puntos ?: 0).toString()
                                tvNombreGrupoSmall.text = g2.nombre ?: getString(com.example.tfg.R.string.guion)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                tvPuntosCompanero.text = getString(com.example.tfg.R.string.cero)
                                tvNombreGrupoSmall.text = getString(com.example.tfg.R.string.guion)
                            }
                        }
                    }
                }
                launch {
                    parejaVM.grupo.collect { g ->
                        android.util.Log.d("FragmentPareja", "parejaVM.grupo.collect -> grupo=${g?.id}")
                        actualizarVistaGrupo(g)
                    }
                }
            }
        }

        btnCrearGrupo.setOnClickListener {
            // pedir nombre y crear grupo
            val et = EditText(requireContext())
            AlertDialog.Builder(requireContext())
                .setTitle(getString(com.example.tfg.R.string.crear_grupo_title))
                .setView(et)
                .setPositiveButton(getString(com.example.tfg.R.string.crear)) { _, _ ->
                    val nombre = et.text.toString().trim().ifEmpty { "Mi grupo" }
                    val usuarioId = LocalizadorServicios.repositorioAuth.usuarioActual()?.id ?: return@setPositiveButton
                    btnCrearGrupo.isEnabled = false
                    parejaVM.crearGrupo(nombre, usuarioId) { res ->
                        btnCrearGrupo.isEnabled = true
                        if (res.isSuccess) {
                            Toast.makeText(requireContext(), "Grupo creado", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setNegativeButton(getString(com.example.tfg.R.string.cancelar), null)
                .show()
        }

        btnGenerarInvitacion.setOnClickListener {
            val grupo = parejaVM.grupo.value
            val usuarioId = LocalizadorServicios.repositorioAuth.usuarioActual()?.id ?: return@setOnClickListener
            if (grupo == null) { Toast.makeText(requireContext(), getString(com.example.tfg.R.string.no_hay_grupo_activo), Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            parejaVM.crearInvitacion(grupo.id, usuarioId, null) { invRes ->
                if (invRes.isSuccess) {
                    val codigo = invRes.getOrNull()
                    tvCodigo.text = getString(com.example.tfg.R.string.grupo_creado_codigo, codigo ?: grupo.id)
                    Toast.makeText(requireContext(), getString(com.example.tfg.R.string.grupo_creado_codigo, codigo ?: grupo.id), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), invRes.exceptionOrNull()?.message ?: "Error creando invitación", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnAceptarInvitacion.setOnClickListener {
            val codigo = etCodigoAceptar.text.toString().trim()
            val usuarioId = LocalizadorServicios.repositorioAuth.usuarioActual()?.id ?: return@setOnClickListener
            if (codigo.isEmpty()) { Toast.makeText(requireContext(), getString(com.example.tfg.R.string.introduce_codigo), Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            parejaVM.aceptarInvitacionPorCodigo(codigo, usuarioId) { res ->
                if (res.isSuccess) {
                    Toast.makeText(requireContext(), "Invitación aceptada", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "No se pudo aceptar la invitación: código no encontrado o error.", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Abrir detalles del grupo
        btnAbrirGrupo.setOnClickListener {
            val g = parejaVM.grupo.value
            if (g == null) { Toast.makeText(requireContext(), getString(com.example.tfg.R.string.no_hay_grupo_activo), Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            viewLifecycleOwner.lifecycleScope.launch {
                val usuariosCacheLocal = try { LocalizadorServicios.repositorioAuth.observarUsuarios().first() } catch (_: Exception) { emptyList<Usuario>() }
                val miembrosTexto = g.miembros.map { (uid, rol) ->
                    val nombre = usuariosCacheLocal.find { it.id == uid }?.nombre ?: usuariosCacheLocal.find { it.id == uid }?.email ?: uid
                    "- $nombre ($rol)"
                }.joinToString("\n")
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(com.example.tfg.R.string.miembros_del_grupo))
                    .setMessage("Nombre: ${g.nombre}\nMiembros (${g.miembros.size}):\n$miembrosTexto")
                    .setPositiveButton(getString(com.example.tfg.R.string.aceptar), null)
                    .show()
            }
        }

        // Salir del grupo
        val salirHandler = View.OnClickListener {
            val usuarioId = LocalizadorServicios.repositorioAuth.usuarioActual()?.id ?: return@OnClickListener
            salirDelGrupo(usuarioId, adapter)
        }
        btnSalirGrupoTop.setOnClickListener(salirHandler)

        // Guardar nombre
        val guardarHandler = View.OnClickListener {
            val g = parejaVM.grupo.value ?: run { Toast.makeText(requireContext(), getString(com.example.tfg.R.string.no_hay_grupo_activo), Toast.LENGTH_SHORT).show(); return@OnClickListener }
            val et = EditText(requireContext())
            et.setText(g.nombre ?: "")
            AlertDialog.Builder(requireContext())
                .setTitle(getString(com.example.tfg.R.string.editar_nombre_grupo))
                .setView(et)
                .setPositiveButton(getString(com.example.tfg.R.string.guardar)) { _, _ ->
                    val nuevo = et.text.toString().trim().ifEmpty { g.nombre ?: "Mi grupo" }
                    parejaVM.actualizarNombreGrupo(g.id, nuevo) { res ->
                        if (res.isSuccess) Toast.makeText(requireContext(), getString(com.example.tfg.R.string.nombre_actualizado), Toast.LENGTH_SHORT).show()
                        else Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton(getString(com.example.tfg.R.string.cancelar), null)
                .show()
        }
        btnEditarNombre.setOnClickListener(guardarHandler)

    }

    private fun salirDelGrupo(usuarioId: String, adapter: MiembrosAdapter) {
        AlertDialog.Builder(requireContext())
            .setTitle("Salir del grupo")
            .setMessage("¿Estás seguro de que quieres salir del grupo?")
            .setPositiveButton(getString(com.example.tfg.R.string.aceptar)) { _, _ ->
                // optimista: ocultar UI inmediatamente para evitar confusión
                mostrarUIGrupo(false)
                tvGroupName.text = getString(com.example.tfg.R.string.guion)
                tvGroupMembers.text = getString(com.example.tfg.R.string.miembros_format, 0)
                adapter.setItems(emptyList())

                parejaVM.salirGrupo(usuarioId) { res ->
                    if (res.isSuccess) {
                        Toast.makeText(requireContext(), "Has salido del grupo", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Error saliendo del grupo", Toast.LENGTH_LONG).show()
                        parejaVM.cargarGrupoPorUsuario(usuarioId)
                    }
                }
            }
            .setNegativeButton(getString(com.example.tfg.R.string.cancelar), null)
            .show()
    }

    private fun mostrarUIGrupo(activo: Boolean) {
        if (activo) {
            // mostrar info del grupo visible y RecyclerView miembros
            tvGroupName.visibility = View.VISIBLE
            tvGroupMembers.visibility = View.VISIBLE
            btnAbrirGrupo.visibility = View.VISIBLE
            btnEditarNombre.visibility = View.VISIBLE
            btnSalirGrupoTop.visibility = View.VISIBLE
            tvMiembrosTitulo.visibility = View.VISIBLE
            rvMiembros.visibility = View.VISIBLE
        } else {
            tvGroupName.visibility = View.GONE
            tvGroupMembers.visibility = View.GONE
            btnAbrirGrupo.visibility = View.GONE
            btnEditarNombre.visibility = View.GONE
            btnSalirGrupoTop.visibility = View.VISIBLE
            tvMiembrosTitulo.visibility = View.GONE
            rvMiembros.visibility = View.GONE
        }
    }

    private fun actualizarVistaGrupo(g: com.example.tfg.modelo.Grupo?) {
        if (g != null) {
            mostrarUIGrupo(true)
            tvGroupName.text = g.nombre
            tvGroupMembers.text = getString(com.example.tfg.R.string.miembros_format, g.miembros.size)

            viewLifecycleOwner.lifecycleScope.launch {
                val usuarios = try { LocalizadorServicios.repositorioAuth.observarUsuarios().first() } catch (_: Exception) { emptyList<Usuario>() }
                val myId = LocalizadorServicios.repositorioAuth.usuarioActual()?.id
                val items = resolverNombresMiembros(g.miembros, usuarios)
                withContext(Dispatchers.Main) {
                    adapter.setItems(items)
                    val otroUid = g.miembros.keys.firstOrNull { it != myId }
                    val otro = otroUid?.let { uid -> usuarios.find { it.id == uid } }
                    tvPuntosCompanero.text = (otro?.puntos ?: 0).toString()
                    tvNombreGrupoSmall.text = g.nombre
                }

            }

        } else {
            mostrarUIGrupo(false)
            tvGroupName.text = getString(com.example.tfg.R.string.guion)
            tvGroupMembers.text = getString(com.example.tfg.R.string.miembros_format, 0)
            adapter.setItems(emptyList())
        }
    }

    private fun actualizarListaMiembrosConUsuarios(g: com.example.tfg.modelo.Grupo, usuarios: List<Usuario>) {
        val myId = LocalizadorServicios.repositorioAuth.usuarioActual()?.id
        lifecycleScope.launch {
            val items = try { resolverNombresMiembros(g.miembros, usuarios) } catch (e: Exception) { g.miembros.map { (uid, rol) -> uid to rol } }
            withContext(Dispatchers.Main) {
                adapter.setItems(items)
                tvGroupName.text = g.nombre
                tvGroupMembers.text = getString(com.example.tfg.R.string.miembros_format, g.miembros.size)
                val otroUid = g.miembros.keys.firstOrNull { it != myId }
                val otro = otroUid?.let { uid -> usuarios.find { it.id == uid } }
                tvPuntosCompanero.text = (otro?.puntos ?: 0).toString()
                tvNombreGrupoSmall.text = g.nombre
                mostrarUIGrupo(true)
            }
        }
    }

    private suspend fun resolverNombresMiembros(miembros: Map<String,String>, usuariosCache: List<Usuario>): List<Pair<String,String>> {
        val db = Firebase.firestore
        val result = mutableListOf<Pair<String,String>>()
        for ((uid, rol) in miembros) {
            val u = usuariosCache.find { it.id == uid }
            if (u != null) {
                val display = if (u.nombre.isNotBlank()) "${u.nombre} (${u.email})" else (u.email.ifEmpty { u.id })
                result.add(display to rol)
            } else {
                try {
                    val doc = db.collection("usuarios").document(uid).get().await()
                    if (doc.exists()) {
                        val nombre = doc.getString("nombre") ?: ""
                        val email = doc.getString("email") ?: ""
                        val display = if (nombre.isNotBlank()) "$nombre ($email)" else (email.ifEmpty { uid })
                        result.add(display to rol)
                    } else {
                        result.add(uid to rol)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("FragmentPareja", "resolverNombresMiembros: fallo leyendo usuarios/$uid: ${e.message}")
                    result.add(uid to rol)
                }
            }
        }
        return result
    }

    private inner class MiembrosAdapter : RecyclerView.Adapter<MiembrosAdapter.VH>() {
        private var items: List<Pair<String,String>> = emptyList()
        fun setItems(list: List<Pair<String,String>>) { items = list; notifyDataSetChanged() }
        inner class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = TextView(parent.context)
            val pad = (16 * parent.context.resources.displayMetrics.density).toInt()
            tv.setPadding(pad,pad,pad,pad)
            return VH(tv)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val (nombre, rol) = items[position]
            holder.tv.text = "$nombre — $rol"
        }
        override fun getItemCount(): Int = items.size
    }
}
