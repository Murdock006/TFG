package es.sintaxys.teamtask.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import es.sintaxys.teamtask.repositorio.AuthRepositorio
import es.sintaxys.teamtask.repositorio.AvatarRepositorio
import es.sintaxys.teamtask.service.LocalizadorServicios
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AvatarViewModel(
    application: Application,
    private val repositorioAvatar: AvatarRepositorio = LocalizadorServicios.repositorioAvatar,
    private val repositorioAuth: AuthRepositorio = LocalizadorServicios.repositorioAuth
) : AndroidViewModel(application) {

    /**
     * Keeps the androidx AndroidViewModelFactory's single-Application lookup working.
     *
     * The defaults are passed explicitly (mirroring `ParejaViewModel`) because a bare
     * `this(application)` would resolve to this same secondary constructor and produce a
     * delegation cycle.
     */
    constructor(application: Application) : this(
        application,
        LocalizadorServicios.repositorioAvatar,
        LocalizadorServicios.repositorioAuth
    )

    private val TAG = "AvatarViewModel"

    // Resultado de la última operación de subida (éxito/error para la UI)
    private val _avatarState = MutableStateFlow<Result<Unit>?>(null)
    val avatarState: StateFlow<Result<Unit>?> = _avatarState.asStateFlow()

    // Avatar actualmente mostrado; null => placeholder
    private val _avatarActual = MutableStateFlow<Bitmap?>(null)
    val avatarActual: StateFlow<Bitmap?> = _avatarActual.asStateFlow()

    // Estado de carga
    private val _cargando = MutableStateFlow(false)
    val cargando: StateFlow<Boolean> = _cargando.asStateFlow()

    /**
     * Sube un avatar seleccionado por el usuario y, en caso de éxito, vuelve a resolverlo para
     * que [avatarActual] emita el nuevo bitmap.
     */
    fun subirAvatar(imageUri: Uri) {
        viewModelScope.launch {
            _cargando.value = true
            try {
                val res = repositorioAvatar.subirAvatar(imageUri)
                if (res.isSuccess) {
                    _avatarState.value = Result.success(Unit)
                    val uid = repositorioAuth.usuarioActual()?.id
                    if (uid != null) {
                        _avatarActual.value = repositorioAvatar.obtenerAvatar(uid, res.getOrNull())
                    }
                    Log.d(TAG, "Avatar subido exitosamente")
                } else {
                    _avatarState.value = Result.failure(res.exceptionOrNull() ?: Exception("Error desconocido"))
                    Log.e(TAG, "Error subiendo avatar: ${res.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception en subirAvatar", e)
                _avatarState.value = Result.failure(e)
            } finally {
                _cargando.value = false
            }
        }
    }

    /**
     * Carga el avatar actual del usuario autenticado a través del repositorio canónico,
     * pasando el hint [es.sintaxys.teamtask.modelo.Usuario.avatarUpdatedAt].
     */
    fun cargarAvatarActual() {
        viewModelScope.launch {
            try {
                val usuario = repositorioAuth.usuarioActual()
                val uid = usuario?.id
                if (uid == null) {
                    _avatarActual.value = null
                    return@launch
                }
                _avatarActual.value = repositorioAvatar.obtenerAvatar(uid, usuario.avatarUpdatedAt)
                Log.d(TAG, "Avatar actual cargado")
            } catch (e: Exception) {
                Log.e(TAG, "Error cargando avatar actual", e)
            }
        }
    }

    /**
     * Limpia el estado local del avatar. `eliminarAvatar` is intentionally NOT wired to any UI:
     * this change adds no remote delete affordance.
     */
    fun eliminarAvatar() {
        _avatarActual.value = null
    }

    /**
     * Resetea el estado de subida para limpiar errores
     */
    fun resetAvatarState() {
        _avatarState.value = null
    }
}
