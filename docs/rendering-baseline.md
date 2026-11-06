# Renderer modernization baseline

This is a source audit for the core-profile rendering work. It records what the
current LWJGL core renderer implements and what the existing
rendering smoke check exercises. This is not a report of a new interactive run.

## Scene behavior to retain

| Area | Behavior retained from the compatibility renderer | Baseline requirement |
| --- | --- | --- |
| Dimensions | 1D, 2D, and 3D scenes. 1D and 2D use orthographic projection; 3D uses perspective projection. | Keep all three modes and switch between them without stale GL state. |
| Arena | Reflecting and periodic bounds, solid divider, divider with a hole, movable piston, and Maxwell demon hole state. | Preserve geometry, colors, hole state, piston motion, and clipping in each dimension. |
| Periodic particles | Particle snapshots carry left, bottom, and back boundary-image flags; the renderer draws the corresponding translated copies. | Preserve all combinations of boundary images in 2D and 3D. |
| Particle appearance | Normal colors plus speed and kinetic-energy color overrides; particle radius and alpha are part of the frame. | Preserve the selected mode and per-particle colors. |
| Camera and sizing | Arrow keys rotate the 3D scene. Resize recomputes projection and the Swing scale rulers. There is no mouse-drag or wheel zoom handler in the current renderer. | Preserve rotation, fit-to-window behavior, and scale rulers; treat zoom as out of current behavior scope unless added separately. |
| Lighting | The renderer configures fixed-function lights. 3D rendering forces lighting on; the current view has no active lighting control. | Match current 3D shading. A new lighting toggle would be a separate feature. |
| Traces | The legacy trace setting was dropped before reaching the renderer, so `2Dliquid` did not accumulate marks. | Preserve preset trace mode with renderer-owned accumulation targets and explicit reset behavior. |

## Existing automated rendering coverage

`RenderingSmokeTest` checks visible particles in 1D, 2D, and 3D; 3D rotation
and resize; and 3D reflecting, periodic, divided, hole, and piston arenas. It
does not currently cover particle color modes, lighting variations, periodic
images at edges, Maxwell demon toggling, or trace accumulation. Its projection
check avoids fixed-function matrix queries so it can run with a core-profile
context.

`ThreadLifecycleSmokeTest` covers preset replacement and disposal while an AWT
render is in flight. It is a lifecycle check rather than a visual parity check.

## Trace behavior for the core renderer

Trace history stays renderer-owned presentation state rather than a list of past
simulation coordinates in `RenderFrame`. A pair of off-screen accumulation
targets lets each submitted frame add marks without relying on the window
system to preserve a swapped back buffer. The renderer clears history when
trace mode is disabled, a new simulation is loaded or restarted, the viewport
resizes, or the camera changes. The frame carries a reset generation. The
`2Dliquid` preset is the known trace-on fixture.

## Core-profile parity checklist

- 1D, 2D, and 3D particles and arena geometry render after dimension changes.
- All arena types, piston positions, and Maxwell demon hole states render.
- Particle colors, radii, periodic copies, clipping, and 3D shading match the
  current scene.
- Arrow-key rotation, resize, and scale rulers continue to work.
- The `2Dliquid` trace-on preset produces accumulating particle marks, and
  switching simulations or rotating the camera clears the prior trace.
- Rendering checks do not query fixed-function matrix state or depend on
  compatibility-only API.

## Core-profile implementation status

The core renderer now covers 1D/2D/3D particles, basic arena walls, periodic
images, solid dividers, divider openings with a gray 3D rim, piston geometry,
particle color overrides, 3D shading, arrow rotation, resize scaling, and
renderer-owned accumulating traces. Trace mode and point size are applied from
loaded settings, including the `2Dliquid` preset. Trace history clears on
simulation changes or restarts, camera rotation, resize, or when trace mode is
turned off.

`ant test-render-core` exercises the core backend using the existing pixel-based
smoke check. It has been made independent of fixed-function matrix queries.
After interactive visual review, the core profile is the sole renderer in the
application. The Swing surface adapter delegates lifecycle and frame drawing
through the backend-neutral `Renderer` contract. The compatibility drawing
code and the view's dependency on renderer-owned particle-color types have been
removed.
