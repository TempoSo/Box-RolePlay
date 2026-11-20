# Plan de pruebas — Box RolePlay (rama `fase1-rendimiento`)

> **Documento vivo.** Arriba queda solo lo pendiente; las re-pruebas ya verificadas se archivan al final.
> Última actualización: 2026-11-19 (triaje) · Checklist original: [`PRUEBAS.md`](PRUEBAS.md) · Registro: [`CAMBIOS.md`](CAMBIOS.md) · Abiertos/dudosos: [`BUGS.md`](BUGS.md) · Cerrados: [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md)

**Equipo de prueba:** Xiaomi 2311DRK48G · Android 16 (SDK 36) · arm64-v8a · app v1.0.12 (26)
**Bloque 2 probado además en:** POCO duchamp · HyperOS 3.0.9.0 · Android 15 (fingerprint del crash de BUG-001).
**Modelo principal:** gemma-4-E4B-it-qat-q4_0-heretic-Q4_0_PURE.gguf · mmproj BF16 · contexto 2048

---

## ✅ Resultados registrados

### Bloque 0 — Compilación e instalación
- [x] **Compila e instala** sin errores.
- [x] **Abre en el inicio nuevo** (mascota + "Toca para chatear"), apps fijadas correctas (Personajes, Roleplay, Texto a voz, Visión), tarjetas Historial/Configuración visibles.

### Bloque 1 — Núcleo de inferencia (GGUF)
- [x] **Carga del modelo: 11 s** · **primer token: 6 s** (segundos desde enviar el mensaje).
- [x] **Caché KV q8 funcionando.**
- [x] **Crash al saturar RAM:** modificando y activando varios ajustes a la vez el sistema se saturó y la app murió. Con cambios menores (uno por vez) funciona con normalidad.
- [x] Contador de rendimiento en el chat operativo: se registraron **gen 2.3 t/s · prompt 4 t/s (19 tok) · 101 tok · 49.1 s** en una respuesta real con contexto al 9 % (~179/2048). "Model on CPU" confirmado en la burbuja.
- [x] Velocidades variables según ajustes: queda en **ensayo y error** por combinación (hilos/batch/flash attention/KV).
- [x] Visión mmproj: el diálogo Rendimiento muestra el mmproj seleccionado (gemma-4-E4B BF16) y el lado máximo de imagen (512). Falta probar la descripción real de imágenes (ver Bloque 4).

### Registro del crash
- El reporte exportado (`box-log-20261116-145203.txt`) se tomó **tras un reinicio limpio**: no contiene el stack del crash ni líneas `AGLlamaPerf` (solo ruido de arranque MIUI).
- **Lección:** exportar logs **inmediatamente después** de reabrir la app tras el crash, sin navegar antes, para que el reporte incluya el error.

### Decisión de diseño
- ❌ **"Prueba de velocidad" quitada** de Configuración → Rendimiento del motor: habrá una **app aparte dedicada al benchmark**.
- El motor conserva las funciones `benchModel()` (llama.cpp) y `LlamaCppModelHelper.benchmarkLoaded()` para reaprovecharlas allí. La comparación de hilos/batch/flash attention se hace ahora midiendo con el **contador del chat** tras cada cambio.

### Diagnóstico del bug de la "Prueba de velocidad"
El botón siempre respondía "No hay un modelo GGUF cargado (o está generando)". Causas reales:
1. **Con LiteRT era imposible que funcionara**: LiteRT usa otro motor y nunca registra un motor GGUF — el mensaje era literalmente cierto pero confuso.
2. **Con GGUF**: al reiniciar la app el modelo se libera; hay que abrir un chat y esperar la carga antes de medir. En la captura del fallo, la sección "Último turno" también estaba vacía — señal de que en esa sesión no se había cargado ningún GGUF todavía.
3. El mensaje no distinguía los estados (sin motor / generando / modelo liberado) ni decía qué modelo buscaba.

**Arreglo aplicado:** la prueba rápida vive ahora en la app **Benchmark de modelos → "Prueba rápida del motor GGUF"** (`QuickBenchDialog.kt`), que muestra el nombre del GGUF cargado, explica qué hacer si no lo hay y diferencia el estado "generando/liberado". Rendimiento del motor queda solo con ajustes (hilos, batch, flash attention, KV, mmproj).

### Bloque 2 — Roleplay crítico (2026-11-16 · POCO duchamp, HyperOS 3.0.9.0, Android 15)

- [x] **2.1 Chat 1 a 1 — ✅ OK (re-prueba con teléfono frío):** funciona bien en GGUF y LiteRT. Responde como el personaje, usa la persona activa ("Hola Héctor. Soy Psicólogo Digital…") y no habla por ti. Métricas GGUF: **gen 4.8 t/s · prompt 6 t/s (118 tok) · 98 tok · 40.3 s** · contexto 321/4096 (8 %). Los 7+ min iniciales no reprodujeron: era thermal throttling situacional → **BUG-012** queda en vigilancia.
- [x] **2.2 Ficha de personaje — OK salvo Compartir:** resumen (no chat directo), saludo elegido y "Continuar chat" bien. "Compartir" genera un diálogo de texto inguardable → **BUG-007**.
- [x] **2.3 Grupos — OK:** chips, filtrado combinado con búsqueda/etiquetas, persistencia tras matar la app y borrado sin perder personajes.
- [x] **2.4 Escenas — funciona pero con 4 bugs:** crear/guardar/chatear con varios personajes y narrador OK. Pero: (1) la lista no se refresca al guardar, hay que salir y volver (**BUG-005**); (2) faltan chips de etiquetas existentes en el editor (**BUG-006**); (3) la instrucción de turno `(Next message: write only as X.)` se envía como burbuja visible del usuario — en Layla va oculta (**BUG-003**); (4) la IA habló por los 3 personajes pese a la instrucción (**BUG-004**). Durante la escena salió un crash nativo con reinicio de sesión (**BUG-001**). ✅ La barra de escena NO aparece en chats 1 a 1 (correcto).
- [x] **2.5 Memoria — funciona pero con 2 bugs:** interruptores, guardado de notas automáticas sin pausa, borrado uno a uno OK. Los recuerdos sin procesar NO se pueden editar (**BUG-008**). "Absorber clústeres": **crash con GGUF** (mismo stack de BUG-001) y **timeout con LiteRT** ("modelo tardó demasiado") → **BUG-002**. Re-prueba 17/11 con la 2.ª ronda: **ya no crashea en GGUF**, pero sigue "error de demora" (el watchdog fijo de 180 s < prefill real de la tanda, ~215 s a 6.5 t/s) → **3.ª ronda**: timeout dinámico + KV reutilizada entre tandas.
- [x] **2.6 Cajón — 1 bug:** abre deslizando desde el borde izquierdo con chats agrupados por personaje; pero la hamburguesa del inicio NO lo abre (**BUG-009**).

📋 **Inventario de bugs:** 17 abiertos y 8 dudosos en [`BUGS.md`](BUGS.md); 16 cerrados en [`BUGS_CERRADOS.md`](BUGS_CERRADOS.md) (solo si quieres auditar un cierre).

### Bloque 3 — Voz (2026-11-16): SUSPENDIDO — la carga del modelo de voz está bloqueada
- [x] **3.1 Descarga HF — FALLIDA (BUG-013):** cualquier repo responde "No encontré ninguna carpata con un modelo Pocket TTS completo". Causa raíz localizada: `parseFiles()` filtra solo `.gguf`/`.litertlm` y no deja ver los `.onnx`.
- [x] **Importar carpeta — FALLIDA (BUG-014):** la carpeta bajada a mano (nombres v2 `flow_lm_*`, `mimi_*`, `tokenizer.model`) se descarta entera; sigue "Faltan: lm_flow.onnx…". Dato clave: los bundles v2 de KevinAHM además **no sirven** para sherpa-onnx 2.0.1 sin el fix BOS (ruido/corta a 0,2 s).
- [x] **3.2 → 3.8 — SUSPENDIDOS:** dependen de conseguir el modelo de voz cargado.
- 🔓 **Desbloqueo sin compilar:** descargar los **7 archivos de la carpeta `es`** de `haex-space/pocket-tts-sherpa-onnx` (nombres v1: `lm_flow.int8.onnx`, `lm_main.int8.onnx`, `encoder.onnx`, `decoder.int8.onnx`, `text_conditioner.onnx`, `vocab.json`, `token_scores.json` — pasan el filtro actual) en una carpeta del móvil → Ajustes de voz → **"Importar carpeta del modelo"**. ~200 MB en total.

### Bloque 4 — Descargas y modelos (2026-11-17 · Xiaomi 2311DRK48G)

- [x] **4.1 Navegador HF — OK salvo colores:** búsqueda y listado funcionan; errores limpios ("Hugging Face respondió 401" en repo privado, "Este repositorio no tiene archivos .gguf ni .litertlm"); pegar un enlace directo lleva al selector de archivos. Faltan colores en las etiquetas de holgura ("cabe bien"/"justo"/"demasiado grande", todas en gris) y el umbral "justo" es conservador → **BUG-020**.
- [x] **4.2 Cambio de modelo GGUF/LiteRT — OK con 1 bug:** la carga funciona bien, pero al cambiar de modelo el primer mensaje a veces falla "llama.cpp engine not initialized for…" + "Session re-initialized"; al reenviar la pregunta carga bien → **BUG-016** (carrera: el envío llega antes de terminar la inicialización). **Arreglable.**
- [x] **4.3 Visión mmproj — funciona, muy lenta:** describe la imagen pero tarda ~4 min con lado 256; con 512 peor → **BUG-017**.
- [x] **4.4 Motor guardado por personaje — OK salvo presets:** guardar/aplicar motor (GGUF/LiteRT), visión, persona y muestreo OK; al borrar el modelo ligado cae inmediatamente al motor general de roleplay sin romper nada. Los presets NO restauran hilos/batch/flash/KV → **BUG-018**.
- [x] **4.5 Benchmark GGUF/LiteRT — OK con nota:** mide en ambos motores; el de LiteRT usa una métrica propia no comparable con GGUF → **BUG-019** (menor, arreglar después). ✅ **Cubre el resto del Bloque 1 (LiteRT): descarga, carga y chateo verificados.**

### Bloque 5 — Robustez y regresión (2026-11-17 · POCO duchamp)

- [x] **5.1 Prueba rápida GGUF — parcial:** la app Benchmark **aún no** trae la prueba rápida en el APK instalado: ese cambio (`QuickBenchDialog.kt`) está **sin compilar** (pendiente de commit). En el build actual la prueba vive en Rendimiento (GGUF) → "Probar velocidad del modelo cargado". Datos del turno GGUF: **carga 3.5 s · prompt 105 tok @ 7.9 t/s · gen 210 tok @ 5.06 t/s**. LiteRT: solo medible por el contador del chat (**8.6 frag/s · 1er token 3.1 s**) — métrica propia no comparable (BUG-019).
- [x] **5.2 Segundo plano 10+ min — ✅ OK:** GGUF y LiteRT aguantan otra app y pantalla apagada; al volver siguen ahí y LiteRT termina de escribir.
- [x] **5.3 Generación en segundo plano — ✅ aceptable (mejora opcional → BUG-024):** en GGUF se **pausa** al apagar pantalla/cambiar de app y reanuda al volver sin perder nada; en LiteRT los cortos terminan y los largos se pausan. Es el congelado de apps en caché de Android, no un defecto; para generar de verdad en segundo plano haría falta un foreground service.
- [x] **5.4 Rotación — ❌ BUG-021 (P1):** rotar **recarga el modelo** en GGUF y LiteRT (el manifiesto solo tiene `configChanges="uiMode"` → la Activity se recrea). Y si rota **mientras genera**, crash nativo: SIGSEGV en `liblitertlm_jni.so` (stack completo en BUGS.md).
- [x] **5.5 Exportar logs tras crash — ❌ BUG-022 (P2):** el reporte solo trae la sesión reabierta. Causa: el capturador Java (`installCrashRecorder`) no puede capturar crashes **nativos** (SIGSEGV), que son los que hay.
- [x] **5.6 Copia de seguridad — ⚠️ BUG-023 (P2):** exportar ✅; **no hay UI para importar/restaurar** la copia (solo existe importación de personaje individual).
- [x] **5.7 Presets de muestreo — ✅ OK:** guardar con nombre, cargar y borrar con ✕. Falta un pequeño ajuste gráfico (a definir por el usuario).
- [x] **5.8 Apps sin modelo — ✅ OK:** "Texto a voz" abre su UI sin cargar modelo (no lee hasta instalar la voz); Visión/Imágenes/Transcripción/Benchmark abren sin crash.
- [ ] 5.9 RAM antes/después + micro-regresión final — sin datos aún (opcional).

---

## ⏳ Pendiente de probar

_(El resto del Bloque 1 — LiteRT — quedó cubierto por la prueba 4.5 del Bloque 4, ver resultados arriba.)_

### Bloque 2 — Re-pruebas tras corregir bugs (ver [`BUGS.md`](BUGS.md))
- [ ] 2.6 Hamburguesa del inicio abre el cajón (BUG-009).
- [ ] 2.1b Calidad GGUF ≈ Layla: usa el nombre de tu persona y sigue el personaje con el mismo modelo (plantilla de turnos aplicada, BUG-015).

### Bloque 3 — Voz: re-pruebas tras cargar un modelo (desbloqueo: importar carpeta `es` de haex-space, ver arriba)
- [ ] 3.2 "Texto a voz" sin LLM: ¿suena?, segundos hasta la 1.ª frase, botón parar.
- [ ] 3.3 Voz predeterminada + calidad del español ("Probar voz").
- [ ] 3.4 Altavoz por mensaje: suena, segundos hasta la 1.ª frase, 2.º toque corta.
- [ ] 3.5 Lectura automática: habla tras cada respuesta; mensaje nuevo corta la voz.
- [ ] 3.6 Voz en vivo: 1.ª frase mientras genera; comparar gen t/s con voz ON/OFF.
- [ ] 3.7 Voz por personaje: voz propia / predeterminada en otros / "Quitar" vuelve a la predeterminada.
- [ ] 3.8 Audio focus: con música o llamada → ¿baja volumen o se corta?
- [ ] 3.1 (tras BUG-013) Descarga HF: listar carpetas, descargar con reanudación `.part`.
- [ ] **Verificación del commit `2cec7db` (fix de Claude, tokenizer.json + q4):** buscar `TTSBard/kyutai-en-clone-onnx` → debe listar **"Principal"** (~130 MB, todo en raíz), bajar las piezas int8/normal **ignorando las q4**, y convertir solo el `tokenizer.json` a `vocab.json`/`token_scores.json`. El filtro de `parseFiles` ya está corregido y verificado en BUG-013 (17/11); este ítem comprueba además el commit `2cec7db`.

### Bloque 4 — Re-pruebas tras corregir bugs
- [ ] 4.2b Cambio de modelo: el primer mensaje entra siempre sin error ni "Session re-initialized" (BUG-016).
- [ ] 4.3b Visión: tiempo con lado 256 y 512 tras las optimizaciones (BUG-017).

### Bloque 5 — Re-pruebas tras corregir bugs (la base ya está probada, ver resultados arriba)
- [ ] 5.1b Benchmark de modelos → "Prueba rápida del motor GGUF" (requiere compilar el cambio pendiente): nombre del modelo y PP/TG; sin cargar → explica cómo proceder.
- [ ] 5.4b Rotar con modelo cargado: sin recarga; rotar mientras genera: sin crash (BUG-021).
- [ ] 5.9 RAM antes/después + micro-regresión (pendiente de la pasada anterior).

---

## Qué registrar siempre

- Segundos de **carga** del modelo y hasta el **primer token**.
- **tok/s** de generación y prompt (contador del chat o `AGLlamaPerf` en logcat).
- **Retardo de la voz** hasta la primera frase.
- **RAM** antes/después de sesiones largas.
- Cualquier crash: **exportar logs inmediatamente** tras reabrir (el reporte adjunta el último error).

## 🗄️ Re-pruebas verificadas (archivo; no leer por defecto)

> Movidas aquí en el triaje del 2026-11-19. Conservan su texto original; el inventario vivo está en [`BUGS.md`](BUGS.md).

### Bloque 2 — Re-pruebas tras corregir bugs (ver [`BUGS.md`](BUGS.md))

- [x] ~~2.1 Chat 1 a 1~~ ✅ **OK ya** (re-prueba): usa tu persona, no habla por ti, GGUF y LiteRT.
- [x] **2.4 Escenas — ✅ OK (19/11, en teléfono):** instrucción de turno oculta (BUG-003: solo se ve `Tohru: Hola`; "Aleatorio" no crea burbuja de usuario), la IA habla SOLO por los personajes permitidos (BUG-004: como Tohru respondió Narrador/KIP/Akiko; con Aleatorio solo KIP; regenerar sin crash), lista se refresca al guardar (BUG-005: `#ai` apareció al instante), chips de etiquetas en el editor (BUG-006: `#ai`, `#anime`, `#ciencia ficción`, `#fantasía`, `#femenino`, `#magia`, `#robot`), sin crash al deslizar ventana (BUG-001). Turno: 95.8 s totales, 1.er token 36.5 s, ~4.3 frag/s, contexto 43 %.
- [x] **2.5 Memoria — ✅ OK (17/11, tras la 5.ª ronda):** "Absorber clústeres" funciona **sin crash ni timeout en GGUF y LiteRT** (verificado por el humano). Ruta final: tandas de 3500 car., reset a fondo solo antes de la primera, límite ligero entre tandas (Gemma 4 conserva la KV), tope de generación 400 tok, tope global de 20 min por tanda, atasco = 120 s sin tokens nuevos, pausa de enfriamiento de 30 s entre tandas. Pendiente en esta pantalla: editar recuerdos sin procesar (**BUG-008**); chat nuevo usa solo recuerdos procesados.
- [x] **2.2 Compartir ficha — ✅ OK (19/11, en teléfono, BUG-007):** "Más acciones → Compartir" abre el chooser con el JSON `chara_card_v2`, botón "Copiar texto" y destino "Guardar en...". No hace falta `FileProvider`.

### Bloque 4 — Re-pruebas tras corregir bugs

- [x] **4.1b/4.1c Etiquetas de holgura — ✅ OK (19/11, en teléfono, BUG-020/028):** en `google/gemma-4-E4B-it-qat-q4_0-gguf` la etiqueta "✔ cabe bien" aparece en su propia línea bajo `4.80 GB · Q4_0`, sin pegarse a metadatos ni al botón Descargar; maquetación y código de colores intactos, umbrales sin tocar. Matiz: el tono concreto (verde/ámbar/rojo) no se pudo confirmar con volcado de UI; requiere captura visual humana para cerrarlo del todo.
- [x] **4.4b Presets — ✅ OK (19/11, en teléfono, BUG-018):** motor `PRUEBA` guardado con 2 hilos → cambiados a 7 → al aplicar el preset volvió a 2; batch 64, ubatch 512, Flash Attention Auto y KV f32 conservados. Perfil borrado y hilos restaurados a 0 (automático).

### Bloque 5 — Re-pruebas tras corregir bugs (la base ya está probada, ver resultados arriba)

- [x] **5.5b Exportar logs tras crash — ✅ OK (19/11, 09:36, BUG-022):** el reporte incluyó el stack nativo; casilla actualizada en el triaje del 19/11.
- [x] **5.6b Restaurar copia — ✅ OK (19/11, en teléfono, BUG-023):** "Restaurar copia" abrió DocumentsUI; al importar `box-roleplay-prueba.zip` apareció "Se restauraron 4 personajes" (biblioteca 4 → 8), con avatares recreados con ids nuevos y `box_chat.db-wal` actualizado. Los 4 duplicados de prueba se borraron después, quedando los originales.
