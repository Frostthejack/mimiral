# Mimiral

Next-generation Android ebook reader with deep Kavita integration and superior TTS capabilities.

## Vision

Mimiral combines the best features of Moon+ Reader, Librera, and KOReader with unique deep integration to self-hosted library servers (Kavita, Calibre) and superior TTS capabilities.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI Framework | Jetpack Compose (Material Design 3) |
| Architecture | MVVM + Clean Architecture |
| Dependency Injection | Hilt |
| Database | Room |
| Networking | Retrofit + OkHttp |
| EPUB Parsing | Readium Mobile |
| PDF Rendering | androidx.pdf (Jetpack PDF Viewer) |
| TTS | Android TextToSpeech API + custom preprocessing |
| Image Loading | Coil |
| Async | Kotlin Coroutines + Flow |

## Project Structure

```
com.mimiral.app/
├── data/
│   ├── local/           # Room database, SharedPreferences
│   ├── remote/          # API clients (Kavita, OPDS, Calibre)
│   └── repository/      # Repository implementations
├── domain/
│   ├── model/           # Domain models
│   ├── repository/      # Repository interfaces
│   └── usecase/         # Use cases
├── ui/
│   ├── library/         # Library screen
│   ├── reader/          # Reader screen
│   ├── discover/        # Discover/sources screen
│   ├── settings/        # Settings screen
│   ├── bookdetail/      # Book detail screen
│   └── components/      # Shared UI components
├── tts/                 # TTS engine and preprocessing
├── sync/                # Sync logic (Kavita, cloud)
├── di/                  # Hilt modules
└── MimiralApp.kt        # Application class
```

## Build Phases

- **Phase 1:** Foundation — Project setup, local library, EPUB/PDF reader
- **Phase 2:** Enhanced Reading — Annotations, TTS engine, additional formats
- **Phase 3:** Cloud & Sync — OPDS, Kavita integration, free book sources
- **Phase 4:** Polish & Release — Collections, statistics, settings, testing

## Documentation

- [Build Draft](docs/Build%20Draft.md) — Full build plan
- [Design Layout Ideas](docs/Design%20Layout%20Ideas.md) — UI/UX design specs
- [Project State](project-state.md) — Current project state
- [Research](docs/Research/) — Technical research documents

## License

TBD
