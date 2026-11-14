#include "LLMInference.h"
#include "ContextWindow.h"
#include "ChatFormats.h"
#include <android/log.h>
#include <cstring>
#include <iomanip>
#include <iostream>
#include <sstream>
#include <cmath>
#include <algorithm>

#define TAG "[offlineLLM-Cpp]"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

void
LLMInference::loadModel(const char *model_path, float minP, float temperature, float topP, int topK,
                        float repeatPenalty, bool storeChats, long contextSize,
                        const char *chatTemplate, int nThreads, bool useMmap, bool useMlock,
                        const EngineOptions &opts) {
    LOGi("loading model with"
         "\n\tmodel_path = %s"
         "\n\tminP = %f"
         "\n\ttemperature = %f"
         "\n\ttopP = %f"
         "\n\ttopK = %d"
         "\n\trepeatPenalty = %f"
         "\n\tstoreChats = %d"
         "\n\tcontextSize = %li"
         "\n\tnThreads = %d"
         "\n\tuseMmap = %d"
         "\n\tuseMlock = %d",
         model_path, minP, temperature, topP, topK, repeatPenalty, storeChats, contextSize,
         nThreads, useMmap, useMlock);

    ggml_backend_load_all();

    llama_model_params model_params = llama_model_default_params();
    model_params.use_mmap = useMmap;
    model_params.use_mlock = useMlock;
    model_params.use_extra_bufts = opts.repack != 0;
    _model = llama_model_load_from_file(model_path, model_params);
    if (!_model) {
        LOGe("failed to load model from %s", model_path);
        throw std::runtime_error("loadModel() failed");
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = contextSize;
    ctx_params.n_batch = std::min<long>(opts.nBatch, contextSize);
    ctx_params.n_ubatch = std::min<int>(opts.nUBatch, (int) ctx_params.n_batch);
    ctx_params.n_threads = nThreads;
    ctx_params.n_threads_batch = opts.nThreadsBatch > 0 ? opts.nThreadsBatch : nThreads;
    ctx_params.no_perf = true;
    ctx_params.type_k = EngineOptions::kvType(opts.kvTypeK);
    ctx_params.type_v = EngineOptions::kvType(opts.kvTypeV);
    ctx_params.flash_attn_type = opts.flashAttn == 2 ? LLAMA_FLASH_ATTN_TYPE_ENABLED
                               : opts.flashAttn == 1 ? LLAMA_FLASH_ATTN_TYPE_DISABLED
                                                     : LLAMA_FLASH_ATTN_TYPE_AUTO;
    // La cuantizacion de la cache V exige Flash Attention.
    if (ctx_params.type_v != GGML_TYPE_F16 && ctx_params.type_v != GGML_TYPE_F32 &&
        ctx_params.flash_attn_type == LLAMA_FLASH_ATTN_TYPE_DISABLED) {
        ctx_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED;
    }
    _ctx = llama_init_from_model(_model, ctx_params);
    if (!_ctx) {
        // Combinacion no soportada (p. ej. cache cuantizada): se reintenta con valores seguros.
        LOGe("llama_init_from_model() failed with custom options, retrying with defaults");
        ctx_params.type_k = GGML_TYPE_F16;
        ctx_params.type_v = GGML_TYPE_F16;
        ctx_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_AUTO;
        _ctx = llama_init_from_model(_model, ctx_params);
    }
    if (!_ctx) {
        LOGe("llama_new_context_with_model() returned null");
        throw std::runtime_error("llama_new_context_with_model() returned null");
    }
    _maxResponse = opts.maxResponse;
    LOGi("context: n_ctx=%u n_batch=%u n_ubatch=%u threads=%d/%d flash=%d kv=%d/%d repack=%d",
         llama_n_ctx(_ctx), llama_n_batch(_ctx), llama_n_ubatch(_ctx), nThreads,
         ctx_params.n_threads_batch, (int) ctx_params.flash_attn_type, (int) ctx_params.type_k,
         (int) ctx_params.type_v, opts.repack);

    SamplerSettings settings;
    settings.minP = minP;
    settings.temperature = temperature;
    settings.topP = topP;
    settings.topK = topK;
    settings.repeatPenalty = repeatPenalty;
    settings.repeatLastN = 256;
    _buildSampler(settings);

    _formattedMessages = std::vector<char>(llama_n_ctx(_ctx));
    _messages.clear();

    if (chatTemplate == nullptr || strlen(chatTemplate) == 0) {
        _chatTemplate = llama_model_chat_template(_model, nullptr);
    } else {
        _chatTemplate = strdup(chatTemplate);
    }
    
    if (_chatTemplate != nullptr) {
        std::string tmpl(_chatTemplate);
        if (tmpl.find("gemma") != std::string::npos || 
            tmpl.find("<start_of_turn>") != std::string::npos ||
            tmpl.find("<turn|") != std::string::npos) {
            _assistantRole = "model";
        } else {
            _assistantRole = "assistant";
        }
    }

    _isGemma4 = _chatTemplate != nullptr && (strstr(_chatTemplate, "<|turn>") != nullptr ||
                                             strstr(_chatTemplate, "<turn|>") != nullptr);
    this->_storeChats = storeChats;
}

void
LLMInference::addChatMessage(const char *message, const char *role) {
    const char* actualRole = role;
    if (strcmp(role, "assistant") == 0) {
        actualRole = _assistantRole.c_str();
    }
    _messages.push_back({strdup(actualRole), strdup(message)});
}

float
LLMInference::getResponseGenerationTime() const {
    // tokens por segundo de la generacion (sin contar el procesamiento del prompt)
    if (_genTimeUs == 0 || _genTokens == 0) return 0.0f;
    return (float) _genTokens / ((float) _genTimeUs / 1e6f);
}

void
LLMInference::getPerf(float *out4) const {
    out4[0] = (float) _prefillTokens;
    out4[1] = (float) _prefillTimeUs / 1e6f;
    out4[2] = (float) _genTokens;
    out4[3] = (float) _genTimeUs / 1e6f;
}

int
LLMInference::getContextSizeUsed() const {
    return _nCtxUsed;
}

std::string
LLMInference::_formatPrompt(const std::vector<llama_chat_message> &msgs) {
    if (_isGemma4) {
        std::vector<std::pair<std::string, std::string>> items;
        items.reserve(msgs.size());
        for (auto &m: msgs) items.emplace_back(m.role, m.content);
        return chatfmt::formatGemma4(items, _enableThinking);
    }
    int new_len = llama_chat_apply_template(
        _chatTemplate,
        msgs.data(),
        msgs.size(),
        true,
        _formattedMessages.data(),
        _formattedMessages.size()
    );
    if (new_len > (int) _formattedMessages.size()) {
        _formattedMessages.resize(new_len);
        new_len = llama_chat_apply_template(
            _chatTemplate,
            msgs.data(),
            msgs.size(),
            true,
            _formattedMessages.data(),
            _formattedMessages.size()
        );
    }

    if (new_len < 0) {
        LOGe("llama_chat_apply_template() failed, using fallback formatting");
        std::stringstream fallback;
        for (auto &msg: msgs) {
            fallback << msg.role << ": " << msg.content << "\n";
        }
        fallback << _assistantRole << ":";
        return fallback.str();
    }

    return std::string(_formattedMessages.begin(), _formattedMessages.begin() + new_len);
}

std::vector<llama_token>
LLMInference::_tokenizeMessages(const std::vector<llama_chat_message> &msgs) {
    return common_tokenize(llama_model_get_vocab(_model), _formatPrompt(msgs), true, true);
}

bool
LLMInference::loadMmproj(const char *mmprojPath, int nThreads) {
    if (_mtmd) {
        mtmd_free(_mtmd);
        _mtmd = nullptr;
    }
    mtmd_context_params p = mtmd_context_params_default();
    p.use_gpu = false;
    p.n_threads = nThreads > 0 ? nThreads : 4;
    p.print_timings = false;
    p.warmup = false; // arranca antes; el primer uso paga el calentamiento
    _mtmd = mtmd_init_from_file(mmprojPath, _model, p);
    if (!_mtmd) {
        LOGe("mtmd_init_from_file() failed for %s", mmprojPath);
        return false;
    }
    if (!mtmd_support_vision(_mtmd)) {
        LOGe("mmproj has no vision support");
        mtmd_free(_mtmd);
        _mtmd = nullptr;
        return false;
    }
    LOGi("mmproj loaded: %s", mmprojPath);
    return true;
}

bool
LLMInference::addImage(int width, int height, const unsigned char *rgb) {
    if (!_mtmd || width <= 0 || height <= 0) return false;
    mtmd_bitmap *bmp = mtmd_bitmap_init((uint32_t) width, (uint32_t) height, rgb);
    if (!bmp) return false;
    _pendingBitmaps.push_back(bmp);
    return true;
}

void
LLMInference::_clearPendingBitmaps() {
    for (mtmd_bitmap *b: _pendingBitmaps) mtmd_bitmap_free(b);
    _pendingBitmaps.clear();
}

// Turno con imagenes: se procesa todo el prompt (texto + imagenes) con mtmd y solo queda
// muestrear. Las imagenes no se reutilizan entre turnos: en el historial quedan como "[imagen]".
void
LLMInference::_startMediaCompletion(const std::string &query) {
    const std::string marker = mtmd_default_marker();
    std::string stored = query;
    for (size_t pos = 0; (pos = stored.find(marker, pos)) != std::string::npos;) {
        stored.replace(pos, marker.size(), "[imagen]");
        pos += 8;
    }
    addChatMessage(stored.c_str(), "user");
    _applyContextWindow();

    // Prompt con los marcadores: se cambia un momento el ultimo mensaje.
    char *savedContent = const_cast<char *>(_messages.back().content);
    _messages.back().content = strdup(query.c_str());
    std::string prompt = _formatPrompt(_messages);
    free(const_cast<char *>(_messages.back().content));
    _messages.back().content = savedContent;

    mtmd_input_text text;
    text.text = prompt.c_str();
    text.add_special = true;
    text.parse_special = true;

    std::vector<const mtmd_bitmap *> bitmaps(_pendingBitmaps.begin(), _pendingBitmaps.end());
    mtmd_input_chunks *chunks = mtmd_input_chunks_init();
    int32_t rc = mtmd_tokenize(_mtmd, chunks, &text, bitmaps.data(), bitmaps.size());
    _clearPendingBitmaps();
    if (rc != 0) {
        mtmd_input_chunks_free(chunks);
        throw std::runtime_error("mtmd_tokenize() failed (el texto debe tener un marcador por imagen)");
    }

    const int nCtx = (int) llama_n_ctx(_ctx);
    const size_t nTokens = mtmd_helper_get_n_tokens(chunks);
    if ((int) nTokens >= nCtx - 16) {
        mtmd_input_chunks_free(chunks);
        throw std::runtime_error("context size too small for this image: increase the context length");
    }

    llama_memory_clear(llama_get_memory(_ctx), false);
    _kvTokens.clear();

    auto t0 = ggml_time_us();
    llama_pos newPast = 0;
    rc = mtmd_helper_eval_chunks(_mtmd, _ctx, chunks, 0, 0, (int32_t) llama_n_batch(_ctx), true, &newPast);
    mtmd_input_chunks_free(chunks);
    if (rc != 0) {
        llama_memory_clear(llama_get_memory(_ctx), false);
        throw std::runtime_error("mtmd_helper_eval_chunks() failed");
    }
    _prefillTimeUs = ggml_time_us() - t0;
    _prefillTokens = (int) nTokens;

    delete _batch;
    _batch = new llama_batch();
    _batch->n_tokens = 0;
    _pendingPrompt.clear();
    _promptDecoded = false;
    _mediaPrefilled = true;
    _kvDirty = true; // el KV ya no coincide con _kvTokens: el siguiente turno parte de cero
}

// Cadena de muestreo, en el orden habitual de llama.cpp: penalizaciones, DRY, top-k, top-p, min-p,
// XTC, temperatura (dinamica si hay rango) y al final el sorteo.
void
LLMInference::_buildSampler(const SamplerSettings &st) {
    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    sampler_params.no_perf = true;
    llama_sampler *chain = llama_sampler_chain_init(sampler_params);

    if (st.repeatPenalty > 1.0f && st.repeatLastN != 0) {
        llama_sampler_chain_add(chain, llama_sampler_init_penalties(st.repeatLastN, st.repeatPenalty, 0.0f, 0.0f));
    }

    if (st.dryMultiplier > 0.0f && _model != nullptr) {
        static const char *breakers[] = {"\n", ":", "\"", "*"};
        llama_sampler_chain_add(chain, llama_sampler_init_dry(
                llama_model_get_vocab(_model), llama_model_n_ctx_train(_model),
                st.dryMultiplier, st.dryBase, st.dryAllowedLength, st.dryPenaltyLastN,
                breakers, 4));
    }

    if (st.topK > 0) {
        llama_sampler_chain_add(chain, llama_sampler_init_top_k(st.topK));
    }
    if (st.topP < 1.0f) {
        llama_sampler_chain_add(chain, llama_sampler_init_top_p(st.topP, 1));
    }
    if (st.minP > 0.0f) {
        llama_sampler_chain_add(chain, llama_sampler_init_min_p(st.minP, 1));
    }
    if (st.xtcProbability > 0.0f) {
        llama_sampler_chain_add(chain, llama_sampler_init_xtc(st.xtcProbability, st.xtcThreshold, 1, LLAMA_DEFAULT_SEED));
    }

    if (st.dynTempRange > 0.0f) {
        llama_sampler_chain_add(chain, llama_sampler_init_temp_ext(st.temperature, st.dynTempRange, 1.0f));
    } else {
        llama_sampler_chain_add(chain, llama_sampler_init_temp(st.temperature));
    }
    llama_sampler_chain_add(chain, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    if (_sampler) llama_sampler_free(_sampler);
    _sampler = chain;
}

void
LLMInference::setSampler(const SamplerSettings &settings) {
    if (!_model) return;
    _buildSampler(settings);
    LOGi("sampler: temp=%f dyn=%f topK=%d topP=%f minP=%f xtc=%f/%f rep=%f(last %d) dry=%f/%f/%d",
         settings.temperature, settings.dynTempRange, settings.topK, settings.topP, settings.minP,
         settings.xtcProbability, settings.xtcThreshold, settings.repeatPenalty,
         settings.repeatLastN, settings.dryMultiplier, settings.dryBase, settings.dryAllowedLength);
}

void
LLMInference::setThinking(bool enabled) {
    if (_enableThinking == enabled) return;
    _enableThinking = enabled;
    LOGi("thinking: %s", enabled ? "on" : "off");
}

void
LLMInference::setContextWindow(float high, float low) {
    if (high <= 0.0f || high > 1.0f) high = 0.75f;
    if (low <= 0.0f || low >= high) low = high * 0.6f;
    _windowHigh = high;
    _windowLow = low;
}

void
LLMInference::resetChat() {
    for (llama_chat_message &message: _messages) {
        free(const_cast<char *>(message.role));
        free(const_cast<char *>(message.content));
    }
    _messages.clear();
    _kvTokens.clear();
    _pendingPrompt.clear();
    _response.clear();
    _cacheResponseTokens.clear();
    _nCtxUsed = 0;
    _kvDirty = false;
    _clearPendingBitmaps();
    if (_ctx) llama_memory_clear(llama_get_memory(_ctx), false);
    if (_sampler) llama_sampler_reset(_sampler); // olvida las penalizaciones del chat anterior
}

// Ventana deslizante: si el prompt pasa del umbral alto, borra los mensajes mas viejos
// (conservando el sistema, el saludo y el ultimo mensaje) hasta bajar al umbral bajo.
void
LLMInference::_applyContextWindow() {
    const int nCtx = (int) llama_n_ctx(_ctx);
    const int high = (int) (nCtx * _windowHigh);
    const int low = (int) (nCtx * _windowLow);

    std::vector<std::string> roles;
    roles.reserve(_messages.size());
    for (auto &m: _messages) roles.emplace_back(m.role);
    const size_t keep = ctxwin::protectedPrefix(roles, _assistantRole);

    auto tokensAfterDrop = [&](size_t drop) -> int {
        std::vector<llama_chat_message> subset(_messages.begin(), _messages.begin() + keep);
        subset.insert(subset.end(), _messages.begin() + keep + drop, _messages.end());
        return (int) _tokenizeMessages(subset).size();
    };

    const size_t drop = ctxwin::computeDrop(_messages.size(), keep, high, low, tokensAfterDrop);
    if (drop == 0) return;

    for (size_t i = keep; i < keep + drop; i++) {
        free(const_cast<char *>(_messages[i].role));
        free(const_cast<char *>(_messages[i].content));
    }
    _messages.erase(_messages.begin() + keep, _messages.begin() + keep + drop);
    LOGi("context window: dropped %zu old messages (ctx=%d, high=%d, low=%d)", drop, nCtx, high, low);
}

// Reutiliza del cache de KV el prefijo que no cambio y deja en _pendingPrompt solo lo nuevo.
void
LLMInference::_prepareDecode() {
    const int nCtx = (int) llama_n_ctx(_ctx);
    if ((int) _promptTokens.size() >= nCtx - 16) {
        throw std::runtime_error(
            "context size too small for this prompt: increase the context length (max tokens)");
    }

    llama_memory_t mem = llama_get_memory(_ctx);
    if (_kvDirty) {
        llama_memory_clear(mem, false);
        _kvTokens.clear();
        _kvDirty = false;
    }
    std::vector<int> cached(_kvTokens.begin(), _kvTokens.end());
    std::vector<int> prompt(_promptTokens.begin(), _promptTokens.end());
    size_t common = ctxwin::reusablePrefix(cached, prompt);

    if (common < _kvTokens.size()) {
        if (!llama_memory_seq_rm(mem, 0, (llama_pos) common, -1)) {
            // Algunos caches (p. ej. ventana deslizante de Gemma) no permiten borrar a medias.
            llama_memory_clear(mem, false);
            _kvTokens.clear();
            common = 0;
        } else {
            _kvTokens.resize(common);
        }
    }

    _pendingPrompt.assign(_promptTokens.begin() + common, _promptTokens.end());
    delete _batch;
    _batch = new llama_batch();
    _batch->token = _pendingPrompt.data();
    _batch->n_tokens = _pendingPrompt.size();
    _promptDecoded = false;
}

void
LLMInference::startCompletion(const char *query) {
    if (!_storeChats) {
        _formattedMessages.clear();
        _formattedMessages = std::vector<char>(llama_n_ctx(_ctx));
    }
    _responseGenerationTime = 0;
    _responseNumTokens = 0;
    _prefillTimeUs = 0;
    _prefillTokens = 0;
    _genTimeUs = 0;
    _genTokens = 0;
    _response.clear();
    _cacheResponseTokens.clear();

    std::string queryString(query);
    if (_mtmd && !_pendingBitmaps.empty()) {
        _startMediaCompletion(queryString);
        return;
    }
    _clearPendingBitmaps();
    if (queryString.find("<turn|") != std::string::npos || queryString.find("<start_of_turn>") != std::string::npos) {
        _promptTokens = common_tokenize(llama_model_get_vocab(_model), queryString, true, true);
    } else {
        addChatMessage(query, "user");
        _applyContextWindow();
        _promptTokens = _tokenizeMessages(_messages);
    }

    _prepareDecode();
}

bool
LLMInference::_isValidUtf8(const char *response) {
    if (!response) {
        return true;
    }
    const unsigned char *bytes = (const unsigned char *) response;
    int num;
    while (*bytes != 0x00) {
        if ((*bytes & 0x80) == 0x00) {
            num = 1;
        } else if ((*bytes & 0xE0) == 0xC0) {
            num = 2;
        } else if ((*bytes & 0xF0) == 0xE0) {
            num = 3;
        } else if ((*bytes & 0xF8) == 0xF0) {
            num = 4;
        } else {
            return false;
        }
        bytes += 1;
        for (int i = 1; i < num; ++i) {
            if ((*bytes & 0xC0) != 0x80) {
                return false;
            }
            bytes += 1;
        }
    }
    return true;
}

std::string
LLMInference::completionLoop() {
    uint32_t contextSize = llama_n_ctx(_ctx);
    _nCtxUsed = llama_memory_seq_pos_max(llama_get_memory(_ctx), 0) + 1;
    if (_nCtxUsed + _batch->n_tokens > contextSize) {
        if (_promptDecoded) {
            // Se acabo el contexto a mitad de la respuesta: se termina limpiamente, sin error.
            if (!_response.empty()) addChatMessage(_response.c_str(), "assistant");
            _response.clear();
            return "[EOG]";
        }
        throw std::runtime_error("context size reached");
    }

    auto start = ggml_time_us();
    const bool firstCall = !_promptDecoded;
    const bool mediaFirst = _mediaPrefilled;
    if (mediaFirst) {
        // El prompt con imagenes ya se proceso en startCompletion: solo hay que muestrear.
        _mediaPrefilled = false;
        _promptDecoded = true;
    } else {
        if (firstCall) {
            // El prompt se procesa en trozos de n_batch: con lotes pequenos (p. ej. 64) una sola
            // llamada con todo el prompt fallaria.
            const int nB = (int) llama_n_batch(_ctx);
            int remaining = _batch->n_tokens;
            _prefillTokens = remaining;
            llama_token *ptr = _batch->token;
            while (remaining > nB) {
                llama_batch chunk = llama_batch_get_one(ptr, nB);
                if (llama_decode(_ctx, chunk) < 0) {
                    _kvTokens.clear();
                    llama_memory_clear(llama_get_memory(_ctx), false);
                    throw std::runtime_error("llama_decode() failed");
                }
                for (int i = 0; i < nB; i++) _kvTokens.push_back(ptr[i]);
                ptr += nB;
                remaining -= nB;
            }
            _batch->token = ptr;
            _batch->n_tokens = remaining;
        }
        if (llama_decode(_ctx, *_batch) < 0) {
            // El cache pudo quedar a medias: se descarta para que el siguiente turno parta limpio.
            _kvTokens.clear();
            llama_memory_clear(llama_get_memory(_ctx), false);
            throw std::runtime_error("llama_decode() failed");
        }
        _promptDecoded = true;
        for (int i = 0; i < _batch->n_tokens; i++) _kvTokens.push_back(_batch->token[i]);

        if (firstCall) _prefillTimeUs = ggml_time_us() - start;
    }
    _currToken = llama_sampler_sample(_sampler, _ctx, -1);
    
    // Check if token is EOG or if the text so far contains stop markers
    bool is_eog = llama_vocab_is_eog(llama_model_get_vocab(_model), _currToken);
    
    std::string piece = common_token_to_piece(_ctx, _currToken, true);
    
    // Check for stop sequences in the cumulative response
    static const std::vector<std::string> stop_sequences = {
        "<turn|", "<|turn_end|>", "<turn_end|>", "<start_of_turn>", "<end_of_turn>", "###", "System instruction:"
    };

    std::string current_full = _response + _cacheResponseTokens + piece;
    for (const auto& stop : stop_sequences) {
        if (current_full.find(stop) != std::string::npos) {
            is_eog = true;
            break;
        }
    }

    if (!is_eog && _maxResponse > 0 && _responseNumTokens >= _maxResponse) is_eog = true;

    if (is_eog) {
        addChatMessage(_response.c_str(), "assistant");
        _response.clear();
        return "[EOG]";
    }

    auto end = ggml_time_us();
    _responseGenerationTime += (end - start);
    _responseNumTokens += 1;
    if (!firstCall) {
        _genTimeUs += (end - start);
        _genTokens += 1;
    }
    _cacheResponseTokens += piece;

    _batch->token = &_currToken;
    _batch->n_tokens = 1;

    if (_isValidUtf8(_cacheResponseTokens.c_str())) {
        _response += _cacheResponseTokens;
        std::string valid_utf8_piece = _cacheResponseTokens;
        _cacheResponseTokens.clear();
        return valid_utf8_piece;
    }

    return "";
}

void
LLMInference::stopCompletion() {
    if (_storeChats && !_response.empty()) {
        addChatMessage(_response.c_str(), "assistant");
    }
    _response.clear();
    _cacheResponseTokens.clear();
}

LLMInference::~LLMInference() {
    for (llama_chat_message &message: _messages) {
        free(const_cast<char *>(message.role));
        free(const_cast<char *>(message.content));
    }
    _clearPendingBitmaps();
    if (_mtmd) mtmd_free(_mtmd);
    if (_ctx) llama_free(_ctx);
    if (_model) llama_model_free(_model);
    delete _batch;
    if (_sampler) llama_sampler_free(_sampler);
}

std::string
LLMInference::benchModel(int pp, int tg, int pl, int nr) {
    g_batch     = llama_batch_init(pp, 0, pl);
    auto pp_avg = 0.0;
    auto tg_avg = 0.0;
    auto pp_std = 0.0;
    auto tg_std = 0.0;

    int i, j;
    int nri;
    for (nri = 0; nri < nr; nri++) {
        common_batch_clear(g_batch);
        const int n_tokens = pp;
        for (i = 0; i < n_tokens; i++) {
            common_batch_add(g_batch, 1, i, { 0 }, false);
        }
        g_batch.logits[g_batch.n_tokens - 1] = true;
        llama_memory_clear(llama_get_memory(this->_ctx), false);

        const auto t_pp_start = ggml_time_us();
        if (llama_decode(this->_ctx, g_batch) != 0) {
            LOGe("llama_decode() failed during prompt processing");
        }
        const auto t_pp_end = ggml_time_us();

        llama_memory_clear(llama_get_memory(this->_ctx), false);
        const auto t_tg_start = ggml_time_us();
        for (i = 0; i < tg; i++) {
            common_batch_clear(g_batch);
            for (j = 0; j < pl; j++) {
                common_batch_add(g_batch, 0, i, { j }, true);
            }
            if (llama_decode(this->_ctx, g_batch) != 0) {
                LOGe("llama_decode() failed during text generation");
            }
        }
        const auto t_tg_end = ggml_time_us();

        llama_memory_clear(llama_get_memory(this->_ctx), false);

        const auto t_pp = double(t_pp_end - t_pp_start) / 1000000.0;
        const auto t_tg = double(t_tg_end - t_tg_start) / 1000000.0;
        const auto speed_pp = double(pp) / t_pp;
        const auto speed_tg = double(pl * tg) / t_tg;

        pp_avg += speed_pp;
        tg_avg += speed_tg;
        pp_std += speed_pp * speed_pp;
        tg_std += speed_tg * speed_tg;
    }

    llama_batch_free(g_batch);
    // El benchmark vació el KV: la caché de prefijo ya no vale (si no, el siguiente turno reusaría
    // tokens que no existen).
    _kvTokens.clear();
    _kvDirty = false;

    pp_avg /= double(nr);
    tg_avg /= double(nr);

    if (nr > 1) {
        pp_std = sqrt(pp_std / double(nr - 1) - pp_avg * pp_avg * double(nr) / double(nr - 1));
        tg_std = sqrt(tg_std / double(nr - 1) - tg_avg * tg_avg * double(nr) / double(nr - 1));
    } else {
        pp_std = 0;
        tg_std = 0;
    }

    char model_desc[128];
    llama_model_desc(this->_model, model_desc, sizeof(model_desc));
    const auto model_size     = double(llama_model_size(this->_model)) / 1024.0 / 1024.0 / 1024.0;
    const auto model_n_params = double(llama_model_n_params(this->_model)) / 1e9;

    std::stringstream result;
    result << std::setprecision(3);
    result << "Model: " << model_desc << " | " << model_size << " GiB | " << model_n_params << "B params\n";
    result << "PP " << pp << ": " << pp_avg << " +/- " << pp_std << " t/s\n";
    result << "TG " << tg << ": " << tg_avg << " +/- " << tg_std << " t/s\n";
    return result.str();
}
