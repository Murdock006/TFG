package es.sintaxys.teamtask.service

import es.sintaxys.teamtask.TFGApplication
import es.sintaxys.teamtask.data.firebase.AuthRepositorioFirebase
import es.sintaxys.teamtask.data.firebase.AvatarRepositorioFirebase
import es.sintaxys.teamtask.data.firebase.TareaRepositorioFirebase
import es.sintaxys.teamtask.data.inmemory.AuthRepositorioInMemory
import es.sintaxys.teamtask.data.inmemory.GrupoRepositorioInMemory
import es.sintaxys.teamtask.repositorio.AuthRepositorio
import es.sintaxys.teamtask.repositorio.AvatarRepositorio
import es.sintaxys.teamtask.repositorio.GrupoRepositorio
import es.sintaxys.teamtask.repositorio.TareaRepositorio
import es.sintaxys.teamtask.service.firebase.FirebaseComposition

object LocalizadorServicios {
    val repositorioAuth: AuthRepositorio by lazy {
        FirebaseComposition.requireContext()
        AuthRepositorioFirebase(FirebaseComposition.auth(), FirebaseComposition.firestore(), FirebaseComposition.storage())
    }

    val repositorioGrupo: GrupoRepositorio by lazy {
        FirebaseComposition.requireContext()
        es.sintaxys.teamtask.repositorio.RepositorioPareja(FirebaseComposition.firestore())
    }

    val repositorioTarea: TareaRepositorio by lazy {
        FirebaseComposition.requireContext()
        TareaRepositorioFirebase(FirebaseComposition.firestore())
    }

    val repositorioAvatar: AvatarRepositorio by lazy {
        FirebaseComposition.requireContext()
        val contexto = requireNotNull(TFGApplication.appContext) { "TFGApplication.appContext no inicializado" }
        AvatarRepositorioFirebase(
            firestore = FirebaseComposition.firestore(),
            auth = FirebaseComposition.auth(),
            context = contexto
        )
    }
}
