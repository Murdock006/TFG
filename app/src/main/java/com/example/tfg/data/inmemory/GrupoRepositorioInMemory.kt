package com.example.tfg.data.inmemory

import com.example.tfg.modelo.Grupo
import com.example.tfg.repositorio.GrupoRepositorio
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.UUID

class GrupoRepositorioInMemory : GrupoRepositorio {

    private val grupos = mutableListOf<Grupo>()
    private val gruposFlow = MutableStateFlow<List<Grupo>>(emptyList())

    override suspend fun crearGrupo(grupo: Grupo): Result<Grupo> = withContext(Dispatchers.Default) {
        val g = grupo.copy(id = UUID.randomUUID().toString())
        grupos.add(g)
        gruposFlow.value = grupos.toList()
        Result.success(g)
    }

    override suspend fun obtenerGrupos(): Result<List<Grupo>> = withContext(Dispatchers.Default) {
        Result.success(grupos.toList())
    }

    override fun observarGrupos(): Flow<List<Grupo>> = gruposFlow
}
