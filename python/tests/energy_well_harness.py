"""Print 3D well cases for comparison with ParticleMovementHarness.java.

Run from the repository root with:
    PYTHONPATH=python python python/tests/energy_well_3d_harness.py
"""

from boltzmann3d.physics import Particle, Simulation


ARENA = (100.0, 100.0, 100.0)
RADIUS = 0.75
WELL_DEPTH = 0.0001
INTERACTION_MULTIPLIER = 2.0


def print_state(
    label: str,
    event_type: int,
    event_time: float,
    first: Particle,
    second: Particle,
) -> None:
    values = (
        event_time,
        *first.position,
        *first.velocity,
        *second.position,
        *second.velocity,
    )
    print(
        f"{label}: event={event_type} time={values[0]:.15g} "
        f"p1=({values[1]:.15g},{values[2]:.15g},{values[3]:.15g}) "
        f"v1=({values[4]:.15g},{values[5]:.15g},{values[6]:.15g}) "
        f"p2=({values[7]:.15g},{values[8]:.15g},{values[9]:.15g}) "
        f"v2=({values[10]:.15g},{values[11]:.15g},{values[12]:.15g})"
    )


def print_2d_state(
    label: str,
    event_type: int,
    event_time: float,
    first: Particle,
    second: Particle,
) -> None:
    print(
        f"{label}: event={event_type} time={event_time:.15g} "
        f"p1=({first.position[0]:.15g},{first.position[1]:.15g}) "
        f"v1=({first.velocity[0]:.15g},{first.velocity[1]:.15g}) "
        f"p2=({second.position[0]:.15g},{second.position[1]:.15g}) "
        f"v2=({second.velocity[0]:.15g},{second.velocity[1]:.15g})"
    )


def make_particle(
    position: tuple[float, float, float],
    velocity: tuple[float, float, float],
) -> Particle:
    return Particle(
        position=position,
        velocity=velocity,
        mass=1.0,
        radius=RADIUS,
        energy_well_depth=WELL_DEPTH,
    )


def make_2d_particle(
    position: tuple[float, float],
    velocity: tuple[float, float],
) -> Particle:
    return Particle(
        position=position,
        velocity=velocity,
        mass=1.0,
        radius=RADIUS,
        energy_well_depth=WELL_DEPTH,
    )


def assert_tangential_velocity_preserved(
    before: tuple[float, float, float],
    after: tuple[float, float, float],
    normal: tuple[float, float, float],
) -> None:
    change = tuple(after_component - before_component
                   for before_component, after_component in zip(before, after))
    normal_change = sum(component * axis for component, axis in zip(change, normal))
    tangential_change = tuple(
        component - normal_change * axis
        for component, axis in zip(change, normal)
    )
    if any(abs(component) > 1.0e-10 for component in tangential_change):
        raise AssertionError(f"well collision changed tangential velocity: {tangential_change}")


def assert_2d_tangential_velocity_preserved(
    before: tuple[float, float],
    after: tuple[float, float],
    normal: tuple[float, float],
) -> None:
    tangent = (-normal[1], normal[0])
    before_tangent = sum(component * axis for component, axis in zip(before, tangent))
    after_tangent = sum(component * axis for component, axis in zip(after, tangent))
    if abs(after_tangent - before_tangent) > 1.0e-10:
        raise AssertionError("2D well collision changed tangential velocity")


def advance_to_next(simulation: Simulation, expected_kind: str):
    event = simulation.next_event()
    if event is None or event.kind != expected_kind:
        raise AssertionError(f"expected {expected_kind}, got {event}")
    processed = simulation.advance_to(event.time)
    if not processed or processed[0].kind != expected_kind:
        raise AssertionError(f"expected processed {expected_kind}, got {processed}")
    return processed[0]


def main() -> None:
    first_2d = make_2d_particle((10.0, 10.0), (20.0, 0.0))
    second_2d = make_2d_particle((20.0, 12.0), (-20.0, 0.0))
    passing_2d = Simulation(
        [first_2d, second_2d],
        (100.0, 100.0),
        interaction_radius_multiplier=INTERACTION_MULTIPLIER,
    )
    entry_2d = advance_to_next(passing_2d, "well_entry")
    normal_2d = tuple(
        (second_coordinate - first_coordinate) / 3.0
        for first_coordinate, second_coordinate in zip(
            first_2d.position, second_2d.position
        )
    )
    assert_2d_tangential_velocity_preserved(
        (20.0, 0.0), first_2d.velocity, normal_2d
    )
    assert_2d_tangential_velocity_preserved(
        (-20.0, 0.0), second_2d.velocity, normal_2d
    )
    print_2d_state("2D well entry", 7, entry_2d.time, first_2d, second_2d)
    exit_2d = advance_to_next(passing_2d, "well_exit")
    print_2d_state("2D well exit", 8, exit_2d.time, first_2d, second_2d)

    trapped_first_2d = make_2d_particle((10.0, 10.0), (-5.0, -2.5))
    trapped_second_2d = make_2d_particle((12.0, 11.0), (5.0, 2.5))
    trapped_2d = Simulation(
        [trapped_first_2d, trapped_second_2d],
        (100.0, 100.0),
        interaction_radius_multiplier=INTERACTION_MULTIPLIER,
    )
    reflected_exit_2d = advance_to_next(trapped_2d, "well_exit")
    if (0, 1) not in trapped_2d._inside_interaction_wells:
        raise AssertionError("the 2D low-energy pair should remain inside the well")
    print_2d_state(
        "2D trapped well reflection", 18, reflected_exit_2d.time,
        trapped_first_2d, trapped_second_2d,
    )
    print("2D energy well PASS: entry, exit, trapped reflection, and tangential velocity")

    first = make_particle((10.0, 10.0, 10.0), (20.0, 0.0, 0.0))
    second = make_particle((20.0, 12.0, 11.0), (-20.0, 0.0, 0.0))
    passing = Simulation(
        [first, second],
        ARENA,
        interaction_radius_multiplier=INTERACTION_MULTIPLIER,
    )

    entry = advance_to_next(passing, "well_entry")
    separation = tuple(
        second_coordinate - first_coordinate
        for first_coordinate, second_coordinate in zip(first.position, second.position)
    )
    distance = sum(component * component for component in separation) ** 0.5
    normal = tuple(component / distance for component in separation)
    assert_tangential_velocity_preserved(
        (20.0, 0.0, 0.0), first.velocity, normal
    )
    assert_tangential_velocity_preserved(
        (-20.0, 0.0, 0.0), second.velocity, normal
    )
    print_state("3D well entry", 7, entry.time, first, second)
    exit_event = advance_to_next(passing, "well_exit")
    print_state("3D well exit", 8, exit_event.time, first, second)

    trapped_first = make_particle((10.0, 10.0, 10.0), (-5.0, -2.5, -2.5))
    trapped_second = make_particle((12.0, 11.0, 11.0), (5.0, 2.5, 2.5))
    trapped = Simulation(
        [trapped_first, trapped_second],
        ARENA,
        interaction_radius_multiplier=INTERACTION_MULTIPLIER,
    )
    reflected_exit = advance_to_next(trapped, "well_exit")
    if (0, 1) not in trapped._inside_interaction_wells:
        raise AssertionError("the low-energy pair should remain inside the well")
    # Java reclassifies its reflected exit event as ENTER_WELL (type 7).
    print_state(
        "3D trapped well reflection", 7, reflected_exit.time,
        trapped_first, trapped_second,
    )
    print("3D energy well PASS: entry, exit, trapped reflection, and tangential velocity")


if __name__ == "__main__":
    main()
