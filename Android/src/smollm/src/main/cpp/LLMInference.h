#ifndef LLMINFERENCE_H
#define LLMINFERENCE_H

#include "llama.h"
#include "common.h"
#include "ggml.h"
#include "SamplerSettings.h"
#include "EngineOptions.h"
#include "mtmd.h"
#include "mtmd-helper.h"
#include <string>
#include <vector>

class LLMInference {
public:
    void loadModel(const char *model_path, float minP, float temperature, float topP, int topK,
                   float repeatPenalty, bool storeChats, long contextSize, const char *chatTemplate,
                   int nThreads, bool useMmap, bool useMlock,
                   const EngineOptions &options = EngineOptions());
    void addChatMessage(const char *message, const char *role);
    float getResponseGenerationTime() const;
    int getContextSizeUsed() const;
    void startCompletion(const char *query);
    std::string completionLoop();
    void stopCompletion();
    // Roleplay: borra el historial y el cache de KV sin recargar el modelo.
    void resetChat();
    // Reconstruye la cadena de muestreo con estos ajustes (se puede llamar entre turnos).
    void setSampler(const SamplerSettings &settings);
    // Roleplay: activa el canal de pensamiento (Gemma 4, token <|think|>) para el proximo turno.
    void setThinking(bool enabled);
    // Umbrales de la ventana deslizante, como fraccion del contexto (alto > bajo).
    void setContextWindow(float high, float low);
    // Limite de tokens por respuesta (-1 = sin limite); se puede cambiar entre turnos.
    void setMaxResponse(int maxTokens) { _maxResponse = maxTokens; }
    // [tokens del prompt procesados, segundos, tokens generados, segundos] del ultimo turno.
    void getPerf(float *out4) const;
    // Vision (mmproj): carga el proyector multimodal; false si no es valido o no admite imagenes.
    bool loadMmproj(const char *mmprojPath, int nThreads);
    bool supportsVision() const { return _mtmd != nullptr && mtmd_support_vision(_mtmd); }
    // Imagen RGB (3 bytes por pixel) para el proximo mensaje; el texto debe llevar un marcador
    // "<__media__>" por imagen.
    bool addImage(int width, int height, const unsigned char *rgb);
    static const char *mediaMarker() { return mtmd_default_marker(); }
    std::string benchModel(int pp, int tg, int pl, int nr);
    ~LLMInference();

private:
    llama_model *_model = nullptr;
    llama_context *_ctx = nullptr;
    llama_sampler *_sampler = nullptr;
    llama_batch *_batch = nullptr;
    llama_token _currToken;

    std::vector<llama_chat_message> _messages;
    std::vector<char> _formattedMessages;
    std::vector<llama_token> _promptTokens;
    std::string _response;
    std::string _cacheResponseTokens;
    const char *_chatTemplate = nullptr;
    bool _storeChats = true;
    std::string _assistantRole = "assistant";

    int64_t _responseGenerationTime = 0;
    int _responseNumTokens = 0;
    int _nCtxUsed = 0;
    int _maxResponse = -1;
    int64_t _prefillTimeUs = 0;
    int _prefillTokens = 0;
    int64_t _genTimeUs = 0;
    int _genTokens = 0;
    int64_t _turnStartUs = 0;

    llama_batch g_batch;

    // Tokens que ya estan procesados en el cache de KV (prompt + respuesta). Sirve para no
    // volver a procesar el prefijo del prompt en cada turno.
    std::vector<llama_token> _kvTokens;
    std::vector<llama_token> _pendingPrompt;
    bool _promptDecoded = false;
    mtmd_context *_mtmd = nullptr;
    std::vector<mtmd_bitmap *> _pendingBitmaps;
    bool _mediaPrefilled = false; // el prompt (con imagenes) ya esta en el KV; solo falta muestrear
    bool _kvDirty = false;        // el KV tiene tokens de imagen que _kvTokens no refleja
    bool _isGemma4 = false;   // plantilla <|turn> ... <turn|>: se formatea a mano
    bool _enableThinking = false; // <|think|> al inicio del turno de sistema (Gemma 4)
    float _windowHigh = 0.75f;
    float _windowLow = 0.45f;

    std::string _formatPrompt(const std::vector<llama_chat_message> &msgs);
    std::vector<llama_token> _tokenizeMessages(const std::vector<llama_chat_message> &msgs);
    void _startMediaCompletion(const std::string &query);
    void _clearPendingBitmaps();
    void _applyContextWindow();
    void _buildSampler(const SamplerSettings &settings);
    void _prepareDecode();

    static bool _isValidUtf8(const char *response);
};

#endif // LLMINFERENCE_H
