status: complete
executive_summary: "TeamTask es un app Android Kotlin de un solo modulo con UI XML/Fragments y una arquitectura hibrida. Firebase es el backend efectivo, pero los limites UI/ViewModel/repositorio no son uniformes: existen accesos directos desde UI, un service locator, dos implementaciones de tareas con reglas divergentes y dos estrategias de avatar."
artifacts:
  - "openspec/changes/teamtask-architecture-inventory/explore.md"
next_recommended: "Usar este inventario como base de un proposal de guia arquitectonica; antes de prescribir cambios, confirmar reglas Firebase, indices, flujos de despliegue y comportamiento de produccion."
risks:
  - "No se ejecutaron Gradle, tests ni inspecciones de Firebase por restriccion explicita."
  - "El worktree estaba dirty antes de esta exploracion y no fue modificado salvo este artefacto OpenSpec."
skill_resolution: "android-mvvm y cognitive-doc-design; convenciones verificadas en .atl/skill-registry.md y openspec/config.yaml"

## Exploration: TeamTask architecture inventory
### Current State

**Alcance y hechos base**

- El proyecto es un unico modulo Gradle `app`; `settings.gradle.kts` no declara modulos adicionales. El namespace/application id real es `com.example.tfg` (`app/build.gradle.kts:7-17`).
- La UI usa XML, Fragments, ViewBinding y Navigation Component (`app/build.gradle.kts:32-34,60-73`; `app/src/main/res/navigation/nav_graph.xml:1-98`). No hay Hilt, Room ni Retrofit en las dependencias actuales; si aparecen en README son afirmaciones no aplicables al arbol real (`README.md:111-119`).
- Firebase Auth, Firestore, Storage, Realtime Database y Analytics estan declarados (`app/build.gradle.kts:46-53`), pero en el codigo inspeccionado el uso funcional visible es Auth, Firestore y Storage. No se encontro una implementacion de Realtime Database en `app/src/main/java`.
- `TFGApplication` conserva un `appContext` global y llama a `FirebaseApp.initializeApp` (`TFGApplication.kt:7-20`). `LocalizadorServicios` selecciona Firebase mediante `USAR_FIREBASE = true` y expone repositorios lazy globales (`service/LocalizadorServicios.kt:14-42`). Las implementaciones in-memory existen para Auth, Grupo y Tarea, pero no son la ruta efectiva por defecto (`data/inmemory/*`, `LocalizadorServicios.kt:32-42`).

**1. Mapa de paquetes y responsabilidades**

| Paquete | Responsabilidad observada | Limite real |
|---|---|---|
| `vista` | `MainActivity`, Fragments y adapters; binding, dialogos, filtros, navegacion y parte de reglas de negocio | Varios Fragments y adapters llaman repositorios globales, Firebase o crean repositorios concretos directamente (`FragmentTareas.kt:28-38,370-937`; `TareasHomeAdapter.kt:22-28,74-210`; `FragmentPareja.kt:364-575`). |
| `viewmodel` | Estado y operaciones de auth, grupos, tareas, avatar y dashboard | Conviven `StateFlow` y `LiveData`; algunos ViewModels reciben repositorio, otros instancian repositorios o consultan el service locator (`TareasViewModel.kt:11-55`; `VistaModeloPrincipal.kt:12-42`; `AvatarViewModel.kt:14-16`; `ParejaViewModel.kt:19-22`). |
| `repositorio` | Interfaces y repositorios Firestore de grupos, tareas, recompensas, disputas, notificaciones y datos locales de categorias | No es una frontera única: `RepositorioTareas` duplica a `data/firebase/TareaRepositorioFirebase`; recompensas, disputas y notificaciones no tienen interfaces equivalentes (`RepositorioRecompensas.kt:10-225`; `RepositorioDisputas.kt:10-47`; `RepositorioNotificaciones.kt:11-53`). |
| `data/firebase` | Implementaciones Firebase de Auth, Tarea y Avatar | Auth/Tarea participan en el service locator, Avatar Firebase no es usado por `AvatarViewModel` (`AuthRepositorioFirebase.kt:20-25`; `TareaRepositorioFirebase.kt:14`; `AvatarViewModel.kt:8,14-16`). |
| `data/inmemory` | Sustitutos en memoria para Auth, Grupo y Tarea | Seleccionables sólo cambiando una constante global; no cubren todos los repositorios. |
| `data/local` | Copia/eliminacion de avatares en `filesDir` y SharedPreferences | Usa FirebaseAuth directamente para resolver uid; no es persistencia remota (`AvatarRepositorioLocal.kt:9-17`). |
| `modelo` | Data classes serializables por Firestore: `Usuario`, `Tarea`, `Grupo`, `Disputa`, `Recompensa`, `Canje`, etc. | Los estados son Strings y no hay sealed states/value objects (`Tarea.kt:5-32`; `Disputa.kt:5-11`; `Canje.kt:5-14`). |
| `service` | Service locator, WorkManager/notificaciones y exportador ICS | El scheduler y el worker son infraestructura; el locator funciona como DI global (`LocalizadorServicios.kt:14-42`; `NotificationScheduler.kt:14-111`). |
| `util` | Constantes agregadas recientemente | `Constants.kt` centraliza porcentajes, puntos y tiempos usados por reglas de tarea/auth. |

**2. Direccion de dependencias y caminos directos**

El flujo pretendido es UI -> ViewModel -> repositorio -> Firebase, pero el flujo efectivo es mixto:

- UI -> `LocalizadorServicios`: `FragmentTareas`, `FragmentTareasPendientes`, `FragmentPgPrincipal`, `FragmentCalendario`, `FragmentPareja`, `FragmentRecompensas`, `MainActivity` y `TareasHomeAdapter` acceden directamente a `LocalizadorServicios` (por ejemplo `FragmentTareas.kt:370-937`, `FragmentTareasPendientes.kt:49-175`, `MainActivity.kt:287-302`).
- UI -> repositorio concreto: `FragmentTareas` crea `RepositorioDisputas` y notifica con `RepositorioNotificaciones` (`FragmentTareas.kt:47,96-98,378-381`); `FragmentRecompensas` crea `RepositorioRecompensas` y `RepositorioNotificaciones` (`FragmentRecompensas.kt:33-34`); `MainActivity` crea `RepositorioNotificaciones` (`MainActivity.kt:302`).
- UI -> Firebase SDK: `MainActivity` usa `FirebaseAuth` directamente para sesion y reset de contraseña (`MainActivity.kt:230-283,510-523`); `FragmentPareja` lee Firestore directamente para estadisticas y usuarios (`FragmentPareja.kt:364-371,549-570`); `TareasHomeAdapter` lee Firestore directamente (`TareasHomeAdapter.kt:74`).
- UI -> dominio/infraestructura: `FragmentTareas` construye `Disputa` y decide estados, puntos y asignaciones; `TareasHomeAdapter` decide acciones segun estado/rol (`TareasHomeAdapter.kt:158-267`).
- ViewModel -> global/concreto: `VistaModeloPrincipal` usa el locator (`VistaModeloPrincipal.kt:36-42`), `AvatarViewModel` instancia `AvatarRepositorioLocal` (`AvatarViewModel.kt:14-16`), `ParejaViewModel` instancia `RepositorioPareja` por defecto (`ParejaViewModel.kt:19-22`) y `TareasViewModel` instancia `RepositorioTareas` por defecto (`TareasViewModel.kt:11`).
- Repositorio -> locator: `TareaRepositorioFirebase` llama `LocalizadorServicios.repositorioAuth` para reservar, liberar y sumar puntos (`TareaRepositorioFirebase.kt:84-91,372-380`), cerrando una dependencia circular de infraestructura hacia el locator.

**3. Lifecycle, listeners y coroutines**

- La mayor parte de la UI usa `viewLifecycleOwner.lifecycleScope` y `repeatOnLifecycle`, especialmente login, tareas pendientes, calendario, perfil, recompensas y dashboard (ejemplos `FragmentTareasPendientes.kt:48-175`, `FragmentCalendario.kt:90-113`, `FragmentPerfil.kt:115-190`, `FragmentRecompensas.kt:76-121`). Esto es coherente con la convencion declarada en `.atl/skill-registry.md:22-28`, pero no implica una politica uniforme.
- `MainActivity` observa notificaciones en `lifecycleScope`, cancela el `Job` al destruirse y evita duplicar por uid (`MainActivity.kt:51-54,286-329,214-219`). Tambien observa usuarios del drawer en `repeatOnLifecycle(STARTED)` (`MainActivity.kt:430-447`).
- `ParejaViewModel` mantiene un `grupoObserverJob` en `viewModelScope`, cancela el anterior al cambiar grupo y en `onCleared` (`ParejaViewModel.kt:42-43,83-105,344-349`).
- Los listeners Firestore principales estan envueltos en `callbackFlow` con `awaitClose` y `remove`: Auth usuarios (`AuthRepositorioFirebase.kt:386-422`), grupos (`RepositorioPareja.kt:169-190`), tareas (`TareaRepositorioFirebase.kt:162-227`) y notificaciones (`RepositorioNotificaciones.kt:34-43`).
- `TareaRepositorioFirebase.observarTareas` abre hasta tres listeners simultaneos y combina resultados en un `MutableMap`; al cambiar el grupo, la suscripcion debe reiniciarse porque el `grupoId` se obtiene antes de abrir listeners (`TareaRepositorioFirebase.kt:162-212`). No hay sincronizacion explicita del mapa entre callbacks concurrentes.
- Hay coroutines de Activity/Fragment que ejecutan lecturas y actualizaciones directas, y adapters que reciben un `CoroutineScope` externo (`TareasHomeAdapter.kt:39,145-210`). El ownership del scope no es visible desde el adapter, por lo que el riesgo de usar un scope mas largo que la vista queda fuera de esa clase.
- Existen callbacks de compatibilidad en `ParejaViewModel` que lanzan una coroutine y esperan otro `StateFlow` (`ParejaViewModel.kt:138-145,168-176,202-209`), mezclando eventos callback y estado.

**4. Navigation graph versus MainActivity**

- El grafo inicia en `fragment_Presentacion`, conecta Presentacion -> Login -> Registro o PgPrincipal, y declara desde PgPrincipal las pantallas de calendario, recompensas, perfil, tareas, pareja y tareas pendientes (`nav_graph.xml:6-53`). Recompensas, Perfil, Registro, Pareja, Tareas, Calendario y TareasPendientes no declaran acciones de retorno propias; se navega por ids/globales.
- `MainActivity` configura NavHost, BottomNavigation, drawer y visibilidad de chrome (`MainActivity.kt:80-151`). Tambien implementa auto-login manual: consulta `FirebaseAuth.currentUser`, exige email verificado, carga el grupo mediante `ParejaViewModel`, agrega un listener temporal de destino y encadena Presentacion -> Login -> PgPrincipal (`MainActivity.kt:230-283`). Esto duplica/controla fuera del grafo el flujo de autenticacion.
- `MainActivity` maneja `openTaskId` desde intents y navega directamente a Tareas (`MainActivity.kt:130-133,202-207,221-227`); `NotificationScheduler` crea el PendingIntent a MainActivity (`NotificationScheduler.kt:74-80`).
- El back no sigue solamente el back stack: en destinos secundarios navega de forma explicita a PgPrincipal y en PgPrincipal requiere doble retroceso (`MainActivity.kt:153-194`). Logout hace `popUpTo(Presentacion, inclusive)` y navega a Login (`MainActivity.kt:571-595`).

**5. Persistencia local y avatares**

- `tfg_prefs` es el namespace principal para `grupoId` (`ParejaViewModel.kt:39-75`), rutas locales `avatar_path_<uid>` (`AvatarRepositorioLocal.kt:11,86`) y limpieza de grupo en logout (`MainActivity.kt:578-580`).
- `avatar_prefs` aparece separadamente en `FragmentPgPrincipal.kt:376-380` para buscar `avatar_<uid>`. Esa clave/namespace no coincide con `AvatarRepositorioLocal`, por lo que el dashboard puede no ver el mismo avatar que perfil/drawer.
- La ruta efectiva del perfil es local: `AvatarViewModel` usa `AvatarRepositorioLocal`, copia bytes a `filesDir/avatars/<uid>.<extension>` y guarda ruta absoluta en `tfg_prefs` (`AvatarViewModel.kt:14-16`; `AvatarRepositorioLocal.kt:14-34`).
- Existe una implementacion Firebase completa que sube a `avatares/<uid>/<uuid>.<extension>`, actualiza `usuarios/{uid}.avatarUrl` y puede borrar el campo (`AvatarRepositorioFirebase.kt:28-84,125-171`), pero no esta conectada al ViewModel inspeccionado. `Usuario` conserva `avatarUrl` (`Usuario.kt:17-19`), creando dos fuentes potenciales de verdad.
- `TFGApplication` no declara una base local; la persistencia local observada es SharedPreferences + archivos privados y el backend Firebase. `res/raw/categorias_sugeridas.json` es contenido estatico cargado por `CategoriasRepositorio` (`CategoriasRepositorio.kt:11-45`).

**6. Colecciones Firestore y repositorios duplicados**

| Coleccion/Storage | Frontera observada | Datos principales |
|---|---|---|
| `usuarios` | `AuthRepositorioFirebase`, `RepositorioPareja`, `TareaRepositorioFirebase`, `RepositorioTareas`, `RepositorioRecompensas`, UI directa | Auth/profile, `grupoId`, `puntos`, `puntosReservados`, `puntosRecompensa`, `rachaDias`, avatar URL. |
| `grupos` | `RepositorioPareja`, `TareaRepositorioFirebase`, `AuthRepositorioFirebase`, UI directa | nombre, miembros `uid -> rol`, emoji, fecha. |
| `invitaciones` | `RepositorioPareja` | codigo, creador, grupo, destinatario, estado, expiracion. |
| `tareas` | `TareaRepositorioFirebase`, `RepositorioTareas`, UI directa de estadisticas | asignacion, grupo, estado, puntos, confirmacion, reclamo, recurrencia, recordatorio. |
| `recompensas` | `RepositorioRecompensas` | predefinidas en memoria y personalizadas por grupo. |
| `canjes` | `RepositorioRecompensas` | coste, usuario, grupo, fecha, estado pendiente/aceptado/rechazado. |
| `disputas` | `RepositorioDisputas`, limpieza de cuenta en Auth | tarea, iniciador, estado, URLs de pruebas; Storage `disputas/<tareaId>/<uuid>.jpg`. |
| `notificaciones` | `RepositorioNotificaciones`, `MainActivity`, escritura indirecta de tareas/UI | destinatario, tipo, contenido, visto, fecha. |
| Storage `avatares/...` | `AvatarRepositorioFirebase` solamente | avatar remoto no conectado al ViewModel actual. |

- `TareaRepositorioFirebase` es la implementacion completa de `TareaRepositorio`, con observacion, validacion de autoasignacion, reserva/transaccion de puntos, reclamos, recurrencia y recordatorios (`TareaRepositorioFirebase.kt:14-561`).
- `RepositorioTareas` es una implementacion independiente, no implementa la interfaz y se usa como default de `TareasViewModel`; declara duplicacion de `marcarCompletada`/`confirmarTarea` en su propio comentario (`RepositorioTareas.kt:9-30`).
- Las reglas no son equivalentes: la implementacion completa normaliza personalizada a 200 puntos (`TareaRepositorioFirebase.kt:16-29`), aplica multiplicador/racha y minimo 1 de recompensa al confirmar (`TareaRepositorioFirebase.kt:466-493`); la simplificada usa 10% sin la misma normalizacion/racha (`RepositorioTareas.kt:97-113,164-179`).
- Ambas rutas modifican usuarios y tareas, por lo que una pantalla que use cada una puede producir invariantes financieras distintas.

**7. Estados e invariantes de dominio observables**

- **Tareas**: el modelo documenta `pendiente | completada | confirmada | reclamada`, mientras la implementacion usa tambien `pendiente_confirmacion` y `eliminada` (`Tarea.kt:15-16`; `TareaRepositorioFirebase.kt:397-400`; `FragmentTareas.kt:492`). Camino normal: crear pendiente; asignar; completar -> pendiente_confirmacion si requiere confirmacion; confirmar -> confirmada; reclamo/resolucion puede volver a pendiente o pasar a confirmada (`TareaRepositorioFirebase.kt:362-384,441-548`). La autoasignacion esta prohibida en la implementacion completa, no de forma uniforme en la simplificada (`TareaRepositorioFirebase.kt:31-41`; `RepositorioTareas.kt:65-77`).
- **Puntos**: `puntos` es saldo de actividad; `puntosReservados` bloquea el coste al crear/asignar; `puntosRecompensa` recibe normalmente 10% al confirmar y se gasta en recompensas (`Usuario.kt:13-16`; `AuthRepositorioFirebase.kt:440-474`; `RepositorioRecompensas.kt:91-113`). La transferencia depende de transacciones, pero hay rutas duplicadas y tambien operaciones no atomicas fuera de transaccion, por ejemplo resolver reclamo actualiza tarea y luego llama operaciones de puntos (`TareaRepositorioFirebase.kt:362-380`).
- **Recompensas/canjes**: las predefinidas son una lista fija en memoria; las personalizadas son documentos por `grupoId` (`RepositorioRecompensas.kt:16-42`). Canjear exige `puntosRecompensa >= coste`, descuenta y crea canje `pendiente` en una transaccion; rechazar devuelve el coste y marca rechazado (`RepositorioRecompensas.kt:84-146`). La consulta de pendientes filtra el propio uid en cliente (`RepositorioRecompensas.kt:178-207`).
- **Grupos**: grupo contiene miembros como mapa uid/rol; crear grupo usa batch para crear grupo y escribir `usuarios/{uid}.grupoId` (`RepositorioPareja.kt:35-57`). Aceptar invitacion usa transaccion y luego una escritura adicional de seguridad (`RepositorioPareja.kt:114-144`). Salir elimina el grupo si queda sin miembros y limpia `grupoId` (`RepositorioPareja.kt:230-254`). `ParejaViewModel` mantiene una copia local de `grupoId` y del grupo (`ParejaViewModel.kt:45-105`).
- **Disputas**: `abierta | en_progreso | cerrada` esta documentado en el modelo, pero el repositorio solo crea/lista por iniciador/sube evidencia; no hay flujo de moderacion/resolucion implementado en el repositorio (`Disputa.kt:5-11`; `RepositorioDisputas.kt:17-47`). `FragmentTareas` abre disputa con evidencia opcional, por lo que la evidencia puede ser una URL vacia si la subida falla o no se elige imagen (`FragmentTareas.kt:90-99`).

### Affected Areas

- **Nucleo de datos y reglas**: `data/firebase/TareaRepositorioFirebase.kt`, `repositorio/RepositorioTareas.kt`, `data/firebase/AuthRepositorioFirebase.kt`, `repositorio/RepositorioRecompensas.kt`.
- **Fronteras de presentacion**: `vista/FragmentTareas.kt`, `vista/TareasHomeAdapter.kt`, `vista/FragmentPareja.kt`, `vista/MainActivity.kt`, `vista/FragmentRecompensas.kt`, `vista/FragmentTareasPendientes.kt`, `vista/FragmentCalendario.kt`.
- **Estado y ciclo de vida**: `viewmodel/ParejaViewModel.kt`, `viewmodel/TareasViewModel.kt`, `viewmodel/VistaModeloPrincipal.kt`, `viewmodel/VistaModeloAuth.kt`, `viewmodel/AvatarViewModel.kt`.
- **Persistencia e identidad visual**: `data/local/AvatarRepositorioLocal.kt`, `data/firebase/AvatarRepositorioFirebase.kt`, `vista/FragmentPerfil.kt`, `vista/FragmentPgPrincipal.kt`, `vista/MainActivity.kt`.
- **Navegacion/entrada externa**: `res/navigation/nav_graph.xml`, `vista/MainActivity.kt`, `service/NotificationScheduler.kt`, `AndroidManifest.xml:21-45`.
- **Infraestructura operativa**: `service/NotificationScheduler.kt`, `service/NotificationWorker.kt`, `TFGApplication.kt`, `app/build.gradle.kts`, `google-services.json`.
- **Documentacion futura**: `README.md` describe MVVM/StateFlow y “arquitectura correcta” (`README.md:81-93,244-259`), pero no registra los caminos directos, duplicados, namespaces locales, estados adicionales ni limites de seguridad constatados.

### Approaches

- **Aproach factual adoptado**: inventariar desde el arbol y usar README/config solo como contexto. No se asume que existan reglas Firestore/Storage, indices, Cloud Functions, App Check, CI o cobertura porque no hay artefactos verificables en el repo.
- **Aproach de lectura de dependencias**: seguir referencias concretas de UI, ViewModels, repositorios y Firebase SDK; distinguir interfaces (`AuthRepositorio`, `TareaRepositorio`) de clases concretas.
- **Aproach de ciclo de vida**: registrar quien crea cada `Flow`, `Job`, listener o scope y si existe cancelacion observable (`awaitClose`, `onCleared`, `onDestroyView`/lifecycle scope).
- **Aproach de dominio**: reconstruir transiciones desde validaciones y escrituras reales, no desde los comentarios del README. Las cadenas de estado deben tratarse como contrato efectivo pendiente de formalizar.
- **Aproaches descartados para esta fase**: no refactorizar, no ejecutar Gradle/tests, no proponer Hilt/Room/Retrofit/Compose, no inferir permisos backend desde mensajes de error ni crear archivos fuera del cambio OpenSpec.

### Recommendation

Preparar una propuesta de guia arquitectonica que primero establezca un mapa de contratos y fuentes de verdad, sin cambiar aun el stack UI. La guia futura deberia:

- declarar el flujo permitido por capa y enumerar excepciones existentes (UI directa, locator, repositorios concretos);
- elegir una unica implementacion de tareas y documentar formalmente las transiciones, reserva/liberacion de puntos, multiplicador, racha y recurrencia;
- definir ownership de listeners y scopes, incluyendo el reinicio por `grupoId` y la combinacion de listeners de tareas;
- documentar el grafo y las navegaciones imperativas de `MainActivity`, intents `openTaskId`, logout y back personalizado;
- elegir una fuente de verdad para avatar y unificar `tfg_prefs`/`avatar_prefs` antes de describir comportamiento estable;
- mapear cada coleccion, campos, estados, transacciones y consumidores, dejando explícitos los indices/reglas/backend que deben confirmarse externamente;
- incorporar una matriz de verificacion manual y pruebas focalizadas futuras, sin afirmar cobertura actual.

### Risks

- **Alto: reglas de puntos divergentes.** Dos repositorios pueden confirmar la misma tarea con diferentes bonus, normalizacion, reserva o validaciones (`TareaRepositorioFirebase.kt:76-120,441-548`; `RepositorioTareas.kt:65-199`).
- **Alto: seguridad de acceso no demostrada.** Hay lecturas globales de grupos/usuarios/tareas y escrituras desde varios caminos; el repo no contiene reglas Firestore/Storage ni funciones de servidor. No se puede determinar si el backend restringe correctamente estas operaciones.
- **Alto: autoridad de dominio dispersa.** UI, adapter, ViewModel, repositorios y Auth modifican estados/puntos; las reglas no estan encapsuladas en un unico contrato.
- **Alto: consistencia de avatar.** La ruta activa guarda archivos locales, mientras existe un modelo/cargador remoto; el dashboard usa otro namespace (`avatar_prefs`) y el drawer usa `tfg_prefs`.
- **Medio: navegacion duplicada.** Auto-login, back, logout e intents se controlan en Activity además del grafo; cambios de destino pueden producir navegacion fuera de estado o back stack inesperado.
- **Medio: listeners y datos combinados.** Tareas combina hasta tres listeners en un mapa mutable; no elimina entradas obsoletas en `observarTareas` salvo en la variante por grupo y no hay politica visible ante cambios de grupo mientras la suscripcion vive (`TareaRepositorioFirebase.kt:184-212,215-227`).
- **Medio: limpieza de cuenta best-effort.** `AuthRepositorioFirebase` borra colecciones documento a documento y evidencia Storage, registrando y continuando ante fallos (`AuthRepositorioFirebase.kt:239-357`); no hay transaccion global ni evidencia de job servidor.
- **Medio: estados incompletos.** Disputas tienen estados de modelo pero no flujo de resolucion; tareas usan valores no representados en comentarios/modelo; canjes filtran autorizacion en cliente.
- **Desconocido: build/CI/seguridad operativa.** Solo se ven configuracion Gradle local, `google-services.json`, ProGuard y tests de plantilla. No hay `.github`; no se inspeccionaron servicios Firebase remotos, reglas, indices, App Check, Crashlytics, secretos de CI o politica de backups.

### Ready for Proposal

Si. El alcance del proposal puede ser una guia arquitectonica factual y un plan incremental de convergencia, manteniendo XML/Fragments/ViewBinding y el modulo unico. Debe tratar como prerequisitos de confirmacion externa las reglas/indices Firebase, el flujo real de despliegue y la prioridad entre avatar local/remoto. Este artefacto es el unico archivo creado en esta exploracion; no se modifico codigo de aplicacion ni se ejecutaron builds/tests.
