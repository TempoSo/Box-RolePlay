# Allowlists de modelos

Este directorio guarda las **listas de modelos permitidos por versión** de la app (`1_0_4.json` … `1_0_12.json`).

Cada archivo describe qué modelos descargables aparecen en la app para esa versión: nombre, `modelId` de Hugging Face, tamaños y variantes por chip (GPU, NPU de Snapdragon/Tensor/MediaTek).

- El archivo activo en la raíz del repositorio es [`model_allowlist.json`](../model_allowlist.json).
- No edites estos JSON salvo que quieras cambiar qué modelos ofrece la app; para cargar tus propios modelos usa la **importación GGUF** o la **descarga desde Hugging Face** dentro de la app.
