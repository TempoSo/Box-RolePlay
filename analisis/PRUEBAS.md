# Pruebas a realizar (una sola compilación de `fase1-rendimiento`)

Marca cada punto y pásame el resultado (✅ / ❌ + captura o log). Filtra logcat por `AGLlamaPerf`.

## Fase 1 – Velocidad GGUF (ya confirmada)
- [ ] Cargar un GGUF: anota segundos de carga y tokens/s (log `AGLlamaPerf`).
- [ ] Rendimiento del motor: cambiar hilos / batch / flash attention y ver si cambia la velocidad.

## Fase 2 – TTS (Pocket TTS vía sherpa-onnx)
- [ ] ¿Compila? Si falla por sherpa: poner `app/libs/sherpa-onnx.aar` (ver `app/libs/LEEME.txt`).
- [ ] Ajustes de voz: importar la carpeta del modelo Pocket y una voz de referencia (wav).
- [ ] Botón de altavoz en un mensaje: ¿suena? ¿en español o inglés? ¿tarda mucho en empezar?
- [ ] Voz por personaje (editor de personaje) y lectura automática.

## Fase 3 – Visión GGUF (mmproj)
- [ ] ¿Compila el CMake con `mtmd`? Si falla, pega el error completo.
- [ ] Rendimiento > Visión: importar el mmproj que corresponda al GGUF (misma familia/tamaño).
- [ ] Reimportar el GGUF con "support image" y mandar una foto en Ask Image. ¿Describe bien?
- [ ] ¿Cuánto tarda la imagen? (probar lado máx 256 / 512).

## Fase 4 – Memoria larga
- [ ] Chat largo (>30 mensajes) en GGUF y en LiteRT: tocar la barra de contexto → "Información del chat".
- [ ] ¿Aparece el resumen? ¿El personaje recuerda nombres/lugares antiguos?
- [ ] ¿Se nota un salto de lentitud cuando se desliza la ventana?

## Fase 5 – Ligereza
- [ ] Dejar la app 10+ min en segundo plano y volver al chat: ¿el modelo se recarga solo y responde?
- [ ] Mientras genera, mandar la app a segundo plano: ¿la generación no se corta (GGUF)?
- [ ] Consumo de RAM antes/después (Ajustes > Batería/Memoria del sistema).

## Pendiente de decidir
- Memoria semántica (embeddings MiniLM + Room): se haría con código propio y modelo bajado en ejecución.
- Entrada de voz (ASR con sherpa-onnx).

## Fase 6 – Pulido
- [ ] El nombre de la app ahora es "Box RolePlay" (launcher y pantallas). ¿Se ve bien?
- [ ] Muestreo (GGUF): escribe un nombre abajo y "Guardar como preset"; reabre y carga el preset; bórralo con ✕.

## Extras – Logs y contador
- [ ] Contador de rendimiento: sobre la entrada del chat (bajo la barra de contexto) aparece "⚡ gen X t/s · prompt Y t/s…" tras cada respuesta. GGUF da cifras exactas; LiteRT, aproximadas (fragmentos/s).
- [ ] Ajustes de Roleplay > "Exportar logs": se abre el menú de compartir con un .txt. Envíamelo (o pega las líneas con `AGLlamaPerf`).
- [ ] Si la app se cierra sola, abre de nuevo y exporta: el reporte incluye el error del cierre.

## TTS – pulido
- [ ] Emojis y "!!!" / "......" no se leen raro; frases cortas ya no suenan entrecortadas.
- [ ] Al enviar un mensaje nuevo (o regenerar), la voz de la respuesta anterior se corta.
- [ ] Llamada o audio de otra app: la voz se calla sola.
- [ ] Tras 2 min sin hablar el motor de voz se libera; la siguiente lectura tarda un poco más en arrancar (carga) y luego fluye.
- [ ] Anota: segundos hasta que suena la primera frase, y si el español suena aceptable con tus archivos del modelo.

## Motor – prueba de velocidad
- [ ] Con un GGUF cargado: Rendimiento del motor > "Probar velocidad del modelo cargado". Anota PP y TG (t/s).
- [ ] Cambia hilos (ej. 4 → 5 → 6), vuelve a cargar y repite; dime cuál da mejor TG.
- [ ] Prueba flash attention encendido/apagado y KV f16 vs q8. Después de la prueba, el chat sigue respondiendo normal.

## Copia de seguridad de personajes
- [ ] Ajustes de Roleplay > "Copia de seguridad: exportar personajes (.zip)": se abre el menú de compartir con un zip (guárdalo en Drive o en archivos).
- [ ] Borra un personaje de prueba e importa el zip con el icono de carpeta: ¿vuelve con su avatar y datos? (Se importan duplicados de los que ya existen; borra los repetidos.)
- [ ] Nota: la copia incluye personajes, no los chats ni la memoria.
