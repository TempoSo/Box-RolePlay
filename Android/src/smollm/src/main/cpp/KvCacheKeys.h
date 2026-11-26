#ifndef KVCACHEKEYS_H
#define KVCACHEKEYS_H

#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <filesystem>
#include <functional>
#include <string>
#include <system_error>
#include <vector>

// Cache de KV en disco (BUG-045 / arranque en frio). Los archivos se nombran por CONTENIDO: el hash
// de los tokens del bloque de sistema (hasta el primer fin de turno). Asi el mismo personaje con el
// mismo texto reutiliza el archivo y, si la tarjeta cambia, el hash cambia solo.
namespace kvcache {

constexpr size_t MIN_SYS_TOKENS = 32;

// Longitud del bloque de sistema: hasta el primer token de fin de turno (incluido). El token 0 es
// BOS y no cuenta. Devuelve 0 si no hay fin de turno o el bloque es demasiado corto/largo.
inline size_t sysPrefixLen(const std::vector<int> &toks, const std::function<bool(int)> &isEog,
                           size_t maxLen) {
    for (size_t i = 1; i < toks.size() && i < maxLen; i++) {
        if (isEog(toks[i])) {
            size_t len = i + 1;
            return len >= MIN_SYS_TOKENS ? len : 0;
        }
    }
    return 0;
}

// FNV-1a de 64 bits sobre los n primeros tokens, en hexadecimal.
inline std::string hashTokens(const std::vector<int> &toks, size_t n) {
    uint64_t h = 1469598103934665603ULL;
    for (size_t i = 0; i < n && i < toks.size(); i++) {
        uint32_t v = (uint32_t) toks[i];
        for (int b = 0; b < 4; b++) {
            h ^= (v >> (8 * b)) & 0xFF;
            h *= 1099511628211ULL;
        }
    }
    char buf[17];
    snprintf(buf, sizeof(buf), "%016llx", (unsigned long long) h);
    return std::string(buf);
}

// Cuantos tokens de la lista son de texto real (hasta el primer id negativo = posicion de imagen).
inline size_t textPrefix(const std::vector<int> &toks) {
    size_t n = 0;
    while (n < toks.size() && toks[n] >= 0) n++;
    return n;
}

inline std::string filePath(const std::string &dir, const std::string &modelKey,
                            const std::string &hash, const char *ext) {
    return dir + "/" + modelKey + "_" + hash + ext;
}

// Deja como maximo `maxFiles` archivos .sys/.chat: borra los mas antiguos. Devuelve cuantos borro.
inline int prune(const std::string &dir, size_t maxFiles) {
    namespace fs = std::filesystem;
    std::error_code ec;
    std::vector<std::pair<fs::file_time_type, fs::path>> files;
    for (fs::directory_iterator it(dir, ec), end; !ec && it != end; it.increment(ec)) {
        const fs::path p = it->path();
        if (p.extension() == ".sys" || p.extension() == ".chat") {
            files.emplace_back(fs::last_write_time(p, ec), p);
        }
    }
    if (files.size() <= maxFiles) return 0;
    std::sort(files.begin(), files.end(), [](auto &a, auto &b) { return a.first < b.first; });
    int removed = 0;
    for (size_t i = 0; i + maxFiles < files.size(); i++) {
        if (fs::remove(files[i].second, ec)) removed++;
    }
    return removed;
}

} // namespace kvcache

#endif // KVCACHEKEYS_H
