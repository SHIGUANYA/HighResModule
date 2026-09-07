#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <cstring>
#include <string>

#define LOG_TAG "HighResNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// UE4 CVar registration structure (simplified)
// FAutoConsoleVariableRef stores a pointer to the actual CVar value
struct FAutoConsoleVariableRef {
    void* next;           // linked list next
    const char* name;     // CVar name
    void* valuePtr;       // pointer to the actual value
    const char* help;     // help text
    int32_t flags;        // flags
};

// Global linked list head of all registered CVars (from libUE4.so)
// We find this by scanning for the pattern after libUE4.so is loaded
static FAutoConsoleVariableRef* g_cvarHead = nullptr;

// Store the render level CVar pointer
static int32_t* g_renderLevelCVar = nullptr;
static int32_t* g_maxSupportRenderLevelCVar = nullptr;

// Find all CVars by scanning libUE4.so memory
static void findAllCVars() {
    // Get the base address of libUE4.so
    void* handle = dlopen("libUE4.so", RTLD_NOLOAD);
    if (!handle) {
        LOGE("Failed to dlopen libUE4.so: %s", dlerror());
        return;
    }

    // Get the size of the loaded library
    Dl_info info;
    if (dladdr((void*)findAllCVars, &info)) {
        LOGI("libUE4.so base: %p", info.dli_fbase);
    }

    // We need to find the CVar linked list head
    // In UE4, CVars are registered via FAutoConsoleVariableRef constructor
    // which adds to a global linked list
    // The head is typically at a known offset from the library base

    // For now, we'll try a different approach: use UE4's console exec
    // to set the CVar value
}

// Try to execute a console command via UE4's Exec function
static bool execConsoleCommand(const char* command) {
    // Find the GEngine global
    void* handle = dlopen("libUE4.so", RTLD_NOLOAD);
    if (!handle) return false;

    // Try to find Exec function - this is a heuristic approach
    // In UE4, console commands are executed via: GEngine->Exec(...)

    // Alternative: use the CVar system directly
    // Find IConsoleManager::Get().FindConsoleVariable()

    // For now, return false - we'll use the Java approach
    return false;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_hook_highres_NativeHelper_setRenderLevel(JNIEnv* env, jclass clazz, jint level) {
    LOGI("setRenderLevel called with level: %d", level);

    // Find libUE4.so
    void* handle = dlopen("libUE4.so", RTLD_NOLOAD);
    if (!handle) {
        LOGE("libUE4.so not loaded yet");
        return JNI_FALSE;
    }

    // Get library info
    Dl_info info;
    if (!dladdr((void*)handle, &info)) {
        LOGE("Failed to get libUE4.so info");
        return JNI_FALSE;
    }

    uintptr_t base = (uintptr_t)info.dli_fbase;
    LOGI("libUE4.so base address: %p", (void*)base);

    // Method 1: Try to find and modify the CVar by scanning memory
    // The CVar "fp.DefaultRenderLevel" is registered as a int32_t
    // We scan for the string pattern in the library

    // First, find the string "fp.DefaultRenderLevel" in the library
    const char* targetStr = "fp.DefaultRenderLevel";
    size_t targetLen = strlen(targetStr);

    // Read the library memory map to find executable segments
    FILE* maps = fopen("/proc/self/maps", "r");
    if (!maps) {
        LOGE("Failed to open /proc/self/maps");
        return JNI_FALSE;
    }

    char line[512];
    while (fgets(line, sizeof(line), maps)) {
        if (strstr(line, "libUE4.so") && strstr(line, "r-xp")) {
            // This is an executable segment of libUE4.so
            uintptr_t start, end;
            sscanf(line, "%lx-%lx", &start, &end);

            LOGI("Scanning segment: %lx-%lx", start, end);

            // Scan for the string pattern
            for (uintptr_t addr = start; addr < end - targetLen; addr++) {
                if (memcmp((void*)addr, targetStr, targetLen) == 0) {
                    LOGI("Found string at: %lx", addr);

                    // Found the string. The CVar value pointer should be nearby.
                    // In FAutoConsoleVariableRef, the structure is:
                    // {next, name, valuePtr, help, flags}
                    // The name points to this string, so valuePtr is at name+8 (on 64-bit)

                    // Actually, we need to find the FAutoConsoleVariableRef structure
                    // that references this string. Let's scan for pointers to this address.

                    // For simplicity, let's try a different approach:
                    // The CVar value is typically within a few hundred bytes of the string
                    // in the .data section. We'll scan the nearby memory.

                    // Actually, the best approach is to use UE4's CVar system directly
                    // via the IConsoleManager interface. But that requires finding
                    // the function pointers.

                    // For now, let's try to modify the value by scanning for
                    // the integer pattern. The default render level is 3.
                    // We want to change it to 4.

                    // Scan nearby data sections for the value
                    FILE* maps2 = fopen("/proc/self/maps", "r");
                    if (maps2) {
                        char line2[512];
                        while (fgets(line2, sizeof(line2), maps2)) {
                            if (strstr(line2, "libUE4.so") && strstr(line2, "rw-p")) {
                                uintptr_t dstart, dend;
                                sscanf(line2, "%lx-%lx", &dstart, &dend);

                                // Scan for the integer value 3 (render level)
                                for (uintptr_t daddr = dstart; daddr < dend - 4; daddr += 4) {
                                    int32_t val = *(int32_t*)daddr;
                                    if (val == 3) {
                                        // Check if this might be the render level CVar
                                        // by checking nearby strings or patterns
                                        LOGI("Found potential render level CVar at: %lx (value: %d)", daddr, val);

                                        // Try to modify it
                                        // First, we need to find the actual CVar structure
                                        // The CVar value is stored in a FAutoConsoleVariableRef
                                        // which has a pointer to the actual int32_t

                                        // For now, we'll try to directly modify the memory
                                        // This is risky but may work
                                        *(int32_t*)daddr = level;
                                        LOGI("Modified value at %lx to %d", daddr, level);

                                        // Verify
                                        int32_t newVal = *(int32_t*)daddr;
                                        LOGI("Verification: value at %lx is now %d", daddr, newVal);

                                        fclose(maps2);
                                        fclose(maps);
                                        return JNI_TRUE;
                                    }
                                }
                            }
                        }
                        fclose(maps2);
                    }
                }
            }
        }
    }
    fclose(maps);

    // Method 2: Try to use UE4's console command system
    // Find GEngine and call Exec
    LOGI("Direct memory scan failed, trying console command approach");

    // This is a fallback - we'll try to execute a console command
    // via the game's own exec mechanism

    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_hook_highres_NativeHelper_findAndHookCVar(JNIEnv* env, jclass clazz) {
    LOGI("findAndHookCVar called");

    // This method tries to find the CVar by using UE4's internal functions
    // We need to find IConsoleManager::Get().FindConsoleVariable()

    void* handle = dlopen("libUE4.so", RTLD_NOLOAD);
    if (!handle) {
        LOGE("libUE4.so not loaded");
        return JNI_FALSE;
    }

    // Try to find the IConsoleManager singleton
    // In UE4, this is typically: IConsoleManager::Get()
    // which returns a static instance

    // Find the Get() function by looking for known symbols
    // This is architecture-specific and may not work on all versions

    // For now, return false and let Java handle it
    return JNI_FALSE;
}

} // extern "C"
