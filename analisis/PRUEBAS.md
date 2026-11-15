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

## Estructura nueva (Fase A: inicio + configuración + inferencia)
- [ ] ¿Compila? (pantallas nuevas en `ui/home/Box*Screen.kt`; navegación en `GalleryNavGraph.kt`). Pega el primer error con archivo y línea.
- [ ] La app abre en el inicio nuevo (mascota + "Toca para chatear"), ya no en Roleplay.
- [ ] "Toca para chatear": abre un chat en blanco con el modelo elegido (sin personaje).
- [ ] Configuración > Configuración de inferencia: filtro TODOS/GGUF/LITERT, elegir modelo (queda marcado "en uso"), "Añadir modelo personalizado" abre la importación.
- [ ] Visión: importar un mmproj, elegirlo, "Sin visión". Persona: abrir, crear y activar.
- [ ] "Generador de imágenes": abre la lista de modelos del generador.
- [ ] "Elige un personaje" abre Roleplay y la flecha de atrás vuelve al inicio.
- [ ] Icono de la app nuevo (burbuja con carita y chispa) en el launcher.

## Estructura nueva (Fase B: resumen del personaje)
- [ ] ¿Compila? (nuevo `ui/roleplay/CharacterDetailSheet.kt`; consultas nuevas en `ConversationDao`).
- [ ] En Personajes, tocar una tarjeta abre el "Resumen" (ya no inicia el chat directo).
- [ ] Resumen: mensajes/chats, recuerdos (tocarlo abre la memoria), Editar / Compartir / Borrar, descripción con "Mostrar más", escenario.
- [ ] "Seleccionar saludo": aleatorio o uno concreto; "Nuevo chat" empieza con el saludo elegido.
- [ ] "Continuar chat" abre la última conversación con ese personaje (gris si no hay ninguna).

## Estructura nueva (Fase C: Apps independientes)
- [ ] ¿Compila? (nuevo `ui/apps/*`; rutas `box_apps`, `box_app_tts`, `box_app_bench` en `GalleryNavGraph.kt`).
- [ ] Inicio: fila "Apps fijadas" (Roleplay, Texto a voz, Visión) y tarjeta "Tus apps".
- [ ] Tus apps: cuadrícula con búsqueda; cada icono abre su app por separado.
- [ ] Texto a voz (app): escribir y "Leer"/"Parar" sin cargar ningún LLM; "Ajustes de voz".
- [ ] Visión / Generador de imágenes / Transcripción / Preguntar con audio / Lab de prompts: abren la lista de modelos de su tarea.
- [ ] Benchmark: "Benchmark completo" y "Prueba rápida del motor GGUF".

## Fase D — Escenas multipersonaje (app "Roleplay")
1. Inicio → app fijada "Roleplay" (rosa) → "Crear un escenario": poner nombre, prompt, etiquetas, elegir 2+ personajes, guardar. Debe aparecer en la lista.
2. Buscar por nombre/etiqueta en la lista; editar y borrar (el borrado pide confirmación).
3. Tocar una escena: abre un chat con el modelo elegido en Configuración de inferencia. Si no hay modelo, lleva a la configuración. Sin personajes muestra aviso.
4. En el chat, escribir algo: la IA debe responder con líneas "Nombre: texto" para varios personajes y sin hablar por ti. Revisar que usa tu persona activa.
5. Narrador activado: aparece "Narrator:" ocasionalmente.
6. "Personajes" (antes Roleplay) sigue funcionando 1 a 1.
Pendiente (D2): chips Aleatorio/Narrador/User/personaje sobre el input.

## Fase E — Cajón lateral y Memoria a largo plazo
1. Inicio: botón de menú (arriba izquierda) o deslizar desde el borde izquierdo abre el cajón.
2. Cajón: buscar chats, Personajes, Apps, Memoria; lista de chats agrupados por personaje (contador), tocar un chat lo abre; papelera borra; abajo tu persona y engranaje a Configuración.
3. Memoria a largo plazo: elegir personaje, buscar recuerdos, editar/borrar, interruptor por personaje, "Borrar todos".
Pendiente (E2): generación automática de recuerdos tras cada respuesta y consolidación en clústeres con el modelo.

## Fase E2 — Memoria automática
1. Chat con un personaje: desde tu 2.º mensaje, tras cada respuesta aparece un recuerdo nuevo en Memoria (frases clave de tu mensaje y la respuesta, en tu idioma, sin markdown ni emojis). No debe notarse ninguna pausa.
2. Sin duplicados al repetir; en escenas ("Roleplay") no se guardan recuerdos.
3. Interruptores por personaje: "Usar memoria" y "Recopilar automáticamente".
4. "Absorber" (con más de 6 recuerdos): junta los viejos en uno y deja los 5 más recientes.
5. Chat nuevo con el personaje: los recuerdos recientes entran al prompt (máx. configurable).
