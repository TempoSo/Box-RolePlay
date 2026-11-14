#ifndef ENGINEOPTIONS_H
#define ENGINEOPTIONS_H

#include "ggml.h"

// Ajustes de rendimiento del motor (estilo "CPU" de Layla). Llegan desde Kotlin como arreglo de
// enteros; el orden debe ser el mismo que EngineOptions.toIntArray en SmolLM.kt.
struct EngineOptions {
    int nBatch = 512;        // lote logico maximo (tokens por llamada de decodificacion)
    int nUBatch = 512;       // lote fisico (se recorta a nBatch)
    int nThreadsBatch = 0;   // 0 = mismo numero de hilos que la generacion
    int flashAttn = 0;       // 0 = auto, 1 = apagado, 2 = encendido
    int kvTypeK = 1;         // indice en kvType(): 0 f32, 1 f16, 2 bf16, 3 q8_0, 4 q5_0, 5 q5_1, 6 q4_0, 7 q4_1, 8 iq4_nl
    int kvTypeV = 1;
    int repack = 1;          // 1 = reempaquetar pesos para la CPU al cargar
    int maxResponse = -1;    // -1 = sin limite

    static constexpr int COUNT = 8;

    bool fromArray(const int *a, int n) {
        if (a == nullptr || n < COUNT) return false;
        nBatch = a[0] < 1 ? 1 : a[0];
        nUBatch = a[1] < 1 ? 1 : a[1];
        nThreadsBatch = a[2] < 0 ? 0 : a[2];
        flashAttn = a[3];
        kvTypeK = a[4];
        kvTypeV = a[5];
        repack = a[6];
        maxResponse = a[7];
        return true;
    }

    static ggml_type kvType(int i) {
        switch (i) {
            case 0: return GGML_TYPE_F32;
            case 2: return GGML_TYPE_BF16;
            case 3: return GGML_TYPE_Q8_0;
            case 4: return GGML_TYPE_Q5_0;
            case 5: return GGML_TYPE_Q5_1;
            case 6: return GGML_TYPE_Q4_0;
            case 7: return GGML_TYPE_Q4_1;
            case 8: return GGML_TYPE_IQ4_NL;
            default: return GGML_TYPE_F16;
        }
    }
};

#endif
