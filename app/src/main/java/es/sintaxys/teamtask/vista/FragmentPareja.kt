package es.sintaxys.teamtask.vista

import android.graphics.Bitmap
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.cardview.widget.CardView
import com.google.android.material.card.MaterialCardView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import es.sintaxys.teamtask.modelo.Usuario
import es.sintaxys.teamtask.service.LocalizadorServicios
import androidx.appcompat.app.AlertDialog
import es.sintaxys.teamtask.viewmodel.ParejaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import es.sintaxys.teamtask.service.firebase.FirebaseComposition
import es.sintaxys.teamtask.util.QrGenerator
import es.sintaxys.teamtask.util.nombreVisible
import kotlinx.coroutines.tasks.await
import android.util.TypedValue
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.charts.HorizontalBarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.firestore.DocumentSnapshot

class FragmentPareja : Fragment() {

    private val parejaVM: ParejaViewModel by activityViewModels()
    private lateinit var adapter: MiembrosAdapter
    private var usuariosCacheActual: List<Usuario> = emptyList()

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
    private lateinit var ivQrInvitacion: ImageView
    private var qrBitmapActual: Bitmap? = null
    private var codigoInvitacionActual: String? = null
    private lateinit var pieChartTareas: com.github.mikephil.charting.charts.PieChart
    private lateinit var tvTareasCompletadas: TextView
    private lateinit var tvTareasPendientesMias: TextView
    private lateinit var tvTareasPendientesOtros: TextView
    private lateinit var emptyStateMiembros: android.widget.LinearLayout

    // Estadísticas avanzadas
    private lateinit var tvResumenTotal: TextView
    private lateinit var tvResumenCompletadas: TextView
    private lateinit var tvResumenPendientes: TextView
    private lateinit var tvResumenPuntos: TextView
    private lateinit var tvResumenTasa: TextView
    private lateinit var barChartMiembros: HorizontalBarChart
    private lateinit var llComparativaMiembros: LinearLayout
    private lateinit var barChartCategorias: HorizontalBarChart
    private lateinit var tvSinCategorias: TextView

    private companion object {
        // Debe coincidir con el tamaño del ImageView ivQrInvitacion en el layout.
        private const val QR_TAMANO_DP = 180f

        private val ESTADOS_COMPLETADOS = setOf("completada", "confirmada")
        private val ESTADOS_EXCLUIDOS = setOf("eliminada")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        rootView = inflater.inflate(es.sintaxys.teamtask.R.layout.fragment_pareja, container, false)

        // Inicializar vistas con findViewById
        rvMiembros = rootView.findViewById(es.sintaxys.teamtask.R.id.rvMiembros)
        bottomContainer = rootView.findViewById(es.sintaxys.teamtask.R.id.bottomContainer)
        bottomBar = rootView.findViewById(es.sintaxys.teamtask.R.id.bottomBar)
        btnSalirGrupoTop = rootView.findViewById(es.sintaxys.teamtask.R.id.btnSalirGrupoTop)
        tvTusPuntos = rootView.findViewById(es.sintaxys.teamtask.R.id.tvTusPuntos)
        tvPuntosReservados = rootView.findViewById(es.sintaxys.teamtask.R.id.tvPuntosReservados)
        tvPuntosCompanero = rootView.findViewById(es.sintaxys.teamtask.R.id.tvPuntosCompanero)
        tvNombreGrupoSmall = rootView.findViewById(es.sintaxys.teamtask.R.id.tvNombreGrupoSmall)
        btnCrearGrupo = rootView.findViewById(es.sintaxys.teamtask.R.id.btnCrearGrupo)
        btnGenerarInvitacion = rootView.findViewById(es.sintaxys.teamtask.R.id.btnGenerarInvitacion)
        tvCodigo = rootView.findViewById(es.sintaxys.teamtask.R.id.tvCodigo)
        etCodigoAceptar = rootView.findViewById(es.sintaxys.teamtask.R.id.etCodigoAceptar)
        btnAceptarInvitacion = rootView.findViewById(es.sintaxys.teamtask.R.id.btnAceptarInvitacion)
        btnAbrirGrupo = rootView.findViewById(es.sintaxys.teamtask.R.id.btnAbrirGrupo)
        btnEditarNombre = rootView.findViewById(es.sintaxys.teamtask.R.id.btnEditarNombre)
        tvGroupName = rootView.findViewById(es.sintaxys.teamtask.R.id.tvGroupName)
        tvGroupMembers = rootView.findViewById(es.sintaxys.teamtask.R.id.tvGroupMembers)
        tvMiembrosTitulo = rootView.findViewById(es.sintaxys.teamtask.R.id.tvMiembrosTitulo)
        cardInfoGrupo = rootView.findViewById(es.sintaxys.teamtask.R.id.cardInfoGrupo)
        tvGroupEmoji = rootView.findViewById(es.sintaxys.teamtask.R.id.tvGroupEmoji)
        btnCopiarCodigo = rootView.findViewById(es.sintaxys.teamtask.R.id.btnCopiarCodigo)
        btnCompartirCodigo = rootView.findViewById(es.sintaxys.teamtask.R.id.btnCompartirCodigo)
        ivQrInvitacion = rootView.findViewById(es.sintaxys.teamtask.R.id.ivQrInvitacion)
        pieChartTareas = rootView.findViewById(es.sintaxys.teamtask.R.id.pieChartTareas)
        tvTareasCompletadas = rootView.findViewById(es.sintaxys.teamtask.R.id.tvTareasCompletadas)
        tvTareasPendientesMias = rootView.findViewById(es.sintaxys.teamtask.R.id.tvTareasPendientesMias)
        tvTareasPendientesOtros = rootView.findViewById(es.sintaxys.teamtask.R.id.tvTareasPendientesOtros)
        emptyStateMiembros = rootView.findViewById(es.sintaxys.teamtask.R.id.emptyStateMiembros)
        tvResumenTotal = rootView.findViewById(es.sintaxys.teamtask.R.id.tvResumenTotal)
        tvResumenCompletadas = rootView.findViewById(es.sintaxys.teamtask.R.id.tvResumenCompletadas)
        tvResumenPendientes = rootView.findViewById(es.sintaxys.teamtask.R.id.tvResumenPendientes)
        tvResumenPuntos = rootView.findViewById(es.sintaxys.teamtask.R.id.tvResumenPuntos)
        tvResumenTasa = rootView.findViewById(es.sintaxys.teamtask.R.id.tvResumenTasa)
        barChartMiembros = rootView.findViewById(es.sintaxys.teamtask.R.id.barChartMiembros)
        llComparativaMiembros = rootView.findViewById(es.sintaxys.teamtask.R.id.llComparativaMiembros)
        barChartCategorias = rootView.findViewById(es.sintaxys.teamtask.R.id.barChartCategorias)
        tvSinCategorias = rootView.findViewById(es.sintaxys.teamtask.R.id.tvSinCategorias)

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
            viewLifecycleOwner.lifecycleScope.launch {
                parejaVM.cargarGrupoPorUsuario(uid)
                // actualizar vista inmediatamente con estado actual (por si ya está cargado en vm)
                actualizarVistaGrupo(parejaVM.grupo.value)
            }
        }

        // Valores por defecto visibles para evitar pantallas vacías
        tvTusPuntos.text = "0"
        tvPuntosReservados.text = getString(es.sintaxys.teamtask.R.string.reservados_format, 0)
        tvPuntosCompanero.text = getString(es.sintaxys.teamtask.R.string.cero)
        tvNombreGrupoSmall.text = getString(es.sintaxys.teamtask.R.string.guion)

        // Observar usuarios y grupo para mapear uid -> nombre y actualizar puntos
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    LocalizadorServicios.repositorioAuth.observarUsuarios().collect { lista ->
                        android.util.Log.d("FragmentPareja", "observarUsuarios: recibidos ${lista.size} usuarios")
                        usuariosCacheActual = lista
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
                            tvPuntosReservados.text = getString(es.sintaxys.teamtask.R.string.reservados_format, yo?.puntosReservados ?: 0)
                        }

                        // si hay grupo, actualizar puntos del compañero (primer distinto)
                        val g2 = parejaVM.grupo.value
                        android.util.Log.d("FragmentPareja", "grupo en observador usuarios = ${g2?.id}")
                        if (g2 != null) {
                            val otroUid = g2.miembros.keys.firstOrNull { it != myId }
                            val otro = otroUid?.let { uid -> lista.find { it.id == uid } }
                            withContext(Dispatchers.Main) {
                                tvPuntosCompanero.text = (otro?.puntos ?: 0).toString()
                                tvNombreGrupoSmall.text = g2.nombre ?: getString(es.sintaxys.teamtask.R.string.guion)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                tvPuntosCompanero.text = getString(es.sintaxys.teamtask.R.string.cero)
                                tvNombreGrupoSmall.text = getString(es.sintaxys.teamtask.R.string.guion)
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
                .setTitle(getString(es.sintaxys.teamtask.R.string.crear_grupo_title))
                .setView(et)
                .setPositiveButton(getString(es.sintaxys.teamtask.R.string.crear)) { _, _ ->
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
                .setNegativeButton(getString(es.sintaxys.teamtask.R.string.cancelar), null)
                .show()
        }

        btnGenerarInvitacion.setOnClickListener {
            val grupo = parejaVM.grupo.value
            val usuarioId = LocalizadorServicios.repositorioAuth.usuarioActual()?.id ?: return@setOnClickListener
            if (grupo == null) { Toast.makeText(requireContext(), getString(es.sintaxys.teamtask.R.string.no_hay_grupo_activo), Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            parejaVM.crearInvitacion(grupo.id, usuarioId, null) { invRes ->
                if (invRes.isSuccess) {
                    val codigo = invRes.getOrNull() ?: grupo.id
                    codigoInvitacionActual = codigo
                    tvCodigo.text = getString(es.sintaxys.teamtask.R.string.grupo_creado_codigo, codigo)
                    actualizarQrInvitacion(codigo)
                    Toast.makeText(requireContext(), getString(es.sintaxys.teamtask.R.string.grupo_creado_codigo, codigo), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), invRes.exceptionOrNull()?.message ?: "Error creando invitación", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnAceptarInvitacion.setOnClickListener {
            val codigo = etCodigoAceptar.text.toString().trim()
            val usuarioId = LocalizadorServicios.repositorioAuth.usuarioActual()?.id ?: return@setOnClickListener
            if (codigo.isEmpty()) { Toast.makeText(requireContext(), getString(es.sintaxys.teamtask.R.string.introduce_codigo), Toast.LENGTH_SHORT).show(); return@setOnClickListener }
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
            if (g == null) { Toast.makeText(requireContext(), getString(es.sintaxys.teamtask.R.string.no_hay_grupo_activo), Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            viewLifecycleOwner.lifecycleScope.launch {
                val usuariosCacheLocal = try { LocalizadorServicios.repositorioAuth.observarUsuarios().first() } catch (_: Exception) { emptyList<Usuario>() }
                val miembrosTexto = g.miembros.map { (uid, rol) ->
                    val usuario = usuariosCacheLocal.find { it.id == uid }
                    val nombre = usuario?.nombreVisible() ?: uid
                    "- $nombre ($rol)"
                }.joinToString("\n")
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(es.sintaxys.teamtask.R.string.miembros_del_grupo))
                    .setMessage("Nombre: ${g.nombre}\nMiembros (${g.miembros.size}):\n$miembrosTexto")
                    .setPositiveButton(getString(es.sintaxys.teamtask.R.string.aceptar), null)
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
            val g = parejaVM.grupo.value ?: run { Toast.makeText(requireContext(), getString(es.sintaxys.teamtask.R.string.no_hay_grupo_activo), Toast.LENGTH_SHORT).show(); return@OnClickListener }
            val et = EditText(requireContext())
            et.setText(g.nombre ?: "")
            AlertDialog.Builder(requireContext())
                .setTitle(getString(es.sintaxys.teamtask.R.string.editar_nombre_grupo))
                .setView(et)
                .setPositiveButton(getString(es.sintaxys.teamtask.R.string.guardar)) { _, _ ->
                    val nuevo = et.text.toString().trim().ifEmpty { g.nombre ?: "Mi grupo" }
                    parejaVM.actualizarNombreGrupo(g.id, nuevo) { res ->
                        if (res.isSuccess) Toast.makeText(requireContext(), getString(es.sintaxys.teamtask.R.string.nombre_actualizado), Toast.LENGTH_SHORT).show()
                        else Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton(getString(es.sintaxys.teamtask.R.string.cancelar), null)
                .show()
        }
        btnEditarNombre.setOnClickListener(guardarHandler)

        // Selector de emoji
        tvGroupEmoji.setOnClickListener {
            val g = parejaVM.grupo.value ?: run { Toast.makeText(requireContext(), getString(es.sintaxys.teamtask.R.string.no_hay_grupo_activo), Toast.LENGTH_SHORT).show(); return@setOnClickListener }
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

        // Compartir la imagen del QR (tocando el propio QR)
        ivQrInvitacion.setOnClickListener { compartirQrImagen() }

    }

    /**
     * Genera el QR (deep link teamtask://invite?codigo=...) en background y lo publica
     * en el hilo principal. Si no hay código, oculta la vista.
     */
    private fun actualizarQrInvitacion(codigo: String?) {
        if (codigo.isNullOrBlank()) {
            qrBitmapActual = null
            ivQrInvitacion.setImageDrawable(null)
            ivQrInvitacion.visibility = View.GONE
            return
        }
        val deepLink = "teamtask://invite?codigo=$codigo"
        val tamanoPx = (resources.displayMetrics.density * QR_TAMANO_DP).toInt()
        viewLifecycleOwner.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.Default) { QrGenerator.generar(deepLink, tamanoPx) }
            qrBitmapActual = bitmap
            ivQrInvitacion.setImageBitmap(bitmap)
            ivQrInvitacion.visibility = if (bitmap != null) View.VISIBLE else View.GONE
        }
    }

    /**
     * Comparte únicamente la imagen del QR vía FileProvider. Es una acción adicional:
     * el botón "Compartir" sigue enviando el código como texto.
     */
    private fun compartirQrImagen() {
        val bitmap = qrBitmapActual
        val codigo = codigoInvitacionActual
        if (bitmap == null || codigo.isNullOrBlank()) {
            Toast.makeText(requireContext(), getString(es.sintaxys.teamtask.R.string.genera_invitacion_primero), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val directorio = java.io.File(requireContext().cacheDir, "qr")
            if (!directorio.exists()) directorio.mkdirs()
            val archivo = java.io.File(directorio, "invitacion_${System.currentTimeMillis()}.png")
            archivo.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                archivo
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_TEXT, getString(es.sintaxys.teamtask.R.string.compartir_qr_mensaje, codigo))
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(intent, getString(es.sintaxys.teamtask.R.string.compartir_qr)))
        } catch (e: Exception) {
            android.util.Log.w("FragmentPareja", "No se pudo compartir el QR: ${e.message}")
            Toast.makeText(requireContext(), "No se pudo compartir el QR", Toast.LENGTH_SHORT).show()
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
        builder.setNegativeButton(getString(es.sintaxys.teamtask.R.string.cancelar), null)
        builder.show()
    }

    // ---------- Estadísticas avanzadas de la pantalla Pareja ----------

    private data class TareaMin(
        val estado: String,
        val asignadoA: String?,
        val categoria: String?,
        val puntos: Int
    )

    private data class MiembroStat(
        val uid: String,
        val nombre: String,
        val completadas: Int,
        val puntos: Int,
        val puntosRecompensa: Int,
        val racha: Int,
        val esLider: Boolean
    )

    private data class EstadisticasGrupo(
        val total: Int,
        val completadas: Int,
        val pendientes: Int,
        val pendientesMias: Int,
        val pendientesOtros: Int,
        val tasaCompletado: Int,
        val puntosConfirmados: Int,
        val miembros: List<MiembroStat>,
        val categorias: List<Pair<String, Int>>
    )

    /**
     * Lee las tareas del grupo y publica las estadísticas avanzadas.
     * El cálculo se hace en [Dispatchers.Default]; el render en el hilo principal.
     */
    private fun cargarYMostrarEstadisticasTareas(grupo: es.sintaxys.teamtask.modelo.Grupo) {
        val grupoId = grupo.id
        val miembrosUids = grupo.miembros.keys.toList()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val usuarios = if (usuariosCacheActual.isNotEmpty()) {
                    usuariosCacheActual
                } else {
                    try { LocalizadorServicios.repositorioAuth.observarUsuarios().first() }
                    catch (_: Exception) { emptyList<Usuario>() }
                }

                val db = FirebaseComposition.firestore()
                val tareasSnapshot = db.collection("tareas")
                    .whereEqualTo("grupoId", grupoId)
                    .get()
                    .await()

                val miId = LocalizadorServicios.repositorioAuth.usuarioActual()?.id

                val stats = withContext(Dispatchers.Default) {
                    calcularEstadisticas(tareasSnapshot.documents, miembrosUids, usuarios, miId)
                }

                withContext(Dispatchers.Main) {
                    renderEstadisticas(stats)
                }
            } catch (e: Exception) {
                android.util.Log.e("FragmentPareja", "cargarYMostrarEstadisticasTareas error", e)
            }
        }
    }

    /**
     * Cálculo puro (sin acceso a UI) de métricas, comparativa y categorías.
     */
    private fun calcularEstadisticas(
        documentos: List<DocumentSnapshot>,
        miembrosUids: List<String>,
        usuarios: List<Usuario>,
        miId: String?
    ): EstadisticasGrupo {
        val tareas = documentos.map { d ->
            TareaMin(
                estado = d.getString("estado") ?: "pendiente",
                asignadoA = d.getString("asignadoA"),
                categoria = d.getString("categoria"),
                puntos = (d.getLong("puntos") ?: 0L).toInt()
            )
        }

        var completadas = 0
        var pendientesMias = 0
        var pendientesOtros = 0
        var puntosConfirmados = 0
        val completadasPorMiembro = LinkedHashMap<String, Int>()
        miembrosUids.forEach { completadasPorMiembro[it] = 0 }
        val categorias = LinkedHashMap<String, Int>()

        for (t in tareas) {
            if (t.estado in ESTADOS_EXCLUIDOS) continue

            if (t.estado in ESTADOS_COMPLETADOS) {
                completadas++
                val uid = t.asignadoA
                if (uid != null && completadasPorMiembro.containsKey(uid)) {
                    completadasPorMiembro[uid] = (completadasPorMiembro[uid] ?: 0) + 1
                }
            } else {
                // Cualquier estado no completado ni eliminado cuenta como pendiente.
                if (t.asignadoA == miId) pendientesMias++ else pendientesOtros++
            }

            if (t.estado == "confirmada") puntosConfirmados += t.puntos

            val categoria = normalizarCategoria(t.categoria)
            if (categoria != null) categorias[categoria] = (categorias[categoria] ?: 0) + 1
        }

        val total = completadas + pendientesMias + pendientesOtros
        val tasa = if (total > 0) Math.round(completadas * 100f / total) else 0

        val usuariosPorId = usuarios.associateBy { it.id }
        val listaMiembros = miembrosUids.map { uid ->
            val u = usuariosPorId[uid]
            MiembroStat(
                uid = uid,
                nombre = u?.nombreVisible() ?: uid,
                completadas = completadasPorMiembro[uid] ?: 0,
                puntos = u?.puntos ?: 0,
                puntosRecompensa = u?.puntosRecompensa ?: 0,
                racha = u?.rachaDias ?: 0,
                esLider = false
            )
        }.sortedWith(
            compareByDescending<MiembroStat> { it.completadas }.thenByDescending { it.puntos }
        )

        val hayLider = listaMiembros.firstOrNull()?.completadas?.let { it > 0 } == true
        val miembrosOrdenados = if (hayLider) {
            listaMiembros.mapIndexed { indice, m -> if (indice == 0) m.copy(esLider = true) else m }
        } else {
            listaMiembros
        }

        val categoriasOrdenadas = categorias.entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }

        return EstadisticasGrupo(
            total = total,
            completadas = completadas,
            pendientes = pendientesMias + pendientesOtros,
            pendientesMias = pendientesMias,
            pendientesOtros = pendientesOtros,
            tasaCompletado = tasa,
            puntosConfirmados = puntosConfirmados,
            miembros = miembrosOrdenados,
            categorias = categoriasOrdenadas
        )
    }

    private fun normalizarCategoria(raw: String?): String? {
        val valor = raw?.trim().orEmpty()
        if (valor.isEmpty()) return null
        return when (valor.lowercase()) {
            "cocina" -> "Cocina"
            "limpieza" -> "Limpieza"
            "ropa" -> "Ropa"
            "mascotas" -> "Mascotas"
            "recados" -> "Recados"
            "personalizada", "personalizado" -> "Personalizada"
            else -> valor
        }
    }

    private fun renderEstadisticas(est: EstadisticasGrupo) {
        tvResumenTotal.text = est.total.toString()
        tvResumenCompletadas.text = est.completadas.toString()
        tvResumenPendientes.text = est.pendientes.toString()
        tvResumenPuntos.text = est.puntosConfirmados.toString()
        tvResumenTasa.text = getString(es.sintaxys.teamtask.R.string.resumen_tasa_completado, est.tasaCompletado)

        // Counters existentes de la tarjeta del PieChart.
        tvTareasCompletadas.text = est.completadas.toString()
        tvTareasPendientesMias.text = est.pendientesMias.toString()
        tvTareasPendientesOtros.text = est.pendientesOtros.toString()

        renderPie(est)
        renderChartMiembros(est.miembros)
        renderComparativaMiembros(est.miembros)
        renderChartCategorias(est.categorias)
    }

    private fun renderPie(est: EstadisticasGrupo) {
        if (est.total <= 0) {
            pieChartTareas.clear()
            pieChartTareas.invalidate()
            return
        }
        val entries = mutableListOf<PieEntry>()
        if (est.completadas > 0) entries.add(PieEntry(est.completadas.toFloat(), getString(es.sintaxys.teamtask.R.string.completadas)))
        if (est.pendientesMias > 0) entries.add(PieEntry(est.pendientesMias.toFloat(), getString(es.sintaxys.teamtask.R.string.mis_pendientes)))
        if (est.pendientesOtros > 0) entries.add(PieEntry(est.pendientesOtros.toFloat(), getString(es.sintaxys.teamtask.R.string.de_otros)))

        val dataSet = PieDataSet(entries, "")
        // Mismos tokens que los indicadores de leyenda del layout (verde/azul/naranja).
        dataSet.colors = listOf(
            colorToken(es.sintaxys.teamtask.R.color.verde),
            colorToken(es.sintaxys.teamtask.R.color.azul),
            colorToken(es.sintaxys.teamtask.R.color.naranja)
        )
        dataSet.setSliceSpace(3f)
        dataSet.setValueTextSize(14f)
        // Texto oscuro fijo: legible sobre los rellenos pastel claros en ambos temas.
        dataSet.setValueTextColor(colorToken(es.sintaxys.teamtask.R.color.texto_sobre_semantico))

        val data = PieData(dataSet)
        data.setValueFormatter(object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = if (value > 0) value.toInt().toString() else ""
        })

        pieChartTareas.apply {
            this.data = data
            description.isEnabled = false
            legend.isEnabled = false
            setDrawEntryLabels(false)
            animateY(1000)
            invalidate()
        }
    }

    private fun renderChartMiembros(miembros: List<MiembroStat>) {
        if (miembros.isEmpty()) {
            barChartMiembros.clear()
            barChartMiembros.invalidate()
            return
        }
        val maxCompletadas = miembros.maxOf { it.completadas }.coerceAtLeast(1)
        val entries = miembros.mapIndexed { indice, m -> BarEntry(indice.toFloat(), m.completadas.toFloat()) }
        val dataSet = BarDataSet(entries, "")
        dataSet.setColor(colorToken(es.sintaxys.teamtask.R.color.primario))
        // Sin etiquetas de valor dentro de las barras: el detalle exacto va en las filas de abajo.
        dataSet.setDrawValues(false)
        dataSet.isHighlightEnabled = false

        val data = BarData(dataSet)
        data.barWidth = 0.55f

        barChartMiembros.apply {
            this.data = data
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setScaleEnabled(false)
            setPinchZoom(false)
            setTouchEnabled(false)
            setFitBars(true)
            axisRight.isEnabled = false
            axisLeft.axisMinimum = 0f
            axisLeft.axisMaximum = maxCompletadas.toFloat()
            axisLeft.granularity = 1f
            axisLeft.textColor = colorToken(es.sintaxys.teamtask.R.color.texto_secundario)
            axisLeft.gridColor = colorToken(es.sintaxys.teamtask.R.color.divisor)
            axisLeft.axisLineColor = colorToken(es.sintaxys.teamtask.R.color.divisor)
            axisLeft.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = value.toInt().toString()
            }
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            xAxis.setDrawGridLines(false)
            xAxis.setDrawAxisLine(false)
            xAxis.setAvoidFirstLastClipping(true)
            xAxis.textSize = 11f
            xAxis.textColor = colorToken(es.sintaxys.teamtask.R.color.texto_secundario)
            xAxis.valueFormatter = IndexAxisValueFormatter(miembros.map { it.nombre })
            invalidate()
        }
    }

    private fun renderComparativaMiembros(miembros: List<MiembroStat>) {
        llComparativaMiembros.removeAllViews()
        if (miembros.isEmpty()) return
        val ctx = requireContext()

        for (m in miembros) {
            val fila = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, dpToPx(6f), 0, dpToPx(6f))
            }

            val titulo = TextView(ctx).apply {
                text = if (m.esLider) "\uD83D\uDC51 ${m.nombre}" else m.nombre
                setTextAppearance(es.sintaxys.teamtask.R.style.TextAppearance_TFG_SubTitulo)
                setTextColor(colorToken(es.sintaxys.teamtask.R.color.texto_principal))
                if (m.esLider) contentDescription = getString(es.sintaxys.teamtask.R.string.ranking_primero_desc)
            }

            val completadas = TextView(ctx).apply {
                text = getString(es.sintaxys.teamtask.R.string.miembro_completadas_format, m.completadas)
                setTextAppearance(es.sintaxys.teamtask.R.style.TextAppearance_TFG_Cuerpo)
                setTextColor(colorToken(es.sintaxys.teamtask.R.color.primario_variant))
            }

            val detalle = TextView(ctx).apply {
                text = getString(
                    es.sintaxys.teamtask.R.string.miembro_detalle_format,
                    m.puntos, m.puntosRecompensa, m.racha
                )
                setTextAppearance(es.sintaxys.teamtask.R.style.TextAppearance_TFG_Label)
                setTextColor(colorToken(es.sintaxys.teamtask.R.color.texto_secundario))
            }

            fila.addView(titulo)
            fila.addView(completadas)
            fila.addView(detalle)
            llComparativaMiembros.addView(fila)
        }
    }

    private fun renderChartCategorias(categorias: List<Pair<String, Int>>) {
        if (categorias.isEmpty()) {
            barChartCategorias.clear()
            barChartCategorias.visibility = View.GONE
            tvSinCategorias.visibility = View.VISIBLE
            return
        }
        barChartCategorias.visibility = View.VISIBLE
        tvSinCategorias.visibility = View.GONE

        val paleta = intArrayOf(
            es.sintaxys.teamtask.R.color.primario,
            es.sintaxys.teamtask.R.color.azul,
            es.sintaxys.teamtask.R.color.naranja,
            es.sintaxys.teamtask.R.color.recurrente,
            es.sintaxys.teamtask.R.color.verde,
            es.sintaxys.teamtask.R.color.rojo
        )
        val maxCantidad = (categorias.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
        val etiquetas = categorias.map { "${it.first} (${it.second})" }
        val entries = categorias.mapIndexed { indice, c -> BarEntry(indice.toFloat(), c.second.toFloat()) }

        val dataSet = BarDataSet(entries, "")
        dataSet.colors = categorias.indices.map { colorToken(paleta[it % paleta.size]) }
        dataSet.setDrawValues(false)
        dataSet.isHighlightEnabled = false

        val data = BarData(dataSet)
        data.barWidth = 0.6f

        barChartCategorias.apply {
            this.data = data
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setScaleEnabled(false)
            setPinchZoom(false)
            setTouchEnabled(false)
            setFitBars(true)
            axisRight.isEnabled = false
            axisLeft.axisMinimum = 0f
            axisLeft.axisMaximum = maxCantidad.toFloat()
            axisLeft.granularity = 1f
            axisLeft.textColor = colorToken(es.sintaxys.teamtask.R.color.texto_secundario)
            axisLeft.gridColor = colorToken(es.sintaxys.teamtask.R.color.divisor)
            axisLeft.axisLineColor = colorToken(es.sintaxys.teamtask.R.color.divisor)
            axisLeft.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = value.toInt().toString()
            }
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            xAxis.setDrawGridLines(false)
            xAxis.setDrawAxisLine(false)
            xAxis.setAvoidFirstLastClipping(true)
            xAxis.textSize = 10f
            xAxis.textColor = colorToken(es.sintaxys.teamtask.R.color.texto_secundario)
            xAxis.valueFormatter = IndexAxisValueFormatter(etiquetas)
            invalidate()
        }
    }

    private fun colorToken(resourceId: Int): Int = ContextCompat.getColor(requireContext(), resourceId)

    private fun dpToPx(dp: Float): Int = (dp * resources.displayMetrics.density).toInt()


    private fun salirDelGrupo(usuarioId: String, adapter: MiembrosAdapter) {
        AlertDialog.Builder(requireContext())
            .setTitle("Salir del grupo")
            .setMessage("¿Estás seguro de que quieres salir del grupo?")
            .setPositiveButton(getString(es.sintaxys.teamtask.R.string.aceptar)) { _, _ ->
                // optimista: ocultar UI inmediatamente para evitar confusión
                mostrarUIGrupo(false)
                tvGroupName.text = getString(es.sintaxys.teamtask.R.string.guion)
                tvGroupMembers.text = getString(es.sintaxys.teamtask.R.string.miembros_format, 0)
                adapter.setItems(emptyList())

                parejaVM.salirGrupo(usuarioId) { res ->
                    if (res.isSuccess) {
                        Toast.makeText(requireContext(), "Has salido del grupo", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Error saliendo del grupo", Toast.LENGTH_LONG).show()
                        // Recargar grupo en coroutine si falla
                        viewLifecycleOwner.lifecycleScope.launch {
                            parejaVM.cargarGrupoPorUsuario(usuarioId)
                        }
                    }
                }
            }
            .setNegativeButton(getString(es.sintaxys.teamtask.R.string.cancelar), null)
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
            emptyStateMiembros.visibility = View.GONE
        } else {
            cardInfoGrupo.visibility = View.GONE
            tvGroupName.visibility = View.GONE
            tvGroupMembers.visibility = View.GONE
            btnAbrirGrupo.visibility = View.GONE
            btnEditarNombre.visibility = View.GONE
            btnSalirGrupoTop.visibility = View.GONE
            tvMiembrosTitulo.visibility = View.GONE
            rvMiembros.visibility = View.GONE
            emptyStateMiembros.visibility = View.GONE
        }
    }

    private fun actualizarVistaGrupo(g: es.sintaxys.teamtask.modelo.Grupo?) {
        if (g != null) {
            mostrarUIGrupo(true)
            tvGroupName.text = g.nombre
            tvGroupMembers.text = getString(es.sintaxys.teamtask.R.string.miembros_format, g.miembros.size)
            tvGroupEmoji.text = g.emoji
            
            // Cargar estadísticas de tareas
            cargarYMostrarEstadisticasTareas(g)

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
            tvGroupName.text = getString(es.sintaxys.teamtask.R.string.guion)
            tvGroupMembers.text = getString(es.sintaxys.teamtask.R.string.miembros_format, 0)
            tvGroupEmoji.text = "❤️"
            adapter.setItems(emptyList())
            limpiarEstadisticas()
        }
    }

    /** Deja las estadísticas en cero cuando no hay grupo activo (p. ej. tras salir). */
    private fun limpiarEstadisticas() {
        val cero = getString(es.sintaxys.teamtask.R.string.cero)
        tvResumenTotal.text = cero
        tvResumenCompletadas.text = cero
        tvResumenPendientes.text = cero
        tvResumenPuntos.text = cero
        tvResumenTasa.text = getString(es.sintaxys.teamtask.R.string.resumen_tasa_completado, 0)
        tvTareasCompletadas.text = cero
        tvTareasPendientesMias.text = cero
        tvTareasPendientesOtros.text = cero

        pieChartTareas.clear()
        pieChartTareas.invalidate()
        barChartMiembros.clear()
        barChartMiembros.invalidate()
        barChartCategorias.clear()
        barChartCategorias.invalidate()
        barChartCategorias.visibility = View.VISIBLE
        tvSinCategorias.visibility = View.GONE
        llComparativaMiembros.removeAllViews()
    }

    private fun actualizarListaMiembrosConUsuarios(g: es.sintaxys.teamtask.modelo.Grupo, usuarios: List<Usuario>) {
        val myId = LocalizadorServicios.repositorioAuth.usuarioActual()?.id
        lifecycleScope.launch {
            val items = try { resolverNombresMiembros(g.miembros, usuarios) } catch (e: Exception) { g.miembros.map { (uid, rol) -> Triple(uid, rol, uid) } }
            withContext(Dispatchers.Main) {
                adapter.setItems(items)
                tvGroupName.text = g.nombre
                tvGroupMembers.text = getString(es.sintaxys.teamtask.R.string.miembros_format, g.miembros.size)
                val otroUid = g.miembros.keys.firstOrNull { it != myId }
                val otro = otroUid?.let { uid -> usuarios.find { it.id == uid } }
                tvPuntosCompanero.text = (otro?.puntos ?: 0).toString()
                tvNombreGrupoSmall.text = g.nombre
                mostrarUIGrupo(true)

                // Empty state for members
                if (items.isEmpty()) {
                    rvMiembros.visibility = View.GONE
                    emptyStateMiembros.visibility = View.VISIBLE
                } else {
                    rvMiembros.visibility = View.VISIBLE
                    emptyStateMiembros.visibility = View.GONE
                }
            }
        }
    }

    private suspend fun resolverNombresMiembros(miembros: Map<String,String>, usuariosCache: List<Usuario>): List<Triple<String,String,String>> {
        val db = FirebaseComposition.firestore()
        val result = mutableListOf<Triple<String,String,String>>()
        for ((uid, rol) in miembros) {
            val u = usuariosCache.find { it.id == uid }
            if (u != null) {
                result.add(Triple(u.nombreVisible(), rol, uid))
            } else {
                try {
                    val doc = db.collection("usuarios").document(uid).get().await()
                    if (doc.exists()) {
                        val nombre = doc.getString("nombre") ?: ""
                        val email = doc.getString("email") ?: ""
                        val visible = Usuario(id = uid, nombre = nombre, email = email).nombreVisible()
                        result.add(Triple(visible, rol, uid))
                    } else {
                        result.add(Triple(uid, rol, uid))
                    }
                } catch (e: Exception) {
                    android.util.Log.w("FragmentPareja", "resolverNombresMiembros: fallo leyendo usuarios/$uid: ${e.message}")
                    result.add(Triple(uid, rol, uid))
                }
            }
        }
        return result
    }

    private inner class MiembrosAdapter : RecyclerView.Adapter<MiembrosAdapter.VH>() {
        private var items: List<Triple<String,String,String>> = emptyList()
        // Almacenar también puntos por uid para mostrar en el badge
        private var puntosMap: Map<String,Int> = emptyMap()

        fun setItems(list: List<Triple<String,String,String>>) { items = list; notifyDataSetChanged() }
        fun setPuntosMap(map: Map<String,Int>) { puntosMap = map; notifyDataSetChanged() }

        inner class VH(val root: View) : RecyclerView.ViewHolder(root) {
            val tvAvatar: TextView   = root.findViewById(es.sintaxys.teamtask.R.id.tvMiembroAvatar)
            val tvNombre: TextView   = root.findViewById(es.sintaxys.teamtask.R.id.tvMiembroNombre)
            val tvEmail: TextView    = root.findViewById(es.sintaxys.teamtask.R.id.tvMiembroEmail)
            val tvPuntos: TextView   = root.findViewById(es.sintaxys.teamtask.R.id.tvMiembroPuntos)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = android.view.LayoutInflater.from(parent.context)
                .inflate(es.sintaxys.teamtask.R.layout.item_miembro, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (nombreVisible, rol, uid) = items[position]
            val inicial = nombreVisible.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            holder.tvAvatar.text  = inicial
            holder.tvNombre.text  = nombreVisible
            // La vista secundaria muestra el rol; el correo nunca se renderiza.
            holder.tvEmail.text   = rol
            val puntosDesdeMap = puntosMap[uid]
            val puntosDesdeCache = usuariosCacheActual.find { it.id == uid }?.puntos
            val puntos = puntosDesdeMap ?: puntosDesdeCache ?: 0
            holder.tvPuntos.text  = "$puntos pts"
        }

        override fun getItemCount(): Int = items.size
    }
}
