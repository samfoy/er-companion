# ER Companion App — v0.3: Save State Polling

## Context

v0.2 is built and working UI-wise. The RetroArch UDP network commands approach has been abandoned — the setting resets on every launch on Android regardless of build version or config editing. We're switching to **mGBA save state file polling** instead.

## How Save State Polling Works

RetroArch writes save states to shared storage. The app watches the save state file for changes and parses EWRAM directly from it.

**mGBA save state format (confirmed from mgba source):**
- Total file size: `0x61000` bytes (396KB) — this is a fixed struct `GBASerializedState`
- Magic header: `0x01000000` + version at offset 0x00
- EWRAM (256KB) starts at file offset `0x21000`
- IWRAM (32KB) starts at file offset `0x19000`
- IO registers start at offset `0x400`

**Party data location:**
- `gPlayerParty` is at GBA address `0x020244F0` (EWRAM)
- EWRAM base = `0x02000000`
- Party offset within EWRAM = `0x020244F0 - 0x02000000 = 0x244F0`
- **Party offset in state file = `0x21000 + 0x244F0 = 0x454F0`**
- Party data size = 6 × 104 bytes = 624 bytes
- Party count byte is just before party: file offset `0x454EC` (4 bytes before, confirmed from ER source)

**Save state file path on Android:**
- Default: `/storage/emulated/0/RetroArch/states/<rom_name>.state0`
- OR `/sdcard/RetroArch/states/<rom_name>.state0`
- The app needs READ_EXTERNAL_STORAGE permission (or use SAF on Android 10+)
- Rom name for ER is likely `Emerald_Rogue` or similar

## What to Build

### 1. Replace `RetroArchClient` with `SaveStateReader`

Create `app/src/main/java/com/ercompanion/savefile/SaveStateReader.kt`:

```kotlin
import android.content.Context
import java.io.File
import java.io.RandomAccessFile

class SaveStateReader(private val context: Context) {
    
    companion object {
        const val MGBA_STATE_SIZE = 0x61000L
        const val EWRAM_OFFSET = 0x21000L
        const val PARTY_COUNT_ADDR = 0x020244ECL  // GBA address
        const val PARTY_DATA_ADDR  = 0x020244F0L  // GBA address
        val PARTY_COUNT_FILE_OFFSET = EWRAM_OFFSET + (PARTY_COUNT_ADDR - 0x02000000L)
        val PARTY_DATA_FILE_OFFSET  = EWRAM_OFFSET + (PARTY_DATA_ADDR - 0x02000000L)
        
        val SEARCH_PATHS = listOf(
            "/storage/emulated/0/RetroArch/states",
            "/sdcard/RetroArch/states",
            "/storage/emulated/0/Android/data/com.retroarch.aarch64/files/states",
            "/storage/emulated/0/Android/data/com.retroarch/files/states"
        )
    }
    
    private var stateFile: File? = null
    private var lastModified: Long = 0L
    
    // Find the most recently modified .state0 file
    fun findStateFile(): File? {
        for (path in SEARCH_PATHS) {
            val dir = File(path)
            if (!dir.exists()) continue
            val stateFiles = dir.listFiles { f -> 
                f.name.endsWith(".state0") || f.name.endsWith(".state")
            } ?: continue
            if (stateFiles.isNotEmpty()) {
                return stateFiles.maxByOrNull { it.lastModified() }
            }
        }
        return null
    }
    
    // Returns true if file has changed since last read
    fun hasNewData(): Boolean {
        val file = stateFile ?: findStateFile()?.also { stateFile = it } ?: return false
        val modified = file.lastModified()
        if (modified > lastModified) {
            lastModified = modified
            return true
        }
        return false
    }
    
    // Read raw party bytes from state file
    fun readPartyData(): Pair<Int, ByteArray>? {
        val file = stateFile ?: findStateFile()?.also { stateFile = it } ?: return null
        if (!file.exists() || file.length() < MGBA_STATE_SIZE) return null
        
        return try {
            RandomAccessFile(file, "r").use { raf ->
                // Read party count (1 byte)
                raf.seek(PARTY_COUNT_FILE_OFFSET)
                val count = raf.read() and 0xFF
                if (count !in 1..6) return null
                
                // Read party data (6 * 104 bytes)
                raf.seek(PARTY_DATA_FILE_OFFSET)
                val buf = ByteArray(624)
                raf.readFully(buf)
                Pair(count, buf)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    // Get human-readable status for debug panel
    fun getStatus(): String {
        val file = stateFile ?: findStateFile()?.also { stateFile = it }
        return when {
            file == null -> "No state file found — check paths below"
            !file.exists() -> "File missing: ${file.absolutePath}"
            file.length() < MGBA_STATE_SIZE -> "File too small (${file.length()} bytes, expected ${MGBA_STATE_SIZE})"
            else -> "Reading: ${file.name} (${file.length()/1024}KB, modified ${(System.currentTimeMillis() - file.lastModified())/1000}s ago)"
        }
    }
    
    fun getSearchedPaths(): List<String> = SEARCH_PATHS
    
    fun setManualPath(path: String) {
        stateFile = File(path)
        lastModified = 0L
    }
    
    fun clearCache() {
        stateFile = null
        lastModified = 0L
    }
}
```

### 2. Update AndroidManifest.xml

Add storage permissions:
```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
```

Also add `android:requestLegacyExternalStorage="true"` to `<application>` tag for Android 10 compatibility.

### 3. Update MainViewModel.kt

Replace the `RetroArchClient` poll loop with a save state watcher:

```kotlin
// Replace client with:
private val saveStateReader = SaveStateReader(application)

// New connection states:
// DISCONNECTED = no state file found
// CONNECTED = state file found and readable  
// ERROR = file found but can't parse

// Poll loop: check every 1000ms if file has changed
// If changed: read party data, parse, update state
// If not changed: keep last known party data (don't clear it)
```

Key behavior change: **don't clear party data when state file hasn't changed** — the party persists between polls. Only clear if the file disappears entirely.

### 4. Update Debug Panel in MainScreen.kt

Replace the host/port fields with:
- **Status line**: shows `SaveStateReader.getStatus()`
- **File path display**: shows which file is being read
- **Manual path override**: text field + Apply button to set a custom path
- **Searched paths list**: show all paths that were checked (collapsed by default)
- **Last updated**: timestamp of last successful read

### 5. Request Storage Permission at Runtime

In `MainActivity.kt`, add runtime permission request for `READ_EXTERNAL_STORAGE` on app start. For Android 11+, also check `Environment.isExternalStorageManager()` and show a prompt to grant "All files access" if needed (required to read `/sdcard/RetroArch/states/`).

```kotlin
// In MainActivity.onCreate, before setting content:
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) 
            != PackageManager.PERMISSION_GRANTED) {
        requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1001)
    }
}
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    if (!Environment.isExternalStorageManager()) {
        startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
    }
}
```

### 6. RetroArch Setup Instructions in App

Add a one-time setup screen or info dialog that tells the user:
1. In RetroArch: **Settings > Saving > Auto Save State** → ON, interval = **5 seconds** (or lowest available)
2. The state file will be at `/sdcard/RetroArch/states/<rom>.state0`
3. Tap "Rescan" after enabling auto-save

### 7. Keep UDP as Fallback

Keep `RetroArchClient.kt` in the project but mark it as secondary. `MainViewModel` should try UDP first (quick check), fall back to save state if UDP times out. This way if UDP ever gets fixed, it just works.

## Build Verification

After making changes, run:
```bash
cd /Users/sam.painter/Projects/er-companion && ./gradlew assembleDebug 2>&1
```

Fix any compilation errors before signaling completion.

## Completion Signal

When the build succeeds, output on its own line:

LOOP_COMPLETE
