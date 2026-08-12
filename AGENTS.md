# AGENTS.md

Guidance for AI coding agents working on this repository.

## Build prerequisites

- Java 21 is **required** (see `build.gradle.kts`, `toolchain`). Gradle 8.14.3
  cannot run on JDK 26, and the project compiles against a 21 toolchain.
- On Arch Linux: `sudo pacman -S jdk21-openjdk`.
- Alternatively install Temurin 21 into `~/.gradle/jdks` (Gradle auto-detects
  toolchains there). A validated install lives there on the dev machine.

## Commands

Run every Gradle command with a JDK 21 on `JAVA_HOME` (or `org.gradle.java.home`):

    export JAVA_HOME=/usr/lib/jvm/java-21-openjdk   # or ~/.gradle/jdks
    ./gradlew compileJava            # compile main
    ./gradlew test                   # unit tests (JUnit 5, excludes performance tags)
    ./gradlew perfTest               # opt-in performance benchmarks (10k-particle guard)
    ./gradlew run                    # launch the JavaFX application

## Conventions

- Threading: all `SimulationWorld` mutation happens on the simulation engine
  thread. UI additions must go through `SimulationEngine.submit(...)` or the
  `CommandManager`. See `SimulationEngine` javadoc.
- `AttractionMatrix` and `SpeciesRegistry` invariants: matrix size must equal
  the species count; particle species indices are always `< speciesCount`.
  Do not resize one without the other.
- Keep new logic behind the existing Strategy/Factory seams (`BoundaryStrategy`,
  `ForceFunction`, `ParticleRenderer`, `RenderMode`).