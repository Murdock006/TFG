package com.example.tfg.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tfg.modelo.Grupo
import com.example.tfg.repositorio.RepositorioPareja
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ParejaViewModel(application: Application, private val repo: RepositorioPareja = RepositorioPareja()) : AndroidViewModel(application) {

    // Constructor adicional que delega al repositorio por defecto (compatible con fábrica AndroidViewModel)
    constructor(application: Application) : this(application, RepositorioPareja())

    private val _grupo = MutableStateFlow<Grupo?>(null)
     val grupo: StateFlow<Grupo?> = _grupo
     
     // Estados para operaciones asincrónicas (reemplazan callbacks)
     private val _crearGrupoState = MutableStateFlow<Result<String>?>(null)
     val crearGrupoState: StateFlow<Result<String>?> = _crearGrupoState
     
     private val _salirGrupoState = MutableStateFlow<Result<Unit>?>(null)
     val salirGrupoState: StateFlow<Result<Unit>?> = _salirGrupoState
     
     private val _crearInvitacionState = MutableStateFlow<Result<String>?>(null)
     val crearInvitacionState: StateFlow<Result<String>?> = _crearInvitacionState
     
     private val _aceptarInvitacionState = MutableStateFlow<Result<String>?>(null)
     val aceptarInvitacionState: StateFlow<Result<String>?> = _aceptarInvitacionState
     
     private val _buscarInvitacionesState = MutableStateFlow<Result<List<com.example.tfg.modelo.Invitacion>>?>(null)
     val buscarInvitacionesState: StateFlow<Result<List<com.example.tfg.modelo.Invitacion>>?> = _buscarInvitacionesState
     
     private val TAG = "ParejaViewModel"

     private val prefsName = "tfg_prefs"
     private val keyGrupoId = "grupoId"

     // Job para la observación remota del grupo actual
     private var grupoObserverJob: Job? = null

    init {
        // Cargar grupoId persistido y obtener grupo desde repo para mostrar inmediatamente
        viewModelScope.launch {
            try {
                val prefs = getApplication<Application>().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                val gid = prefs.getString(keyGrupoId, null)
                if (!gid.isNullOrBlank()) {
                    Log.d(TAG, "Cargando grupoId persistido=$gid")
                    val g = repo.obtenerGrupoPorId(gid).getOrNull()
                    if (g != null) {
                        _grupo.value = g
                        // empezar a observar cambios remotos
                        startObservingGrupo(gid)
                    } else {
                        // si no existe en Firestore, limpiar preferencia
                        prefs.edit().remove(keyGrupoId).apply()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cargando grupo persistido", e)
            }
        }
    }

    // permitir fijar un grupo en memoria (fallback temporal) y persistir localmente
    fun setGrupoLocal(grupo: Grupo) {
        _grupo.value = grupo
        try {
            val prefs = getApplication<Application>().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            prefs.edit().putString(keyGrupoId, grupo.id).apply()
            Log.d(TAG, "setGrupoLocal: guardado grupoId=${grupo.id} en SharedPreferences")
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando grupoId en prefs", e)
        }
        // iniciar observación remota del grupo para sincronizar miembros y nombres
        startObservingGrupo(grupo.id)
    }

    private fun startObservingGrupo(grupoId: String) {
        // cancelar observador anterior
        grupoObserverJob?.cancel()
        grupoObserverJob = viewModelScope.launch {
            try {
                repo.observarGrupoPorId(grupoId).collect { g ->
                    // actualizar estado local (puede ser null si eliminado)
                    _grupo.value = g
                    if (g == null) {
                        // si el grupo fue borrado remotamente, limpiar las prefs
                        try {
                            val prefs = getApplication<Application>().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                            prefs.edit().remove(keyGrupoId).apply()
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error observando grupo remoto", e)
            }
        }
    }

    // permitir al usuario salir del grupo
     fun salirGrupo(usuarioUid: String) {
         viewModelScope.launch {
             val gid = _grupo.value?.id
             if (gid.isNullOrBlank()) {
                 _salirGrupoState.value = Result.failure(Exception("No hay grupo activo"))
                 return@launch
             }
             try {
                 val res = repo.quitarMiembroGrupo(gid, usuarioUid)
                 if (res.isSuccess) {
                     // limpiar estado local y preferencias
                     _grupo.value = null
                     try {
                         val prefs = getApplication<Application>().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                         prefs.edit().remove(keyGrupoId).apply()
                     } catch (_: Exception) {}
                     // cancelar observador
                     grupoObserverJob?.cancel()
                     grupoObserverJob = null
                 }
                 _salirGrupoState.value = res
             } catch (e: Exception) {
                 Log.e(TAG, "salirGrupo(Exception)", e)
                 _salirGrupoState.value = Result.failure(Exception(e.message ?: "Error saliendo del grupo"))
             }
         }
     }
     
     // Versión con callback para compatibilidad hacia atrás (DEPRECATED)
     fun salirGrupo(usuarioUid: String, onResult: (Result<Unit>) -> Unit) {
         viewModelScope.launch {
             salirGrupo(usuarioUid)
             // Observar el estado una sola vez
             salirGrupoState.collect { res ->
                 if (res != null) {
                     onResult(res)
                 }
             }
         }
     }

    // función original: crear grupo sin callback (usa StateFlow internamente)
     fun crearGrupo(nombre: String, creadorUid: String) {
         viewModelScope.launch {
             try {
                 val res = repo.crearGrupo(nombre, creadorUid)
                 if (res.isSuccess) {
                     val gId = res.getOrNull()
                     if (!gId.isNullOrBlank()) {
                         val gObj = repo.obtenerGrupoPorId(gId).getOrNull()
                         if (gObj != null) setGrupoLocal(gObj)
                     }
                 }
                 _crearGrupoState.value = res
             } catch (e: Exception) {
                 Log.e(TAG, "crearGrupo(Exception)", e)
                 _crearGrupoState.value = Result.failure(Exception(e.message ?: "Error creando grupo"))
             }
         }
     }

     // nueva función: crear grupo y devolver resultado mediante StateFlow
     fun crearGrupo(nombre: String, creadorUid: String, onResult: (Result<String>) -> Unit) {
         viewModelScope.launch {
             crearGrupo(nombre, creadorUid)
             // Observar el estado una sola vez
             crearGrupoState.collect { res ->
                 if (res != null) {
                     onResult(res)
                 }
             }
         }
     }

    fun crearInvitacion(grupoId: String, creadoPor: String, correoDestino: String? = null, horasExpiracion: Int? = 72) {
         viewModelScope.launch {
             try {
                 val res = repo.crearInvitacion(grupoId, creadoPor, correoDestino, horasExpiracion)
                 _crearInvitacionState.value = res
             } catch (e: Exception) {
                 Log.e(TAG, "crearInvitacion(Exception)", e)
                 _crearInvitacionState.value = Result.failure(Exception(e.message ?: "Error creando invitación"))
             }
         }
     }
     
     // Versión con callback para compatibilidad hacia atrás (DEPRECATED)
     fun crearInvitacion(grupoId: String, creadoPor: String, correoDestino: String? = null, horasExpiracion: Int? = 72, onResult: (Result<String>) -> Unit) {
         viewModelScope.launch {
             crearInvitacion(grupoId, creadoPor, correoDestino, horasExpiracion)
             crearInvitacionState.collect { res ->
                 if (res != null) {
                     onResult(res)
                 }
             }
         }
     }

    fun aceptarInvitacion(codigo: String, usuarioUid: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            try {
                val res = repo.aceptarInvitacion(codigo, usuarioUid)
                if (res.isSuccess) {
                    // actualizar grupo en memoria y persistir
                    val g = repo.obtenerGrupoPorUsuario(usuarioUid).getOrNull()
                    if (g != null) setGrupoLocal(g)
                }
                onResult(res)
            } catch (e: Exception) {
                Log.e(TAG, "aceptarInvitacion(Exception)", e)
                onResult(Result.failure(Exception(e.message ?: "Error aceptando invitación")))
            }
        }
    }

    // Nueva función: aceptar invitación por código normalizado (intenta variantes simples)
    fun aceptarInvitacionPorCodigo(codigoRaw: String, usuarioUid: String, onResult: (Result<String>) -> Unit) {
        val codigo = codigoRaw.trim()
        if (codigo.isEmpty()) {
            onResult(Result.failure(Exception("Código vacío")))
            return
        }
        viewModelScope.launch {
            try {
                val variantes = listOf(codigo, codigo.uppercase(), codigo.lowercase())
                for (v in variantes) {
                    val res = repo.aceptarInvitacion(v, usuarioUid)
                    if (res.isSuccess) {
                        val g = repo.obtenerGrupoPorUsuario(usuarioUid).getOrNull()
                        if (g != null) setGrupoLocal(g)
                        onResult(res)
                        return@launch
                    }
                    Log.d(TAG, "aceptarInvitacionPorCodigo: intento con '$v' falló: ${res.exceptionOrNull()?.message}")
                }
                onResult(Result.failure(Exception("No se pudo aceptar la invitación: código no encontrado o error. Comprueba el código y tu conexión.")))
            } catch (e: Exception) {
                Log.e(TAG, "aceptarInvitacionPorCodigo(Exception)", e)
                onResult(Result.failure(Exception(e.message ?: "Error aceptando invitación")))
            }
        }
    }

    fun buscarInvitacionesPorCorreo(correo: String, onResult: (Result<List<com.example.tfg.modelo.Invitacion>>) -> Unit) {
        viewModelScope.launch {
            try {
                val res = repo.buscarInvitacionesPorCorreo(correo)
                onResult(res)
            } catch (e: Exception) {
                Log.e(TAG, "buscarInvitacionesPorCorreo(Exception)", e)
                onResult(Result.failure(Exception(e.message ?: "Error buscando invitaciones")))
            }
        }
    }

    // Actualiza el nombre del grupo y actualiza estado local si éxito
    fun actualizarNombreGrupo(grupoId: String, nuevoNombre: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            try {
                val res = repo.actualizarNombreGrupo(grupoId, nuevoNombre)
                if (res.isSuccess) {
                    // forzar refresco local: leer grupo actualizado y setGrupoLocal
                    val gObj = repo.obtenerGrupoPorId(grupoId).getOrNull()
                    if (gObj != null) setGrupoLocal(gObj)
                }
                onResult(res)
            } catch (e: Exception) {
                Log.e(TAG, "actualizarNombreGrupo(Exception)", e)
                onResult(Result.failure(Exception(e.message ?: "Error actualizando nombre de grupo")))
            }
        }
    }

    // Nuevo: cargar el grupo asociado a un usuario (útil al iniciar la app)
    fun cargarGrupoPorUsuario(usuarioUid: String) {
        viewModelScope.launch {
            try {
                // Primero, intentar leer el campo 'grupoId' directamente desde el documento de usuario
                val gidRes = repo.obtenerGrupoIdDesdeUsuario(usuarioUid)
                if (gidRes.isSuccess) {
                    val gid = gidRes.getOrNull()
                    if (!gid.isNullOrBlank()) {
                        // cargar el grupo por id
                        val gRes = repo.obtenerGrupoPorId(gid)
                        if (gRes.isSuccess) {
                            val g = gRes.getOrNull()
                            if (g != null) {
                                // validar que el usuario está en miembros
                                if (g.miembros.containsKey(usuarioUid)) {
                                    setGrupoLocal(g)
                                    return@launch
                                } else {
                                    // inconsistencia: borrar grupoId del usuario en Firestore
                                    try {
                                        repo.limpiarGrupoIdUsuario(usuarioUid)
                                    } catch (_: Exception) {}
                                    // continuar: no asignar grupo
                                }
                            }
                        }
                    }
                }
                // Si no hay grupoId válido en el documento de usuario, no hacemos fallback: el usuario no pertenece a ningún grupo
                _grupo.value = null
                try {
                    val prefs = getApplication<Application>().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    prefs.edit().remove(keyGrupoId).apply()
                } catch (_: Exception) {}
            } catch (e: Exception) {
                Log.e(TAG, "cargarGrupoPorUsuario(Exception)", e)
            }
        }
    }

     // Limpieza de grupoId para un usuario (útil para pruebas y correcciones en desarrollo)
     fun limpiarGrupoIdUsuario(usuarioUid: String, onResult: (Result<Unit>) -> Unit) {
         viewModelScope.launch {
             try {
                 val res = repo.limpiarGrupoIdUsuario(usuarioUid)
                 onResult(res)
             } catch (e: Exception) {
                 Log.e(TAG, "limpiarGrupoIdUsuario(Exception)", e)
                 onResult(Result.failure(Exception(e.message ?: "Error limpiando grupoId")))
             }
         }
     }

     // Limpiar recursos cuando el ViewModel se destruye
     override fun onCleared() {
         super.onCleared()
         grupoObserverJob?.cancel()
         grupoObserverJob = null
         Log.d(TAG, "ParejaViewModel limpiado")
     }
 }
