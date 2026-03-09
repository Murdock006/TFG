package com.example.tfg.vista

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tfg.R
import com.example.tfg.databinding.FragmentRecompensasListBinding
import com.example.tfg.modelo.Canje
import com.example.tfg.modelo.Notificacion
import com.example.tfg.modelo.Recompensa
import com.example.tfg.repositorio.RepositorioNotificaciones
import com.example.tfg.repositorio.RepositorioRecompensas
import com.example.tfg.service.LocalizadorServicios
import com.example.tfg.viewmodel.ParejaViewModel
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FragmentRecompensas : Fragment() {

    private lateinit var b: FragmentRecompensasListBinding
    private val repo    = RepositorioRecompensas()
    private val repoNot = RepositorioNotificaciones()
    private val parejaVM: ParejaViewModel by activityViewModels()

    private enum class Tab { DISPONIBLES, PENDIENTES, HISTORIAL }
    private var tabActual = Tab.DISPONIBLES
    private var puntosActuales = 0         // puntos de actividad (solo informativos)
    private var puntosRecompensa = 0        // puntos exclusivos para canjear recompensas

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        b = FragmentRecompensasListBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.rvRecompensas.layoutManager = LinearLayoutManager(requireContext())

        // Tabs
        b.btnTabDisponibles.setOnClickListener { cambiarTab(Tab.DISPONIBLES) }
        b.btnTabPendientes.setOnClickListener  { cambiarTab(Tab.PENDIENTES) }
        b.btnTabHistorial.setOnClickListener   { cambiarTab(Tab.HISTORIAL) }

        // FAB crear personalizada — requiere ≥1000 pts de recompensa
        b.fabCrearRecompensa.setOnClickListener {
            if (puntosRecompensa < 1000) {
                Toast.makeText(requireContext(),
                    "Necesitas 1000 pts 🎁 para crear una recompensa personalizada\n(tienes $puntosRecompensa pts)",
                    Toast.LENGTH_LONG).show()
            } else {
                mostrarDialogoCrearPersonalizada()
            }
        }

        // Cargar puntos y primera pestaña
        cargarDatos()

        // Observar cambios de puntos en tiempo real
        lifecycleScope.launch {
            LocalizadorServicios.repositorioAuth.observarUsuarios().collect { usuarios ->
                val uid = LocalizadorServicios.repositorioAuth.usuarioActual()?.id
                val usuario = usuarios.find { it.id == uid }
                puntosActuales   = usuario?.puntos ?: puntosActuales
                puntosRecompensa = usuario?.puntosRecompensa ?: puntosRecompensa
                actualizarCabecera()
                if (tabActual == Tab.DISPONIBLES) cargarDisponibles()
            }
        }
    }

    private fun cambiarTab(tab: Tab) {
        tabActual = tab
        // Resaltar tab activo
        val activo   = requireContext().getColor(R.color.negro)
        val inactivo = Color.TRANSPARENT
        b.btnTabDisponibles.setBackgroundColor(if (tab == Tab.DISPONIBLES) activo else inactivo)
        b.btnTabPendientes.setBackgroundColor(if (tab == Tab.PENDIENTES)  activo else inactivo)
        b.btnTabHistorial.setBackgroundColor(if (tab == Tab.HISTORIAL)    activo else inactivo)
        val colorTextoActivo   = Color.WHITE
        val colorTextoInactivo = requireContext().getColor(R.color.texto_principal)
        b.btnTabDisponibles.setTextColor(if (tab == Tab.DISPONIBLES) colorTextoActivo else colorTextoInactivo)
        b.btnTabPendientes.setTextColor(if (tab == Tab.PENDIENTES)  colorTextoActivo else colorTextoInactivo)
        b.btnTabHistorial.setTextColor(if (tab == Tab.HISTORIAL)    colorTextoActivo else colorTextoInactivo)
        cargarDatos()
    }

    private fun actualizarCabecera() {
        b.tvMisPuntos.text = "🎁 $puntosRecompensa pts recompensa  |  ⭐ $puntosActuales pts actividad"
        // Indicar visualmente si ya puede crear recompensas personalizadas
        b.fabCrearRecompensa.alpha = if (puntosRecompensa >= 1000) 1f else 0.4f
    }

    private fun cargarDatos() {
        lifecycleScope.launch {
            val uid = LocalizadorServicios.repositorioAuth.usuarioActual()?.id
            val usuarios = try { LocalizadorServicios.repositorioAuth.observarUsuarios().first() } catch (_: Exception) { emptyList() }
            val usuario = usuarios.find { it.id == uid }
            puntosActuales   = usuario?.puntos ?: 0
            puntosRecompensa = usuario?.puntosRecompensa ?: 0
            actualizarCabecera()
            when (tabActual) {
                Tab.DISPONIBLES -> cargarDisponibles()
                Tab.PENDIENTES  -> cargarPendientes()
                Tab.HISTORIAL   -> cargarHistorial()
            }
        }
    }

    // ── TAB 1: Disponibles ──────────────────────────────────────────────────
    private suspend fun cargarDisponibles() {
        val grupoId = parejaVM.grupo.value?.id
        val lista   = repo.listarRecompensas(grupoId).getOrNull() ?: emptyList()
        b.rvRecompensas.adapter = AdapterDisponibles(lista, puntosRecompensa)
        // Mostrar nota informativa del 10% una sola vez (encima de la lista)
        b.tvInfoRecompensas.visibility = View.VISIBLE
        b.tvInfoRecompensas.text = "ℹ️ Ganas pts de recompensa completando tareas: el 10% del valor de cada tarea se acumula automáticamente como 🎁 pts."
    }

    private inner class AdapterDisponibles(
        private val items: List<Recompensa>,
        private val misPuntos: Int
    ) : RecyclerView.Adapter<AdapterDisponibles.VH>() {

        inner class VH(val card: androidx.cardview.widget.CardView) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val dp   = parent.context.resources.displayMetrics.density
            val card = androidx.cardview.widget.CardView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { (it as RecyclerView.LayoutParams).bottomMargin = (10 * dp).toInt() }
                radius = 12 * dp; cardElevation = 4 * dp
                setContentPadding((14*dp).toInt(),(12*dp).toInt(),(14*dp).toInt(),(12*dp).toInt())
            }
            return VH(card)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val r  = items[position]
            val dp = holder.card.context.resources.displayMetrics.density
            val ll = LinearLayout(holder.card.context).apply { orientation = LinearLayout.VERTICAL }

            // Fila título + tipo
            val rowTop = LinearLayout(holder.card.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = android.view.Gravity.CENTER_VERTICAL
            }
            val tvTitulo = TextView(holder.card.context).apply {
                text = r.titulo
                textSize = 15f
                setTextColor(holder.card.context.getColor(R.color.texto_principal))
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val tvTipo = TextView(holder.card.context).apply {
                text = if (r.esPredefinida) "⭐ Oficial" else "👥 Personalizada"
                textSize = 11f
                setTextColor(holder.card.context.getColor(R.color.texto_secundario))
            }
            rowTop.addView(tvTitulo); rowTop.addView(tvTipo)

            // Descripción
            val tvDesc = TextView(holder.card.context).apply {
                text      = r.descripcion ?: ""
                textSize  = 12f
                setTextColor(holder.card.context.getColor(R.color.texto_secundario))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = (4*dp).toInt() }
                visibility = if (r.descripcion.isNullOrBlank()) View.GONE else View.VISIBLE
            }

            // Barra de progreso
            val progresoPct = if (r.coste > 0) (misPuntos.coerceAtMost(r.coste) * 100 / r.coste) else 100
            val progressBar = ProgressBar(holder.card.context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max      = 100
                progress = progresoPct
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (10*dp).toInt()
                ).also { it.topMargin = (8*dp).toInt() }
            }

            // Texto progreso — sin mencionar el 10% (ya aparece en la cabecera informativa)
            val puedeCanjear = misPuntos >= r.coste
            val tvProgreso = TextView(holder.card.context).apply {
                textSize = 12f
                setTextColor(holder.card.context.getColor(R.color.texto_secundario))
                text = if (puedeCanjear)
                    "✅ ¡Puedes canjearla! Tienes $misPuntos pts 🎁"
                else
                    "🎁 $misPuntos / ${r.coste} pts  •  $progresoPct%  •  faltan ${r.coste - misPuntos} pts"
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = (2*dp).toInt() }
            }

            // Fila coste + botón
            val rowBottom = LinearLayout(holder.card.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = (8*dp).toInt() }
            }
            val tvCoste = TextView(holder.card.context).apply {
                text = "${r.coste} pts"
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(holder.card.context.getColor(R.color.texto_principal))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val btnCanjear = Button(holder.card.context).apply {
                text      = "Canjear"
                isEnabled = puedeCanjear
                textSize  = 13f
            }
            rowBottom.addView(tvCoste); rowBottom.addView(btnCanjear)

            ll.addView(rowTop); ll.addView(tvDesc); ll.addView(progressBar)
            ll.addView(tvProgreso); ll.addView(rowBottom)
            holder.card.removeAllViews(); holder.card.addView(ll)

            // Color fondo: verde suave si puede canjear
            holder.card.setCardBackgroundColor(
                if (puedeCanjear) Color.parseColor("#F1F8E9") else Color.WHITE
            )

            btnCanjear.setOnClickListener { confirmarCanje(r) }

            // Long press en personalizada: eliminar
            if (r.esPersonalizada) {
                holder.card.setOnLongClickListener {
                    androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Eliminar recompensa")
                        .setMessage("¿Eliminar \"${r.titulo}\"?")
                        .setPositiveButton("Eliminar") { _, _ ->
                            lifecycleScope.launch {
                                repo.eliminarRecompensa(r.id)
                                cargarDatos()
                            }
                        }.setNegativeButton("Cancelar", null).show()
                    true
                }
            }
        }

        override fun getItemCount() = items.size
    }

    // ── TAB 2: Pendientes de confirmar ─────────────────────────────────────
    private suspend fun cargarPendientes() {
        b.tvInfoRecompensas.visibility = View.GONE
        val uid     = LocalizadorServicios.repositorioAuth.usuarioActual()?.id ?: ""
        val grupoId = parejaVM.grupo.value?.id ?: ""
        val lista   = if (grupoId.isBlank()) emptyList()
                      else repo.obtenerCanjesPendientesParaMiembro(grupoId, uid).getOrNull() ?: emptyList()
        b.rvRecompensas.adapter = AdapterCanjes(lista, mostrarAcciones = true)
    }

    // ── TAB 3: Historial ────────────────────────────────────────────────────
    private suspend fun cargarHistorial() {
        b.tvInfoRecompensas.visibility = View.GONE
        val uid     = LocalizadorServicios.repositorioAuth.usuarioActual()?.id
        val grupoId = parejaVM.grupo.value?.id
        val lista   = repo.obtenerHistorialCanjes(uid, grupoId).getOrNull() ?: emptyList()
        b.rvRecompensas.adapter = AdapterCanjes(lista, mostrarAcciones = false)
    }

    // ── Adapter canjes (Pendientes + Historial) ─────────────────────────────
    private inner class AdapterCanjes(
        private val items: List<Canje>,
        private val mostrarAcciones: Boolean
    ) : RecyclerView.Adapter<AdapterCanjes.VH>() {

        inner class VH(val card: androidx.cardview.widget.CardView) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val dp   = parent.context.resources.displayMetrics.density
            val card = androidx.cardview.widget.CardView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { (it as RecyclerView.LayoutParams).bottomMargin = (10*dp).toInt() }
                radius = 12 * dp; cardElevation = 4 * dp
                setContentPadding((14*dp).toInt(),(12*dp).toInt(),(14*dp).toInt(),(12*dp).toInt())
            }
            return VH(card)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val c  = items[position]
            val dp = holder.card.context.resources.displayMetrics.density
            val ll = LinearLayout(holder.card.context).apply { orientation = LinearLayout.VERTICAL }

            val tvTitulo = TextView(holder.card.context).apply {
                text = c.tituloRecompensa
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(holder.card.context.getColor(R.color.texto_principal))
            }
            val fechaTxt = c.fecha?.toDate()?.let { d ->
                val cal = java.util.Calendar.getInstance().apply { time = d }
                "${cal.get(java.util.Calendar.DAY_OF_MONTH)}/${cal.get(java.util.Calendar.MONTH)+1}/${cal.get(java.util.Calendar.YEAR)}"
            } ?: ""
            val estadoBadge = when (c.estado) {
                "aceptado"  -> "✅ Aceptado"
                "rechazado" -> "❌ Rechazado"
                else        -> "⏳ Pendiente confirmación"
            }
            val tvMeta = TextView(holder.card.context).apply {
                text = "${c.nombreUsuario}  •  ${c.coste} pts  •  $fechaTxt  •  $estadoBadge"
                textSize = 12f
                setTextColor(holder.card.context.getColor(R.color.texto_secundario))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = (4*dp).toInt() }
            }
            ll.addView(tvTitulo); ll.addView(tvMeta)

            // Acciones: solo en tab Pendientes (canjes del otro miembro que yo debo confirmar)
            if (mostrarAcciones && c.estado == "pendiente") {
                val rowAcc = LinearLayout(holder.card.context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).also { it.topMargin = (8*dp).toInt() }
                }
                val btnAceptar = Button(holder.card.context).apply {
                    text = "✅ Aceptar"
                    textSize = 12f
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = (4*dp).toInt() }
                    setOnClickListener { responderCanje(c, true) }
                }
                val btnRechazar = Button(holder.card.context).apply {
                    text = "❌ Rechazar"
                    textSize = 12f
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = (4*dp).toInt() }
                    setOnClickListener { responderCanje(c, false) }
                }
                rowAcc.addView(btnAceptar); rowAcc.addView(btnRechazar)
                ll.addView(rowAcc)
            }

            holder.card.removeAllViews(); holder.card.addView(ll)
            holder.card.setCardBackgroundColor(when (c.estado) {
                "aceptado"  -> Color.parseColor("#F1F8E9")
                "rechazado" -> Color.parseColor("#FFEBEE")
                else        -> Color.WHITE
            })
        }

        override fun getItemCount() = items.size
    }

    // ── Diálogo confirmar canje ─────────────────────────────────────────────
    private fun confirmarCanje(r: Recompensa) {
        val msg = "Canjear \"${r.titulo}\" por ${r.coste} pts 🎁\n\n" +
                  "Se descontarán de tus puntos de recompensa (los que ganas completando tareas).\n\n" +
                  "El otro miembro del grupo recibirá una notificación y deberá aceptar."
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Confirmar canje")
            .setMessage(msg)
            .setPositiveButton("Canjear") { _, _ ->
                lifecycleScope.launch { ejecutarCanje(r) }
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private suspend fun ejecutarCanje(r: Recompensa) {
        val usuario = LocalizadorServicios.repositorioAuth.usuarioActual()
            ?: return Toast.makeText(requireContext(), "Sin sesión", Toast.LENGTH_SHORT).show()
        val grupoId = parejaVM.grupo.value?.id

        val res = repo.canjearRecompensa(
            recompensaId     = r.id,
            usuarioUid       = usuario.id,
            tituloRecompensa = r.titulo,
            coste            = r.coste,
            nombreUsuario    = usuario.nombre.ifBlank { usuario.email },
            grupoId          = grupoId
        )
        if (res.isSuccess) {
            Toast.makeText(requireContext(), "¡Canje solicitado! -${r.coste} pts 🎁 recompensa. El otro miembro debe aceptarlo.", Toast.LENGTH_LONG).show()
            // Notificar a los demás miembros
            val grupo = parejaVM.grupo.value
            if (grupo != null) {
                grupo.miembros.keys.filter { it != usuario.id }.forEach { miembroUid ->
                    val contenido = mapOf(
                        "tipo"         to "canje",
                        "canjeId"      to (res.getOrNull() ?: ""),
                        "recompensa"   to r.titulo,
                        "usuario"      to usuario.nombre.ifBlank { usuario.email },
                        "texto"        to "${usuario.nombre.ifBlank { usuario.email }} quiere canjear \"${r.titulo}\" (${r.coste} pts). ¿Lo aceptas?"
                    )
                    repoNot.enviarNotificacion(
                        Notificacion(id = "", tipo = "canje", contenido = contenido,
                            destinatario = miembroUid, visto = false, fecha = Timestamp.now())
                    )
                }
            }
            cargarDatos()
        } else {
            Toast.makeText(requireContext(), res.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_LONG).show()
        }
    }

    private fun responderCanje(c: Canje, aceptado: Boolean) {
        val msg = if (aceptado) "Aceptar el canje de \"${c.tituloRecompensa}\" de ${c.nombreUsuario}?"
                  else          "Rechazar el canje de \"${c.tituloRecompensa}\"? Se le devolverán los puntos."
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(if (aceptado) "Aceptar canje" else "Rechazar canje")
            .setMessage(msg)
            .setPositiveButton(if (aceptado) "Aceptar" else "Rechazar") { _, _ ->
                lifecycleScope.launch {
                    val res = repo.responderCanje(c.id, aceptado)
                    val texto = if (res.isSuccess) {
                        if (aceptado) "Canje aceptado ✅" else "Canje rechazado (puntos devueltos)"
                    } else res.exceptionOrNull()?.message ?: "Error"
                    Toast.makeText(requireContext(), texto, Toast.LENGTH_SHORT).show()
                    cargarDatos()
                }
            }
            .setNegativeButton("Cancelar", null).show()
    }

    // ── Diálogo crear recompensa personalizada ──────────────────────────────
    private fun mostrarDialogoCrearPersonalizada() {
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density
        val ll  = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20*dp).toInt(), (12*dp).toInt(), (20*dp).toInt(), (4*dp).toInt())
        }

        val etTitulo = EditText(ctx).apply {
            hint = "Título de la recompensa"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        val etDesc = EditText(ctx).apply {
            hint = "Descripción (opcional)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }

        // El coste lo elige el sistema según nivel, no el usuario
        val tvNivelLabel = TextView(ctx).apply {
            text = "Nivel de la recompensa:"
            textSize = 13f
            setTextColor(ctx.getColor(R.color.texto_principal))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (10*dp).toInt() }
        }
        val niveles = arrayOf("Pequeña (100 pts)", "Media (300 pts)", "Grande (600 pts)", "Especial (1000 pts)")
        val costesNivel = intArrayOf(100, 300, 600, 1000)
        val spNivel = android.widget.Spinner(ctx).apply {
            adapter = android.widget.ArrayAdapter(ctx, android.R.layout.simple_spinner_item, niveles)
                .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        }
        val tvCosteFijo = TextView(ctx).apply {
            text = "Coste asignado: ${costesNivel[0]} pts 🎁"
            textSize = 12f
            setTextColor(ctx.getColor(R.color.texto_secundario))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (4*dp).toInt() }
        }
        spNivel.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                tvCosteFijo.text = "Coste asignado: ${costesNivel[pos]} pts 🎁"
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }

        ll.addView(etTitulo); ll.addView(etDesc)
        ll.addView(tvNivelLabel); ll.addView(spNivel); ll.addView(tvCosteFijo)

        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle("Nueva recompensa personalizada")
            .setView(ll)
            .setPositiveButton("Crear") { _, _ ->
                val titulo = etTitulo.text.toString().trim()
                val desc   = etDesc.text.toString().trim().ifBlank { null }
                val coste  = costesNivel[spNivel.selectedItemPosition]
                if (titulo.isBlank()) {
                    Toast.makeText(ctx, "Introduce un título", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    val uid     = LocalizadorServicios.repositorioAuth.usuarioActual()?.id ?: ""
                    val grupoId = parejaVM.grupo.value?.id
                    val res     = repo.crearRecompensaPersonalizada(titulo, desc, coste, uid, grupoId)
                    if (res.isSuccess) {
                        Toast.makeText(ctx, "Recompensa \"$titulo\" creada ($coste pts)", Toast.LENGTH_SHORT).show()
                        cargarDatos()
                    } else {
                        Toast.makeText(ctx, res.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null).show()
    }
}
