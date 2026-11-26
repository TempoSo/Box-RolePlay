#include "../../main/cpp/KvCacheKeys.h"
#include <cstdio>
#include <fstream>
#include <thread>
#include <chrono>
using namespace kvcache;
static int fails = 0;
#define CHECK(c) do { if (!(c)) { printf("FALLO linea %d: %s\n", __LINE__, #c); fails++; } } while (0)

int main() {
    auto eog = [](int t) { return t == 106; };
    std::vector<int> t = {2}; // BOS
    for (int i = 0; i < 40; i++) t.push_back(1000 + i);
    t.push_back(106); // fin del bloque de sistema (indice 41)
    for (int i = 0; i < 10; i++) t.push_back(2000 + i);
    CHECK(sysPrefixLen(t, eog, 4096) == 42);
    CHECK(sysPrefixLen(t, eog, 20) == 0);                 // tope: bloque mas largo que el maximo
    CHECK(sysPrefixLen({2, 5, 106, 7}, eog, 4096) == 0);   // demasiado corto para valer la pena
    CHECK(sysPrefixLen({2, 5, 6, 7}, eog, 4096) == 0);     // sin fin de turno
    CHECK(sysPrefixLen({106, 5, 6}, eog, 4096) == 0);      // el token 0 no cuenta

    // Hash: estable, depende solo de los n primeros y cambia si cambia el contenido.
    std::vector<int> u = t;
    CHECK(hashTokens(t, 42) == hashTokens(u, 42));
    u[50] = 9999; // despues del bloque de sistema
    CHECK(hashTokens(t, 42) == hashTokens(u, 42));
    u[10] = 7;    // dentro del bloque
    CHECK(hashTokens(t, 42) != hashTokens(u, 42));
    CHECK(hashTokens(t, 42).size() == 16);

    CHECK(textPrefix({1, 2, 3, -2, -3, 4}) == 3);
    CHECK(textPrefix({1, 2}) == 2);
    CHECK(textPrefix({-2, 1}) == 0);
    CHECK(filePath("/d", "m1", "abc", ".sys") == "/d/m1_abc.sys");

    // Limpieza: se conservan los 2 mas recientes
    std::string dir = "/tmp/kvtest_dir";
    std::filesystem::remove_all(dir);
    std::filesystem::create_directories(dir);
    for (int i = 0; i < 4; i++) {
        std::ofstream(dir + "/f" + std::to_string(i) + (i % 2 ? ".chat" : ".sys")) << "x";
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
    }
    std::ofstream(dir + "/otro.txt") << "x"; // no se toca
    CHECK(prune(dir, 1, 1000) == 1); // solo 1 .chat: cae f1.chat
    CHECK(!std::filesystem::exists(dir + "/f1.chat") && std::filesystem::exists(dir + "/f3.chat"));
    CHECK(std::filesystem::exists(dir + "/f0.sys") && std::filesystem::exists(dir + "/f2.sys"));
    CHECK(prune(dir, 1, 2) == 1); // 3 archivos de 1 byte, tope 2: cae el mas viejo (f0.sys)
    CHECK(!std::filesystem::exists(dir + "/f0.sys") && std::filesystem::exists(dir + "/f3.chat"));
    CHECK(prune(dir, 1, 0) == 1); // tope 0: queda siempre el mas reciente
    CHECK(std::filesystem::exists(dir + "/f3.chat") && !std::filesystem::exists(dir + "/f2.sys"));
    CHECK(std::filesystem::exists(dir + "/otro.txt"));
    CHECK(prune("/tmp/no_existe_dir_kv", 2, 10) == 0);

    printf(fails ? "%d FALLOS\n" : "TODO OK\n", fails);
    return fails ? 1 : 0;
}
