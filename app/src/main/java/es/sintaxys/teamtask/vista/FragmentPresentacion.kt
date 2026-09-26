package es.sintaxys.teamtask.vista

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import es.sintaxys.teamtask.databinding.FragmentPresentacionBinding

class FragmentPresentacion : Fragment() {

    private lateinit var binding: FragmentPresentacionBinding
    private val mensajes = listOf(
        "Cargando dependencias...",
        "Inicializando módulos...",
        "Preparando Tareas..."
    )
    private var mensajeIndex = 0
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPresentacionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mostrarMensajes()
    }

    private fun mostrarMensajes() {
        runnable = object : Runnable {
            override fun run() {
                // Verificar que el fragment siga visible y no sea null
                if (!isAdded || view == null) return

                if (mensajeIndex < mensajes.size) {
                    binding.textoPresentacion.text = mensajes[mensajeIndex]
                    mensajeIndex++
                    handler.postDelayed(this, 2000)
                } else {
                    // La transición Presentación → Login la posee el coordinator de MainActivity;
                    // este Fragment solo señala que su secuencia de mensajes terminó.
                    if (isAdded) {
                        (activity as? MainActivity)?.onPresentacionMensajesCompletados()
                    }
                }
            }
        }
        handler.post(runnable!!)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Cancelar todos los postDelayed pendientes
        runnable?.let { handler.removeCallbacks(it) }
    }
}
