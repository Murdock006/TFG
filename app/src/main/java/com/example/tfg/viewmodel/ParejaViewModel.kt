package com.example.tfg.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tfg.repositorio.RepositorioPareja
import com.example.tfg.modelo.Grupo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ParejaViewModel(private val repo: RepositorioPareja = RepositorioPareja()) : ViewModel() {

    private val _grupo = MutableStateFlow<Grupo?>(null)
    val grupo: StateFlow<Grupo?> = _grupo

    fun crearGrupo(nombre: String, creadorUid: String) {
        viewModelScope.launch {
            val res = repo.crearGrupo(nombre, creadorUid)
            if (res.isSuccess) {
                val gId = res.getOrNull()
                // obtener grupo recien creado
                val g = repo.obtenerGrupoPorUsuario(creadorUid).getOrNull()
                _grupo.value = g
            }
        }
    }

    fun crearInvitacion(grupoId: String, creadoPor: String, horasExpiracion: Int? = 72, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val res = repo.crearInvitacion(grupoId, creadoPor, horasExpiracion)
            onResult(res)
        }
    }

    fun aceptarInvitacion(codigo: String, usuarioUid: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val res = repo.aceptarInvitacion(codigo, usuarioUid)
            if (res.isSuccess) {
                // actualizar grupo en memoria
                _grupo.value = repo.obtenerGrupoPorUsuario(usuarioUid).getOrNull()
            }
            onResult(res)
        }
    }
}
