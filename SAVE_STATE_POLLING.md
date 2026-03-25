# v0.3: Save State Polling Implementation

## Overview

Replaced UDP network commands with mGBA save state file polling for reading party data from RetroArch.

## Changes Made

### 1. New SaveStateReader Class
- **File**: `app/src/main/java/com/ercompanion/savefile/SaveStateReader.kt`
- Searches multiple common RetroArch save state paths
- Reads party data directly from mGBA state files (offset `0x454F0`)
- Detects file changes to trigger updates
- Provides debug status information

### 2. Updated MainViewModel
- **File**: `app/src/main/java/com/ercompanion/MainViewModel.kt`
- Replaced primary polling mechanism to use `SaveStateReader`
- Kept `RetroArchClient` as fallback for enemy party scanning (UDP still used for that)
- Polls every 1000ms (1 second) instead of 500ms
- Persists party data between polls (only updates when file changes)

### 3. Storage Permissions
- **File**: `app/src/main/AndroidManifest.xml`
- Added `READ_EXTERNAL_STORAGE` permission
- Added `MANAGE_EXTERNAL_STORAGE` permission
- Added `requestLegacyExternalStorage="true"` for Android 10 compatibility

### 4. Runtime Permission Requests
- **File**: `app/src/main/java/com/ercompanion/MainActivity.kt`
- Requests `READ_EXTERNAL_STORAGE` on Android 6+
- Prompts for "All files access" on Android 11+

### 5. Updated Debug Panel
- **File**: `app/src/main/java/com/ercompanion/ui/MainScreen.kt`
- Shows save state file status
- Displays currently read file path and last modified time
- Manual path override field for custom save state locations
- Collapsible list of searched paths

## User Setup Required

1. **Enable RetroArch Auto-Save State**:
   - In RetroArch: Settings > Saving > Auto Save State = ON
   - Set interval to 5 seconds (or lowest available)

2. **Grant Storage Permission**:
   - App will prompt on first launch
   - Android 11+: Grant "All files access" permission

3. **Verify Save State Location**:
   - Check debug panel to see which file is being read
   - Override path if needed

## Technical Details

### mGBA State File Format
- Total size: `0x61000` bytes (396KB)
- EWRAM starts at offset `0x21000`
- Party count at GBA address `0x020244EC` → file offset `0x454EC`
- Party data at GBA address `0x020244F0` → file offset `0x454F0`
- Party data size: 6 × 104 bytes = 624 bytes

### Default Search Paths
1. `/storage/emulated/0/RetroArch/states`
2. `/sdcard/RetroArch/states`
3. `/storage/emulated/0/Android/data/com.retroarch.aarch64/files/states`
4. `/storage/emulated/0/Android/data/com.retroarch/files/states`

## Build Status

✅ Build successful (`./gradlew assembleDebug`)
- No compilation errors
- One unused parameter warning (non-blocking)
