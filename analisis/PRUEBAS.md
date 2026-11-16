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

### Fase D2 — Barra de escena en el chat
1. En un chat de escena aparece sobre el campo de texto: "🎲 Aleatorio", "Yo", "Narrador" y un chip por personaje. En chats normales NO aparece.
2. "Yo" (por defecto): escribes normal. "Narrador" o un personaje: tu texto sale con "Nombre: " delante y la IA no debe responder como ese nombre.
3. "🎲 Aleatorio": envía "(Next message: write only as X.)" con un personaje al azar (distinto del anterior y del que controlas) y la IA habla como él.
4. Reabrir la escena desde el cajón: la barra vuelve a salir.

## Fase E — Cajón lateral y Memoria a largo plazo
1. Inicio: botón de menú (arriba izquierda) o deslizar desde el borde izquierdo abre el cajón.
2. Cajón: buscar chats, Personajes, Apps, Memoria; lista de chats agrupados por personaje (contador), tocar un chat lo abre; papelera borra; abajo tu persona y engranaje a Configuración.
3. Memoria a largo plazo: elegir personaje, buscar recuerdos, editar/borrar, interruptor por personaje, "Borrar todos".
Pendiente (E2): generación automática de recuerdos tras cada respuesta y consolidación en clústeres con el modelo.

## Fase E2 — Memoria en dos niveles
1. Chat con un personaje: desde tu 2.º mensaje, tras cada respuesta se guarda al instante una nota ligera ("sin procesar"). No debe notarse pausa ni recarga.
2. Memoria a largo plazo → personaje: la sección "Recuerdos sin procesar (N)" lista las notas (se pueden borrar una a una).
3. "Absorber clústeres": carga el modelo elegido en Configuración si hace falta, genera UN resumen y lo guarda como recuerdo real; las notas absorbidas desaparecen. Revisar: idioma correcto, sin inventos, sin etiquetas raras. Si falla, las notas se conservan y se muestra el motivo.
4. Chat nuevo con el personaje: solo los recuerdos reales (procesados) entran al prompt.
5. Tras absorber, volver al chat: debe seguir funcionando (el motor se reinicia la conversación).
6. Interruptores por personaje: "Usar memoria" y "Recopilar recuerdos automáticamente".

## Motores guardados (Configuración de inferencia)
1. "Guardar combinación actual": pide un nombre y guarda modelo, visión (mmproj), persona activa y muestreo.
2. Tocar un motor guardado: cambia el modelo, la visión, la persona y el muestreo de golpe y avisa si algo ya no existe (modelo borrado, persona eliminada…).
3. Guardar otro con el mismo nombre lo reemplaza; la papelera lo borra. Sobrevive a cerrar la app.
4. Tras aplicar uno con otra visión o modelo, recargar el modelo (la visión se aplica al cargar).

## Voz en vivo (hablar mientras se escribe)
1. Voz → activar "Leer cada respuesta automáticamente" y luego "Hablar mientras se escribe" (apagado por defecto; depende del primero).
2. Con un personaje: la voz debe empezar cuando termina la primera frase, sin esperar la respuesta completa, y seguir frase a frase sin cortes ni repetir ni saltarse frases. Compara el audio con el texto final.
3. Fíjate en la velocidad de escritura (el contador de rendimiento): la voz y el modelo comparten CPU; si baja mucho, deja el interruptor apagado.
4. Parar la respuesta a mitad: la voz termina lo ya encolado y se calla. Tocar el altavoz de un mensaje sigue funcionando.
5. Sin muestra de voz o sin modelo TTS: aviso como antes, el chat sigue normal.
6. Con el interruptor apagado el comportamiento es el de antes (lee al terminar).

## Motor por personaje
1. Con al menos un motor guardado (Configuración de inferencia), abrir un personaje: aparece el botón "Motor: …" con un menú.
2. Elegir un motor y abrir un chat (nuevo o "Continuar"): usa el modelo del motor, aplica su visión y muestreo y cambia a su persona. Elegir "El de Configuración" lo quita.
3. La elección se recuerda al cerrar la app. Prioridad del modelo: motor > modelo propio del personaje > predeterminado.
4. Si el modelo del motor fue borrado, cae al del personaje o al predeterminado sin romper nada.
5. Si el modelo ya estaba cargado con otra visión, hay que recargarlo para que la visión nueva se aplique.

## Grupos de personajes
1. Abrir un personaje → botón "Grupos": crear un grupo (p. ej. "Asistentes"), marcar/desmarcar al personaje y borrar grupos.
2. En la biblioteca aparece una fila de chips ("Todos" + tus grupos) encima de las etiquetas; elegir uno filtra la lista. Se combina con la búsqueda y las etiquetas.
3. Un personaje puede estar en varios grupos. Al cerrar y abrir la app los grupos siguen ahí.
4. Borrar un grupo no borra los personajes. Si no hay grupos, la fila no aparece.

## Descargar de Hugging Face
1. Configuración de inferencia → "Descargar de Hugging Face". Escribe un nombre (p. ej. "qwen 3b gguf") y busca: salen repositorios ordenados por descargas.
2. También sirve pegar "usuario/repositorio" o un enlace de huggingface.co: abre directo la lista de archivos.
3. Archivos: tamaño, cuantización (Q4_K_M…), marca "cabe bien / justo / demasiado grande" según tu RAM, "visión" para los mmproj. Los modelos partidos en varios archivos salen deshabilitados.
4. Descargar un modelo: barra de progreso; al terminar aparece en "Mis modelos" como cualquier modelo importado y se puede elegir y cargar.
5. Descargar un mmproj: aparece en la lista de visión de Configuración de inferencia.
6. Cortar la conexión o cancelar a mitad y volver a pulsar Descargar: continúa donde iba (archivo .part).
7. Sin espacio libre: avisa antes de empezar. Repositorios privados o con acceso restringido: error "No encontrado/403", sin romper nada.
8. La app debe seguir abierta durante la descarga (no hay servicio en segundo plano todavía).

## Descargar modelo de voz de Hugging Face
- [ ] Ajustes de voz → «Descargar de Hugging Face»: al abrir busca solo y lista idiomas (Español, Inglés, Alemán… y variantes 24l) con su tamaño.
- [ ] Elegir «Español» → Descargar: baja 5 .onnx int8 + tokenizer.model y lo convierte (vocab.json / token_scores.json); aparece «Modelo completo.»
- [ ] Probar «Leer»: si el motor falla al cargar (formato v2 distinto al que espera sherpa-onnx), copiar el mensaje de error.
- [ ] «Descargar» baja las 7 piezas con barra de progreso; al terminar, «Modelo completo.»
- [ ] Cortar la red a mitad y repetir: reanuda sin empezar de cero.
- [ ] Con un repo de otro idioma (pegar usuario/repo o enlace): si tiene varias carpetas, deja elegir una.
- [ ] Texto a voz → «Leer» suena con el modelo descargado (hace falta también una muestra .wav).
