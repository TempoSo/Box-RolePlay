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
