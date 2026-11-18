# Java-to-Python Conversion Checklist

This checklist tracks feature parity between the Java application and the Python desktop application. “Implemented” means Python code exists; it does not mean every Java edge case has been compared yet. Keep Java production code available as the reference until the remaining items are complete.

## Physics

- [x] Particle position, velocity, and straight-line movement in 1D, 2D, and 3D.
- [x] Collision-time prediction and elastic particle collisions in 1D, 2D, and 3D.
- [x] Reflecting arena walls.
- [x] Periodic boundaries and particle wrapping.
- [x] Divided arenas, including solid dividers and openings.
- [x] Pairwise attractive square-well interactions.
- [x] Attractive wall well and lower-wall heat-reservoir behavior in 2D/3D.
- [x] Collision-triggered forward and reverse reaction rules.
- [x] Java wall-thermostat collision scaling and accumulated kinetic-energy tracking.
- [x] Basic movable-piston kinematics and particle collisions in 1D, 2D, and 3D.
- [x] Adiabatic/isothermal piston mode switching and isothermal coupling to wall-thermostat energy tracking.
- [ ] Compare each implemented behavior against Java, including edge cases and collision ordering.
- [x] Compare controlled 1D/2D/3D isothermal piston impacts and following thermostat-adjusted wall collisions against Java particle methods.
- [x] Compare 1D/2D/3D piston prediction when the piston stops before a particle catches it, an exact piston/wall event tie, and a particle moving away from the piston against Java's particle boundary predictor.
- [x] Compare Java Calendar boundary-row selection and rescheduling after piston/wall events with Python event processing for controlled 1D/2D/3D cases.
- [ ] Exercise the full Java `Physics.advanceToTime` loop after piston target changes and with multiple particles. Its `PISTON_ALL` path recalculates boundary predictions, but still contains a TODO, and the harness does not instantiate the full Physics controller.
- [x] Port and compare Java's periodic finite-system correction for predicted average kinetic energy in 1D/2D/3D.
- [x] Port and compare Java's finite-system correction for kinetic-energy width in 1D/2D/3D.
- [x] Port and compare Java's finite-system corrected average-speed prediction in 1D/2D/3D.
- [x] Match Java's corrected RMS-speed behavior: ordinary RMS speed for nonperiodic arenas; periodic prediction remains unsupported.
- [x] Port and compare Java's finite-system corrected velocity-width prediction in 1D/2D/3D; periodic prediction remains unsupported because corrected RMS speed is unsupported.
- [ ] Port Java real-gas corrections where the statistics use them.
  - [x] Port and compare the 2D/3D `realGasQx` polynomial.
  - [x] Port and compare the 1D/2D/3D `realGasB2` pair-radius term.
  - [x] Port and compare the finite-arena `realGasB2Correction` for reflecting, periodic, divided, and divided-with-hole cases.
  - [x] Port and compare Java's type-radius-adjusted effective volume in 1D/2D/3D arena cases.
  - [x] Port and compare `realGasX` for two particle types across reflecting, periodic, and divided arenas.
- [ ] Add support for multiple particle types and reaction relationships throughout the simulation model.
- [ ] Replace or benchmark Python's all-pairs event search if it is too slow at the particle counts the application needs. Python currently recomputes candidate events instead of using Java's event calendar.

## Comparison and correctness

- [x] Java comparison harness exists in `java-tests/ParticleMovementHarness.java`.
- [x] Python unit tests exist in `python/tests/`.
- [ ] Keep matching Java and Python input cases for movement, collisions, boundaries, wells, reactions, and supported arena modes.
- [ ] Record expected outputs and comparison tolerances. Floating-point results can differ by tiny rounding amounts even when both implementations use the same equations.
- [ ] Run the full Java and Python comparison suite after each physics feature is ported.

## Simulation setup and desktop application

- [x] Expose the movable piston in the Panda3D prototype with a visible marker and P-key control in 1D/2D/3D.
- [ ] Generate configurable initial particle positions and velocities for realistic multi-particle simulations.
- [ ] Add a Python simulation-settings model for particle types/counts, arena dimensions and mode, wells, reactions, piston, and thermostat options.
- [ ] Decide whether Python must load and save Java `.set` files; implement that compatibility if it remains a requirement.
- [ ] Build the PySide6 desktop interface for settings, simulation controls, and readouts. `python/boltzmann3d/PySide6/app.py` is currently only a stub.
- [ ] Integrate the Panda3D renderer with the desktop interface and replace hard-coded two-particle demos with configurable simulations.
- [ ] Add pause, resume, reset, stepping, simulation-speed, and dimension controls to the finished interface.

## Statistics, plots, and delivery

- [ ] Port Java statistics used by the application, such as temperature, pressure, speed distributions, and radial-distribution data where enabled.
- [ ] Recreate the Java plots and readouts in the Python desktop interface.
- [ ] Port supported project presets and save/load behavior.
- [ ] Document installation, launch, settings, and controls for Python users.
- [ ] Package the Python desktop application for the target operating systems.
- [ ] Remove or archive Java only after the Python feature checklist and comparison suite are complete.

## Current implementation references

- Python physics: `python/boltzmann3d/physics/`
- Panda3D prototype: `python/boltzmann3d/panda3d/`
- PySide6 entry point: `python/boltzmann3d/PySide6/app.py`
- Java physics reference: `src/edu/byu/chem/boltzmann/model/physics/`
- Java settings persistence: `src/edu/byu/chem/boltzmann/model/io/`
- Java comparison harness: `java-tests/ParticleMovementHarness.java`
