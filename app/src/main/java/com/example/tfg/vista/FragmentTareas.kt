package com.example.tfg.vista

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tfg.R
import com.example.tfg.databinding.FragmentTareasCrearBinding
import com.example.tfg.databinding.FragmentTareasListaBinding
import com.example.tfg.modelo.Disputa
import com.example.tfg.modelo.Tarea
import com.example.tfg.repositorio.RepositorioDisputas
import com.example.tfg.service.LocalizadorServicios
import com.example.tfg.service.NotificationScheduler
import com.example.tfg.viewmodel.ParejaViewModel
import com.example.tfg.repositorio.CategoriasRepositorio
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FragmentTareas : Fragment() {

    private var listaBinding: FragmentTareasListaBinding? = null
    private var crearBinding: FragmentTareasCrearBinding? = null
    private val parejaVM: ParejaViewModel by activityViewModels()
    private val repoDisputas = RepositorioDisputas()
    private var pickImageLauncher: ActivityResultLauncher<String>? = null
    private var pendingTareaParaDisputa: Tarea? = null
    private var usuariosCacheGlobal: List<com.example.tfg.modelo.Usuario> = emptyList()
    private var miembrosParaSpinner: MutableList<Pair<String,String>> = mutableListOf()
    private val TAG = "FragmentTareas"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val modo = arguments?.getString("modo") ?: "lista"
        val taskIdArg = arguments?.getString("taskId")
        return when {
            modo == "crear" -> {
                crearBinding = FragmentTareasCrearBinding.inflate(inflater, container, false)
                crearBinding!!.root
            }
            !taskIdArg.isNullOrBlank() -> {
                // modo detalle: inflar layout detalle manualmente (no binding)
                inflater.inflate(R.layout.fragment_tarea_detalle, container, false)
            }
            else -> {
                listaBinding = FragmentTareasListaBinding.inflate(inflater, container, false)
                listaBinding!!.root
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // launcher para seleccionar imagen (evidencias)
        pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            lifecycleScope.launch {
                val tarea = pendingTareaParaDisputa ?: return@launch
                if (uri != null) {
                    try {
                        val subida = repoDisputas.subirFotoDisputa(tarea.id, uri.toString())
                        if (subida.isSuccess) {
                            val url = subida.getOrNull()
                            val disputa = Disputa(id = "", tareaId = tarea.id, iniciador = LocalizadorServicios.repositorioAuth.usuarioActual()?.id ?: "", estado = "abierta", pruebas = if (url != null) listOf(url) else emptyList(), fechaCreacion = com.google.firebase.Timestamp.now())
                            repoDisputas.abrirDisputa(disputa)
                            Toast.makeText(requireContext(), "Disputa creada", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error subida: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                pendingTareaParaDisputa = null
            }
        }

        // mantener cache de usuarios
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                LocalizadorServicios.repositorioAuth.observarUsuarios().collect { list -> usuariosCacheGlobal = list }
            }
        }

        listaBinding?.let { b ->
            val categoriaArg = arguments?.getString("categoria")
            if (!categoriaArg.isNullOrBlank()) {
                // Mostrar plantillas/sugerencias de la categoría y permitir crear nuevas instancias (reasignables)
                b.rvTareas.layoutManager = LinearLayoutManager(requireContext())
                lifecycleScope.launch {
                    val repoCat = CategoriasRepositorio(requireContext())
                    val cats = try { repoCat.cargarCategoriasDesdeRaw() } catch (e: Exception) { emptyList() }
                    val cat = cats.find { it.id.equals(categoriaArg, true) || it.nombre.equals(categoriaArg, true) }
                    val sugeridas = cat?.tareas ?: emptyList()
                    val adapter = SugeridasAdapter(sugeridas, categoriaArg)
                    b.rvTareas.adapter = adapter
                }
            } else {
                // comportamiento original: mostrar tareas reales (creadas / asignadas / del grupo)
                val adapter = TareasAdapter()
                b.rvTareas.layoutManager = LinearLayoutManager(requireContext())
                b.rvTareas.adapter = adapter

                // observar tareas
                lifecycleScope.launch {
                    LocalizadorServicios.repositorioTarea.observarTareas().collect { list -> adapter.setItems(list) }
                }

                // si viene taskId abrir detalles
                val taskIdArg = arguments?.getString("taskId")
                if (!taskIdArg.isNullOrBlank()) {
                    lifecycleScope.launch {
                        val res = LocalizadorServicios.repositorioTarea.obtenerTareas()
                        if (res.isSuccess) {
                            val tarea = res.getOrNull()?.firstOrNull { it.id == taskIdArg }
                            if (tarea != null) mostrarDialogoTareaDetalles(tarea)
                        }
                    }
                }
            }
        }

        crearBinding?.let { b ->
            val categorias = listOf("Cocina", "Limpieza", "Ropa", "Mascotas", "Recados", "Personalizada")
            val adaptCat = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categorias)
            adaptCat.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            b.spCategoria.adapter = adaptCat

            val dificultades = listOf("Fácil", "Media", "Difícil")
            val adaptDif = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, dificultades)
            adaptDif.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            b.spDificultad.adapter = adaptDif

            val adaptMiembros = android.widget.ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, mutableListOf())
            adaptMiembros.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            b.spAsignarA.adapter = adaptMiembros

            // actualizar miembros cuando cambie el grupo
            lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    parejaVM.grupo.collect { g ->
                        // construir lista de pairs (display, uid)
                        miembrosParaSpinner.clear()
                        miembrosParaSpinner.add(Pair("Sin asignar", ""))
                        g?.miembros?.keys?.forEach { uid ->
                            val u = usuariosCacheGlobal.find { it.id == uid }
                            val display = when {
                                u != null && u.nombre.isNotBlank() -> if (u.email.isNotBlank()) "${u.nombre} (${u.email})" else u.nombre
                                u != null && u.email.isNotBlank() -> u.email
                                else -> uid
                            }
                            miembrosParaSpinner.add(Pair(display, uid))
                        }
                        // poblar adaptador con solo los textos
                        adaptMiembros.clear()
                        adaptMiembros.addAll(miembrosParaSpinner.map { it.first })
                        adaptMiembros.notifyDataSetChanged()
                    }
                }
            }

            var fechaProgramadaTs: com.google.firebase.Timestamp? = null
            b.btnElegirFecha.setOnClickListener {
                val hoy = java.util.Calendar.getInstance()
                val dp = DatePickerDialog(requireContext(), { _, year, month, day ->
                    val cal = java.util.Calendar.getInstance()
                    cal.set(year, month, day, 12, 0, 0)
                    fechaProgramadaTs = com.google.firebase.Timestamp(cal.time)
                    b.tvFechaProgramada.text = "Fecha: ${day}/${month+1}/${year}"
                }, hoy.get(java.util.Calendar.YEAR), hoy.get(java.util.Calendar.MONTH), hoy.get(java.util.Calendar.DAY_OF_MONTH))
                dp.show()
            }

            b.btnCrearTarea.setOnClickListener {
                val titulo = b.etTitulo.text.toString().trim()
                val puntos = b.etPuntos.text.toString().toIntOrNull() ?: 0
                if (titulo.isEmpty()) { Toast.makeText(requireContext(), "Introduce título", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                val creadorId = LocalizadorServicios.repositorioAuth.usuarioActual()?.id
                val categoria = b.spCategoria.selectedItem as String
                val dificultadStr = b.spDificultad.selectedItem as String
                val dificultad = when (dificultadStr) { "Fácil" -> 1; "Media" -> 2; else -> 3 }
                val asignIdx = b.spAsignarA.selectedItemPosition
                val asignadoUid = if (asignIdx > 0 && asignIdx < miembrosParaSpinner.size) miembrosParaSpinner[asignIdx].second else null

                val tarea = Tarea(titulo = titulo, puntos = puntos, creadoPor = creadorId, categoria = categoria, dificultad = dificultad, asignadoA = asignadoUid, fechaProgramada = fechaProgramadaTs)
                lifecycleScope.launch {
                    val res = LocalizadorServicios.repositorioTarea.crearTarea(tarea)
                    if (res.isSuccess) {
                        val creado = res.getOrNull()
                        if (creado != null && creado.fechaProgramada != null) {
                            val trigger = creado.fechaProgramada!!.toDate().time - 30 * 60 * 1000L
                            NotificationScheduler.scheduleReminder(requireContext(), creado.id, "Tarea: ${creado.titulo}", "Tarea programada para ${b.tvFechaProgramada.text}", trigger)
                        }
                        Toast.makeText(requireContext(), "Tarea creada", Toast.LENGTH_SHORT).show()
                        findNavController().navigate(R.id.fragment_PgPrincipal)
                    } else Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Si venimos en modo detalle con taskId, cargar y mostrar
        val taskIdArg = arguments?.getString("taskId")
        if (!taskIdArg.isNullOrBlank() && crearBinding == null && listaBinding == null) {
            // estamos en la vista detalle (layout inflado manualmente)
            val tvTitulo = view.findViewById<TextView>(R.id.tvDetalleTitulo)
            val tvMeta = view.findViewById<TextView>(R.id.tvDetalleMeta)
            val tvAsignado = view.findViewById<TextView>(R.id.tvDetalleAsignado)
            val tvDesc = view.findViewById<TextView>(R.id.tvDetalleDescripcion)
            val btnAccion = view.findViewById<Button>(R.id.btnDetalleAccion)
            val btnMas = view.findViewById<Button>(R.id.btnDetalleMas)
            val btnAsignar = view.findViewById<Button>(R.id.btnDetalleAsignar)

            lifecycleScope.launch {
                val res = LocalizadorServicios.repositorioTarea.obtenerTareas()
                if (res.isSuccess) {
                    val tarea = res.getOrNull()?.firstOrNull { it.id == taskIdArg }
                    if (tarea != null) {
                        tvTitulo.text = tarea.titulo
                        val dif = when (tarea.dificultad) {1->"Fácil";2->"Media";else->"Difícil"}
                        tvMeta.text = "${tarea.puntos} pts · $dif"
                        tvDesc.text = tarea.descripcion ?: ""

                        // Mostramos la opción de asignar siempre al creador cuando la tarea esté pendiente; no mostramos a quién está asignada aquí
                        val usuarioActualId = LocalizadorServicios.repositorioAuth.usuarioActual()?.id ?: ""
                        if (!usuarioActualId.isBlank() && usuarioActualId == tarea.creadoPor && tarea.estado == "pendiente") {
                            tvAsignado.text = ""
                            btnAsignar.visibility = View.VISIBLE
                            btnAsignar.setOnClickListener {
                                lifecycleScope.launch {
                                    val grupo = parejaVM.grupo.value
                                    val usuarios = try { LocalizadorServicios.repositorioAuth.observarUsuarios().first() } catch (_: Exception) { emptyList<com.example.tfg.modelo.Usuario>() }
                                    val opciones = mutableListOf<Pair<String,String>>()
                                    if (grupo != null) {
                                        grupo.miembros.keys.forEach { uid ->
                                            val u2 = usuarios.find { it.id == uid }
                                            val display = if (u2 != null && u2.nombre.isNotBlank()) "${u2.nombre} (${if (u2.email.isNotBlank()) u2.email else u2.id})" else u2?.email ?: uid
                                            opciones.add(Pair(display, uid))
                                        }
                                    }
                                    if (opciones.isEmpty()) Toast.makeText(requireContext(), "No hay miembros", Toast.LENGTH_SHORT).show() else {
                                        val names = opciones.map { it.first }.toTypedArray()
                                        androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("Selecciona miembro").setItems(names) { _, idx ->
                                            lifecycleScope.launch {
                                                val elegido = opciones[idx].second
                                                val nueva = tarea.copy(asignadoA = elegido, grupoId = parejaVM.grupo.value?.id)
                                                val res2 = LocalizadorServicios.repositorioTarea.actualizarTarea(nueva)
                                                if (res2.isSuccess) {
                                                    Toast.makeText(requireContext(), "Tarea asignada", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(requireContext(), res2.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }.setNegativeButton("Cancelar", null).show()
                                    }
                                }
                            }
                        } else {
                            btnAsignar.visibility = View.GONE
                        }

                        // configurar botones coherentes (sin mostrar a quién se asignó aquí)
                        btnMas.setOnClickListener {
                            // Mostrar opciones secundarias según estado
                            val opciones = when (tarea.estado) {
                                "pendiente" -> arrayOf("Editar","Eliminar")
                                "completada" -> arrayOf("Confirmar","Reclamar")
                                else -> arrayOf("Editar")
                            }
                            androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("Opciones")
                                .setItems(opciones) { _, idx ->
                                    // acciones simples para ejemplo
                                    when (opciones[idx]) {
                                        "Editar" -> mostrarDialogoEditar(tarea)
                                        "Eliminar" -> lifecycleScope.launch { LocalizadorServicios.repositorioTarea.actualizarTarea(tarea.copy(estado = "eliminada")) }
                                        "Confirmar" -> lifecycleScope.launch {
                                            // deshabilitar botón para evitar doble click
                                            btnAccion.isEnabled = false
                                            try {
                                                val r = LocalizadorServicios.repositorioTarea.confirmarTarea(tarea.id, tarea.creadoPor ?: "")
                                                if (r.isSuccess) {
                                                    Toast.makeText(requireContext(), "Tarea confirmada", Toast.LENGTH_SHORT).show()
                                                    // ocultar el botón de acción en la vista detalle
                                                    btnAccion.visibility = Button.GONE
                                                } else {
                                                    Toast.makeText(requireContext(), r.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_SHORT).show()
                                                    btnAccion.isEnabled = true
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(requireContext(), e.message ?: "Error", Toast.LENGTH_SHORT).show()
                                                btnAccion.isEnabled = true
                                            }
                                        }
                                        "Reclamar" -> { pendingTareaParaDisputa = tarea; pickImageLauncher?.launch("image/*") }
                                    }
                                }.setNegativeButton("Cancelar", null).show()
                        }

                        val uid = LocalizadorServicios.repositorioAuth.usuarioActual()?.id ?: ""
                        when {
                            !uid.isBlank() && uid == tarea.creadoPor && tarea.estado == "completada" -> {
                                btnAccion.text = "Confirmar"
                                btnAccion.setOnClickListener {
                                    lifecycleScope.launch {
                                        btnAccion.isEnabled = false
                                        val res = LocalizadorServicios.repositorioTarea.confirmarTarea(tarea.id, tarea.creadoPor ?: "")
                                        if (res.isSuccess) {
                                            Toast.makeText(requireContext(), "Tarea confirmada", Toast.LENGTH_SHORT).show()
                                            btnAccion.visibility = Button.GONE
                                        } else {
                                            Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_SHORT).show()
                                            btnAccion.isEnabled = true
                                        }
                                    }
                                }
                            }
                            !uid.isBlank() && uid == tarea.asignadoA && tarea.estado == "pendiente" -> {
                                btnAccion.text = "Completar"
                                btnAccion.setOnClickListener {
                                    lifecycleScope.launch {
                                        val r = LocalizadorServicios.repositorioTarea.marcarCompletada(tarea.id, uid)
                                        if (r.isSuccess) Toast.makeText(requireContext(), "Tarea marcada como completada", Toast.LENGTH_SHORT).show() else Toast.makeText(requireContext(), r.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            else -> {
                                btnAccion.visibility = Button.GONE
                            }
                        }
                    }
                }
            }
        }
    }

    // Adapter simple
    private inner class TareasAdapter : RecyclerView.Adapter<TareasAdapter.VH>() {
        private var items: List<Tarea> = emptyList()
        fun setItems(list: List<Tarea>) { items = list; notifyDataSetChanged() }

        inner class VH(val root: View) : RecyclerView.ViewHolder(root) {
            val tvTitulo: TextView = root.findViewById(R.id.tvTituloTarea)
            val tvMeta: TextView = root.findViewById(R.id.tvMetaTarea)
            val tvAsignado: TextView = root.findViewById(R.id.tvAsignado)
            val btnAccion: Button = root.findViewById(R.id.btnAccionTarea)
            val cardRoot: androidx.cardview.widget.CardView = root.findViewById(R.id.cardRoot)
            val vIndicator: View? = root.findViewById(R.id.vIndicator)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_tarea, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val tarea = items[position]
            val usuarioId = LocalizadorServicios.repositorioAuth.usuarioActual()?.id ?: ""
            holder.tvTitulo.text = tarea.titulo
            val dif = when (tarea.dificultad) {1->"Fácil";2->"Media";else->"Difícil"}
            holder.tvMeta.text = "${tarea.puntos} pts · $dif"

            // No mostrar asignación en la tarjeta de lista (se gestiona en detalle)
            holder.tvAsignado.visibility = View.GONE

            // indicador lateral por dificultad (verde/amarillo/rojo)
            when (tarea.dificultad) {
                1 -> holder.vIndicator?.setBackgroundColor(android.graphics.Color.parseColor("#A5D6A7"))
                2 -> holder.vIndicator?.setBackgroundColor(android.graphics.Color.parseColor("#FFF59D"))
                else -> holder.vIndicator?.setBackgroundColor(android.graphics.Color.parseColor("#FFCDD2"))
            }

            holder.cardRoot.setCardBackgroundColor(android.graphics.Color.WHITE)
            when (tarea.estado) {
                "completada" -> holder.cardRoot.setCardBackgroundColor(android.graphics.Color.parseColor("#FFF59D"))
                "confirmada" -> holder.cardRoot.setCardBackgroundColor(android.graphics.Color.parseColor("#C8E6C9"))
                "reclamada" -> holder.cardRoot.setCardBackgroundColor(android.graphics.Color.parseColor("#FFCDD2"))
            }

            holder.btnAccion.setOnClickListener(null)
            holder.root.setOnLongClickListener { mostrarDialogoEditar(tarea); true }
            // Simplificar lista: solo permitir "Completar" si soy el asignado y la tarea está pendiente
            if (!usuarioId.isBlank() && usuarioId == tarea.asignadoA && tarea.estado == "pendiente") {
                holder.btnAccion.visibility = View.VISIBLE
                holder.btnAccion.isEnabled = true
                holder.btnAccion.text = "Completar"
                holder.btnAccion.setOnClickListener {
                    lifecycleScope.launch {
                        val res = LocalizadorServicios.repositorioTarea.marcarCompletada(tarea.id, usuarioId)
                        if (res.isSuccess) Toast.makeText(requireContext(), "Tarea marcada como completada", Toast.LENGTH_SHORT).show() else {
                            val msg = res.exceptionOrNull()?.message ?: "Error"
                            Log.e(TAG, "marcarCompletada failed: $msg")
                            if (msg.contains("PERMISSION_DENIED") || msg.contains("permission", true)) Toast.makeText(requireContext(), "Permisos Firestore insuficientes", Toast.LENGTH_LONG).show() else Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else if (!usuarioId.isBlank() && usuarioId == tarea.creadoPor && tarea.estado == "pendiente") {
                // Si soy el creador y la tarea está pendiente, permitir asignar/reasignar desde la lista
                holder.btnAccion.visibility = View.VISIBLE
                holder.btnAccion.isEnabled = true
                holder.btnAccion.text = "Asignar"
                holder.btnAccion.setOnClickListener {
                    lifecycleScope.launch {
                        val grupo = parejaVM.grupo.value
                        val usuarios = try { LocalizadorServicios.repositorioAuth.observarUsuarios().first() } catch (_: Exception) { emptyList<com.example.tfg.modelo.Usuario>() }
                        val opciones = mutableListOf<Pair<String,String>>()
                        if (grupo != null) {
                            grupo.miembros.keys.forEach { uid ->
                                val u2 = usuarios.find { it.id == uid }
                                val display = if (u2 != null && u2.nombre.isNotBlank()) "${u2.nombre} (${if (u2.email.isNotBlank()) u2.email else u2.id})" else u2?.email ?: uid
                                opciones.add(Pair(display, uid))
                            }
                        }
                        if (opciones.isEmpty()) Toast.makeText(requireContext(), "No hay miembros", Toast.LENGTH_SHORT).show() else {
                            val names = opciones.map { it.first }.toTypedArray()
                            androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("Selecciona miembro").setItems(names) { _, idx ->
                                lifecycleScope.launch {
                                    val elegido = opciones[idx].second
                                    val nueva = tarea.copy(asignadoA = elegido, grupoId = parejaVM.grupo.value?.id)
                                    val res2 = LocalizadorServicios.repositorioTarea.actualizarTarea(nueva)
                                    if (res2.isSuccess) {
                                        Toast.makeText(requireContext(), "Tarea asignada", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(requireContext(), res2.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }.setNegativeButton("Cancelar", null).show()
                        }
                    }
                }
            } else {
                holder.btnAccion.visibility = View.GONE
            }
            // clic corto abre siempre el detalle
            holder.root.setOnClickListener { mostrarDialogoTareaDetalles(tarea) }
        }

        override fun getItemCount(): Int = items.size
    }

    // Adapter para sugeridas (plantillas)
    private inner class SugeridasAdapter(private val items: List<com.example.tfg.modelo.TareaSugerida>, private val categoriaId: String) : RecyclerView.Adapter<SugeridasAdapter.SV>() {
        inner class SV(val root: View) : RecyclerView.ViewHolder(root) {
            val tvTitulo: TextView = root.findViewById(R.id.tvTituloTarea)
            val tvMeta: TextView = root.findViewById(R.id.tvMetaTarea)
            val btnAccion: Button = root.findViewById(R.id.btnAccionTarea)
            val vIndicator: View? = root.findViewById(R.id.vIndicator)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SV {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_tarea, parent, false)
            return SV(v)
        }

        override fun onBindViewHolder(holder: SV, position: Int) {
            val sug = items[position]
            Log.d(TAG, "SugeridasAdapter bind: ${sug.titulo} at pos $position")
            holder.tvTitulo.text = sug.titulo
            holder.tvMeta.text = "${sug.puntos} pts · ${sug.dificultad}"
            holder.btnAccion.visibility = View.VISIBLE
            holder.btnAccion.text = getString(R.string.asignar)

            // indicador lateral por dificultad
            when (sug.dificultad.lowercase()) {
                "fácil", "facil" -> holder.vIndicator?.setBackgroundColor(android.graphics.Color.parseColor("#A5D6A7"))
                "media" -> holder.vIndicator?.setBackgroundColor(android.graphics.Color.parseColor("#FFF59D"))
                else -> holder.vIndicator?.setBackgroundColor(android.graphics.Color.parseColor("#FFCDD2"))
            }

            holder.btnAccion.setOnClickListener {
                // abrir selector de miembro para crear una nueva instancia de Tarea (siempre permite crear)
                lifecycleScope.launch {
                    Log.d(TAG, "Intentando asignar sugerida: ${sug.titulo}")
                    val grupo = parejaVM.grupo.value
                    val usuarios = try { LocalizadorServicios.repositorioAuth.observarUsuarios().first() } catch (_: Exception) { emptyList<com.example.tfg.modelo.Usuario>() }
                    val opciones = mutableListOf<Pair<String,String>>()
                    // permitir 'Sin asignar' para crear sin responsable
                    opciones.add(Pair(getString(R.string.asignado_por_defecto), ""))
                    if (grupo != null) {
                        grupo.miembros.keys.forEach { uid ->
                            val u2 = usuarios.find { it.id == uid }
                            val display = when {
                                u2 != null && u2.nombre.isNotBlank() -> if (u2.email.isNotBlank()) "${u2.nombre} (${u2.email})" else u2.nombre
                                u2 != null && u2.email.isNotBlank() -> u2.email
                                else -> uid
                            }
                            opciones.add(Pair(display, uid))
                        }
                    }
                    // opciones siempre contiene al menos 'Sin asignar'
                    val nombres = opciones.map { it.first }.toTypedArray()
                    androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle(getString(R.string.selecciona_miembro)).setItems(nombres) { _, idx ->
                        lifecycleScope.launch {
                            val elegidoUid = opciones[idx].second.ifBlank { null }
                            val creador = LocalizadorServicios.repositorioAuth.usuarioActual()?.id
                            val dificultadInt = when (sug.dificultad.lowercase()) { "fácil", "facil" -> 1; "media" -> 2; else -> 3 }
                            val tarea = Tarea(titulo = sug.titulo, descripcion = sug.descripcion, categoria = categoriaId, dificultad = dificultadInt, puntos = sug.puntos, creadoPor = creador, asignadoA = elegidoUid, grupoId = parejaVM.grupo.value?.id)
                            Log.d(TAG, "Creando tarea desde sugerida: titulo=${tarea.titulo} asignadoA=${tarea.asignadoA}")
                            val res = LocalizadorServicios.repositorioTarea.crearTarea(tarea)
                            if (res.isSuccess) {
                                Log.d(TAG, "Tarea creada OK: ${res.getOrNull()?.id}")
                                Toast.makeText(requireContext(), getString(R.string.tarea_asignada_ok, opciones[idx].first), Toast.LENGTH_SHORT).show()
                                // opcional: si quieres volver atrás para ver la lista real, descomenta:
                                // findNavController().popBackStack()
                            } else {
                                Log.e(TAG, "Error crear tarea: ${res.exceptionOrNull()?.message}")
                                Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_LONG).show()
                            }
                        }
                    }.setNegativeButton(getString(R.string.cancelar), null).show()
                }
            }

            // click en la tarjeta puede mostrar detalles de la sugerencia si se quiere
            holder.root.setOnClickListener { /* opcional: mostrar info */ }
        }

        override fun getItemCount(): Int = items.size
    }

    private fun mostrarDialogoEditar(tarea: Tarea) {
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_editar_tarea, null)
        val etTitulo = v.findViewById<android.widget.EditText>(R.id.etTituloEditar)
        val etPuntos = v.findViewById<android.widget.EditText>(R.id.etPuntosEditar)
        val spDificultad = v.findViewById<android.widget.Spinner>(R.id.spDificultadEditar)
        etTitulo.setText(tarea.titulo)
        etPuntos.setText(tarea.puntos.toString())
        val opciones = listOf("Fácil","Media","Difícil")
        val adapt = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, opciones)
        adapt.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spDificultad.adapter = adapt
        spDificultad.setSelection(if (tarea.dificultad==1) 0 else if (tarea.dificultad==2) 1 else 2)

        androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("Editar tarea").setView(v)
            .setPositiveButton("Guardar") { _, _ ->
                val nuevoTitulo = etTitulo.text.toString().trim()
                val nuevosPts = etPuntos.text.toString().toIntOrNull() ?: tarea.puntos
                val nuevaDif = when(spDificultad.selectedItemPosition) {0->1;1->2;else->3}
                lifecycleScope.launch {
                    val nueva = tarea.copy(titulo = nuevoTitulo, puntos = nuevosPts, dificultad = nuevaDif)
                    LocalizadorServicios.repositorioTarea.actualizarTarea(nueva)
                    Toast.makeText(requireContext(), "Tarea editada", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun mostrarDialogoTareaDetalles(tarea: Tarea) {
        // Mostrar diálogo con información básica y opción 'Asignar' si corresponde
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        val sb = StringBuilder()
        sb.append("Título: ${tarea.titulo}\n")
        sb.append("Puntos: ${tarea.puntos}\n")
        if (!tarea.descripcion.isNullOrBlank()) sb.append("\n${tarea.descripcion}\n")

        // cerrar = positive
        builder.setTitle("Detalle tarea").setMessage(sb.toString())
            .setPositiveButton("Cerrar", null)

        // Añadir botón "Crear otra" para crear una nueva instancia (duplicado) y asignarla
        builder.setNegativeButton("Crear otra") { _, _ ->
            lifecycleScope.launch {
                // elegir miembro (incluye 'Sin asignar')
                val grupo = parejaVM.grupo.value
                val usuarios = try { LocalizadorServicios.repositorioAuth.observarUsuarios().first() } catch (_: Exception) { emptyList<com.example.tfg.modelo.Usuario>() }
                val opciones = mutableListOf<Pair<String,String>>()
                opciones.add(Pair(getString(R.string.asignado_por_defecto), ""))
                if (grupo != null) {
                    grupo.miembros.keys.forEach { uid ->
                        val u2 = usuarios.find { it.id == uid }
                        val display = when {
                            u2 != null && u2.nombre.isNotBlank() -> if (u2.email.isNotBlank()) "${u2.nombre} (${u2.email})" else u2.nombre
                            u2 != null && u2.email.isNotBlank() -> u2.email
                            else -> uid
                        }
                        opciones.add(Pair(display, uid))
                    }
                }
                val nombres = opciones.map { it.first }.toTypedArray()
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.selecciona_miembro))
                    .setItems(nombres) { _, idx ->
                        lifecycleScope.launch {
                            val elegidoUid = opciones[idx].second.ifBlank { null }
                            val creador = LocalizadorServicios.repositorioAuth.usuarioActual()?.id
                            val nueva = Tarea(
                                titulo = tarea.titulo,
                                descripcion = tarea.descripcion,
                                categoria = tarea.categoria,
                                dificultad = tarea.dificultad,
                                puntos = tarea.puntos,
                                creadoPor = creador,
                                asignadoA = elegidoUid,
                                grupoId = parejaVM.grupo.value?.id
                            )
                            Log.d(TAG, "Creando duplicado de tarea: ${nueva.titulo} asignadoA=${nueva.asignadoA}")
                            val res = LocalizadorServicios.repositorioTarea.crearTarea(nueva)
                            if (res.isSuccess) {
                                Toast.makeText(requireContext(), "Tarea creada y asignada", Toast.LENGTH_SHORT).show()
                                NotificationScheduler.showImmediateNotification(requireContext(), ("tarea_${res.getOrNull()?.id}").hashCode(), "Nueva tarea", "Se ha creado: ${nueva.titulo}", res.getOrNull()?.id ?: "")
                            } else {
                                Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Error al crear tarea", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    .setNegativeButton(getString(R.string.cancelar), null)
                    .show()
            }
        }

        // permite asignar solo si soy el creador y la tarea está pendiente: siempre abrir selector para asignar/reasignar
        val usuarioActualId = LocalizadorServicios.repositorioAuth.usuarioActual()?.id ?: ""
        if (!usuarioActualId.isBlank() && usuarioActualId == tarea.creadoPor && tarea.estado == "pendiente") {
            builder.setNeutralButton("Asignar") { _, _ ->
                lifecycleScope.launch {
                    val grupo = parejaVM.grupo.value
                    val usuarios = try { LocalizadorServicios.repositorioAuth.observarUsuarios().first() } catch (_: Exception) { emptyList<com.example.tfg.modelo.Usuario>() }
                    val opciones = mutableListOf<Pair<String,String>>()
                    if (grupo != null) {
                        grupo.miembros.keys.forEach { uid ->
                            val u2 = usuarios.find { it.id == uid }
                            val display = if (u2 != null && u2.nombre.isNotBlank()) "${u2.nombre} (${if (u2.email.isNotBlank()) u2.email else u2.id})" else u2?.email ?: uid
                            opciones.add(Pair(display, uid))
                        }
                    }
                    if (opciones.isEmpty()) Toast.makeText(requireContext(), "No hay miembros", Toast.LENGTH_SHORT).show() else {
                        val names = opciones.map { it.first }.toTypedArray()
                        androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("Selecciona miembro").setItems(names) { _, idx ->
                            lifecycleScope.launch {
                                val elegido = opciones[idx].second
                                val nueva = tarea.copy(asignadoA = elegido, grupoId = parejaVM.grupo.value?.id)
                                val res = LocalizadorServicios.repositorioTarea.actualizarTarea(nueva)
                                if (res.isSuccess) {
                                    Toast.makeText(requireContext(), "Tarea asignada", Toast.LENGTH_SHORT).show()
                                    NotificationScheduler.showImmediateNotification(requireContext(), ("tarea_${nueva.id}").hashCode(), "Nueva asignación", "Te han asignado: ${nueva.titulo}", nueva.id)
                                } else {
                                    val msg = res.exceptionOrNull()?.message ?: "Error"
                                    Log.e(TAG, "Error asignar tarea desde detalle: $msg")
                                    Toast.makeText(requireContext(), "Error asignar: $msg", Toast.LENGTH_LONG).show()
                                }
                            }
                        }.setNegativeButton("Cancelar", null).show()
                    }
                }
            }
        }

        builder.show()
    }

}
