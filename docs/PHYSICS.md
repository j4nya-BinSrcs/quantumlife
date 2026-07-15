# Particle Life — Physics & Algorithm

This document explains the theory behind the simulation: the physics model, the
mathematics, the algorithm, optimization strategies, and how the 3D
implementation differs from classic 2D Particle Life.

Primary references: Tom Mohr's *"How Particle Life emerges from simplicity"*
(the video this project is based on), Jeffrey Ventrella's *Clusters* (the
original inspiration for the genre), and the widely studied
`hunar4321/particle-life` implementation.

---

## 1. The idea

Particle Life is an artificial-life system built from an almost embarrassingly
simple rule set:

* There are `N` particles, each belonging to one of `K` **species** (colors).
* A `K × K` **attraction matrix** `A` assigns every *ordered* pair of species
  `(i, j)` a scalar `A[i][j] ∈ [-1, 1]`:
  * `A[i][j] > 0` — species *i* is **attracted** to species *j*
  * `A[i][j] < 0` — species *i* is **repelled** by species *j*
  * `A[i][j] = 0` — neutral
* Every simulation step, each particle sums a small pairwise force from every
  neighbor within an interaction radius and integrates the result.

Two properties make the emergent behavior far richer than ordinary molecular
dynamics:

1. **The matrix may be asymmetric** — `A[i][j] ≠ A[j][i]` is allowed. Red may
   chase green while green flees red. Newton's third law is deliberately
   violated.
2. **Forces are not derived from a potential**, so **energy is not
   conserved** — it is injected and removed by the rules themselves. Friction
   keeps the system from diverging; asymmetry keeps it from dying. The system
   settles into *dynamic* steady states — self-propelled "cells", chasing
   chains, orbiting clusters, membranes, mitosing blobs — rather than a static
   energy minimum.

## 2. The force function

The kernel used here is the piecewise-linear force popularized by Tom Mohr's
video. For two particles at distance `r`, with interaction radius `r_max`,
inner (repulsion) radius fraction `β ∈ (0, 1)`, and matrix entry `a = A[i][j]`,
define the normalized distance `x = r / r_max`:

```
           ┌  x / β − 1                                 0 ≤ x < β      (universal repulsion)
F(x, a) =  │  a · (1 − |2x − 1 − β| / (1 − β))          β ≤ x < 1      (matrix-driven)
           └  0                                         x ≥ 1          (out of range)
```

Shape, piece by piece:

* **`x < β` — universal short-range repulsion.** Independent of the matrix,
  always negative (repulsive), and equal to `−1` at contact, rising linearly
  to `0` at `x = β`. This is the "personal space" term: it prevents particle
  overlap and removes the `1/r²` singularity that plagues gravity-style
  kernels. No collision detection is needed — dense clusters stay resolved.
* **`β ≤ x < 1` — the interesting part.** A triangular bump whose *sign and
  height* are the matrix entry `a`. It is `0` at `x = β`, peaks at the
  midpoint `x = (1 + β)/2` with value exactly `a`, and falls back to `0` at
  `x = 1`. The force fades smoothly to zero at the edge of the interaction
  range — no popping when neighbors cross the cutoff.
* **`x ≥ 1` — zero.** A hard support of `r_max` is what makes spatial
  partitioning (§5) legal rather than approximate.

The actual acceleration contribution on particle *p₁* from *p₂* is

```
a₁ += (r̂₁₂ · F(r/r_max, A[s₁][s₂]) · r_max · f) / m₁
```

where `r̂₁₂` is the unit vector from *p₁* to *p₂*, `f` is a global force
multiplier, and `m₁` the mass. The `r_max` scale factor keeps behavior
size-invariant: doubling the interaction radius doubles force magnitudes so
clusters scale rather than change character. A minimum-distance clamp
(`r ← max(r, ε)`) guards the unit-vector division when two particles coincide.

Because equilibrium (`F = 0` with mutual attraction) occurs at `x = β`,
particles in a cluster sit at spacing `≈ β · r_max` — β directly controls
cluster density.

## 3. Integration and friction

Velocities and positions are advanced with **semi-implicit (symplectic)
Euler**, which is far more stable than explicit Euler for oscillatory systems
and costs the same:

```
v ← v · μ(Δt) + a · Δt
p ← p + v · Δt
```

Friction uses the **half-life parameterization** from the video: instead of an
opaque per-frame factor, the user sets `t½`, the time in which velocity decays
to half. The per-step multiplier is

```
μ(Δt) = 0.5^(Δt / t½)
```

This makes friction *frame-rate independent*: the same `t½` produces the same
motion at any time step. Because forces continuously inject energy while
friction continuously removes it, the system behaves as an over-damped medium
— particles move as if swimming in honey, which is exactly the regime where
Particle Life's structures are stable.

Additional stability guards, all configurable:

* **Maximum velocity clamp** — a hard ceiling on `|v|` so pathological matrix
  settings cannot fling particles to infinity in one frame.
* **Per-particle damping** — an extra multiplicative velocity decay.
* **Fixed time step** with a time-scale multiplier — the physics loop runs on
  a fixed `Δt` (deterministic, reproducible from a seed) decoupled from the
  render loop.

### Boundary conditions

Three strategies (Strategy pattern, selectable live):

* **Wrap (toroidal)** — positions wrap modulo the world size; distance
  calculations use the *minimum-image convention* (each axis delta is wrapped
  to `[−L/2, L/2]`) so forces act across the seam. Structures drift freely.
* **Bounce** — elastic reflection with a restitution factor at the walls of
  the bounding cube.
* **Open** — no constraint; a gentle inward pull beyond the cube keeps the
  swarm in view.

## 4. The algorithm (per step)

```
1. rebuild spatial index from current positions            O(N)
2. for each particle p:                                    O(N · k)
     for each neighbor q with |q − p| < r_max:
         accumulate force kernel into p.force
   (parallelized across particles; read-only positions)
3. for each particle p:                                    O(N)
     a = force / m;  integrate velocity (friction, clamp)
     integrate position; apply boundary strategy
4. publish an immutable snapshot for the renderer
```

Forces are accumulated into a separate buffer while positions stay read-only,
so step 2 is trivially data-parallel and the result is independent of particle
iteration order (deterministic given a seed).

## 5. Optimization strategies

Naive Particle Life is `O(N²)` — 10,000 particles means 10⁸ pair tests per
frame, hopeless on a CPU. The optimizations used here, in order of impact:

1. **Uniform spatial grid** (chosen over an octree). The world is divided
   into cubic cells of side `r_max`; a particle's neighbors can only lie in
   its own cell or the 26 adjacent ones (9 in 2D, 27 in 3D). Rebuilding is
   `O(N)` per frame using a counting-sort layout into two flat `int` arrays —
   zero allocation, cache-friendly, and much cheaper than an octree rebuild.
   Octrees win for wildly non-uniform scales; Particle Life has a single fixed
   interaction radius, the textbook case for a uniform grid. Complexity drops
   to `O(N · k)` where `k` is the mean neighbor count.
2. **Squared-distance culling** — compare `r²` against `r_max²` and take the
   square root only for actual interactions.
3. **Structure-of-arrays hot path** — positions/velocities/forces live in
   flat `double[]` arrays indexed by particle, not in object graphs. This is
   the single biggest constant-factor win on the JVM: linear memory access,
   no pointer chasing, no per-pair allocation. The object-oriented `Particle`
   API is a *view* over this storage.
4. **Parallel force pass** — the force loop is partitioned across a worker
   pool sized to the machine's cores. Physics runs on its own thread,
   decoupled from the JavaFX render thread.
5. **Renderer decoupling** — the renderer consumes double-buffered position
   snapshots and never touches live physics state; mesh/node objects are
   pooled and reused, never reallocated per frame.
6. **Profile before optimizing** — a performance test suite times the force
   pass at 500/1k/5k/10k particles so regressions are measurable.

## 6. 2D → 3D differences

Moving from the video's 2D to full 3D changes more than adding a coordinate:

* **Neighborhood size**: 27 grid cells instead of 9; the force kernel gains a
  `z` component but is otherwise unchanged (it depends only on scalar
  distance).
* **Density dilution**: at equal particle count and radius, the expected
  number of neighbors scales with the *volume* fraction `(r_max / L)³` rather
  than the area fraction. 3D needs more particles or a larger relative
  `r_max` to reach the same local density at which structures emerge —
  defaults are tuned accordingly.
* **Emergent structures differ**: 2D produces rings and amoebas; 3D produces
  shells, tubes, and orbiting satellites — closed 2D loops become spherical
  membranes.
* **Rendering cost dominates**: 2D can blit circles; 3D needs a scene graph,
  a perspective camera, depth ordering and lighting. With JavaFX the naive
  approach (one `Sphere` node per particle) collapses past ~2–3k nodes, so
  the high-performance path renders all particles of a species as a **single
  `TriangleMesh` of camera-facing billboard quads** — a handful of nodes
  total regardless of particle count.
* **Navigation**: 3D requires a real camera model — orbit (yaw/pitch around a
  pivot), pan in the view plane, dolly zoom, plus visual anchors (grid, axes,
  bounding cube) without which depth is unreadable.

## 7. Possible improvements

* **GPU compute** (the standard endgame — compute shaders make 1M+ particles
  feasible; outside JavaFX's scope but the engine's Strategy seams allow it).
* **Barnes–Hut / far-field approximation** if long-range forces were added.
* Per-pair interaction radii (4-matrix variant: attraction/repulsion strength
  *and* range per pair).
* Verlet or higher-order integrators for stiffer settings.
* Temperature/noise term (small random kicks) to escape frozen states.
* Species evolution — mutating the matrix over time.
