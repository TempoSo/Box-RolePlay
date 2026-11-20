# 📦 Archivo de bugs cerrados — Box RolePlay

> **Histórico de bugs cerrados.** Los activos y dudosos siguen en [`BUGS.md`](BUGS.md). Este archivo se consulta solo si quieres auditar un cierre; tú decides cuándo.
> Cada ID conserva aquí su texto original (síntoma, causa, fix y archivos), más el commit o la verificación que lo cerró.

## Resumen

| ID | Bug | Cierre |
|---|---|---|
| [BUG-001](#bug-001) | Crash nativo GGUF al deslizar la ventana de contexto | `0804d16c` · verificado 2.4 (19/11) |
| [BUG-002](#bug-002) | Absorber clústeres: crash GGUF / timeout LiteRT | 5 rondas · verificado 2.5 (17/11) |
| [BUG-003](#bug-003) | Instrucción de turno visible como burbuja | `0ad14cb9` · verificado 2.4 (19/11) |
| [BUG-004](#bug-004) | IA habla por todos los personajes | `0ad14cb9` · verificado 2.4b (19/11) |
| [BUG-005](#bug-005) | Lista de escenas no se refresca al guardar | `ff2bff7b` · verificado 2.4 (19/11) |
| [BUG-006](#bug-006) | Sin chips de etiquetas en escenas | `d9432256` · verificado 2.4 (19/11) |
| [BUG-012](#bug-012) | Thermal throttling | descartado como bug de código; queda en vigilancia |
| [BUG-013](#bug-013) | Descarga HF de voz: nunca encuentra carpetas | `89bc4cc7` · verificado 3.1 (17/11) |
| [BUG-018](#bug-018) | Presets de motor no guardan rendimiento | `478740ac` · verificado 4.4b (19/11) |
| [BUG-020](#bug-020) | Etiquetas de holgura todas del mismo color | `18d259da` · verificado 4.1/4.1c |
| [BUG-022](#bug-022) | Exportar logs no incluía el crash | `150516be` · verificado 09:36 (19/11) |
| [BUG-023](#bug-023) | No había UI para restaurar copia | `fec62bd3` · verificado 5.6b (19/11) → deriva en BUG-038 |
| [BUG-024](#bug-024) | Generación se pausa en segundo plano | comportamiento aceptable; mejora opcional |
| [BUG-027](#bug-027) | Importar muestras .wav para clonar voz | descartado (19/11) → pasa a BUG-041 |
| [BUG-028](#bug-028) | Etiqueta de holgura sin espaciado estable | `3cb6de0b` · verificado 4.1c (19/11) |
| [BUG-031](#bug-031) | LiteRT: respuestas vacías tras cancelar | `150516be` · verificado 10:51 (19/11) |

---

### BUG-001

**✅ Crash nativo GGUF al deslizar la ventana de contexto**

- **Fix aplicado (compilado, pendiente de re-prueba en teléfono — 2.4):** plantilla de turnos de Gemma 4 construida en Kotlin (`engine/GgufChatTemplate.kt`, nueva): el prompt llega al nativo ya formateado y `startCompletion` lo tokeniza tal cual, **saltándose `_applyContextWindow()` y el formateo nativo** (la ruta exacta del SIGSEGV). Además: no se siembran ni envían cadenas vacías al tokenizador (`LlamaCppEngine.seedConversation` filtra vacíos; `LlamaCppModelHelper.runInference` rechaza mensaje vacío sin imágenes) y el recorte del historial sigue haciéndolo la app (ContextWindowPlanner) antes de sembrar el turno. Arreglado junto con BUG-015 (misma zona). Los GGUF que no son Gemma 4 siguen la ruta nativa de siempre.
- **Síntoma:** la app muere y la sesión se re-inicializa sola ("llama.cpp engine not initialized for …" + "Session re-initialized"). Pasó en escena y en chat largo.
- **Stack (resumen):** `SIGSEGV` en `llama_vocab::impl::tokenize` ← `common_tokenize` ← `LLMInference::_applyContextWindow()` ← `LLMInference::startCompletion` ← JNI `SmolLM.startCompletion`. Libs: `libllama.so` + `libsmollm_v8_4_fp16_dotprod_i8mm_sve.so` del APK.
- **Repro:** escena con prompt grande (contexto 25 %→34 %) enviando mensaje; también al "Absorber clústeres".
- **Causa probable:** `_applyContextWindow()` re-tokeniza el historial al deslizar la ventana (Fase 4) y el `tokenize` nativo crashea (vocab inválido / cadena vacía / mismatch entre la build de llama.cpp del submódulo y el JNI de SmolLM).
- **Archivos:** `engine/LlamaCppEngine.kt` (sembrar historial y deslizamiento), `runtime/LlamaCppModelHelper.kt`, submódulo `Android/src/llama.cpp` (no editar a la ligera).
- **Mitigación en Kotlin mientras tanto:** no dejar que el prompt llegue al límite (recortar historial antes de `startCompletion`), evitar tokenizar cadenas vacías, y limitar el tamaño del prompt de absorción.


---

### BUG-002

**✅ Absorber clústeres: crash con GGUF / timeout con LiteRT**

- **Fix aplicado (compilado, pendiente de re-prueba en teléfono — 2.5):** absorción por tandas (`MemoryAbsorbPrompt.tandas`, máx. 3500 caracteres y 8 tandas; se descartan las más viejas). `MemoryAbsorber.run` acepta varios prompts, resetea la conversación por tanda y aplica watchdog **por tanda** (180 s) con progreso "Absorbiendo tanda X de Y…". Cada tanda produce su propio recuerdo; las notas de una tanda sin resumen útil no se borran. Archivos: `ui/memory/MemoryAbsorber.kt`, `ui/memory/MemoryScreen.kt` (MemoryViewModel), `data/characters/RawMemory.kt` (tandas), tests en `RawMemoryTest.kt`.
- **Re-prueba 2.5 (17/11):** seguía crasheando en GGUF, ahora en el branch de marcadores (`SIGSEGV` en `llama_vocab::impl::tokenize` ← `common_tokenize` ← `startCompletion`, sin `_applyContextWindow`). Descartado el contenido (las notas automáticas ya van sin etiquetas y la tanda cabe holgada): la causa probable es la **carrera del reset por tanda** — `resetConversation` en GGUF hace close+reload asíncrono y el Absorber solo esperaba 300 ms, con `isModelLoaded` aún a `true` → la tanda podía arrancar contra un motor a medio cerrar (use-after-free en el tokenizador).
- **2.ª ronda (compilada, re-probada — 2.5: crash ARREGLADO):** (1) el reset por tanda ya no cierra/recarga el modelo: nueva vía `prepareForAuxInference` (`LlmModelHelper` con default = `resetConversation`; en llama.cpp = `seedConversation("", vacío)` → `resetChat()` nativo, borra historial+KV sin tocar los pesos); (2) `resetConversation` apaga `isModelLoaded` durante el close/reload; (3) sanitizado de notas en `MemoryAbsorbPrompt.build`; (4) prompt archivista estilo Layla con contexto del personaje por campos; (5) logs `AGLlamaPerf` del estado del prompt antes de tokenizar. Archivos: `runtime/LlmModelHelper.kt`, `runtime/LlamaCppModelHelper.kt`, `ui/memory/MemoryAbsorber.kt`, `engine/LlamaCppEngine.kt`, `data/characters/RawMemory.kt`, `ui/memory/MemoryScreen.kt`, `RawMemoryTest.kt` (+4 tests).
- **3.ª ronda (compilada, re-probada — 2.5: crash sigue ausente, quedó "límite 379 s"):** el watchdog **fijo de 180 s** era menor que el prefill real de una tanda en el dispositivo (log: prefill ~6.5 t/s ⇒ ~215 s para ~1400 tokens), y además `resetChat()` entre tandas tiraba la KV (`llama_memory_clear`) y re-prefilleaba el preámbulo archivista idéntico de cada tanda. Cambios (todo Kotlin): (1) **timeout dinámico por tanda** `MemoryAbsorber.tandaTimeoutMs` con la velocidad real medida (`LastEnginePerf.stats`), margen ×1.5, suelo 4 min / techo 30 min; (2) **límite ligero entre tandas** `auxTandaBoundary` (`LlmModelHelper` default = `prepareForAuxInference`; en llama.cpp Gemma 4 = `LlamaCppEngine.prepareForAuxTanda`, que conserva la KV: el nativo reutiliza el prefijo común con `_prepareDecode`+`reusablePrefix` y solo prefill las notas nuevas; en modelos no Gemma 4 sigue `resetChat` completo); (3) reset a fondo solo antes de la primera tanda. Tests: +6 en `MemoryAbsorberTest.kt`.
- **4.ª ronda (compilada, re-probada — 2.5: quedó "límite 623 s"):** el límite de 379 s de la 3.ª ronda encajaba con prefill ~240 s + **generación sin tope ~140 s** (el nativo corre la absorción con `_maxResponse = -1`): un resumen desbocado sin EOS consume el watchdog entero. Cambios: (1) **tope de generación por tanda** (`MemoryAbsorber.MAX_RESPONSE_TOKENS = 400` vía `extraContext["max_response_tokens"]`, solo llama.cpp; `LlamaCppEngine.setMaxResponse`/`restoreMaxResponse`, restaurado al tope del chat en complete/cancel/error); (2) timeout recalibrado (~3.0 car./tok, margen ×2 por térmica, gen = 400 tok, suelo 8 min).
- **5.ª ronda (compilada, VERIFICADA por el usuario — 2.5 OK en GGUF y LiteRT, 17/11):** las ESTIMACIONES fallaron dos veces (180 s, 623 s): la térmica del SoC ralentiza el prefill a menos de la mitad en carga sostenida (medido: 5.8 t/s fresco → ~2.4 t/s caliente; 623 s ≈ prefill 1167 tok @ ~2.4 t/s + generación 400 tok @ ~3 t/s, es decir el tope de la 4.ª ronda SÍ trabajó y el dispositivo simplemente iba a media velocidad). Ya no se estima: (1) **tope global por tanda de 20 min** (`TANDA_TIMEOUT_MS`, anti-cuelgue); (2) **detector de atasco** `tandaAbortReason`/`STALL_TIMEOUT_MS` = 120 s sin tokens nuevos tras el primero (los tokens llegan cada ~0.3 s; en prefill no hay señal y manda el tope global); (3) **pausa de enfriamiento de 30 s** (`COOLDOWN_MS`) entre tandas. Tests: 6 en `MemoryAbsorberTest.kt` reescritos (fases, stall, bordes). **Cerrado.**
- **Observación:** tombstone 07:18 en `liblitertlm_jni.so` (hilo `callback_thread`, null deref) — crash nativo de LiteRT, a diagnosticar aparte si reaparece.
- **Síntoma:** con GGUF crashea (mismo stack de BUG-001). Con LiteRT: mensaje "modelo tardó demasiado".
- **Repro:** Memoria → personaje → "Absorber clústeres" con muchas notas.
- **Causa probable:** el prompt de absorción (todas las notas juntas) es enorme → desliza ventana (crash) o agota un watchdog de tiempo.
- **Archivos:** `data/characters/MemoryAbsorber.kt`, `ui/memory/MemoryScreen.kt`.
- **Fix propuesto:** absorber en tandas (chunking) o recortar notas al tamaño del contexto; quitar/ajustar el watchdog para LiteRT; avisar progreso.


---

### BUG-013

**✅ Descarga HF del modelo de voz: nunca encuentra carpetas (bloquea el Bloque 3)**

- **Fix aplicado (compilado, pendiente de re-prueba en teléfono — 3.1):** `HfApi.parseTreeFiles()` devuelve el árbol sin filtro de extensión y `HfClient.treeFiles()` lo expone; el diálogo de voz usa esa vía, dejando `parseFiles` intacto para el navegador LLM. `DEFAULT_REPO` ahora es `haex-space/pocket-tts-sherpa-onnx` (BOS ya integrado, compatible con sherpa-onnx 2.0.1); nunca volver a KevinAHM como repo por defecto. Tests en `HfApiTest.kt`.
- **Verificado por el usuario (17/11):** 3.1 ya descarga e importa modelos desde HF. El bloque 3 queda abierto solo por la importación de muestras `.wab` (BUG-027).
- **Síntoma:** en "Ajustes de voz → Descargar de Hugging Face", con cualquier repo (incluido el por defecto `KevinAHM/pocket-tts-onnx`), sale siempre "No encontré ninguna carpata con un modelo Pocket TTS completo en ese repositorio" y "Descargar" queda deshabilitado (captura 17:06).
- **Causa raíz localizada:** `HfApi.parseFiles()` (`data/characters/HfApi.kt`, línea ~74) **solo devuelve archivos `.gguf`/`.litertlm`** — es el filtro del navegador de modelos LLM. `HfClient.files()` lo usa tal cual, así que el diálogo de voz recibe vacío antes de llegar a `PocketHfSelect.variants()`. El parseo del enlace pegado funciona bien (`parseRepoId` acepta URLs completas).
- **Fix:** añadir un parseo del árbol sin filtro de extensión (p. ej. `parseTreeFiles()` o parámetro `extensions`) para el TTS, dejando `parseFiles` como está para el navegador LLM.
- ⚠️ **Trampa al arreglarlo:** NO dejar `KevinAHM/pocket-tts-onnx` como repo por defecto. Sus bundles v2 exigen insertar `bos_before_voice.npy` antes del embedding de voz y sherpa-onnx 2.0.1 no lo hace: el audio sale **ruido o corta a los 0,2 s** (documentado por haex-space). Usar un repo ya compatible con sherpa-onnx: `haex-space/pocket-tts-sherpa-onnx` (carpetas `de/en/es/fr/it/pt` con nombres v1 y BOS ya integrado en `encoder.onnx`) o el release oficial k2-fsa `sherpa-onnx-pocket-tts-int8-2026-01-26` (inglés).
- **Archivos:** `data/characters/HfApi.kt` (`parseFiles`), `ui/roleplay/TtsModelDownloadDialog.kt` (`DEFAULT_REPO`), `tts/PocketHfSelect.kt` (revisar al tocar).
- **Actualización (commit `2cec7db`, Claude — revisado por OpenCode):** añade soporte de repos con `tokenizer.json` (Unigram de HF) además de `tokenizer.model`, y la selección prefiere int8 sin bajar variantes q4 (`PocketHfSelect.kt`, `SentencePieceModel.kt` → `parseHfJson()`, `TtsModelDownloadDialog.kt`), con 8 tests unitarios que pasan. **NO toca el filtro de `parseFiles`**: la causa raíz sigue viva y en el teléfono la búsqueda seguirá dando "No encontré ninguna carpeta…" con cualquier repo. Los tests pasan porque alimentan listas de archivos directamente a la lógica de selección, sin pasar por la capa de red donde vive el filtro.


---

### BUG-003

**✅ Escenas: la instrucción "(Next message: write only as X.)" se envía como mensaje visible**

- **Fix aplicado (2026-11-19):** la instrucción viaja en `SendMessageTrigger.promptSuffix` y `generateResponse()` la concatena al `input` del motor (`engineInput`) sin crear mensaje ni persistirlo. "🎲 Aleatorio" dispara un turno con `messages = emptyList()` y ese sufijo como único prompt. Compilado y tests de `ScenarioTest` en verde.
- **✅ Verificado en el teléfono (2026-11-19, 2.4/2.4b):** escribiendo como Tohru solo aparece la burbuja `Tohru: Hola`, sin ningún mensaje visible con `(Next message…)`; con "🎲 Aleatorio" y el campo vacío no se crea burbuja de usuario y la respuesta es un único bloque `KIP:`.
- **Síntoma:** aparece como burbuja tuya verde en el chat (captura 16:22). En **Layla esa instrucción va oculta** (se inyecta al prompt sin crear mensaje) — es el comportamiento esperado.
- **Archivos:** `ui/llmchat/LlmChatScreen.kt` (línea ~362 crea el mensaje visible; ~227 lógica del speaker), `data/characters/ScenePromptBuilder.kt` (`turnInstruction()` ya la genera; falta inyectarla oculta).
- **Fix:** inyectar la instrucción al prompt sin añadir mensaje a la conversación (o añadirlo marcado `hidden` que no persiste ni renderiza).
- **Verificado (2026-11-19):** el envío se hace con `SendMessageTrigger(model, messages)` (`ui/common/chat/ChatView.kt:79`) y `LlmChatScreen.kt:358-365` mete la instrucción como `ChatMessageText` de lado USER, que es lo que se pinta y se guarda. No existe ningún soporte de mensajes ocultos. Dos vías: (a) añadir a `SendMessageTrigger` un `promptSuffix: String?` que `generateResponse` concatene al prompt del turno sin tocar la lista de mensajes; (b) `ChatMessageText` con marca `hidden` que no se persista ni renderice (más invasivo).


---

### BUG-004

**✅ Escenas: la IA habla por TODOS los personajes pese a "write only as X"**

- **Fix aplicado (2026-11-19):** instrucción de turno reforzada + `TurnRestriction` que se inyecta al prompt y filtra los bloques `Nombre:` de hablantes no permitidos durante el streaming (`setLastTextMessageContent`, el texto puede encoger). Al escribir como un personaje/narrador, la IA se queda con los demás; con "Aleatorio", solo con el elegido. La restricción se conserva al regenerar/swipe. Compilado y tests de `ScenarioTest` en verde.
- **✅ Verificado en el teléfono (2026-11-19, 2.4b):** escribiendo como **Tohru**, la IA respondió como **Narrador, KIP y Akiko Fujimoto** y no emitió ningún bloque `Tohru:`; con **Aleatorio** solo habló **KIP**. Las flechas de regeneración no provocaron crash.
- **Síntoma:** pediste "write only as Akiko" y respondió Narrator + KIP + Akiko (captura 16:22).
- **Causa:** el system prompt ya lo prohíbe (`ScenePromptBuilder.build()` líneas 52-58) pero el modelo lo ignora; la instrucción de turno es débil y encima visible (BUG-003).
- **Fix propuesto:** al construir el prompt del turno, añadir restricción fuerte ("This turn: write ONLY as $X. One block. No other characters, no narrator.") y/o filtrar en post-procesado las líneas `Nombre:` prohibidas.
- **Archivos:** `data/characters/ScenePromptBuilder.kt`, `ui/llmchat/LlmChatScreen.kt`.
- **Verificado (2026-11-19):** el único freno del system es "Only write for the characters listed…" (`ScenePromptBuilder.build()`, líneas ~52-58) y la instrucción de turno es un paréntesis suave (`turnInstruction()`, líneas ~73-78). El post-filtrado es viable porque todas las respuestas siguen el formato `Nombre: texto` (`NARRATOR` y `SceneSpeakerBar` usan esa convención): se pueden quitar las líneas que empiecen por `Nombre:` de personajes no permitidos, siempre que no estén dentro de un párrafo de otro hablante.


---

### BUG-005

**✅ Escenas: la lista no se refresca al guardar**

- **Fix aplicado (2026-11-19):** `ScenarioStore` pasa a ser un **singleton de Hilt** con una única fuente observable (`scenarios: StateFlow<List<Scenario>>`), recargada tras cada `save()`/`delete()` y en el arranque. `ScenariosViewModel` ya no guarda su propia copia: expone el `StateFlow` del store, así que la lista y el editor (dos destinos distintos) comparten el mismo estado aunque cada uno tenga su instancia de ViewModel. `ChatRepository.sceneMemberNames()` también recibe el store por inyección.
- **✅ Verificado en el teléfono (2026-11-19, 2.4):** al añadir la etiqueta `#ai` en el editor de "El Final" y guardar, la lista mostró de inmediato `#fantasía #ciencia ficción #ai` sin salir de la pantalla; al quitarla y guardar, desapareció.
- **Archivos:** `data/characters/ScenarioStore.kt`, `ui/scenarios/ScenariosViewModel.kt`, `data/local/ChatRepository.kt`.
- **Síntoma:** guardas el escenario y no aparece; hay que salir al inicio y volver.
- **Causa verificada (2026-11-19):** las escenas viven en `ui/scenarios/` (`ScenarioStore` sobre `scenarios.json`, sin Room). `ScenariosScreen` y `ScenarioEditorScreen` llaman cada una a `hiltViewModel()` y son **destinos de navegación distintos**, así que tienen **dos instancias** de `ScenariosViewModel`: el editor refresca su propio `_scenarios` y la lista (otra instancia) nunca se entera. `save()` sí escribe el archivo y `refresh()` sí se llama, pero sobre la instancia equivocada.
- **Fix propuesto:** que `ScenarioStore` exponga un `StateFlow`/Flow recargado tras cada `save`/`delete` y que `ScenariosViewModel` lo recoja (o refrescar al volver con `LifecycleResumeEffect`/`DisposableEffect` sobre la entrada de la lista).
- **Archivos:** `ui/scenarios/ScenariosViewModel.kt`, `ui/scenarios/ScenarioScreens.kt`, `data/characters/ScenarioStore.kt`.


---

### BUG-006

**✅ Escenas: no se ven las etiquetas existentes**

- **Fix aplicado (2026-11-19):** componente compartido `ui/common/TagChips.kt` con las etiquetas ya usadas (escenas + personajes, sin duplicados y ordenadas); al tocarlas se añaden/quitan del campo de texto libre. Se usa en el editor de escenas y también en el de personajes. Compilado y `TagChipsTest` en verde.
- **✅ Verificado en el teléfono (2026-11-19, 2.4):** el editor de "El Final" muestra los chips `#ai`, `#anime`, `#ciencia ficción`, `#fantasía`, `#femenino`, `#magia`, `#robot`; al tocar `#ai` se añadió al campo de etiquetas y al volver a tocarlo se quitó. La biblioteca de personajes también muestra chips (`ai`, `anime`, `femenino`, `magia`, `robot`).
- **Síntoma:** al crear/editar no hay chips de etiquetas como en Personajes; te olvidas de qué tags existen.
- **Verificado (2026-11-19):** el editor de **personajes tampoco** tiene chips: usa un `OutlinedTextField` de texto separado por comas (`ui/roleplay/RoleplayScreens.kt:531-536`). En escenas pasa lo mismo (`ui/scenarios/ScenarioScreens.kt:180-186`, `tagText.split(",")` al guardar). Por tanto no hay patrón que copiar: hay que crear los chips.
- **Fix propuesto:** encima del campo de texto, una fila de chips con las etiquetas ya usadas (escenas + personajes) que al tocarlas añadan/quiten la etiqueta del campo; mantener el texto libre para etiquetas nuevas.
- **Archivos:** `ui/scenarios/ScenarioScreens.kt`, opcionalmente `ui/roleplay/RoleplayScreens.kt` (mismo componente para los dos editores).


---

### BUG-027

**✅ Importar muestras `.wav` para clonar voz: no deja**

- **Síntoma (feedback 17/11):** tras arreglar 3.1, el humano reportó que "no deja importar los `.wab`, que son los archivos para clonar las voces", y después aclaró: **"es wav, se me confundió la letra"**. Es decir, falla la importación de muestras `.wav`.
- **Código verificado:** hay dos entradas para importar voz: `ui/roleplay/TtsSettingsDialog.kt:127` ("Elegir muestra de voz (.wav)" → `TtsFiles.importVoice(context, uri, "default")`) y `CharacterVoiceRow` (`:241` → `id = "char_$characterId"`). Ambas usan `ActivityResultContracts.OpenDocument()` con MIME `audio/*`. `TtsFiles.importVoice()` (`tts/TtsFiles.kt:47-54`) lee los bytes y exige `WavReader.parse(bytes) != null`; si falla, el diálogo muestra "Ese archivo no es un WAV legible (PCM de 16/24/32 bits)." y no guarda nada.
- **Lo que SÍ está soportado:** `WavReader.parse()` (`data/characters/WavReader.kt:21-77`) acepta RIFF/WAVE con PCM 8/16/24/32 bits o float de 32 bits (format 1/3, también `WAVE_FORMAT_EXTENSIBLE`), mono/estéreo, y mezcla a mono; `TtsManager.launchJob()` (`tts/TtsManager.kt:218`) vuelve a parsear el archivo ya guardado antes de generar. Por tanto **no es falta de soporte de `.wav` en el código**: hace falta el mensaje exacto del teléfono o una muestra para ver si el archivo usa otro codec (μ-law/ADPCM), tiene cabeceras no estándar o el selector no lo abre.
- **Pendiente con el humano:** qué toast/error sale al elegir el `.wav` y, si puede, adjuntar el archivo que falla. No se aplica ningún fix a ciegas.
- **Archivos:** `tts/TtsFiles.kt`, `data/characters/WavReader.kt`, `tts/TtsManager.kt`, `ui/roleplay/TtsSettingsDialog.kt`.


---

### BUG-031

**✅ LiteRT (I-6): tras cancelar y reenviar, las respuestas salen vacías (~500 ms) y queda "cargando modelo…"**

- **Síntoma:** con un modelo `.litertlm`, enviar un mensaje, cancelarlo a mitad y enviar otro: desde ese momento todas las respuestas salen vacías en ~500 ms y el indicador se queda en "⏳ cargando modelo…" hasta reabrir la app.
- **Causa verificada:** `LlmChatModelHelper.resetConversation()` y `resetConversationWithHistory()` cerraban la conversación (`conversation.close()`) **sin cancelar ni esperar** la generación en curso. El chat LiteRT recrea la conversación justo antes de cada envío (`LlmChatViewModel.applySystemPromptIfNeeded` → `resetConversationWithHistory`); al llegar con el turno anterior aún vivo en el hilo nativo, la conversación nueva quedaba inservible y el motor devolvía un `done` vacío. Además `stopResponse` solo hacía `cancelProcess()` y el ViewModel ponía `inProgress = false` sin limpiar `preparing` ni la línea "⏳ cargando modelo…".
- **Fix aplicado (2026-11-19):** (1) **candado por modelo** en `LlmChatModelHelper`, equivalente al `nativeGate` de `LlamaCppEngine`: reset, envío y limpieza se serializan; antes de cerrar se cancela, se espera al callback nativo (máx. 10 s) y **solo entonces** se recrea la conversación con el historial. (2) `cleanUp` toma el mismo candado, evita dobles limpiezas y se ejecuta en un hilo propio si lo llaman desde el principal (la espera ya no bloquea la UI). (3) El chat limpia `preparing` y el "⏳ cargando/procesando…" al cancelar, al terminar y en el `catch`. (4) Una respuesta **vacía en menos de 750 ms** se trata como error ("El modelo devolvió una respuesta vacía…"), de modo que `handleError` limpie y reinicie la sesión en vez de dejar el chat mudo.
- **Archivos:** `ui/llmchat/LlmChatModelHelper.kt`, `ui/llmchat/LlmChatViewModel.kt`.
- **Segunda ronda (2026-11-19, tras probar en el teléfono con `ea50de1`):** el fallo seguía. El log `box-log-20261119-093634.txt` demuestra que **no se recreaba la conversación**: no hay ni una línea "Resetting conversation with …" en todo el turno, y las respuestas vacías llegan en **503 y 511 ms** (500 ms son el `delay(500)` del chat; el turno real dura ~3 ms). Causa: `LiteRtWindowState`/`inSync` consideraba la conversación "en sincronía" y se saltaba el reset, así que se seguía enviando sobre la `Conversation` que había quedado inservible al cancelar. Corregido: `stopResponse` marca el modelo en `cancelledTurns`; `applySystemPromptIfNeeded` consume esa marca y **fuerza** `resetConversationWithHistory` (cancelar + esperar + cerrar + recrear con historial) antes del siguiente envío. Además la detección de respuesta vacía ahora cuenta **caracteres** entregados, no callbacks (LiteRT puede llamar a `onMessage` con un mensaje vacío y eso dejaba la burbuja vacía sin error), se comprueba `conversation.isAlive` antes de enviar, se quita la burbuja vacía en `handleError` y se añaden trazas del turno (`Turno LiteRT '…': N caracteres en X ms`).
- **Re-prueba (teléfono):** modelo `.litertlm` → enviar → cancelar a mitad → enviar otro mensaje. Debe responder con normalidad, sin "cargando modelo…" pegado y sin reinicio de sesión. En el log debe verse `Cancelación manual de '…': se recreará la conversación al reenviar` y `Conversación de '…' cancelada: se recrea con el historial`.
- **✅ Verificado en el teléfono (2026-11-19, 10:51):** tras varias respuestas cortadas a mitad, el chat sigue respondiendo con normalidad (respuestas de 6.9 s y siguientes con contenido real, contexto 368/4096). El estado de la sesión ya no se degrada. Queda solo un detalle cosmético arreglado después: la línea de estado se quedaba en "⏳ cargando modelo…" durante la generación.


---

### BUG-018

**✅ Presets de motor: no guardan rendimiento (hilos, batch, flash, KV, visión)**

- **Fix aplicado (2026-11-19):** `EnginePreset` incorpora los diez campos de rendimiento como anulables (migración tolerante); `BoxInferenceScreen` los guarda y `EngineApply.applyBasics()` los restaura junto a visión y muestreo, usando la misma vía para presets generales y de personaje. Tests en `EnginePresetTest` en verde. Nota: el archivo real es `EnginePerfSettings.kt`, no `EnginePerfStore.kt`.
- **✅ Verificado en el teléfono (2026-11-19, 4.4b):** se guardó el motor `PRUEBA` con 2 hilos, se cambiaron a 7 y al aplicar el preset volvieron a 2; batch 64, ubatch 512, Flash Attention Auto y KV f32 se conservaron. El perfil de prueba se borró y los hilos se dejaron en 0 (automático).
- **Síntoma (Bloque 4, prueba 4.4):** el preset guarda modelo, mmproj, persona y muestreo, pero NO los ajustes de rendimiento: al aplicarlo no se restauran hilos, batch, flash attention, KV ni el lado máximo de imagen.
- **Causa verificada:** `data/characters/EnginePreset.kt` solo serializa `id/name/model/mmproj/personaId/samplerMode/samplerCustom` — el rendimiento vive en `EnginePerfStore` y no se incluye en el preset.
- **Campos exactos a añadir (verificados en `data/characters/EnginePerfSettings.kt:11-29`):** `threads`, `nBatch`, `nUBatch`, `flashAttn`, `kvType`, `noMmap`, `repack`, `contextLength`, `maxResponse`, `imageMaxSide`. El `mmproj` ya está en el preset; el contexto y el tope de respuesta conviene guardarlos también aunque la prueba 4.4 no los mencione.
- **Fix:** añadir los campos de rendimiento a `EnginePreset` (hilos, batch, flash, KV, imageMaxSide) con valores por defecto (migración tolerante) y aplicarlos al cargar el preset.
- **Archivos:** `data/characters/EnginePreset.kt`, `data/characters/EnginePerfStore.kt`, `ui/modelmanager/EnginePerfDialog.kt`.


---

### BUG-020

**✅ Navegador HF: las etiquetas de holgura se ven todas en el mismo color**

- **Fix aplicado (compilado):** la etiqueta de holgura se pinta aparte con color según `HfFit` (GOOD = terciario/verde, TIGHT = ámbar, TOO_BIG = `error`) en `ui/modelmanager/HfBrowserScreen.kt`. Los umbrales de `HfApi.fit()` no se tocan; la recalibración sigue siendo opcional.
- **Comprobación 2026-11-19 (4.1c):** el label se ve en su propia línea y el código de colores sigue intacto (BUG-028). El tono concreto (verde/ámbar/rojo) no se pudo confirmar con un volcado de UI; haría falta captura visual del humano para darlo por cerrado del todo.
- **Verificado por el usuario (17/11):** 4.1 funciona; el detalle pendiente de maquetación (espaciado / "orden de las letras") se sigue en BUG-028.
- **Síntoma (Bloque 4, prueba 4.1):** "cabe bien" / "justo" / "demasiado grande" aparecen en el mismo gris; no se distinguen de un vistazo. Además el umbral de "justo" resulta conservador (aparece con margen real de sobra).
- **Causa verificada:** `ui/modelmanager/HfBrowserScreen.kt:142-150` pinta el label siempre con `onSurfaceVariant`, sin depender del nivel. Umbrales en `HfApi.fit()` (`data/characters/HfApi.kt:93-98`): GOOD ≤ 45 % de la RAM, TIGHT ≤ 65 %, TOO_BIG el resto.
- **Fix:** colorear según `HfFit` (GOOD verde/terciario, TIGHT ámbar, TOO_BIG error) y, si acaso, recalibrar umbrales con datos reales (POCO: 11 GB RAM).
- **Archivos:** `ui/modelmanager/HfBrowserScreen.kt`, `data/characters/HfApi.kt`.


---

### BUG-028

**✅ Navegador HF (4.1): la etiqueta de holgura no sigue un espaciado estable**

- **Fix aplicado (2026-11-19):** la etiqueta se pinta en su propia línea (4 dp sobre los metadatos, `maxLines = 1`) y los metadatos se acotan a 2 líneas con ellipsis y 8 dp de separación del botón. Colores de BUG-020 intactos; umbrales sin tocar. Compilado.
- **✅ Verificado en el teléfono (2026-11-19, 4.1c):** en `google/gemma-4-E4B-it-qat-q4_0-gguf` la etiqueta "✔ cabe bien" aparece en su propia línea bajo `4.80 GB · Q4_0`, separada de los metadatos y del botón Descargar; no aparece pegada ni recortada.
- **Síntoma (feedback 17/11, aclarado por el humano):** 4.1 funciona, pero "me gustaría que siga el orden de las letras ya que algunos aparecen muy apegados y otros muy separados". Se refiere a la **separación visual** de la etiqueta de holgura respecto al resto del texto, no al orden alfabético de los archivos.
- **Causa verificada (capturas 15:33 del 17/11, repo `unsloth/gemma-4-12b-it-GGUF`):** `ui/modelmanager/HfBrowserScreen.kt:132-163` pinta los metadatos (`fmtSize · quant · visión/partido`) en un `Text` con `Modifier.weight(1f)` y la etiqueta (`✔ cabe bien` / `justo` / `demasiado grande`) en otro `Text` hermano, con el botón Descargar detrás. Como el primer texto ocupa todo el ancho sobrante: (a) cuando los metadatos son largos la etiqueta queda **pegada sin separación** (se ve "Q5_K_Sdemasiado grande"); (b) cuando son cortos queda lejos, a mitad de fila (se ve "5.30 GB · Q3_K_M   justo"); (c) las etiquetas largas se **recortan contra el botón** ("justo" y "demasiado grande" aparecen cortados); (d) si los metadatos parten en dos líneas, la etiqueta queda centrada a media altura. Es decir: sin separador ni ancho propio, pegada o lejos según la longitud, y solapada con el botón.
- **Fix propuesto:** dar a la etiqueta un hueco propio y alineado (p. ej. ancho fijo o su propia línea bajo los metadatos) con `Arrangement.spacedBy` y `maxLines` acotado, garantizando que nunca pise el botón; conservar el color de BUG-020 (GOOD verde, TIGHT ámbar, TOO_BIG rojo). Los umbrales de `HfApi.fit()` no se tocan.
- **Archivos:** `ui/modelmanager/HfBrowserScreen.kt`.


---

### BUG-022

**✅ "Exportar logs" tras un crash no incluye el crash (solo la sesión reabierta)**

- **Fix aplicado (compilado, pendiente de re-prueba en teléfono — 5.5b):** sin tocar JNI, `LogExporter.buildReport` lee `logcat -b crash -d -t 6000`, filtra por `com.box.gallery` y conserva ±5/60 líneas alrededor de cada coincidencia (la tombstone no repite el paquete en el backtrace); lo adjunta como "Crash nativo (logcat -b crash)". El buffer del sistema persiste entre reinicios del proceso. `last_crash.txt` sigue capturando excepciones Java. Archivo: `data/characters/LogExporter.kt`.
- **Ampliación (2026-11-19):** una app normal no puede leer `logcat -b crash` de forma fiable (`READ_LOGS` es privilegiado), así que el reporte incluye una **segunda fuente sin permisos**: `ActivityManager.getHistoricalProcessExitReasons()` (`ApplicationExitInfo`). Se listan las últimas 8 salidas del proceso **excluyendo solo las normales** (`REASON_EXIT_SELF`, `REASON_UNKNOWN`): así aparecen tanto los crashes (`CRASH_NATIVE`, `CRASH`, `SIGNALED`, `ANR`, `LOW_MEMORY`) como un **cierre forzado desde el sistema** (`USER_REQUESTED` / `USER_STOPPED`), que es justo lo que no se veía antes. Cada entrada lleva hora, motivo, estado, pid, proceso y descripción, más la traza (`getTraceInputStream`) en Android 12+. Todo con comprobación de versión y `runCatching`; el apartado se titula "Crash nativo (ApplicationExitInfo)". No necesita el protobuf del tombstone.
- **✅ Verificado en el teléfono (2026-11-19 09:36):** el reporte incluye la sección y detecta el cierre forzado: `2026-11-19 09:02:03 · USER_REQUESTED (cierre forzado) · pid=7430 · descripción: [FORCE STOP] stop com.box.gallery due to from process:com.miui.securitycenter`. También salieron dos muertes por `MiuiMemoryService` y tres por `OneKeyClean` que no se conocían.
- **Verificado con los dos reportes del 17/11 (15:26 y 15:30):** el apartado "Crash nativo (logcat -b crash)" aparece y trae `SIGSEGV` de `libllama.so` (16/11 16:54), el `SIGABRT` del callback del TTS (13:36/13:37) y el `SIGABRT` de Scudo al cerrar GGUF (15:23, BUG-030). También se ve el `ClassCastException` de BUG-025 y un `IllegalArgumentException` de clave duplicada en `LazyColumn` (16/11 22:34, sin frames de la app en el stack; no se abre bug nuevo sin más datos).
- **Síntoma (Bloque 5, prueba 5.5 · POCO):** tras un crash, el reporte exportado solo trae los logs de la app reabierta; el crash no aparece.
- **Causa verificada:** el capturador Java existe y está conectado (`LogExporter.installCrashRecorder` en `GalleryApplication.kt:35`, escribe `last_crash.txt` que `buildReport` adjunta) — pero solo captura **excepciones Java** (`Thread.setDefaultUncaughtExceptionHandler`). Los crashes reales del proyecto son **nativos** (SIGSEGV en `libllama.so`/BUG-001 y `liblitertlm_jni.so`/BUG-021): el proceso muere por señal sin pasar por el handler de Java → `last_crash.txt` nunca se escribe.
- **Fix propuesto:** captura de señales nativas (handler de SIGSEGV/SIGABRT que vuelque `last_crash.txt`, vía JNI propio o librería tipo xCrash) y/o al arrancar, recuperar el logcat/dropbox del proceso anterior y adjuntarlo al reporte.
- **Archivos:** `data/characters/LogExporter.kt`, `com/box/gallery/GalleryApplication.kt` (+ JNI si va por señales).


---

### BUG-023

**✅ Copia de seguridad: se exporta, pero no hay forma de importarla**

- **Pendiente (2026-11-19, 5.6b):** restaura bien pero duplica en vez de sobrescribir y solo cubre personajes → ver BUG-038.
- **Fix aplicado (2026-11-19):** tarjeta "Restaurar copia" en `ui/home/BoxSettingsScreen.kt` con `OpenDocument` (`.zip`, `.json`, `.png`) y `RoleplayViewModel.importFromSettings()`, que usa el importador existente y muestra el resultado con estado propio de Ajustes. Duplica por defecto (ids nuevos); el texto solo promete personajes, que es lo que contiene el .zip. Compilado.
- **✅ Verificado en el teléfono (2026-11-19, 5.6b):** "Restaurar copia" abrió el selector del sistema (Android DocumentsUI); al elegir el `.zip` exportado apareció "Se restauraron 4 personajes" y la biblioteca pasó de 4 a 8 fichas. Los avatares se recrearon con ids nuevos (3 PNG nuevos de los mismos tamaños) y se actualizó `box_chat.db-wal`. Los 4 duplicados de prueba se borraron después, quedando los originales.
- **Síntoma (Bloque 5, prueba 5.6 · POCO):** la exportación .zip funciona (comparte el archivo), pero no existe UI para **restaurar** esa copia.
- **Contexto verificado (2026-11-19):** la exportación vive en `ui/home/BoxSettingsScreen.kt:83-103` (`RoleplayViewModel.backupZip()` → `CharacterRepository.exportBackupZip()`), y el .zip contiene **solo personajes** (`NNN-nombre.json` chara_card_v2 + `NNN-nombre.avatar.png`); **no incluye chats, escenas ni personas** (la línea anterior decía "+ chats": era incorrecta). Además el backend de restauración **ya existe**: `CharacterRepository.importAnyFromUri()` detecta el .zip, empareja cada `.json` con su `.avatar.png` e importa todos los personajes; `RoleplayViewModel.import(uri)` lo expone y avisa cuántos se importaron. Lo que falta es **solo la UI**.
- **Fix propuesto:** tarjeta "Restaurar copia" en Configuración con `OpenDocument` (`application/zip`, `application/json`, `image/png`) que llame al importador y muestre el resultado. Ojo: el mensaje de `RoleplayViewModel.import` lo consume la biblioteca de personajes, así que Configuración debe llevar su **propio** `status`. `importFromBytes` siempre genera un id nuevo: decidir con el humano si sobrescribir por nombre o dejar duplicados (por defecto, duplicar es lo seguro).
- **Archivos:** `ui/home/BoxSettingsScreen.kt`, `data/characters/CharacterRepository.kt`, `ui/roleplay/RoleplayViewModel.kt`.


---

### BUG-012

**⚠️ Vigilancia: thermal throttling (no reprodujo en re-prueba)**

- **Síntoma original:** personaje "ligero" tardaba 7+ min; prefill ~5 t/s con prompts de ~750 tok (ficha grande de Tohru), gen 2.3–3.7 t/s. El teléfono se throttled.
- **Actualización (2026-11-16):** en re-prueba con el teléfono frío **NO reprodujo** — 2.1 OK en GGUF y LiteRT con **gen 4.8 t/s · prompt 6 t/s** (118 tok, 98 tok, 40.3 s, contexto 321/4096). Era térmico situacional, no bug de código. Baja a vigilancia.
- **Mejoras opcionales (si vuelve a pasar):** recortar/limitar descripción+personalidad al construir el prompt (p. ej. máx. caracteres por campo), indicador de progreso de prefill, aviso térmico, y evaluar el watchdog de LiteRT ("modelo tardó demasiado").
- **Archivos:** `data/characters/CharacterPromptBuilder.kt`, `engine/LlamaCppEngine.kt`, `runtime/LlamaCppModelHelper.kt`.


---

### BUG-024

**ℹ️ Mejora: la generación se pausa en segundo plano (no es crash ni pierde datos)**

- **Síntoma (Bloque 5, prueba 5.3 · POCO):** GGUF: al apagar pantalla o cambiar de app la generación se **pausa** y reanuda al volver (sin perder nada). LiteRT: los turnos cortos terminan; los largos también se pausan. No hay crash ni corrupción.
- **Explicación:** es el congelado de apps en caché de Android (cached app freezer) más el agresivo gestor de HyperOS. Comportamiento aceptable; no rompe nada.
- **Fix opcional (si se quiere generar de verdad en segundo plano):** foreground service con notificación mientras genera (permiso `FOREGROUND_SERVICE` + servicio) y mantener el motor fuera del ciclo de la Activity (refuerza BUG-021).
- **Archivos:** `Android/src/app/src/main/AndroidManifest.xml`, `runtime/`, `com/box/gallery/MainActivity.kt`.

---

