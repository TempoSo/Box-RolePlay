# 📓 Registro de cambios — Box RolePlay

> **Documento vivo para continuidad de memoria** entre sesiones de trabajo (humano, Claude, OpenCode u otros agentes).
>
> **Regla para agentes IA (obligatoria):** tras CUALQUIER cambio de código o documentación, añade una **entrada nueva arriba** en la sección "Registro" con: fecha, qué se cambió, **archivos exactos** (ruta completa) y motivo. No borres ni reescribas entradas antiguas. Si detectas un bug y su causa, documéntalo aquí también.

**Documentos vivos hermanos:**
- [`PRUEBAS.md`](PRUEBAS.md) — checklist original completa de pruebas por fase.
- [`PLAN_PRUEBAS.md`](PLAN_PRUEBAS.md) — plan priorizado con resultados de pruebas en dispositivo.
- [`BUGS.md`](BUGS.md) — inventario vivo de bugs **abiertos y dudosos** (síntomas, stacks, repros, fixes propuestos y archivos afectados).
- [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md) — histórico de los bugs cerrados con el mismo detalle y el commit/verificación que los cerró. Se consulta solo si se quiere auditar.
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

### 2026-11-26 — Voz: gestor con varios modelos de TTS (sin compilar)
- **Problema:** descargar o importar un modelo Pocket TTS sustituía al anterior (una sola carpeta `tts/pocket`); no se podía tener español e inglés ni cambiar entre ellos; la pantalla solo decía «Modelo completo».
- **Qué:** nueva biblioteca `TtsModels`: cada descarga/importación crea su carpeta en `filesDir/tts/models/<id>/` (con `label.txt`), sin borrar las demás, y pasa a ser la activa. En «Voz (Pocket TTS)» se listan los modelos con selector (radio), tamaño, aviso si están incompletos y botón «Borrar» con confirmación. El modelo antiguo (`tts/pocket`) aparece como «Modelo anterior». `TtsFiles.modelDir()` devuelve ahora la carpeta del modelo activo, así `TtsManager` no cambia. Al cambiar de modelo se recarga el motor (`TtsManager.release()`). La descarga solo mueve las piezas de su variante (ya no arrastra restos de la carpeta de trabajo).
- **Archivos:** `tts/TtsModels.kt` (nuevo), `tts/TtsFiles.kt`, `ui/roleplay/TtsModelDownloadDialog.kt`, `ui/roleplay/TtsSettingsDialog.kt`.
- **Probar en teléfono:** descargar español y luego inglés → deben aparecer los dos y poderse alternar y leer con cada uno; importar carpeta; borrar uno; el modelo anterior sigue funcionando. Ojo: la voz (muestra) es independiente del modelo.

### 2026-11-26 — Notas para futuras pruebas (caché KV y contexto)
- Caché de KV verificada en teléfono (log 09:03): chat restaurado 1738/1760 tokens en 0,11 s; primer mensaje de 22 tokens en 2,6 s. El recorte de turnos del contexto deslizante invalida el KV guardado (cambia el principio del prompt por el resumen); es normal y ocurre una vez por llenado.
- **Pendiente de probar (no implementado):** (1) contexto 8192 en Rendimiento (≈470 MB de KV; menos recortes); (2) KV q8_0 con el Auto-optimizar del benchmark (mitad de RAM/archivos; ver si pierde velocidad en CPU o exige Flash Attention); (3) desplazar el KV al recortar (nativo, arriesgado con SWA de Gemma). Si q8_0 no pierde velocidad, valorar ponerlo por defecto.

### 2026-11-26 — Caché KV en disco: límites de espacio y borrado con el chat
- **Qué:** `kvcache::prune(dir, maxChat=2, maxBytes=400 MB)` (antes 8 archivos, hasta ~1 GB): como mucho 2 `.chat` y 400 MB en total, borrando primero lo más viejo y conservando siempre el último. `saveKvChat` solo guarda si la conversación pasa de ~1000 tokens sobre la ficha. Al borrar un chat (o todos) se borran los `.chat` (`KvCacheFiles.clearChats`); las `.sys` no llevan conversación y se quedan. Texto del interruptor actualizado.
- **Archivos:** `smollm/src/main/cpp/KvCacheKeys.h`, `LLMInference.cpp`, `smollm/src/test/cpp/KvCacheKeysTest.cpp` (OK), `data/local/ChatRepository.kt`, `data/characters/EnginePerfSettings.kt`, `ui/roleplay/EnginePerfDialog.kt`.
- **Sin compilar.** Pendiente aparte: KV q8_0 para archivos a la mitad (prueba de calidad).

### 2026-11-25 — Caché KV en disco: verificada en teléfono (sistema) + más log para el chat
- **Resultado (log 23:29):** compila y funciona. Sistema restaurado en 0,03 s; primer mensaje tras cargar: 55 tok en 7 s (antes 30–43 s). Primera vez con una ficha nueva sigue costando 28–50 s (se guarda ~40–55 MB por ficha). Fotos en chat: `media: reutiliza 1852 de 2141`. Archivos .chat de 44–142 MB.
- **Duda:** el .chat guardado a las 23:01 (mismo sistema de 671 tokens) no se usó al recargar a las 23:02 (restauró solo «sistema»). Sin log no se sabe por qué; se añaden líneas `kv-cache:` en cada rama de fallo (`LLMInference.cpp`).

### 2026-11-25 — Caché de KV en disco con dos interruptores (nativo + Kotlin, SIN COMPILAR)
- **Qué:** el primer mensaje tras cargar el modelo procesaba ~730–990 tokens de ficha (30–43 s). Ahora el bloque de sistema (hasta el primer fin de turno de la ficha) se procesa una vez y se guarda en disco (`llama_state_seq_save_file`); en cargas siguientes se restaura (se espera ~5–10 s en vez de ~43 s). Interruptor **«Caché del sistema»** (activo por defecto, sin contenido de conversación) e interruptor **«Caché del chat completo»** (apagado por defecto; guarda la conversación **sin cifrar** y permite reanudar chats largos sin reprocesar; se guarda al descargar el modelo si no está generando). Botón «Borrar cachés». Máximo 8 archivos (`kvcache::prune`). Clave = hash de ruta+tamaño+contexto+tipo KV+flash del modelo y hash de los tokens del sistema: si cambia la ficha o el modelo, no se reutiliza. Cualquier fallo al cargar deja el KV limpio y arranca normal. El benchmark no usa la caché.
- **Archivos:** `Android/src/smollm/src/main/cpp/KvCacheKeys.h` (nuevo) y `smollm/src/test/cpp/KvCacheKeysTest.cpp` (OK con g++), `LLMInference.h/.cpp` (`setKvCache`, `_kvColdStart`, `saveKvChat`), `smollm.cpp` (JNI), `SmolLM.kt`, `engine/LlamaCppEngine.kt` (`kvCache`, `saveKvChat`), `runtime/LlamaCppModelHelper.kt` (clave y guardado en `cleanUp`), `data/characters/EnginePerfSettings.kt` (`kvCacheSystem/kvCacheChat`, `KvCacheFiles`), `ui/roleplay/EnginePerfDialog.kt`.
- **Pendiente de verificar en el teléfono:** compilación en Actions (API `llama_state_seq_*_file` no compilada aquí); que el estado de Gemma (SWA) se guarde/restaure (si falla, cae a arranque en frío y se ve en el log `kv-cache:`); tiempos del primer mensaje; borrar cachés.

### 2026-11-25 — Fotos en chats, 2.ª pasada: el prompt de imagen metía la plantilla dentro de otra plantilla
- Log 18:06 (build con `MediaReuse`): la foto en un chat de 5 turnos tardó 126 s de prompt; `media: reutiliza 715 de 2061 posiciones`. El reuso funcionó, pero solo hasta el primer turno: el prompt de imagen tenía **2061** posiciones para un texto de ~950 tokens.
- Causa (`LLMInference.cpp::_startMediaCompletion`): Kotlin ya entrega el prompt completo con plantilla Gemma (`<|turn>…<turn|>`), pero el camino de imagen lo añadía como un mensaje de usuario y volvía a aplicar la plantilla: sistema duplicado + plantilla anidada dentro de un turno de usuario. Por eso no coincidía con el KV de los turnos de texto (que sí usan el prompt tal cual) y el modelo recibía un prompt deformado.
- Fix: si la consulta ya trae plantilla (`<turn|` o `<start_of_turn>`), se usa tal cual, igual que el camino de texto; el formateo nativo queda solo para consultas sin plantilla. También afecta a la app Visión (mismo camino).
- Hipótesis, sin confirmar: el prompt deformado pudo contribuir a la basura (`<unusedNN>`) vista con Q4_K_M + imagen. Vigilar en la próxima prueba.
- Re-prueba: foto en chat largo → `media: reutiliza` ≈ todo menos el mensaje nuevo, prompt de ~40 s; la app Visión debe seguir describiendo bien (el texto del prompt cambia: ahora es el correcto).

### 2026-11-25 — Fotos dentro de un chat: reutilizar el KV (nativo)
- Log del 25/11 17:23: en un chat con 11 turnos la foto tardó 164 s de prompt (vs 38 s en Visión vacía); el turno con imagen vaciaba el KV y reprocesaba todo, y el siguiente también.
- `smollm/src/main/cpp/MediaReuse.h` (nuevo) + `LLMInference.cpp::_startMediaCompletion`: se calcula el prefijo común entre el KV y el prompt (cada posición de imagen lleva un id negativo único, así el prefijo se detiene donde empieza la imagen), se recorta el KV y solo se procesa lo nuevo: texto con `llama_decode` por lotes y la imagen con `mtmd_helper_eval_chunk_single`. `_kvDirty` ya no se marca tras una imagen. Línea nueva en logcat: `media: reutiliza X de Y posiciones del prompt`.
- Test `smollm/src/test/cpp/MediaReuseTest.cpp` (g++, OK). La parte que llama a llama.cpp/mtmd no se pudo compilar aquí: revisar en el build.
- Re-prueba: chat con historial largo + foto → «prompt» debe bajar de ~160 s a ~40 s y el log debe decir `reutiliza` casi todo; el turno de texto siguiente debe procesar pocas decenas de tokens.

### 2026-11-25 — Cierre de BUG-017, BUG-029 y BUG-045
- Movidos a `BUGS_CERRADOS.md` con su evidencia: 017 (logs del 24/11: mmproj F16 11–16 s frente a BF16 89–97 s para codificar; `DOTPROD = 1`), 029 (confirmado por el humano) y 045 (log 23/11: `hist=2` procesó 22 tok; limitación conocida: chats con imágenes re-codifican el historial).
- `BUGS.md`: triaje y fila del Escalón 3 actualizados; `PLAN_PRUEBAS.md`: línea de estado. 043 y 044 se mantienen abiertas.

### 2026-11-25 — Benchmark: auto-optimización, hilos de lectura y sin contexto
- Botón «Auto-optimizar» (solo GGUF marcados): por etapas hilos → hilos de lectura → nBatch → flash → KV; el ganador (por puntuación) de cada etapa queda fijo para la siguiente. 1 pasada + calentamiento, máx. 64 tokens; hasta 12 pruebas por modelo (`AutoTune`, probado). Resume el mejor al final y se aplica con el botón existente.
- Nuevo ajuste «hilos de lectura» (`threadsBatch`, ya soportado por el nativo vía `nThreadsBatch`): `EnginePerfSettings`, `EnginePerfStore`, `BenchCfg`. Va al aplicar la mejor a los chats.
- El contexto sale del panel y del barrido: fijo en 2048 (es de chats y no mide velocidad).
- nBatch del barrido ahora sube también nUBatch (antes 1024 era igual a 512).
- `BenchMathTest` 6 OK. Sin compilar.

### 2026-11-24 — Benchmark: ajustes propios, barrido y repeticiones
- Panel «Ajustes del motor GGUF (solo benchmark)» (hilos, contexto, nBatch, nUBatch, flash, KV, repack, mmap): aparece solo si hay un GGUF marcado y se guarda aparte (`BenchStore`). `LlamaCppModelHelper.perfOverride` lo aplica solo durante la medición y se limpia al acabar: los ajustes de los chats no cambian.
- Barrido automático de un parámetro (nBatch, hilos, flash, KV, contexto) con los valores que escribas: cada valor recarga el modelo y es una prueba.
- Pasada de calentamiento (no cuenta) + 1–5 pasadas medidas promediadas; entre pasadas se limpia el chat sin recargar.
- Cada resultado guarda su configuración; por modelo se listan las configuraciones probadas con la mejor (★) y «Aplicar la mejor a mis chats» (no toca el contexto).
- La puntuación y la recomendación usan la mejor configuración de cada modelo. `BenchMathTest` 5 OK. Sin compilar.

### 2026-11-24 — Benchmark en una sola pantalla (aislada, estilo Layla)
- `ui/apps/BenchmarkAppScreen.kt` (reescrita): marcas modelos GGUF y/o LiteRT descargados, Ejecutar, y se miden uno tras otro. Pantalla «Trabajando…» con cancelar y pantalla encendida. Resultados guardados (últimos 60) con puntuación y ranking local.
- `ui/apps/BenchRunner.kt` (nuevo): por modelo descarga lo cargado → carga → prompt fijo de resumen → descarga. Mide 1.er token, prefill, decode, RAM (PSS) y carga. En GGUF usa las cifras reales del nativo (`LastEnginePerf`); en LiteRT, tiempos medidos en Kotlin y tokens estimados (se marca «≈»).
- `data/characters/BenchMath.kt` (lógica pura, `BenchMathTest` 3 OK), `BenchStore.kt`, `BenchPrompt.kt` (el texto de Layla, ~900 tokens).
- Ranking local: 50 % generación, 20 % lectura, 30 % RAM. Sin mediciones: suposición por tamaño (el mayor ≤ 40 % de la RAM).
- Quitados «Benchmark completo» y «Prueba rápida» (QuickBenchDialog). No incluye % de CPU.
- Sin compilar: revisar en el teléfono tras el build.

### 2026-11-24 — Ajustes de Box recuperados + Visión solo con modelos descargados
- `VisionScreen`: la lista de modelos se filtra a importados o descargados (ya no salen Gemma-4 E2B/E4B LiteRT sin archivo).
- `BoxSettingsScreen`: nuevas filas "Permitir capturas de pantalla" (`AppLockManager`) y "Cifrado biométrico" (`BiometricEncryptionSection`, ahora `internal`). Tema y token HF del diálogo original no se añadieron.
- Sin compilar: revisar en el teléfono tras el build.

### 2026-11-24 — BUG-017: el motor se compila con dotprod (base general) y el log imprime system_info

**Qué y por qué:** los tiempos de `SmolLM-mtmd` (codificar una imagen 56–98 s; pasar 256 tokens de imagen al modelo ~50 s a ~5 t/s, igual que generar) indican que las multiplicaciones de matrices no usan las instrucciones rápidas del procesador. Causa probable: en `CMakeLists.txt` las variantes `smollm_v8_*` aplican `-march=…` solo a `LLMInference.cpp` y `smollm.cpp`; `ggml` (donde se calcula todo) se compila una vez, sin bandera de arquitectura, o sea armv8-a básico, sin `dotprod` (y por eso el Repack no cambió nada). Cambio: `-DGGML_CPU_ARM_ARCH=armv8.2-a+fp16+dotprod` en `smollm/build.gradle.kts`, configurable con `-PggmlArmArch=…` (con `…+i8mm` es más rápido pero solo en CPUs con i8mm; vacío vuelve al comportamiento anterior). Se eligió la base general porque la app puede usarla más gente: casi todo móvil ARM64 con Android 15+ tiene fp16 y dotprod; un móvil sin ellos se cerraría al cargar un modelo (riesgo conocido, poco probable). Además, `LLMInference.cpp` escribe `system_info: …` (NEON, DOTPROD, MATMUL_INT8…) al cargar el contexto: tras el build, el log debe decir `DOTPROD = 1`. Es una deducción de la configuración, no una medición: si tras el build los tiempos no cambian, no era esto. Sin compilar: lo dirá el build. **Aparte (sin cambios):** Q4_K_M normal (no QAT) con imagen devuelve basura (`<unusedNN>`) en 6 de 6 pruebas; con texto funciona; los QAT (heretic, UD-Q4_K_XL) describen bien. Causa sin separar (proyector de otra variante o numérica); se usan QAT para visión.

**Archivos:**
- `Android/src/smollm/build.gradle.kts` — `GGML_CPU_ARM_ARCH` configurable.
- `Android/src/smollm/src/main/cpp/LLMInference.cpp` — línea `system_info`.
- `analisis/BUGS.md` — nota en BUG-017.

### 2026-11-23 — BUG-017: tiempos de imagen en el log (codificar vs decodificar) y fin de la doble carga

**Qué y por qué:** los logs del 23/11 mostraron (1) que con mmproj F16 + lado 256 + K-quant la visión baja de 140–179 s a 81–92 s por imagen, pero el prompt sigue a 3–6 t/s también en texto solo, y no se sabía si el tiempo se iba en el codificador de imagen o en procesar los tokens; (2) la línea `[GGUF] cargando` aparecía **duplicada con la misma hora** en 3 de 20 cargas (dos cargas simultáneas del mismo modelo). Cambios: **nativo** (`LLMInference.cpp`): mtmd ya mide «image slice encoded in X ms» y «image decoded (batch n/m) in X ms» pero lo escribía en stderr, que en Android no llega a logcat; se redirige con `mtmd_helper_log_set` a logcat (etiqueta `SmolLM-mtmd`, solo INFO o superior) y el log persistente lo recoge. Es solo un redireccionado de mensajes: no cambia el procesado ni la generación. Nota: el proyector se inicia con `warmup=false`, así que la **primera imagen tras cargar paga un calentamiento** que verás dentro de esos tiempos; conviene procesar dos imágenes seguidas y comparar la segunda. **Doble carga** (`ModelManagerViewModel.initializeModel`): comprobar «ya se está cargando» y marcarlo era un paso no atómico (la brecha incluía limpiar otros modelos); ahora va dentro de `synchronized(model)`, y la segunda llamada simultánea ve que ya se carga y se salta. Sin compilar: el build lo dirá. Re-prueba: abrir Visión, cambiar de modelo y de proyector, y comprobar en «Exportar logs» que cada `[GGUF] cargando` aparece una sola vez y que salen líneas `SmolLM-mtmd` con ms de codificado y decodificado.

**Archivos:**
- `Android/src/smollm/src/main/cpp/LLMInference.cpp` — `mtmdLogToAndroid` + `mtmd_helper_log_set`.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/modelmanager/ModelManagerViewModel.kt` — claim atómico en `initializeModel`.
- `analisis/BUGS.md` — nota en BUG-017.

### 2026-11-22 — BUG-017: pantalla «Visión» propia, estilo Layla (todo en una pantalla)

**Qué y por qué:** el flujo de Visión era «Ask Image» (documentación y modelos recomendados de Google) → lista de modelos → chat, confuso y sin sitio para probar combinaciones de modelo/proyector. Pantalla nueva `VisionScreen`, con la estructura de la miniapp de visión de Layla (solo como guía de funciones; código propio): **01 Imagen de prueba** (elegir foto, vista previa con rotación EXIF, «Procesar imagen», parar, respuesta con su línea de tiempos), **02 Archivos del modelo** (modelo con visión elegido entre los importados + proyector mmproj con «Sin visión», fichas por archivo y «Añadir proyector»; al cambiar el archivo el modelo se recarga solo; en LiteRT avisa de que la visión va integrada), **03 Plantilla del prompt** (sistema e instrucción de imagen, plegable, con «Restablecer»), **04 Longitud de respuesta** (256/512/1024/2048/4096 o valor libre). Reutiliza `LlmAskImageViewModel`, `initializeModel` y el modo diagnóstico: cada prueba queda en «Exportar logs» con tokens/s, primer token y motivo de parada. Cada «Procesar» es un turno limpio (se vacía el hilo y no se guarda en el historial). Se recuerda el último modelo elegido. El botón Visión de la pantalla de inicio y de Apps abre esta pantalla; la pantalla antigua de chat con imagen sigue existiendo para conversaciones ya guardadas. **No incluido:** el selector de «Modelos recomendados» con descarga de pares texto+proyector (hay un consejo de texto sobre F16/Q8_0 y E2B); queda como siguiente paso. **Sin compilar:** UI Android pura, solo revisada por lectura; el build de GitHub Actions dirá si falta algún import o hay un tipo distinto.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/apps/VisionScreen.kt` — nuevo.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/navigation/GalleryNavGraph.kt` — ruta `box_app_vision`; Visión apunta a ella (inicio y Apps).
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/VisionSettings.kt` — `lastModel`/`setLastModel`.
- `analisis/BUGS.md` — nota en BUG-017.

### 2026-11-22 — Análisis del log 22:04: la visión tarda por el prefill de las imágenes, no por el escalado; el tope de 256 funciona

**Qué y por qué:** el humano probó el APK con los ajustes de visión y exportó `box-log-20261122-220402.txt`. **Sin cambios de código** en esta entrada; los datos cambian el siguiente paso de BUG-017.

- **Turno de visión medido:** 2 imágenes a lado 512 → prompt de **596 tok en 443,9 s (1,3 t/s)**, primer token a los **444 s**, generación de 255 tok en 75,2 s (3,39 t/s), total **8,7 min**. Parada `max_tokens`: el tope de 256 de la app Visión se aplica bien.
- **Conclusión:** el coste dominante es el **prefill de los tokens de imagen** (el encoder mmproj + los tokens visuales), no el `Bitmap.copy()`/escalado de `toRgb`. Bajar el lado de 512 a 256 ayuda poco; la palanca real es un modelo de visión pequeño o LiteRT con visión integrada. Gemma 4 E4B + mmproj BF16 en CPU no da chat fluido.
- **BUG-045:** el turno era el primero (`hist=0 turnos`), así que no hay evidencia todavía de reutilización de prefijo; hace falta un segundo turno.
- **BUG-029:** el diagnóstico aún muestra dos cortes por `stop:###`, pero son generaciones de las **18:25/18:26**, anteriores al build con `StopMarkers.h`; el parche nuevo sigue pendiente de re-prueba.
- **UI:** el humano pide acercar la pantalla de Visión a la de Layla (vista previa + «Procesar imagen» + ajustes), porque el flujo actual con «Ask Image», documentación y modelos recomendados confunde.

**Archivos:**
- `analisis/BUGS.md` — medición en BUG-017.

### 2026-11-22 — App Visión: ajustes propios inspirados en Layla (tope de tokens, prompt e instrucción de imagen)

**Qué y por qué:** la app «Visión» usaba el mismo flujo que un chat normal: sin tope de respuesta y sin instrucción propia, así que una descripción podía seguir generando indefinidamente y el turno se hacía eterno (relacionado con BUG-017). Layla/MobileVLM acota la visión con una configuración dedicada: tope de tokens (256 por defecto), prompt de sistema y una instrucción de imagen que se envía al procesar solo una foto.

**Qué se cambió:**
- Nuevos ajustes persistentes **solo para la app Visión**: `maxResponseTokens` (256 por defecto; ≤0 = sin tope, máximo 8192), `systemPrompt` e `imageInstruction`.
- La pantalla de visión aplica su prompt de sistema a su propio ViewModel (no toca Personajes, Roleplay ni Escenas) y pasa el tope de tokens al turno. En GGUF viaja como `max_response_tokens` y el motor lo restaura al terminar; a LiteRT no se le envía esa clave porque gestiona su propio límite y su visión integrada.
- Ahora se puede **enviar solo una imagen**: si no hay texto, el turno usa la instrucción de imagen configurada (equivalente a «Procesar imagen»). El mensaje aparece en el chat como texto del usuario.
- Botón «Ajustes de visión · N tok» sobre la caja de entrada y diálogo con los tres campos y «Restablecer».
- La selección de modelo/mmproj no cambia: sigue en Configuración de inferencia (Rendimiento > Visión) para GGUF/LiteRT sin visión integrada.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/VisionSettings.kt` — nuevo (ajustes + `VisionStore`).
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/apps/VisionSettingsDialog.kt` — nuevo (diálogo).
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatScreen.kt` — `LlmAskImageScreen` y `ChatViewWrapper` (instrucción de imagen, tope, envío solo con imagen).
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatViewModel.kt` — `generateResponse(..., maxResponseTokens)`.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/common/chat/ChatView.kt` — pasa `allowImageOnlySend`.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/common/chat/ChatPanel.kt` — ídem.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/common/chat/MessageInputText.kt` — botón de enviar habilitado con solo imagen.
- `Android/src/app/src/test/java/com/google/ai/edge/gallery/data/characters/VisionSettingsTest.kt` — nuevo (límites de los ajustes).
- `analisis/BUGS.md` — notas de los parches 017/045.
- `analisis/PLAN_PRUEBAS.md` — re-prueba 4.3c de la app Visión.

**Verificación:** pendiente de compilar y probar en dispositivo (ver `PLAN_PRUEBAS.md`). Casos: imagen sola en GGUF con mmproj (debe responder y parar en ~256 tok), texto + imagen, cambiar prompt/instrucción y confirmar que Personajes/Roleplay no cambian.

### 2026-11-22 — BUG-029: «###» deja de cortar la respuesta (parche, pendiente de re-prueba)

**Qué y por qué:** causa confirmada leyendo `LLMInference.cpp`: la lista de paradas fija del nativo incluía `"###"` y `"System instruction:"` y se comprobaba sobre **todo el texto acumulado**; cualquier encabezado markdown `### 1. …` (muy usado por Gemma) cerraba el turno, descartaba la pieza y devolvía `[EOG]`. Los dos cortes del log 16:18 caen justo antes de un posible `###`; la respuesta larga que salió completa usaba `##`. Ahora la lógica vive en `StopMarkers.h` (cabecera pura): los marcadores de turno de las plantillas (`<turn|`, `<end_of_turn>`…) siguen cortando donde aparezcan, pero `###` solo corta como **cabecera de turno inventada**: al inicio de línea, seguida de un rol (user, human, instruction, assistant, system, response, input, model) y `:` (p. ej. `### User:`). `System instruction:` solo al inicio de línea. En Kotlin, `cleanModelOutput` quita del resultado final lo que llegó a mostrarse de esa cabecera. Probado: `StopMarkersTest.cpp` compilado con g++ (14 comprobaciones OK). El nativo y el cambio de Kotlin no se pueden compilar aquí; quedan para el build. Re-prueba: en GGUF (heretic y Q4_K_M) pedir «explícame cuatro áreas de la fotografía usando encabezados ###»: debe salir completa y el motivo de parada ya no debe ser `stop:###`. Repetir 3–4 veces.

**Archivos:**
- `Android/src/smollm/src/main/cpp/StopMarkers.h` — nuevo.
- `Android/src/smollm/src/main/cpp/LLMInference.cpp` — usa `stopmarkers::find`.
- `Android/src/smollm/src/test/cpp/StopMarkersTest.cpp` — nuevo.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/engine/LlamaCppEngine.kt` — limpieza final de cabecera inventada.
- `analisis/BUGS.md` — nota en BUG-029.

### 2026-11-22 — BUG-017: optimización de procesamiento de imágenes en visión mmproj

**Qué y por qué:** el procesamiento de imágenes para el modelo mmproj (visión) era lento porque `toRgb` en `LlamaCppEngine.kt` copiaba siempre el bitmap a ARGB_8888 y lo escalaba, aun cuando la imagen ya cabía en el límite (`imageMaxSide`). Ese trabajo extra se paga en cada turno con imágenes.

**Qué se cambió:** en `toRgb` solo se escala cuando la imagen supera `maxSide`. Si ya cabe, se leen sus píxeles directamente, sin `Bitmap.copy()` ni `createScaledBitmap`. Excepción obligatoria: un bitmap `HARDWARE` no permite `getPixels()`, así que en ese caso sí se copia a ARGB_8888. Las copias creadas dentro del método se reciclan en un `finally`; el bitmap del llamador nunca se recicla.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/engine/LlamaCppEngine.kt` — `toRgb`.

**Verificación:** pendiente de compilar y de medir en dispositivo. El coste dominante puede ser el encoder de visión (mmproj), no el escalado: si sigue lento, medir encode vs prefill antes de optimizar más.

### 2026-11-22 — BUG-045: cada turno vuelve a prefillar toda la conversación (caché de prefijo no reutilizada)

**Qué y por qué:** cada envío llamaba a `seedConversation`, que a su vez llamaba a `instance.resetChat()`, vaciando la caché KV del motor nativo cada turno. El prompt se procesaba desde cero en cada generación, con primeros tokens de 9–65 s y costes crecientes con el historial (238 tok de prompt cuando la KV ya tenía 213: prefijo reutilizable 0).

**Qué se cambió:** en `seedConversation` se omite `instance.resetChat()` cuando está activa la plantilla Gemma 4 (`useGemma4Template`). En esa ruta Kotlin construye el prompt y el nativo no tokeniza el historial: basta actualizar `lastSystemPrompt`/`lastHistory` y dejar que el nativo recorte el prefijo común. Para las rutas normales el comportamiento no cambia.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/engine/LlamaCppEngine.kt` — `seedConversation`.

**Verificación:** mismo patrón que `prepareForAuxTanda()`; pendiente de re-prueba con un modelo Gemma 4 en dispositivo.

### 2026-11-22 — BUG-029: el motor nativo informa de *por qué* terminó cada generación

**Qué y por qué:** el análisis del log de las 16:18 dejó claro que el diagnóstico no podía distinguir el fin de turno del propio modelo de un corte provocado por un stop string nuestro (`###` es el sospechoso): `LLMInference.cpp` devolvía el mismo `[EOG]` en los dos casos. Ahora el nativo guarda el motivo y la app lo muestra en el diagnóstico y en el log persistente. **No cambia el comportamiento de la generación**: los mismos stops, el mismo `[EOG]`, el mismo texto en pantalla.

- **Nativo:** nuevo `LLMInference::getStopReason()` con tres valores: `token` (el modelo emitió su EOG), `stop:<texto>` (lo cerró un stop string propio) y `max_tokens` (tope de respuesta). Se limpia en `startCompletion` y en `resetChat`. El **token EOG manda sobre los stops** porque el marcador de fin de turno (`<turn|`) está en las dos listas; si el marcador llega troceado en varios tokens, sale como `stop:<turn|`. La cadena se sanea a ASCII imprimible antes de cruzar el JNI (`NewStringUTF` usa UTF-8 modificado).
- **Kotlin:** `SmolLM.getStopReason()` + `external fun getStopReason`. `LlamaCppEngine` lo lee con la puerta del nativo aún en la mano y lo pasa a `DiagFormat.stopReason(...)`, que ahora traduce:
  - `token` → «fin de turno del modelo (token EOG)».
  - `stop:<turn|` (y los demás marcadores de plantilla) → «fin de turno del modelo (marcador «<turn|»)»; no es un stop nuestro.
  - `stop:###` / `stop:System instruction:` → «secuencia de parada propia «###» (la detectó el motor nativo)».
  - `max_tokens` → «TOPE DE RESPUESTA (lo cortó el motor nativo)».
  - Vacío (motor sin el cambio) → se mantiene el texto anterior, «fin natural del modelo (EOS)».
- **Log persistente:** la línea `AGLlamaPerf` del turno termina ahora en `| parada: <motivo>`, así que el motivo sale también en «Exportar logs» sin depender del modo diagnóstico.
- **Prueba nueva:** `motivoDeParadaDelMotorNativo` (8 comprobaciones: los cuatro motivos, la prioridad de cancelación/error y la compatibilidad con el texto viejo).

**Archivos exactos:**
- `Android/src/smollm/src/main/cpp/LLMInference.h` — `getStopReason()` y `_stopReason`.
- `Android/src/smollm/src/main/cpp/LLMInference.cpp` — cálculo del motivo en el bucle de generación, saneado y limpieza en `startCompletion`/`resetChat`.
- `Android/src/smollm/src/main/cpp/smollm.cpp` — binding JNI `SmolLM_getStopReason`.
- `Android/src/smollm/src/main/java/com/jegly/offlineLLM/smollm/SmolLM.kt` — `getStopReason()` público y su `external`.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/DiagLog.kt` — `stopReason(..., nativeStop)`, `describeNativeStop`, `TURN_MARKERS`.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/engine/LlamaCppEngine.kt` — lectura del motivo, línea `AGLlamaPerf` y paso al diagnóstico.
- `Android/src/app/src/test/java/com/google/ai/edge/gallery/data/characters/DiagFormatTest.kt` — prueba nueva.
- `analisis/BUGS.md`, `analisis/CAMBIOS.md` — estado de BUG-029 y esta entrada.

**Verificación:** `.\gradlew.bat :app:testDebugUnitTest --tests "…DiagFormatTest"` desde `Android/src` → **BUILD SUCCESSFUL**. Los dos `.cpp` pasan `clang++ --target=aarch64-linux-android24 -std=c++17 -fsyntax-only` con las rutas de `CMakeLists.txt` (sin errores). **Re-prueba pendiente en el móvil:** provocar el corte y mirar «MOTIVO DE PARADA»; si sale `stop:###`, el arreglo de BUG-029 es quitar ese stop o condicionarlo a la familia Gemma.

### 2026-11-22 — Análisis del log 16:18: el corte de BUG-029 también aparece en el Q4_K_M y la KV se tira en cada turno

**Qué y por qué:** `git pull` de `origin/fase1-rendimiento` (conflicto en `analisis/CAMBIOS.md`, resuelto conservando las dos entradas) y análisis del reporte nuevo `box-log-20261122-161812.txt` (app v1.0.12 (26), Xiaomi 2311DRK48G, dos sesiones: 16:03 y 16:06). **Sin cambios de código.**

- **El reporte funciona (BUG-043 y BUG-044 OK a falta de tu OK para cerrarlos):** trae «Sesión actual» + «Sesión anterior 1», motivo de la salida anterior, `[MEM]` cada 20 s, hitos `[GGUF]` y la sección «Modo diagnóstico» con 4 generaciones completas (parámetros, prompt, respuesta cruda, t/s y motivo de parada). El ruido de MIUI queda fuera.
- **No hay crashes ni ANR nuevos:** todos los `ApplicationExitInfo` son cierres externos (`FORCE STOP` de `com.miui.securitycenter` / `MiuiMemoryService`, `SwipeUpClean` y una instalación). El «último cierre inesperado» que encabeza el reporte es el `ClassCastException` de `LlmChatModelHelper.cleanUp` del **17/11 12:44**, es decir BUG-025, cerrado el 19/11: es un registro viejo, no un fallo de hoy. Memoria nativa estable (560→597 MB durante dos turnos largos, sin fuga).
- **BUG-029 — evidencia nueva y corrección de la hipótesis del 21/11:** el modo diagnóstico dice «fin natural del modelo (EOS)» en las cuatro generaciones (ni tope, ni contexto lleno, ni cancelación, ni error). Pero **también corta el GGUF puro**: `gemma-4-E4B-it-Q4_K_M.gguf` cortó a los 40 tok en «…(científico/neurobiológico, funcional/psicológico y clínico/aplicado):\n\n---», tras anunciar tres preguntas que no escribe. El otro corte (qat/heretic, 41 tok) acaba en «…junto con su desarrollo:». Los dos caen **justo donde el modelo iba a abrir un encabezado de sección**, y la única respuesta larga de la sesión (1467 tok, mismo Q4_K_M) escribe la lista con **dos** almohadillas (`## 1. …`), nunca con tres. El nativo corta si el texto acumulado contiene `###`, descarta la pieza y devuelve un `[EOG]` genérico, que es lo que el diagnóstico etiqueta como «fin natural». Queda **pendiente** que el nativo diga qué condición cerró la generación antes de tocar los stops.
- **BUG-045 nuevo (rendimiento):** cada turno vuelve a prefillar toda la conversación. En el log el prompt se procesa a 2,5–5,4 t/s y el primer token tarda 9–65 s; el diagnóstico informa de **todos** los tokens del prompt en cada turno (238 tok con 213 ya en la KV; 87 tok con 63), así que el prefijo reutilizable es 0. Causa en Kotlin: `seedConversation` llama a `resetChat()` en cada envío. Causa latente en el nativo: `_kvTokens` nunca registra los tokens generados.

**Archivos exactos:**
- `analisis/BUGS.md` — evidencia 22/11 en BUG-029, re-prueba de 043/044, BUG-045 nuevo y fila del Escalón 3 actualizada.
- `analisis/CAMBIOS.md` — esta entrada (y resolución del conflicto del pull: se conservan BUG-044 y el cierre 037/041/042).
- `analisis/PLAN_PRUEBAS.md` — nota de re-prueba del 22/11 en el bloque de 029.

**Motivo:** el log desmonta la acotación de BUG-029 a la cuantización qat/heretic y descubre un coste de prefill que domina el tiempo de cada turno; ambos datos cambian el siguiente paso (instrumentar el motivo nativo del corte y dejar de tirar la KV). **Sin cambios de código.**

### 2026-11-22 — BUG-044: modo diagnóstico (prompt, respuesta cruda, tokens/s y motivo de parada)

**Qué y por qué:** para cazar BUG-029 (respuesta que se corta) y BUG-017 (visión lenta) hacen falta datos de cada generación, no solo el logcat. Nuevo modo diagnóstico, **encendido por defecto** mientras dure la caza y con interruptor en Ajustes. Cada generación se guarda en `files/diag/gen-*.txt` (últimas 30, campos largos recortados a 60 000 caracteres) con: modelo y motor, parámetros (contexto, hilos, tope, muestreo), tokens y t/s de prompt y de generación, primer token, total, contexto usado/tamaño, **motivo de parada** (fin natural EOS / secuencia de parada / tope de respuesta / contexto lleno / cancelada / error), prompt y respuesta cruda. Va aparte del log normal (BUG-043) y se añade al reporte exportado. **Limitación:** en GGUF con plantilla Gemma 4 el prompt se guarda COMPLETO; con las demás plantillas el prompt final lo arma el código nativo (`llama_chat_apply_template`) y desde Kotlin solo se ven las piezas (sistema, historial, mensaje actual). Para ver el texto final haría falta un getter nativo: no se toca sin tu confirmación. LiteRT se registra desde el ViewModel (historial + mensaje; sin motivo de parada, el motor no lo informa). Probado: `DiagFormatTest` (3 OK); el resto es Android y queda para el build.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/DiagLog.kt` — nuevo (`DiagRecord`, `DiagFormat`, `DiagLog`).
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/engine/LlamaCppEngine.kt` — registro en completar/cancelar/error.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatViewModel.kt` — registro LiteRT.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/LogExporter.kt` — sección en el reporte.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/home/BoxSettingsScreen.kt` — interruptor.
- `Android/src/app/src/main/java/com/box/gallery/GalleryApplication.kt` — `DiagLog.init`.
- `Android/src/app/src/test/java/com/google/ai/edge/gallery/data/characters/DiagFormatTest.kt` — nuevo.
- `analisis/BUGS.md` — BUG-044.

### 2026-11-21 — Cierre documental de 037/041/042 y acotación de BUG-029 al GGUF qat/heretic

**Qué y por qué:** el humano re-probó en el móvil y confirmó: BUG-042 OK (caso Akiko), BUG-041 «todo funciona» y BUG-037 corregido. Se cierran los tres y el inventario queda en **40 cerrados · 2 abiertos (017, 029)**. BUG-029 sigue abierto, pero la nueva evidencia lo acota: con `gemma-4-E4B-it-qat-q4_0-heretic-Q4_0_PURE.gguf` corta en 1:1 con personaje y en «Toca para chatear» (sin personaje); con el GGUF puro y con `.litertlm` no corta, y los tokens son similares. Eso descarta una regla general de la app y apunta a la cuantización del modelo. Se revisaron las tres exportaciones de logs adjuntas (19/11, 20/11 y 21/11): se leen bien, pero no contienen `AGLlamaPerf` ni el motivo del EOG, así que **no se afirma** que los logs se guarden mal ni se aplica el parche nativo de stops a ciegas. Hace falta una exportación hecha justo tras el turno que se corta, o el contador del chat.

- **Anotado para después (NO implementado):** al duplicar un personaje, vincular sus recuerdos a la copia cuando exista; si el personaje no cambió, añadirlos al original. **Nunca sobrescribir**: se borrarían recuerdos nuevos.
- **Tampoco se implementa ahora:** la narración con `[]` ni el cambio de stops en `LLMInference.cpp`.

**Archivos exactos:**
- `analisis/BUGS.md` — cierres 037/041/042, contador 40/2, nota de recuerdos futuros y evidencia qat vs. puro/LiteRT en BUG-029.
- `analisis/BUGS_CERRADOS.md` — filas de resumen y textos íntegros de BUG-037, BUG-041 y BUG-042; nota de cierre en BUG-038.
- `analisis/PLAN_PRUEBAS.md` — 0.37, 2.41 y 2.42 marcados como OK, estado general actualizado y checklist de re-captura para BUG-029.
- `analisis/CAMBIOS.md` — esta entrada.

**Motivo:** dejar el registro al día tras la re-prueba humana y conservar la pista real de BUG-029 sin documentar una causa no demostrada. **Sin cambios de código.**

### 2026-11-21 — BUG-042 implementado: la copia duplica solo si el personaje cambió

**Qué y por qué:** el humano pidió que, al restaurar, un personaje **idéntico** no se duplique y que, si la copia trae una versión distinta (su caso: Akiko con y sin `{{char}}` al inicio de la descripción), se conserve la versión editada y la de la copia entre como personaje nuevo. Así no se pierde ninguna de las dos.

- **Comparación:** `BackupFormat.characterSignature` arma una firma canónica con todos los campos de la tarjeta (nombre, descripción, personalidad, escenario, saludo, saludos alternativos, ejemplos, system prompt, post-history, notas, creador, versión, etiquetas, fuente, extensiones y lorebook). Quedan fuera id, avatar, modelo y fechas. Normaliza saltos de línea, espacios en los extremos y el formato del JSON de extensiones.
- **Restauración v2:** mismo id + misma firma = no se toca; mismo id + firma distinta = se conserva el existente y la copia se inserta con un `UUID` nuevo. Un id que no existe se crea con su id original.
- **Restauración v1:** se empareja por nombre (sin distinguir mayúsculas) y se aplica la misma regla; si cambió, se crea con un `UUID` nuevo.
- **Resumen:** «N personaje(s) nuevo(s)», «N copia(s) de personajes editados» y «N personaje(s) sin cambios». El diálogo y el subtítulo de Ajustes ya no prometen «no duplica» a secas: explican que lo idéntico no se duplica y lo editado se conserva.
- **Decisión:** los recuerdos no se remapean; la copia del personaje es solo la tarjeta. Los recuerdos de la copia siguen vinculados al id original (el personaje editado), no a la copia nueva.

**Archivos exactos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/BackupFormat.kt` — `characterSignature`, `sameCharacterContent` y normalización.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/BackupRepository.kt` — `restoreCharacters` y `restoreLegacyCharacters` devuelven un resumen; `saveCharacter` recibe la tarjeta ya leída; resumen en `restore`.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/roleplay/RestoreBackupDialog.kt` — texto del diálogo.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/home/BoxSettingsScreen.kt` — subtítulo de «Restaurar copia».
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/roleplay/RoleplayViewModel.kt` — comentario del flujo.
- `Android/src/app/src/test/java/com/google/ai/edge/gallery/data/characters/BackupFormatTest.kt` — 4 pruebas nuevas.
- `analisis/BUGS.md`, `analisis/BUGS_CERRADOS.md`, `analisis/PLAN_PRUEBAS.md` — estado de BUG-042.
- `analisis/CAMBIOS.md` — esta entrada.

**Verificación:** `.\gradlew.bat :app:testDebugUnitTest --tests "com.google.ai.edge.gallery.data.characters.BackupFormatTest"` desde `Android/src` → **BUILD SUCCESSFUL**. No se compiló el APK ni se probó en el móvil (el humano no tiene internet para descargarlo); queda pendiente la re-prueba del caso Akiko.

### 2026-11-21 — Re-pruebas de escenas, compartir y copia; BUG-036 descartado; BUG-042 abierto

**Qué y por qué:** re-pruebas del humano tras el `pull` `c232ed9`:
- **032, 033, 034 y 035 OK:** Aleatorio acumula burbujas, enviar en escena guarda contexto, enviar vacío hace hablar al hablante elegido y la persona es opcional. Se cierran y pasan a `BUGS_CERRADOS.md`.
- **036 descartado (falso positivo):** la captura de `[The studio lights…]` era de **Layla**, no de Box. El prompt de escena ya usaba solo nombre/descripción/personalidad; el único cambio del supuesto bug era el test `sceneUsesOnlyNameDescriptionPersonality`, que se retira. **Decisión:** `[...]` se reserva para narración/acciones; se mantiene el encabezado `Narrador:` para que el filtro sepa quién habla (`Narrador: [texto]`, y `[acción]` dentro del bloque de un personaje). Pendiente de implementar en el prompt.
- **007 y 039 OK:** compartir genera `Nombre.json` real con `FileProvider` y se puede guardar/importar; «Copiar texto» es una acción del selector de Android porque el JSON también viaja como texto, no un botón de Box.
- **038 OK en su alcance base:** selector, fusión sin duplicar, restauración de elementos borrados y `.zip` v1. Se cierra; la afinación pasa a **BUG-042**: si el personaje es idéntico no se duplica, pero si cambió (p. ej. `{{char}}` al inicio de la descripción de Akiko) se crea una copia nueva con otro id para no perder la edición.
- **041** sigue pendiente de re-prueba (biblioteca de voces).

**Archivos exactos:**
- `Android/src/app/src/test/java/com/google/ai/edge/gallery/data/characters/ScenarioTest.kt` — se retira el test de BUG-036.
- `analisis/BUGS.md` — 032–035, 007, 038 y 039 cerrados; 036 descartado; nuevo BUG-042; decisión de `[...]`; contadores 37 cerrados · 5 abiertos.
- `analisis/BUGS_CERRADOS.md` — filas y entradas de 007, 032–036, 038 y 039.
- `analisis/PLAN_PRUEBAS.md` — re-pruebas del 21/11 archivadas; pendientes 041, 042 y narración `[]`; estado actual de bugs.
- `README.md` — la copia de seguridad ya no es solo de personajes.
- `analisis/CAMBIOS.md` — esta entrada.

**Estado:** no se tocó código de producción; solo se retiró un test. `analisis/PRUEBAS.md` y los submódulos, sin tocar. La copia inteligente (BUG-042) y la narración `[]` quedan documentadas para la próxima sesión.

### BUG-038: copia de seguridad completa que fusiona, con selector (fase 1, sin chats)

**Qué y por qué:** BUG-038 (Escalón 2), fase 1 acordada: **sin chats** (la base cifrada queda para una fase 2). Antes el `.zip` solo llevaba personajes y, al restaurar, creaba copias nuevas (duplicados). Ahora:
- **Contenido:** personajes (tarjeta chara_card_v2 + avatar + modelo propio y fechas, guardados con su **id**), recuerdos (de la base y los «sin procesar»), personas, presets (de motor y de muestreo con nombre) y escenas. Lleva un `manifest.json` (versión 2).
- **Restaurar fusiona:** lo de la copia sobrescribe el elemento con el mismo id (o el mismo nombre en los presets de muestreo); lo que ya tenías y no está en la copia se conserva; no se borra nada ni se duplica. La persona activa sigue siendo la que ya lo era. Los recuerdos de personajes que no existen se descartan.
- **Selector:** tras elegir el `.zip` sale «¿Qué quieres restaurar?» con casillas por sección y su número de elementos.
- **Compatibilidad:** los `.zip` antiguos (v1, sin ids) se siguen leyendo y cada personaje se empareja por **nombre** con el existente para no duplicarlo. Una tarjeta suelta `.json/.png` se importa como antes.
- Lógica pura en `BackupFormat` (fusión, detección de secciones, JSON); test `BackupFormatTest` 10/10 OK (kotlinc embebido). `BackupRepository`, el diálogo y el cableado no se pudieron compilar aquí: pendiente de GitHub Actions y re-prueba. Se elimina `CharacterRepository.exportBackupZip` (sustituido) y `RoleplayViewModel.importFromSettings` (sustituido por `prepareRestore`/`restoreBackup`).

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/BackupFormat.kt` — nuevo, lógica pura.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/BackupRepository.kt` — nuevo, exportar/inspeccionar/restaurar.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/RawMemoryStore.kt` — `all()` y `merge()`.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/CharacterRepository.kt` — `readBackupBytes`; se quita `exportBackupZip`.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/roleplay/RoleplayViewModel.kt` — `prepareRestore`, `restoreBackup`, `backupZip`.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/roleplay/RestoreBackupDialog.kt` — nuevo.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/home/BoxSettingsScreen.kt`, `ui/roleplay/RoleplaySettingsDialog.kt`, `ui/roleplay/RoleplayScreens.kt` — textos y flujo.
- `Android/src/app/src/test/java/com/google/ai/edge/gallery/data/characters/BackupFormatTest.kt` — nuevo.
- `analisis/BUGS.md` — nota en BUG-038 (y 023).

### BUG-032 a 036: escenas — burbuja por turno, mensajes como contexto, enviar vacío, persona opcional

**Qué y por qué:** BUG-032 a BUG-036 (Escalón 2, tanda de escenas; un solo commit porque comparten el flujo de `LlmChatScreen`/`LlmChatViewModel`).
- **032 (causa confirmada en el código):** `generateResponse` reutilizaba como burbuja de streaming la última respuesta si era del asistente. Con «Aleatorio» o un turno forzado no hay mensaje tuyo de por medio, así que la respuesta nueva **sobrescribía la visible** (en la base de datos sí se guardaba aparte; por eso el hilo se «arreglaba» al reabrir). Ahora cada turno abre su propia burbuja (`agentBubbleStarted`).
- **033:** en una escena, enviar un texto **ya no genera**: se muestra y se guarda como contexto. El texto llega al motor dentro del siguiente turno de Aleatorio / hablante forzado (`ScenePromptBuilder.engineInput`; `trailingUserTexts`; `previousTexts(contextTail)` para que no se duplique ni se pierda en el historial). Interruptor por escena «El modelo responde a mis mensajes» (apagado por defecto; las escenas antiguas pasan a apagado).
- **034:** con un hablante elegido (personaje o Narrador) y el campo vacío, el botón enviar se habilita y el modelo escribe **por ese hablante** (`allowEmptySend` en `MessageInputText`/`ChatPanel`/`ChatView`).
- **035:** interruptor por escena «Usar mi persona»: apagado, el prompt no la menciona, la conversación no se liga a ella y la barra no muestra «Yo» (se empieza como Narrador o el primer personaje). Además la barra respeta ahora «Incluir narrador» de la escena (antes el chip y las restricciones siempre lo asumían activo).
- **036:** comprobado: el prompt de escena ya solo usa nombre, descripción y personalidad de cada ficha (`Member` no tiene más campos); se añade un test que lo fija. La acotación `[The studio lights…]` no sale de la ficha: venía del prompt de la escena o de la descripción del personaje; no se reproduce con esta lógica.
- Nota: en LiteRT, los turnos sin mensaje tuyo hacen que la conversación se recree con el historial en cada turno (más lento, pero correcto).
- Test de lógica pura: `ScenarioTest` 12/12 OK (kotlinc embebido, sin las pruebas que dependen de Android). La parte de UI no se pudo compilar aquí: pendiente de GitHub Actions y re-prueba en el teléfono.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatViewModel.kt` — burbuja por turno, contexto pendiente, `saveContextMessage`, `sceneOptions`.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatScreen.kt` — contexto sin respuesta, envío vacío con hablante, opciones de escena.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/SceneSpeakerBar.kt` — `showMe`.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/common/chat/{MessageInputText,ChatPanel,ChatView}.kt` — `allowEmptySend`.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/ScenePromptBuilder.kt` — `engineInput`.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/ScenarioStore.kt` — `usePersona`, `replyToUser`.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/local/ChatRepository.kt` — `sceneOptions`.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/scenarios/ScenarioScreens.kt`, `ScenariosViewModel.kt` — interruptores y persona opcional.
- `Android/src/app/src/test/java/com/google/ai/edge/gallery/data/characters/ScenarioTest.kt` — 3 tests nuevos.
- `analisis/BUGS.md` — notas en BUG-032 a 036.

### BUG-039 y BUG-007: compartir la ficha como archivo .json (FileProvider)

**Qué y por qué:** BUG-039 y BUG-007 (Escalón 2, un solo commit). «Compartir» una ficha entregaba solo texto (`EXTRA_TEXT`), sin nombre ni extensión, y «Guardar en…» no creaba un `.json` válido. Ahora se escribe `Nombre.json` en la caché (`cache/shared/`) y se comparte con `FileProvider` + `EXTRA_STREAM` (`application/json`); el JSON sigue yendo también como texto, así que «Copiar texto» se conserva. Aplica a los tres puntos de compartir (lista, hoja de detalle y editor). El nombre se limpia de caracteres prohibidos (`ShareFileName`, test 3/3). **No** se hizo la variante `.png` con la tarjeta incrustada (era «opcional» en el bug). Pendiente de re-prueba en el teléfono.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/roleplay/CharacterShare.kt` — nuevo.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/ShareFileName.kt` — nuevo.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/roleplay/RoleplayScreens.kt` — tres llamadas.
- `Android/src/app/src/main/res/xml/file_paths.xml` — ruta `shared/`.
- `Android/src/app/src/test/java/com/google/ai/edge/gallery/data/characters/ShareFileNameTest.kt` — nuevo.
- `analisis/BUGS.md` — notas en BUG-039 y BUG-007.

### Escalon 1 cerrado: 008, 009, 010, 014, 019, 040 archivados; 041 a la tanda final

**Qué y por qué:** el humano re-probó en el teléfono y confirmó BUG-008, 009, 010, 019, 040 (presets de rendimiento y de muestreo) y 041; BUG-014 se da por cerrado (con el modelo completo solo sale «Modelo completo.», el aviso largo aparece cuando faltan piezas). Se mueven 008, 009, 010, 014, 019 y 040 a `BUGS_CERRADOS.md` y quedan en `BUGS.md` como línea de referencia. BUG-041 (biblioteca de voces) funciona pero se deja abierto en la «Tanda final» para pulirla. **Escalón 1 completo.** Quedan 12 abiertos.

**Archivos:**
- `analisis/BUGS.md` — referencias, tabla de escalones y recuento.
- `analisis/BUGS_CERRADOS.md` — seis bloques nuevos y filas en el resumen.

### BUG-041: biblioteca de voces de Pocket TTS (subir, grabar, nombrar, elegir)

**Qué y por qué:** BUG-041 (Escalón 1). Biblioteca de voces de Pocket TTS: ahora se pueden guardar varias voces con nombre. Pantalla «Voces» (diálogo): subir un `.wav` o grabar con el micrófono (24 kHz mono, máx. 30 s), escuchar la muestra, nombre (propuesto = nombre del archivo sin extensión), etiquetas fijas + una propia, guardar; lista con buscador por nombre/etiqueta, duración real, escuchar, probar con un texto editable (usa el modelo) y borrar con confirmación. Se elige como voz predeterminada («Biblioteca de voces» en Roleplay > Voz) o por personaje («Elegir voz»). Los `default.wav` y `char_*.wav` anteriores se incorporan solos como voces («Predeterminada», «Voz de personaje xxxx»). Se quita `TtsFiles.importVoice` (sustituido por `VoiceStore`). No se añadió el interruptor «Guardar como archivo de audio» de la referencia. Pendiente de re-prueba en el teléfono. Test de lógica pura: `VoiceLibraryTest` (9 OK, compilado con kotlinc embebido).

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/tts/VoiceLibrary.kt` — nuevo, lógica pura (nombres, búsqueda, JSON, migración).
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/tts/VoiceStore.kt` — nuevo, almacén (`tts/voices.json` + `tts/voices/<id>.wav`).
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/tts/WavWriter.kt` — nuevo, WAV de la grabación.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/tts/TtsFiles.kt` — se elimina `importVoice`.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/tts/TtsManager.kt` — `speakAs` para probar una voz concreta.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/roleplay/VoiceLibraryDialog.kt` — nuevo, pantalla de voces.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/roleplay/TtsSettingsDialog.kt` — voz predeterminada y voz de personaje usan la biblioteca.
- `Android/src/app/src/test/java/com/google/ai/edge/gallery/tts/VoiceLibraryTest.kt` — nuevo.
- `analisis/BUGS.md` — nota en BUG-041.

### BUG-040: barra de presets (cargar / Nuevo / editar) en Rendimiento y Muestreo

**Qué y por qué:** re-prueba de BUG-040 en el teléfono: el preset de rendimiento se guardaba pero no había forma de cargarlo desde su diálogo. Además, en «Muestreo (GGUF)» la ficha «Custom» sobraba en la fila de Default/Creative/Precise (ocupaba espacio y siempre salía marcada). Se crea una barra de presets compartida estilo Layla: fichas para cargar, botón «Nuevo» (guarda los valores actuales con un nombre) y lápiz de edición; borrar solo se puede en modo edición y pide confirmación. «Custom» deja de ser una ficha: es el estado «valores tocados a mano» y se muestra en una línea de estado («Usando «X»» o «Valores personalizados (sin guardar)»). Se retiran los botones antiguos de guardar. No se añade importar/exportar: irá con la copia de seguridad (BUG-038). Pendiente de re-prueba en el teléfono.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/roleplay/PresetBar.kt` — nuevo, barra compartida.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/roleplay/SamplerDialog.kt` — usa la barra; sin ficha Custom.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/roleplay/EnginePerfDialog.kt` — barra de presets de rendimiento (cargar/nuevo/borrar) en vez de la sección de guardado.
- `analisis/BUGS.md` — nota en BUG-040.

### 2026-11-22 — BUG-043: log persistente por sesión (sobrevive a cierres del proceso)

**Qué y por qué:** el humano notó que el log exportado «se pierde» al reiniciarse la app. Causa: una app solo puede leer el logcat de su propio proceso y ese buffer empieza de cero en cada arranque, así que tras un cierre (MIUI por memoria, deslizar para cerrar, crash) el log de «antes» ya no existía. Ahora se copia el logcat del proceso en vivo, filtrado, a `files/logs/session-<fecha>.log` (últimas 5 sesiones, trozos de ~1,5 MB), con cabecera (versión, dispositivo, motivo de la salida anterior), estado de memoria cada 20 s, avisos `onTrimMemory`/`onLowMemory`, hitos del motor GGUF (cargar, descargar, generar, parar) y el crash de Java. El reporte exportado incluye ahora la sesión actual y las 2 anteriores en lugar de `logcat -d`. El filtro descarta el ruido de MIUI/HWUI/Skia y los volcados JSON del gestor de modelos. Los crashes nativos siguen sin traza propia, pero la última línea de memoria/hito y el motivo de salida permiten distinguirlos de un cierre por memoria. Probado: `LogLineFilterTest` (5 OK); `PersistentLog` y `LogExporter` sin compilar (Android), pendientes del build.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/LogLineFilter.kt` — nuevo, filtro puro.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/PersistentLog.kt` — nuevo.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/LogExporter.kt` — `lastExitSummary`, sesiones en el reporte, crash al log.
- `Android/src/app/src/main/java/com/box/gallery/GalleryApplication.kt` — arranca `PersistentLog`.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/engine/LlamaCppEngine.kt` — hitos.
- `Android/src/app/src/test/java/com/google/ai/edge/gallery/data/characters/LogLineFilterTest.kt` — nuevo.
- `analisis/BUGS.md` — BUG-042.

### 2026-11-20 — BUG-040: «Guardar como preset» en Rendimiento del motor (Escalón 1)

**Qué y por qué:** BUG-040 (Escalón 1). Decisión del humano: añadir un botón propio para guardar el rendimiento como preset (como el de temperatura). Se añade la sección «Guardar como preset» en «Rendimiento del motor»; guarda un `EnginePreset` solo de rendimiento que aparece en «Motores guardados» y se aplica por la vía existente. Pendiente de re-prueba en el teléfono.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/roleplay/EnginePerfDialog.kt` — sección, campo de nombre y botón.
- `analisis/BUGS.md` — BUG-040 con parche aplicado.

### 2026-11-20 — BUG-010: «Sin personaje» en el cajón (Escalón 1)

**Qué y por qué:** BUG-010 (Escalón 1). Decisión del humano: renombrar. El grupo de chats cuyo personaje se borró pasa de «Personaje borrado» a «Sin personaje». Pendiente de re-prueba.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/home/BoxDrawer.kt` — título del grupo.
- `analisis/BUGS.md` — BUG-010 con parche aplicado.

### 2026-11-20 — BUG-019: el benchmark avisa de que GGUF y LiteRT no son comparables (Escalón 1)

**Qué y por qué:** BUG-019 (Escalón 1). El benchmark mide en GGUF y en LiteRT con métodos distintos, así que sus cifras no se pueden comparar. Se elige etiquetarlo en vez de unificar la métrica: aviso en la pantalla «Benchmark de modelos» y aclaración en la prueba rápida GGUF. Solo texto de interfaz. Pendiente de comprobación visual.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/apps/BenchmarkAppScreen.kt` — aviso de no comparabilidad.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/apps/QuickBenchDialog.kt` — aclaración en el texto.
- `analisis/BUGS.md` — BUG-019 con parche aplicado.

### 2026-11-20 — BUG-014: el error de importar la carpeta de voz lista los archivos esperados (Escalón 1)

**Qué y por qué:** BUG-014 (Escalón 1). El aviso al importar una carpeta de modelo de voz sin piezas reconocibles era genérico («No se encontraron archivos…») y confundía, sobre todo con las carpetas v2 de KevinAHM. Ahora lista los archivos exactos que se esperan y, si detecta nombres v2, explica que no se pueden importar tal cual y remite a «Descargar de Hugging Face». Pendiente de re-prueba en el teléfono.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/tts/PocketModelFiles.kt` — `EXPECTED_NAMES` y `noPartsMessage`.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/roleplay/TtsSettingsDialog.kt` — usa el mensaje nuevo y lee los nombres de la carpeta.
- `Android/src/app/src/test/java/com/google/ai/edge/gallery/tts/PocketModelFilesTest.kt` — 3 pruebas nuevas.
- `analisis/BUGS.md` — BUG-014 con parche aplicado.

### 2026-11-20 — BUG-009: la hamburguesa del inicio abre el cajón (Escalón 1)

**Qué y por qué:** BUG-009 (Escalón 1). La hamburguesa del inicio no abría el cajón (solo el gesto de deslizar). El botón sí estaba conectado a `drawerState.open()`, pero se dibujaba antes de la `Column` que ocupa toda la pantalla, así que esta se quedaba con los toques. Se mueve el botón después de la `Column` para que quede encima. Pendiente de re-prueba en el teléfono.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/home/BoxHomeScreen.kt` — el `IconButton` del menú pasa a dibujarse encima.
- `analisis/BUGS.md` — BUG-009 con parche aplicado.

### 2026-11-20 — BUG-008: editar los recuerdos sin procesar (Escalón 1)

**Qué y por qué:** BUG-008 (Escalón 1). Los recuerdos «sin procesar» solo se podían borrar. Ahora cada nota tiene un botón ✏️ que abre un diálogo de edición; el cambio conserva el `id` y la fecha de la nota para no alterar el orden cronológico de las tandas del absorbedor, y un texto en blanco no se guarda. Test `editingRawNoteKeepsIdAndDate` (11 pruebas OK con kotlinc). Pendiente de re-prueba en el teléfono.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/memory/MemoryScreen.kt` — botón y diálogo de edición, `editRaw` en el ViewModel.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/RawMemory.kt` — `withEditedText`.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/RawMemoryStore.kt` — `updateText`.
- `Android/src/app/src/test/java/com/google/ai/edge/gallery/data/characters/RawMemoryTest.kt` — test nuevo.
- `analisis/BUGS.md` — BUG-008 con parche aplicado.

### 2026-11-20 — Cerrar 011 y 016; aclarar el caso de 037 (solo documentación)

**Qué y por qué:** el humano confirmó en el teléfono que **011 funciona** (ficha y tarjeta con la persona activa) y que **016 funciona** (4.2c: GGUF→GGUF y LiteRT→GGUF sin «El modelo sigue cargando»). Se cierran los dos siguiendo la regla: bloque íntegro a `analisis/BUGS_CERRADOS.md` (con su fila en la tabla), línea de referencia en `analisis/BUGS.md`, contadores a **23 cerrados · 18 abiertos** y plan de pruebas al día. **No se tocó código.**

- **016, evidencia del log del 20/11 07:47:** no hay `Timeout esperando a que cargue` ni `no está listo tras … s`; la única línea de modelo es una limpieza correcta (`Cleaning up model 'gemma-4-E4B-it-qat-q4_0-heretic-Q4_0_PURE.gguf'` + `Clean up done` a las 07:47:02). `ApplicationExitInfo` no trae crashes nuevos: los cierres por `MiuiMemoryService` son del 19/11 y los del 20/11 00:04/00:12 son `OTHER`.
- **037, caso de la captura 07:46:** el usuario escribe `KIP: …` y responden `Narrator:`, `Tohru:` y `Psicólogo Digital:` en la misma burbuja. Es el comportamiento que `ScenePromptBuilder.userTurnRestriction` define para un turno **del usuario** (permite a todos los demás hablantes + narrador), es decir, lo que debe cambiar **BUG-033**; no reproduce el fallo de los alias del narrador. Queda abierto y el humano lo trabajará más adelante.

**Archivos exactos:**
- `analisis/BUGS.md`
- `analisis/BUGS_CERRADOS.md` (además se completan los bloques de **015** y **030**, que se habían quedado solo como línea de referencia en el commit anterior: ahora tienen su entrada íntegra y su fila en la tabla, 23/23)
- `analisis/PLAN_PRUEBAS.md`
- `analisis/CAMBIOS.md`

**Estado:** `analisis/PRUEBAS.md` y los submódulos, sin tocar.

---

### 2026-11-19 — Plan de pruebas al día tras el Escalón 0

**Qué y por qué:** `PLAN_PRUEBAS.md` seguía listando como pendientes re-pruebas que el humano ya pasó (2.1b, 5.4b) y un 4.2b que ahora se replantea como 4.2c. Se mueven las verificadas al archivo de abajo, se añade una sección «Escalón 0» con el estado real (015, 021, 025, 026 y 030 OK; 011, 016 y 037 pendientes) y se describe qué debe traer el log si 4.2c vuelve a fallar. Solo documentación.

**Archivos exactos:**
- `analisis/PLAN_PRUEBAS.md`
- `analisis/CAMBIOS.md`

---

### 2026-11-19 — BUG-016: pedir la (re)carga del modelo si el envío se queda esperando

**Qué y por qué:** la re-prueba 4.2b del 19/11 mostró que ya no sale "Session re-initialized", pero el envío podía quedarse hasta 180 s esperando un motor que figuraba como `INITIALIZED` sin estarlo y acabar en «El modelo sigue cargando (…). Vuelve a enviar el mensaje en unos segundos.» (GGUF→GGUF y LiteRT→GGUF). Dos cambios:

- `ModelManagerViewModel.initializeModel` ya no se fía solo del estado: si dice `INITIALIZED` pero `model.runtimeHelper.isModelReady(model)` es falso, registra el aviso y **recarga** en vez de saltarse la carga.
- `LlmChatViewModel.generateResponse` acepta `onModelStuck`; a los 15 s (y como máximo 3 veces) deja en el log el estado real (`instancia=`, `inicializando=`) y pide una reinicialización, en vez de esperar a ciegas. `LlmChatScreen` conecta el callback a `initializeModel`.

**Archivos exactos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/modelmanager/ModelManagerViewModel.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatViewModel.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatScreen.kt`
- `analisis/BUGS.md`

**Estado:** compilado (`:app:assembleDebug` OK). Pendiente de re-prueba en el teléfono (4.2c). No se ha tocado ningún submódulo.

---

### 2026-11-19 — BUG-011: la ficha y la tarjeta muestran la persona activa en `{{user}}`

**Qué y por qué:** el humano confirmó que el chat ya funciona («4 funciona correctamente, lo prefiero así»), pero quedaba un resto: la vista previa de la tarjeta de personaje sustituía `{{user}}` por el literal `User` en vez de por la persona activa, porque `RoleplayScreen` pasaba el nombre fijo `DEFAULT_USER_NAME`. Ahora `RoleplayScreen` calcula `activePersonaName` desde `viewModel.personas` (la persona con `isActive`, o `User` si no hay ninguna) y lo pasa como `userName` a `CharacterLibrary` y `CharacterDetailSheet`, que lo usan al llamar a `CharacterPromptBuilder.applyMacros`. Las llaves simples (`{user}`) siguen siendo literales por decisión del humano (sintaxis SillyTavern).

**Archivos exactos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/roleplay/RoleplayScreens.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/roleplay/CharacterLibrary.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/roleplay/CharacterDetailSheet.kt`
- `analisis/BUGS.md`

**Estado:** compilado (`:app:assembleDebug` OK). Pendiente de re-prueba visual en el teléfono.

---

### 2026-11-19 — Cierre del Escalón 0: 015, 021, 025, 026 y 030 verificados

**Qué y por qué:** con las re-pruebas del humano del 19/11 se cierran cinco bugs del Escalón 0. Siguiendo la regla nueva, **no se marca ✅ dentro del texto**: se mueve el bloque íntegro a `analisis/BUGS_CERRADOS.md` (con su ancla y su fila en la tabla resumen) y en `analisis/BUGS.md` queda solo una línea de referencia. Quedan abiertos 011 (tarjeta recién parcheada), 016 (refuerzo 4.2c) y 037 (falta el caso concreto).

- **BUG-015** (plantilla de turnos GGUF): 2.1b OK en GGUF y LiteRT; el recorte de respuestas largas es BUG-029, aparte.
- **BUG-021** (rotación): 5.4b OK con y sin generación, en ambos motores. Commit `0ab2979`.
- **BUG-025** (ClassCastException al limpiar GGUF de AgentChat): el log exportado del 19/11 22:02:37 muestra `Cleaning up model 'gemma-4-E4B-it-Q4_K_M.gguf'` + `BoxLlamaCppModelHelper: Clean up done` sin excepción. Commit `c3374e8`.
- **BUG-026** (NoSuchMethodError del TTS): la voz con Pocket funciona y el log de la sesión no trae el `SIGABRT`. Commit `a037faf`.
- **BUG-030** (Scudo al cerrar GGUF): sin crash nativo nuevo el 19/11 tras el cierre atómico y las esperas acotadas. Commits `0354818`, `79eb6b4`.

**Archivos exactos:**
- `analisis/BUGS.md`
- `analisis/BUGS_CERRADOS.md`
- `analisis/CAMBIOS.md`

**Estado:** solo documentación; el código de esos cinco bugs ya estaba compilado y probado. `analisis/PRUEBAS.md` no se ha tocado.

---

### 2026-11-19 — Orden de ejecución por esfuerzo en `BUGS.md` (solo documentación)

**Qué y por qué:** el humano gestiona los prompts y pidió atacar primero lo fácil y dejar lo difícil al final. Se añade a `analisis/BUGS.md` una sección «Orden sugerido» con 4 escalones (0 = verificación gratis → 3 = duros/nativos) y una línea `Esfuerzo / Escalón / nota` en las 25 entradas vivas, para que un modelo nuevo no tenga que deducir el orden ni reabrir el histórico. **No se tocó código** y el punto de partida elegido es el Escalón 0 (8 dudosos, solo re-prueba).

**Archivos exactos:**
- `analisis/BUGS.md`
- `analisis/CAMBIOS.md`

---

### 2026-11-19 — Reordenar la documentación de bugs: activos vs. cerrados (solo `.md`)

**Qué y por qué:** el humano pidió mantener el sistema de documentos con los bugs **activos** y mover los **cerrados** a otro archivo sin borrarlos, para decidir después si conviene consultarlo. Triaje de los 41 IDs: **16 cerrados · 8 dudosos · 17 abiertos** (HEAD `ca51c23c`, rama `fase1-rendimiento`). **No se tocó código.**

- `analisis/BUGS.md`: los 16 bloques cerrados se sustituyen por una línea de referencia con ancla a su entrada del histórico; conserva los 25 activos/dudosos con su texto literal.
- `analisis/BUGS_CERRADOS.md` (nuevo): los 16 bloques originales íntegros, con un resumen de cierre (commit o verificación en dispositivo). Se consulta solo si se quiere auditar un cierre.
- `analisis/PLAN_PRUEBAS.md`: las 8 re-pruebas ya verificadas se mueven a «🗄️ Re-pruebas verificadas» al final; arriba queda solo lo pendiente. La casilla 5.5b (BUG-022) se marca como OK: el humano verificó el 19/11 a las 09:36 que el reporte incluía el stack nativo. El inventario pasa de 24 a 41 IDs.
- `AGENTS.md`, `README.md`, `analisis/MEMORIA.md`: enlaces al nuevo archivo; la base de AGENTS.md se corrige a Box 1.0.12 (`8d6503a8`) y MEMORIA.md anota que BUG-002 ya está verificado.

**Archivos exactos:**
- `AGENTS.md`
- `README.md`
- `analisis/BUGS.md`
- `analisis/BUGS_CERRADOS.md`
- `analisis/PLAN_PRUEBAS.md`
- `analisis/MEMORIA.md`
- `analisis/CAMBIOS.md`

---

### 2026-11-19 — Limpieza: 56 imports sin uso en 25 archivos

**Qué y por qué:** limpieza de código: se quitan **56 imports sin uso en 25 archivos** (`app/src/main`, `app/src/test` y `smollm/src/main`). Solo se eliminan líneas `import`; no cambia ningún comportamiento. Criterio conservador: se borra un import solo si su nombre no aparece **en ningún sitio** del resto del archivo (ni en código ni en comentarios/KDoc), se excluyen los imports de operadores y delegados que se usan sin nombrarlos (`getValue`, `setValue`, `provideDelegate`, `invoke`…) y los comodín. **No compilado con Gradle** (la nube no puede): si algún import faltara, el compilador lo señalará y se restaura esa línea.

**Archivos:**
- `Android/src/app/src/main/java/com/box/gallery/MainActivity.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat/SkillManagerBottomSheet.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/mobileactions/MobileActionsViewModel.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/HfApi.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/RawMemory.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/security/AppLockManager.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/security/SecurityAuditLog.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/security/SecurityUtils.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/apps/BoxApps.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/benchmark/BenchmarkResultsViewer.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/home/BoxSettingsScreen.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/home/HomeScreen.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/imagegen/ImageGenViewModel.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatScreen.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/memory/MemoryScreen.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/modelmanager/ModelImportDialog.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/modelmanager/ModelManagerViewModel.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/navigation/GalleryNavGraph.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/roleplay/CharacterDetailSheet.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/roleplay/CharacterLibrary.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/roleplay/RoleplayScreens.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/scenarios/ScenarioScreens.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/whisper/WhisperScreen.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/whisper/WhisperViewModel.kt`
- `Android/src/smollm/src/main/java/com/jegly/offlineLLM/smollm/SmolLM.kt`

### 2026-11-19 — BUG-011: macros solo con sintaxis SillyTavern (se revierten las llaves simples)

**Qué y por qué:** decisión del humano: las macros siguen la sintaxis de SillyTavern, solo `{{char}}`/`{{user}}` (y `<BOT>`/`<USER>`). Se revierte el soporte de llaves simples (`{user}`) añadido antes en BUG-011; se mantiene la sustitución de macros al **mostrar** descripción, personalidad y escenario en la hoja de detalle y en la biblioteca. Una ficha con `{user}` lo muestra y lo envía literal (se corrige a mano en la ficha).

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/CharacterPromptBuilder.kt` — `MACRO` vuelve a la sintaxis original.
- `Android/src/app/src/test/java/com/google/ai/edge/gallery/data/characters/CharacterPromptBuilderTest.kt` — `replacesSingleBraceMacros` → `singleBracesAreNotMacros`.
- `analisis/BUGS.md` — nota de BUG-011 corregida.

### 2026-11-19 — BUG-011: macros {user}/{char} (también con llaves simples) sustituidas en fichas, biblioteca y prompt

**Qué y por qué:** BUG-011. Las macros de llaves simples (`{user}`, `{char}`, `{bot}`) que usan algunas fichas (Tohru) no se reconocían ni en la interfaz ni en el prompt, y la hoja de detalle y la biblioteca mostraban los textos sin sustituir macros.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/CharacterPromptBuilder.kt` — `MACRO` acepta llaves simples y `{{bot}}`.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/roleplay/CharacterDetailSheet.kt` — descripción, personalidad y escenario con macros sustituidas.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/roleplay/CharacterLibrary.kt` — vista previa con macros sustituidas.
- `Android/src/app/src/test/java/com/google/ai/edge/gallery/data/characters/CharacterPromptBuilderTest.kt` — test `replacesSingleBraceMacros`.
- `analisis/BUGS.md` — BUG-011 ✅.

### 2026-11-19 — BUG-037: el filtro de escenas reconoce Narrador/Narradora

**Qué y por qué:** BUG-037 (hallazgo de la revisión de código). La interfaz muestra "Narrador" y el modelo suele responder `Narrador:`, pero el filtro de escenas solo conocía `Narrator`, así que un bloque del narrador no permitido se colaba como si fuera del personaje anterior. Ahora `headerSpeaker` reconoce `Narrator`, `Narrador` y `Narradora` y devuelve el nombre canónico (solo si la escena tiene narrador).

**Verificación:** test nuevo `filterRecognisesSpanishNarratorAlias`: falla con el código anterior y pasa con el nuevo (8 pruebas de `ScenarioTest` sin las de JSON, que dependen de Android). No compilado con Gradle.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/ScenePromptBuilder.kt` — alias del narrador en `headerSpeaker`.
- `Android/src/app/src/test/java/com/google/ai/edge/gallery/data/characters/ScenarioTest.kt` — test nuevo.
- `analisis/BUGS.md` — BUG-037 ✅.

### 2026-11-19 — BUG-027 descartado y BUG-041 (biblioteca de voces de Pocket TTS)

**Qué y por qué:** el humano aclaró que las capturas de "Pocket TTS / Voces" eran de **Layla** (referencia de funcionalidad), no del fork. En el fork subir el `.wav` funciona; lo que falta es **guardar varias voces con nombre y etiquetas**. BUG-027 se descarta como fallo de importación y se abre BUG-041 (biblioteca de voces). No se toca código.

**Archivos:**
- `analisis/BUGS.md` — BUG-027 pasa a ✅ (descartado) y se añade BUG-041 con las funciones de referencia y el diseño propuesto (`VoiceStore`).

### 2026-11-19 — Revisión de la Tanda 2/3 y registro de la tanda final de roleplay (BUG-032 a BUG-040)

**Qué y por qué:** revisión de los 16 commits de OpenCode (`1dda4fd`…`5995f15`: BUG-022 ampliado, BUG-031/I-6 LiteRT, BUG-003/004/005/006, BUG-018, BUG-023, BUG-028, BUG-007) y registro de las re-pruebas del humano del 19/11 como **"Tanda final — roleplay de grupo, copias y compartir"** (BUG-032 a BUG-040) en `analisis/BUGS.md`. No se toca código.

**Revisión:** los cambios son coherentes con lo documentado. Observaciones, sin bloquear: (1) `LlmChatModelHelper.cleanUp` ahora ejecuta `onDone` en un hilo propio cuando se llama desde el principal (antes era síncrono); los llamadores solo actualizan estado, pero conviene vigilarlo si aparece algún fallo de hilos; (2) el filtro de escenas no reconoce `Narrador:` (solo `Narrator`) → BUG-037, comprobado con una prueba en JVM del `ScenePromptBuilder`; (3) las pruebas con el motor LiteRT y la interfaz solo se pueden confirmar en el teléfono (ya verificadas por el humano en 2.4/2.4b/4.4b/5.6b). No pude compilar con Gradle desde la nube.

**Tanda final registrada:** Aleatorio sustituye en vez de añadir (BUG-032), el mensaje del usuario dispara respuesta de todos (BUG-033), enviar vacío con hablante elegido (BUG-034), persona opcional (BUG-035), solo nombre/descripción/personalidad (BUG-036), alias del Narrador (BUG-037), copia que fusiona y con selector (BUG-038), compartir como archivo `.json`/`.png` (BUG-039, reabre BUG-007) y botón de presets de rendimiento (BUG-040). Incluye el modelo objetivo del chat de escena y cómo lo resuelve SillyTavern (modo Manual + Swap).

**Archivos:**
- `analisis/BUGS.md` — nueva sección "Tanda final", notas en BUG-007 y BUG-023.

### 2026-11-19 — Re-pruebas en teléfono de la Tanda 3 (2.4, 2.4b, 4.4b, 5.6b, 4.1c y BUG-007)

**Qué y por qué:** se instaló el APK compilado tras el commit `6492a7f` (`Android/src/app/build/outputs/apk/debug/app-debug.apk`) en el Xiaomi 2311DRK48G (Android 16) mediante depuración inalámbrica (`adb pair` + `adb connect`, puerto de emparejamiento 33077) y se ejecutaron las re-pruebas pendientes. Resultados reales:

- **2.4 / 2.4b (escenas):** al editar "El Final" aparecen los chips `#ai`, `#anime`, `#ciencia ficción`, `#fantasía`, `#femenino`, `#magia`, `#robot`; al tocar `#ai` y guardar, la lista se refresca sola mostrando `#fantasía #ciencia ficción #ai` (y al quitarlo, desaparece). Escribiendo como **Tohru**, el único mensaje de usuario visible fue `Tohru: Hola` (sin burbuja con la instrucción oculta) y la IA respondió solo como **Narrador, KIP y Akiko Fujimoto**, sin hablar por Tohru. "🎲 Aleatorio" con el campo vacío generó únicamente un bloque `KIP:` y ninguna burbuja de usuario. Al tocar las flechas de regeneración no hubo crash. El turno tardó 95.8 s hasta terminar (1.er token 36.5 s, ~4.3 frag/s, contexto 43 %).
- **4.4b (presets):** se creó el motor guardado `PRUEBA` con 2 hilos, se cambió a 7 y al aplicar el preset volvió a 2; batch (64), ubatch (512), flash (Auto) y KV (f32) quedaron intactos. El perfil de prueba se borró y los hilos se restauraron a 0 (automático).
- **5.6b (copia):** "Restaurar copia" abrió el selector del sistema, se importó `box-roleplay-prueba.zip` y apareció el aviso "Se restauraron 4 personajes": los 4 originales pasaron a ser 8. Los avatares se recrearon con ids nuevos (3 PNG nuevos con los mismos tamaños) y `box_chat.db-wal` se actualizó. Los 4 duplicados de prueba se borraron después, dejando los personajes originales.
- **4.1c (HF):** en `google/gemma-4-E4B-it-qat-q4_0-gguf`, la etiqueta "✔ cabe bien" aparece en su **propia línea** bajo `4.80 GB · Q4_0`, sin pegarse a los metadatos ni al botón Descargar.
- **BUG-007 (compartir):** el menú "Compartir" de una ficha abre el `ACTION_SEND` con el JSON `chara_card_v2`, el botón "Copiar texto" y el destino "Guardar en...". Queda confirmado en dispositivo: no hace falta pasar a `FileProvider`.

**Archivos:** sin cambios de código. `analisis/CAMBIOS.md` — esta entrada; `analisis/BUGS.md` — estados ✅ con fecha de verificación; `analisis/PLAN_PRUEBAS.md` — casillas de re-prueba marcadas.

### 2026-11-19 — BUG-007: confirmado en código, sin cambios (queda re-prueba en dispositivo)

**Qué y por qué:** se ha vuelto a revisar `ui/roleplay/RoleplayScreens.kt` (~185-192 y ~226-233): "Compartir" ya lanza `ACTION_SEND` con `type = "application/json"`, `EXTRA_TEXT` con el JSON de la tarjeta y `EXTRA_SUBJECT` con `<nombre>.json`, a través de `Intent.createChooser`. No hay diálogo propio ni texto no guardable en el código actual, así que **no se toca código**: la única duda (si el destino elegido permite guardar el archivo) solo se puede resolver en el teléfono. Si la prueba 4.1c/5.6 confirma que no se puede guardar, se cambiará a exportar `.json` en caché + `FileProvider`, como hace la copia `.zip`.

**Archivos:** sin cambios de código. `analisis/CAMBIOS.md` — esta entrada; `analisis/BUGS.md` — nota de confirmación.

### 2026-11-19 — BUG-028: hueco estable para la etiqueta de holgura del navegador HF

**Qué y por qué:** la etiqueta ("✔ cabe bien" / "justo" / "demasiado grande") compartía fila con los metadatos y el botón Descargar, así que quedaba pegada al texto, lejos o recortada según la longitud de los metadatos. Ahora los metadatos se limitan a dos líneas con ellipsis y separación de 8 dp del botón, y la etiqueta va en su **propia línea** con 4 dp de separación superior y una sola línea; se conservan los colores de BUG-020 y no se tocan los umbrales de `HfApi.fit()`. Sin tests nuevos (cambio de UI); compila.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/modelmanager/HfBrowserScreen.kt` — etiqueta en línea propia y metadatos acotados.
- `analisis/BUGS.md` — BUG-028 ✅.
- `analisis/CAMBIOS.md` — esta entrada.

### 2026-11-19 — BUG-023: UI para restaurar una copia de personajes

**Qué y por qué:** la exportación .zip funcionaba pero no había forma de restaurarla desde la app, aunque el backend (`CharacterRepository.importAnyFromUri()`, que detecta .zip y empareja `.json` con `.avatar.png`) ya existía. Se añade la tarjeta "Restaurar copia" en Configuración con `OpenDocument` para `.zip`, `.json` y `.png`, y un método `RoleplayViewModel.importFromSettings()` que devuelve el resultado sin usar el canal de mensajes de la biblioteca. El importador crea ids nuevos: por defecto **duplica** en vez de sobrescribir (opción segura). El texto no promete chats ni escenas porque el .zip solo contiene personajes y avatares. Sin tests nuevos (cambio de UI); compila.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/home/BoxSettingsScreen.kt` — tarjeta de restauración, launcher y estado propio.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/roleplay/RoleplayViewModel.kt` — `importFromSettings(uri)` con mensaje propio.
- `analisis/BUGS.md` — BUG-023 ✅.
- `analisis/CAMBIOS.md` — esta entrada.

### 2026-11-19 — BUG-018: los presets de motor guardan y restauran el rendimiento

**Qué y por qué:** los presets solo guardaban modelo, visión, persona y muestreo; al aplicarlos no se restauraban hilos, batch, flash attention, KV, etc. Se amplía `EnginePreset` con los diez campos de `EnginePerfSettings` como **anulables** (`null` = "no cambiar"), de modo que los presets antiguos siguen cargando sin sobrescribir los ajustes actuales. La codificación solo escribe los campos guardados. Se unifica la aplicación en `EngineApply.applyBasics()` (antes `BoxInferenceScreen` tenía su propia copia parcial) y se usa tanto al aplicar un preset general como uno de personaje. Nota de realidad: el inventario citaba `EnginePerfStore.kt` y `ui/modelmanager/EnginePerfDialog.kt`, pero los archivos reales son `EnginePerfSettings.kt` y `ui/roleplay/EnginePerfDialog.kt`; el diálogo no se tocó porque solo edita los ajustes vigentes. Tests de ida/vuelta JSON, presets antiguos y sobreescritura parcial (4 en `EnginePresetTest`, todos pasan).

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/EnginePreset.kt` — campos de rendimiento anulables + codificación/decodificación tolerante.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/EnginePerfSettings.kt` — `overriddenBy(preset)` (solo sobrescribe lo guardado y sanea).
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/CharacterEngineStore.kt` — `EngineApply.applyBasics` aplica visión + rendimiento + muestreo.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/home/BoxInferenceScreen.kt` — guarda el rendimiento en el preset y aplica por la vía compartida.
- `Android/src/app/src/test/java/com/google/ai/edge/gallery/data/characters/EnginePresetTest.kt` — nuevos tests.
- `analisis/BUGS.md` — BUG-018 ✅.
- `analisis/CAMBIOS.md` — esta entrada.

### 2026-11-19 — BUG-006: chips con las etiquetas ya usadas en escenas y personajes

**Qué y por qué:** los editores de escena y de personaje solo tenían un campo de texto libre para las etiquetas, así que no se veían las ya existentes. Se añade un componente compartido `TagChips` (lógica pura + `FlowRow` de `FilterChip`) que muestra las etiquetas usadas en escenas y personajes, sin duplicados (ignorando mayúsculas) y en orden alfabético; al tocar una se añade o quita del campo, que sigue admitiendo texto nuevo. `RoleplayViewModel` expone las escenas del store singleton para alimentar los chips del editor de personajes. Tests de la lógica (`TagChipsTest`, 2 pasan).

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/common/TagChips.kt` — nuevo componente compartido y funciones puras `collect`/`toggle`.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/scenarios/ScenarioScreens.kt` — chips en el editor de escenas (etiquetas de escenas + personajes).
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/roleplay/RoleplayScreens.kt` — chips en el editor de personajes.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/roleplay/RoleplayViewModel.kt` — expone `scenarios` del store para los chips.
- `Android/src/app/src/test/java/com/google/ai/edge/gallery/ui/common/TagChipsTest.kt` — nuevo test de la lógica de etiquetas.
- `analisis/BUGS.md` — BUG-006 ✅.
- `analisis/CAMBIOS.md` — esta entrada.

### 2026-11-19 — BUG-003 + BUG-004: instrucción de turno oculta y un solo hablante en escenas

**Qué y por qué:** la instrucción `(Next message: write only as X.)` se creaba como un `ChatMessageText` de SIDE USER: se pintaba como burbuja y se guardaba en la base de datos (BUG-003). Además el system prompt solo pedía "Only write for the characters listed" y el modelo ignoraba la restricción, contestando como narrador + todos los personajes (BUG-004). Se arreglan juntos porque comparten el mismo flujo de envío. Ahora la instrucción viaja en `SendMessageTrigger.promptSuffix` y se concatena al `input` del motor **sin crear mensaje ni persistirlo**; el botón "🎲 Aleatorio" dispara un turno con mensaje vacío (solo el sufijo). Se añade una restricción fuerte de turno, un post-filtro de bloques `Nombre:` no permitidos aplicado durante el streaming (reemplazando el contenido, para que el texto pueda encoger) y se reutiliza la restricción al regenerar/swipe. `ScenarioTest` cubre el filtro con Narrator + KIP + Akiko, encabezados markdown, texto sin nombre y deducción del hablante desde una respuesta anterior.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/ScenePromptBuilder.kt` — `TurnRestriction`, `restriction()`, `userTurnRestriction()`, `restrictionForReply()`, `filterReply()` e instrucción de turno reforzada.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/common/chat/ChatView.kt` — `SendMessageTrigger` con `promptSuffix` y `restriction`.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/common/chat/ChatViewModel.kt` — `setLastTextMessageContent()` (reemplazo, no concatenación).
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatViewModel.kt` — `generateResponse(promptSuffix, sceneRestriction)`, `engineInput` para prompt y motor, filtro en streaming, `runAgain`/`regenerateLast` con la misma restricción.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatScreen.kt` — Aleatorio sin mensaje visible y restricción según el hablante elegido.
- `Android/src/app/src/test/java/com/google/ai/edge/gallery/data/characters/ScenarioTest.kt` — nuevos tests de restricción y filtro (8 pasan).
- `analisis/BUGS.md` — BUG-003 y BUG-004 ✅.
- `analisis/CAMBIOS.md` — esta entrada.

### 2026-11-19 — BUG-005: una sola fuente observable para las escenas (la lista se refresca al guardar)

**Qué y por qué:** guardar una escena en el editor no refrescaba la lista: son destinos de navegación distintos y cada uno recibía su propia instancia de `ScenariosViewModel`, con su propio `MutableStateFlow` cargado una sola vez. El guardado escribía bien `scenarios.json`, pero el `refresh()` ocurría en la instancia del editor. Ahora `ScenarioStore` es un **singleton de Hilt** y la **única fuente observable**: mantiene una caché en memoria y publica `scenarios: StateFlow<List<Scenario>>`, que se recarga tras cada `save`/`delete` (y en el arranque). `ScenariosViewModel` ya no tiene estado propio de escenas: expone el `StateFlow` del store. `ChatRepository.sceneMemberNames()` también recibe el store por inyección (antes creaba una instancia suelta).

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/ScenarioStore.kt` — `@Singleton @Inject`, caché + `StateFlow` `scenarios`, `reload()` tras cada escritura.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/scenarios/ScenariosViewModel.kt` — recibe `ScenarioStore` por inyección y expone su `StateFlow` (sin `_scenarios` propio).
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/local/ChatRepository.kt` — `ScenarioStore` inyectado en el constructor.
- `analisis/BUGS.md` — BUG-005 ✅.
- `analisis/CAMBIOS.md` — esta entrada.

### 2026-11-19 — Revisión de cara a la Tanda 3: causas verificadas de BUG-005, 006, 023 y anclas de 003/004/007/018

**Qué y por qué:** antes de abrir el chat de la Tanda 3 se revisó el estado real (`150516b`, árbol limpio, local y GitHub sincronizados) y el código de los bugs que toca esa tanda. **No se tocó código de producción**: solo se afinan las entradas de `analisis/BUGS.md` con lo verificado, para que el siguiente chat no rediagnostique ni invente.

**Hallazgos de la revisión:**
- **BUG-005** (causa localizada): `ScenariosScreen` y `ScenarioEditorScreen` son destinos de navegación distintos y cada uno crea su propia instancia de `ScenariosViewModel`; el editor refresca su `_scenarios` y la lista nunca se entera, aunque `store.save()` y `refresh()` funcionan.
- **BUG-006**: el editor de **personajes tampoco** tiene chips de etiquetas (solo un campo de texto separado por comas), así que no hay patrón que copiar: hay que crear el componente de chips.
- **BUG-023**: el backend de restauración **ya existe** (`CharacterRepository.importAnyFromUri` detecta el .zip y empareja avatares; `RoleplayViewModel.import(uri)` lo expone). Falta **solo la UI**. Corregido además el alcance del .zip: contiene **personajes y avatares, no chats** (la entrada decía "+ chats").
- **BUG-003**: el mecanismo es `SendMessageTrigger` + `ChatMessageText` visible; no hay soporte de mensajes ocultos. Se documentan las dos vías (sufijo de prompt en el trigger o mensaje `hidden`).
- **BUG-004**: se confirma que el system solo pide "Only write for the characters listed" y que el post-filtrado por líneas `Nombre:` es viable.
- **BUG-007**: el código actual ya usa `ACTION_SEND` con JSON; queda confirmar en dispositivo si el destino permite guardarlo como archivo.
- **BUG-018**: se listan los campos exactos de `EnginePerfSettings` que faltan en el preset.

**Archivos:**
- `analisis/BUGS.md` — notas de verificación en BUG-003, BUG-004, BUG-005, BUG-006, BUG-007, BUG-018 y BUG-023.
- `analisis/CAMBIOS.md` — esta entrada.

### 2026-11-19 — BUG-031 y BUG-022 verificados en el teléfono; la línea de estado deja de decir "cargando modelo…" al generar

**Qué y por qué:** el humano confirma que BUG-031 ya funciona con normalidad en el teléfono (varias respuestas cortadas a mitad seguidas de respuestas correctas). Se marcan como verificados BUG-031 y BUG-022 en `analisis/BUGS.md`. Además, en la captura de las 10:51 se ve `⏳ cargando modelo…` **mientras el modelo está generando**: `generateResponse` escribe "⏳ cargando modelo…" antes del bucle de espera y no lo restaura al salir, así que la etiqueta se quedaba hasta el `done`. Se vuelve a poner "⏳ procesando…" justo después del bucle.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatViewModel.kt` — línea de estado tras la espera al modelo.
- `analisis/BUGS.md` — BUG-031 ✅ (verificado 19/11) y BUG-022 ✅ (verificado 19/11) con la evidencia.
- `analisis/CAMBIOS.md` — esta entrada.

### 2026-11-19 — BUG-022: nombres completos de motivo en `ApplicationExitInfo`

**Qué y por qué:** el reporte `box-log-20261119-093634.txt` ya trae la sección nueva y **detecta el force-stop** (`2026-11-19 09:02:03 · USER_REQUESTED (cierre forzado) · descripción: [FORCE STOP] stop com.box.gallery due to from process:com.miui.securitycenter`), pero dos entradas salían como `motivo 16`. Verificado con `javap` sobre `android-37.0/android.jar`: `16 = REASON_PACKAGE_UPDATED` (las instalaciones del APK a las 09:00:33 y 09:34:57). Se añaden también `REASON_DEPENDENCY_DIED` (12), `REASON_FREEZER` (14) y `REASON_PACKAGE_STATE_CHANGE` (15) para no dejarlos como número.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/LogExporter.kt` — `exitReasonName()`.
- `analisis/CAMBIOS.md` — esta entrada.

### 2026-11-19 — BUG-031 (segunda ronda): la conversación no se recreaba tras cancelar porque se creía "en sincronía"

**Qué y por qué:** la primera ronda (`6d7f304`) no arregló el bug en el teléfono. El log `box-log-20261119-093634.txt` (APK con `ea50de1`) lo explica sin ambigüedad:

- No aparece **ni una** línea `Resetting conversation with …` en todo el turno fallido: el chat **no** recreó la conversación.
- Las respuestas vacías tardan **503.0 ms** y **511.0 ms**; 500 ms son el `delay(500)` de `LlmChatViewModel.generateResponse`. El turno real dura ~3 ms: el motor termina al instante sin emitir un solo token.
- La captura lo confirma: `¡Hola! Estoy muy` cortado a los 6.9 s y después dos burbujas vacías.

Causa: `applySystemPromptIfNeeded` solo resetea si `needsReset`; con el historial creciendo de dos en dos, `inSync` seguía siendo `true` y se reutilizaba la misma `Conversation` de LiteRT, que **queda inservible al cancelarla** (LiteRT-LM 0.18.1). Por eso el fallo duraba hasta reiniciar la app.

**Cambios:**
1. `LlmChatModelHelper`: nueva marca por modelo `cancelledTurns`. `stopResponse()` la activa (es el botón de parar) y `consumeCancelled(model)` la consume una sola vez. Se limpia al recargar el motor completo.
2. `LlmChatViewModel.applySystemPromptIfNeeded`: si la marca está activa, `needsReset` pasa a `true` y se llama a `resetConversationWithHistory` (cancelar + esperar + cerrar + recrear con historial, ya bajo el candado de la primera ronda) antes del siguiente envío.
3. Detección de respuesta vacía **por caracteres**, no por callback: LiteRT puede llamar a `onMessage` con un mensaje vacío; eso creaba la burbuja vacía y además hacía que la comprobación anterior no saltara. Ahora, si no se entregó ningún carácter, se marca el modelo y se reporta error.
4. `runInference` comprueba `conversation.isAlive` antes de enviar (evita enviar sobre una conversación cerrada) y deja una traza por turno: `Turno LiteRT '<modelo>': N caracteres en X ms (…, stop=…)`.
5. `handleError` quita la burbuja de texto vacía si quedó una.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatModelHelper.kt` — `cancelledTurns`, `consumeCancelled`, `stopResponse`, comprobación de `isAlive`, detección por caracteres y trazas.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatViewModel.kt` — `consumeCancelled` en `needsReset` y limpieza de la burbuja vacía.
- `analisis/BUGS.md` — BUG-031 con la segunda ronda documentada.
- `analisis/CAMBIOS.md` — esta entrada.

**Pendiente:** re-prueba en el teléfono (enviar → cancelar a mitad → enviar). En el log deben verse `Cancelación manual de '…': se recreará la conversación al reenviar` y `Conversación de '…' cancelada: se recrea con el historial`.

### 2026-11-19 — BUG-022: `ApplicationExitInfo` incluye también el cierre forzado desde el sistema

**Qué y por qué:** el log `box-log-20261119-090208.txt` (force-stop de las 09:01, exportado a las 09:02) **no traía el apartado "Crash nativo (ApplicationExitInfo)"**. Dos causas: (1) el APK instalado es anterior al cambio `1dda4fd`; (2) aun con el cambio, el filtro solo aceptaba crashes (`CRASH_NATIVE`, `CRASH`, `ANR`, `SIGNALED`, `LOW_MEMORY`) y un cierre forzado del usuario llega como `REASON_USER_REQUESTED`, así que la sección se habría generado vacía y no se habría impreso. Se cambia el filtro: ahora se excluyen solo las salidas normales (`REASON_EXIT_SELF`, `REASON_UNKNOWN`) y se listan las últimas 8 con nombre legible del motivo, hora, estado, pid, **proceso** y descripción (más la traza en Android 12+). Se añade `exitReasonName()` para no repetir el `when` dentro del bucle.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/LogExporter.kt` — `nativeExitReasons()` sin filtro de crashes y con `processName`; `exitReasonName()` nuevo.
- `analisis/BUGS.md` — BUG-022: se documenta que el force-stop también aparece.
- `analisis/CAMBIOS.md` — esta entrada.

**Verificado en el log adjunto:** el proceso actual arranca a las 09:02:05 y el reporte se genera a las 09:02:08; **no queda ninguna línea de las 09:01** en "logcat (últimas líneas)" porque una app solo puede leer sus propios logs (el force-stop mató el proceso anterior). Tampoco hay ningún mensaje del motor LiteRT ni de "falla de escritura": los únicos crashes del buffer `crash` son del 16/11 (`libllama.so`) y del 17/11 (`liblitertlm_jni.so` 07:18, TTS 13:36/13:37, Scudo 15:23/15:27/15:30).

### 2026-11-19 — BUG-031 (refuerzo): liberar la petición si el envío falla en seco

**Qué y por qué:** `LlmChatModelHelper.runInference` registra una `CompletableFuture` por turno para poder esperarlo al resetear/limpiar. Si `conversation.sendMessageAsync` lanzaba una excepción síncrona, la petición quedaba registrada para siempre y el turno siguiente esperaba los 10 s de `AWAIT_GENERATION_MS` antes de continuar. Ahora el envío va en `try/catch` y llama a `finish(e)`, que libera la petición y reporta el error por el camino normal.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatModelHelper.kt` — `sendMessageAsync` protegido; `finish(e)` si falla.
- `analisis/CAMBIOS.md` — esta entrada.

### 2026-11-19 — I-6/BUG-031: candado del motor LiteRT, espera al turno cancelado y respuesta vacía como error

**Qué y por qué:** con un modelo `.litertlm`, tras enviar, cancelar a mitad y reenviar, todas las respuestas salían vacías en ~500 ms y el indicador se quedaba en "⏳ cargando modelo…". Causa verificada: `LlmChatModelHelper.resetConversation()`/`resetConversationWithHistory()` cerraban la conversación sin cancelar ni esperar la generación en curso (el chat LiteRT la recrea justo antes de cada envío), y el motor quedaba inservible. Cambios, todo Kotlin:
1. **Candado por modelo** en `LlmChatModelHelper` (equivalente al `nativeGate` de `LlamaCppEngine`): reset, envío y limpieza se serializan. Antes de cerrar se cancela, se espera el callback nativo (máx. 10 s) y solo entonces se recrea la conversación con el historial.
2. **`cleanUp`** toma el candado, evita dobles limpiezas (`cleaningUp`) y se ejecuta en un hilo propio si lo llaman desde el principal, para que `pending.get(5 s)` no bloquee la UI.
3. **Estados de la UI:** `stopResponse`, `errorListener`, `cleanUpListener` y el `catch` limpian `preparing` y la línea "⏳ cargando/procesando…" (antes quedaban pegados).
4. **Respuesta vacía = error:** si el turno termina sin ningún fragmento en menos de 750 ms y no fue una cancelación manual, se reporta como error para que `handleError` limpie y reinicie la sesión, en vez de dejar el chat mudo.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatModelHelper.kt` — candado por modelo, `awaitPreviousGeneration`, reset/envío/limpieza serializados, `cleanUp` fuera del hilo principal, detección de respuesta vacía.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatViewModel.kt` — limpieza de `preparing` y del "⏳ …" al cancelar/terminar/fallar.
- `analisis/BUGS.md` — BUG-031 nuevo (🟡 pendiente de re-prueba en teléfono).
- `analisis/CAMBIOS.md` — esta entrada.

**Pendiente:** re-prueba en el teléfono con `.litertlm` (enviar → cancelar → enviar). No probado en dispositivo.

### 2026-11-19 — BUG-022: el reporte de logs añade `ApplicationExitInfo` como fuente de crashes nativos

**Qué y por qué:** una app normal no puede leer `logcat -b crash` de forma fiable (`READ_LOGS` es privilegiado). `LogExporter.buildReport` incluye ahora un apartado **"Crash nativo (ApplicationExitInfo)"** con `ActivityManager.getHistoricalProcessExitReasons()`: últimas salidas anómalas (`REASON_CRASH_NATIVE`, `REASON_CRASH`, `REASON_ANR`, `REASON_SIGNALED`, `REASON_LOW_MEMORY`) con hora, motivo, estado, pid y descripción, más la traza (`getTraceInputStream`) en Android 12+. Comprobación de versión (`SDK_INT >= R` para la API y `>= S` para la traza) y `runCatching` en todo; no necesita el protobuf del tombstone. Se mantiene el buffer `logcat -b crash` como primera fuente. Antes de este cambio la rama se sincronizó con `origin/fase1-rendimiento` (`79eb6b4`, hallazgos I-1/I-2/I-3/I-4).

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/LogExporter.kt` — `nativeExitReasons()` y sección nueva en `buildReport`.
- `analisis/BUGS.md` — BUG-022 ampliado (traza pendiente de ver en el teléfono).
- `analisis/CAMBIOS.md` — esta entrada.

### 2026-11-18 — Revisión de código: puerta única al nativo y unloadModel espera la carga (I-1, I-3, BUG-030/BUG-021)

**Qué y por qué:** revisión de código (hallazgos I-1 e I-3, relacionados con BUG-030 y BUG-021). Ambos tocan el mismo archivo y se entregan en un solo commit.

- **I-3 — acceso al nativo serializado.** `LLMInference` no es seguro entre hilos y el motor lo llamaba desde varios (generación, `seedConversation`, `prepareForAuxTanda`, `setSampler`, `setThinking`, `setMaxResponse`, `benchModel`). Ahora hay una puerta única (`nativeGate`, `Semaphore(1)`): una generación nueva espera a que la anterior termine de verdad (cancelar no interrumpe el prefill nativo) y los métodos auxiliares esperan la puerta (`applySampler` y `benchModel` no esperan: si está ocupada, se saltan). `isGenerating` se marca dentro de `stateLock` al lanzar la generación (antes quedaba una ventana en la que `seedConversation` podía colarse) y un contador de época evita que una generación vieja ponga a `false` el estado de la nueva. Los callbacks `onComplete/onError/onCancelled` se ejecutan **después** de soltar la puerta.
- **I-1 — `unloadModel` espera la carga en curso.** Antes solo esperaba la generación, así que si llegaba a mitad de una carga cerraba la instancia vieja y la carga terminaba sobre una instancia sin dueño. Ahora espera a generación **y** carga (máx. 60 s); si no terminan, no cierra y lo deja en el log. `resetConversation` ahora registra su recarga como `loadJob`, de modo que un `unloadModel` posterior también la espera.

**Verificación:** compilado con kotlinc 2.0.21 contra un `SmolLM` simulado que detecta llamadas nativas solapadas y cierres con una llamada dentro. El motor anterior falla "stop + seed inmediato" y "dos generaciones seguidas" (solape); el nuevo pasa los 7 comprobantes (incluye unload durante carga y durante generación). La compilación detectó un `import kotlinx.coroutines.ensureActive` que faltaba y ya está añadido. **No probado en el teléfono ni compilado con Gradle** (la nube no puede).

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/engine/LlamaCppEngine.kt` — `nativeGate`, `generationEpoch`, `whenNativeIdle`, `unloadModel`, `resetConversation`, `generateResponse` y métodos auxiliares.

### 2026-11-18 — Revisión de código: absorber respeta el orden cronológico de las notas (I-4, BUG-002)

**Qué y por qué:** revisión de código de la Tanda 1+2 (hallazgo I-4, BUG-002). `RawMemoryStore.forCharacter()` devuelve las notas de la **más nueva a la más vieja**, y el código anterior a la Tanda 1 le daba la vuelta (`notes.asReversed()`) antes de armar el prompt de absorción. Al repartir en tandas, `MemoryScreen.absorb` pasaba la lista **sin invertir** a `MemoryAbsorbPrompt.tandas`, que asume orden cronológico: dentro de cada tanda las notas salían al revés y, con más de 8 tandas, `takeLast` descartaba las más NUEVAS en vez de las más viejas. Los tests no lo veían porque alimentaban la función ya en orden cronológico.

Se añade `MemoryAbsorbPrompt.tandasFromNewestFirst()` (da la vuelta a la lista y delega en `tandas`) y `MemoryScreen` lo usa. Test nuevo con la lista en el orden real de la store (12 notas, 5 tandas máx.): se conservan las notas 8-12 y cada tanda va en orden cronológico.

**Archivos:**
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/characters/RawMemory.kt` — `tandasFromNewestFirst()`.
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/memory/MemoryScreen.kt` — `absorb()` usa `tandasFromNewestFirst`.
- `Android/src/app/src/test/java/com/google/ai/edge/gallery/data/characters/RawMemoryTest.kt` — test del orden real de la store (10 tests pasan).
- `analisis/CAMBIOS.md` — esta entrada.

**Pendiente de re-prueba en teléfono:** Memoria → "Absorber clústeres" con notas de distintas fechas: el recuerdo debe seguir el orden de los hechos.

### 2026-11-18 — Revisión de código: SmolLM.close() atómico (I-2, BUG-030)

**Qué y por qué:** revisión de código de la Tanda 1+2 (hallazgo I-2). `SmolLM.close()` no era atómico: dos hilos podían leer `nativePtr != 0` a la vez y llamar dos veces al `close` nativo sobre el mismo `LLMInference`, lo que produce `Scudo ERROR: invalid chunk state` (BUG-030). Los dos candidatos a solaparse eran `LlamaCppEngine.unloadModel()` y `LlamaCppEngine.resetConversation()`, que capturan la misma instancia y la cierran en corrutinas distintas. Ahora `close()` es `@Synchronized` e idempotente, y pone `nativePtr = 0` **antes** de llamar al nativo (así cualquier otro método que llegue en ese instante falla limpio en `verifyHandle()` en vez de usar un puntero liberado). `nativePtr` pasa a `@Volatile`.

**Archivos:**
- `Android/src/smollm/src/main/java/com/jegly/offlineLLM/smollm/SmolLM.kt` — `close()` atómico e idempotente; `nativePtr` volátil.
- `analisis/CAMBIOS.md` — esta entrada.

**Pendiente de re-prueba en teléfono:** cambiar/descargar modelo GGUF varias veces seguidas (BUG-030 no debe reaparecer).

### 2026-11-17 — Evidencia de las capturas: causa probable de BUG-029 (`###` nativo) y BUG-028 acotado a la etiqueta de holgura

**Qué y por qué:** el humano reenvía los dos logs (15:26 y 15:30) junto con **5 capturas**; los logs son **idénticos por hash** a los ya analizados, así que lo nuevo es la evidencia visual. **No se tocó código de producción**: solo se documenta lo verificado en código con esa evidencia. Con las capturas se cierra el diagnóstico de BUG-029: la respuesta escrita con GGUF se corta a los **98 tok** (`gemma-4-E4B…PURE.gguf`, 3.5 t/s, 40.7 s, contexto 5 %) y **63 tok** (`gemma-4-E…_K_M.gguf`, 4.1 t/s, 47.9 s, contexto 8 %), en ambos casos **justo antes de la lista o sección prometida**. La causa probable está en el wrapper nativo (`Android/src/smollm`, trackeado en el repo, **no** es submódulo): `LLMInference.cpp:577-594` corta en silencio si el texto acumulado contiene uno de los stop strings, entre ellos **`###`**, y descarta la pieza que contiene el stop; como los Gemma 4 instruct abren cada sección con `### Título`, la respuesta termina justo antes del encabezado sin dejar rastro. El motivo no llega a la app porque `SmolLM.getResponseAsFlow` (`SmolLM.kt:256-263`) se traga el `[EOG]`. Descartados: `maxResponse` (global, `-1` por defecto, y dos modelos cortaron a 98 y 63), los stops de la app en `LlamaCppEngine.kt:377-382` (el token de la línea 380 está verificado a nivel de bytes y no es un doble salto de línea), `cleanModelOutput(isFinal=true)` (el chat se arma con deltas) y `ThinkingSplitter` (libera lo retenido en `finish()`). BUG-028 queda acotado a la **etiqueta de holgura** de 4.1: sin hueco propio, aparece pegada sin separación cuando los metadatos son largos ("Q5_K_Sdemasiado grande"), lejos cuando son cortos y **recortada contra el botón Descargar**; el humano pidió **solo documentar**, sin arreglo por ahora. La prueba rápida para confirmar BUG-029 es pedir en el chat "responde en texto plano, sin encabezados ni `###`".

**Archivos:**
- `analisis/BUGS.md` — BUG-029 reescrito con los datos de las capturas, la causa probable (`###` en `LLMInference.cpp`) y los descartes; BUG-028 afinado con la evidencia de las capturas (etiqueta pegada, desplazada y recortada contra el botón).
- `analisis/CAMBIOS.md` — esta entrada.

**Próximo paso (sin ejecutar sin confirmación):** el humano probará en el teléfono la petición "sin encabezados ni `###`" para confirmar la causa de BUG-029; sigue pendiente el toast o el `.wav` que falla en BUG-027 y la confirmación de si BUG-030 es uno de los dos crashes nativos que iba a subir. Nada de esto se arregla aún.

### 2026-11-17 — Triaje del feedback: importación `.wav`, espaciado de 4.1, corte de texto con GGUF y crashes de los reportes

**Qué y por qué:** el humano confirma que 3.1 (descarga HF) y 4.2 (cambio de GGUF/LiteRT) funcionan, y que 4.1 funciona aunque quiere mejorar el espaciado; aparecen tres síntomas nuevos y dos crashes en los reportes exportados. **No se tocó código de producción**: solo se documenta lo verificado en código y logs. Altas: BUG-027 (el humano escribió `.wab` por error: es `.wav`; el código sí acepta PCM/float estándar, así que hace falta el toast exacto o el archivo que falla), BUG-028 (la etiqueta de holgura de `HfBrowserScreen` es un `Text` hermano de los metadatos con `weight(1f)`, por eso aparece pegada o lejos según la longitud), BUG-029 (la respuesta **escrita** con GGUF se corta; vías verificadas: stop tokens de `LlamaCppEngine.generateResponse`, recorte `isFinal` descartado en el chat porque el helper usa deltas, `ThinkingSplitter` y tope `maxResponse`) y BUG-030 (`SIGABRT` de Scudo en `LLMInference::~LLMInference()` ← `SmolLM.close` ← `LlamaCppEngine.unloadModel` a las 15:23; presunto doble cierre con `resetConversation` o cierre con el hilo nativo vivo porque el `join` tiene timeout). Se añaden notas de verificación a BUG-013/016/020/022 y de reaparición a BUG-026 (callback JNI a las 15:27 y 15:30, con la build instalada aún por confirmar). Aclaraciones del humano tras el triaje: en 4.1 quiere **separación visual** (no orden alfabético) y con GGUF se corta el **texto** (no la voz). Siguen pendientes: el mensaje exacto al importar el `.wav`, un ejemplo de texto GGUF cortado con su modelo y si los 2 crashes nativos que iba a subir son estos u otros. Sobre BUG-028 el humano eligió **solo documentar**, sin tocar código. Las capturas adjuntas no son visibles para este agente.

**Archivos:**
- `analisis/BUGS.md` — BUG-027, BUG-028, BUG-029 y BUG-030 nuevos; notas de verificación/reaparición en BUG-013, BUG-016, BUG-020, BUG-022 y BUG-026.
- `analisis/CAMBIOS.md` — esta entrada.

**Próximo paso (sin ejecutar sin confirmación):** arreglar los bugs en tandas separadas con commit local por bug; no hacer push. Para BUG-027 hace falta el toast o una muestra `.wav`; para BUG-029 el humano va a pegar ejemplo y modelo. BUG-028 ya está acotado (separación visual, pero el humano quiere solo documentar) y BUG-030 tiene el stack completo.

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
