# 🐞 Inventario de bugs — Box RolePlay

> **Documento vivo.** Un bug = una entrada. Al arreglar uno: marca ✅, fecha, y añade una entrada en [`CAMBIOS.md`](CAMBIOS.md) con los archivos tocados.
> Origen: pruebas del Bloque 2 (2026-11-16, POCO duchamp, HyperOS 3.0.9.0, Android 15 — fingerprint del crash) y de los Bloques 4 y 5 (2026-11-17, Xiaomi 2311DRK48G / POCO duchamp).

---

## P0 — Críticos (bloquean el roleplay)

### BUG-001 · ✅ Crash nativo GGUF al deslizar la ventana de contexto *(arreglado 2026-11-17)*
- **Fix aplicado (compilado, pendiente de re-prueba en teléfono — 2.4):** plantilla de turnos de Gemma 4 construida en Kotlin (`engine/GgufChatTemplate.kt`, nueva): el prompt llega al nativo ya formateado y `startCompletion` lo tokeniza tal cual, **saltándose `_applyContextWindow()` y el formateo nativo** (la ruta exacta del SIGSEGV). Además: no se siembran ni envían cadenas vacías al tokenizador (`LlamaCppEngine.seedConversation` filtra vacíos; `LlamaCppModelHelper.runInference` rechaza mensaje vacío sin imágenes) y el recorte del historial sigue haciéndolo la app (ContextWindowPlanner) antes de sembrar el turno. Arreglado junto con BUG-015 (misma zona). Los GGUF que no son Gemma 4 siguen la ruta nativa de siempre.
- **Síntoma:** la app muere y la sesión se re-inicializa sola ("llama.cpp engine not initialized for …" + "Session re-initialized"). Pasó en escena y en chat largo.
- **Stack (resumen):** `SIGSEGV` en `llama_vocab::impl::tokenize` ← `common_tokenize` ← `LLMInference::_applyContextWindow()` ← `LLMInference::startCompletion` ← JNI `SmolLM.startCompletion`. Libs: `libllama.so` + `libsmollm_v8_4_fp16_dotprod_i8mm_sve.so` del APK.
- **Repro:** escena con prompt grande (contexto 25 %→34 %) enviando mensaje; también al "Absorber clústeres".
- **Causa probable:** `_applyContextWindow()` re-tokeniza el historial al deslizar la ventana (Fase 4) y el `tokenize` nativo crashea (vocab inválido / cadena vacía / mismatch entre la build de llama.cpp del submódulo y el JNI de SmolLM).
- **Archivos:** `engine/LlamaCppEngine.kt` (sembrar historial y deslizamiento), `runtime/LlamaCppModelHelper.kt`, submódulo `Android/src/llama.cpp` (no editar a la ligera).
- **Mitigación en Kotlin mientras tanto:** no dejar que el prompt llegue al límite (recortar historial antes de `startCompletion`), evitar tokenizar cadenas vacías, y limitar el tamaño del prompt de absorción.

### BUG-002 · ✅ Absorber clústeres: crash con GGUF / timeout con LiteRT *(arreglado 2026-11-17)*
- **Fix aplicado (compilado, pendiente de re-prueba en teléfono — 2.5):** absorción por tandas (`MemoryAbsorbPrompt.tandas`, máx. 3500 caracteres y 8 tandas; se descartan las más viejas). `MemoryAbsorber.run` acepta varios prompts, resetea la conversación por tanda y aplica watchdog **por tanda** (180 s) con progreso "Absorbiendo tanda X de Y…". Cada tanda produce su propio recuerdo; las notas de una tanda sin resumen útil no se borran. Archivos: `ui/memory/MemoryAbsorber.kt`, `ui/memory/MemoryScreen.kt` (MemoryViewModel), `data/characters/RawMemory.kt` (tandas), tests en `RawMemoryTest.kt`.
- **Síntoma:** con GGUF crashea (mismo stack de BUG-001). Con LiteRT: mensaje "modelo tardó demasiado".
- **Repro:** Memoria → personaje → "Absorber clústeres" con muchas notas.
- **Causa probable:** el prompt de absorción (todas las notas juntas) es enorme → desliza ventana (crash) o agota un watchdog de tiempo.
- **Archivos:** `data/characters/MemoryAbsorber.kt`, `ui/memory/MemoryScreen.kt`.
- **Fix propuesto:** absorber en tandas (chunking) o recortar notas al tamaño del contexto; quitar/ajustar el watchdog para LiteRT; avisar progreso.

## P1 — Correctitud del roleplay

### BUG-013 · ❌ Descarga HF del modelo de voz: nunca encuentra carpetas (bloquea el Bloque 3)
- **Síntoma:** en "Ajustes de voz → Descargar de Hugging Face", con cualquier repo (incluido el por defecto `KevinAHM/pocket-tts-onnx`), sale siempre "No encontré ninguna carpata con un modelo Pocket TTS completo en ese repositorio" y "Descargar" queda deshabilitado (captura 17:06).
- **Causa raíz localizada:** `HfApi.parseFiles()` (`data/characters/HfApi.kt`, línea ~74) **solo devuelve archivos `.gguf`/`.litertlm`** — es el filtro del navegador de modelos LLM. `HfClient.files()` lo usa tal cual, así que el diálogo de voz recibe vacío antes de llegar a `PocketHfSelect.variants()`. El parseo del enlace pegado funciona bien (`parseRepoId` acepta URLs completas).
- **Fix:** añadir un parseo del árbol sin filtro de extensión (p. ej. `parseTreeFiles()` o parámetro `extensions`) para el TTS, dejando `parseFiles` como está para el navegador LLM.
- ⚠️ **Trampa al arreglarlo:** NO dejar `KevinAHM/pocket-tts-onnx` como repo por defecto. Sus bundles v2 exigen insertar `bos_before_voice.npy` antes del embedding de voz y sherpa-onnx 2.0.1 no lo hace: el audio sale **ruido o corta a los 0,2 s** (documentado por haex-space). Usar un repo ya compatible con sherpa-onnx: `haex-space/pocket-tts-sherpa-onnx` (carpetas `de/en/es/fr/it/pt` con nombres v1 y BOS ya integrado en `encoder.onnx`) o el release oficial k2-fsa `sherpa-onnx-pocket-tts-int8-2026-01-26` (inglés).
- **Archivos:** `data/characters/HfApi.kt` (`parseFiles`), `ui/roleplay/TtsModelDownloadDialog.kt` (`DEFAULT_REPO`), `tts/PocketHfSelect.kt` (revisar al tocar).
- **Actualización (commit `2cec7db`, Claude — revisado por OpenCode):** añade soporte de repos con `tokenizer.json` (Unigram de HF) además de `tokenizer.model`, y la selección prefiere int8 sin bajar variantes q4 (`PocketHfSelect.kt`, `SentencePieceModel.kt` → `parseHfJson()`, `TtsModelDownloadDialog.kt`), con 8 tests unitarios que pasan. **NO toca el filtro de `parseFiles`**: la causa raíz sigue viva y en el teléfono la búsqueda seguirá dando "No encontré ninguna carpeta…" con cualquier repo. Los tests pasan porque alimentan listas de archivos directamente a la lógica de selección, sin pasar por la capa de red donde vive el filtro.

### BUG-014 · ❌ Importar carpeta del modelo de voz: solo acepta nombres antiguos (bloquea el Bloque 3)
- **Síntoma:** importas una carpeta con nombres v2 (`flow_lm_flow.onnx`, `flow_lm_main.onnx`, `mimi_*`, `tokenizer.model`, `bundle.json` — la del repo de KevinAHM) → toast "No se encontraron archivos de Pocket TTS en esa carpeta" y el panel sigue mostrando "Faltan: lm_flow.onnx, lm_main.onnx, encoder.onnx, decoder.onnx, vocab.json, token_scores.json" (capturas 17:07–17:08).
- **Causa:** `PocketModelFiles.isPart()/missing()` (`tts/PocketModelFiles.kt`) solo entienden nombres v1 y `TtsFiles.importModelFolder()` filtra con `isPart` → la carpeta se descarta entera. Inconsistente con `PocketHfSelect` (que sí entiende v2) y con el propio diálogo de descarga (que convierte `tokenizer.model` → `vocab.json`/`token_scores.json` con `SentencePieceModel`).
- **Fix recomendado:** no dar soporte v2 crudo a la importación (aunque se renombrara, los bundles KevinAHM necesitan el fix BOS de BUG-013); en su lugar, apuntar a repos compatibles sherpa-onnx y **mejorar el mensaje de error** de la importación con la lista exacta de archivos esperados (hoy es genérico y confunde).
- **Archivos:** `tts/PocketModelFiles.kt`, `tts/TtsFiles.kt`, `ui/roleplay/TtsSettingsDialog.kt` (mensaje y texto de ayuda).

### BUG-003 · ❌ Escenas: la instrucción "(Next message: write only as X.)" se envía como mensaje visible
- **Síntoma:** aparece como burbuja tuya verde en el chat (captura 16:22). En **Layla esa instrucción va oculta** (se inyecta al prompt sin crear mensaje) — es el comportamiento esperado.
- **Archivos:** `ui/llmchat/LlmChatScreen.kt` (línea ~362 crea el mensaje visible; ~227 lógica del speaker), `data/characters/ScenePromptBuilder.kt` (`turnInstruction()` ya la genera; falta inyectarla oculta).
- **Fix:** inyectar la instrucción al prompt sin añadir mensaje a la conversación (o añadirlo marcado `hidden` que no persiste ni renderiza).

### BUG-004 · ❌ Escenas: la IA habla por TODOS los personajes pese a "write only as X"
- **Síntoma:** pediste "write only as Akiko" y respondió Narrator + KIP + Akiko (captura 16:22).
- **Causa:** el system prompt ya lo prohíbe (`ScenePromptBuilder.build()` líneas 52-58) pero el modelo lo ignora; la instrucción de turno es débil y encima visible (BUG-003).
- **Fix propuesto:** al construir el prompt del turno, añadir restricción fuerte ("This turn: write ONLY as $X. One block. No other characters, no narrator.") y/o filtrar en post-procesado las líneas `Nombre:` prohibidas.
- **Archivos:** `data/characters/ScenePromptBuilder.kt`, `ui/llmchat/LlmChatScreen.kt`.

### BUG-005 · ❌ Escenas: la lista no se refresca al guardar
- **Síntoma:** guardas el escenario y no aparece; hay que salir al inicio y volver.
- **Archivos:** pantalla/editor de escenas en `ui/roleplay/` (lista debe observar el Flow de Room o recargar al volver del editor).

### BUG-006 · ❌ Escenas: no se ven las etiquetas existentes
- **Síntoma:** al crear/editar no hay chips de etiquetas como en Personajes; te olvidas de qué tags existen.
- **Fix:** fila de chips de etiquetas (selección + creación) igual que en el editor de personajes.
- **Archivos:** `ui/roleplay/` (editor de escenas; copiar patrón del editor de personajes).

### BUG-015 · ✅ GGUF: el prompt se envía sin plantilla de turnos (calidad inferior a Layla) *(arreglado 2026-11-17)*
- **Síntoma:** con el MISMO modelo GGUF (gemma-4-E4B PURE), mismo contexto (4096) y mismos parámetros, Box escribe peor que Layla: pregunta "cómo prefieres que te llame" pese a que la persona ("The User is Héctor") está en el prompt; seguimiento del personaje más flojo (capturas 20:49 Layla vs 20:50 Box). El usuario confirma que el problema solo se aprecia en GGUF.
- **Evidencia (dumps de prompt):** Layla envuelve el contenido con los marcadores de turno del modelo (`<|turn>system … <turn|>`, `<|turn>user`, `<|turn>model`). Box lo manda como **texto crudo**, sin estructura de turnos. Gemma depende fuertemente de su chat template: sin turnos, el seguimiento de instrucciones se degrada notablemente.
- **Diferencia secundaria:** Layla inyecta además "remembers previously: …" (resumen de memoria) dentro del system; en el ejemplo de Box la memoria estaba a 0 — no es parte de este bug, pero alimenta la comparación.
- **Causa probable (verificada en código):** `LlamaCppEngine.seedConversation()` (línea ~235) solo llama `instance.addSystemPrompt()` / `instance.addChatMessage()` del JNI SmolLM (`com.jegly.offlineLLM.smollm`); el wrapper nativo concatena sin aplicar la plantilla de chat del modelo (no hay jinja ni `llama_chat_apply_template` en la app).
- **Fix aplicado (compilado, pendiente de re-prueba en teléfono — 2.1b), vía Kotlin como se pedía:** nuevo `engine/GgufChatTemplate.kt` que construye el prompt con la plantilla canónica de Gemma 4 (`<|turn>system/user/model … <turn|>` + `<|turn>model\n` final, turno de usuario vacío si el saludo del personaje abre el historial, e inyección de `<|think|>` con thinking), replicando el formato de `chatfmt::formatGemma4` del nativo. `LlamaCppModelHelper.initialize` la activa cuando el nombre del modelo es de la familia Gemma 4 (mismos patrones que `ThinkingSplitter`). El nativo tokeniza el prompt tal cual. Incluye tests (`engine/GgufChatTemplateTest.kt`). No hizo falta tocar el JNI ni `llama_chat_apply_template` (vía nativa descartada; el nativo mantiene su ruta para otros GGUF). Arreglado de forma coordinada con BUG-001 (misma zona, mismo mecanismo).
- **Fix propuesto:** aplicar la plantilla según la familia del modelo antes de `startCompletion` — opción A en Kotlin (envolver system/historial/user con los turnos de Gemma `<start_of_turn>`, `<bos>`, etc., leyendo la plantilla del GGUF o detectando la familia); opción B en nativo con `llama_chat_apply_template` (más correcto, toca JNI del submódulo). Coordinar con BUG-001: el recorte de `_applyContextWindow` debe respetar los turnos al deslizar la ventana.
- **Archivos:** `engine/LlamaCppEngine.kt` (`seedConversation`, `generateResponse`), `runtime/LlamaCppModelHelper.kt`, submódulo `Android/src/llama.cpp` / JNI SmolLM (solo si va por la vía nativa).
- **Repro:** mismo personaje + persona activa, mismo GGUF a 4096: "Hola, preséntate y dime mi nombre" → Box no usa el nombre; Layla sí.

### BUG-016 · ❌ Cambiar de modelo: el primer mensaje a veces falla "engine not initialized" y re-inicia la sesión
- **Síntoma (Bloque 4, prueba 4.2):** al cambiar de modelo GGUF/LiteRT, el primer mensaje de un chat nuevo a veces responde "llama.cpp engine not initialized for …" seguido del aviso "Session re-initialized"; al reenviar la misma pregunta, carga y funciona. La carga del modelo en sí está bien.
- **Cadena verificada en código:** `runInference()` (`runtime/LlamaCppModelHelper.kt:200-203`) falla en seco si `engines[model.name]` no existe o `isModelLoaded == false`; ese error entra a `handleError()` (`ui/llmchat/LlmChatViewModel.kt:907-939`), que muestra el error, hace `cleanupModel` + `initializeModel` y añade el aviso "Session re-initialized". Es decir: el envío llega antes de que termine de inicializarse el motor recién seleccionado (carrera).
- **¿Es arreglable? Sí.** Dos vías complementarias: (A) en el ViewModel, no enviar mientras el modelo seleccionado está inicializando — deshabilitar enviar / mostrar "cargando modelo…" y encolar el primer mensaje hasta `isModelLoaded`; (B) en `runInference`, en vez de fallar al primer intento, esperar la inicialización con un timeout razonable antes de reportar error.
- **Archivos:** `runtime/LlamaCppModelHelper.kt` (`runInference`), `ui/llmchat/LlmChatViewModel.kt` (`handleError`, flujo de envío).

### BUG-021 · ✅ Rotar la pantalla recarga el modelo (GGUF y LiteRT) y, si está generando, crash nativo *(arreglado 2026-11-17)*
- **Fix aplicado (compilado, pendiente de re-prueba en teléfono — 5.4b):** (a) `AndroidManifest.xml`: `configChanges="uiMode|orientation|screenSize"` → la rotación ya no destruye la Activity ni recarga el modelo; (b) al destruir/liberar el motor se cancela la generación y se espera al hilo nativo ANTES de cerrar: GGUF en `LlamaCppEngine.unloadModel`/`resetConversation` (espera + cierre en un hilo aparte, acotado a 60 s, y cierre sobre la instancia capturada) y LiteRT en `LlmChatModelHelper.cleanUp` (cancela, espera máx. 5 s al callback de fin y solo entonces cierra). (c) Verificado: el motor GGUF vive en el singleton `LlamaCppModelHelper` (proceso) y el de LiteRT en los `Model`, fuera del ciclo de la Activity.
- **Síntoma (Bloque 5, prueba 5.4 · POCO duchamp):** al rotar, el modelo se recarga entero en **ambos** motores (no debería pasar). Si rota **mientras está escribiendo**, la app crashea: `SIGSEGV` en `liblitertlm_jni.so` (LiteRT; 4 frames del JNI + pthread, sin frames de la app).
- **Causa verificada:** `AndroidManifest.xml` (línea ~78) declara solo `android:configChanges="uiMode"` → la rotación **destruye y recrea la Activity**, y con ella se re-inicializa el motor (por eso recarga). Si había generación en curso, el hilo nativo queda en marcha mientras se desmonta todo → SIGSEGV.
- **Fix propuesto:** (a) añadir `orientation|screenSize` a `configChanges` (elimina de golpe la recarga y el crash); (b) además, al destruir, **cancelar la generación y esperar al hilo** antes de tocar/liberar el motor (refuerzo, igual para BUG-001); (c) verificar que los motores viven en scope Application/ViewModel, no Activity.
- **Archivos:** `Android/src/app/src/main/AndroidManifest.xml`, `com/box/gallery/MainActivity.kt`, `runtime/LlamaCppModelHelper.kt`, helper LiteRT de `runtime/`.

## P2 — UX

### BUG-007 · ❌ Ficha: "Compartir" genera un texto no guardable
- **Síntoma:** diálogo de texto sin opción de guardar/archivo.
- **Fix:** compartir de verdad: `ACTION_SEND` con texto plano o exportar `.json` a caché + `FileProvider` (como hace la copia de seguridad .zip).
- **Archivos:** `ui/roleplay/CharacterDetailSheet.kt`, `data/characters/CharacterRepository.kt`.

### BUG-008 · ❌ Memoria: los recuerdos "sin procesar" no se pueden editar
- **Síntoma:** solo se pueden borrar; editar no funciona (los procesados sí, verificar).
- **Archivos:** `ui/memory/MemoryScreen.kt`, `data/characters/RawMemory.kt`.

### BUG-009 · ❌ Cajón: la hamburguesa del inicio no abre el cajón (solo el swipe)
- **Archivos:** `ui/home/BoxHomeScreen.kt` (conectar botón al estado del `ModalNavigationDrawer`).

### BUG-010 · ⚠️ Cajón: chats de personajes borrados salen como "Personaje borrado"
- **Decisión pendiente:** renombrar a "Sin personaje" u ocultarlos/moverlos a "Chat libre".
- **Archivos:** `ui/home/` (cajón) + `data/local/` (consulta de chats agrupados).

### BUG-011 · ⚠️ Macros `{user}`/`{{char}}` sin sustituir en fichas y previews
- **Síntoma:** "Psicólogo Digital" muestra `{{char}}`/`{{user}}` crudos en la biblioteca; Tohru muestra `{user}` crudo en la ficha.
- **Verificar también:** que el prompt final SÍ las sustituya (y soportar ambas sintaxis y alias de tarjeta tipo SillyTavern).
- **Archivos:** `data/characters/CharacterPromptBuilder.kt` (`applyMacros`), `ui/roleplay/CharacterDetailSheet.kt`, `ui/roleplay/CharacterLibrary.kt`.

### BUG-018 · ⚠️ Presets de motor: no guardan rendimiento (hilos, batch, flash, KV, visión)
- **Síntoma (Bloque 4, prueba 4.4):** el preset guarda modelo, mmproj, persona y muestreo, pero NO los ajustes de rendimiento: al aplicarlo no se restauran hilos, batch, flash attention, KV ni el lado máximo de imagen.
- **Causa verificada:** `data/characters/EnginePreset.kt` solo serializa `id/name/model/mmproj/personaId/samplerMode/samplerCustom` — el rendimiento vive en `EnginePerfStore` y no se incluye en el preset.
- **Fix:** añadir los campos de rendimiento a `EnginePreset` (hilos, batch, flash, KV, imageMaxSide) con valores por defecto (migración tolerante) y aplicarlos al cargar el preset.
- **Archivos:** `data/characters/EnginePreset.kt`, `data/characters/EnginePerfStore.kt`, `ui/modelmanager/EnginePerfDialog.kt`.

### BUG-020 · ⚠️ Navegador HF: las etiquetas de holgura se ven todas en el mismo color
- **Síntoma (Bloque 4, prueba 4.1):** "cabe bien" / "justo" / "demasiado grande" aparecen en el mismo gris; no se distinguen de un vistazo. Además el umbral de "justo" resulta conservador (aparece con margen real de sobra).
- **Causa verificada:** `ui/modelmanager/HfBrowserScreen.kt:142-150` pinta el label siempre con `onSurfaceVariant`, sin depender del nivel. Umbrales en `HfApi.fit()` (`data/characters/HfApi.kt:93-98`): GOOD ≤ 45 % de la RAM, TIGHT ≤ 65 %, TOO_BIG el resto.
- **Fix:** colorear según `HfFit` (GOOD verde/terciario, TIGHT ámbar, TOO_BIG error) y, si acaso, recalibrar umbrales con datos reales (POCO: 11 GB RAM).
- **Archivos:** `ui/modelmanager/HfBrowserScreen.kt`, `data/characters/HfApi.kt`.

### BUG-022 · ⚠️ "Exportar logs" tras un crash no incluye el crash (solo la sesión reabierta)
- **Síntoma (Bloque 5, prueba 5.5 · POCO):** tras un crash, el reporte exportado solo trae los logs de la app reabierta; el crash no aparece.
- **Causa verificada:** el capturador Java existe y está conectado (`LogExporter.installCrashRecorder` en `GalleryApplication.kt:35`, escribe `last_crash.txt` que `buildReport` adjunta) — pero solo captura **excepciones Java** (`Thread.setDefaultUncaughtExceptionHandler`). Los crashes reales del proyecto son **nativos** (SIGSEGV en `libllama.so`/BUG-001 y `liblitertlm_jni.so`/BUG-021): el proceso muere por señal sin pasar por el handler de Java → `last_crash.txt` nunca se escribe.
- **Fix propuesto:** captura de señales nativas (handler de SIGSEGV/SIGABRT que vuelque `last_crash.txt`, vía JNI propio o librería tipo xCrash) y/o al arrancar, recuperar el logcat/dropbox del proceso anterior y adjuntarlo al reporte.
- **Archivos:** `data/characters/LogExporter.kt`, `com/box/gallery/GalleryApplication.kt` (+ JNI si va por señales).

### BUG-023 · ⚠️ Copia de seguridad: se exporta, pero no hay forma de importarla
- **Síntoma (Bloque 5, prueba 5.6 · POCO):** la exportación .zip funciona (comparte el archivo), pero no existe UI para **restaurar** esa copia.
- **Contexto verificado:** la exportación vive en `ui/home/BoxSettingsScreen.kt:83-103` (`backupZip()` + share). Hay importación de **personaje individual** (`data/characters/CharacterRepository.kt:42-135`, `importFromUri`/`importFromBytes`), pero ninguna ruta restaura la copia completa (varios personajes + avatares + chats).
- **Fix propuesto:** botón "Restaurar copia" que pida el .zip y recorra sus personajes vía el importador existente, eligiendo sobrescribir u omitir si ya existe.
- **Archivos:** `ui/home/BoxSettingsScreen.kt`, `data/characters/CharacterRepository.kt`, `ui/roleplay/RoleplayViewModel.kt`.

## P3 — Rendimiento

### BUG-012 · ⚠️ Vigilancia: thermal throttling (no reprodujo en re-prueba)
- **Síntoma original:** personaje "ligero" tardaba 7+ min; prefill ~5 t/s con prompts de ~750 tok (ficha grande de Tohru), gen 2.3–3.7 t/s. El teléfono se throttled.
- **Actualización (2026-11-16):** en re-prueba con el teléfono frío **NO reprodujo** — 2.1 OK en GGUF y LiteRT con **gen 4.8 t/s · prompt 6 t/s** (118 tok, 98 tok, 40.3 s, contexto 321/4096). Era térmico situacional, no bug de código. Baja a vigilancia.
- **Mejoras opcionales (si vuelve a pasar):** recortar/limitar descripción+personalidad al construir el prompt (p. ej. máx. caracteres por campo), indicador de progreso de prefill, aviso térmico, y evaluar el watchdog de LiteRT ("modelo tardó demasiado").
- **Archivos:** `data/characters/CharacterPromptBuilder.kt`, `engine/LlamaCppEngine.kt`, `runtime/LlamaCppModelHelper.kt`.

### BUG-017 · ⚠️ Visión mmproj: muy lenta (~4 min con lado 256; 512 peor)
- **Síntoma (Bloque 4, prueba 4.3):** describir una imagen funciona, pero tarda ~4 minutos con `imageMaxSide` 256; con 512 aún peor. Inutilizable para chat fluido.
- **Contexto de código:** `runInference` antepone `engine.queueImages(images, maxSide)` al prompt (`runtime/LlamaCppModelHelper.kt:206-212`); el lado viene de `EnginePerfStore` (por defecto 512).
- **Mejoras propuestas (parcial — puede haber coste inherente del encoder de visión en CPU):** redimensionado real del bitmap antes del encoder, hilos del codificador mtmd, y medir por fases (encode vs prefill) para localizar el cuello; valorar caché si se reenvía la misma imagen.
- **Archivos:** `engine/LlamaCppEngine.kt` (`queueImages`), `runtime/LlamaCppModelHelper.kt`, `data/characters/EnginePerfStore.kt`.

### BUG-019 · ⚠️ Benchmark LiteRT: métrica propia no comparable con GGUF
- **Síntoma (Bloque 4, prueba 4.5):** el benchmark mide en ambos motores, pero la medición de LiteRT usa otra base (métrica propia del runtime) y sus cifras no son comparables con las de GGUF. Menor; arreglar más adelante.
- **Fix propuesto:** unificar la medición (mismos prompts de prueba y misma fórmula tok/s) o etiquetar claramente que no son comparables.
- **Archivos:** app Benchmark de modelos (GGUF: `QuickBenchDialog.kt`) y la medición del runtime LiteRT.

### BUG-024 · ℹ️ Mejora: la generación se pausa en segundo plano (no es crash ni pierde datos)
- **Síntoma (Bloque 5, prueba 5.3 · POCO):** GGUF: al apagar pantalla o cambiar de app la generación se **pausa** y reanuda al volver (sin perder nada). LiteRT: los turnos cortos terminan; los largos también se pausan. No hay crash ni corrupción.
- **Explicación:** es el congelado de apps en caché de Android (cached app freezer) más el agresivo gestor de HyperOS. Comportamiento aceptable; no rompe nada.
- **Fix opcional (si se quiere generar de verdad en segundo plano):** foreground service con notificación mientras genera (permiso `FOREGROUND_SERVICE` + servicio) y mantener el motor fuera del ciclo de la Activity (refuerza BUG-021).
- **Archivos:** `Android/src/app/src/main/AndroidManifest.xml`, `runtime/`, `com/box/gallery/MainActivity.kt`.

---

## ✅ Lo que ya funciona (Bloque 2, 2026-11-16)

Ficha de personaje (stats, descripción, escenario) · **chat 1 a 1: persona usada, personaje correcto, sin hablar por ti (GGUF y LiteRT, re-prueba OK)** · grupos con chips y persistencia · escenas: crear/guardar/chatear con varios personajes y narrador · barra de escena SOLO en chats de escena (correcto) · memoria: botones, guardado y borrado de recuerdos · cajón por swipe con chats agrupados por personaje · contador de rendimiento en escenas (743 tok @ 5 t/s, gen 3.7 t/s).

## ✅ Lo que ya funciona (Bloque 4 — descargas y modelos, 2026-11-17 · Xiaomi)

Navegador HF: búsqueda, listado con holgura de RAM y errores limpios ("Hugging Face respondió 401", "Este repositorio no tiene archivos .gguf ni .litertlm"); pegar enlace lleva directo al selector de archivos (colores del label → BUG-020) · descarga y cambio de modelo GGUF/LiteRT chateando tras el cambio (carrera del primer mensaje → BUG-016) · visión mmproj funcional, muy lenta (BUG-017) · motor guardado por personaje (GGUF/LiteRT, visión, persona, muestreo) y **fallback al motor general de roleplay al borrar el modelo ligado** (presets sin rendimiento → BUG-018) · benchmark de velocidad en GGUF y LiteRT (métrica LiteRT no comparable → BUG-019). Esta tanda cubre el resto del Bloque 1 (LiteRT).

## ✅ Lo que ya funciona (Bloque 5 — robustez y regresión, 2026-11-17 · POCO)

Segundo plano 10+ min: GGUF y LiteRT aguantan otra app y pantalla apagada y siguen al volver · LiteRT termina los turnos cortos aunque apagues la pantalla · presets de muestreo: guardar con nombre, cargar y borrar con ✕ (falta un pequeño retoque gráfico) · apps sin modelo: "Texto a voz" y el resto abren su UI sin cargar nada · exportación de copia de seguridad .zip y de logs funcionan (importación y crashes nativos → BUG-023/BUG-022) · turno GGUF de referencia: **carga 3.5 s · prompt 105 tok @ 7.9 t/s · gen 210 tok @ 5.06 t/s**; LiteRT por contador: **8.6 frag/s · 1er token 3.1 s**.
