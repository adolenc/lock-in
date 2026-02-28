# Copilot Instructions

## Build, Test, and Lint

This is a standard Android project using Gradle.

You DO NOT build the app on your own, the builds are always done manually by user from android studio.

## High-Level Architecture

The application follows a standard MVVM architecture with a clean separation between UI and Data layers.

- **Data Layer (`com.example.trivialfitnesstracker.data`)**
  - **Database:** Uses Room for local persistence (`AppDatabase`).
  - **Repository:** `WorkoutRepository` acts as the single source of truth, mediating between DAOs and the UI.
  - **Entities:** defined in `data/entity` (e.g., `Exercise`, `WorkoutSession`, `ExerciseLog`).
  - **DAOs:** defined in `data/dao` for database access.

- **UI Layer (`com.example.trivialfitnesstracker.ui`)**
  - Organized by feature (e.g., `history`, `settings`, `stats`, `workout`).
  - **ViewModels:** Handle UI logic and state (e.g., `WorkoutViewModel`), communicating with the Repository.
  - **Activities/Fragments:** Observe ViewModels and handle user interaction.
  - **Custom Views:** Found in `ui/stats` (e.g., `ContributionGraphView`).

## Key Conventions

- **Dependency Injection:**
  - Uses manual dependency injection. `AppDatabase` is a singleton accessed via `AppDatabase.getDatabase(context)`.
  - ViewModels are typically instantiated with dependencies manually or via factories where needed.

- **Concurrency:**
  - Uses Kotlin Coroutines (`suspend` functions).
  - Explicitly switches contexts using `Dispatchers.IO` for database operations and `Dispatchers.Main` for UI updates (e.g., `lifecycleScope.launch`).

- **Date and Time:**
  - Time is stored as Epoch Milliseconds (`Long`).
  - "Adjusted Time" logic is used: The "day" logically starts at 4:00 AM. Logic for this is centralized in `WorkoutRepository.getAdjustedTime()`.
  - Uses `java.time` (`Instant`, `ZoneId`, `LocalDate`) for date calculations.

- **Database Migrations:**
  - Schema changes require manual `Migration` definitions in `AppDatabase` (e.g., `MIGRATION_1_2`).
  - `exportSchema = false` is currently set in `AppDatabase`.

- **Navigation:**
  - Standard Intent-based navigation between Activities.

- **UI Components:**
  - Uses standard Android View system (XML layouts), not Jetpack Compose.
  - Custom Views handle complex visualizations (graphs).
