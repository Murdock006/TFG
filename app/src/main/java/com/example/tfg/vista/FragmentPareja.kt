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
import com.google.android.material.card.MaterialCardView
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
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter

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
    private lateinit var cardInfoGrupo: MaterialCardView
    private lateinit var tvGroupEmoji: TextView
    private lateinit var btnCopiarCodigo: Button
    private lateinit var btnCompartirCodigo: Button
    private lateinit var pieChartTareas: com.github.mikephil.charting.charts.PieChart
    private lateinit var tvTareasCompletadas: TextView
    private lateinit var tvTareasPendientes: TextView

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
        cardInfoGrupo = rootView.findViewById(com.example.tfg.R.id.cardInfoGrupo)
        tvGroupEmoji = rootView.findViewById(com.example.tfg.R.id.tvGroupEmoji)
        btnCopiarCodigo = rootView.findViewById(com.example.tfg.R.id.btnCopiarCodigo)
        btnCompartirCodigo = rootView.findViewById(com.example.tfg.R.id.btnCompartirCodigo)
        pieChartTareas = rootView.findViewById(com.example.tfg.R.id.pieChartTareas)
        tvTareasCompletadas = rootView.findViewById(com.example.tfg.R.id.tvTareasCompletadas)
        tvTareasPendientes = rootView.findViewById(com.example.tfg.R.id.tvTareasPendientes)

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
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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

        // Selector de emoji
        tvGroupEmoji.setOnClickListener {
            val g = parejaVM.grupo.value ?: run { Toast.makeText(requireContext(), getString(com.example.tfg.R.string.no_hay_grupo_activo), Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            mostrarSelectorEmoji { nuevoEmoji ->
                parejaVM.actualizarEmojiGrupo(g.id, nuevoEmoji) { res ->
                    if (res.isSuccess) {
                        tvGroupEmoji.text = nuevoEmoji
                        Toast.makeText(requireContext(), "Emoji actualizado", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Error actualizando emoji", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Copiar código
        btnCopiarCodigo.setOnClickListener {
            val codigoTexto = tvCodigo.text.toString()
            if (codigoTexto.isBlank() || codigoTexto == "Código: -") {
                Toast.makeText(requireContext(), "Genera una invitación primero", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val codigo = codigoTexto.removePrefix("Código: ").trim()
            val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Código de invitación", codigo)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "Código copiado al portapapeles", Toast.LENGTH_SHORT).show()
        }

        // Compartir código
        btnCompartirCodigo.setOnClickListener {
            val codigoTexto = tvCodigo.text.toString()
            if (codigoTexto.isBlank() || codigoTexto == "Código: -") {
                Toast.makeText(requireContext(), "Genera una invitación primero", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val codigo = codigoTexto.removePrefix("Código: ").trim()
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, "¡Únete a mi grupo! Código de invitación: $codigo")
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Invitación de grupo")
            }
            startActivity(android.content.Intent.createChooser(intent, "Compartir código"))
        }

    }

    private fun mostrarSelectorEmoji(onEmojiSelected: (String) -> Unit) {
        val emojis = arrayOf("❤️", "🔥", "👫", "💑", "🏠", "🌟", "💕", "💖", "🎉", "🎊", "🌈", "✨")
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Selecciona un emoji")
        builder.setItems(emojis) { dialog, which ->
            onEmojiSelected(emojis[which])
            dialog.dismiss()
        }
        builder.setNegativeButton(getString(com.example.tfg.R.string.cancelar), null)
        builder.show()
    }

    private fun cargarYMostrarEstadisticasTareas(grupoId: String) {
        lifecycleScope.launch {
            try {
                val db = Firebase.firestore
                val tareasSnapshot = db.collection("tareas")
                    .whereEqualTo("grupoId", grupoId)
                    .get()
                    .await()

                val completadas = tareasSnapshot.documents.count { it.getString("estado") == "completada" || it.getString("estado") == "confirmada" }
                val pendientes = tareasSnapshot.documents.count { it.getString("estado") == "pendiente" }
                val total = completadas + pendientes

                withContext(Dispatchers.Main) {
                    tvTareasCompletadas.text = completadas.toString()
                    tvTareasPendientes.text = pendientes.toString()

                    // Configurar gráfico circular
                    if (total > 0) {
                        val entries = mutableListOf<PieEntry>()
                        if (completadas > 0) entries.add(PieEntry(completadas.toFloat(), "Completadas"))
                        if (pendientes > 0) entries.add(PieEntry(pendientes.toFloat(), "Pendientes"))

                        val dataSet = PieDataSet(entries, "")
                        dataSet.colors = listOf(
                            resources.getColor(com.example.tfg.R.color.exito, requireContext().theme),
                            resources.getColor(com.example.tfg.R.color.gris_claro, requireContext().theme)
                        )
                        dataSet.setSliceSpace(2f)
                        dataSet.setValueTextSize(12f)
                        dataSet.setValueTextColor(android.graphics.Color.WHITE)

                        val data = PieData(dataSet)
                        data.setValueFormatter(object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                return if (value > 0) value.toInt().toString() else ""
                            }
                        })

                        pieChartTareas.apply {
                            this.data = data
                            description.isEnabled = false
                            legend.isEnabled = false
                            animateY(1000)
                            invalidate()
                        }
                    } else {
                        // Sin tareas, mostrar un gráfico vacío o un mensaje
                        pieChartTareas.clear()
                        pieChartTareas.invalidate()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FragmentPareja", "cargarYMostrarEstadisticasTareas error", e)
            }
        }
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
            cardInfoGrupo.visibility = View.VISIBLE
            tvGroupName.visibility = View.VISIBLE
            tvGroupMembers.visibility = View.VISIBLE
            btnAbrirGrupo.visibility = View.VISIBLE
            btnEditarNombre.visibility = View.VISIBLE
            btnSalirGrupoTop.visibility = View.VISIBLE
            tvMiembrosTitulo.visibility = View.VISIBLE
            rvMiembros.visibility = View.VISIBLE
        } else {
            cardInfoGrupo.visibility = View.GONE
            tvGroupName.visibility = View.GONE
            tvGroupMembers.visibility = View.GONE
            btnAbrirGrupo.visibility = View.GONE
            btnEditarNombre.visibility = View.GONE
            btnSalirGrupoTop.visibility = View.GONE
            tvMiembrosTitulo.visibility = View.GONE
            rvMiembros.visibility = View.GONE
        }
    }

    private fun actualizarVistaGrupo(g: com.example.tfg.modelo.Grupo?) {
        if (g != null) {
            mostrarUIGrupo(true)
            tvGroupName.text = g.nombre
            tvGroupMembers.text = getString(com.example.tfg.R.string.miembros_format, g.miembros.size)
            tvGroupEmoji.text = g.emoji
            
            // Cargar estadísticas de tareas
            cargarYMostrarEstadisticasTareas(g.id)

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
            tvGroupEmoji.text = "❤️"
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
        // Almacenar también puntos por uid para mostrar en el badge
        private var puntosMap: Map<String,Int> = emptyMap()

        fun setItems(list: List<Pair<String,String>>) { items = list; notifyDataSetChanged() }
        fun setPuntosMap(map: Map<String,Int>) { puntosMap = map; notifyDataSetChanged() }

        inner class VH(val root: View) : RecyclerView.ViewHolder(root) {
            val tvAvatar: TextView   = root.findViewById(com.example.tfg.R.id.tvMiembroAvatar)
            val tvNombre: TextView   = root.findViewById(com.example.tfg.R.id.tvMiembroNombre)
            val tvEmail: TextView    = root.findViewById(com.example.tfg.R.id.tvMiembroEmail)
            val tvPuntos: TextView   = root.findViewById(com.example.tfg.R.id.tvMiembroPuntos)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = android.view.LayoutInflater.from(parent.context)
                .inflate(com.example.tfg.R.layout.item_miembro, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (nombreCompleto, rol) = items[position]
            // nombreCompleto tiene formato "Nombre (email)" o solo email/uid
            val partes = nombreCompleto.split("(")
            val nombre = partes[0].trim()
            val email  = if (partes.size > 1) partes[1].trimEnd(')').trim() else ""
            val inicial = nombre.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            holder.tvAvatar.text  = inicial
            holder.tvNombre.text  = nombre.ifBlank { email }
            holder.tvEmail.text   = if (email.isNotBlank()) "$email • $rol" else rol
            holder.tvPuntos.text  = "0 pts"  // valor por defecto; se actualizará con puntosMap si se pasa
        }

        override fun getItemCount(): Int = items.size
    }
}
