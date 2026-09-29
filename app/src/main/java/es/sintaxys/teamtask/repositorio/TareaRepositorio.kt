package es.sintaxys.teamtask.repositorio

import es.sintaxys.teamtask.modelo.Tarea
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow

interface TareaRepositorio {
    suspend fun crearTarea(tarea: Tarea): Result<Tarea>
    suspend fun obtenerTareas(): Result<List<Tarea>>
    fun observarTareas(): Flow<List<Tarea>>
    fun observarTareasPorGrupo(grupoId: String): Flow<List<Tarea>>
    suspend fun actualizarTarea(tarea: Tarea): Result<Tarea>
    suspend fun actualizarRecordatorio(tareaId: String, minutosAntes: Int): Result<Unit>
    suspend fun actualizarImportante(tareaId: String, esImportante: Boolean): Result<Unit>
    suspend fun reprogramarTarea(tareaId: String, fechaProgramada: Timestamp?): Result<Unit>
    suspend fun resolverReclamo(tareaId: String, aceptado: Boolean): Result<Tarea>
    suspend fun marcarCompletada(tareaId: String, ejecutorUid: String): Result<Unit>
    suspend fun confirmarTarea(tareaId: String, confirmadoPorUid: String): Result<Unit>
}
