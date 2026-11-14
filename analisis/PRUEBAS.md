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
