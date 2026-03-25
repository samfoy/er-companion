# ER Companion

Android companion app for **Pokémon Emerald Rogue** running on AYN Thor dual-screen handheld.

## Features

- Real-time party monitoring via RetroArch Network Commands
- Gen3 Pokémon data decryption and parsing
- Dark theme optimized for gaming
- Live HP tracking with visual bars
- Expandable cards showing stats and moves
- Runtime memory address scanning

## Requirements

- Android 8.0+ (API 26)
- RetroArch with mGBA core
- RetroArch Network Commands enabled (Settings > Network > Network Commands = ON)
- RetroArch running on same device (localhost UDP:55355)

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Architecture**: MVVM with StateFlow
- **Async**: Kotlin Coroutines

## Build

```bash
./gradlew assembleDebug
```

## How It Works

1. **UDP Communication**: Sends `READ_CORE_MEMORY` commands to RetroArch's UDP server
2. **Address Scanning**: Locates party data in GBA EWRAM (0x02000000+)
3. **Decryption**: Decrypts Gen3 BoxPokemon structures using personality XOR otId
4. **Real-time Updates**: Polls memory every 500ms for live data

## Memory Layout

See `PROMPT.md` for detailed Gen3 Pokémon struct layout and decryption algorithm.

## Project Structure

```
app/src/main/java/com/ercompanion/
├── network/
│   └── RetroArchClient.kt       # UDP socket client
├── parser/
│   ├── Gen3PokemonParser.kt     # Pokemon decryption & parsing
│   └── AddressScanner.kt        # Runtime address finder
├── data/
│   └── PokemonData.kt           # Species & move name mappings
├── ui/
│   ├── MainScreen.kt            # Compose UI
│   └── theme/                   # Material3 theme
├── MainViewModel.kt             # State management
└── MainActivity.kt              # Entry point
```

## License

MIT
