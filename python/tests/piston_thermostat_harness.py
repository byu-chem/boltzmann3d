"""Print 1D/2D/3D isothermal-piston cases for comparison with Java.

Run from the repository root with:
    PYTHONPATH=python python python/tests/piston_thermostat_harness.py

Compare these results with ``ParticleMovementHarness`` in Java.
"""

from math import sqrt

from boltzmann3d.physics import Particle, Piston, PistonMode, Simulation


_ATOMIC_MASS_UNIT_KG = 1.660538782e-27


def _assert_close(label: str, actual: float, expected: float) -> None:
    if abs(actual - expected) > 1.0e-12:
        raise AssertionError(f"{label}: expected {expected}, got {actual}")


def _assert_energy_close(label: str, actual: float, expected: float) -> None:
    if abs(actual - expected) > abs(expected) * 1.0e-12:
        raise AssertionError(f"{label}: expected {expected}, got {actual}")


def run_dimension_case(
    dimension: int,
    position: tuple[float, ...],
    velocity: tuple[float, ...],
    arena_sizes: tuple[float, ...],
) -> None:
    particle = Particle(position=position, velocity=velocity, mass=1.0, radius=1.0)
    piston_axis = 0 if dimension == 1 else 1
    simulation = Simulation(
        [particle],
        arena_sizes,
        piston=Piston(arena_sizes[piston_axis], speed=2.0),
    )
    simulation.set_piston_target_level(
        0.5,
        speed=2.0,
        mode=PistonMode.ISOTHERMAL,
    )

    piston_event = simulation.next_event()
    if piston_event is None or piston_event.kind != "piston":
        raise AssertionError(f"{dimension}D expected piston event, got {piston_event}")
    _assert_close(f"{dimension}D piston event time", piston_event.time, 14.0 / 3.0)
    simulation.advance_to(piston_event.time)
    thermostat = simulation.thermostat
    if thermostat is None:
        raise AssertionError("isothermal piston mode did not create a thermostat")

    piston_position = particle.position
    piston_velocity = particle.velocity
    piston_delta_ke = thermostat.delta_ke_joules
    _assert_close(f"{dimension}D post-piston normal velocity", piston_velocity[piston_axis], -5.0)
    _assert_energy_close(
        f"{dimension}D piston deltaKE", piston_delta_ke, 12.0 * _ATOMIC_MASS_UNIT_KG
    )
    for axis in range(dimension):
        if axis != piston_axis:
            _assert_close(
                f"{dimension}D piston tangential velocity axis {axis}",
                piston_velocity[axis],
                velocity[axis],
            )

    wall_event = simulation.next_event()
    if wall_event is None or wall_event.kind != "wall":
        raise AssertionError(f"{dimension}D expected wall event, got {wall_event}")
    _assert_close(f"{dimension}D wall event time", wall_event.time, 6.4)
    simulation.advance_to(wall_event.time)
    _assert_close(
        f"{dimension}D thermostat-adjusted normal velocity",
        particle.velocity[piston_axis],
        sqrt(12.5),
    )
    _assert_energy_close(
        f"{dimension}D post-wall deltaKE",
        thermostat.delta_ke_joules,
        5.75 * _ATOMIC_MASS_UNIT_KG,
    )

    def values(items: tuple[float, ...]) -> str:
        return "(" + ",".join(f"{value:.15g}" for value in items) + ")"

    print(
        f"{dimension}D isothermal piston: event={piston_event.time:.15g} "
        f"p={values(piston_position)} v={values(piston_velocity)} "
        f"deltaKE={piston_delta_ke:.15g} J"
    )
    print(
        f"{dimension}D thermostat wall: event={wall_event.time:.15g} "
        f"p={values(particle.position)} v={values(particle.velocity)} "
        f"deltaKE={thermostat.delta_ke_joules:.15g} J"
    )


def main() -> None:
    run_dimension_case(1, (5.0,), (1.0,), (20.0,))
    run_dimension_case(2, (5.0, 5.0), (0.5, 1.0), (20.0, 20.0))
    run_dimension_case(
        3,
        (5.0, 5.0, 5.0),
        (0.5, 1.0, 0.25),
        (20.0, 20.0, 20.0),
    )


if __name__ == "__main__":
    main()
