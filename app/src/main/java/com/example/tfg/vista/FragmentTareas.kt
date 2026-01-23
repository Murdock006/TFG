package com.example.tfg.vista

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tfg.databinding.FragmentTareasListaBinding
import com.example.tfg.databinding.FragmentTareasCrearBinding
import com.example.tfg.modelo.Tarea
import com.example.tfg.viewmodel.TareasViewModel
import com.example.tfg.viewmodel.ParejaViewModel
import com.example.tfg.service.LocalizadorServicios
import com.example.tfg.service.NotificationScheduler
import com.example.tfg.repositorio.RepositorioDisputas
import com.example.tfg.modelo.Disputa
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class FragmentTareas : Fragment() {

    private var listaBinding: FragmentTareasListaBinding? = null
    private var crearBinding: FragmentTareasCrearBinding? = null
    private val tareasVM: TareasViewModel by viewModels()
    private val parejaVM: ParejaViewModel by activityViewModels()
    private val repoDisputas = RepositorioDisputas()
    private var pickImageLauncher: ActivityResultLauncher<String>? = null
    private var pendingTareaParaDisputa: Tarea? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Por simplicidad: si se indica argumento "modo" crudo = "crear" o "lista" decidimos qué vista inflar
        val modo = arguments?.getString("modo") ?: "lista"
        return if (modo == "crear") {
            crearBinding = FragmentTareasCrearBinding.inflate(inflater, container, false)
            crearBinding!!.root
        } else {
            listaBinding = FragmentTareasListaBinding.inflate(inflater, container, false)
            listaBinding!!.root
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // registrar launcher para seleccionar imagen (se reutiliza para cada reclamo)
        pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            // uri puede ser nulo si el usuario cancela
            viewLifecycleOwner.lifecycleScope.launch {
                val tarea = pendingTareaParaDisputa
                if (tarea == null) {
                    Toast.makeText(requireContext(), "No hay tarea para reclamar", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val pruebasUrls = mutableListOf<String>()
                if (uri != null) {
                    try {
                        val subida = repoDisputas.subirFotoDisputa(tarea.id, uri.toString())
                        if (subida.isSuccess) {
                            pruebasUrls.add(subida.getOrNull()!!)
                        }
                    } catch (e: Exception) {
                        // ignore upload errors, pero informamos
                        Toast.makeText(requireContext(), "Error subiendo evidencia: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                // crear disputa en Firestore
                try {
                    val iniciador = LocalizadorServicios.repositorioAuth.usuarioActual()?.id ?: ""
                    val disputa = Disputa(id = "", tareaId = tarea.id, iniciador = iniciador, estado = "abierta", pruebas = pruebasUrls, fechaCreacion = com.google.firebase.Timestamp.now())
                    val res = repoDisputas.abrirDisputa(disputa)
                    if (res.isSuccess) {
                        Toast.makeText(requireContext(), "Disputa creada", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Error creando disputa", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Error creando disputa: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                pendingTareaParaDisputa = null
            }
        }

        // Configurar según binding
        listaBinding?.let { b ->
            b.rvTareas.layoutManager = LinearLayoutManager(requireContext())
            val adapter = TareasAdapter()
            b.rvTareas.adapter = adapter

            val repo = LocalizadorServicios.repositorioTarea
            // observar las tareas desde LocalizadorServicios (inmemory) si está disponible
            val repoFlow = repo.observarTareas()
            // subscribir desde lifecycle usando repeatOnLifecycle
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    repoFlow.collect { list ->
                        adapter.setItems(list)
                    }
                }
            }
        }

        crearBinding?.let { b ->
            // poblar categorías
            val categorias = listOf("Cocina", "Limpieza", "Ropa", "Mascotas", "Recados", "Personalizada")
            val adaptCat = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categorias)
            adaptCat.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            b.spCategoria.adapter = adaptCat

            // poblar dificultad
            val dificultades = listOf("Fácil", "Media", "Difícil")
            val adaptDif = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, dificultades)
            adaptDif.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            b.spDificultad.adapter = adaptDif

            // poblar asignarA con miembros del grupo (monitorizar grupo y usuarios)
            var usuariosCache: List<com.example.tfg.modelo.Usuario> = emptyList()
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    LocalizadorServicios.repositorioAuth.observarUsuarios().collect { list -> usuariosCache = list }
                }
            }
            // preparar adapter para miembros
            val adaptMiembros = android.widget.ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, mutableListOf<String>())
            adaptMiembros.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            b.spAsignarA.adapter = adaptMiembros

            // observar el grupo compartido para actualizar los miembros disponibles
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    parejaVM.grupo.collect { g ->
                        val items = mutableListOf<String>()
                        items.add("Sin asignar")
                        g?.miembros?.forEach { (uid, rol) ->
                            val nombre = usuariosCache.find { it.id == uid }?.nombre ?: uid
                            items.add("${nombre} (${uid})")
                        }
                        adaptMiembros.clear()
                        adaptMiembros.addAll(items)
                        adaptMiembros.notifyDataSetChanged()
                    }
                }
            }

            // Fecha
            var fechaProgramadaTs: com.google.firebase.Timestamp? = null
            b.btnElegirFecha.setOnClickListener {
                val hoy = java.util.Calendar.getInstance()
                val dp = android.app.DatePickerDialog(requireContext(), { _, year, month, day ->
                    val cal = java.util.Calendar.getInstance()
                    cal.set(year, month, day, 12, 0, 0)
                    fechaProgramadaTs = com.google.firebase.Timestamp(cal.time)
                    b.tvFechaProgramada.text = "Fecha: ${day}/${month+1}/${year}"
                }, hoy.get(java.util.Calendar.YEAR), hoy.get(java.util.Calendar.MONTH), hoy.get(java.util.Calendar.DAY_OF_MONTH))
                dp.show()
            }

            b.btnCrear.setOnClickListener {
                val titulo = b.etTitulo.text.toString().trim()
                val puntos = b.etPuntos.text.toString().toIntOrNull() ?: 0
                if (titulo.isEmpty()) { Toast.makeText(requireContext(), "Introduce título", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                val creadorId = LocalizadorServicios.repositorioAuth.usuarioActual()?.id
                val categoria = b.spCategoria.selectedItem as String
                val dificultadStr = b.spDificultad.selectedItem as String
                val dificultad = when(dificultadStr) { "Fácil" -> 1; "Media" -> 2; else -> 3 }
                // asignarA: si seleccion "-" or empty then null
                val asignarIdx = b.spAsignarA.selectedItemPosition
                val asignadoUid = if (asignarIdx >= 0) {
                    val sel = b.spAsignarA.selectedItem as String
                    // guardamos en formato "nombre (uid)" - intentar extraer uid
                    val idxStart = sel.lastIndexOf('(')
                    val idxEnd = sel.lastIndexOf(')')
                    if (idxStart >=0 && idxEnd > idxStart) sel.substring(idxStart+1, idxEnd) else null
                } else null

                val tarea = Tarea(titulo = titulo, puntos = puntos, creadoPor = creadorId, categoria = categoria, dificultad = dificultad, asignadoA = asignadoUid, fechaProgramada = fechaProgramadaTs)
                viewLifecycleOwner.lifecycleScope.launch {
                    val res = LocalizadorServicios.repositorioTarea.crearTarea(tarea)
                    if (res.isSuccess) {
                        // programar recordatorio 30 minutos antes si hay fecha
                        val creado = res.getOrNull()
                        if (creado != null && creado.fechaProgramada != null) {
                            val trigger = creado.fechaProgramada!!.toDate().time - 30 * 60 * 1000L
                            requireContext().let { ctx -> NotificationScheduler.scheduleReminder(ctx, creado.id, "Tarea: ${creado.titulo}", "Tarea programada para ${b.tvFechaProgramada.text}", trigger) }
                        }
                        Toast.makeText(requireContext(), "Tarea creada", Toast.LENGTH_SHORT).show()
                        findNavController().navigate(com.example.tfg.R.id.fragment_PgPrincipal)
                    } else {
                        Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private inner class TareasAdapter : RecyclerView.Adapter<TareasAdapter.VH>() {
        private var items: List<Tarea> = emptyList()
        fun setItems(list: List<Tarea>) { items = list; notifyDataSetChanged() }
        inner class VH(val container: LinearLayout) : RecyclerView.ViewHolder(container)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val context = parent.context
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                val pad = (12 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val tv = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                textSize = 16f
            }
            val btn = Button(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            container.addView(tv)
            container.addView(btn)
            return VH(container)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val tarea = items[position]
            val usuarioId = LocalizadorServicios.repositorioAuth.usuarioActual()?.id ?: ""
            val tv = holder.container.getChildAt(0) as TextView
            val btn = holder.container.getChildAt(1) as Button
            tv.text = "${tarea.titulo} - ${tarea.puntos} pts - ${tarea.estado}"

            when (tarea.estado) {
                "pendiente" -> {
                    // Si está asignada a este usuario o no asignada, permitir completar
                    if (tarea.asignadoA.isNullOrBlank() || tarea.asignadoA == usuarioId) {
                        btn.text = "Completar"
                        btn.isEnabled = true
                        btn.setOnClickListener {
                            viewLifecycleOwner.lifecycleScope.launch {
                                val nueva = tarea.copy(estado = if (tarea.requiereConfirmacion) "completada" else "confirmada")
                                val res = LocalizadorServicios.repositorioTarea.actualizarTarea(nueva)
                                if (res.isSuccess) Toast.makeText(requireContext(), "Tarea actualizada", Toast.LENGTH_SHORT).show() else Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        btn.text = "No puedes completar"
                        btn.isEnabled = false
                    }
                }
                "completada" -> {
                    // Si el creador puede confirmar o reclamar
                    if (!usuarioId.isBlank() && usuarioId == tarea.creadoPor) {
                        btn.text = "Acciones"
                        btn.isEnabled = true
                        btn.setOnClickListener {
                            val opciones = arrayOf("Confirmar", "Reclamar")
                            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setTitle("Elige acción")
                                .setItems(opciones) { _, which ->
                                    when (which) {
                                        0 -> { // Confirmar
                                            viewLifecycleOwner.lifecycleScope.launch {
                                                val nueva = tarea.copy(estado = "confirmada")
                                                val res = LocalizadorServicios.repositorioTarea.actualizarTarea(nueva)
                                                if (res.isSuccess) Toast.makeText(requireContext(), "Tarea confirmada", Toast.LENGTH_SHORT).show() else Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        1 -> { // Reclamar
                                            viewLifecycleOwner.lifecycleScope.launch {
                                                val ahora = com.google.firebase.Timestamp.now()
                                                val nueva = tarea.copy(estado = "reclamada", fechaReclamada = ahora, reclamadoPor = usuarioId)
                                                val res = LocalizadorServicios.repositorioTarea.actualizarTarea(nueva)
                                                if (res.isSuccess) {
                                                    Toast.makeText(requireContext(), "Reclamo enviado (selecciona evidencia opcional)", Toast.LENGTH_SHORT).show()
                                                    // preparar subida: guardar tarea pendiente y lanzar selector de imagen
                                                    pendingTareaParaDisputa = nueva
                                                    pickImageLauncher?.launch("image/*")
                                                } else {
                                                    Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                }
                                .setNegativeButton("Cancelar", null)
                                .show()
                        }
                    } else if (!usuarioId.isBlank() && usuarioId != tarea.asignadoA) {
                        btn.text = "Confirmar"
                        btn.isEnabled = true
                        btn.setOnClickListener {
                            viewLifecycleOwner.lifecycleScope.launch {
                                val nueva = tarea.copy(estado = "confirmada")
                                val res = LocalizadorServicios.repositorioTarea.actualizarTarea(nueva)
                                if (res.isSuccess) Toast.makeText(requireContext(), "Tarea confirmada", Toast.LENGTH_SHORT).show() else Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        btn.text = "Pendiente de confirmación"
                        btn.isEnabled = false
                    }
                }
                "confirmada" -> {
                    btn.text = "Confirmada"
                    btn.isEnabled = false
                }
                else -> {
                    btn.text = "Acción"
                    btn.isEnabled = false
                }
            }
        }
        override fun getItemCount(): Int = items.size
    }
}
