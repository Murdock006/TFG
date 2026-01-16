package com.example.tfg.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tfg.repositorio.RepositorioDatos
import com.example.tfg.service.ServiceLocator
import kotlinx.coroutines.launch

// ViewModel en castellano que expone LiveData para la vista
class VistaModeloPrincipal(
    private val repositorioDatos: RepositorioDatos = RepositorioDatos(),
) : ViewModel() {

    private val _textoLiveData = MutableLiveData<String>()
    val textoLiveData: LiveData<String> = _textoLiveData

    private val _numGrupos = MutableLiveData<Int>(0)
    val numGrupos: LiveData<Int> = _numGrupos

    private val _numTareas = MutableLiveData<Int>(0)
    val numTareas: LiveData<Int> = _numTareas

    init {
        // Inicializar con valor del repositorio
        _textoLiveData.value = repositorioDatos.obtenerTexto()
        actualizarConteos()
    }

    fun actualizarTexto() {
        // Ejemplo sencillo de actualización
        _textoLiveData.value = repositorioDatos.obtenerTexto()
    }

    fun actualizarConteos() {
        viewModelScope.launch {
            val gruposRes = ServiceLocator.grupoRepositorio.obtenerGrupos()
            val tareasRes = ServiceLocator.tareaRepositorio.obtenerTareas()
            _numGrupos.value = gruposRes.getOrNull()?.size ?: 0
            _numTareas.value = tareasRes.getOrNull()?.size ?: 0
        }
    }
}
