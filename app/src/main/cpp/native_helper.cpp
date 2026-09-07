#ifndef STANDALONE_EXE
#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#define LOG_TAG "HighResNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#include <cstdio>
#define LOGI(...) printf(__VA_ARGS__)
#define LOGE(...) printf(__VA_ARGS__)
#endif

#include <cstring>
#include <string>
#include <cstdlib>
#include <cstdio>
#include <unistd.h>
#include <sys/uio.h>
#include <dirent.h>
#include <sys/stat.h>

static pid_t findMainGamePid() {
    DIR* proc = opendir("/proc");
    if (!proc) return -1;

    struct dirent* entry;
    while ((entry = readdir(proc)) != nullptr) {
        if (entry->d_type != DT_DIR) continue;

        char path[256];
        snprintf(path, sizeof(path), "/proc/%s/cmdline", entry->d_name);

        FILE* f = fopen(path, "r");
        if (!f) continue;

        char cmdline[256] = {0};
        if (fgets(cmdline, sizeof(cmdline), f)) {
            fclose(f);
            if (strcmp(cmdline, "com.tencent.tmgp.gnyx") == 0) {
                pid_t pid = atoi(entry->d_name);
                closedir(proc);
                LOGI("Found main game process: %d", pid);
                return pid;
            }
        } else {
            fclose(f);
        }
    }
    closedir(proc);
    return -1;
}

static ssize_t readRemote(pid_t pid, void* remoteAddr, void* localBuf, size_t len) {
    struct iovec local = {localBuf, len};
    struct iovec remote = {remoteAddr, len};
    return process_vm_readv(pid, &local, 1, &remote, 1, 0);
}

static ssize_t writeRemote(pid_t pid, void* remoteAddr, void* localBuf, size_t len) {
    struct iovec local = {localBuf, len};
    struct iovec remote = {remoteAddr, len};
    return process_vm_writev(pid, &local, 1, &remote, 1, 0);
}

#ifndef STANDALONE_EXE
extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_hook_highres_NativeHelper_setRenderLevel(JNIEnv* env, jclass clazz, jint level) {
    LOGI("setRenderLevel called with level: %d", level);

    pid_t mainPid = findMainGamePid();
    if (mainPid <= 0) {
        LOGE("Main game process not found");
        return JNI_FALSE;
    }

    char mapsPath[64];
    snprintf(mapsPath, sizeof(mapsPath), "/proc/%d/maps", mainPid);

    FILE* maps = fopen(mapsPath, "r");
    if (!maps) {
        LOGE("Failed to open maps for pid %d", mainPid);
        return JNI_FALSE;
    }

    const char* targetStr = "fp.DefaultRenderLevel";
    size_t targetLen = strlen(targetStr);

    char line[1024];
    uintptr_t textStart = 0, textEnd = 0;
    uintptr_t dataStart = 0, dataEnd = 0;
    uintptr_t rwStart = 0, rwEnd = 0;

    while (fgets(line, sizeof(line), maps)) {
        if (!strstr(line, "libUE4.so")) continue;

        uintptr_t start, end;
        char perms[8];
        sscanf(line, "%lx-%lx %s", &start, &end, perms);

        if (strstr(perms, "r-x")) {
            if (!textStart) { textStart = start; textEnd = end; }
        } else if (strstr(perms, "rw-")) {
            if (!rwStart) { rwStart = start; rwEnd = end; }
            else { dataStart = start; dataEnd = end; }
        }
    }
    fclose(maps);

    LOGI("Main process libUE4.so: text=%lx-%lx, rw=%lx-%lx, data=%lx-%lx",
         textStart, textEnd, rwStart, rwEnd, dataStart, dataEnd);

    if (!textStart || !rwStart) {
        LOGE("Could not find libUE4.so segments in main process");
        return JNI_FALSE;
    }

    // Scan text segment for the CVar name string
    uintptr_t stringAddr = 0;
    const size_t SCAN_CHUNK = 4096;
    unsigned char* buf = (unsigned char*)malloc(SCAN_CHUNK);

    for (uintptr_t addr = textStart; addr < textEnd; addr += SCAN_CHUNK - targetLen) {
        size_t toRead = SCAN_CHUNK;
        if (addr + toRead > textEnd) toRead = textEnd - addr;

        ssize_t nread = readRemote(mainPid, (void*)addr, buf, toRead);
        if (nread <= 0) continue;

        for (size_t i = 0; i + targetLen <= (size_t)nread; i++) {
            if (memcmp(buf + i, targetStr, targetLen) == 0) {
                stringAddr = addr + i;
                LOGI("Found CVar name string at remote: %lx", stringAddr);
                break;
            }
        }
        if (stringAddr) break;
    }

    if (!stringAddr) {
        LOGE("CVar name string not found in main process");
        free(buf);
        return JNI_FALSE;
    }

    // Scan all rw segments of libUE4.so in the main process for a pointer to our string
    // FAutoConsoleVariableRef layout (64-bit):
    // offset 0: next (pointer to next CVarRef)
    // offset 8: name (pointer to name string) <-- this is what we search for
    // offset 16: valuePtr (pointer to int32_t value)

    uintptr_t structAddr = 0;
    uintptr_t allRegions[][2] = {{rwStart, rwEnd}, {dataStart, dataEnd}};

    const size_t SCAN_BUF_SIZE = 65536;
    unsigned char* scanBuf = (unsigned char*)malloc(SCAN_BUF_SIZE);

    for (int r = 0; r < 2; r++) {
        uintptr_t rStart = allRegions[r][0];
        uintptr_t rEnd = allRegions[r][1];
        if (!rStart) continue;

        LOGI("Scanning rw region %lx-%lx for string pointer", rStart, rEnd);

        for (uintptr_t chunkStart = rStart; chunkStart < rEnd; chunkStart += SCAN_BUF_SIZE - 8) {
            size_t toRead = SCAN_BUF_SIZE;
            if (chunkStart + toRead > rEnd) toRead = rEnd - chunkStart;

            ssize_t nread = readRemote(mainPid, (void*)chunkStart, scanBuf, toRead);
            if (nread <= 0) continue;

            for (size_t i = 0; i + 8 <= (size_t)nread; i += 8) {
                uintptr_t ptrVal;
                memcpy(&ptrVal, scanBuf + i, 8);

                if (ptrVal == stringAddr) {
                    uintptr_t candidate = chunkStart + i - 8;
                    LOGI("Found candidate FAutoConsoleVariableRef at: %lx", candidate);

                    // Verify: read the value pointer at offset 16 and check it points to valid memory
                    uintptr_t testValPtr = 0;
                    ssize_t vr = readRemote(mainPid, (void*)(candidate + 16), &testValPtr, 8);
                    if (vr == 8 && testValPtr > 0x1000) {
                        structAddr = candidate;
                        LOGI("Confirmed FAutoConsoleVariableRef at remote: %lx, valuePtr=%lx", structAddr, testValPtr);
                        break;
                    }
                }
            }
            if (structAddr) break;
        }
        if (structAddr) break;
    }
    free(scanBuf);

    if (!structAddr) {
        LOGE("FAutoConsoleVariableRef structure not found");
        free(buf);
        return JNI_FALSE;
    }

    // Read the value pointer (offset 16 from struct start)
    uintptr_t valuePtr = 0;
    ssize_t nread = readRemote(mainPid, (void*)(structAddr + 16), &valuePtr, 8);
    if (nread != 8 || valuePtr == 0) {
        LOGE("Failed to read value pointer from struct");
        free(buf);
        return JNI_FALSE;
    }

    LOGI("CVar value pointer at remote: %lx", valuePtr);

    // Write the new value
    int32_t newLevel = (int32_t)level;
    ssize_t nwritten = writeRemote(mainPid, (void*)valuePtr, &newLevel, sizeof(newLevel));
    if (nwritten != sizeof(newLevel)) {
        LOGE("Failed to write new value");
        free(buf);
        return JNI_FALSE;
    }

    // Verify by reading back
    int32_t verifyVal = 0;
    readRemote(mainPid, (void*)valuePtr, &verifyVal, 4);
    LOGI("Verified: CVar fp.DefaultRenderLevel = %d in main process (PID %d)", verifyVal, mainPid);

    free(buf);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_hook_highres_NativeHelper_findAndHookCVar(JNIEnv* env, jclass clazz) {
    return JNI_FALSE;
}

} // extern "C"
#endif // !STANDALONE_EXE

#ifdef STANDALONE_EXE

static pid_t findMainGamePid_standalone() {
    DIR* proc = opendir("/proc");
    if (!proc) return -1;
    struct dirent* entry;
    while ((entry = readdir(proc)) != nullptr) {
        if (entry->d_type != DT_DIR) continue;
        char path[256];
        snprintf(path, sizeof(path), "/proc/%s/cmdline", entry->d_name);
        FILE* f = fopen(path, "r");
        if (!f) continue;
        char cmdline[256] = {0};
        if (fgets(cmdline, sizeof(cmdline), f)) {
            fclose(f);
            if (strcmp(cmdline, "com.tencent.tmgp.gnyx") == 0) {
                pid_t pid = atoi(entry->d_name);
                closedir(proc);
                return pid;
            }
        } else {
            fclose(f);
        }
    }
    closedir(proc);
    return -1;
}

static ssize_t readRemote_standalone(pid_t pid, void* remoteAddr, void* localBuf, size_t len) {
    struct iovec local = {localBuf, len};
    struct iovec remote = {remoteAddr, len};
    return process_vm_readv(pid, &local, 1, &remote, 1, 0);
}

static ssize_t writeRemote_standalone(pid_t pid, void* remoteAddr, void* localBuf, size_t len) {
    struct iovec local = {localBuf, len};
    struct iovec remote = {remoteAddr, len};
    return process_vm_writev(pid, &local, 1, &remote, 1, 0);
}

int main(int argc, char* argv[]) {
    int level = 4;
    if (argc > 1) level = atoi(argv[1]);
    printf("[set_render_level] Target level: %d\n", level);

    pid_t mainPid = findMainGamePid_standalone();
    if (mainPid <= 0) {
        printf("[set_render_level] ERROR: Main game process not found\n");
        return 1;
    }
    printf("[set_render_level] Found main game process: %d\n", mainPid);

    char mapsPath[64];
    snprintf(mapsPath, sizeof(mapsPath), "/proc/%d/maps", mainPid);
    FILE* maps = fopen(mapsPath, "r");
    if (!maps) {
        printf("[set_render_level] ERROR: Cannot open maps\n");
        return 1;
    }

    const char* targetStr = "fp.DefaultRenderLevel";
    size_t targetLen = strlen(targetStr);
    char line[1024];

    // Collect useful segments: libUE4.so (all perms) + anonymous rw- (for native heap/globals)
    // Skip dalvik/ART/stack segments to avoid Java memory
    #define MAX_SEGS 4096
    uintptr_t segStarts[MAX_SEGS], segEnds[MAX_SEGS];
    char segNames[MAX_SEGS][128];
    int segCount = 0;

    while (fgets(line, sizeof(line), maps)) {
        uintptr_t start, end;
        char perms[8];
        sscanf(line, "%lx-%lx %s", &start, &end, perms);
        if (perms[0] != 'r') continue;
        
        // Skip dalvik/ART/stack/boot segments
        if (strstr(line, "dalvik") || strstr(line, "boot") || strstr(line, "stack_and_tls") || strstr(line, ".art"))
            continue;
        
        if (segCount >= MAX_SEGS) break;
        
        segStarts[segCount] = start;
        segEnds[segCount] = end;
        // Extract segment name
        char* name = segNames[segCount];
        name[0] = 0;
        char* bracket = strchr(line, '[');
        if (bracket) { 
            char* endb = strchr(bracket, ']');
            if (endb) { 
                size_t len = endb - bracket + 1;
                if (len > 127) len = 127;
                strncpy(name, bracket, len); 
                name[len] = 0; 
            }
        } else {
            // For file-mapped segments, get filename
            char* slash = strrchr(line, '/');
            if (slash) { 
                char* space = strchr(slash, ' ');
                if (!space) space = slash + strlen(slash);
                size_t len = space - slash;
                if (len > 127) len = 127;
                strncpy(name, slash, len);
                name[len] = 0;
            }
        }
        // Remove trailing newline/whitespace
        size_t nlen = strlen(name);
        while (nlen > 0 && (name[nlen-1] == '\n' || name[nlen-1] == ' ')) name[--nlen] = 0;
        segCount++;
    }
    fclose(maps);

    printf("[set_render_level] Found %d useful segments (skipped dalvik/ART)\n", segCount);

    // Search mode: find all strings matching a pattern
    if (argc > 2 && strcmp(argv[1], "search") == 0) {
        const char* pattern = argv[2];
        size_t patLen = strlen(pattern);
        printf("[set_render_level] Searching for pattern: '%s'\n", pattern);
        const size_t SCAN_CHUNK = 65536;
        unsigned char* buf = (unsigned char*)malloc(SCAN_CHUNK);
        int found = 0;
        for (int s = 0; s < segCount; s++) {
            uintptr_t segS = segStarts[s], segE = segEnds[s];
            for (uintptr_t addr = segS; addr < segE; addr += SCAN_CHUNK - patLen) {
                size_t toRead = SCAN_CHUNK;
                if (addr + toRead > segE) toRead = segE - addr;
                ssize_t nread = readRemote_standalone(mainPid, (void*)addr, buf, toRead);
                if (nread <= 0) continue;
                for (size_t i = 0; i + patLen <= (size_t)nread; i++) {
                    if (memcmp(buf + i, pattern, patLen) == 0) {
                        // Print the surrounding string
                        size_t start = i > 32 ? i - 32 : 0;
                        size_t end = i + patLen + 64;
                        if (end > (size_t)nread) end = nread;
                        char str[128] = {0};
                        size_t len = end - start;
                        if (len > 127) len = 127;
                        memcpy(str, buf + start, len);
                        printf("[set_render_level] Found at %lx (%s): ...%s...\n", addr + i, segNames[s], str);
                        found++;
                        if (found >= 50) { printf("[set_render_level] Too many matches, stopping\n"); free(buf); return 0; }
                        i += patLen - 1;
                    }
                }
            }
        }
        printf("[set_render_level] Total matches: %d\n", found);
        free(buf);
        return 0;
    }

    // Integer pattern search: search for consecutive int32 values
    // Usage: set_render_level search-int <val1> <val2> [val3] ...
    if (argc > 2 && strcmp(argv[1], "search-int") == 0) {
        int values[16];
        int valCount = argc - 2;
        if (valCount > 16) valCount = 16;
        for (int i = 0; i < valCount; i++) values[i] = atoi(argv[i + 2]);
        printf("[set_render_level] Searching for int32 pattern: ");
        for (int i = 0; i < valCount; i++) printf("%d ", values[i]);
        printf("\n");

        // Only scan rw- segments (writable, non-executable)
        const size_t SCAN_CHUNK = 65536;
        unsigned char* buf = (unsigned char*)malloc(SCAN_CHUNK);
        int found = 0;
        for (int s = 0; s < segCount; s++) {
            // Re-read perms from maps for this segment
            // For now, scan all segments but filter by checking if it's likely rw
            uintptr_t segS = segStarts[s], segE = segEnds[s];
            size_t segSize = segE - segS;
            // Skip very small segments
            if (segSize < (size_t)(valCount * 4)) continue;
            for (uintptr_t addr = segS; addr < segE; addr += SCAN_CHUNK - valCount * 4) {
                size_t toRead = SCAN_CHUNK;
                if (addr + toRead > segE) toRead = segE - addr;
                ssize_t nread = readRemote_standalone(mainPid, (void*)addr, buf, toRead);
                if (nread <= 0) continue;
                for (size_t i = 0; i + valCount * 4 <= (size_t)nread; i += 4) {
                    int32_t* vals = (int32_t*)(buf + i);
                    bool match = true;
                    for (int v = 0; v < valCount; v++) {
                        if (vals[v] != values[v]) { match = false; break; }
                    }
                    if (match) {
                        printf("[set_render_level] Found int pattern at %lx (%s): ", addr + i, segNames[s]);
                        for (int v = 0; v < valCount; v++) printf("[%d]=%d ", v, vals[v]);
                        printf("\n");
                        found++;
                        if (found >= 20) { printf("[set_render_level] Too many matches, stopping\n"); free(buf); return 0; }
                    }
                }
            }
        }
        printf("[set_render_level] Total matches: %d\n", found);
        free(buf);
        return 0;
    }

    // Float pattern search: search for consecutive float values
    // Usage: set_render_level search-float <val1> <val2> ...
    if (argc > 2 && strcmp(argv[1], "search-float") == 0) {
        float values[16];
        int valCount = argc - 2;
        if (valCount > 16) valCount = 16;
        for (int i = 0; i < valCount; i++) values[i] = (float)atof(argv[i + 2]);
        printf("[set_render_level] Searching for float pattern: ");
        for (int i = 0; i < valCount; i++) printf("%.6f ", values[i]);
        printf("\n");

        const size_t SCAN_CHUNK = 65536;
        unsigned char* buf = (unsigned char*)malloc(SCAN_CHUNK);
        int found = 0;
        for (int s = 0; s < segCount; s++) {
            uintptr_t segS = segStarts[s], segE = segEnds[s];
            size_t segSize = segE - segS;
            if (segSize < (size_t)(valCount * 4)) continue;
            for (uintptr_t addr = segS; addr < segE; addr += SCAN_CHUNK - valCount * 4) {
                size_t toRead = SCAN_CHUNK;
                if (addr + toRead > segE) toRead = segE - addr;
                ssize_t nread = readRemote_standalone(mainPid, (void*)addr, buf, toRead);
                if (nread <= 0) continue;
                for (size_t i = 0; i + valCount * 4 <= (size_t)nread; i += 4) {
                    float* vals = (float*)(buf + i);
                    bool match = true;
                    for (int v = 0; v < valCount; v++) {
                        if (vals[v] != values[v]) { match = false; break; }
                    }
                    if (match) {
                        printf("[set_render_level] Found float pattern at %lx (%s): ", addr + i, segNames[s]);
                        for (int v = 0; v < valCount; v++) printf("[%d]=%.6f ", v, vals[v]);
                        printf("\n");
                        found++;
                        if (found >= 20) { printf("[set_render_level] Too many matches, stopping\n"); free(buf); return 0; }
                    }
                }
            }
        }
        printf("[set_render_level] Total matches: %d\n", found);
        free(buf);
        return 0;
    }

    // Normal mode: modify CVar
    uintptr_t stringAddr = 0;
    const size_t SCAN_CHUNK = 65536;
    unsigned char* buf = (unsigned char*)malloc(SCAN_CHUNK);

    for (int s = 0; s < segCount && !stringAddr; s++) {
        uintptr_t segS = segStarts[s], segE = segEnds[s];
        printf("[set_render_level] Scanning segment %lx-%lx (%lu bytes)\n", segS, segE, segE - segS);
        for (uintptr_t addr = segS; addr < segE; addr += SCAN_CHUNK - targetLen) {
            size_t toRead = SCAN_CHUNK;
            if (addr + toRead > segE) toRead = segE - addr;
            ssize_t nread = readRemote_standalone(mainPid, (void*)addr, buf, toRead);
            if (nread <= 0) continue;
            for (size_t i = 0; i + targetLen <= (size_t)nread; i++) {
                if (memcmp(buf + i, targetStr, targetLen) == 0) {
                    stringAddr = addr + i;
                    printf("[set_render_level] Found CVar name string at: %lx\n", stringAddr);
                    break;
                }
            }
        }
    }

    if (!stringAddr) {
        printf("[set_render_level] ERROR: CVar name string not found\n");
        free(buf);
        return 1;
    }

    uintptr_t structAddr = 0;

    const size_t SCAN_BUF_SIZE = 65536;
    unsigned char* scanBuf = (unsigned char*)malloc(SCAN_BUF_SIZE);

    for (int s = 0; s < segCount && !structAddr; s++) {
        uintptr_t rStart = segStarts[s], rEnd = segEnds[s];
        // Only scan rw segments for pointers (not executable ones)
        char perms[8] = {0};
        // Re-read perms from maps... simplified: scan all non-executable segments
        // Actually just scan all segments - safe because we're reading pointers
        printf("[set_render_level] Scanning for struct in segment %lx-%lx\n", rStart, rEnd);
        for (uintptr_t cs = rStart; cs < rEnd; cs += SCAN_BUF_SIZE - 8) {
            size_t toRead = SCAN_BUF_SIZE;
            if (cs + toRead > rEnd) toRead = rEnd - cs;
            ssize_t nread = readRemote_standalone(mainPid, (void*)cs, scanBuf, toRead);
            if (nread <= 0) continue;
            for (size_t i = 0; i + 8 <= (size_t)nread; i += 8) {
                uintptr_t ptrVal;
                memcpy(&ptrVal, scanBuf + i, 8);
                if (ptrVal == stringAddr) {
                    uintptr_t candidate = cs + i - 8;
                    uintptr_t testValPtr = 0;
                    ssize_t vr = readRemote_standalone(mainPid, (void*)(candidate + 16), &testValPtr, 8);
                    if (vr == 8 && testValPtr > 0x1000) {
                        structAddr = candidate;
                        printf("[set_render_level] Found FAutoConsoleVariableRef at: %lx, valuePtr=%lx\n", structAddr, testValPtr);
                        break;
                    }
                }
            }
        }
    }
    free(scanBuf);

    if (!structAddr) {
        printf("[set_render_level] ERROR: CVar structure not found\n");
        free(buf);
        return 1;
    }

    uintptr_t valuePtr = 0;
    ssize_t nread = readRemote_standalone(mainPid, (void*)(structAddr + 16), &valuePtr, 8);
    if (nread != 8 || valuePtr == 0) {
        printf("[set_render_level] ERROR: Cannot read value pointer\n");
        free(buf);
        return 1;
    }

    printf("[set_render_level] Value pointer: %lx\n", valuePtr);

    int32_t newLevel = (int32_t)level;
    ssize_t nwritten = writeRemote_standalone(mainPid, (void*)valuePtr, &newLevel, sizeof(newLevel));
    if (nwritten != sizeof(newLevel)) {
        printf("[set_render_level] ERROR: Failed to write value\n");
        free(buf);
        return 1;
    }

    int32_t verifyVal = 0;
    readRemote_standalone(mainPid, (void*)valuePtr, &verifyVal, 4);
    printf("[set_render_level] SUCCESS! CVar fp.DefaultRenderLevel = %d (was %d)\n", verifyVal, level);

    free(buf);
    return 0;
}
#endif
