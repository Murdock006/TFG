package es.sintaxys.teamtask.vista

import android.graphics.Bitmap
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
import es.sintaxys.teamtask.R
import es.sintaxys.teamtask.databinding.FragmentTareasCrearBinding
import es.sintaxys.teamtask.databinding.FragmentTareasListaBinding
import es.sintaxys.teamtask.modelo.Disputa
import es.sintaxys.teamtask.modelo.Tarea
import es.sintaxys.teamtask.repositorio.RepositorioDisputas
import es.sintaxys.teamtask.service.LocalizadorServicios
import es.sintaxys.teamtask.service.NotificationScheduler
import es.sintaxys.teamtask.viewmodel.ParejaViewModel
import es.sintaxys.teamtask.viewmodel.TareasViewModel
import es.sintaxys.teamtask.repositorio.CategoriasRepositorio
import es.sintaxys.teamtask.repositorio.RepositorioNotificaciones
import es.sintaxys.teamtask.modelo.Notificacion
import es.sintaxys.teamtask.util.AvatarImagen
import es.sintaxys.teamtask.util.Constants
import es.sintaxys.teamtask.util.SelectorFechaHora
import es.sintaxys.teamtask.util.TareaUi
import es.sintaxys.teamtask.util.nombreVisible
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


class FragmentTareas : Fragment() {

    private var listaBinding: FragmentTareasListaBinding? = null
    private var crearBinding: FragmentTareasCrearBinding? = null
    private val parejaVM: ParejaViewModel by activityViewModels()
    private val tareasVM: TareasViewModel by activityViewModels()
    private val repoDisputas = RepositorioDisputas()
    private var pickImageLauncher: ActivityResultLauncher<String>? = null
    private var pendingTareaParaDisputa: Tarea? = null
    private var pendingMotivoReclamo: String? = null
    private var pendingAccionDisputa: AccionDisputa? = null

    /** Acción que ejecutará el selector de imagen una vez elegido (o descartado) el archivo. */
    private enum class AccionDisputa { ABRIR, RESPONDER }

    private var usuariosCacheGlobal: List<es.sintaxys.teamtask.modelo.Usuario> = emptyList()
    private var miembrosParaSpinner: MutableList<Pair<String,String>> = mutableListOf()
    private val TAG = "FragmentTareas"
    private var ultimoBotonConfirmar: Button? = null

    // Lector tipado de los argumentos de navegación declarados en nav_graph.xml (Safe Args).
    private val navArgs: FragmentTareasArgs?
        get() = arguments?.let { FragmentTareasArgs.fromBundle(it) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val modo = navArgs?.modo ?: "lista"
        val taskIdArg = navArgs?.taskId
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

        // Cambiar título de la tarjeta según la categoría
        val categoriaArg = navArgs?.categoria
        if (!categoriaArg.isNullOrBlank()) {
            try {
                view.findViewById<TextView>(R.id.tvTituloTareas)?.text = categoriaArg.uppercase()
            } catch (e: Exception) {
                Log.w(TAG, "Error actualizando título de categoría: ${e.message}")
            }
        }

        // launcher para seleccionar imagen (evidencias)
        pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            // El estado pendiente se captura y se limpia ANTES de suspender para que una nueva
            // selección no reutilice datos obsoletos.
            val tarea = pendingTareaParaDisputa
            val accion = pendingAccionDisputa
            val motivo = pendingMotivoReclamo
            pendingTareaParaDisputa = null
            pendingAccionDisputa = null
            pendingMotivoReclamo = null
            lifecycleScope.launch {
                if (tarea == null || accion == null) return@launch
                // La foto es opcional (task-domain/spec.md: la UI puede abrir la disputa con
                // pruebas=emptyList() si el procesado falla o no se elige imagen). Antes, un fallo
                // dejaba el flujo en silencio: no se creaba nada ni se avisaba.
                var evidenciaBase64: String? = null
                if (uri != null) {
                    val procesada = repoDisputas.procesarEvidenciaDisputa(uri.toString(), requireContext().contentResolver)
                    if (procesada.isSuccess) {
                        evidenciaBase64 = procesada.getOrNull()
                    } else {
                        Log.w(TAG, "Procesamiento de evidencia falló: ${procesada.exceptionOrNull()?.message}")
                        Toast.makeText(requireContext(), getString(R.string.evidencia_no_subida), Toast.LENGTH_LONG).show()
                    }
                }
                when (accion) {
                    AccionDisputa.ABRIR -> abrirDisputaDeTarea(tarea, motivo, evidenciaBase64)
                    AccionDisputa.RESPONDER -> responderDisputaDeTarea(tarea, motivo, evidenciaBase64)
                }
            }
        }

        // mantener cache de usuarios
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                LocalizadorServicios.repositorioAuth.observarUsuarios().collect { list -> usuariosCacheGlobal = list }
            }
        }

        // observers para StateFlow del TareasViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                tareasVM.marcarCompletadaState.collect { result ->
                    result?.let {
                        if (it.isSuccess) {
                            Toast.makeText(requireContext(), getString(R.string.tarea_marcar_completada), Toast.LENGTH_SHORT).show()
                            volverAInicio()
                        } else {
                            val msg = it.exceptionOrNull()?.message ?: "Error"
                            Log.e(TAG, "marcarCompletada failed: $msg")
                            if (msg.contains("PERMISSION_DENIED") || msg.contains("permission", true)) {
                                Toast.makeText(requireContext(), getString(R.string.permisos_firestore), Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                            }
                        }
                        tareasVM.resetMarcarCompletadaState()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                tareasVM.confirmarTareaState.collect { result ->
                    result?.let {
                        ultimoBotonConfirmar?.isEnabled = true
                        ultimoBotonConfirmar = null
                        if (it.isSuccess) {
                            Toast.makeText(requireContext(), getString(R.string.tarea_confirmada), Toast.LENGTH_SHORT).show()
                            volverAInicio()
                        } else {
                            Toast.makeText(requireContext(), it.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_SHORT).show()
                        }
                        tareasVM.resetConfirmarTareaState()
                    }
                }
            }
        }

        listaBinding?.let { b ->
            val categoriaArg = navArgs?.categoria
            if (!categoriaArg.isNullOrBlank()) {
                // Mostrar plantillas/sugerencias de la categoría y permitir crear nuevas instancias (reasignables)
                b.rvTareas.layoutManager = LinearLayoutManager(requireContext())
                b.progressBar.visibility = View.VISIBLE
                viewLifecycleOwner.lifecycleScope.launch {
                    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        val repoCat = CategoriasRepositorio(requireContext())
                        val cats = try { repoCat.cargarCategoriasDesdeRaw() } catch (e: Exception) { emptyList() }
                        val cat = cats.find { it.id.equals(categoriaArg, true) || it.nombre.equals(categoriaArg, true) }
                        val sugeridas = cat?.tareas ?: emptyList()
                        val adapter = SugeridasAdapter(sugeridas, categoriaArg)
                        b.rvTareas.adapter = adapter
                        b.progressBar.visibility = View.GONE

                        if (sugeridas.isEmpty()) {
                            b.rvTareas.visibility = View.GONE
                            b.emptyState.visibility = View.VISIBLE
                        } else {
                            b.rvTareas.visibility = View.VISIBLE
                            b.emptyState.visibility = View.GONE
                        }
                    }
                }
            } else {
                // comportamiento original: mostrar tareas reales (creadas / asignadas / del grupo)
                val adapter = TareasAdapter()
                b.rvTareas.layoutManager = LinearLayoutManager(requireContext())
                b.rvTareas.adapter = adapter

                // observar tareas
                b.progressBar.visibility = View.VISIBLE
                viewLifecycleOwner.lifecycleScope.launch {
                    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        LocalizadorServicios.repositorioTarea.observarTareas().collect { list ->
                            // Las tareas eliminadas (soft delete) no se listan.
                            val visibles = list.filter { it.estado != "eliminada" }
                            adapter.setItems(visibles)
                            b.progressBar.visibility = View.GONE

                            if (visibles.isEmpty()) {
                                b.rvTareas.visibility = View.GONE
                                b.emptyState.visibility = View.VISIBLE
                            } else {
                                b.rvTareas.visibility = View.VISIBLE
                                b.emptyState.visibility = View.GONE
                            }
                        }
                    }
                }

                // si viene taskId abrir detalles
                val taskIdArg = navArgs?.taskId
                if (!taskIdArg.isNullOrBlank()) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                            val res = LocalizadorServicios.repositorioTarea.obtenerTareas()
                            if (res.isSuccess) {
                                val tarea = res.getOrNull()?.firstOrNull { it.id == taskIdArg }
                                if (tarea != null) mostrarDialogoTareaDetalles(tarea)
                            }
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

            // Preselecciona la categoría recibida por navegación (sirve tanto para
            // "Personalizada" como para las categorías estándar), de modo que el formulario
            // completo —incluida la emergencia ×1.5— esté disponible para todas ellas.
            val categoriaInicialArg = navArgs?.categoria
            if (!categoriaInicialArg.isNullOrBlank()) {
                val idxInicial = categorias.indexOfFirst { it.equals(categoriaInicialArg, true) }
                if (idxInicial >= 0) b.spCategoria.setSelection(idxInicial)
            }

            val dificultades = listOf("Fácil", "Media", "Difícil")
            val adaptDif = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, dificultades)
            adaptDif.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            b.spDificultad.adapter = adaptDif

            val adaptMiembros = android.widget.ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, mutableListOf())
            adaptMiembros.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            b.spAsignarA.adapter = adaptMiembros

            fun isCategoriaPersonalizadaSeleccionada(): Boolean {
                val cat = (b.spCategoria.selectedItem as? String)?.trim().orEmpty()
                return cat.equals("Personalizada", true) || cat.equals("Personalizado", true)
            }

            fun actualizarUiPuntosSegunCategoria() {
                val esPersonalizada = isCategoriaPersonalizadaSeleccionada()
                b.tilPuntos.visibility = if (esPersonalizada) View.GONE else View.VISIBLE
                b.tvPuntosFijos.visibility = if (esPersonalizada) View.VISIBLE else View.GONE
                if (esPersonalizada) {
                    b.etPuntos.setText(Constants.PUNTOS_FIJOS_PERSONALIZADA.toString())
                }
            }

            b.spCategoria.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    actualizarUiPuntosSegunCategoria()
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }

            // Ajuste inicial
            actualizarUiPuntosSegunCategoria()

            // actualizar miembros cuando cambie el grupo
            lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    parejaVM.grupo.collect { g ->
                        if (usuariosCacheGlobal.isEmpty()) {
                            try {
                                usuariosCacheGlobal = LocalizadorServicios.repositorioAuth.observarUsuarios().first()
                            } catch (_: Exception) {
                            }
                        }
                        // construir lista de pairs (display, uid)
                        miembrosParaSpinner.clear()
                        miembrosParaSpinner.add(Pair("Sin asignar", ""))
                        val uidActual = LocalizadorServicios.repositorioAuth.usuarioActual()?.id
                        g?.miembros?.keys?.forEach { uid ->
                            if (!uidActual.isNullOrBlank() && uid == uidActual) return@forEach
                            val u = usuariosCacheGlobal.find { it.id == uid }
                            val display = u?.nombreVisible() ?: uid
                            miembrosParaSpinner.add(Pair(display, uid))
                        }
                        // poblar adaptador con solo los textos
                        adaptMiembros.clear()
                        adaptMiembros.addAll(miembrosParaSpinner.map { it.first })
                        adaptMiembros.notifyDataSetChanged()
                    }
                }
            }

            // Opciones del formulario: se inicializan al entrar en modo creación y se
            // reinician tras un envío correcto para que la siguiente tarea no las herede.
            var fechaProgramadaTs: com.google.firebase.Timestamp? = null
            var esEmergenciaLocal = false
            var esImportanteLocal = false
            var tipoRecurrenciaLocal: String? = null
            var esRecurrenteLocal = false

            b.btnElegirFecha.setOnClickListener {
                SelectorFechaHora.elegir(requireContext(), fechaProgramadaTs) { ts ->
                    fechaProgramadaTs = ts
                    b.tvFechaProgramada.text = SelectorFechaHora.formatear(ts)
                }
            }

            // Opciones extra: recurrencia, emergencia, importante.
            // La emergencia (×1.5) está disponible para CUALQUIER categoría, no solo "personalizada".
            b.btnOpcionesExtra.setOnClickListener {
                val opts = arrayOf(
                    if (esImportanteLocal) "✅ Importante (activo)" else "⭐ Marcar como importante",
                    if (esEmergenciaLocal) "✅ Emergencia ×1.5 (activo)" else "🚨 Activar emergencia (×1.5 pts)",
                    "🔁 Recurrencia: ${tipoRecurrenciaLocal ?: "ninguna"}"
                )
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.opciones_extra_title))
                    .setItems(opts) { _, i ->
                        when (i) {
                            0 -> { esImportanteLocal = !esImportanteLocal; Toast.makeText(requireContext(), if (esImportanteLocal) "Marcada como importante" else "Importante desactivado", Toast.LENGTH_SHORT).show() }
                            1 -> { esEmergenciaLocal = !esEmergenciaLocal; Toast.makeText(requireContext(), if (esEmergenciaLocal) "Emergencia activada ×1.5" else "Emergencia desactivada", Toast.LENGTH_SHORT).show() }
                            2 -> {
                                val tipos = arrayOf("Ninguna", "Diaria", "Semanal", "Mensual")
                                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                    .setTitle(getString(R.string.tipo_recurrencia_title))
                                    .setItems(tipos) { _, ti ->
                                        tipoRecurrenciaLocal = if (ti == 0) null else tipos[ti].lowercase()
                                        esRecurrenteLocal = ti != 0
                                        Toast.makeText(requireContext(), getString(R.string.recurrencia_msg, tipoRecurrenciaLocal ?: getString(R.string.ninguna)), Toast.LENGTH_SHORT).show()
                                    }.show()
                            }
                        }
                    }.show()
            }

            b.btnCrearTarea.setOnClickListener {
                val titulo = b.etTitulo.text.toString().trim()
                if (titulo.isEmpty()) { Toast.makeText(requireContext(), getString(R.string.introduce_titulo), Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                val creadorId = LocalizadorServicios.repositorioAuth.usuarioActual()?.id
                val categoria = b.spCategoria.selectedItem as String
                val esCategoriaPersonalizada = categoria.equals("Personalizada", true) || categoria.equals("Personalizado", true)
                val puntos = if (esCategoriaPersonalizada) Constants.PUNTOS_FIJOS_PERSONALIZADA else (b.etPuntos.text.toString().toIntOrNull() ?: 0)
                val dificultadStr = b.spDificultad.selectedItem as String
                val dificultad = when (dificultadStr) { "Fácil" -> 1; "Media" -> 2; else -> 3 }
                val asignIdx = b.spAsignarA.selectedItemPosition
                val asignadoUid = if (asignIdx > 0 && asignIdx < miembrosParaSpinner.size) miembrosParaSpinner[asignIdx].second else null
                if (!creadorId.isNullOrBlank() && !asignadoUid.isNullOrBlank() && asignadoUid == creadorId) {
                    Toast.makeText(requireContext(), getString(R.string.no_autoasignar), Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val multiplicador = if (esEmergenciaLocal) Constants.MULTIPLICADOR_EMERGENCIA else 1.0

                lifecycleScope.launch {
                    // Garantizar grupoId: si el grupo aún no está cargado, intentar cargarlo una vez.
                    // Sin grupo la tarea queda invisible en las vistas de grupo, así que se falla.
                    var grupoId = parejaVM.grupo.value?.id
                    if (grupoId.isNullOrBlank() && !creadorId.isNullOrBlank()) {
                        try { parejaVM.cargarGrupoPorUsuario(creadorId) } catch (_: Exception) { }
                        grupoId = parejaVM.grupo.value?.id
                    }
                    if (grupoId.isNullOrBlank()) {
                        Toast.makeText(requireContext(), getString(R.string.error_grupo_no_disponible), Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    val tarea = Tarea(
                        titulo = titulo, puntos = puntos, creadoPor = creadorId,
                        categoria = categoria, dificultad = dificultad, asignadoA = asignadoUid,
                        fechaProgramada = fechaProgramadaTs,
                        esEmergencia = esEmergenciaLocal, multiplicadorPuntos = multiplicador,
                        esRecurrente = esRecurrenteLocal, tipoRecurrencia = tipoRecurrenciaLocal,
                        esImportante = esImportanteLocal,
                        grupoId = grupoId
                    )
                    val res = LocalizadorServicios.repositorioTarea.crearTarea(tarea)
                    if (res.isSuccess) {
                        val creado = res.getOrNull()
                        if (creado != null) programarRecordatorio(creado)
                        Toast.makeText(requireContext(), getString(R.string.tarea_creada), Toast.LENGTH_SHORT).show()
                        // Reiniciar las opciones del formulario tras un envío correcto.
                        esEmergenciaLocal = false
                        esImportanteLocal = false
                        tipoRecurrenciaLocal = null
                        esRecurrenteLocal = false
                        fechaProgramadaTs = null
                        b.tvFechaProgramada.text = getString(R.string.sin_fecha)
                        // Notificar al asignado vía Firebase
                        if (!asignadoUid.isNullOrBlank() && creado != null) {
                            Log.d(TAG, "Enviando notificación Firebase (formulario): tipo=asignacion, destinatario=$asignadoUid, tareaId=${creado.id}")
                            val repoNot = RepositorioNotificaciones()
                            repoNot.enviarNotificacion(
                                Notificacion(
                                    id = "", tipo = "asignacion",
                                    contenido = mapOf("tareaId" to creado.id, "titulo" to creado.titulo, "desde" to (creadorId ?: "")),
                                    destinatario = asignadoUid, visto = false, fecha = Timestamp.now()
                                )
                            )
                        }
                        findNavController().navigate(R.id.fragment_PgPrincipal)
                    } else Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Si venimos en modo detalle con taskId, cargar y mostrar
        val taskIdArg = navArgs?.taskId
        if (!taskIdArg.isNullOrBlank() && crearBinding == null && listaBinding == null) {
            // estamos en la vista detalle (layout inflado manualmente)
            val tvTitulo = view.findViewById<TextView>(R.id.tvDetalleTitulo)
            val tvMeta = view.findViewById<TextView>(R.id.tvDetalleMeta)
            val tvFechaHora = view.findViewById<TextView>(R.id.tvDetalleFechaHora)
            val tvEstadoChip = view.findViewById<TextView>(R.id.tvDetalleEstadoChip)
            val tvAsignado = view.findViewById<TextView>(R.id.tvDetalleAsignado)
            val tvDesc = view.findViewById<TextView>(R.id.tvDetalleDescripcion)
            val btnAccion = view.findViewById<Button>(R.id.btnDetalleAccion)
            val btnMas = view.findViewById<Button>(R.id.btnDetalleMas)
            val btnAsignar = view.findViewById<Button>(R.id.btnDetalleAsignar)
            val cardCancelarRecurrencia = view.findViewById<View>(R.id.cardCancelarRecurrencia)
            val btnCancelarRecurrencia = view.findViewById<Button>(R.id.btnDetalleCancelarRecurrencia)
            val cardDetalle = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardDetallePrincipal)

            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    val res = LocalizadorServicios.repositorioTarea.obtenerTareas()
                    if (res.isSuccess) {
                        val tarea = res.getOrNull()?.firstOrNull { it.id == taskIdArg }
                        if (tarea != null) {
                            tvTitulo.text = tarea.titulo
                            val dif = when (tarea.dificultad) {1->"Fácil";2->"Media";else->"Difícil"}
                        // En emergencia se muestra el valor efectivo (puntos × multiplicador), no el base.
                        val puntosMostrados = (tarea.puntos * tarea.multiplicadorPuntos.coerceAtLeast(1.0)).toInt()
                        tvMeta.text = "$puntosMostrados pts · $dif${if (tarea.esEmergencia) " · 🚨 Emergencia ×${tarea.multiplicadorPuntos}" else ""}${if (tarea.esRecurrente) " · 🔄 Recurrente" else ""}${if (tarea.estado == "reclamada") " · ${getString(R.string.tarea_en_disputa)}" else ""}"
                        tvDesc.text = tarea.descripcion ?: ""

                        // Fecha/hora y estado con la presentación centralizada de TareaUi.
                        tvFechaHora.text = TareaUi.formatearFechaHora(tarea.fechaProgramada)
                        tvEstadoChip.text = TareaUi.etiquetaEstado(tarea.estado)
                        tvEstadoChip.background = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.bg_estado_chip)?.apply {
                            setTint(requireContext().getColor(TareaUi.colorEstado(tarea.estado)))
                        }
                        tvEstadoChip.setTextColor(requireContext().getColor(TareaUi.colorTextoEstado(tarea.estado)))

                        // Marca visual de emergencia (borde rojo) / recurrencia (borde violeta) en la
                        // tarjeta de detalle. Si es ambas, el borde rojo de emergencia tiene prioridad.
                        if (tarea.esEmergencia) {
                            cardDetalle?.strokeColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.emergencia)
                            cardDetalle?.strokeWidth = resources.getDimensionPixelSize(R.dimen.stroke_thick)
                        } else if (tarea.esRecurrente) {
                            cardDetalle?.strokeColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.recurrente)
                            cardDetalle?.strokeWidth = resources.getDimensionPixelSize(R.dimen.stroke_thick)
                        } else {
                            cardDetalle?.strokeColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.divisor)
                            cardDetalle?.strokeWidth = resources.getDimensionPixelSize(R.dimen.stroke_thin)
                        }

                        // Mostramos la opción de asignar siempre al creador cuando la tarea esté pendiente; no mostramos a quién está asignada aquí
                        val usuarioActualId = LocalizadorServicios.repositorioAuth.usuarioActual()?.id ?: ""

                        // Cancelar recurrencia: solo el creador (asignador) de una instancia
                        // recurrente activa y pendiente. Al cancelar, la instancia pendiente se
                        // elimina (soft delete) y no se genera ninguna repetición posterior.
                        if (tarea.esRecurrente && tarea.estado == "pendiente" && !usuarioActualId.isBlank() && usuarioActualId == tarea.creadoPor) {
                            cardCancelarRecurrencia.visibility = View.VISIBLE
                            btnCancelarRecurrencia.setOnClickListener {
                                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                    .setTitle(getString(R.string.cancelar_recurrencia))
                                    .setMessage(getString(R.string.cancelar_recurrencia_confirm))
                                    .setPositiveButton(getString(R.string.confirmar)) { _, _ ->
                                        lifecycleScope.launch {
                                            // Soft delete: la instancia desaparece de las listas y
                                            // el repositorio libera la reserva del creador.
                                            val actualizada = tarea.copy(esRecurrente = false, tipoRecurrencia = null, estado = "eliminada")
                                            val resRec = LocalizadorServicios.repositorioTarea.actualizarTarea(actualizada)
                                            if (resRec.isSuccess) {
                                                Toast.makeText(requireContext(), getString(R.string.recurrencia_cancelada), Toast.LENGTH_SHORT).show()
                                                cardCancelarRecurrencia.visibility = View.GONE
                                            } else {
                                                Toast.makeText(requireContext(), resRec.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                    .setNegativeButton(getString(R.string.cancelar), null)
                                    .show()
                            }
                        } else {
                            cardCancelarRecurrencia.visibility = View.GONE
                        }

                        if (!usuarioActualId.isBlank() && usuarioActualId == tarea.creadoPor && tarea.estado == "pendiente") {
                            tvAsignado.text = ""
                            btnAsignar.visibility = View.VISIBLE
                            btnAsignar.setOnClickListener {
                                lifecycleScope.launch {
                                    val grupo = parejaVM.grupo.value
                                    val usuarios = try { LocalizadorServicios.repositorioAuth.observarUsuarios().first() } catch (_: Exception) { emptyList<es.sintaxys.teamtask.modelo.Usuario>() }
                                    val opciones = mutableListOf<Pair<String,String>>()
                                    if (grupo != null) {
                                        val uidActual = LocalizadorServicios.repositorioAuth.usuarioActual()?.id
                                        grupo.miembros.keys.forEach { uid ->
                                            if (!uidActual.isNullOrBlank() && uid == uidActual) return@forEach
                                            val u2 = usuarios.find { it.id == uid }
                                            val display = u2?.nombreVisible() ?: uid
                                            opciones.add(Pair(display, uid))
                                        }
                                    }
                                    if (opciones.isEmpty()) Toast.makeText(requireContext(), getString(R.string.no_hay_miembros), Toast.LENGTH_SHORT).show() else {
                                        val names = opciones.map { it.first }.toTypedArray()
                                        androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle(getString(R.string.selecciona_miembro)).setItems(names) { _, idx ->
                                            lifecycleScope.launch {
                                                val elegido = opciones[idx].second
                                                if (!usuarioActualId.isBlank() && elegido == usuarioActualId) {
                                                    Toast.makeText(requireContext(), getString(R.string.no_autoasignar), Toast.LENGTH_LONG).show()
                                                    return@launch
                                                }
                                                val nueva = tarea.copy(asignadoA = elegido, grupoId = parejaVM.grupo.value?.id)
                                                val res2 = LocalizadorServicios.repositorioTarea.actualizarTarea(nueva)
                                                if (res2.isSuccess) {
                                                    Toast.makeText(requireContext(), getString(R.string.tarea_asignada), Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(requireContext(), res2.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }.setNegativeButton(getString(R.string.cancelar), null).show()
                                    }
                                }
                            }
                        } else {
                            btnAsignar.visibility = View.GONE
                        }

                        // configurar botones coherentes (sin mostrar a quién se asignó aquí)
                        btnMas.setOnClickListener {
                            val uid2 = LocalizadorServicios.repositorioAuth.usuarioActual()?.id ?: ""
                            val esPersonalizada = tarea.categoria.equals("personalizado", true) || tarea.categoria.equals("personalizada", true)
                            val opciones = when {
                                (tarea.estado == "pendiente_confirmacion" || tarea.estado == "completada") && uid2 == tarea.creadoPor -> arrayOf(getString(R.string.confirmar), getString(R.string.reclamar))
                                tarea.estado == "pendiente" && esPersonalizada && uid2 == tarea.creadoPor -> arrayOf(getString(R.string.editar), getString(R.string.eliminar))
                                tarea.estado == "pendiente" && uid2 == tarea.creadoPor -> arrayOf(getString(R.string.eliminar))
                                else -> emptyArray()
                            }
                            if (opciones.isEmpty()) return@setOnClickListener
                            androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle(getString(R.string.opciones))
                                .setItems(opciones) { _, idx ->
                                    // acciones simples para ejemplo
                                    when (opciones[idx]) {
                                        getString(R.string.editar) -> mostrarDialogoEditar(tarea)
                                        getString(R.string.eliminar) -> lifecycleScope.launch { LocalizadorServicios.repositorioTarea.actualizarTarea(tarea.copy(estado = "eliminada")) }
                                        getString(R.string.confirmar) -> {
                                            // deshabilitar botón para evitar doble click
                                            btnAccion.isEnabled = false
                                            ultimoBotonConfirmar = btnAccion
                                            tareasVM.confirmarTarea(tarea.id, tarea.creadoPor ?: "")
                                            // El observer maneja el resultado y re-habilita el botón
                                        }
                                        getString(R.string.reclamar) -> solicitarReclamo(tarea)
                                    }
                                }.setNegativeButton(getString(R.string.cancelar), null).show()
                        }

                        // BUG B: la opción "Responder disputa" solo debe ofrecerse mientras la
                        // disputa siga sin respuesta de B. responderDisputa() la pasa a
                        // "en_progreso" y sella respondidoPor (ver RepositorioDisputas), así que
                        // se consulta la disputa una única vez al abrir el detalle de una tarea
                        // en disputa (no en cada bind de lista).
                        val disputaYaRespondida = if (tarea.estado == "reclamada") {
                            try {
                                val d = repoDisputas.listarDisputasPorTarea(tarea.id).getOrNull()?.firstOrNull()
                                d != null && (d.estado == "en_progreso" || !d.respondidoPor.isNullOrBlank())
                            } catch (_: Exception) {
                                false
                            }
                        } else false

                        val uid = LocalizadorServicios.repositorioAuth.usuarioActual()?.id ?: ""
                        when {
                            !uid.isBlank() && uid == tarea.creadoPor && (tarea.estado == "pendiente_confirmacion" || tarea.estado == "completada") -> {
                                btnAccion.text = getString(R.string.confirmar)
                                btnAccion.setOnClickListener {
                                    btnAccion.isEnabled = false
                                    ultimoBotonConfirmar = btnAccion
                                    tareasVM.confirmarTarea(tarea.id, tarea.creadoPor ?: "")
                                    // El observer maneja el resultado y muestra Toast
                                }
                            }
                            !uid.isBlank() && uid == tarea.creadoPor && tarea.estado == "reclamada" -> {
                                btnAccion.visibility = View.VISIBLE
                                btnAccion.isEnabled = true
                                btnAccion.text = getString(R.string.resolver_reclamo)
                                btnAccion.setOnClickListener { mostrarDialogoResolverReclamo(tarea) }
                            }
                            // B (asignado): puede responder la disputa abierta por el creador
                            // UNA sola vez. Tras responder (disputa en_progreso / respondidoPor
                            // no nulo) la opción desaparece y solo queda pendiente la resolución
                            // de A. La tarea sigue en "reclamada" hasta que A la resuelva.
                            !uid.isBlank() && uid == tarea.asignadoA && tarea.estado == "reclamada" && !disputaYaRespondida -> {
                                btnAccion.visibility = View.VISIBLE
                                btnAccion.isEnabled = true
                                btnAccion.text = getString(R.string.responder_disputa)
                                btnAccion.setOnClickListener { solicitarRespuestaDisputa(tarea) }
                            }
                            !uid.isBlank() && uid == tarea.asignadoA && tarea.estado == "pendiente" -> {
                                btnAccion.text = getString(R.string.completar_btn)
                                btnAccion.setOnClickListener {
                                    tareasVM.marcarCompletada(tarea.id, uid)
                                    // El observer maneja el resultado y muestra Toast
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Alcance acotado: solo se limpian los bindings que este Fragment infla directamente.
        listaBinding = null
        crearBinding = null
    }

    // Vuelve al menú principal (Inicio) tras una acción completada correctamente,
    // cerrando el destino actual para no apilar pantallas duplicadas.
    private fun volverAInicio() {
        try {
            val navController = findNavController()
            val actual = navController.currentDestination?.id ?: return
            val opciones = androidx.navigation.NavOptions.Builder()
                .setPopUpTo(actual, true)
                .setLaunchSingleTop(true)
                .build()
            navController.navigate(R.id.fragment_PgPrincipal, null, opciones)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo volver a Inicio: ${e.message}")
        }
    }

    // Agenda el recordatorio local de una tarea programada (minutosAntes antes de su fecha).
    // Si la tarea no tiene fecha programada no hace nada.
    private fun programarRecordatorio(tarea: Tarea) {
        val fecha = tarea.fechaProgramada ?: return
        val trigger = fecha.toDate().time - tarea.minutosAntes * Constants.SECONDS_PER_MINUTE * Constants.MILLIS_PER_SECOND
        NotificationScheduler.scheduleReminder(
            requireContext(),
            tarea.id,
            getString(R.string.recordatorio_tarea_title, tarea.titulo),
            getString(R.string.recordatorio_tarea_msg, SelectorFechaHora.formatear(fecha)),
            trigger
        )
    }

    // Colorea el indicador lateral según la dificultad usando el mismo drawable tintado que
    // TareasHomeAdapter (verde = fácil, naranja = media, rojo = difícil).
    private fun pintarIndicadorDificultad(vIndicator: View?, dificultad: Int) {
        val colorRes = when (dificultad) {
            1 -> R.color.verde
            2 -> R.color.naranja
            else -> R.color.rojo
        }
        val ctx = requireContext()
        vIndicator?.background = androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.v_indicador_dificultad)?.apply {
            setTint(androidx.core.content.ContextCompat.getColor(ctx, colorRes))
        }
    }

    // Adapter simple
    private inner class TareasAdapter : RecyclerView.Adapter<TareasAdapter.VH>() {
        private var items: List<Tarea> = emptyList()
        fun setItems(list: List<Tarea>) { items = list; notifyDataSetChanged() }

        inner class VH(val root: View) : RecyclerView.ViewHolder(root) {
            val tvTitulo: TextView = root.findViewById(R.id.tvTituloTarea)
            val tvDificultad: TextView = root.findViewById(R.id.tvDificultad)
            val tvMeta: TextView = root.findViewById(R.id.tvMetaTarea)
            val tvPuntos: TextView = root.findViewById(R.id.tvPuntosTarea)
            val tvFechaHora: TextView = root.findViewById(R.id.tvFechaHoraTarea)
            val tvEstadoChip: TextView = root.findViewById(R.id.tvEstadoChip)
            val tvAsignado: TextView = root.findViewById(R.id.tvAsignado)
            val btnAccion: Button = root.findViewById(R.id.btnAccionTarea)
            val cardRoot: com.google.android.material.card.MaterialCardView = root.findViewById(R.id.cardRoot)
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
            val dif = when (tarea.dificultad) {
                1 -> getString(R.string.dificultad_facil)
                2 -> getString(R.string.dificultad_media)
                else -> getString(R.string.dificultad_dificil)
            }
            holder.tvDificultad.text = dif
            // En emergencia se muestra el valor efectivo (puntos × multiplicador), no el base.
            val puntosMostrados = (tarea.puntos * tarea.multiplicadorPuntos.coerceAtLeast(1.0)).toInt()
            holder.tvPuntos.text = "$puntosMostrados pts"

            // El estado ahora vive en el chip (tvEstadoChip); la meta solo conserva las marcas de
            // emergencia/recurrencia. Sin marcas se oculta para no dejar una línea vacía.
            val marcas = buildString {
                if (tarea.esEmergencia) append("🚨")
                if (tarea.esRecurrente) { if (isNotEmpty()) append(" "); append("🔄") }
            }
            if (marcas.isNotEmpty()) {
                holder.tvMeta.text = marcas
                holder.tvMeta.visibility = View.VISIBLE
            } else {
                holder.tvMeta.visibility = View.GONE
            }

            // Fecha programada: ocultar la fila si no hay fecha en vez de mostrar "Sin fecha".
            if (tarea.fechaProgramada != null) {
                holder.tvFechaHora.text = TareaUi.formatearFechaHora(tarea.fechaProgramada)
                holder.tvFechaHora.visibility = View.VISIBLE
            } else {
                holder.tvFechaHora.visibility = View.GONE
            }

            // Chip de estado: etiqueta, fondo y color de texto centralizados en TareaUi.
            holder.tvEstadoChip.text = TareaUi.etiquetaEstado(tarea.estado)
            holder.tvEstadoChip.background = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.bg_estado_chip)?.apply {
                setTint(androidx.core.content.ContextCompat.getColor(requireContext(), TareaUi.colorEstado(tarea.estado)))
            }
            holder.tvEstadoChip.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), TareaUi.colorTextoEstado(tarea.estado)))

            // Marca visual de emergencia (borde rojo) y recurrencia (borde violeta). Se resetea en
            // cada bind porque el holder se recicla. Si es ambas, el rojo de emergencia tiene prioridad.
            if (tarea.esEmergencia) {
                holder.cardRoot.strokeColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.emergencia)
                holder.cardRoot.strokeWidth = resources.getDimensionPixelSize(R.dimen.stroke_thick)
            } else if (tarea.esRecurrente) {
                holder.cardRoot.strokeColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.recurrente)
                holder.cardRoot.strokeWidth = resources.getDimensionPixelSize(R.dimen.stroke_thick)
            } else {
                holder.cardRoot.strokeColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.divisor)
                holder.cardRoot.strokeWidth = resources.getDimensionPixelSize(R.dimen.stroke_thin)
            }

            // No mostrar asignación en la tarjeta de lista (se gestiona en detalle)
            holder.tvAsignado.visibility = View.GONE

            // indicador lateral por dificultad (verde/amarillo/rojo) con el mismo drawable tintado
            pintarIndicadorDificultad(holder.vIndicator, tarea.dificultad)

            // El fondo por estado se eliminó: duplicaba el mensaje del chip (tvEstadoChip) con una
            // paleta distinta y con colores hardcodeados que no respetaban el tema oscuro. El chip
            // es ahora la única fuente del estado.

            holder.btnAccion.setOnClickListener(null)
            // Solo tareas personalizadas permiten edición (long press eliminado para predefinidas)
            // Simplificar lista: solo permitir "Completar" si soy el asignado y la tarea está pendiente
            if (!usuarioId.isBlank() && usuarioId == tarea.asignadoA && tarea.estado == "pendiente") {
                holder.btnAccion.visibility = View.VISIBLE
                holder.btnAccion.isEnabled = true
                holder.btnAccion.text = getString(R.string.completar_btn)
                holder.btnAccion.setOnClickListener {
                    tareasVM.marcarCompletada(tarea.id, usuarioId)
                    // El observer maneja el resultado y muestra Toast
                }
            } else if (!usuarioId.isBlank() && usuarioId == tarea.creadoPor && tarea.estado == "pendiente") {
                // Si soy el creador y la tarea está pendiente, permitir asignar/reasignar desde la lista
                holder.btnAccion.visibility = View.VISIBLE
                holder.btnAccion.isEnabled = true
                holder.btnAccion.text = getString(R.string.asignar)
                holder.btnAccion.setOnClickListener {
                    lifecycleScope.launch {
                        val grupo = parejaVM.grupo.value
                        val usuarios = try { LocalizadorServicios.repositorioAuth.observarUsuarios().first() } catch (_: Exception) { emptyList<es.sintaxys.teamtask.modelo.Usuario>() }
                        val opciones = mutableListOf<Pair<String,String>>()
                        if (grupo != null) {
                            val uidActual = LocalizadorServicios.repositorioAuth.usuarioActual()?.id
                            grupo.miembros.keys.forEach { uid ->
                                if (!uidActual.isNullOrBlank() && uid == uidActual) return@forEach
                                val u2 = usuarios.find { it.id == uid }
                                val display = u2?.nombreVisible() ?: uid
                                opciones.add(Pair(display, uid))
                            }
                        }
                        if (opciones.isEmpty()) Toast.makeText(requireContext(), getString(R.string.no_hay_miembros), Toast.LENGTH_SHORT).show() else {
                            val names = opciones.map { it.first }.toTypedArray()
                            androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle(getString(R.string.selecciona_miembro)).setItems(names) { _, idx ->
                                lifecycleScope.launch {
                                    val elegido = opciones[idx].second
                                    if (!usuarioId.isBlank() && elegido == usuarioId) {
                                        Toast.makeText(requireContext(), getString(R.string.no_autoasignar), Toast.LENGTH_LONG).show()
                                        return@launch
                                    }
                                    val nueva = tarea.copy(asignadoA = elegido, grupoId = parejaVM.grupo.value?.id)
                                    val res2 = LocalizadorServicios.repositorioTarea.actualizarTarea(nueva)
                                    if (res2.isSuccess) {
                                        Toast.makeText(requireContext(), getString(R.string.tarea_asignada), Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(requireContext(), res2.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }.setNegativeButton(getString(R.string.cancelar), null).show()
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
    private inner class SugeridasAdapter(private val items: List<es.sintaxys.teamtask.modelo.TareaSugerida>, private val categoriaId: String) : RecyclerView.Adapter<SugeridasAdapter.SV>() {
        inner class SV(val root: View) : RecyclerView.ViewHolder(root) {
            val tvTitulo: TextView = root.findViewById(R.id.tvTituloTarea)
            val tvDificultad: TextView = root.findViewById(R.id.tvDificultad)
            val tvMeta: TextView = root.findViewById(R.id.tvMetaTarea)
            val tvPuntos: TextView = root.findViewById(R.id.tvPuntosTarea)
            val tvFechaHora: TextView = root.findViewById(R.id.tvFechaHoraTarea)
            val tvEstadoChip: TextView = root.findViewById(R.id.tvEstadoChip)
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
            holder.tvPuntos.text = "${sug.puntos} pts"
            // Las sugeridas son catálogo, no tareas reales: no tienen estado ni fecha. Se ocultan
            // explícitamente sus views para no dejar placeholders vacíos, y la meta se oculta porque
            // ya no muestra los puntos (viven en tvPuntosTarea).
            holder.tvMeta.visibility = View.GONE
            holder.tvFechaHora.visibility = View.GONE
            holder.tvEstadoChip.visibility = View.GONE
            holder.btnAccion.visibility = View.VISIBLE
            holder.btnAccion.text = getString(R.string.asignar)

            // La dificultad de las sugerencias viene en mayúsculas (FACIL/MEDIA/DIFICIL):
            // se normaliza a una etiqueta legible y a un nivel numérico para el indicador.
            val dificultadIndicador = when (sug.dificultad.lowercase()) {
                "fácil", "facil" -> 1
                "media" -> 2
                else -> 3
            }
            holder.tvDificultad.text = when (dificultadIndicador) {
                1 -> getString(R.string.dificultad_facil)
                2 -> getString(R.string.dificultad_media)
                else -> getString(R.string.dificultad_dificil)
            }
            // indicador lateral por dificultad (verde/amarillo/rojo)
            pintarIndicadorDificultad(holder.vIndicator, dificultadIndicador)

            holder.btnAccion.setOnClickListener {
                // abrir selector de miembro para crear una nueva instancia de tarea
                lifecycleScope.launch {
                    Log.d(TAG, "Intentando asignar sugerida: ${sug.titulo}")
                    val grupo = parejaVM.grupo.value
                    val usuarios = try { LocalizadorServicios.repositorioAuth.observarUsuarios().first() } catch (_: Exception) { emptyList<es.sintaxys.teamtask.modelo.Usuario>() }
                    val opciones = mutableListOf<Pair<String,String>>()
                    if (grupo != null) {
                        val uidActual = LocalizadorServicios.repositorioAuth.usuarioActual()?.id
                        grupo.miembros.keys.forEach { uid ->
                            if (!uidActual.isNullOrBlank() && uid == uidActual) return@forEach
                            val u2 = usuarios.find { it.id == uid }
                            val display = u2?.nombreVisible() ?: uid
                            opciones.add(Pair(display, uid))
                        }
                    }
                    if (opciones.isEmpty()) {
                        Toast.makeText(requireContext(), getString(R.string.no_hay_miembros), Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    val nombres = opciones.map { it.first }.toTypedArray()
                    androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle(getString(R.string.selecciona_miembro)).setItems(nombres) { _, idx ->
                        val elegidoUid = opciones[idx].second.ifBlank { null }
                        val creador = LocalizadorServicios.repositorioAuth.usuarioActual()?.id
                        if (!creador.isNullOrBlank() && !elegidoUid.isNullOrBlank() && elegidoUid == creador) {
                            Toast.makeText(requireContext(), getString(R.string.no_autoasignar), Toast.LENGTH_LONG).show()
                            return@setItems
                        }
                        // Fecha/hora OBLIGATORIA: si el usuario cancela el picker, no se crea la tarea.
                        SelectorFechaHora.elegir(requireContext()) { fechaElegida ->
                            // Tras elegir fecha/hora, ofrecer emergencia (×1.5) también para tareas estándar.
                            val etiquetaEmergencia = arrayOf(getString(R.string.emergencia_opcion))
                            val seleccionEmergencia = booleanArrayOf(false)
                            var esEmergencia = false
                            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setTitle(getString(R.string.opciones_extra_title))
                                .setMultiChoiceItems(etiquetaEmergencia, seleccionEmergencia) { _, _, checked -> esEmergencia = checked }
                                .setPositiveButton(getString(R.string.crear)) { _, _ ->
                                    lifecycleScope.launch {
                                        val dificultadInt = when (sug.dificultad.lowercase()) { "fácil", "facil" -> 1; "media" -> 2; else -> 3 }
                                        val multiplicador = if (esEmergencia) Constants.MULTIPLICADOR_EMERGENCIA else 1.0
                                        val tarea = Tarea(titulo = sug.titulo, descripcion = sug.descripcion, categoria = categoriaId, dificultad = dificultadInt, puntos = sug.puntos, creadoPor = creador, asignadoA = elegidoUid, grupoId = parejaVM.grupo.value?.id, fechaProgramada = fechaElegida, esEmergencia = esEmergencia, multiplicadorPuntos = multiplicador)
                                        Log.d(TAG, "Creando tarea desde sugerida: titulo=${tarea.titulo} asignadoA=${tarea.asignadoA}")
                                        val res = LocalizadorServicios.repositorioTarea.crearTarea(tarea)
                                        if (res.isSuccess) {
                                            val creada = res.getOrNull()
                                            Log.d(TAG, "Tarea creada OK: ${creada?.id}")
                                            if (creada != null) programarRecordatorio(creada)
                                            Toast.makeText(requireContext(), getString(R.string.tarea_asignada_ok, opciones[idx].first), Toast.LENGTH_SHORT).show()
                                            // Notificar al asignado vía Firebase para que reciba la notificación en su dispositivo
                                            if (!elegidoUid.isNullOrBlank()) {
                                                Log.d(TAG, "Enviando notificación Firebase (sugerida): tipo=asignacion, destinatario=$elegidoUid, tareaId=${creada?.id}")
                                                val repoNot = RepositorioNotificaciones()
                                                val notifResult = repoNot.enviarNotificacion(
                                                    Notificacion(
                                                        id = "",
                                                        tipo = "asignacion",
                                                        contenido = mapOf(
                                                            "tareaId" to (creada?.id ?: ""),
                                                            "titulo" to tarea.titulo,
                                                            "desde" to (creador ?: "")
                                                        ),
                                                        destinatario = elegidoUid,
                                                        visto = false,
                                                        fecha = Timestamp.now()
                                                    )
                                                )
                                                if (notifResult.isSuccess) {
                                                    Log.d(TAG, "Notificación de sugerida enviada OK, id=${notifResult.getOrNull()}")
                                                } else {
                                                    Log.e(TAG, "Error enviando notificación de sugerida: ${notifResult.exceptionOrNull()?.message}")
                                                }
                                            }
                                            // opcional: si quieres volver atrás para ver la lista real, descomenta:
                                            // findNavController().popBackStack()
                                        } else {
                                            Log.e(TAG, "Error crear tarea: ${res.exceptionOrNull()?.message}")
                                            Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                                .setNegativeButton(getString(R.string.cancelar), null)
                                .show()
                        }
                    }.setNegativeButton(getString(R.string.cancelar), null).show()
                }
            }

            // click en la tarjeta puede mostrar detalles de la sugerencia si se quiere
            holder.root.setOnClickListener { /* opcional: mostrar info */ }
        }

        override fun getItemCount(): Int = items.size
    }

    /** Paso previo al adjuntar evidencia: recoge un motivo opcional y abre el selector de imagen. */
    private fun solicitarReclamo(tarea: Tarea) {
        val entrada = android.widget.EditText(requireContext()).apply {
            hint = getString(R.string.motivo_reclamo_hint)
            setPadding(48, 24, 48, 24)
        }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.reclamar))
            .setView(entrada)
            .setPositiveButton(getString(R.string.continuar)) { _, _ ->
                pendingMotivoReclamo = entrada.text.toString().trim().ifBlank { null }
                pendingTareaParaDisputa = tarea
                pendingAccionDisputa = AccionDisputa.ABRIR
                pickImageLauncher?.launch("image/*")
            }
            .setNegativeButton(getString(R.string.cancelar), null)
            .show()
    }

    /**
     * Entrada de B (asignado): muestra el reclamo de A (motivo + evidencia) y le permite
     * responder con su propia versión y foto. La resolución sigue siendo de A.
     */
    private fun solicitarRespuestaDisputa(tarea: Tarea) {
        lifecycleScope.launch {
            val disputa = try {
                repoDisputas.listarDisputasPorTarea(tarea.id).getOrNull()?.firstOrNull()
            } catch (_: Exception) {
                null
            }
            val contenido = android.widget.LinearLayout(requireContext()).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(48, 24, 48, 24)
            }
            val tvMsg = TextView(requireContext()).apply {
                text = buildString {
                    append(getString(R.string.reclamo_abierto_msg))
                    if (!tarea.motivoReclamo.isNullOrBlank()) {
                        append("\n\n")
                        append(getString(R.string.motivo_reclamo_titulo, tarea.motivoReclamo))
                    }
                }
            }
            contenido.addView(tvMsg)

            // La evidencia viaja como base64 dentro del documento de la disputa: se decodifica a
            // un Bitmap para mostrarla (Glide solo cargaría URLs, que ya no existen).
            val bitmapEvidencia = decodificarEvidenciaBase64(disputa?.pruebas?.firstOrNull())
            if (bitmapEvidencia != null) {
                val iv = android.widget.ImageView(requireContext()).apply {
                    adjustViewBounds = true
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    contentDescription = getString(R.string.evidencia_reclamo_titulo)
                    setImageBitmap(bitmapEvidencia)
                }
                contenido.addView(iv)
            }

            val entrada = android.widget.EditText(requireContext()).apply {
                hint = getString(R.string.motivo_respuesta_hint)
                setPadding(0, 24, 0, 0)
            }
            contenido.addView(entrada)

            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.responder_disputa_titulo))
                .setView(contenido)
                .setPositiveButton(getString(R.string.continuar)) { _, _ ->
                    pendingMotivoReclamo = entrada.text.toString().trim().ifBlank { null }
                    pendingTareaParaDisputa = tarea
                    pendingAccionDisputa = AccionDisputa.RESPONDER
                    pickImageLauncher?.launch("image/*")
                }
                .setNegativeButton(getString(R.string.cancelar), null)
                .show()
        }
    }

    /** A abre la disputa: crea el documento, pasa la tarea a "reclamada" y notifica a B. */
    private suspend fun abrirDisputaDeTarea(tarea: Tarea, motivo: String?, evidenciaBase64: String?) {
        val reclamante = LocalizadorServicios.repositorioAuth.usuarioActual()?.id ?: ""
        val disputa = Disputa(
            id = "",
            tareaId = tarea.id,
            iniciador = reclamante,
            estado = "abierta",
            pruebas = evidenciaBase64?.let { listOf(it) } ?: emptyList(),
            fechaCreacion = Timestamp.now()
        )
        val resDisputa = repoDisputas.abrirDisputa(disputa)
        if (resDisputa.isFailure) {
            Toast.makeText(
                requireContext(),
                getString(R.string.error_abrir_disputa, resDisputa.exceptionOrNull()?.message ?: ""),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        // La tarea pasa a "reclamada" para que el asignado pueda responder y el creador resolver.
        val actualizada = tarea.copy(
            estado = "reclamada",
            fechaReclamada = Timestamp.now(),
            reclamadoPor = reclamante,
            motivoReclamo = motivo
        )
        val resUpd = LocalizadorServicios.repositorioTarea.actualizarTarea(actualizada)
        if (resUpd.isFailure) {
            Toast.makeText(requireContext(), getString(R.string.error_marcar_reclamada), Toast.LENGTH_LONG).show()
            return
        }
        notificarDisputa("disputa_abierta", tarea.asignadoA, tarea, reclamante)
        Toast.makeText(requireContext(), getString(R.string.disputa_creada), Toast.LENGTH_SHORT).show()
        volverAInicio()
    }

    /** B responde la disputa con su versión; A queda habilitado para resolver. */
    private suspend fun responderDisputaDeTarea(tarea: Tarea, motivo: String?, evidenciaBase64: String?) {
        val respondidoPor = LocalizadorServicios.repositorioAuth.usuarioActual()?.id ?: ""
        val res = repoDisputas.responderDisputa(
            tareaId = tarea.id,
            respondidoPor = respondidoPor,
            motivo = motivo,
            pruebas = evidenciaBase64?.let { listOf(it) } ?: emptyList()
        )
        if (res.isFailure) {
            Toast.makeText(
                requireContext(),
                getString(R.string.error_responder_disputa, res.exceptionOrNull()?.message ?: ""),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        notificarDisputa("disputa_respondida", tarea.creadoPor, tarea, respondidoPor)
        Toast.makeText(requireContext(), getString(R.string.disputa_respondida), Toast.LENGTH_SHORT).show()
        volverAInicio()
    }

    /**
     * Notifica al otro miembro una acción del flujo de disputas. Fuera de transacción y con
     * fallos ignorados a propósito: la disputa ya quedó registrada en Firestore.
     */
    private suspend fun notificarDisputa(tipo: String, destinatario: String?, tarea: Tarea, desde: String) {
        if (destinatario.isNullOrBlank()) return
        try {
            RepositorioNotificaciones().enviarNotificacion(
                Notificacion(
                    id = "",
                    tipo = tipo,
                    contenido = mapOf("tareaId" to tarea.id, "titulo" to tarea.titulo, "desde" to desde),
                    destinatario = destinatario,
                    visto = false,
                    fecha = Timestamp.now()
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo enviar notificación de disputa ($tipo): ${e.message}")
        }
    }

    /**
     * Decodifica la evidencia guardada como base64 (JPEG) en el documento de la disputa.
     * Devuelve `null` cuando no hay evidencia o el base64 es inválido.
     */
    private fun decodificarEvidenciaBase64(base64: String?): Bitmap? {
        if (base64.isNullOrBlank()) return null
        val bytes = AvatarImagen.decodificarBase64(base64) ?: return null
        return AvatarImagen.decodificarJpeg(bytes)
    }

    /** Muestra la evidencia del reclamo y permite aceptarlo (confirma y transfiere) o rechazarlo. */
    private fun mostrarDialogoResolverReclamo(tarea: Tarea) {
        lifecycleScope.launch {
            val disputa = try {
                repoDisputas.listarDisputasPorTarea(tarea.id).getOrNull()?.firstOrNull()
            } catch (_: Exception) {
                null
            }
            val contenido = android.widget.LinearLayout(requireContext()).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(48, 24, 48, 24)
            }
            val tvMsg = TextView(requireContext()).apply {
                text = buildString {
                    append(getString(R.string.reclamo_pendiente_msg))
                    if (!tarea.motivoReclamo.isNullOrBlank()) {
                        append("\n\n")
                        append(getString(R.string.motivo_reclamo_titulo, tarea.motivoReclamo))
                    }
                }
            }
            contenido.addView(tvMsg)

            // Evidencia del reclamo (base64 -> Bitmap).
            val bitmapEvidencia = decodificarEvidenciaBase64(disputa?.pruebas?.firstOrNull())
            if (bitmapEvidencia != null) {
                val iv = android.widget.ImageView(requireContext()).apply {
                    adjustViewBounds = true
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    contentDescription = getString(R.string.evidencia_reclamo_titulo)
                    setImageBitmap(bitmapEvidencia)
                }
                contenido.addView(iv)
            }

            // Respuesta del asignado (si ya respondió): su motivo y su evidencia.
            val motivoRespuesta = disputa?.motivoRespuesta
            if (!motivoRespuesta.isNullOrBlank()) {
                val tvRespuesta = TextView(requireContext()).apply {
                    setPadding(0, 24, 0, 0)
                    text = getString(R.string.motivo_respuesta_titulo, motivoRespuesta)
                }
                contenido.addView(tvRespuesta)
            }
            val bitmapRespuesta = decodificarEvidenciaBase64(disputa?.pruebasRespuesta?.firstOrNull())
            if (bitmapRespuesta != null) {
                val ivRespuesta = android.widget.ImageView(requireContext()).apply {
                    adjustViewBounds = true
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    contentDescription = getString(R.string.evidencia_respuesta_titulo)
                    setImageBitmap(bitmapRespuesta)
                }
                contenido.addView(ivRespuesta)
            }

            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.resolver_reclamo))
                .setView(contenido)
                .setPositiveButton(getString(R.string.aceptar_reclamo)) { _, _ -> aplicarResolucionReclamo(tarea.id, true) }
                .setNegativeButton(getString(R.string.rechazar_reclamo)) { _, _ -> aplicarResolucionReclamo(tarea.id, false) }
                .setNeutralButton(getString(R.string.cancelar), null)
                .show()
        }
    }

    private fun aplicarResolucionReclamo(tareaId: String, aceptado: Boolean) {
        lifecycleScope.launch {
            val res = LocalizadorServicios.repositorioTarea.resolverReclamo(tareaId, aceptado)
            if (res.isSuccess) {
                // Cierre de la disputa (best-effort): la tarea ya quedó resuelta, así que un fallo
                // aquí solo se loguea y no bloquea el flujo de éxito del usuario.
                try {
                    val cierre = repoDisputas.cerrarDisputa(tareaId)
                    if (cierre.isFailure) {
                        android.util.Log.w("FragmentTareas", "No se pudo cerrar la disputa de la tarea $tareaId: ${cierre.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("FragmentTareas", "Error cerrando la disputa de la tarea $tareaId: ${e.message}")
                }
                Toast.makeText(
                    requireContext(),
                    getString(if (aceptado) R.string.reclamo_aceptado else R.string.reclamo_rechazado),
                    Toast.LENGTH_SHORT
                ).show()
                volverAInicio()
            } else {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.error_resolver_reclamo, res.exceptionOrNull()?.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun mostrarDialogoEditar(tarea: Tarea) {
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_editar_tarea, null)
        val etTitulo = v.findViewById<android.widget.EditText>(R.id.etTituloEditar)
        val etPuntos = v.findViewById<android.widget.EditText>(R.id.etPuntosEditar)
        val tilPuntos = v.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilPuntosEditar)
        val tvPuntosFijos = v.findViewById<TextView>(R.id.tvPuntosFijosEditar)
        val spDificultad = v.findViewById<android.widget.Spinner>(R.id.spDificultadEditar)
        val tvFechaEditar = v.findViewById<TextView>(R.id.tvFechaEditar)
        val btnElegirFechaEditar = v.findViewById<Button>(R.id.btnElegirFechaEditar)
        etTitulo.setText(tarea.titulo)
        etPuntos.setText(tarea.puntos.toString())
        var fechaEditada: com.google.firebase.Timestamp? = tarea.fechaProgramada
        tvFechaEditar.text = fechaEditada?.let { SelectorFechaHora.formatear(it) } ?: getString(R.string.sin_fecha)
        btnElegirFechaEditar.setOnClickListener {
            SelectorFechaHora.elegir(requireContext(), fechaEditada) { ts ->
                fechaEditada = ts
                tvFechaEditar.text = SelectorFechaHora.formatear(ts)
            }
        }
        val esPersonalizada = tarea.categoria.equals("personalizada", true) || tarea.categoria.equals("personalizado", true)
        if (esPersonalizada) {
            tilPuntos.visibility = View.GONE
            tvPuntosFijos.visibility = View.VISIBLE
            etPuntos.setText(Constants.PUNTOS_FIJOS_PERSONALIZADA.toString())
        } else {
            tilPuntos.visibility = View.VISIBLE
            tvPuntosFijos.visibility = View.GONE
        }
        val opciones = listOf("Fácil","Media","Difícil")
        val adapt = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, opciones)
        adapt.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spDificultad.adapter = adapt
        spDificultad.setSelection(if (tarea.dificultad==1) 0 else if (tarea.dificultad==2) 1 else 2)

        androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle(getString(R.string.editar_tarea)).setView(v)
            .setPositiveButton(getString(R.string.guardar)) { _, _ ->
                val nuevoTitulo = etTitulo.text.toString().trim()
                val nuevosPts = if (esPersonalizada) Constants.PUNTOS_FIJOS_PERSONALIZADA else (etPuntos.text.toString().toIntOrNull() ?: tarea.puntos)
                val nuevaDif = when(spDificultad.selectedItemPosition) {0->1;1->2;else->3}
                lifecycleScope.launch {
                    val nueva = tarea.copy(titulo = nuevoTitulo, puntos = nuevosPts, dificultad = nuevaDif, fechaProgramada = fechaEditada)
                    val res = LocalizadorServicios.repositorioTarea.actualizarTarea(nueva)
                    if (res.isSuccess) {
                        val cambioFecha = tarea.fechaProgramada != fechaEditada
                        if (cambioFecha) {
                            if (fechaEditada != null) programarRecordatorio(nueva)
                            else NotificationScheduler.cancelReminder(requireContext(), nueva.id)
                        }
                        Toast.makeText(requireContext(), getString(R.string.tarea_editada), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_LONG).show()
                    }
                }
            }.setNegativeButton(getString(R.string.cancelar), null).show()
    }

    private fun mostrarDialogoTareaDetalles(tarea: Tarea) {
        // Mostrar diálogo con información básica y opción 'Asignar' si corresponde
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        val sb = StringBuilder()
        sb.append("Título: ${tarea.titulo}\n")
        // En emergencia se muestra el valor efectivo (puntos × multiplicador), no el base.
        val puntosMostrados = (tarea.puntos * tarea.multiplicadorPuntos.coerceAtLeast(1.0)).toInt()
        sb.append("Puntos: $puntosMostrados\n")
        sb.append("Fecha: ${TareaUi.formatearFechaHora(tarea.fechaProgramada)}\n")
        sb.append("Estado: ${TareaUi.etiquetaEstado(tarea.estado)}\n")
        if (tarea.esEmergencia) sb.append("🚨 Emergencia ×${tarea.multiplicadorPuntos}\n")
        if (tarea.esRecurrente) sb.append("🔄 Recurrente${if (!tarea.tipoRecurrencia.isNullOrBlank()) " (${tarea.tipoRecurrencia})" else ""}\n")
        if (!tarea.descripcion.isNullOrBlank()) sb.append("\n${tarea.descripcion}\n")

        // cerrar = positive
        builder.setTitle(getString(R.string.detalle_tarea)).setMessage(sb.toString())
            .setPositiveButton(getString(R.string.cerrar), null)

        // Añadir botón "Crear otra" para crear una nueva instancia (duplicado) y asignarla
        builder.setNegativeButton(getString(R.string.crear_otra)) { _, _ ->
            lifecycleScope.launch {
                // elegir miembro del grupo
                val grupo = parejaVM.grupo.value
                val usuarios = try { LocalizadorServicios.repositorioAuth.observarUsuarios().first() } catch (_: Exception) { emptyList<es.sintaxys.teamtask.modelo.Usuario>() }
                val opciones = mutableListOf<Pair<String,String>>()
                if (grupo != null) {
                    val uidActual = LocalizadorServicios.repositorioAuth.usuarioActual()?.id
                    grupo.miembros.keys.forEach { uid ->
                        if (!uidActual.isNullOrBlank() && uid == uidActual) return@forEach
                        val u2 = usuarios.find { it.id == uid }
                        val display = u2?.nombreVisible() ?: uid
                        opciones.add(Pair(display, uid))
                    }
                }

                if (opciones.isEmpty()) {
                    Toast.makeText(requireContext(), getString(R.string.no_hay_miembros), Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val nombres = opciones.map { it.first }.toTypedArray()
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.selecciona_miembro))
                    .setItems(nombres) { _, idx ->
                        lifecycleScope.launch {
                            val elegidoUid = opciones[idx].second.ifBlank { null }
                            val creador = LocalizadorServicios.repositorioAuth.usuarioActual()?.id
                            if (!creador.isNullOrBlank() && !elegidoUid.isNullOrBlank() && elegidoUid == creador) {
                                Toast.makeText(requireContext(), getString(R.string.no_autoasignar), Toast.LENGTH_LONG).show()
                                return@launch
                            }
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
                                Toast.makeText(requireContext(), getString(R.string.tarea_creada_asignada), Toast.LENGTH_SHORT).show()
                                // Notificar al asignado vía Firebase para que reciba la notificación en su dispositivo
                                if (!elegidoUid.isNullOrBlank()) {
                                    Log.d(TAG, "Enviando notificación Firebase: tipo=asignacion, destinatario=$elegidoUid, tareaId=${res.getOrNull()}")
                                    val repoNot = RepositorioNotificaciones()
                                    val notifResult = repoNot.enviarNotificacion(
                                        Notificacion(
                                            id = "",
                                            tipo = "asignacion",
                                            contenido = mapOf(
                                                "tareaId" to (res.getOrNull()?.id ?: ""),
                                                "titulo" to nueva.titulo,
                                                "desde" to (creador ?: "")
                                            ),
                                            destinatario = elegidoUid,
                                            visto = false,
                                            fecha = Timestamp.now()
                                        )
                                    )
                                    if (notifResult.isSuccess) {
                                        Log.d(TAG, "Notificación enviada OK, id=${notifResult.getOrNull()}")
                                    } else {
                                        Log.e(TAG, "Error enviando notificación: ${notifResult.exceptionOrNull()?.message}")
                                    }
                                } else {
                                    Log.w(TAG, "elegidoUid es null/blank, no se envía notificación")
                                }
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
                    val usuarios = try { LocalizadorServicios.repositorioAuth.observarUsuarios().first() } catch (_: Exception) { emptyList<es.sintaxys.teamtask.modelo.Usuario>() }
                    val opciones = mutableListOf<Pair<String,String>>()
                    if (grupo != null) {
                        val uidActual = LocalizadorServicios.repositorioAuth.usuarioActual()?.id
                        grupo.miembros.keys.forEach { uid ->
                            if (!uidActual.isNullOrBlank() && uid == uidActual) return@forEach
                            val u2 = usuarios.find { it.id == uid }
                            val display = u2?.nombreVisible() ?: uid
                            opciones.add(Pair(display, uid))
                        }
                    }
                                    if (opciones.isEmpty()) Toast.makeText(requireContext(), getString(R.string.no_hay_miembros), Toast.LENGTH_SHORT).show() else {
                                        val names = opciones.map { it.first }.toTypedArray()
                                        androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle(getString(R.string.selecciona_miembro)).setItems(names) { _, idx ->
                                            lifecycleScope.launch {
                                                val elegido = opciones[idx].second
                                                if (!usuarioActualId.isBlank() && elegido == usuarioActualId) {
                                                    Toast.makeText(requireContext(), getString(R.string.no_autoasignar), Toast.LENGTH_LONG).show()
                                                    return@launch
                                                }
                                                val nueva = tarea.copy(asignadoA = elegido, grupoId = parejaVM.grupo.value?.id)
                                                val res2 = LocalizadorServicios.repositorioTarea.actualizarTarea(nueva)
                                                if (res2.isSuccess) {
                                                    Toast.makeText(requireContext(), getString(R.string.tarea_asignada), Toast.LENGTH_SHORT).show()
                                } else {
                                    val msg = res2.exceptionOrNull()?.message ?: "Error"
                                    Log.e(TAG, "Error asignar tarea desde detalle: $msg")
                                    Toast.makeText(requireContext(), getString(R.string.error_asignar, msg), Toast.LENGTH_LONG).show()
                                }
                            }
                        }.setNegativeButton(getString(R.string.cancelar), null).show()
                    }
                }
            }
        }

        builder.show()
    }

}
