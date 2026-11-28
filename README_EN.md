<h1 align="center">🎭 Box RolePlay</h1>

<p align="center"><b>On-device AI roleplay for Android. No cloud. No accounts. No limits.</b></p>

<p align="center"><a href="README.md">🇪🇸 Leer en español</a></p>

[![Fork](https://img.shields.io/badge/Fork%20of-Box%20(jegly)-6272A4.svg)](https://github.com/jegly/Box)
[![Base](https://img.shields.io/badge/Base-Box%201.0.12-6272A4.svg)](https://github.com/jegly/Box/tree/8d6503a8a393ace3d50cfd5084827a0614028c67)
[![Android](https://img.shields.io/badge/Android-13%2B-50FA7B.svg?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-BD93F9.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Engine](https://img.shields.io/badge/Engines-LiteRT%20%2B%20llama.cpp-BD93F9.svg)]()
[![llama.cpp](https://img.shields.io/badge/llama.cpp-GGUF%20%C2%B7%20Vulkan-FFB86C.svg)](https://github.com/ggerganov/llama.cpp)
[![TTS](https://img.shields.io/badge/Voice-Pocket%20%2B%20Kokoro-FF79C6.svg)](https://github.com/k2-fsa/sherpa-onnx)
[![Offline](https://img.shields.io/badge/Network-100%25%20Local-FF5555.svg)]()
[![License](https://img.shields.io/badge/License-Apache%202.0-8BE9FD.svg)](LICENSE)

---

## What is Box RolePlay?

**Box RolePlay** is a fork of [Box](https://github.com/jegly/Box) (itself derived from [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery)) rebuilt around one idea: **talking to AI characters on your phone, with nothing leaving the device**.

> [!NOTE]
> **Fork base: Box [`1.0.12`](https://github.com/jegly/Box/tree/8d6503a8a393ace3d50cfd5084827a0614028c67)** (commit `8d6503a8`). This project is developed on top of the open-source code at that point in history. Later releases in upstream's README refer to distributed APKs; the code in this repository is always the reference here.

Models run on the device (LiteRT and llama.cpp with GGUF), so you can roleplay characters, run multi-character scenes and keep long-term memory without accounts, subscriptions or a network connection.

> **Current state:** the main branch is `main`, with the character store, chat, per-turn memory and Spanish Kokoro already integrated. The on-device checklist lives in [`analisis/PRUEBAS.md`](analisis/PRUEBAS.md) and the prioritized plan in [`analisis/PLAN_PRUEBAS.md`](analisis/PLAN_PRUEBAS.md). Spanish Kokoro has been validated on a phone; memory retrieval and the new chat actions are still awaiting physical validation.

---

## ✨ Roleplay features

### 👤 Characters
- **Character library** with avatar, description, scenario, greetings and tags; groups and filters.
- **Character detail sheet**: usage stats, memories, edit/share/delete, greeting picker and «continue chat».
- **Active persona**: your own profile with name and description that the AI uses to know who you are.
- **Backup** as a `.zip` (characters, memories, personas, presets and scenes) with selective import.
- **Character store**: Chub search, 2-column grid, numbered pages, +18 filter, link import and a «Surprise me» button.
- **Preview before importing**: tapping a card (or «Surprise me») shows its description, personality, scenario and greetings so you decide whether to import it.

### 🎭 Multi-character scenes
- Scenarios with a name, prompt, tags and 2+ characters; the AI voices each one (`Name: text`) without speaking for you.
- **Scene bar** in chat: `Me` · `Narrator` · each character · `🎲 Random`, with an optional narrator.

### 🧠 Long-term memory (two levels)
- A **lightweight note** is saved automatically after every reply; **«Absorb clusters»** lets the model distill those notes into permanent memories.
- Memories are retrieved **every turn** using keywords, recency, importance and usage, with a configurable threshold and cap. The active session is excluded to avoid an echo loop.
- The character's **lorebook** is author canon and is injected per turn above learned memory.
- The memory block stays stable as long as the selected memories do not change, so llama.cpp's prefix cache is not invalidated. «Chat info» shows what was retrieved and its score.
- Per-character switches for *Use memory* and *Collect memories automatically*; a Memory screen with search, editing and deletion.

### 🗣 Voice: cloning and built-in voices
- **Pocket TTS** via sherpa-onnx: clones a voice from a 5–12 s `.wav` sample. The sample is cleaned on save (DC offset, noise, silences, level) and the original is kept so it can be reprocessed.
- **Kokoro** with voices bundled in the model, selectable per character. **Real Spanish** thanks to our own IPA phonemization (`KokoroPhonemizer` + lexicon), with no dependency on espeak-ng.
- **Per-character voice**, automatic reading of replies and **live voice** sentence by sentence while the model is still writing.
- **Models downloadable from Hugging Face** inside the app (by language, resumable), plus a voice library with names, tags, recording and preview.
- Emoji and punctuation cleanup for natural reading; respects other apps' audio focus.

### 💬 Chat
- **Copy, Edit, Speak/Stop, Regenerate and Continue** buttons on each character message.
- «Continue» trims the end of the reply and asks the model to carry on, without leaving a visible user message.

### ⚙️ Saved engines and per-character engines
- An **engine** stores model, vision (mmproj), active persona and sampling; apply it with one tap or assign it per character.
- **Named sampling presets** (temperature, top-K, top-P…).

### 📥 Hugging Face downloads
- Built-in **GGUF model search** by text, `user/repo` or direct link, with file size, quantization and a RAM fit warning.
- **Resumable downloads** (`.part`), free-space check and **mmproj** support for vision. The same applies to voice models.

### 👁 Vision and other apps
- Vision with GGUF (mmproj) in *Ask Image*; image generation, transcription, ask-with-audio, prompt lab and benchmark as standalone apps.
- Custom home screen with mascot and pinned apps; side drawer with chats grouped by character.

### 📊 Performance and diagnostics
- Tokens/s counter for generation and prompt under the chat; thread, flash attention and KV cache settings.
- **Export logs** (logcat + device + last crash) from Roleplay settings; model release after 10 minutes in the background.

---

## 🧱 What it inherits from Box (base `1.0.12`)

| Area | Detail |
|---|---|
| Inference engines | **LiteRT** for `.litertlm`/Gemma and **llama.cpp** for GGUF (configurable threads, batch, flash attention and KV cache) |
| Models | Curated downloads, local GGUF import and Hugging Face downloads (added by this fork) |
| Chat | Multi-turn, markdown, SQLCipher-encrypted history, resumable |
| Image | Stable Diffusion generation via stable-diffusion.cpp (GGUF) |
| Speech input | Speech-to-text with whisper.cpp |
| Privacy | Encrypted history (AES-256), biometric lock |
| Benchmark | Model performance test screen |

Everything else —characters, scenes, memory, TTS, Hugging Face downloads, saved engines, store— is **original code of this fork**.

---

## 🚧 Roadmap

- [x] Phases 1–5 — GGUF engine, TTS (Pocket + Kokoro), vision, memory and background lightness
- [x] Phases 6 and A–E2 — polish, new home screen, character sheet, apps, scenes, drawer and two-level memory
- [x] Store with pagination and random pick; chat actions; per-turn memory with scoring and tracing
- [x] Spanish Kokoro with custom phonemization (validated on device)
- [ ] **Voice Studio**: import mp3/m4a, resampling, waveform trimming and A/B comparison before cloning
- [ ] Memory retrieval settings in the UI (they currently exist with defaults only)
- [ ] Semantic memory (local embeddings) and background absorption

---

## 🔨 Build from source

```bash
git clone --recurse-submodules https://github.com/TempoSo/Box-RolePlay.git
cd Box-RolePlay/Android
./gradlew :app:assembleDebug
```

> `--recurse-submodules` is required: the project uses `llama.cpp`, `stable-diffusion.cpp` and `whisper.cpp`. The first build compiles native libraries and can take 15–25 minutes.
>
> If it fails because of sherpa-onnx, the AAR goes in `Android/src/app/libs/sherpa-onnx.aar` (see `Android/src/app/libs/LEEME.txt`, in Spanish).

You can also open `Android/` in Android Studio and run on a physical device (recommended; the emulator lacks reliable NPU/GPU acceleration).

### Requirements

- Android 13 or newer
- ~4 GB of free storage for a typical quantized LLM
- 6–8 GB RAM recommended (depends on the model)

---

## 📁 Repository layout

```
├── Android/            # App code (Kotlin + Jetpack Compose)
│   └── src/
│       ├── app/        #   main module
│       ├── llama.cpp/  #   submodule (GGUF engine)
│       └── ...         #   stable-diffusion.cpp, whisper.cpp, smollm
├── analisis/           # Working documents (mostly Spanish)
│   ├── BUGS.md / BUGS_CERRADOS.md
│   ├── PRUEBAS.md / PLAN_PRUEBAS.md
│   ├── CAMBIOS.md      #   change log
│   ├── MEMORIA.md / MEMORIA_MEJORAS.md
├── model_allowlists/   # Model allowlists by version
├── skills/             # Agent skills (docs and examples)
└── images/             # Graphic assets
```

---

## 🙏 Credits

- **[Box](https://github.com/jegly/Box)** by jegly — the base of this fork.
- **[Google AI Edge Gallery](https://github.com/google-ai-edge/gallery)** — the original project.
- **[llama.cpp](https://github.com/ggerganov/llama.cpp)** — the GGUF engine.
- **[sherpa-onnx (k2-fsa)](https://github.com/k2-fsa/sherpa-onnx)** — Pocket TTS and Kokoro engines.
- **[LiteRT](https://ai.google.dev/edge/litert)** — Google's runtime for `.litertlm` models.
- **[whisper.cpp](https://github.com/ggerganov/whisper.cpp)** and **[stable-diffusion.cpp](https://github.com/leejet/stable-diffusion.cpp)** — local speech-to-text and image generation.
- **[Hugging Face LiteRT Community](https://huggingface.co/litert-community)** — converted and published models.

> **Disclaimer:** Box RolePlay is an independent project, not affiliated with or endorsed by Google LLC or the original author of Box.

---

## 📄 License

Apache License 2.0. See [LICENSE](LICENSE).
