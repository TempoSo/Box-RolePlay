# 📓 Registro de cambios — Box RolePlay

> **Documento vivo para continuidad de memoria** entre sesiones de trabajo (humano, Claude, OpenCode u otros agentes).
>
> **Regla para agentes IA (obligatoria):** tras CUALQUIER cambio de código o documentación, añade una **entrada nueva arriba** en la sección "Registro" con: fecha, qué se cambió, **archivos exactos** (ruta completa) y motivo. No borres ni reescribas entradas antiguas. Si detectas un bug y su causa, documéntalo aquí también.

**Documentos vivos hermanos:**
- [`PRUEBAS.md`](PRUEBAS.md) — checklist original completa de pruebas por fase.
- [`PLAN_PRUEBAS.md`](PLAN_PRUEBAS.md) — plan priorizado con resultados de pruebas en dispositivo.
- [`BUGS.md`](BUGS.md) — inventario de bugs priorizados (síntomas, stacks, repros, fixes propuestos y archivos afectados).
- [`MEMORIA.md`](MEMORIA.md) — análisis de diseño: memoria de Layla vs la de Box, con modificaciones propuestas.

---

## 🧭 Contexto del proyecto (léeme antes de tocar nada)

- **Qué es:** fork de [Box](https://github.com/jegly/Box) de jegly (que deriva de Google AI Edge Gallery) reorientado a **roleplay con IA 100 % local** (personajes, escenas multipersonaje, memoria a largo plazo, TTS por personaje).
- **Base del fork: Box 1.0.1.** El README upstream que venía en el repo era el de la 3.5.7: sus funciones posteriores (Box Assist, Nano Hub, upscale, música, Bonsai/FLUX, MCP…) **NO están en este código**.
- **Rama activa:** `fase1-rendimiento`.
- **Nombre de la app:** "Box RolePlay" (`Android/src/app/src/main/res/values/strings.xml` → `app_name`; paquete raíz `com.box.gallery` con código bajo `com.google.ai.edge.gallery`).
- **Convenciones:** commits y documentación en **español**, sin tildes en muchos commits históricos. Los submódulos (`llama.cpp`, `stable-diffusion.cpp`, `whisper.cpp`) son obligatorios al clonar.
- **Historia previa:** el trabajo roleplay empezó con parches "roleplay fase 2→8" (personas, contexto, muestreo, motor, memoria, lorebook, biblioteca, voz base, especulativa) y continuó con la estructura actual de fases 1–6 y A–E2 (ver git log).

## 🗺️ Mapa rápido de código

| Área | Ruta (desde `Android/src/app/src/main/java/com/google/ai/edge/gallery/`) |
|---|---|
| Inicio, apps fijadas, Configuración, Rendimiento, Inferencia | `ui/home/Box*Screen.kt` |
| Personajes, escenas, motores guardados, diálogo Rendimiento | `ui/roleplay/` |
| Apps independientes (voz, visión, benchmark…) | `ui/apps/` |
| Memoria a largo plazo | `ui/memory/` + `data/local/` (DAO/entidades) |
| Navegación (rutas `box_apps`, `box_app_bench`…) | `ui/navigation/GalleryNavGraph.kt` |
| Motor GGUF (llama.cpp) | `engine/LlamaCppEngine.kt` |
| Puente modelos GGUF (singleton `object`) | `runtime/LlamaCppModelHelper.kt` |
| TTS Pocket (sherpa-onnx) | `tts/` |
| Benchmark LiteRT (upstream) | `ui/benchmark/` |
| Escritura nativa | `Android/src/llama.cpp/` (submódulo) |

---

## 📝 Registro

### 2026-11-17 — Triaje del feedback: `.wab`, espaciado de 4.1, corte de texto con GGUF y crashes de los reportes

**Qué y por qué:** el humano confirma que 3.1 (descarga HF) y 4.2 (cambio de GGUF/LiteRT) funcionan, y que 4.1 funciona aunque quiere mejorar el espaciado; aparecen tres síntomas nuevos y dos crashes en los reportes exportados. **No se tocó código de producción**: solo se documenta lo verificado en código y logs. Altas: BUG-027 (no existe soporte de `.wab`: `TtsFiles.importVoice` exige WAV vía `WavReader.parse` y `TtsSettingsDialog` muestra "Ese archivo no es un WAV legible"), BUG-028 (la etiqueta de holgura de `HfBrowserScreen` es un `Text` hermano de los metadatos con `weight(1f)`, por eso aparece pegada o lejos según la longitud), BUG-029 (la respuesta **escrita** con GGUF se corta; vías verificadas: stop tokens de `LlamaCppEngine.generateResponse`, recorte `isFinal` descartado en el chat porque el helper usa deltas, `ThinkingSplitter` y tope `maxResponse`) y BUG-030 (`SIGABRT` de Scudo en `LLMInference::~LLMInference()` ← `SmolLM.close` ← `LlamaCppEngine.unloadModel` a las 15:23; presunto doble cierre con `resetConversation` o cierre con el hilo nativo vivo porque el `join` tiene timeout). Se añaden notas de verificación a BUG-013/016/020/022 y de reaparición a BUG-026 (callback JNI a las 15:27 y 15:30, con la build instalada aún por confirmar). Aclaraciones del humano tras el triaje: en 4.1 quiere **separación visual** (no orden alfabético) y con GGUF se corta el **texto** (no la voz). Siguen pendientes: qué contiene un `.wab` y si los 2 crashes nativos que iba a subir son estos u otros. Las capturas adjuntas no son visibles para este agente.

**Archivos:**
- `analisis/BUGS.md` — BUG-027, BUG-028, BUG-029 y BUG-030 nuevos; notas de verificación/reaparición en BUG-013, BUG-016, BUG-020, BUG-022 y BUG-026.
- `analisis/CAMBIOS.md` — esta entrada.

**Próximo paso (sin ejecutar sin confirmación):** arreglar los bugs en tandas separadas con commit local por bug; no hacer push. Para BUG-027 hace falta una muestra `.wab` o su formato; para BUG-029 hace falta una respuesta cortada concreta y el nombre del modelo. BUG-028 ya está acotado (separación visual) y BUG-030 tiene el stack completo.

### 2026-11-17 — 2.5 VERIFICADO en ambos motores (BUG-002 cerrado); BUG-026: crash del TTS por callback JNI

**Qué y por qué:** el humano verifica que "Absorber clústeres" ya funciona **sin crash ni timeout en GGUF y LiteRT** (BUG-002 cerrado tras 5 rondas; BUGS.md y PLAN_PRUEBAS.md actualizados). En los logs (15:20) quedan dos **SIGABRT nuevos (13:36:52 y 13:37:10)**, ambos del **TTS**, no de memoria: al generar voz, el JNI de sherpa-onnx busca con `GetMethodID` un `invoke` de **instancia** con firma `([F)Ljava/lang/Integer;` (retorn boxed), pero el callback se pasaba como lambda Kotlin `(FloatArray) -> Int`, que D8 desazaúca en una clase sintética con `invoke` **estático** → `NoSuchMethodError` pendiente → SIGABRT en el siguiente `NewFloatArray` (stack: `OfflineTts.generateWithConfigImpl` ← `SherpaPocketEngine.generate` ← `TtsManager.launchJob$lambda$2`). Fix (BUG-026): clase normal `ChunkCallback` que implementa `Function1<FloatArray, Int?>`; verificado con `javap` que emite `public java.lang.Integer invoke(float[])` — instancia y firma exacta. 177 tests en verde, `:app:assembleDebug` OK. Pendiente de re-prueba de voz en el dispositivo.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/tts/SherpaPocketEngine.kt` — callback de muestras como clase `ChunkCallback` en vez de lambda (BUG-026).
- `analisis/BUGS.md` — BUG-002 cerrado (verificado), BUG-026 nuevo con stack y causa raíz.
- `analisis/PLAN_PRUEBAS.md` — 2.5 marcado OK en GGUF y LiteRT.

### 2026-11-17 — BUG-002 (5.ª ronda): watchdog por tope global + atasco + enfriamiento; BUG-025: crash al limpiar GGUF vía AgentChat

**Qué y por qué:** re-prueba 2.5 con la 4.ª ronda: el límite fue **623 s** y se agotó. Los números encajan con **el tope de generación de la 4.ª ronda trabajando correctamente** (400 tok @ ~3 t/s ≈ 135 s) y un **prefill a ~2.4 t/s** (~1167 tokens ≈ 488 s): la térmica del SoC ralentiza el prefill a menos de la mitad en carga sostenida (por la mañana el mismo modelo iba a 5.8 t/s). Conclusión: estimar tiempos no sirve en este dispositivo (fallaron 180 s, 379 s y 623 s por la misma razón). Cambios en la absorción:
1. **Tope global por tanda de 20 min** (`MemoryAbsorber.TANDA_TIMEOUT_MS`): anti-cuelgue, no presupuesto.
2. **Detector de atasco** (`tandaAbortReason` + `STALL_TIMEOUT_MS` = 120 s): solo tras el primer token (los tokens llegan cada ~0.3 s; un silencio de 2 min es un cuelgue). En prefill no hay señal de progreso y manda el tope global. El progreso se lee de un `AtomicLong` (los partial llegan en el hilo del motor).
3. **Pausa de enfriamiento de 30 s** (`COOLDOWN_MS`) entre tandas, con log.
4. Tests: 6 en `MemoryAbsorberTest.kt` reescritos (prefill no dispara atasco, stall real, bordes). 177 tests en verde; `:app:assembleDebug` OK; dex verificado (cadenas nuevas dentro del APK).

Además, **BUG-025 (crash real hoy a las 12:44)**: `ClassCastException: LlamaCppEngine cannot be cast to LlmModelInstance` en `LlmChatModelHelper.cleanUp` vía `AgentChatTask.cleanUpModelFn` ← `cleanupModel` ← `setAppInForeground`. Causa: la limpieza de AgentChat estaba **fija a LiteRT** aunque el modelo cargado fuera GGUF; el cast duro explotaba al limpiar (mata la app en plena prueba; venía del 16/11, línea 359 del APK viejo). Fix: (1) `AgentChatTask.cleanUpModelFn` despacha vía `model.runtimeHelper.cleanUp`; (2) `LlmChatModelHelper.cleanUp` ya no hace cast duro (instancia no LiteRT: log, `instance = null`, `onDone`, sin crash).

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/memory/MemoryAbsorber.kt` — vigilancia por fases reescrita (tope global + atasco + pausa de enfriamiento), constantes y tests de decisión.
- `Android/src/app/src/test/java/com/google/ai/edge/gallery/ui/memory/MemoryAbsorberTest.kt` — reescrito a `tandaAbortReason`.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat/AgentChatTaskModule.kt` — limpieza despachada por runtime (BUG-025).
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatModelHelper.kt` — cast duro eliminado en `cleanUp` (BUG-025).
- `analisis/BUGS.md` — BUG-002 (5.ª ronda) y BUG-025 nuevos.

**Próxima prueba:** 2.5 GGUF. Esperado: tanda 1 ~6-9 min con térmica (prefill lento + resumen ≤400 tok), pausa 30 s, siguientes tandas ~4-6 min. Mensajes de error posibles ahora: atasco real (raro) o tope de 20 min (cuelgue). El log `MemoryAbsorber` muestra por tanda el tope y las pausas; `AGLlamaPerf` las velocidades reales.

### 2026-11-17 — BUG-020: etiquetas de holgura del navegador HF coloreadas por nivel

**Qué y por qué:** "cabe bien", "justo" y "demasiado grande" salían todas en el mismo gris (`onSurfaceVariant`), así que la estimación de RAM del navegador HF no se distinguía de un vistazo. La etiqueta se muestra ahora aparte con color: verde/terciario para GOOD, ámbar para TIGHT y `error` para TOO_BIG. No se tocan los umbrales de `HfApi.fit()` (45 % / 65 % de la RAM).

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/modelmanager/HfBrowserScreen.kt` — etiqueta de holgura separada y coloreada según `HfFit` (import de `Color`).
- `analisis/BUGS.md`, `analisis/CAMBIOS.md` — marca y registro.

**Próxima prueba:** 4.1b. En el navegador HF, comprobar que un modelo pequeño sale en verde, uno justo en ámbar y uno grande en rojo (los umbrales siguen siendo los mismos).

### 2026-11-17 — BUG-022: "Exportar logs" incluye los crashes nativos del buffer del sistema

**Qué y por qué:** el reporte solo traía los logs de la sesión reabierta porque `installCrashRecorder` captura excepciones Java, pero los crashes del proyecto son señales nativas (SIGSEGV en `libllama.so` / `liblitertlm_jni.so`) que matan el proceso sin pasar por ese handler. Sin tocar JNI, `buildReport` lee ahora `logcat -b crash -d`, filtra por el paquete `com.box.gallery` y adjunta las líneas vecinas (el backtrace de tombstone no repite el nombre del paquete). Ese buffer del sistema sobrevive al reinicio del proceso. `last_crash.txt` se mantiene para las excepciones Java.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/LogExporter.kt` — nuevo `nativeCrashLog(context)` y sección "Crash nativo (logcat -b crash)" en `buildReport`.
- `analisis/BUGS.md`, `analisis/CAMBIOS.md` — marca y registro.

**Próxima prueba:** 5.5b. Provocar un crash (p. ej. rotar mientras genera con BUG-021 sin arreglar), reabrir la app y "Exportar logs": el .txt debe incluir la tombstone con `>>> com.box.gallery <<<` y las líneas de `libllama.so`/`liblitertlm_jni.so`. Si el dispositivo no permite leer el buffer `crash`, el reporte indicará el motivo en lugar de fallar.

### 2026-11-17 — BUG-016: el primer mensaje espera al modelo recién seleccionado (sin "Session re-initialized")

**Qué y por qué:** al cambiar de modelo GGUF/LiteRT el envío podía llegar antes de que acabara la inicialización: `model.instance` seguía apuntando al motor anterior mientras se liberaba/cargaba el nuevo, y `runInference` (llama.cpp) fallaba en seco con "llama.cpp engine not initialized", lo que disparaba `handleError` → cleanup + initialize + aviso "Session re-initialized". Se arreglan las dos vías: (A) `LlmChatViewModel.generateResponse` deja el mensaje encolado con el indicador "⏳ cargando modelo…" y consulta `runtimeHelper.isModelReady(model)` (que en GGUF exige motor cargado **y** instancia actual) hasta 180 s antes de armar el prompt e inferir; (B) `LlamaCppModelHelper.runInference` reintenta con un sleep acotado de 60 s en vez de fallar al primer intento. Si se agota el tiempo se muestra un error específico que `handleError` **no** trata como fallo del motor (no reinicia la sesión). `runAgain` ya no espera solo a `model.instance`.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/runtime/LlmModelHelper.kt` — nuevo `isModelReady(model)` con implementación por defecto (`model.instance != null`).
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/runtime/LlamaCppModelHelper.kt` — override `isModelReady` (motor del mapa cargado + instancia vigente) y espera acotada (60 s) al inicio de `runInference`.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatViewModel.kt` — espera encolada con estado "cargando modelo…" y timeout (180 s), salida anticipada en `handleError` para ese caso, `runAgain` sin espera obsoleta.
- `analisis/BUGS.md`, `analisis/CAMBIOS.md` — marca y registro.

**Próxima prueba:** 4.2b. Cambiar varias veces entre GGUF y LiteRT y enviar el primer mensaje en cuanto se seleccione: debe responder sin "engine not initialized" ni "Session re-initialized"; el indicador "cargando modelo…" solo aparece durante la carga.

### 2026-11-17 — BUG-013: el diálogo de voz ve el árbol HF completo y usa un repo compatible con sherpa-onnx

**Qué y por qué:** "Descargar de Hugging Face" siempre decía "No encontré ninguna carpeta…" porque el diálogo de voz pasaba por `HfClient.files()` → `HfApi.parseFiles()`, que solo deja pasar `.gguf`/`.litertlm` (filtro del navegador LLM) y descartaba todos los `.onnx`/`.json` del TTS. Se añade `HfApi.parseTreeFiles()` (mismo parseo, sin filtro de extensión) y `HfClient.treeFiles()`, y el diálogo usa esta última; `parseFiles` queda intacto para el navegador. Además el repo por defecto pasa de `KevinAHM/pocket-tts-onnx` a `haex-space/pocket-tts-sherpa-onnx`: los bundles v2 de KevinAHM exigen insertar `bos_before_voice.npy` y sherpa-onnx 2.0.1 no lo hace (ruido o audio de 0,2 s). Se retira el aviso de licencia específico de KevinAHM y se añaden tests del parseo.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/HfApi.kt` — `parseTreeFiles()` y helper `parseTree()` compartido con `parseFiles()`.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/HfClient.kt` — `treeFiles(repoId)` (árbol completo).
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/roleplay/TtsModelDownloadDialog.kt` — usa `HfClient.treeFiles`, `DEFAULT_REPO = haex-space/pocket-tts-sherpa-onnx`, texto de licencia genérico.
- `Android/src/app/src/test/java/com/google/ai/edge/gallery/data/characters/HfApiTest.kt` — test del árbol sin filtro y de que `parseFiles` sigue filtrando.
- `analisis/BUGS.md`, `analisis/CAMBIOS.md` — marca y registro.

**Próxima prueba:** 3.1 (listar carpetas de `haex-space/pocket-tts-sherpa-onnx`, descargar con reanudación) y la verificación del commit `2cec7db` con `TTSBard/kyutai-en-clone-onnx`: ahora debe listar "Principal" y bajar int8/normal ignorando q4.

### 2026-11-17 — BUG-002 (4.ª ronda): tope de generación por tanda (el 379 s lo consumió un resumen desbocado)

**Qué y por qué:** re-prueba 2.5 con la 3.ª ronda: el crash sigue ausente, pero la tanda 1 agotó su límite de **379 s**. Ese límite corresponde a la fórmula con las stats del turno previo (6.5 t/s prefill / 4.33 t/s gen) y encaja con: **~240 s de prefill real + ~140 s de generación sin tope**. El nativo corre la absorción con `_maxResponse = -1` (sin tope), así que un resumen desbocado (el modelo no emite EOS y sigue) puede consumir el watchdog entero solo en generación. Cambios:

1. **Tope de generación por tanda** (`MAX_RESPONSE_TOKENS = 400`): `MemoryAbsorber` envía `extraContext["max_response_tokens"]` **solo para llama.cpp** (comparación de identidad con `LlamaCppModelHelper`, para no arriesgar un kwarg desconocido en litertlm); `LlamaCppModelHelper.runInference` lo aplica vía `LlamaCppEngine.setMaxResponse` y lo **restaura** al tope del chat (`InferenceParams.maxResponse`, normalmente -1) en complete/cancel/error. El nativo corta limpio a los 400 tokens generados (`completionLoop` → EOG), y `MemoryAbsorbPrompt.clean` ya trunca el texto a 1200 car.
2. **Timeout recalibrado** (`tandaTimeoutMs`): ~3.0 car./token (antes 3.5), margen ×2 (antes ×1.5, el dispositivo se ralentiza por térmica en tandas largas), presupuesto de generación = 400 tokens (el tope real). Suelo **8 min**, techo 30 min. Con las stats del dispositivo: ~10 min por tanda.
3. Observación (no acción): tombstone viejo de las 07:18 en `liblitertlm_jni.so` (hilo `callback_thread`, null deref, 6 frames) en LiteRT — crash nativo del runtime de LiteRT, a diagnosticar aparte si reaparece.
4. Tests actualizados a la fórmula nueva (6 en verde). Suite completa: 176 tests, 0 fallos. `:app:assembleDebug` OK.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/memory/MemoryAbsorber.kt` — `MAX_RESPONSE_TOKENS`, `extraContext` condicionado a llama.cpp, fórmula del timeout recalibrada, KDoc.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/runtime/LlamaCppModelHelper.kt` — lectura de `max_response_tokens` en `runInference`, aplicar tope y restaurarlo en los tres finales de la generación.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/engine/LlamaCppEngine.kt` — `setMaxResponse` / `restoreMaxResponse`.
- `Android/src/app/src/test/java/com/google/ai/edge/gallery/ui/memory/MemoryAbsorberTest.kt` — tests de la fórmula nueva.

**Próxima prueba:** 2.5 GGUF. Esperado: tanda 1 ~4-5 min (prefill ~240 s + resumen ≤400 tok), tandas siguientes menos. Si alguna tanda se corta a 400 tokens, el resumen queda truncado pero útil (el texto se limpia a 1200 car.). Solo debería volver a salir "tardó demasiado" si hay un cuelgue real (o térmica extrema; el mensaje indica tanda y límite).

### 2026-11-17 — BUG-002 (3.ª ronda): timeout dinámico por tanda + reutilización de KV entre tandas (Gemma 4)

**Qué y por qué:** el crash de 2.5 GGUF desapareció con la 2.ª ronda, pero el watchdog fijo de **180 s** por tanda saltaba siempre: el log del dispositivo muestra prefill a **6.5 t/s** y una tanda de ~1400 tokens (preámbulo archivista + notas) necesita ~215 s **solo de prefill**, más la generación — "El modelo tardó demasiado" sin que hubiera colga real. Además, el `resetChat()` nativo entre tandas hacía `llama_memory_clear`: la KV se tiraba en cada tanda y el preámbulo archivista (idéntico en todas) se re-prefilleaba entero, multiplicando el tiempo. Cambios:

1. **Timeout dinámico por tanda** (`MemoryAbsorber.tandaTimeoutMs`, en el companion, con tests): estima el prefill con la velocidad real medida del dispositivo (`LastEnginePerf.stats`: tok/s de prompt y de generación de los últimos turnos, incluidas las tandas ya hechas) ×1.5 de margen + ~300 tokens de resumen a la velocidad de generación medida ×1.5. Sin estadísticas usa supuestos conservadores (5 t/s / 3 t/s). Suelo 4 min, techo 30 min. El mensaje de error ahora indica la tanda y el límite aplicado. Log `MemoryAbsorber` por tanda con el watchdog elegido.
2. **Límite ligero entre tandas** (solo Kotlin, sin tocar nativo): nueva vía `auxTandaBoundary` en `LlmModelHelper` (default = `prepareForAuxInference`, mismo comportamiento en LiteRT) y en `LlamaCppModelHelper` (delega en `LlamaCppEngine.prepareForAuxTanda`). En Gemma 4 (ruta de marcadores, donde `GgufChatTemplate` construye el prompt en Kotlin) ese reset **no tira la KV**: limpia `lastHistory`/`lastSystemPrompt` y resetea el sampler (`setSampler` recrea la cadena y olvida penalizaciones); el nativo ya reutiliza el prefijo común (`LLMInference::_prepareDecode` + `ctxwin::reusablePrefix`) y trunca los tokens generados con `seq_rm`. Resultado: el preámbulo se prefill UNA vez y cada tanda solo procesa sus notas. En modelos GGUF no Gemma 4 sigue `resetChat` completo (su prompt se forma con el historial nativo y debe seguir independiente). El reset a fondo (`prepareForAuxInference` → `seedConversation("")`) queda **solo antes de la primera tanda**.
3. Tests: 6 nuevos en `MemoryAbsorberTest` (conservadores sin stats, dispositivo lento >180 s, suelo 4 min, stats degeneradas NaN, techo 30 min). Suite completa: 176 tests en verde. `:app:assembleDebug` OK.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/memory/MemoryAbsorber.kt` — timeout dinámico por tanda (`tandaTimeoutMs`), reset a fondo solo en la primera tanda + `auxTandaBoundary` entre tandas, logs y mensaje de error con tanda/límite.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/runtime/LlmModelHelper.kt` — nueva vía `auxTandaBoundary(model)` (default = `prepareForAuxInference`).
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/runtime/LlamaCppModelHelper.kt` — override `auxTandaBoundary` → `LlamaCppEngine.prepareForAuxTanda`.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/engine/LlamaCppEngine.kt` — `prepareForAuxTanda`: reset ligero que conserva la KV en Gemma 4; `resetChat` completo en el resto.
- `Android/src/app/src/test/java/com/google/ai/edge/gallery/ui/memory/MemoryAbsorberTest.kt` (nuevo) — 6 tests de `tandaTimeoutMs`.

**Próxima prueba:** re-probar 2.5 "Absorber clústeres" en GGUF: ya no debe salir "El modelo tardó demasiado"; la primera tanda puede tardar ~4-5 min (prefill completo) y las siguientes menos (KV reutilizada). El log `AGLlamaPerf` debe mostrar `reusablePrefix` funcionando (tandas 2+ con prefill corto) y `MemoryAbsorber` el watchdog por tanda.

### 2026-11-17 — BUG-002 (2.ª ronda): sin close/reload por tanda + prompt archivista estilo Layla

**Qué y por qué:** 2.5 seguía crasheando en GGUF (`SIGSEGV` en `llama_vocab::impl::tokenize` ← `common_tokenize` ← `LLMInference::startCompletion`, ahora por el branch de marcadores y **sin** pasar por `_applyContextWindow`). Descartado el contenido como detonante (las notas automáticas ya van sin etiquetas por `AutoMemory.clean` y la tanda es más corta que un turno normal), la única pieza exclusiva del flujo de absorción era la secuencia **reset→inferencia por tanda**: `resetConversation` en GGUF cierra y reabre los pesos de forma **asíncrona** y el Absorber solo esperaba 300 ms; además `isModelLoaded` seguía a `true` durante el cierre/recarga, así que la tanda podía arrancar contra un motor a medio cerrar (ventana de use-after-free → el mismo tipo de SIGSEGV en el tokenizador que BUG-001). Cambios:

1. El reset por tanda ya no cierra ni recarga el modelo: nueva vía ligera `prepareForAuxInference()` en `LlmModelHelper` (por defecto delega en `resetConversation`, que es lo que ya hacía LiteRT); en llama.cpp hace `seedConversation("", vacío)` → `resetChat()` nativo (borra historial + KV sin tocar los pesos, síncrono).
2. Defensa en el motor: `resetConversation` apaga `isModelLoaded` mientras dura el close/reload (ningún `generateResponse` entra durante la recarga). `seedConversation` avisa en el log si se ignora por modelo ocupado.
3. Sanitizado de notas en el prompt (defensa por contenido: notas manuales pueden traer marcadores de turno pegados del chat).
4. Prompt archivista estilo Layla para la absorción: contexto del personaje por campos (`{{char}}`, `{{user}}`, persona, descripción, personalidad; máx. 600 caracteres por campo).
5. Logs de diagnóstico (`AGLlamaPerf`): tamaño del prompt, plantilla activa, system e historial justo antes de tokenizar, y tanda/modelo en el Absorber. Si volviera a crashear, el logcat dice el estado exacto.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/runtime/LlmModelHelper.kt` — nuevo método `prepareForAuxInference(model)` con implementación por defecto (`resetConversation`).
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/runtime/LlamaCppModelHelper.kt` — override de `prepareForAuxInference`: `seedConversation("", emptyList())` sin close/reload.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/memory/MemoryAbsorber.kt` — usa `prepareForAuxInference`; fuera el `delay(300)`; logs por tanda; comentario actualizado.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/engine/LlamaCppEngine.kt` — `resetConversation` marca `isModelLoaded=false` durante el close/reload; `seedConversation` loggea si se ignora; `generateResponse` loggea estado del prompt (`AGLlamaPerf`).
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/RawMemory.kt` — `MemoryAbsorbPrompt.build` con contexto del personaje (persona/description/personality) y sanitizado de notas (`sanitize`, quita etiquetas `<...>`).
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/memory/MemoryScreen.kt` — `MemoryViewModel.absorb` pasa persona + descripción + personalidad del personaje al prompt.
- `Android/src/app/src/test/java/com/google/ai/edge/gallery/data/characters/RawMemoryTest.kt` — 4 tests nuevos (contexto estilo Layla, campos vacíos omitidos, sanitizado de etiquetas).
- `analisis/BUGS.md`, `analisis/CAMBIOS.md` — marca y registro.

**Re-prueba:** 2.5 (Absorber con GGUF y varias notas). 2.4/2.1b/5.4b no deberían verse afectados (no se tocó la ruta de chat; el único cambio en la ruta normal es el log).

---

### 2026-11-17 — BUG-001 + BUG-015 (coordinados): plantilla de turnos de Gemma 4 en Kotlin y cierre del crash nativo GGUF

**Qué y por qué:** crash nativo (`SIGSEGV` en `llama_vocab::impl::tokenize` ← `common_tokenize` ← `LLMInference::_applyContextWindow()`) al deslizar la ventana en escena/chat largo y al absorber clústeres, y prompt GGUF sin plantilla de turnos (degradación de Gemma frente a Layla). Se arreglan juntos porque comparten zona y mecanismo: el prompt de Gemma 4 se construye ahora **en Kotlin** con la plantilla canónica y el nativo lo tokeniza tal cual (`startCompletion` detecta los marcadores y se salta el formateo nativo y `_applyContextWindow()` — la ruta exacta del crash). El recorte del historial lo sigue haciendo la app (ContextWindowPlanner) antes de sembrar el turno. Vía Kotlin, como pedía el plan; no se tocó el JNI ni el submódulo llama.cpp.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/engine/GgufChatTemplate.kt` — **nuevo**: plantilla de Gemma 4 (`<|turn>…<turn|>`, turno de usuario vacío si el saludo abre el historial, `<|think|>` con thinking) y detección de familia por nombre.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/engine/LlamaCppEngine.kt` — `useGemma4Template`/`thinkingEnabled`/`lastHistory`; `setGemma4Template()`; `setThinking` guarda el flag; `seedConversation` guarda el historial sin vacíos (BUG-001: no tokenizar cadenas vacías); `generateResponse` construye el prompt con `GgufChatTemplate.build` si la plantilla está activa; `resetConversation` limpia `lastHistory`.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/runtime/LlamaCppModelHelper.kt` — `initialize` activa la plantilla si el modelo es Gemma 4; `runInference` rechaza mensaje vacío sin imágenes (BUG-001).
- `Android/src/app/src/test/java/com/google/ai/edge/gallery/engine/GgufChatTemplateTest.kt` — **nuevo**: 7 tests del formato y la detección.
- `analisis/BUGS.md`, `analisis/CAMBIOS.md` — marcas y registro.

**Nota:** los GGUF que no son Gemma 4 siguen la ruta nativa de siempre (allí `_applyContextWindow` sigue activa; el planner de la app ya recorta antes de sembrar).

---

### 2026-11-17 — BUG-002: "Absorber clústeres" por tandas con watchdog por tanda

**Qué y por qué:** con GGUF el prompt de absorción desbordaba el contexto y deslizaba la ventana (crash, mismo stack que BUG-001); con LiteRT agotaba el watchdog ("El modelo tardó demasiado"). La absorción va ahora por tandas pequeñas y cada tanda genera su propio recuerdo.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/RawMemory.kt` — `MemoryAbsorbPrompt.tandas()`: reparte notas cronológicas en tandas (máx. 3500 caracteres, máx. 8 tandas; se descartan las más viejas).
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/memory/MemoryAbsorber.kt` — `run()` acepta una lista de prompts: resetea la conversación por tanda, watchdog **por tanda** (180 s), progreso "Absorbiendo tanda X de Y…" y devuelve los textos por tanda.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/memory/MemoryScreen.kt` — `MemoryViewModel.absorb` usa tandas y guarda un recuerdo por tanda; solo borra las notas de las tandas con resumen útil; mensaje final con conteos.
- `Android/src/app/src/test/java/com/google/ai/edge/gallery/data/characters/RawMemoryTest.kt` — 3 tests nuevos de `tandas()`.
- `analisis/BUGS.md`, `analisis/CAMBIOS.md` — marca y registro.

---

### 2026-11-17 — BUG-021: rotación sin recarga del modelo y cierre seguro de los dos motores

**Qué y por qué:** la rotación destruía la Activity (manifiesto solo con `configChanges="uiMode"`), recargaba el modelo en ambos motores y, si estaba generando, acababa en `SIGSEGV` nativo. Ahora la rotación solo recompone la vista, y al liberar el motor se cancela la generación y se espera al hilo nativo antes de cerrarlo.

**Archivos:**
- `Android/src/app/src/main/AndroidManifest.xml` — `android:configChanges="uiMode|orientation|screenSize"` en `MainActivity`.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/engine/LlamaCppEngine.kt` — `unloadModel` y `resetConversation` cancelan la generación y **esperan al hilo** (unión acotada a 60 s, en un hilo aparte, sin bloquear al llamador; un prefill en marcha puede tardar más que un token) antes de `instance.close()`, sobre la instancia capturada.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatModelHelper.kt` — registro de generación pendiente por modelo (`pendingGenerations`); `cleanUp` cancela (`cancelProcess`), espera al callback de fin (máx. 5 s) y solo entonces cierra conversación y motor.
- `analisis/BUGS.md`, `analisis/CAMBIOS.md` — marca y registro.

**Nota:** el motor GGUF vive en el singleton `LlamaCppModelHelper` (proceso) y el de LiteRT en los `Model` del ViewModel de gestión, fuera del ciclo de la Activity — verificado, no hizo falta mover nada.

---

### 2026-11-17 — Subida del trabajo pendiente a `origin/fase1-rendimiento` (2 commits)

**Qué y por qué:** el usuario pidió subir a GitHub todo el trabajo sin commitear de las últimas sesiones para que Claude trabaje desde ahí. Se agrupó en 2 commits:
1. **Código:** "Benchmark: prueba rápida del motor GGUF en la app Benchmark con diagnóstico de estado" — `ui/apps/QuickBenchDialog.kt` (nuevo), `ui/apps/BenchmarkAppScreen.kt`, `ui/roleplay/EnginePerfDialog.kt` (sale el botón antiguo de prueba de velocidad), `runtime/LlamaCppModelHelper.kt` (`benchmarkLoaded()`), `ui/home/BoxSettingsScreen.kt` (retoque menor).
2. **Docs:** "Docs: README del fork, guía para agentes y análisis de bugs y pruebas" — `README.md` (reescritura del fork), `Android/README.md`, `model_allowlists/README.md`, `skills/README.md`, `AGENTS.md` (nuevo), `analisis/BUGS.md` (nuevo, 24 bugs), `analisis/CAMBIOS.md` (nuevo), `analisis/MEMORIA.md` (nuevo), `analisis/PLAN_PRUEBAS.md` (nuevo), `analisis/PRUEBAS.md` (actualización de la sesión del motor ya hecha, sin contenido nuevo de esta sesión), `.github/FUNDING.yml` (quita financiación del autor original).

**Nota:** tras estos commits, el árbol queda limpio. Punto de partida indicado para el chat de reparación de bugs (Tanda 1: BUG-001, BUG-002, BUG-021, BUG-015).

### 2026-11-17 — Sesión: Bloque 5 (robustez y regresión) probado + bugs BUG-021…BUG-024

**Qué y por qué:** el usuario ejecutó el Bloque 5 en el POCO duchamp. Segundo plano 10+ min y apps sin modelo pasan; la generación en segundo plano se pausa y reanuda (comportamiento normal de Android, mejora opcional → BUG-024). Aparecen 3 problemas reales: **rotar recarga el modelo y crashea si estaba generando** (crash nativo litertlm), los logs exportados no incluyen el crash, y la copia de seguridad no se puede importar. Con esto el plan de pruebas queda **ejecutado al completo** (solo queda 5.9 RAM, opcional).

**Verificación en código:**
- **BUG-021** (P1): el manifiesto solo declara `configChanges="uiMode"` (`AndroidManifest.xml` ~78) → la rotación recrea la Activity y re-inicializa el motor; generando, el hilo nativo de `liblitertlm_jni.so` acaba en SIGSEGV.
- **BUG-022** (P2): `installCrashRecorder` (`GalleryApplication.kt:35`) solo captura excepciones Java (`setDefaultUncaughtExceptionHandler`); los crashes del proyecto son nativos (SIGSEGV) → `last_crash.txt` nunca se escribe y el reporte solo trae la sesión reabierta.
- **BUG-023** (P2): exportación de copia en `BoxSettingsScreen.kt:83-103` (`backupZip()` + share); solo existe importación de personaje individual (`CharacterRepository.kt:42-135`), no de la copia completa .zip.
- **5.1:** `QuickBenchDialog.kt` está en el repo pero **sin compilar** (untracked) — por eso la app Benchmark del teléfono aún no trae la prueba rápida; el build actual la mantiene en Rendimiento (GGUF).

**Archivos:**
- `analisis/BUGS.md` — añadidos BUG-021 (P1), BUG-022 (P2), BUG-023 (P2), BUG-024 (mejora, P3); sección "Lo que ya funciona" con el Bloque 5. Inventario: 24 bugs.
- `analisis/PLAN_PRUEBAS.md` — resultados 5.1–5.8 registrados; el checklist del Bloque 5 pasa a re-pruebas (5.1b–5.6b); contador a 24 bugs.

### 2026-11-17 — Sesión: Bloque 4 (descargas y modelos) probado + bugs BUG-016…BUG-020

**Qué y por qué:** el usuario ejecutó el Bloque 4 completo en el Xiaomi (4.1–4.5). Casi todo pasa: navegador HF con errores limpios (401, "no tiene archivos .gguf ni .litertlm"), pegar enlace directo al selector, descarga y cambio de modelos GGUF/LiteRT, visión mmproj (funciona pero lenta), motor guardado por personaje con fallback correcto al borrar el modelo ligado, y benchmark en ambos motores. La prueba 4.5 cubre además el resto del Bloque 1 (LiteRT). Se registran 5 hallazgos nuevos.

**Verificación en código de los hallazgos:**
- **BUG-016** (P1, cambio de modelo): `runInference` falla en seco si el motor recién seleccionado aún no está cargado (`runtime/LlamaCppModelHelper.kt:200-203`) y `handleError` re-inicializa con el aviso "Session re-initialized" (`ui/llmchat/LlmChatViewModel.kt:923-935`). Es una carrera: arreglable (encolar/deshabilitar envío o esperar inicialización).
- **BUG-017** (P3, visión lenta): ~4 min con lado 256; el lado viene de `EnginePerfStore` y las imágenes entran por `queueImages`.
- **BUG-018** (P2, presets): `data/characters/EnginePreset.kt` solo serializa id/name/model/mmproj/personaId/samplerMode/samplerCustom — sin hilos/batch/flash/KV/imageMaxSide.
- **BUG-019** (P3, benchmark LiteRT): métrica propia no comparable con GGUF.
- **BUG-020** (P2 UX, navegador HF): labels de holgura siempre `onSurfaceVariant` (`ui/modelmanager/HfBrowserScreen.kt:142-150`); umbrales en `HfApi.fit()` (`data/characters/HfApi.kt:93-98`, GOOD ≤45 %, TIGHT ≤65 %).
- **BUG-015** recatalogado de P2 a P1 (es calidad de roleplay, no solo UX).

**Archivos:**
- `analisis/BUGS.md` — añadidos BUG-016, BUG-017, BUG-018, BUG-019, BUG-020; BUG-015 movido a P1; sección "Lo que ya funciona" ampliada con los resultados del Bloque 4. Inventario: 20 bugs.
- `analisis/PLAN_PRUEBAS.md` — resultados 4.1–4.5 registrados; el pendiente del Bloque 1 (LiteRT) queda cubierto por 4.5; la sección pendiente del Bloque 4 pasa a re-pruebas (4.1b–4.4b); contador de bugs a 20.

### 2026-11-16 — Análisis de diseño: memoria de Layla vs Box (`MEMORIA.md`)

**Qué y por qué:** el usuario aclaró la diferencia de filosofía de memoria — Layla mantiene un "remembers previously" siempre poblado (resumen continuo generado en tiempo real mientras se chatea) y Box solo inyecta recuerdos tras la absorción manual de notas. Se investigó la documentación oficial de Layla 7 (blog de Layla Network: procesador BART dedicado en tiempo real, grafo de conocimiento, recall por frecuencia+recencia con guardia anti-eco) y se verificó el pipeline completo de Box en código (`AutoMemory`, `MemorySummarizer`, `MemoryAbsorber`, `MemoryPromptBuilder`). Documento base para modificaciones futuras de la memoria.

**Archivos:**
- `analisis/MEMORIA.md` — **nuevo**: cómo funciona la memoria de Layla (con fuentes), cómo funciona la de Box hoy (piezas y archivos exactos), comparación directa y 6 modificaciones propuestas priorizadas (absorción automática, consolidación sin modelo, persistir el resumen de Fase 4, recall con anti-eco, borrado ligado, clústeres temporales).
- `analisis/CAMBIOS.md`, `AGENTS.md` — `MEMORIA.md` añadido a los documentos vivos.

---

### 2026-11-16 — Bug nuevo: BUG-015 (GGUF envía el prompt sin plantilla de turnos)

**Qué y por qué:** el usuario comparó Box vs Layla con el mismo modelo GGUF (gemma-4-E4B PURE), contexto 4096 y mismos parámetros: Layla escribe bien (usa el nombre de la persona, sigue el personaje) y Box degrada (pregunta el nombre pese a estar en el prompt). Los dumps de prompt muestran que Layla envuelve el contenido con los turnos del modelo (`<|turn>system/user/model`) y Box lo manda como texto crudo. Solo pasa en GGUF.

**Verificación en código:** `LlamaCppEngine.seedConversation()` (línea ~235) solo llama `addSystemPrompt()`/`addChatMessage()` del JNI SmolLM; no hay jinja ni `llama_chat_apply_template` en la app — el prompt llega sin estructura de turnos. Gemma es muy sensible a su chat template, de ahí la degradación.

**Archivos:**
- `analisis/BUGS.md` — añadido BUG-015 (P1) con evidencia, causa y fix propuesto (plantilla en Kotlin o `llama_chat_apply_template` en nativo; coordinar con el recorte de BUG-001).
- `analisis/PLAN_PRUEBAS.md` — añadida la re-prueba 2.1b (calidad GGUF ≈ Layla) al Bloque 2.

---

### 2026-11-16 — Verificación del fix de voz de Claude (`2cec7db`) y sincronización del repo

**Qué y por qué:** Claude subió a `fase1-rendimiento` el commit `2cec7db` ("Pocket TTS: acepta tokenizer.json (Unigram de HF) e ignora variantes q4") sin compilar ni probar en dispositivo. Se revisó el diff completo y se sincronizó el repo local (fast-forward; no toca ninguno de los 15 archivos pendientes de commit de esta sesión).

**Veredicto de la revisión:**
- ✅ Correcto lo que añade: `PocketHfSelect` acepta repos con `tokenizer.json` además de `tokenizer.model`; `SentencePieceModel.parseHfJson()` lee el vocabulario Unigram de HF; el diálogo convierte cualquiera de los dos formatos a `vocab.json`/`token_scores.json`; la selección prefiere int8 y no baja q4. Incluye tests unitarios (8, pasan).
- ❌ **No arregla el BUG-013:** el commit no toca `HfApi.parseFiles()` (el filtro que solo deja pasar `.gguf`/`.litertlm` del árbol del repo). En el teléfono la búsqueda de voz seguirá respondiendo "No encontré ninguna carpata…" con cualquier repo, incluido `TTSBard/kyutai-en-clone-onnx`. Los 8 tests de Claude prueban la lógica de selección/conversión directamente, sin pasar por la capa de red donde vive el filtro.
- ❌ BUG-014 (importar carpeta: solo nombres v1) sin cambios en este commit.
- El desbloqueo real del Bloque 3 sin compilar sigue siendo: importar los 7 archivos de la carpeta `es` de `haex-space/pocket-tts-sherpa-onnx`.
- Fix pendiente del BUG-013 (pequeño): parsear el árbol sin filtro de extensión para el TTS y apuntar `DEFAULT_REPO` a un repo compatible sherpa-onnx (la trampa BOS de KevinAHM sigue aplicando).

**Archivos (los del commit, ya en local):**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/tts/PocketHfSelect.kt` — `TOKENIZER_JSON`; `variants()` acepta repos con tokenizer.model o tokenizer.json.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/tts/SentencePieceModel.kt` — `parseHfJson()` (Unigram de HF).
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/roleplay/TtsModelDownloadDialog.kt` — conversión con cualquiera de los dos tokenizadores; limpia tokenizer.json viejo al instalar.
- `Android/src/app/src/test/java/com/google/ai/edge/gallery/tts/PocketHfSelectTest.kt`, `SentencePieceModelTest.kt` — tests nuevos.
- `analisis/BUGS.md` — BUG-013 actualizado con el veredicto. `analisis/PLAN_PRUEBAS.md` — ítem de verificación de `2cec7db` añadido al Bloque 3.

---

### 2026-11-16 — Sesión: Bloque 3 (voz) suspendido + diagnóstico de la carga del modelo TTS

**Qué y por qué:** el usuario probó el Bloque 3 y la descarga desde Hugging Face y la importación de carpeta fallan, así que el bloque queda suspendido (nada de voz funciona sin modelo). Diagnóstico completo hecho en código:

1. **BUG-013:** `HfApi.parseFiles()` filtra solo `.gguf`/`.litertlm` (filtro del navegador LLM) y `HfClient.files()` lo usa tal cual → el diálogo de voz nunca ve los `.onnx` del repo → "No encontré ninguna carpata…" siempre.
2. **BUG-014:** `PocketModelFiles.isPart()/missing()` solo aceptan nombres v1 → la carpeta v2 bajada a mano (flow_lm_*, mimi_*, tokenizer.model) se descarta entera al importar.
3. **Trampa de repo:** los bundles v2 de KevinAHM/pocket-tts-onnx requieren insertar `bos_before_voice.npy`; sherpa-onnx 2.0.1 no lo hace (ruido). Repo compatible verificado: `haex-space/pocket-tts-sherpa-onnx` (nombres v1, BOS integrado, carpeta `es`).

**Desbloqueo para el usuario sin compilar:** importar los 7 archivos de la carpeta `es` de haex-space → pasa el filtro v1 del APK actual.

**Archivos:**
- `analisis/BUGS.md` — añadidos BUG-013 y BUG-014 (P1, bloquean el Bloque 3) con causa raíz, fix propuesto y trampa del repo.
- `analisis/PLAN_PRUEBAS.md` — Bloque 3 movido a resultados como SUSPENDIDO con el diagnóstico y el desbloqueo; sección pendiente convertida en re-pruebas.

---

### 2026-11-16 — Re-prueba 2.1 del Bloque 2: OK en GGUF y LiteRT

**Qué y por qué:** el usuario repitió 2.1 (chat 1 a 1) con el teléfono frío y funcionó bien en ambos motores: responde como el personaje, usa la persona activa ("Hola Héctor…") y no habla por ti. La lentitud de 7+ min de la primera tanda era **thermal throttling situacional**, no bug de código: BUG-012 baja de ❌ a ⚠️ vigilancia. Mejor cifra de generación registrada hasta ahora (4.8 t/s).

**Archivos:**
- `analisis/PLAN_PRUEBAS.md` — 2.1 marcado ✅ con métricas (gen 4.8 t/s · prompt 6 t/s · 98 tok · 40.3 s · contexto 321/4096); retirado de las re-pruebas pendientes.
- `analisis/BUGS.md` — BUG-012 a ⚠️ vigilancia con métricas de la re-prueba; 2.1 añadido a "Lo que ya funciona".

---

### 2026-11-16 — Sesión: resultados del Bloque 2 + inventario de bugs

**Qué y por qué:** se ejecutó el Bloque 2 (roleplay crítico) en POCO duchamp (HyperOS 3.0.9.0, Android 15). Salió el dato más importante hasta ahora: un **crash nativo SIGSEGV** en `llama_vocab::impl::tokenize` ← `common_tokenize` ← `LLMInference::_applyContextWindow()` ← `startCompletion` (libllama.so + libsmollm i8mm del APK) al deslizar la ventana de contexto en escenas/chats largos y al "Absorber clústeres" con GGUF; con LiteRT la absorción da timeout ("modelo tardó demasiado"). Se inventariaron 12 bugs con prioridad para repararlos por bloques en chats limpios.

**Archivos:**
- `analisis/BUGS.md` — **nuevo**: inventario de 12 bugs (P0: BUG-001 crash nativo tokenize al deslizar ventana, BUG-002 absorber clústeres crash/timeout; P1: BUG-003 instrucción de turno visible —en Layla va oculta—, BUG-004 la IA habla por todos los personajes, BUG-005 lista de escenas sin refrescar, BUG-006 sin chips de etiquetas en escenas; P2: BUG-007 compartir ficha inútil, BUG-008 recuerdos sin procesar no editables, BUG-009 hamburguesa no abre cajón, BUG-010 chats de personajes borrados, BUG-011 macros `{user}`/`{{char}}` sin sustituir en fichas/previews; P3: BUG-012 thermal throttling 7+ min al primer turno). Cada uno con síntoma, stack cuando hay, repro, fix propuesto y archivos exactos. Es la base de los chats de reparación.
- `analisis/PLAN_PRUEBAS.md` — resultados del Bloque 2 (2.1 suspendido, 2.2/2.3 OK, 2.4/2.5/2.6 con bugs) añadidos a "Resultados registrados" con referencias a BUGS.md; la sección pendiente del Bloque 2 convertida en **re-pruebas tras corregir**; añadido segundo dispositivo de prueba (POCO duchamp) a la cabecera y enlace a BUGS.md.

**Estado de trabajo:** 14 archivos pendientes de commit (docs + QuickBench). Siguiente paso: chats limpios por prioridad desde `BUGS.md` (empezar por P0).

---

### 2026-11-16 — Sesión: docs del fork + prueba rápida de benchmark

**Qué y por qué:** el repo traía el README de upstream Box 3.5.7 (no representaba el fork). Se reescribió toda la documentación, se vació FUNDING (apuntaba al autor upstream), se quitó la "Prueba de velocidad" de Rendimiento (siempre decía "no hay modelo cargado": con LiteRT era imposible, y con GGUF exigía carga en la misma sesión) y se reubicó como diálogo propio en la app "Benchmark de modelos".

**Archivos:**
- `README.md` — reescrito completo en español: qué es Box RolePlay, características roleplay, herencia de Box 1.0.1 (con aviso destacado de que la base es 1.0.1 y el README heredado era 3.5.7), roadmap, compilación, créditos.
- `Android/README.md` — sustituido el banner ASCII de 318 líneas por estructura + compilación + rutas clave.
- `model_allowlists/README.md` — explica las allowlists por versión.
- `skills/README.md` — referencias "AI Edge Gallery" → "Box RolePlay" (crédito upstream conservado en comunidad).
- `.github/FUNDING.yml` — vaciado (antes `github: jegly`, `buy_me_a_coffee: jegly`).
- `analisis/PLAN_PRUEBAS.md` — **nuevo**: plan priorizado con resultados reales (Xiaomi 2311DRK48G, Android 16; GGUF carga 11 s, primer token 6 s, KV q8 OK, crash al saturar RAM, contador gen 2.3 t/s · prompt 4 t/s).
- `analisis/PRUEBAS.md` — sección "Motor – prueba de velocidad" actualizada (botón eliminado; medir con contador del chat).
- `Android/.../ui/roleplay/EnginePerfDialog.kt` — **eliminada** la sección "Prueba de velocidad" (queda: ajustes + mmproj + último turno).
- `Android/.../ui/home/BoxSettingsScreen.kt` — subtítulo de "Rendimiento del motor" sin "prueba de velocidad".
- `Android/.../ui/apps/QuickBenchDialog.kt` — **nuevo**: prueba rápida GGUF con diagnóstico honesto (nombre del modelo cargado, guía si no hay, distingue generando/liberado).
- `Android/.../ui/apps/BenchmarkAppScreen.kt` — "Prueba rápida del motor GGUF" abre `QuickBenchDialog` (antes reutilizaba `EnginePerfDialog`).
- `Android/.../runtime/LlamaCppModelHelper.kt` — añadido `loadedGgufModelName(): String?` (nombre del primer GGUF con `isModelLoaded`).
- `AGENTS.md` — **nuevo** (raíz): instrucciones automáticas para agentes IA (OpenCode/Claude/otros): reglas del proyecto, documentos vivos, compilación y dispositivo de prueba. Es el mecanismo de continuidad de memoria junto con este archivo.

**Pendiente tras esto:** reconstruir y probar QuickBench (ver Bloque 5 de `PLAN_PRUEBAS.md`).

---

### Resumen histórico pre-log (contexto del git log)

Hitos del fork anteriores a este registro (ver `git log --oneline` para detalles):

| Etapa | Contenido (commits aproximados) |
|---|---|
| Origen | Merge de upstream jegly/main en rama de trabajo; parches "roleplay fase 2" (`f4df716`) |
| Roleplay fase 3–8 | personas (`625b7da`), contexto GGUF (`dc86d81`), LiteRT Gemma 4 (`b5175c9`), swipes (`f742d48`), muestreo (`59a67a7`), motor por modelo (`6be8d8a`), memoria/lorebook (`51f98b8`, `a21008c`), biblioteca (`5b91566`), tarjetas ST (`ede76f7`), visión+pensamiento (`0382a36`), voz base (`8417d62`), especulativa (`28a71ee`) |
| Fase 1 | Rendimiento GGUF: lotes, ubatch, flash attn, caché KV, repack, mmap, contexto, prefill por trozos, estadísticas (`d5d70f4`) |
| Fase 2 | Voz: Pocket TTS (sherpa-onnx), altavoz, lectura automática, voz por personaje (`a84f61d`); descarga de voces desde HF (`fd31e27`, `604fe84`) |
| Fase 3 | Visión GGUF con mmproj/mtmd (`c911490`) |
| Fase 4 | Memoria larga: resumen automático al salir de ventana, panel "Información del chat" (`a110993`) |
| Fase 5 | Liberar modelo tras 10 min en 2.º plano y recarga (`2b39745`) |
| Fase 6 | Nombre "Box RolePlay", presets de muestreo (`5d87fef`) |
| Extras | Contador de rendimiento y exportar logs (`09694da`), pulido TTS (`b31e278`), bench de motor (`44af314`), copia de seguridad .zip (`284fd2d`) |
| Fase A | Inicio/configuración/inferencia propios, icono (`51f48ad`) |
| Fase B | Ficha/resumen de personaje (`0a8fc5b`) |
| Fase C | Mini-apps independientes (`c35f68d`) |
| Fase D/D2 | Escenas multipersonaje (`0261e8d`), barra de escena (`1fb7263`) |
| Fase E/E2 | Cajón lateral (`8e41412`), memoria en dos niveles (`b2e4764`, `a804ebb`) |
| Extras 2 | Motores guardados (`7e6a8a3`), voz en vivo (`6a89340`), motor por personaje (`9b44008`), grupos (`04f7748`), descarga GGUF desde HF (`07fe275`), sherpa AAR (`3c859d8`) |
