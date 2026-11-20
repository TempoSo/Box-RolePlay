# 🐞 Inventario de bugs — Box RolePlay

> **Triaje 2026-11-19:** 16 cerrados · 8 dudosos · 17 abiertos. Los cerrados se sustituyeron por una línea de referencia y su texto íntegro se conserva en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md); este documento sigue siendo el inventario de los activos; empieza por el § Orden sugerido.
>
> **Documento vivo.** Un bug = una entrada. Al arreglar uno: marca ✅, fecha, y añade una entrada en [`CAMBIOS.md`](CAMBIOS.md) con los archivos tocados.
> Origen: pruebas del Bloque 2 (2026-11-16, POCO duchamp, HyperOS 3.0.9.0, Android 15 — fingerprint del crash) y de los Bloques 4 y 5 (2026-11-17, Xiaomi 2311DRK48G / POCO duchamp).

---

## 🧭 Orden sugerido (más fácil → más difícil)

> **Regla:** trabaja **un escalón a la vez**. Si un ID ya está en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md), sáltalo y avisa. Los dudosos del Escalón 0 **no llevan parche**: solo re-prueba tuya o compilación.

| Escalón | IDs | Coste | Qué es |
|---|---|---|---|
| **0 · Verificación gratis** | 011, 015, 016, 021, 025, 026, 030, 037 | 0 parche | Arreglados en código; faltan tus re-pruebas (2.1b, 4.2b, 5.4b, Gradle…) |
| **1 · Rápidos** | 008, 009, 010, 014, 019, 040 | S | Causa localizada; decisión pequeña en 010 y 040 |
| **2 · Medianos con tests** | 007, 032–036, 038, 039, 041 | M–L | FileProvider, Tanda final de escenas, copia/Room, VoiceStore |
| **3 · Duros / nativos** | 017, 029 | L | Visión en CPU y stop strings en `LLMInference.cpp` |

> **Agrupaciones:** 007 y 039 comparten `RoleplayScreens.kt` → un solo commit. 032–036 comparten el flujo de escenas → una sola tanda. En 038 y 041 la parte «L» aparece solo si se decide incluir chats cifrados o grabación de audio.

---

## P0 — Críticos (bloquean el roleplay)

> **BUG-001** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-001).

> **BUG-002** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-002).

### BUG-030 · 🟡 Crash nativo al cerrar GGUF: Scudo "invalid chunk state" en `LLMInference::~LLMInference()` *(documentado 2026-11-17; mitigado en Kotlin 2026-11-18 — `SmolLM.close()` atómico, `unloadModel` espera carga y generación, acceso nativo serializado; pendiente de re-prueba en el teléfono)*
**Esfuerzo:** S · **Escalón:** 0 · Re-prueba de cierre/cambio de modelo

- **Síntoma (crash 15:23:26 del 17/11, presente en los dos reportes adjuntos):** `SIGABRT` en el hilo `DefaultDispatch` con `Abort message: 'Scudo ERROR: invalid chunk state when deallocating address 0x200007da8f0fad0'`. Backtrace: `abort` ← `scudo::Allocator::deallocate` ← `LLMInference::~LLMInference()` ← `Java_com_jegly_offlineLLM_smollm_SmolLM_close` ← `SmolLM.close` ← `LlamaCppEngine$unloadModel$1$1.invokeSuspend` ← coroutines. Mata la app al descargar/cambiar de modelo GGUF o al limpiar el motor.
- **Causa probable (verificada en Kotlin; el nativo no se ha tocado):** doble cierre o cierre con el motor aún en uso. `LlamaCppEngine.unloadModel()` (`engine/LlamaCppEngine.kt:163-187`) captura `inst = instance`, cancela `loadJob`/`generationJob` y llama a `inst.close()` en un `CoroutineScope(Default)` tras `withTimeoutOrNull(60_000) { gen?.join() }`: si el join expira, cierra mientras el hilo nativo sigue vivo. `resetConversation()` (`:194-241`) captura la **misma** `instance` y también la cierra en otro coroutine antes de asignar `instance = SmolLM()`, sin invalidarla antes; ambas vías pueden solaparse y cerrar dos veces el mismo `SmolLM`. `LlamaCppModelHelper.cleanUp()` (`:201-203`) saca el engine del mapa antes de llamar a `unloadModel`, de modo que una segunda limpieza no vuelve a entrar por esa vía.
- **Fix propuesto:** bajo `stateLock`, marcar la instancia como cerrada (o sustituirla por `null`/una nueva) **antes** de lanzar el cierre asíncrono, y dejar que `close()` lo ejecute solo la primera vía que la capturó; si agota el timeout esperando a generación/carga, **no cerrar** y dejar aviso en el log. Revisar `resetConversation` para que no cierre una instancia ya marcada. No tocar el submódulo salvo que el doble cierre persista.
- **Archivos:** `engine/LlamaCppEngine.kt`, `runtime/LlamaCppModelHelper.kt`; submódulo `Android/src/llama.cpp` solo si hace falta.

## P1 — Correctitud del roleplay

> **BUG-013** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-013).

### BUG-014 · ❌ Importar carpeta del modelo de voz: solo acepta nombres antiguos (bloquea el Bloque 3)
**Esfuerzo:** S · **Escalón:** 1 · Mejorar el mensaje de error con los archivos esperados

- **Síntoma:** importas una carpeta con nombres v2 (`flow_lm_flow.onnx`, `flow_lm_main.onnx`, `mimi_*`, `tokenizer.model`, `bundle.json` — la del repo de KevinAHM) → toast "No se encontraron archivos de Pocket TTS en esa carpeta" y el panel sigue mostrando "Faltan: lm_flow.onnx, lm_main.onnx, encoder.onnx, decoder.onnx, vocab.json, token_scores.json" (capturas 17:07–17:08).
- **Causa:** `PocketModelFiles.isPart()/missing()` (`tts/PocketModelFiles.kt`) solo entienden nombres v1 y `TtsFiles.importModelFolder()` filtra con `isPart` → la carpeta se descarta entera. Inconsistente con `PocketHfSelect` (que sí entiende v2) y con el propio diálogo de descarga (que convierte `tokenizer.model` → `vocab.json`/`token_scores.json` con `SentencePieceModel`).
- **Fix recomendado:** no dar soporte v2 crudo a la importación (aunque se renombrara, los bundles KevinAHM necesitan el fix BOS de BUG-013); en su lugar, apuntar a repos compatibles sherpa-onnx y **mejorar el mensaje de error** de la importación con la lista exacta de archivos esperados (hoy es genérico y confunde).
- **Archivos:** `tts/PocketModelFiles.kt`, `tts/TtsFiles.kt`, `ui/roleplay/TtsSettingsDialog.kt` (mensaje y texto de ayuda).

> **BUG-003** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-003).

> **BUG-004** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-004).

> **BUG-005** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-005).

> **BUG-006** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-006).

### BUG-015 · ✅ GGUF: el prompt se envía sin plantilla de turnos (calidad inferior a Layla) *(arreglado 2026-11-17)*
**Esfuerzo:** S · **Escalón:** 0 · Re-prueba 2.1b: calidad frente a Layla

- **Síntoma:** con el MISMO modelo GGUF (gemma-4-E4B PURE), mismo contexto (4096) y mismos parámetros, Box escribe peor que Layla: pregunta "cómo prefieres que te llame" pese a que la persona ("The User is Héctor") está en el prompt; seguimiento del personaje más flojo (capturas 20:49 Layla vs 20:50 Box). El usuario confirma que el problema solo se aprecia en GGUF.
- **Evidencia (dumps de prompt):** Layla envuelve el contenido con los marcadores de turno del modelo (`<|turn>system … <turn|>`, `<|turn>user`, `<|turn>model`). Box lo manda como **texto crudo**, sin estructura de turnos. Gemma depende fuertemente de su chat template: sin turnos, el seguimiento de instrucciones se degrada notablemente.
- **Diferencia secundaria:** Layla inyecta además "remembers previously: …" (resumen de memoria) dentro del system; en el ejemplo de Box la memoria estaba a 0 — no es parte de este bug, pero alimenta la comparación.
- **Causa probable (verificada en código):** `LlamaCppEngine.seedConversation()` (línea ~235) solo llama `instance.addSystemPrompt()` / `instance.addChatMessage()` del JNI SmolLM (`com.jegly.offlineLLM.smollm`); el wrapper nativo concatena sin aplicar la plantilla de chat del modelo (no hay jinja ni `llama_chat_apply_template` en la app).
- **Fix aplicado (compilado, pendiente de re-prueba en teléfono — 2.1b), vía Kotlin como se pedía:** nuevo `engine/GgufChatTemplate.kt` que construye el prompt con la plantilla canónica de Gemma 4 (`<|turn>system/user/model … <turn|>` + `<|turn>model\n` final, turno de usuario vacío si el saludo del personaje abre el historial, e inyección de `<|think|>` con thinking), replicando el formato de `chatfmt::formatGemma4` del nativo. `LlamaCppModelHelper.initialize` la activa cuando el nombre del modelo es de la familia Gemma 4 (mismos patrones que `ThinkingSplitter`). El nativo tokeniza el prompt tal cual. Incluye tests (`engine/GgufChatTemplateTest.kt`). No hizo falta tocar el JNI ni `llama_chat_apply_template` (vía nativa descartada; el nativo mantiene su ruta para otros GGUF). Arreglado de forma coordinada con BUG-001 (misma zona, mismo mecanismo).
- **Fix propuesto:** aplicar la plantilla según la familia del modelo antes de `startCompletion` — opción A en Kotlin (envolver system/historial/user con los turnos de Gemma `<start_of_turn>`, `<bos>`, etc., leyendo la plantilla del GGUF o detectando la familia); opción B en nativo con `llama_chat_apply_template` (más correcto, toca JNI del submódulo). Coordinar con BUG-001: el recorte de `_applyContextWindow` debe respetar los turnos al deslizar la ventana.
- **Archivos:** `engine/LlamaCppEngine.kt` (`seedConversation`, `generateResponse`), `runtime/LlamaCppModelHelper.kt`, submódulo `Android/src/llama.cpp` / JNI SmolLM (solo si va por la vía nativa).
- **Repro:** mismo personaje + persona activa, mismo GGUF a 4096: "Hola, preséntate y dime mi nombre" → Box no usa el nombre; Layla sí.

### BUG-016 · ✅ Cambiar de modelo: el primer mensaje a veces falla "engine not initialized" y re-inicia la sesión *(arreglado 2026-11-17)*
**Esfuerzo:** S · **Escalón:** 0 · Re-prueba 4.2b (el 17/11 se vio bien; casilla sin marcar)

- **Fix aplicado (compilado, pendiente de re-prueba en teléfono — 4.2b):** dos vías. (A) `LlmChatViewModel.generateResponse` encola el mensaje y espera `runtimeHelper.isModelReady(model)` (GGUF: motor cargado + instancia vigente) hasta 180 s, mostrando "⏳ cargando modelo…"; el error de timeout no dispara la reinicialización en `handleError`. (B) `LlamaCppModelHelper.runInference` espera hasta 60 s a que el motor exista y esté cargado antes de reportar error. `runAgain` ya no se queda con una instancia obsoleta. Archivos: `runtime/LlmModelHelper.kt`, `runtime/LlamaCppModelHelper.kt`, `ui/llmchat/LlmChatViewModel.kt`.
- **Síntoma (Bloque 4, prueba 4.2):** al cambiar de modelo GGUF/LiteRT, el primer mensaje de un chat nuevo a veces responde "llama.cpp engine not initialized for …" seguido del aviso "Session re-initialized"; al reenviar la misma pregunta, carga y funciona. La carga del modelo en sí está bien.
- **Cadena verificada en código:** `runInference()` (`runtime/LlamaCppModelHelper.kt:200-203`) falla en seco si `engines[model.name]` no existe o `isModelLoaded == false`; ese error entra a `handleError()` (`ui/llmchat/LlmChatViewModel.kt:907-939`), que muestra el error, hace `cleanupModel` + `initializeModel` y añade el aviso "Session re-initialized". Es decir: el envío llega antes de que termine de inicializarse el motor recién seleccionado (carrera).
- **¿Es arreglable? Sí.** Dos vías complementarias: (A) en el ViewModel, no enviar mientras el modelo seleccionado está inicializando — deshabilitar enviar / mostrar "cargando modelo…" y encolar el primer mensaje hasta `isModelLoaded`; (B) en `runInference`, en vez de fallar al primer intento, esperar la inicialización con un timeout razonable antes de reportar error.
- **Archivos:** `runtime/LlamaCppModelHelper.kt` (`runInference`), `ui/llmchat/LlmChatViewModel.kt` (`handleError`, flujo de envío).
- **Verificado por el usuario (17/11):** 4.2 funciona en **GGUF y LiteRT** al cambiar de modelo; no aparece "engine not initialized" ni "Session re-initialized".

### BUG-021 · ✅ Rotar la pantalla recarga el modelo (GGUF y LiteRT) y, si está generando, crash nativo *(arreglado 2026-11-17)*
**Esfuerzo:** S · **Escalón:** 0 · Re-prueba 5.4b: rotar con y sin generación

- **Fix aplicado (compilado, pendiente de re-prueba en teléfono — 5.4b):** (a) `AndroidManifest.xml`: `configChanges="uiMode|orientation|screenSize"` → la rotación ya no destruye la Activity ni recarga el modelo; (b) al destruir/liberar el motor se cancela la generación y se espera al hilo nativo ANTES de cerrar: GGUF en `LlamaCppEngine.unloadModel`/`resetConversation` (espera + cierre en un hilo aparte, acotado a 60 s, y cierre sobre la instancia capturada) y LiteRT en `LlmChatModelHelper.cleanUp` (cancela, espera máx. 5 s al callback de fin y solo entonces cierra). (c) Verificado: el motor GGUF vive en el singleton `LlamaCppModelHelper` (proceso) y el de LiteRT en los `Model`, fuera del ciclo de la Activity.
- **Síntoma (Bloque 5, prueba 5.4 · POCO duchamp):** al rotar, el modelo se recarga entero en **ambos** motores (no debería pasar). Si rota **mientras está escribiendo**, la app crashea: `SIGSEGV` en `liblitertlm_jni.so` (LiteRT; 4 frames del JNI + pthread, sin frames de la app).
- **Causa verificada:** `AndroidManifest.xml` (línea ~78) declara solo `android:configChanges="uiMode"` → la rotación **destruye y recrea la Activity**, y con ella se re-inicializa el motor (por eso recarga). Si había generación en curso, el hilo nativo queda en marcha mientras se desmonta todo → SIGSEGV.
- **Fix propuesto:** (a) añadir `orientation|screenSize` a `configChanges` (elimina de golpe la recarga y el crash); (b) además, al destruir, **cancelar la generación y esperar al hilo** antes de tocar/liberar el motor (refuerzo, igual para BUG-001); (c) verificar que los motores viven en scope Application/ViewModel, no Activity.
- **Archivos:** `Android/src/app/src/main/AndroidManifest.xml`, `com/box/gallery/MainActivity.kt`, `runtime/LlamaCppModelHelper.kt`, helper LiteRT de `runtime/`.

> **BUG-027** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-027).

### BUG-029 · ❌ GGUF: la respuesta escrita se queda corta ("no termina de hablar", como las fotos) *(documentado 2026-11-17; causa probable localizada)*
**Esfuerzo:** L · **Escalón:** 3 · Stop strings en `LLMInference.cpp` (C++, no es submódulo)

- **Síntoma (capturas 15:25 y 15:26 del 17/11, app "AI Chat", contexto 4096):** la generación para **sin error y sin rastro**, muy antes de cualquier tope: turno 1 con `gemma-4-E4B…PURE.gguf` corta a los **98 tok** (3.5 t/s, 40.7 s, contexto 5 % ~213/4096) justo tras "Piensa en estas preguntas conmigo:" — anuncia preguntas y no las escribe; turno 2 con `gemma-4-E…_K_M.gguf` corta a los **63 tok** (4.1 t/s, 47.9 s, contexto 8 % ~323/4096) tras anunciar "cuatro áreas clave" sin listarlas. Dos modelos distintos, ambos cortes **exactamente donde empezaría la lista/sección**.
- **Causa probable (verificada en el wrapper nativo; `Android/src/smollm` está trackeado en el repo, NO es submódulo):** en el bucle de generación (`Android/src/smollm/src/main/cpp/LLMInference.cpp:577-594`) la respuesta termina de forma silenciosa si el token muestreado es EOG (`llama_vocab_is_eog`, p. ej. el `<turn|>` de Gemma 4) o si el texto acumulado contiene un **stop string**: `<turn|`, `<|turn_end|>`, `<turn_end|>`, `<start_of_turn>`, `<end_of_turn>`, **`###`**, `System instruction:`. El motivo nunca llega a la app: `SmolLM.getResponseAsFlow` (`Android/src/smollm/src/main/java/com/jegly/offlineLLM/smollm/SmolLM.kt:256-263`) corta el flujo al recibir el `[EOG]` y no lo emite. Encaja con **`###`**: los Gemma 4 instruct abren cada sección con `### Título`; el nativo comprueba el stop con la pieza ya incluida y **descarta esa pieza**, así que en pantalla no queda ningún marcador y el texto termina justo antes del encabezado — exactamente el patrón de las dos capturas.
- **Descartados:** (a) tope de generación — `maxResponse` es global (`EnginePerfSettings` default `-1`, `LLMInference.h:67`) y dos modelos cortaron a 98 y 63; (b) los stops de la app en `LlamaCppEngine.kt:377-382` (`<|im_end|>`, `<|eot_id|>`, el token de fin de texto de GPT — línea 380, "GPT-style EOS" — y `<|im_start|>`; el tercero verificado a nivel de bytes, no es un doble salto de línea) — ninguno aparece en el texto visible; (c) `cleanModelOutput(isFinal=true)` no afecta al chat, el mensaje se arma con los deltas (`runtime/LlamaCppModelHelper.kt:302-306`); (d) `ThinkingSplitter` libera lo retenido en `finish()` aunque el bloque no se cierre.
- **Fix propuesto:** quitar `###` de los stops para Gemma 4 (o hacer la lista configurable por familia en `LLMInference.cpp`/`ChatFormats.h`) y, para el futuro, devolver el motivo del corte (EOG por token / stop string / tope) en lugar del `[EOG]` genérico y registrarlo en `AGLlamaPerf`. No hay alternativa solo en Kotlin: el corte ocurre antes de llegar a la app.
- **Archivos:** `Android/src/smollm/src/main/cpp/LLMInference.cpp` (stop strings y motivo del EOG), `Android/src/smollm/src/main/java/com/jegly/offlineLLM/smollm/SmolLM.kt` (exponer el motivo), `engine/LlamaCppEngine.kt` (log del motivo), `engine/GgufChatTemplate.kt` (referencia de plantilla).

> **BUG-031** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-031).

## P2 — UX

### BUG-007 · ⚠️ Ficha: "Compartir" genera un texto no guardable *(2026-11-19: el humano confirma que sigue entregando texto sin tipo de archivo → ver BUG-039)*
**Esfuerzo:** M · **Escalón:** 2 · Ver también BUG-039: comparten `RoleplayScreens.kt`, un solo commit

- **Síntoma original:** diálogo de texto sin opción de guardar/archivo.
- **Estado verificado (2026-11-19):** el código ya no abre un diálogo propio: `ui/roleplay/RoleplayScreens.kt:185-191` y `:225-231` hacen `ACTION_SEND` tipo `application/json` con el JSON en `EXTRA_TEXT` y el nombre en `EXTRA_SUBJECT`.
- **Revisión 2026-11-19 (Tanda 3):** confirmado de nuevo en código, **sin cambios**.
- **✅ Verificado en el teléfono (2026-11-19, 2.2):** el menú "Más acciones → Compartir" de una ficha abre el chooser con el JSON `chara_card_v2`, un botón "Copiar texto" y el destino "Guardar en..." (además de Quick Share, Xiaomi Share, Bluetooth, Drive, Gmail, WhatsApp). El texto se puede guardar/copiar: **no hace falta** exportar a caché + `FileProvider`.
- **Fix:** compartir de verdad: `ACTION_SEND` con texto plano o exportar `.json` a caché + `FileProvider` (como hace la copia de seguridad .zip). El primer mecanismo ya está implantado y verificado.
- **Archivos:** `ui/roleplay/CharacterDetailSheet.kt`, `data/characters/CharacterRepository.kt`.

### BUG-008 · ❌ Memoria: los recuerdos "sin procesar" no se pueden editar
**Esfuerzo:** S · **Escalón:** 1 · Causa localizada en `ui/memory/MemoryScreen.kt`

- **Síntoma:** solo se pueden borrar; editar no funciona (los procesados sí, verificar).
- **Archivos:** `ui/memory/MemoryScreen.kt`, `data/characters/RawMemory.kt`.

### BUG-009 · ❌ Cajón: la hamburguesa del inicio no abre el cajón (solo el swipe)
**Esfuerzo:** S · **Escalón:** 1 · Conectar el botón al estado del `ModalNavigationDrawer`

- **Archivos:** `ui/home/BoxHomeScreen.kt` (conectar botón al estado del `ModalNavigationDrawer`).

### BUG-010 · ⚠️ Cajón: chats de personajes borrados salen como "Personaje borrado"
**Esfuerzo:** S · **Escalón:** 1 · Requiere decisión tuya: renombrar u ocultar

- **Decisión pendiente:** renombrar a "Sin personaje" u ocultarlos/moverlos a "Chat libre".
- **Archivos:** `ui/home/` (cajón) + `data/local/` (consulta de chats agrupados).

### BUG-011 · ✅ Macros `{user}`/`{{char}}` sin sustituir en fichas y previews *(arreglado 2026-11-19, pendiente de re-prueba en el teléfono)*
**Esfuerzo:** S · **Escalón:** 0 · Ya arreglado: solo abrir las fichas en el teléfono

- **Síntoma:** "Psicólogo Digital" muestra `{{char}}`/`{{user}}` crudos en la biblioteca; Tohru muestra `{user}` crudo en la ficha.
- **Verificar también:** que el prompt final SÍ las sustituya (y soportar ambas sintaxis y alias de tarjeta tipo SillyTavern).
- **Archivos:** `data/characters/CharacterPromptBuilder.kt` (`applyMacros`), `ui/roleplay/CharacterDetailSheet.kt`, `ui/roleplay/CharacterLibrary.kt`.
- **Causa verificada (2026-11-19):** (1) `CharacterPromptBuilder.MACRO` solo reconocía `{{char}}`/`{{user}}` y `<BOT>`/`<USER>`; fichas como Tohru escriben `{user}` con llaves simples y pasaba crudo **también al prompt**. (2) La hoja de detalle y la vista previa de la biblioteca mostraban `description`/`personality`/`scenario` sin pasar por `applyMacros` (el prompt final sí lo hacía).
- **Fix aplicado (corregido el mismo día, decisión del humano):** las llaves simples NO son macros (igual que SillyTavern); `MACRO` vuelve a ser solo `{{char}}`/`{{user}}` y `<BOT>`/`<USER>`. Una ficha con `{user}` lo conserva literal y se corrige a mano. Lo que sí se arregla: `CharacterDetailSheet` y `CharacterLibrary` sustituyen las macros al mostrar (con el nombre de la ficha y `User`). Test `singleBracesAreNotMacros` en `CharacterPromptBuilderTest.kt` (16 pruebas OK con kotlinc; pendiente de Gradle). Archivos: `data/characters/CharacterPromptBuilder.kt`, `ui/roleplay/CharacterDetailSheet.kt`, `ui/roleplay/CharacterLibrary.kt`.
- **Re-prueba (teléfono):** abrir la ficha de Tohru y la de Psicólogo Digital: ya no deben verse `{user}` ni `{{char}}` crudos ni en la hoja de detalle ni en la biblioteca; y en un chat, el modelo debe llamar por su nombre al usuario.

> **BUG-018** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-018).

> **BUG-020** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-020).

> **BUG-028** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-028).

> **BUG-022** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-022).

> **BUG-023** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-023).

### BUG-025 · ✅ Crash al limpiar un modelo GGUF cargado por AgentChat: ClassCastException en LlmChatModelHelper.cleanUp *(arreglado 2026-11-17)*
**Esfuerzo:** S · **Escalón:** 0 · Re-prueba: limpiar GGUF desde AgentChat

- **Síntoma (crash 12:44 del 17/11, con el APK de la 4.ª ronda):** `ClassCastException: LlamaCppEngine cannot be cast to LlmModelInstance` en `LlmChatModelHelper.cleanUp` (línea 367), vía `AgentChatTask.cleanUpModelFn` ← `ModelManagerViewModel.cleanupModel` ← `setAppInForeground`. Mata la app al limpiar el modelo (p. ej. al volver a primer plano), interrumpe pruebas en curso.
- **Causa raíz:** `AgentChatTaskModule.cleanUpModelFn` estaba **fijo a `LlmChatModelHelper.cleanUp`** (LiteRT) aunque el modelo cargado fuera GGUF (instancia `LlamaCppEngine`): el cast duro dentro de `cleanUp` explota. El crash venía de ayer (23:18, línea 359 del APK viejo) y hoy (12:44, línea 367 del APK nuevo) — bug viejo, no regresión.
- **Fix:** (1) `AgentChatTask.cleanUpModelFn` despacha vía `model.runtimeHelper.cleanUp` (el helper correcto según el runtime); (2) `LlmChatModelHelper.cleanUp` ya no hace cast duro: ante una instancia no LiteRT avisa en el log, pone `model.instance = null` y termina sin crashear (defensa para cualquier otro punto de llamada).
- **Archivos:** `customtasks/agentchat/AgentChatTaskModule.kt`, `ui/llmchat/LlmChatModelHelper.kt`.

### BUG-026 · ✅ Crash del TTS al generar voz: NoSuchMethodError en el callback JNI de sherpa-onnx *(arreglado 2026-11-17, compilado pendiente de re-prueba)*
**Esfuerzo:** S · **Escalón:** 0 · Re-prueba con la build del fix; reapareció el 17/11

- **Síntoma (crashes SIGABRT 13:36:52 y 13:37:10 del 17/11, tid `pool-*-thread-1`):** al generar voz con Pocket (sherpa-onnx), `JNI DETECTED ERROR IN APPLICATION: NewFloatArray called with pending exception java.lang.NoSuchMethodError: no non-static method "SherpaPocketEngine$$ExternalSyntheticLambda0.invoke([F)Ljava/lang/Integer;"`. Mata la app a la primera generación de voz. Stack: `OfflineTts.generateWithConfigImpl` ← `OfflineTts.generateWithConfigAndCallback` ← `SherpaPocketEngine.generate` (`SherpaPocketEngine.kt:97`) ← `TtsManager.launchJob$lambda$2` (`TtsManager.kt:270`).
- **Causa raíz:** el callback de muestras se pasaba como lambda Kotlin `(FloatArray) -> Int`. D8 la desazaúca en una clase sintética (`$$ExternalSyntheticLambda0`) cuyo `invoke` es **estático** cuando no captura nada, y el JNI de sherpa-onnx busca con `GetMethodID` un `invoke` de **instancia** con firma `([F)Ljava/lang/Integer;` (retorno **boxed** `Integer`, porque sherpa declara `Function1<? super float[], java.lang.Integer>`). Sin método → excepción pendiente → abort en el siguiente `NewFloatArray`.
- **Fix:** clase normal `SherpaPocketEngine.ChunkCallback` que implementa `Function1<FloatArray, Int?>` con `override fun invoke(samples: FloatArray): Int?`. Verificado con `javap`: emite `public java.lang.Integer invoke(float[])` — instancia, firma exacta.
- **Archivos:** `tts/SherpaPocketEngine.kt`.
- **Actualización (reportes 15:26 y 15:30 del 17/11):** el mismo `NoSuchMethodError` reaparece a las 15:27:54 y 15:30:05 con la firma `SherpaPocketEngine$$ExternalSyntheticLambda0.invoke([F)Ljava/lang/Integer;` — no consta si el dispositivo ya tenía instalado el APK con el fix (compilado pero pendiente de re-prueba). No se abre bug nuevo: hay que confirmar con esa build que el `javap` con `invoke` de instancia basta.

## P3 — Rendimiento

> **BUG-012** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-012).

### BUG-017 · ⚠️ Visión mmproj: muy lenta (~4 min con lado 256; 512 peor)
**Esfuerzo:** L · **Escalón:** 3 · Medir por fases (encode vs prefill) antes de optimizar

- **Síntoma (Bloque 4, prueba 4.3):** describir una imagen funciona, pero tarda ~4 minutos con `imageMaxSide` 256; con 512 aún peor. Inutilizable para chat fluido.
- **Contexto de código:** `runInference` antepone `engine.queueImages(images, maxSide)` al prompt (`runtime/LlamaCppModelHelper.kt:206-212`); el lado viene de `EnginePerfStore` (por defecto 512).
- **Mejoras propuestas (parcial — puede haber coste inherente del encoder de visión en CPU):** redimensionado real del bitmap antes del encoder, hilos del codificador mtmd, y medir por fases (encode vs prefill) para localizar el cuello; valorar caché si se reenvía la misma imagen.
- **Archivos:** `engine/LlamaCppEngine.kt` (`queueImages`), `runtime/LlamaCppModelHelper.kt`, `data/characters/EnginePerfStore.kt`.

### BUG-019 · ⚠️ Benchmark LiteRT: métrica propia no comparable con GGUF
**Esfuerzo:** S · **Escalón:** 1 · Unificar métrica o etiquetar como no comparable

- **Síntoma (Bloque 4, prueba 4.5):** el benchmark mide en ambos motores, pero la medición de LiteRT usa otra base (métrica propia del runtime) y sus cifras no son comparables con las de GGUF. Menor; arreglar más adelante.
- **Fix propuesto:** unificar la medición (mismos prompts de prueba y misma fórmula tok/s) o etiquetar claramente que no son comparables.
- **Archivos:** app Benchmark de modelos (GGUF: `QuickBenchDialog.kt`) y la medición del runtime LiteRT.

> **BUG-024** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-024).

### BUG-041 · ❌ Voces de Pocket TTS: no se pueden guardar varias voces propias (biblioteca de voces)
**Esfuerzo:** L · **Escalón:** 2 · Sucede a BUG-027 (descartado); parte L si incluye grabación

- **Síntoma (humano, 2026-11-19, con captura de "Voz (Pocket TTS)"):** subir la muestra `.wav` funciona, pero solo hay **una voz predeterminada** (`default.wav`) y **una por personaje** (`char_<id>.wav`); no se puede guardar cada muestra como una voz con nombre para tener varias y elegirlas.
- **Código verificado:** `tts/TtsFiles.kt` `importVoice(context, uri, id)` guarda `voices/<id>.wav` solo con los ids `"default"` y `"char_$characterId"` (`ui/roleplay/TtsSettingsDialog.kt:76` y `:216`). No existe un almacén de voces con nombre.
- **Referencia de funciones (la app Layla, solo como guía de funcionalidad; código propio, sin copiar nada de ella):** pantalla "Voces" con (1) origen: subir `.wav` o grabar con el micrófono; (2) reproducir la muestra antes de guardar; (3) texto de prueba editable y botón "Probar voz"; (4) nombre + etiquetas (fijas: hombre, mujer, narrador, personaje, calmado, enérgico, grave, suave, robótico, cálido, y una personalizada); (5) "Guardar voz"; (6) lista de voces con buscador por nombre/etiqueta, botón reproducir y borrar; (7) interruptor "Guardar como archivo de audio". El nombre debe autocompletarse con el nombre del archivo **sin extensión**; la duración mostrada debe ser la real (en la referencia sale "0:00" aunque dure 10 s).
- **Fix propuesto:** `VoiceStore` (singleton Hilt con `StateFlow<List<Voice>>`, como `ScenarioStore`) que guarda `voices/<uuid>.wav` + un `voices.json` con `id`, `name`, `tags`, `createdAt`; `TtsFiles.importVoice` recibe el id de la voz; el selector de voz (por defecto y por personaje) elige de la lista en vez de importar al vuelo. Migrar `default.wav` y `char_*.wav` existentes como voces con nombre ("Predeterminada", nombre del personaje). Test de lógica pura para el almacén (alta, borrado, búsqueda por etiqueta).
- **Archivos:** `tts/TtsFiles.kt`, `ui/roleplay/TtsSettingsDialog.kt`, `tts/TtsManager.kt`, nueva pantalla de voces y `VoiceStore`.

## 🧩 Tanda final — Roleplay de grupo, copias y compartir *(documentado 2026-11-19, pendiente; hacer DESPUÉS de los bugs menores)*

> Origen: re-pruebas del humano del 19/11 (2.4, 2.4b, 4.4b, 5.6b) con capturas del chat de escena (`AI Chat`, LiteRT) y de la pantalla de grupo "Political Pundits". Referencia de diseño: [chats de grupo de SillyTavern](https://docs.sillytavern.app/usage/core-concepts/groupchats).

**Modelo objetivo del chat de escena (lo que el humano quiere):**
1. **Lo que escribe el usuario es solo contexto.** Enviar un texto lo guarda como mensaje (de su persona, de un personaje o del Narrador) y **el modelo NO responde**. Es como si el modelo hubiera escrito ese mensaje y se archivara como contexto.
2. **El modelo solo habla cuando el usuario lo pide:**
   - **🎲 Aleatorio:** elige un personaje al azar y habla **solo por él**, **añadiendo** un mensaje nuevo al hilo (nunca borra ni sustituye el anterior).
   - **Enviar con el campo vacío + personaje seleccionado:** el modelo escribe **por ese personaje** (el usuario decide quién habla, sin azar). Equivale al "forzar respuesta de un miembro" de SillyTavern (icono de burbuja junto al miembro).
3. **El usuario puede interpretar a cualquiera:** su persona, un personaje existente o el Narrador. Si interpreta a KIP, el siguiente turno (Aleatorio) no debe ser KIP.
4. **La persona del usuario se importa como un personaje más** en la escena: debe poder **quitarse** (papelera) cuando el usuario va a interpretar a un personaje ya creado; si se quita, desaparece de la barra de hablantes.
5. **En roleplay solo se usan `nombre`, `descripción` y `personalidad`** de cada ficha (no escenario, saludo, ejemplos de mensajes ni system prompt de la ficha). Así se ahorra contexto y el hilo se mantiene mejor. Distinto del chat 1 a 1, que usa la ficha completa.

**Cómo lo hace SillyTavern (verificado en su documentación):** estrategias de orden de respuesta *Manual* (solo responde el miembro elegido; los mensajes del usuario **nunca** disparan respuesta), *Natural* (menciones → "talkativeness" → un miembro al azar), *List* y *Pooled* (azar entre los que no han hablado desde el último mensaje del usuario). Con entrada vacía en modo manual responde un miembro no silenciado al azar; el botón junto a cada miembro fuerza que hable ese personaje aunque esté silenciado. El historial es **compartido** y por defecto se incluye solo la ficha del que habla (*Swap*); *Join* mezcla todas las fichas y la propia documentación avisa de que puede confundir a los personajes. Campos que une *Join*: descripción, escenario, personalidad, ejemplos. → Para Box RolePlay encaja el modo **Manual + Swap**, con nombre/descripción/personalidad.

### BUG-032 · ❌ Escena: "🎲 Aleatorio" sustituye la respuesta anterior en vez de añadir un mensaje nuevo
**Esfuerzo:** S · **Escalón:** 2 · Tanda final: agrupar con BUG-033 a BUG-036

- **Síntoma (capturas 16:57–16:58, AI Chat):** el mensaje del usuario `Narrator: el mundo se encontraba en su final` se queda como último; cada pulsación de Aleatorio deja **una sola respuesta** (Akiko, luego Psicólogo…) y la anterior desaparece del hilo visible, en lugar de acumular `Akiko → KIP → Psicólogo…`. Con el campo vacío no hay mensaje de usuario nuevo y la respuesta nueva ocupa el sitio de la anterior. El contador "1/1" sugiere que el hilo la trata como respuesta alternativa (swipe) del mismo mensaje del usuario.
- **Pistas de código (sin verificar el mecanismo exacto):** `ui/llmchat/LlmChatScreen.kt` (`onRandom`, `SendMessageTrigger(messages = emptyList(), promptSuffix…)`), `ui/llmchat/LlmChatViewModel.kt` (`generateResponse`: `persistAssistantMessage(... asSwipe = regenerate)`, `lastUserTextIndex`, la ventana de contexto/`needsReset`) y `data/local/ChatRepository.kt`. Comprobar primero en el log si el turno se persiste como mensaje nuevo o como swipe, y si `ContextWindowPlanner` descarta el turno anterior (el contexto bajó de 47 % a 40 % tras pulsar Aleatorio).
- **Fix:** cada turno de Aleatorio/hablante forzado = mensaje AGENT **nuevo** en la lista y en la base de datos, con el nombre del hablante como encabezado; solo "Regenerar" (flechas) debe crear swipe.

### BUG-033 · ❌ Escena: el mensaje del usuario dispara respuesta del modelo con TODOS los personajes en un solo mensaje
**Esfuerzo:** M · **Escalón:** 2 · Tanda final; reaparece BUG-004 (cerrado)

- **Síntoma:** al enviar `hola amigos…` el modelo responde con KIP + Akiko + Tohru en un mismo mensaje (captura 16:57:03: KIP, Tohru, Psicólogo y Akiko en una sola burbuja). El filtro de BUG-004 solo recorta bloques de hablantes no permitidos; no impide que el usuario dispare una respuesta.
- **Fix (diseño objetivo, puntos 1-3 de arriba):** en escenas, **enviar un texto no genera**: lo guarda como contexto. La generación se lanza solo con Aleatorio o con "enviar vacío" sobre un hablante seleccionado. Ofrecer un interruptor "El modelo responde a mis mensajes" (por defecto desactivado en escenas) por si el usuario quiere el comportamiento antiguo.
- **Archivos probables:** `ui/llmchat/LlmChatScreen.kt` (`onSendMessage`, `SceneSpeakerBar`), `ui/llmchat/SceneSpeakerBar.kt`, `ui/llmchat/LlmChatViewModel.kt`, `data/characters/ScenePromptBuilder.kt`, y la pantalla de grupo de `ui/roleplay/` ("Political Pundits").

### BUG-034 · ❌ Escena: "enviar con el campo vacío" y personaje seleccionado debería hacer hablar a ese personaje
**Esfuerzo:** S · **Escalón:** 2 · Tanda final: enviar con campo vacío

- **Mejora pedida:** el usuario elige un personaje en la barra, deja el texto vacío y pulsa enviar → el modelo escribe **por ese personaje** (hoy el botón de enviar no hace nada con el campo vacío). Así el usuario decide quién sigue, sin azar.
- **Fix:** en `onSendMessage`, si `rawMessages` está vacío y hay hablante seleccionado (personaje o Narrador), lanzar el mismo `SendMessageTrigger` que Aleatorio pero con `Speaker.Character(seleccionado)` (o `Narrator`). Habilitar el botón de enviar con campo vacío solo en ese caso.

### BUG-035 · ❌ Escena: la persona del usuario se importa como participante y estorba al interpretar a otro personaje
**Esfuerzo:** M · **Escalón:** 2 · Tanda final: persona opcional en la escena

- **Síntoma (captura 17:05, "Nuevo roleplay"):** la lista de participantes incluye `User` (persona) además de las fichas, y aparece como hablante en la barra; la papelera existe en el editor, pero no está claro que al quitarla desaparezca de la barra ni que el prompt deje de llamarla "User".
- **Fix:** hacer que la persona sea **opcional** en la escena (quitarla = el usuario interpreta a otro personaje o solo al Narrador); verificar que, al quitarla, `ScenePromptBuilder` no la nombra y `SceneSpeakerBar` no la ofrece; mantener "Yo" como atajo solo si la persona está en la escena.

### BUG-036 · ❌ Escena: usar solo nombre, descripción y personalidad de cada ficha
**Esfuerzo:** M · **Escalón:** 2 · Tanda final: recortar campos de la ficha

- **Mejora pedida:** en roleplay de grupo/escena, cada personaje aporta únicamente `name`, `description` y `personality` (ahorra contexto y mantiene mejor el hilo). El chat 1 a 1 sigue usando la ficha completa.
- **Pista:** `ScenePromptBuilder.build()` (`data/characters/ScenePromptBuilder.kt`, líneas ~30-60) arma el prompt por personaje con `CharacterPromptBuilder.applyMacros`; revisar qué campos mete hoy (escenario, saludo, ejemplos, system prompt) y recortarlos. Valorar un interruptor "Usar ficha completa" por escena.
- **Observación relacionada (captura 17:00, "Political Pundits"):** dos personajes distintos (The Conservative / The Libertarian) devolvieron **el mismo texto en inglés**, una acotación de escenario (`[The studio lights are bright…]`) que parece venir del escenario/saludo de la escena y no de su personalidad; recortar los campos extra debería evitarlo. Comprobar también que el idioma de respuesta sigue al usuario (español).

### BUG-037 · ✅ Filtro de escenas: el Narrador en español no se reconoce (`Narrador:` ≠ `Narrator`)
**Esfuerzo:** S · **Escalón:** 0 · Compilar con Gradle + re-prueba en escena

- **Hallazgo de la revisión de código (2026-11-19):** `ScenePromptBuilder.NARRATOR = "Narrator"` pero la barra muestra "Narrador" y el modelo suele responder `Narrador:`. `headerSpeaker` no lo reconoce como encabezado conocido, así que `filterReply` deja pasar ese bloque en turnos que no lo permiten (comprobado con una prueba en JVM: con el hablante `KIP` fijo se descarta Tohru, pero un `Narrador:` justo detrás de KIP se conserva como si fuera de KIP).
- **Fix:** aceptar ambos alias (`Narrator`, `Narrador`, y `Narradora`) en `headerSpeaker`, y añadir test en `ScenarioTest.kt`.
- **Fix aplicado (2026-11-19):** `ScenePromptBuilder.headerSpeaker` acepta `Narrator`, `Narrador` y `Narradora` (sin distinguir mayúsculas) y devuelve el nombre canónico `NARRATOR`, solo si el narrador forma parte de la escena. Test `filterRecognisesSpanishNarratorAlias` en `ScenarioTest.kt` (falla con el código anterior, pasa con el nuevo; compilado con kotlinc, pendiente de Gradle). Archivos: `data/characters/ScenePromptBuilder.kt`, `test/.../ScenarioTest.kt`.

### BUG-038 · ❌ Copia de seguridad: duplica en vez de fusionar, y no incluye todo ni permite elegir qué restaurar
**Esfuerzo:** L · **Escalón:** 2 · Sucede a BUG-023 (cerrado); parte L si incluye chats Room

- **Estado (5.6b, probado por el humano 2026-11-19):** "Restaurar copia" funciona, pero al restaurar el `.zip` **duplica** los personajes existentes en lugar de sobrescribirlos, y el `.zip` contiene **solo personajes** (BUG-023).
- **Comportamiento deseado:** (1) **fusionar, no reemplazar**: lo que está en la copia **sobrescribe** el elemento existente con el mismo id/nombre, y lo que ya existe y no está en la copia (p. ej. 3 personajes creados después de la copia) **se conserva intacto**; nunca se borra nada; (2) la copia debe incluir **personajes, historial de chats, recuerdos, perfiles de persona, presets (motor, muestreo) y escenas**; (3) un **selector** al restaurar: "¿Qué quieres recuperar?" con casillas (personajes / chats / recuerdos / personas / presets / escenas / todo).
- **Pista:** `data/characters/CharacterRepository.kt` (`exportBackupZip`, `importFromUri/Bytes`), `ui/home/BoxSettingsScreen.kt`, `ui/roleplay/RoleplayViewModel.kt` (`importFromSettings`). Cuidado con el historial: está en Room cifrado (SQLCipher); definir formato de exportación (JSON dentro del `.zip`) y evitar duplicar conversaciones (usar el id de conversación como clave).

### BUG-039 · ❌ Ficha: "Compartir" entrega un texto JSON sin tipo de archivo (reabre BUG-007)
**Esfuerzo:** M · **Escalón:** 2 · Reabre BUG-007: exportar a caché + FileProvider

- **Síntoma (humano, 2.2/4.1):** el JSON `chara_card_v2` se comparte como texto plano (`EXTRA_TEXT`), así que no se puede guardar ni enviar como archivo: no hay nombre ni extensión. Con "Guardar en…" no se crea un `.json` válido.
- **Fix:** exportar a caché un archivo `NombreDelPersonaje.json` (y, si la ficha tiene imagen, opcionalmente `.png` con la tarjeta incrustada, el formato del ecosistema SillyTavern) y compartirlo con `FileProvider` + `ACTION_SEND` (`EXTRA_STREAM`, `application/json` / `image/png`). Mantener "Copiar texto" como segunda opción. Archivos: `ui/roleplay/RoleplayScreens.kt` (líneas ~185-231), `data/characters/CharacterRepository.kt`.

### BUG-040 · ℹ️ Presets de motor (4.4b): confirmar que el botón de guardar cubre hilos y Flash Attention
**Esfuerzo:** S · **Escalón:** 1 · Requiere decisión tuya antes de tocar

- **Nota del humano:** "no hay botón que permita guardar los presets de hilos/flash como perfiles". BUG-018 ya guarda los diez campos de rendimiento dentro del preset de motor y se verificó en 4.4b; si lo que falta es un **botón propio** en la sección de rendimiento (como el de temperatura), añadirlo para que sea descubrible, sin duplicar la lógica. Revisar con el humano antes de tocar.

---

## ✅ Lo que ya funciona (Bloque 2, 2026-11-16)

Ficha de personaje (stats, descripción, escenario) · **chat 1 a 1: persona usada, personaje correcto, sin hablar por ti (GGUF y LiteRT, re-prueba OK)** · grupos con chips y persistencia · escenas: crear/guardar/chatear con varios personajes y narrador · barra de escena SOLO en chats de escena (correcto) · memoria: botones, guardado y borrado de recuerdos · cajón por swipe con chats agrupados por personaje · contador de rendimiento en escenas (743 tok @ 5 t/s, gen 3.7 t/s).

## ✅ Lo que ya funciona (Bloque 4 — descargas y modelos, 2026-11-17 · Xiaomi)

Navegador HF: búsqueda, listado con holgura de RAM y errores limpios ("Hugging Face respondió 401", "Este repositorio no tiene archivos .gguf ni .litertlm"); pegar enlace lleva directo al selector de archivos (colores del label → BUG-020) · descarga y cambio de modelo GGUF/LiteRT chateando tras el cambio (carrera del primer mensaje → BUG-016) · visión mmproj funcional, muy lenta (BUG-017) · motor guardado por personaje (GGUF/LiteRT, visión, persona, muestreo) y **fallback al motor general de roleplay al borrar el modelo ligado** (presets sin rendimiento → BUG-018) · benchmark de velocidad en GGUF y LiteRT (métrica LiteRT no comparable → BUG-019). Esta tanda cubre el resto del Bloque 1 (LiteRT).

## ✅ Lo que ya funciona (Bloque 5 — robustez y regresión, 2026-11-17 · POCO)

Segundo plano 10+ min: GGUF y LiteRT aguantan otra app y pantalla apagada y siguen al volver · LiteRT termina los turnos cortos aunque apagues la pantalla · presets de muestreo: guardar con nombre, cargar y borrar con ✕ (falta un pequeño retoque gráfico) · apps sin modelo: "Texto a voz" y el resto abren su UI sin cargar nada · exportación de copia de seguridad .zip y de logs funcionan (importación y crashes nativos → BUG-023/BUG-022) · turno GGUF de referencia: **carga 3.5 s · prompt 105 tok @ 7.9 t/s · gen 210 tok @ 5.06 t/s**; LiteRT por contador: **8.6 frag/s · 1er token 3.1 s**.
