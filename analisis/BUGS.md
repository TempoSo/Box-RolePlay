# 🐞 Inventario de bugs — Box RolePlay

> **Triaje 2026-11-21:** 37 cerrados · 5 abiertos (017, 029, 037, 041, 042). Los cerrados se sustituyeron por una línea de referencia y su texto íntegro se conserva en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md); este documento sigue siendo el inventario de los activos; empieza por el § Orden sugerido.
>
> **Documento vivo.** Un bug = una entrada. Al cerrar uno: **no se marca ✅ dentro del texto**; se mueve el bloque entero a [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md), se deja aquí solo una línea de referencia y se añade una entrada en [`CAMBIOS.md`](CAMBIOS.md) con fecha, archivos tocados y motivo.
> Origen: pruebas del Bloque 2 (2026-11-16, POCO duchamp, HyperOS 3.0.9.0, Android 15 — fingerprint del crash) y de los Bloques 4 y 5 (2026-11-17, Xiaomi 2311DRK48G / POCO duchamp).

---

## 🧭 Orden sugerido (más fácil → más difícil)

> **Regla:** trabaja **un escalón a la vez**. Si un ID ya está en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md), sáltalo y avisa. Los pendientes del Escalón 0 ya llevan parche; solo falta tu re-prueba.

| Escalón | IDs | Coste | Qué es |
|---|---|---|---|
| **0 · Verificación gratis** | 037 | 0 parche nuevo | 011, 015, 016, 021, 025, 026 y 030 cerrados (015/021/025/026/030 el 19/11; 011/016 el 20/11); queda un caso concreto de 037 |
| **1 · Rápidos** | ✅ completo | S | 008, 009, 010, 014, 019 y 040 verificados y cerrados el 20/11; 041 (voces) funciona y pasa a la tanda final para pulirla |
| **2 · Medianos con tests** | 041 (pulido), 042 (implementado, falta re-prueba) | M–L | Pulir la biblioteca de voces y validar en el móvil la fusión por contenido |
| **3 · Duros / nativos** | 017, 029 | L | Visión en CPU y stop strings en `LLMInference.cpp` |

> **Agrupaciones:** 032–035 (escenas) y 007/039 (compartir) quedaron verificados el 21/11. En 041 la parte «L» aparece solo si se decide incluir grabación de audio. BUG-042 nace de la re-prueba de 038: la copia ya funciona y ahora distingue «idéntico» (no toca) de «parecido» (conserva tu versión y añade la de la copia); falta validarlo en el móvil.

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
- **Fix propuesto:** quitar `###` de los stops para Gemma 4 (o hacer la lista configurable por familia en `LLMInference.cpp`/`ChatFormats.h`) y, para el futuro, devolver el motivo del corte (EOG por token / stop string / tope) en lugar del `[EOG]` genérico y registrarlo en `AGLlamaPerf`. No hay alternativa solo en Kotlin: el corte ocurre antes de llegar a la app.
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
- **Archivos:** `engine/LlamaCppEngine.kt` (`queueImages`), `runtime/LlamaCppModelHelper.kt`, `data/characters/EnginePerfStore.kt`.

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

### BUG-037 · ⚠️ Filtro de escenas: el Narrador en español no se reconoce (`Narrador:` ≠ `Narrator`)
**Esfuerzo:** S · **Escalón:** 0 · Re-prueba pendiente: falta el caso concreto que falla

- **Hallazgo de la revisión de código (2026-11-19):** `ScenePromptBuilder.NARRATOR = "Narrator"` pero la barra muestra "Narrador" y el modelo suele responder `Narrador:`. `headerSpeaker` no lo reconoce como encabezado conocido, así que `filterReply` deja pasar ese bloque en turnos que no lo permiten (comprobado con una prueba en JVM: con el hablante `KIP` fijo se descarta Tohru, pero un `Narrador:` justo detrás de KIP se conserva como si fuera de KIP).
- **Fix:** aceptar ambos alias (`Narrator`, `Narrador`, y `Narradora`) en `headerSpeaker`, y añadir test en `ScenarioTest.kt`.
- **Fix aplicado (2026-11-19):** `ScenePromptBuilder.headerSpeaker` acepta `Narrator`, `Narrador` y `Narradora` (sin distinguir mayúsculas) y devuelve el nombre canónico `NARRATOR`, solo si el narrador forma parte de la escena. Test `filterRecognisesSpanishNarratorAlias` en `ScenarioTest.kt` (falla con el código anterior, pasa con el nuevo; 9/9 pruebas de `ScenarioTest` OK con Gradle el 19/11). Archivos: `data/characters/ScenePromptBuilder.kt`, `test/.../ScenarioTest.kt`.
- **Re-prueba 19/11 (humano):** «un poco dando más problemas» al probarlo en un escalón más fuerte (el 4). No se pudo identificar el caso: sin saber qué escena, qué hablante estaba fijado y qué texto se coló, no hay nada que corregir sin adivinar. **Pendiente:** captura o texto exacto y si la escena tenía el narrador activo.
- **Caso aportado el 20/11 (captura 07:46, GGUF `gemma-4-E…PURE`, escena con Narrador en la barra y KIP seleccionado):** el usuario escribe `KIP: Muchachos tenemos un problema` y la respuesta trae tres bloques en la misma burbuja: `Narrator:`, `Tohru:` y `Psicólogo Digital:`. **Aclaración:** ese turno es del **usuario**, y `ScenePromptBuilder.userTurnRestriction` permite justo a *todos* los demás hablantes de la escena (menos el que firmó) + narrador si está activo; es el comportamiento que pide cambiar **BUG-033** («un mensaje del usuario no debería disparar a todos»), no un fallo de los alias del narrador. Es decir: la captura no reproduce el síntoma de este bug. El humano lo deja para más adelante (necesita tiempo); no se toca nada aquí hasta tener un caso con el hablante fijado por la app (Aleatorio o envío vacío) y el narrador activo/desactivado.

> **BUG-038** — cerrado y verificado el 2026-11-21 en su alcance base (copia v2 con selector y fusión por id). La afinación de identidad pasa a **BUG-042**. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-038).

### BUG-042 · 🟡 Copia de seguridad: duplicar si el personaje cambió, no sobrescribir
**Esfuerzo:** M · **Escalón:** 2 · Sucede a BUG-038 (cerrado)

- **Regla pedida (21/11):** al restaurar, un personaje **idéntico** no se duplica; si el mismo id/nombre trae **cualquier cambio de contenido** (p. ej. añadir `{{char}}` al inicio de la descripción), se conserva el existente y se crea una **copia nueva con otro id**. Así no se pierde la edición.
- **Caso real:** Akiko Fujimoto con la descripción original y la misma descripción empezando por `{{char}}` deben quedar como dos personajes, no sobrescribirse.
- **Regla implementada (21/11):** se compara la firma canónica de la tarjeta (todos los campos de `CharacterCard`: nombre, descripción, personalidad, escenario, saludo, saludos alternativos, ejemplos, system prompt, post-history, notas, creador, versión, etiquetas, fuente, extensiones y lorebook; **sin** id, avatar, modelo ni fechas), normalizando saltos de línea, espacios en los extremos y el formato del JSON. Igual → no se toca; distinto → se conserva el existente y la copia entra con un `UUID` nuevo. En `.zip` v1 se empareja por nombre con el mismo criterio.
- **Caso real:** Akiko Fujimoto con la descripción original y la misma descripción empezando por `{{char}}` quedan como dos personajes.
- **UI:** el resumen distingue «nuevo(s)», «copia(s) de personajes editados» y «sin cambios»; el diálogo avisa de que nada se borra. No se añade la opción «sobrescribir»: la regla pedida es no perder la versión editada.
- **Archivos:** `data/characters/BackupFormat.kt` (firma y comparación), `data/characters/BackupRepository.kt` (`restoreCharacters`, `restoreLegacyCharacters`), `ui/roleplay/RestoreBackupDialog.kt`, `ui/home/BoxSettingsScreen.kt`, `ui/roleplay/RoleplayViewModel.kt`, `test/.../BackupFormatTest.kt`.
- **Estado:** implementado el 21/11; tests JVM `characterIsIdenticalAfterBackupRoundTrip`, `addingCharMacroMakesTheCharacterDifferent`, `whitespaceAndJsonFormattingDoNotCountAsChanges` y `anyRealFieldChangeMakesTheCharacterDifferent` en verde. **Pendiente de re-prueba en el móvil** con el caso Akiko.

> **BUG-039** — cerrado y verificado el 2026-11-21 (mismo fix que BUG-007: `.json` real con `FileProvider`). Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-039).

> **BUG-040** — cerrado y verificado el 2026-11-20. Su síntoma, causa, fix y commit están en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md#bug-040).

### BUG-041 · 🟡 Voces de Pocket TTS: no se pueden guardar varias voces propias (biblioteca de voces)
**Pulido pendiente (2026-11-20):** el humano la probó y funciona; queda pulirla en la tanda final (junto al roleplay de grupo y los demás «mayores»). Ideas: interruptor «Guardar como archivo de audio», nombres de las voces migradas de personajes, exportar/importar con la copia de seguridad (BUG-038).
**Esfuerzo:** L · **Escalón:** 2 · Sucede a BUG-027 (descartado); parte L si incluye grabación

- **Síntoma (humano, 2026-11-19, con captura de "Voz (Pocket TTS)"):** subir la muestra `.wav` funciona, pero solo hay **una voz predeterminada** (`default.wav`) y **una por personaje** (`char_<id>.wav`); no se puede guardar cada muestra como una voz con nombre para tener varias y elegirlas.
- **Código verificado:** `tts/TtsFiles.kt` `importVoice(context, uri, id)` guarda `voices/<id>.wav` solo con los ids `"default"` y `"char_$characterId"` (`ui/roleplay/TtsSettingsDialog.kt:76` y `:216`). No existe un almacén de voces con nombre.
- **Referencia de funciones (la app Layla, solo como guía de funcionalidad; código propio, sin copiar nada de ella):** pantalla "Voces" con (1) origen: subir `.wav` o grabar con el micrófono; (2) reproducir la muestra antes de guardar; (3) texto de prueba editable y botón "Probar voz"; (4) nombre + etiquetas (fijas: hombre, mujer, narrador, personaje, calmado, enérgico, grave, suave, robótico, cálido, y una personalizada); (5) "Guardar voz"; (6) lista de voces con buscador por nombre/etiqueta, botón reproducir y borrar; (7) interruptor "Guardar como archivo de audio". El nombre debe autocompletarse con el nombre del archivo **sin extensión**; la duración mostrada debe ser la real (en la referencia sale "0:00" aunque dure 10 s).
- **Fix propuesto:** `VoiceStore` (singleton Hilt con `StateFlow<List<Voice>>`, como `ScenarioStore`) que guarda `voices/<uuid>.wav` + un `voices.json` con `id`, `name`, `tags`, `createdAt`; `TtsFiles.importVoice` recibe el id de la voz; el selector de voz (por defecto y por personaje) elige de la lista en vez de importar al vuelo. Migrar `default.wav` y `char_*.wav` existentes como voces con nombre ("Predeterminada", nombre del personaje). Test de lógica pura para el almacén (alta, borrado, búsqueda por etiqueta).
- **Archivos:** `tts/TtsFiles.kt`, `ui/roleplay/TtsSettingsDialog.kt`, `tts/TtsManager.kt`, nueva pantalla de voces y `VoiceStore`.
Parche aplicado (2026-11-20, pendiente de re-prueba): biblioteca de voces (subir .wav o grabar, nombre, etiquetas, buscador, probar, borrar). Re-prueba en el teléfono: Roleplay > Voz > «Biblioteca de voces»: (1) subir un .wav y ver que el nombre es el del archivo sin extensión y la duración real; (2) grabar 5 s con el micrófono (pide permiso); (3) guardar dos voces y comprobar que ambas salen en la lista y el buscador filtra por etiqueta; (4) «Usar» una como predeterminada y «Probar voz»; (5) en un personaje, «Elegir voz» con otra distinta; (6) tras actualizar, las voces antiguas (default.wav, char_*.wav) deben aparecer ya en la lista; (7) borrar una voz pide confirmación.


### BUG-042 · 🟡 Logs: se pierden al reiniciarse la app (sin diagnóstico de cierres)
**Esfuerzo:** M · **Escalón:** 2

- **Síntoma (humano, 2026-11-22):** el log exportado solo cubre la sesión actual; tras un cierre no hay rastro de lo anterior.
- **Causa:** logcat sin permisos de sistema es solo del proceso propio y se reinicia con él. Los logs de la app de referencia se guardan en archivos.
- **Parche aplicado (2026-11-22, pendiente de re-prueba):** log persistente por sesión, ver CAMBIOS. Re-prueba: (1) usar la app un rato, cerrarla deslizando y reabrirla; (2) Ajustes > exportar log: debe traer «Sesión actual» y «Sesión anterior 1» con líneas de memoria (MEM) y hitos (GGUF); (3) la cabecera de la sesión nueva dice el motivo de la salida anterior; (4) comprobar que no hay ruido de MIUI y que el tamaño del reporte es razonable.
- **Archivos:** `data/characters/PersistentLog.kt`, `LogLineFilter.kt`, `LogExporter.kt`, `GalleryApplication.kt`, `engine/LlamaCppEngine.kt`.

## ✅ Lo que ya funciona (Bloque 2, 2026-11-16)

Ficha de personaje (stats, descripción, escenario) · **chat 1 a 1: persona usada, personaje correcto, sin hablar por ti (GGUF y LiteRT, re-prueba OK)** · grupos con chips y persistencia · escenas: crear/guardar/chatear con varios personajes y narrador · barra de escena SOLO en chats de escena (correcto) · memoria: botones, guardado y borrado de recuerdos · cajón por swipe con chats agrupados por personaje · contador de rendimiento en escenas (743 tok @ 5 t/s, gen 3.7 t/s).

## ✅ Lo que ya funciona (Bloque 4 — descargas y modelos, 2026-11-17 · Xiaomi)

Navegador HF: búsqueda, listado con holgura de RAM y errores limpios ("Hugging Face respondió 401", "Este repositorio no tiene archivos .gguf ni .litertlm"); pegar enlace lleva directo al selector de archivos (colores del label → BUG-020) · descarga y cambio de modelo GGUF/LiteRT chateando tras el cambio (carrera del primer mensaje → BUG-016) · visión mmproj funcional, muy lenta (BUG-017) · motor guardado por personaje (GGUF/LiteRT, visión, persona, muestreo) y **fallback al motor general de roleplay al borrar el modelo ligado** (presets sin rendimiento → BUG-018) · benchmark de velocidad en GGUF y LiteRT (métrica LiteRT no comparable → BUG-019). Esta tanda cubre el resto del Bloque 1 (LiteRT).

## ✅ Lo que ya funciona (Bloque 5 — robustez y regresión, 2026-11-17 · POCO)

Segundo plano 10+ min: GGUF y LiteRT aguantan otra app y pantalla apagada y siguen al volver · LiteRT termina los turnos cortos aunque apagues la pantalla · presets de muestreo: guardar con nombre, cargar y borrar con ✕ (falta un pequeño retoque gráfico) · apps sin modelo: "Texto a voz" y el resto abren su UI sin cargar nada · exportación de copia de seguridad .zip y de logs funcionan (importación y crashes nativos → BUG-023/BUG-022) · turno GGUF de referencia: **carga 3.5 s · prompt 105 tok @ 7.9 t/s · gen 210 tok @ 5.06 t/s**; LiteRT por contador: **8.6 frag/s · 1er token 3.1 s**.
