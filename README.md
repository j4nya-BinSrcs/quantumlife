# Particle Life 3D

An interactive 3D [Particle Life](https://en.wikipedia.org/wiki/Particle_life)
simulation built with JavaFX. Particles of different species attract and repel
each other according to a customizable attraction matrix, producing emergent
structures: flocks, vortices, predator-prey chains, and morphing cell-like
clusters. The world is a cubic volume rendered in real time with an orbit
camera, and everything from the force curve to the boundary conditions is
tunable while the simulation runs.

## Features

- **3D JavaFX viewport** — billboard-mesh renderer that sustains 10k+
  particles at interactive rates, with an orbit camera (yaw/pitch/distance),
  auto-orbit, and toggles for grid, axes, bounding box, motion trails, and
  bloom glow.
- **Particle Life physics** — pairwise forces between species driven by an
  editable attraction matrix; both smooth and piecewise-linear force curves.
- **Live-tunable world** — particle count, species count, spawn seed, world
  size, interaction radius, friction half-life, time step, speed cap, damping,
  and time scaling — all adjustable from the sidebar without restarting.
- **Boundary strategies** — periodic wrap (toroidal), bouncing walls, or an
  open world with a soft pull-back spring.
- **Opt-in GPU compute pass** — the pairwise force accumulation can run as
  OpenGL compute shaders (LWJGL + EGL). `AUTO` always stays on the CPU; select
  `GPU` explicitly to try it. See [GPU backend](#gpu-backend) for caveats.
- **Preset management** — curated starter worlds seeded on first run, plus
  save/load/delete of your own simulations to a local SQLite database.
- **Persistence** — window geometry, theme, camera pose, and the last session
  are restored across runs (crash-safe JSON config).
- **Heatmap editor** — visualize and edit the attraction matrix as a species ×
  species heatmap, with undo/redo support.
- **Themes** — dark and light themes applied to the whole UI and viewport.

## Requirements

- **JDK 21** (toolchain). The Gradle wrapper pins this via
  `org.gradle.java.home` in `gradle.properties`; adjust that path if your JDK
  21 lives elsewhere. Gradle 8.14.3 does **not** run on JDK 26+.
- A GPU/OpenGL driver is used for rendering; the GPU compute pass needs
  OpenGL 4.3+ (falling back gracefully to CPU when unavailable).
- Linux is the primary target (LWJGL native libraries are configured for
  Linux). Build scripts use the `-Xmx2g` heap.

## Build and run

```sh
./gradlew run
```

The build fetches dependencies from Maven Central on first run. The first
launch seeds three built-in presets and opens the default world.

## Tests

```sh
./gradlew test          # unit tests (JUnit 5)
./gradlew perfTest      # opt-in performance benchmarks
```

## Using the app

- **Orbit** — drag to rotate; scroll to zoom. **Reset view** and **auto-orbit**
  are in the visualization section of the sidebar.
- **Species** — add/remove species; each has a color and radius.
- **Matrix** — the heatmap edits attraction between species pairs; `beta` (the
  repulsion-zone fraction of the interaction radius) and the force-curve type
  are in the physics section.
- **Physics** — world size, interaction radius, friction half-life, time step,
  speed cap, boundary type, and compute backend.
- **Simulation** — particle count, species count, seed, spawn radius, and time
  scale (speed multiplier).
- **Database** — save the current world as a named preset, load a saved one,
  or delete it.
- **Themes** — switch dark/light; the choice persists.

## Architecture

The codebase is split into decoupled packages under `com.particlelife`:

| Package | Responsibility |
| --- | --- |
| `app` | JavaFX entry point, application context, lifecycle |
| `config` | Crash-safe JSON config for window/theme/camera/session |
| `core.physics` | Force calculators, spatial grid, integrator, boundaries, compute backends |
| `core.engine` | Simulation engine thread, `FrameSnapshot` hand-off to the renderer |
| `core.simulation` | World/settings state shared with the UI |
| `core.commands` | Undoable command layer for UI edits |
| `forces` | Attraction matrix and force functions |
| `math` | `Vector3`, `MathUtils`, deterministic RNG |
| `particle` | Structure-of-arrays `ParticleStore` (hot-path layout) |
| `presets` | Built-in starter worlds |
| `render` | 3D viewport, particle renderers, orbit camera, world decor |
| `serialization` | JSON mapping of worlds/settings |
| `species` | Species model and registry |
| `database` | SQLite preset storage (one file, `~/.particle-life-3d/presets.db`) |
| `events` | Decoupled event bus between engine and UI |
| `themes` | UI theming |
| `ui` | JavaFX sidebar, heatmap editor, HUD |

The physics engine runs on its own thread; each step's state is copied into a
`FrameSnapshot` and handed to the renderer, so the UI and physics never touch
the same arrays concurrently. Rendering uses a single `TriangleMesh` per
species (billboards) rather than thousands of scene-graph nodes, which is what
keeps large populations smooth.

## GPU backend

`GpuForceEngine` runs the pairwise force pass as OpenGL compute shaders over a
uniform spatial grid, mirroring the CPU `SpatialGrid` semantics with minimum
image and normalized-force conventions. It is created headless through EGL
(discrete GPU preferred, GL 4.3+ required; hidden GLFW window as fallback).

**Known limitations:**

- **Latency-bound below large populations.** The per-frame CPU↔GPU sync costs
  on the order of 100–200 µs, so below tens of thousands of particles the
  parallel CPU spatial grid is faster. `AUTO` therefore never selects the GPU
  backend.
- **Driver instability at high load.** Sustained runs at roughly 30k+
  particles have reproduced NVIDIA driver crashes (SIGSEGV in the EGL driver).
  Keep heavy GPU runs below ~25k particles, and treat the GPU backend as
  experimental.
- The GPU pass is FP32, so results are physically equivalent but not
  bit-identical to the double-precision CPU path (validated to <0.7% relative
  error).

## Data locations

- **Config**: `~/.particle-life-3d/config.json`
- **Preset database**: `~/.particle-life-3d/presets.db` (SQLite)

## License

Proprietary — all rights reserved unless stated otherwise.