# 🐞 Inventario de bugs — Box RolePlay

> **Triaje 2026-11-22:** 40 cerrados · 2 abiertos (017, 029) · 2 herramientas abiertas (043, 044) · BUG-045 nuevo (rendimiento). Los cerrados se sustituyeron por una línea de referencia y su texto íntegro se conserva en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md); este documento sigue siendo el inventario de los activos; empieza por el § Orden sugerido.
>
> **Documento vivo.** Un bug = una entrada. Al cerrar uno: **no se marca ✅ dentro del texto**; se mueve el bloque entero a [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md), se deja aquí solo una línea de referencia y se añade una entrada en [`CAMBIOS.md`](CAMBIOS.md) con fecha, archivos tocados y motivo.
> Origen: pruebas del Bloque 2 (2026-11-16, POCO duchamp, HyperOS 3.0.9.0, Android 15 — fingerprint del crash) y de los Bloques 4 y 5 (2026-11-17, Xiaomi 2311DRK48G / POCO duchamp).

---

## 🧭 Orden sugerido (más fácil → más difícil)

> **Regla:** trabaja **un escalón a la vez**. Si un ID ya está en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md), sáltalo y avisa. Los pendientes del Escalón 0 ya llevan parche; solo falta tu re-prueba.

| Escalón | IDs | Coste | Qué es |
|---|---|---|---|
| **0 · Verificación gratis** | ✅ completo | 0 parche nuevo | 011, 015, 016, 021, 025, 026, 030 y 037 cerrados (015/021/025/026/030 el 19/11; 011/016 el 20/11; 037 el 21/11) |
| **1 · Rápidos** | ✅ completo | S | 008, 009, 010, 014, 019, 040 y 041 verificados y cerrados entre el 20 y el 21/11 |
| **2 · Medianos con tests** | ✅ completo | 0 | 041 (biblioteca de voces) y 042 (copia inteligente) verificados y cerrados el 21/11 |
| **3 · Duros / nativos** | 017, 029, 045 | L | Visión en CPU y stops de `LLMInference.cpp`; 029: el corte también sale con el GGUF puro (pista: stop `###`); 045: se re-prefilla toda la conversación en cada turno |

> **Agrupaciones:** 032–035 (escenas), 007/039 (compartir), 041 y 042 quedaron verificados el 21/11. En 041 la parte «L» aparece solo si se decide incluir grabación de audio. BUG-042 nace de la re-prueba de 038: la copia ya funciona y distingue «idéntico» (no toca) de «parecido» (conserva tu versión y añade la de la copia); re-prueba OK el 21/11.
>
> **Mejora futura (anotada, NO implementada):** al duplicar un personaje, vincular sus recuerdos a la copia cuando exista; si el personaje no cambió, añadirlos al original. **Nunca sobrescribir**: se perderían los recuerdos nuevos. Punto de entrada: `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/BackupRepository.kt`.

> **Decisión de formato (21/11):** `[...]` se reserva para **narración y acciones**. El encabezado `Narrador:` se mantiene porque el filtro de escenas identifica hablantes por `Nombre:`; el narrador escribe `Narrador: [texto]` y un personaje puede usar `[acción]` dentro de su bloque. No se aceptan bloques `[...]` sueltos sin encabezado (se perdería el filtrado). Pendiente de implementar en el prompt de escena.

---

## P0 — Críticos (bloquean el roleplay)

> **BUG-001** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-001).

> **BUG-002** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-002).

> **BUG-030** — cerrado y verificado el 2026-11-19. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-030).

## P1 — Correctitud del roleplay

> **BUG-013** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-013).

> **BUG-014** — cerrado y verificado el 2026-11-20. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-014).

> **BUG-003** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-003).

> **BUG-004** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-004).

> **BUG-005** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-005).

> **BUG-006** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-006).

> **BUG-015** — cerrado y verificado el 2026-11-19 (2.1b: calidad en GGUF y LiteRT). Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-015). El recorte de respuestas largas en GGUF es otro bug: **BUG-029**.

> **BUG-016** — cerrado y verificado el 2026-11-20 (4.2c: GGUF→GGUF y LiteRT→GGUF sin «El modelo sigue cargando»). Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-016).

> **BUG-021** — cerrado y verificado el 2026-11-19 (5.4b: rotar con y sin generación). Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-021).

> **BUG-027** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-027).

### BUG-029 · ❌ GGUF: la respuesta escrita se queda corta ("no termina de hablar", como las fotos) *(documentado 2026-11-17; causa probable localizada)*
**Esfuerzo:** L · **Escalón:** 3 · Stop strings en `LLMInference.cpp` (C++, no es submódulo)

- **Síntoma (capturas 15:25 y 15:26 del 17/11, app "AI Chat", contexto 4096):** la generación para **sin error y sin rastro**, muy antes de cualquier tope: turno 1 con `gemma-4-E4B…PURE.gguf` corta a los **98 tok** (3.5 t/s, 40.7 s, contexto 5 % ~213/4096) justo tras "Piensa en estas preguntas conmigo:" — anuncia preguntas y no las escribe; turno 2 con `gemma-4-E…_K_M.gguf` corta a los **63 tok** (4.1 t/s, 47.9 s, contexto 8 % ~323/4096) tras anunciar "cuatro áreas clave" sin listarlas. Dos modelos distintos, ambos cortes **exactamente donde empezaría la lista/sección**.
- **Causa probable (verificada en el wrapper nativo; `Android/src/smollm` está trackeado en el repo, NO es submódulo):** en el bucle de generación (`Android/src/smollm/src/main/cpp/LLMInference.cpp:577-594`) la respuesta termina de forma silenciosa si el token muestreado es EOG (`llama_vocab_is_eog`, p. ej. el `<turn|>` de Gemma 4) o si el texto acumulado contiene un **stop string**: `<turn|`, `<|turn_end|>`, `<turn_end|>`, `<start_of_turn>`, `<end_of_turn>`, **`###`**, `System instruction:`. El motivo nunca llega a la app: `SmolLM.getResponseAsFlow` (`Android/src/smollm/src/main/java/com/jegly/offlineLLM/smollm/SmolLM.kt:256-263`) corta el flujo al recibir el `[EOG]` y no lo emite. Encaja con **`###`**: los Gemma 4 instruct abren cada sección con `### Título`; el nativo comprueba el stop con la pieza ya incluida y **descarta esa pieza**, así que en pantalla no queda ningún marcador y el texto termina justo antes del encabezado — exactamente el patrón de las dos capturas.
- **Descartados:** (a) tope de generación — `maxResponse` es global (`EnginePerfSettings` default `-1`, `LLMInference.h:67`) y dos modelos cortaron a 98 y 63; (b) los stops de la app en `LlamaCppEngine.kt:377-382` (`<|im_end|>`, `<|eot_id|>`, el token de fin de texto de GPT — línea 380, "GPT-style EOS" — y `<|im_start|>`; el tercero verificado a nivel de bytes, no es un doble salto de línea) — ninguno aparece en el texto visible; (c) `cleanModelOutput(isFinal=true)` no afecta al chat, el mensaje se arma con los deltas (`runtime/LlamaCppModelHelper.kt:302-306`); (d) `ThinkingSplitter` libera lo retenido en `finish()` aunque el bloque no se cierre.
- **Evidencia nueva (21/11, re-prueba del usuario):** el corte **solo aparece con `gemma-4-E4B-it-qat-q4_0-heretic-Q4_0_PURE.gguf`** (cuantización qat/heretic), tanto en chat 1:1 con personaje como en la mascota «Toca para chatear» (1:1 sin personaje). Con el GGUF puro y con el `.litertlm` de la misma familia **no se corta nada**, y los recuentos de tokens son similares. Esto descarta una regla general de la app (plantilla, personaje o 1:1) y apunta a la **cuantización del modelo**: el qat/heretic parece emitir antes el token EOG o un marcador de fin de turno. Las tres exportaciones de logs revisadas (19/11, 20/11, 21/11) se leen bien pero **no incluyen `AGLlamaPerf` ni el motivo del EOG**; en el del 21/11 solo consta `Último turno GGUF: … gen 42 tok` frente a `gen 189 tok` del 19/11. Para confirmar el motivo hay que exportar logs **inmediatamente** tras el turno que se corta, sin navegar antes, o copiar el contador del chat en pantalla. *(Actualizado el 22/11: con el modo diagnóstico se ve que **también corta el Q4_K_M** — ver la evidencia siguiente.)*
- **Evidencia nueva (22/11, reporte `box-log-20261122-161812.txt` con el modo diagnóstico de BUG-044):** cuatro generaciones con «MOTIVO DE PARADA: fin natural del modelo (EOS)»; ninguna cancelada, sin error, sin tope (`tope=-1`) y con el contexto casi vacío (63, 213, 279 y 1554 de 4096). Hay **dos cortes**:
  - `gemma-4-E4B-it-qat-q4_0-heretic-Q4_0_PURE.gguf` · 16:08:55 · 41 tok · acaba en «…junto con su desarrollo:».
  - `gemma-4-E4B-it-Q4_K_M.gguf` (**el GGUF puro**) · 16:10:06 · 40 tok · acaba en «…(científico/neurobiológico, funcional/psicológico y clínico/aplicado):\n\n---», después de anunciar «tres preguntas» que no llega a escribir.
  → **El corte no es exclusivo de la cuantización qat/heretic**: también corta el Q4_K_M, así que la acotación del 21/11 no vale como causa única. Los dos cortes caen **exactamente donde el modelo iba a abrir un encabezado de sección**, y la única respuesta larga de la sesión (1467 tok, 16:17:41, mismo Q4_K_M) escribe la lista con **dos** almohadillas (`## 1. Exposición`), nunca con tres.
- **Pista fuerte (22/11):** el nativo marca fin de generación si el texto acumulado contiene `###` —entre otros stops— **descarta la pieza** y devuelve un `[EOG]` genérico (`LLMInference.cpp:577-595`). Desde Kotlin eso es indistinguible del EOG del modelo, por eso el diagnóstico lo etiqueta como «fin natural». Encaja con los dos cortes y con las capturas del 17/11.
- **Descartado en el log del 22/11:** tope de respuesta (el único tope que existe es el de las tandas de absorción, 400 tok, y no hay ninguna línea `GGUF maxResponse auxiliar`), contexto lleno, cancelación, error y `restoreMaxResponse` fallido (no aparece el aviso).
- **Parche aplicado (2026-11-22, pendiente de re-prueba):** `###` y `System instruction:` ya no cortan por sí solos; solo como cabecera de turno inventada (`### User:`), ver CAMBIOS. Re-prueba: pedir en GGUF «explícame cuatro áreas de la fotografía usando encabezados ###» 3–4 veces con el heretic y con el Q4_K_M; deben salir completas y el reporte de diagnóstico no debe decir `stop:###`.
- **Siguiente paso (sin implementar):** que `LLMInference.cpp` devuelva **cuál** condición cerró la generación (token EOG / stop `###` / otro stop) y que se guarde en el diagnóstico. Si sale `###`, el arreglo es quitarlo o condicionarlo a la familia del modelo; sin ese dato no se toca a ciegas.
- **Archivos:** `Android/src/smollm/src/main/cpp/LLMInference.cpp` (stop strings y motivo del EOG), `Android/src/smollm/src/main/java/com/jegly/offlineLLM/smollm/SmolLM.kt` (exponer el motivo), `engine/LlamaCppEngine.kt` (log del motivo), `engine/GgufChatTemplate.kt` (referencia de plantilla).

> **BUG-031** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-031).

## P2 — UX

> **BUG-007** — cerrado y verificado el 2026-11-21 (se comparte `Nombre.json` con `FileProvider`; el texto sigue disponible). Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-007).

> **BUG-008** — cerrado y verificado el 2026-11-20. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-008).

> **BUG-009** — cerrado y verificado el 2026-11-20. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-009).

> **BUG-010** — cerrado y verificado el 2026-11-20. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-010).

> **BUG-011** — cerrado y verificado el 2026-11-20 (ficha y tarjeta con la persona activa). Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-011).

> **BUG-018** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-018).

> **BUG-020** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-020).

> **BUG-028** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-028).

> **BUG-022** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-022).

> **BUG-023** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-023).

> **BUG-025** — cerrado y verificado el 2026-11-19 (log 22:02:37: `Cleaning up model` + `Clean up done`, sin `ClassCastException`). Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-025).

> **BUG-026** — cerrado y verificado el 2026-11-19 (generación de voz con Pocket sin `NoSuchMethodError`). Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-026).

## P3 — Rendimiento

> **BUG-012** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-012).

### BUG-017 · ⚠️ Visión mmproj: muy lenta (~4 min con lado 256; 512 peor)
**Esfuerzo:** L · **Escalón:** 3 · Medir por fases (encode vs prefill) antes de optimizar

- **Síntoma (Bloque 4, prueba 4.3):** describir una imagen funciona, pero tarda ~4 minutos con `imageMaxSide` 256; con 512 aún peor. Inutilizable para chat fluido.
- **Contexto de código:** `runInference` antepone `engine.queueImages(images, maxSide)` al prompt (`runtime/LlamaCppModelHelper.kt:206-212`); el lado viene de `EnginePerfStore` (por defecto 512).
- **Mejoras propuestas (parcial — puede haber coste inherente del encoder de visión en CPU):** redimensionado real del bitmap antes del encoder, hilos del codificador mtmd, y medir por fases (encode vs prefill) para localizar el cuello; valorar caché si se reenvía la misma imagen.
- **Parche del 22/11 (pendiente de re-prueba):** `toRgb` ya no copia ni escala si la imagen cabe en `imageMaxSide` (solo copia bitmaps `HARDWARE`, que no dejan leer píxeles). Además la app **Visión** tiene ajustes propios inspirados en Layla: tope de respuesta (256 tok por defecto), prompt de sistema e instrucción de imagen, de modo que una descripción no se alarga indefinidamente. Si sigue lento, medir encode vs prefill antes de tocar más.
- **Medición del 22/11 22:03 (APK con los ajustes de visión):** **2 imágenes** a lado 512 → prompt de **596 tok en 443,9 s (1,3 t/s)**, primer token a los **444 s**, generación de 255 tok en 75,2 s (3,39 t/s), total **8,7 min**; parada `max_tokens` (el tope de 256 funciona). El cuello es el **prefill de los tokens de imagen**, no el escalado de Kotlin: 1 imagen a 512 ronda la mitad. Para CPU conviene un modelo de visión pequeño (SmolVLM2-500M, Qwen3.5-0.8B, MobileVLM2-1.7B) o LiteRT con visión integrada; Gemma 4 E4B + mmproj BF16 es inviable para chat fluido.
- **Archivos:** `engine/LlamaCppEngine.kt` (`queueImages`/`toRgb`), `runtime/LlamaCppModelHelper.kt`, `data/characters/EnginePerfStore.kt`, `data/characters/VisionSettings.kt`, `ui/apps/VisionSettingsDialog.kt`, `ui/llmchat/LlmChatScreen.kt`.

- **Pantalla Visión nueva (2026-11-22, pendiente de build y re-prueba):** Visión abre ahora `VisionScreen` (01 imagen, 02 modelo+mmproj, 03 prompt, 04 tokens). Re-prueba: (1) Apps > Visión abre la pantalla nueva; (2) elegir foto y «Procesar imagen» con el modelo cargado; (3) cambiar el mmproj (F16/Q8_0 frente a BF16) y comprobar que recarga solo; (4) «Exportar logs» y mandar el reporte para comparar «Prompt: X t/s» entre combinaciones.

### BUG-045 · ⚠️ GGUF: cada turno vuelve a prefillar toda la conversación (la caché de prefijo no se usa)
**Esfuerzo:** M · **Escalón:** 3 · Medir con el diagnóstico antes y después

- **Síntoma medido (log del 22/11):** el prompt se procesa a **2,5–5,4 t/s**: 23 tok en 9,1 s, 83 tok en 15,3 s, 87 tok en 18,8 s y 238 tok en 65,2 s. El primer token tarda **9–65 s** y el coste crece con el historial; con ~1500 tok serían unos 5 min solo de prefill por turno. El turno de referencia del 17/11 ya medía 7,9 t/s con 105 tok, así que el prefill es lento de por sí y el historial lo multiplica.
- **Evidencia de que no se reutiliza nada:** el diagnóstico informa de **todos** los tokens del prompt en cada turno (238 tok cuando el KV ya tenía 213; 87 tok cuando ya tenía 63) → el prefijo común que calcula el nativo es 0.
- **Causa 1 (Kotlin):** cada envío llama a `applySystemPromptIfNeeded` (`ui/llmchat/LlmChatViewModel.kt:992`), que para GGUF llama a `LlamaCppModelHelper.seedConversation` → `LlamaCppEngine.seedConversation` (`engine/LlamaCppEngine.kt:363`) → `instance.resetChat()` → `llama_memory_clear`. El motor arranca cada turno con la KV vacía, así que la reutilización de `LLMInference::_prepareDecode` + `ctxwin::reusablePrefix` nunca se aprovecha en el chat normal.
- **Causa 2 (nativo, latente):** `LLMInference.cpp` solo añade a `_kvTokens` los tokens del **prompt** (líneas 551 y 565); los tokens **generados** nunca se registran aunque el KV sí los contiene. Como el recorte se decide con `if (common < _kvTokens.size())`, cuando el prefijo reutilizable coincide con el tamaño registrado **no se borra la cola** y el prompt nuevo se añade detrás de la respuesta anterior (las posiciones salen de `memory->seq_pos_max()+1`, `llama-batch.cpp:100`). Hoy lo tapa el `resetChat()` de la causa 1, pero afecta a las tandas de absorción (`prepareForAuxTanda`), que sí conservan la KV: la segunda tanda ve el resumen de la primera como si fuera parte de la conversación.
- **Arreglo propuesto (sin implementar):** (a) registrar también los tokens generados en `_kvTokens` para que su tamaño refleje la KV real; (b) en la ruta de plantilla Gemma 4 —donde el prompt lo arma Kotlin y el historial nativo no se tokeniza— no llamar a `resetChat()` en `seedConversation`: basta fijar `lastSystemPrompt`/`lastHistory` y dejar que el nativo recorte el prefijo; (c) comparar con el diagnóstico los tokens de prompt por turno antes y después.
- **Parche del 22/11 (pendiente de re-prueba):** aplicado el punto (b): `seedConversation` omite `resetChat()` cuando `useGemma4Template` está activo (mismo patrón que `prepareForAuxTanda`). Falta (a) en el nativo y la medición antes/después con el diagnóstico.
- **Archivos:** `Android/src/app/src/main/java/com/google/ai/edge/gallery/engine/LlamaCppEngine.kt`, `.../ui/llmchat/LlmChatViewModel.kt`, `Android/src/smollm/src/main/cpp/LLMInference.cpp` (no es submódulo).

> **BUG-019** — cerrado y verificado el 2026-11-20. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-019).

> **BUG-024** — cerrado y verificado. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-024).

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

> **BUG-032** — cerrado y verificado el 2026-11-21 (cada turno de Aleatorio añade una burbuja nueva). Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-032).

> **BUG-033** — cerrado y verificado el 2026-11-21 (enviar en escena guarda contexto y no genera; interruptor por escena). Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-033).

> **BUG-034** — cerrado y verificado el 2026-11-21 (enviar vacío con hablante elegido). Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-034).

> **BUG-035** — cerrado y verificado el 2026-11-21 (persona opcional y barra que respeta «Incluir narrador»). Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-035).

> **BUG-036** — **descartado el 2026-11-21 (falso positivo):** la captura de `[The studio lights…]` era de **Layla**, no de Box, y el prompt de escena ya usaba solo nombre/descripción/personalidad. El único cambio de este supuesto bug era el test `sceneUsesOnlyNameDescriptionPersonality`, que se ha retirado. Decisión: `[...]` se usará para narración/acciones, no para recortar campos de la ficha (ver «Decisión de formato» arriba).

> **BUG-037** — cerrado y verificado el 2026-11-21 (filtro de escenas: alias `Narrador` reconocido). Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-037).

> **BUG-038** — cerrado y verificado el 2026-11-21 en su alcance base (copia v2 con selector y fusión por id). La afinación de identidad pasa a **BUG-042**. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-038).

> **BUG-042** — cerrado y verificado el 2026-11-21 (la copia conserva tu edición y duplica solo si cambia el personaje). Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-042).

> **BUG-039** — cerrado y verificado el 2026-11-21 (mismo fix que BUG-007: `.json` real con `FileProvider`). Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-039).

> **BUG-040** — cerrado y verificado el 2026-11-20. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-040).

> **BUG-041** — cerrado y verificado el 2026-11-21 (biblioteca de voces: «todo funciona»). Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-041).


### BUG-043 · 🟡 Logs: se pierden al reiniciarse la app (sin diagnóstico de cierres)
**Esfuerzo:** M · **Escalón:** 2

- **Síntoma (humano, 2026-11-22):** el log exportado solo cubre la sesión actual; tras un cierre no hay rastro de lo anterior.
- **Causa:** logcat sin permisos de sistema es solo del proceso propio y se reinicia con él. Los logs de la app de referencia se guardan en archivos.
- **Parche aplicado (2026-11-22, pendiente de re-prueba):** log persistente por sesión, ver CAMBIOS. Re-prueba: (1) usar la app un rato, cerrarla deslizando y reabrirla; (2) Ajustes > exportar log: debe traer «Sesión actual» y «Sesión anterior 1» con líneas de memoria (MEM) y hitos (GGUF); (3) la cabecera de la sesión nueva dice el motivo de la salida anterior; (4) comprobar que no hay ruido de MIUI y que el tamaño del reporte es razonable.
- **Re-prueba 22/11 (log 16:18): OK.** El reporte trae «Sesión actual» + «Sesión anterior 1», el motivo de la salida anterior (`FORCE STOP` de MIUI), `[MEM]` cada 20 s, hitos `[GGUF]` y el ruido de MIUI fuera. Queda tu OK para moverlo a `BUGS_CERRADOS.md`.
- **Archivos:** `data/characters/PersistentLog.kt`, `LogLineFilter.kt`, `LogExporter.kt`, `GalleryApplication.kt`, `engine/LlamaCppEngine.kt`.

### BUG-044 · 🟡 Modo diagnóstico para cazar BUG-029 y BUG-017 (temporal)
**Esfuerzo:** M · **Escalón:** herramienta · Temporal: apagar/quitar cuando 029 y 017 estén cerrados

- **Qué es:** registro por generación (prompt, respuesta cruda, tokens/s, motivo de parada), ver CAMBIOS 2026-11-22.
- **Parche aplicado (2026-11-22, pendiente de re-prueba):** (1) tras el build, Ajustes > «Modo diagnóstico» aparece encendido; (2) conversa con un GGUF y un LiteRT; (3) «Exportar logs»: al final debe haber «Modo diagnóstico» con una entrada por respuesta; (4) provoca una respuesta cortada y mira «MOTIVO DE PARADA»; (5) con el modo apagado no debe guardar nada nuevo. Reenvía ese reporte para atacar 029 y 017.
- **Re-prueba 22/11 (log 16:18): funciona.** Cuatro generaciones registradas con parámetros, prompt completo, respuesta cruda, t/s y motivo de parada. **Limitación encontrada:** el motivo cae en el cajón «fin natural del modelo (EOS)» tanto si el EOG lo emite el modelo como si lo provoca un stop string del nativo, porque `LLMInference.cpp` devuelve un `[EOG]` genérico en los dos casos. Para cerrar BUG-029 hace falta exponer cuál de los dos fue.
- **Archivos:** `data/characters/DiagLog.kt`, `engine/LlamaCppEngine.kt`, `ui/llmchat/LlmChatViewModel.kt`, `LogExporter.kt`, `BoxSettingsScreen.kt`.

## ✅ Lo que ya funciona (Bloque 2, 2026-11-16)

Ficha de personaje (stats, descripción, escenario) · **chat 1 a 1: persona usada, personaje correcto, sin hablar por ti (GGUF y LiteRT, re-prueba OK)** · grupos con chips y persistencia · escenas: crear/guardar/chatear con varios personajes y narrador · barra de escena SOLO en chats de escena (correcto) · memoria: botones, guardado y borrado de recuerdos · cajón por swipe con chats agrupados por personaje · contador de rendimiento en escenas (743 tok @ 5 t/s, gen 3.7 t/s).

## ✅ Lo que ya funciona (Bloque 4 — descargas y modelos, 2026-11-17 · Xiaomi)

Navegador HF: búsqueda, listado con holgura de RAM y errores limpios ("Hugging Face respondió 401", "Este repositorio no tiene archivos .gguf ni .litertlm"); pegar enlace lleva directo al selector de archivos (colores del label → BUG-020) · descarga y cambio de modelo GGUF/LiteRT chateando tras el cambio (carrera del primer mensaje → BUG-016) · visión mmproj funcional, muy lenta (BUG-017) · motor guardado por personaje (GGUF/LiteRT, visión, persona, muestreo) y **fallback al motor general de roleplay al borrar el modelo ligado** (presets sin rendimiento → BUG-018) · benchmark de velocidad en GGUF y LiteRT (métrica LiteRT no comparable → BUG-019). Esta tanda cubre el resto del Bloque 1 (LiteRT).

## ✅ Lo que ya funciona (Bloque 5 — robustez y regresión, 2026-11-17 · POCO)

Segundo plano 10+ min: GGUF y LiteRT aguantan otra app y pantalla apagada y siguen al volver · LiteRT termina los turnos cortos aunque apagues la pantalla · presets de muestreo: guardar con nombre, cargar y borrar con ✕ (falta un pequeño retoque gráfico) · apps sin modelo: "Texto a voz" y el resto abren su UI sin cargar nada · exportación de copia de seguridad .zip y de logs funcionan (importación y crashes nativos → BUG-023/BUG-022) · turno GGUF de referencia: **carga 3.5 s · prompt 105 tok @ 7.9 t/s · gen 210 tok @ 5.06 t/s**; LiteRT por contador: **8.6 frag/s · 1er token 3.1 s**.
