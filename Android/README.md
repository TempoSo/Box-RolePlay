# 📱 Box RolePlay — App Android

Código fuente de la app **Box RolePlay**: roleplay con IA 100 % local, construido sobre el motor de [Box](https://github.com/jegly/Box) (Kotlin + Jetpack Compose).

## Estructura

```
Android/
├── src/
│   ├── app/                  # Módulo principal de la app
│   │   └── src/main/
│   │       ├── java/         #   código Kotlin (ui/, roleplay, inferencia...)
│   │       ├── assets/       #   skills incluidas y recursos
│   │       ├── jniLibs/      #   librerías nativas
│   │       └── res/          #   recursos (strings, iconos, temas)
│   ├── llama.cpp/            # Submódulo — motor GGUF
│   ├── stable-diffusion.cpp/ # Submódulo — generación de imagen
│   └── whisper.cpp/          # Submódulo — voz a texto
└── gradle/                   # Configuración de Gradle
```

## Compilar

```bash
# Desde la raíz del repositorio (los submódulos son obligatorios)
git clone --recurse-submodules -b fase1-rendimiento https://github.com/TempoSo/Box-RolePlay.git
cd Box-RolePlay/Android

# Debug
./gradlew :app:assembleDebug

# Release
./gradlew :app:assembleRelease
```

La primera compilación construye las librerías nativas desde el código de los submódulos: cuenta con **15–25 minutos**.

También puedes abrir esta carpeta (`Android/`) en **Android Studio** y ejecutar directamente sobre un dispositivo físico.

> **Nota (Pocket TTS):** si la compilación falla por sherpa-onnx, coloca el AAR en `app/libs/sherpa-onnx.aar` (ver `app/libs/LEEME.txt`).

## Requisitos

- Android Studio (Kotlin, Jetpack Compose, Hilt)
- Android 13+ en el dispositivo de prueba
- 6–8 GB de RAM recomendados según el modelo a cargar

## Dónde mirar

| Quiero... | Ruta |
|---|---|
| Pantallas de inicio, apps e inferencia | `src/app/src/main/java/.../ui/home/` |
| Personajes, escenas y memoria | `src/app/src/main/java/.../ui/roleplay/` |
| Navegación | `src/app/src/main/java/.../GalleryNavGraph.kt` |
| Checklist de pruebas por fase | [`analisis/PRUEBAS.md`](../analisis/PRUEBAS.md) |
