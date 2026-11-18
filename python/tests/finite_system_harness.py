"""Print finite-system kinetic-energy and speed predictions for Java comparison.

Run from the repository root with:
    PYTHONPATH=python python python/tests/finite_system_harness.py

Compare with the finite-system output from ``ParticleMovementHarness``.
"""

from boltzmann3d.physics import (
    Particle,
    Simulation,
    average_speed_meters_per_second,
    average_kinetic_energy_joules,
    finite_system_corrected_average_speed_meters_per_second,
    finite_system_corrected_kinetic_energy_joules,
    finite_system_corrected_kinetic_energy_width_joules,
    finite_system_corrected_rms_speed_meters_per_second,
    finite_system_corrected_velocity_width_meters_per_second,
    kinetic_energy_width_joules,
    rms_speed_meters_per_second,
    real_gas_correction_factor,
    real_gas_b2,
    real_gas_b2_correction,
    real_gas_effective_volume,
    real_gas_x,
    predict_collision_rate_for_type,
    predict_average_collision_rate,
    predict_pressure,
    calculate_simulation_statistics,
)


def main() -> None:
    temperature = 300.0
    particle_mass_kg = 2.0e-26
    total_mass_kg = 1.0e-25
    total_particles = 6
    # Captured from FiniteSystemSpeedHarness using the same inputs.
    java_average_speed_outputs = {
        1: (363.101089925494, 378.479156506206, 373.886149783594),
        2: (570.357858310189, 582.351025762768, 572.957651363804),
        3: (726.202179850988, 736.353447703554, 723.479238257995),
    }
    java_corrected_velocity_width_outputs = {
        1: 252.687728412661,
        2: 273.975186454999,
        3: 281.205049839241,
    }
    java_real_gas_qx_outputs = {
        2: (1.0, 1.08387741769214, 1.58317918503777, 3.10710296770546),
        3: (1.0, 1.06548376262379, 1.40092972649603, 2.08017365697687),
    }
    java_real_gas_b2_outputs = {
        1: 1.75e-9,
        2: 4.81056375080937e-18,
        3: 1.12246487518885e-26,
    }
    java_real_gas_b2_correction_outputs = {
        "1D reflecting": 1.0,
        "2D reflecting": 0.963473940560410,
        "3D reflecting": 0.948829136283382,
        "2D periodic": 1.0,
        "3D divided": 0.936055552411030,
        "3D divided with hole": 0.948829136283382,
    }
    b2_correction_cases = {
        "1D reflecting": ((70e-9,), False, False),
        "2D reflecting": ((70e-9, 50e-9), False, False),
        "3D reflecting": ((70e-9, 50e-9, 40e-9), False, False),
        "2D periodic": ((70e-9, 50e-9), True, False),
        "3D divided": ((70e-9, 50e-9, 40e-9), False, True),
        "3D divided with hole": ((70e-9, 50e-9, 40e-9), False, False),
    }
    for label, (arena_sizes, periodic, divided) in b2_correction_cases.items():
        actual = real_gas_b2_correction(
            0.5e-9,
            1.25e-9,
            arena_sizes,
            periodic=periodic,
            divided=divided,
        )
        expected = java_real_gas_b2_correction_outputs[label]
        if abs(actual - expected) > abs(expected) * 1e-12:
            raise AssertionError(
                f"Real-gas B2 correction {label} differs from Java: "
                f"expected {expected}, got {actual}"
            )
        print(f"Real-gas B2 correction {label}: {actual:.15g}")
    java_effective_volume_outputs = {
        "1D reflecting": 6.75e-8,
        "1D periodic": 7.0e-8,
        "2D reflecting": 3.20625e-15,
        "2D periodic": 3.5e-15,
        "3D reflecting": 1.20234375e-22,
        "3D divided": 5.6109375e-23,
        "3D divided with hole": 1.20234375e-22,
    }
    effective_volume_cases = {
        "1D reflecting": ((70e-9,), False, False),
        "1D periodic": ((70e-9,), True, False),
        "2D reflecting": ((70e-9, 50e-9), False, False),
        "2D periodic": ((70e-9, 50e-9), True, False),
        "3D reflecting": ((70e-9, 50e-9, 40e-9), False, False),
        "3D divided": ((70e-9, 50e-9, 40e-9), False, True),
        "3D divided with hole": ((70e-9, 50e-9, 40e-9), False, False),
    }
    for label, (arena_sizes, periodic, divided) in effective_volume_cases.items():
        actual = real_gas_effective_volume(
            arena_sizes,
            1.25e-9,
            periodic=periodic,
            divided=divided,
        )
        expected = java_effective_volume_outputs[label]
        if abs(actual - expected) > abs(expected) * 1e-12:
            raise AssertionError(
                f"Effective volume {label} differs from Java: "
                f"expected {expected}, got {actual}"
            )
        print(f"Effective volume {label}: {actual:.15g}")
    java_real_gas_x_outputs = {
        "2D reflecting": (0.00380055226217230, 0.00701789749308668),
        "3D reflecting": (0.000207990969834389, 0.000494729293258400),
        "2D periodic": (0.00409529042342955, 0.00973332723924695),
        "3D divided": (0.000438489315678449, 0.00103029727129079),
        "3D divided with hole": (0.000207990969834389, 0.000494729293258400),
    }
    real_gas_x_cases = {
        "2D reflecting": ((70e-9, 50e-9), False, False),
        "3D reflecting": ((70e-9, 50e-9, 40e-9), False, False),
        "2D periodic": ((70e-9, 50e-9), True, False),
        "3D divided": ((70e-9, 50e-9, 40e-9), False, True),
        "3D divided with hole": ((70e-9, 50e-9, 40e-9), False, False),
    }
    for label, (arena_sizes, periodic, divided) in real_gas_x_cases.items():
        for target_type_index, expected in enumerate(java_real_gas_x_outputs[label]):
            actual = real_gas_x(
                target_type_index,
                (0.5e-9, 1.25e-9),
                (3, 2),
                arena_sizes,
                periodic=periodic,
                divided=divided,
            )
            if abs(actual - expected) > abs(expected) * 1e-12:
                raise AssertionError(
                    f"Real-gas X {label} target type {target_type_index} differs from Java: "
                    f"expected {expected}, got {actual}"
                )
            print(f"Real-gas X {label} target type {target_type_index}: {actual:.15g}")
    amu_kg = 1.660538782e-27
    collision_rate_cases = {
        "2D reflecting": (
            420.952743504076,
            (70e-9, 50e-9),
            False,
            False,
            ((2279147033.78879, 2285938332.91838), (2811820187.93843, 2827325564.30993)),
            (2492216295.44864, 2502493225.47500),
        ),
        "3D reflecting": (
            280.635162336051,
            (70e-9, 50e-9, 40e-9),
            False,
            False,
            ((143566343.079022, 143585007.675659), (235136881.726603, 235209603.933665)),
            (180194558.538054, 180234846.178861),
        ),
        "2D periodic": (
            420.952743504076,
            (70e-9, 50e-9),
            True,
            False,
            ((2131659846.46618, 2138505639.00870), (2675395695.60433, 2695895210.91919)),
            (2349154186.12144, 2361461467.77289),
        ),
        "3D divided": (
            280.635162336051,
            (70e-9, 50e-9, 40e-9),
            False,
            True,
            ((305734078.349386, 305817883.424622), (496273802.433146, 496593522.123534)),
            (381949967.982890, 382128138.904186),
        ),
        "3D divided with hole": (
            280.635162336051,
            (70e-9, 50e-9, 40e-9),
            False,
            False,
            ((143566343.079022, 143585007.675659), (235136881.726603, 235209603.933665)),
            (180194558.538054, 180234846.178861),
        ),
    }
    for label, (collision_temperature, arena_sizes, periodic, divided, per_type, averages) in collision_rate_cases.items():
        for target_type_index in range(2):
            for apply_correction, expected in enumerate(per_type[target_type_index]):
                actual = predict_collision_rate_for_type(
                    target_type_index,
                    (0.5e-9, 1.25e-9),
                    (20.0 * amu_kg, 40.0 * amu_kg),
                    (3, 2),
                    collision_temperature,
                    arena_sizes,
                    periodic=periodic,
                    divided=divided,
                    real_gas_corrections=bool(apply_correction),
                )
                if abs(actual - expected) > abs(expected) * 1e-12:
                    raise AssertionError(
                        f"Collision rate {label} target {target_type_index} "
                        f"correction={bool(apply_correction)} differs from Java: "
                        f"expected {expected}, got {actual}"
                    )
                print(
                    f"Collision rate {label} target {target_type_index} "
                    f"corrected={bool(apply_correction)}: {actual:.15g} s^-1"
                )
        for apply_correction, expected in enumerate(averages):
            actual = predict_average_collision_rate(
                (0, 1),
                (0.5e-9, 1.25e-9),
                (20.0 * amu_kg, 40.0 * amu_kg),
                (3, 2),
                collision_temperature,
                arena_sizes,
                periodic=periodic,
                divided=divided,
                real_gas_corrections=bool(apply_correction),
            )
            if abs(actual - expected) > abs(expected) * 1e-12:
                raise AssertionError(
                    f"Average collision rate {label} correction={bool(apply_correction)} "
                    f"differs from Java: expected {expected}, got {actual}"
                )
            print(
                f"Average collision rate {label} corrected={bool(apply_correction)}: "
                f"{actual:.15g} s^-1"
            )
    pressure_cases = {
        "1D reflecting": (841.905487008152, (70e-9,), False, False),
        "1D periodic": (841.905487008152, (70e-9,), True, False),
        "2D reflecting": (420.952743504076, (70e-9, 50e-9), False, False),
        "2D periodic": (420.952743504076, (70e-9, 50e-9), True, False),
        "3D reflecting": (280.635162336051, (70e-9, 50e-9, 40e-9), False, False),
        "3D divided": (280.635162336051, (70e-9, 50e-9, 40e-9), False, True),
        "3D divided with hole": (280.635162336051, (70e-9, 50e-9, 40e-9), False, False),
    }
    java_pressure_outputs = {
        "1D reflecting": (8.49789412108857e-13, 9.37400925322581e-13),
        "1D periodic": (8.30269391000000e-13, 9.15974618458065e-13),
        "2D reflecting": (8.78230056727859e-6, 8.82754085412083e-6),
        "2D periodic": (8.30269391000000e-6, 8.35573341129598e-6),
        "3D reflecting": (152.603666331701, 152.653899226430),
        "3D divided": (322.428477813347, 322.651706783365),
        "3D divided with hole": (152.603666331701, 152.653899226430),
    }
    for label, (pressure_temperature, arena_sizes, periodic, divided) in pressure_cases.items():
        for apply_correction, expected in enumerate(java_pressure_outputs[label]):
            actual = predict_pressure(
                (0, 1),
                (0.5e-9, 1.25e-9),
                (3, 2),
                pressure_temperature,
                arena_sizes,
                periodic=periodic,
                divided=divided,
                real_gas_corrections=bool(apply_correction),
            )
            if abs(actual - expected) > abs(expected) * 1e-12:
                raise AssertionError(
                    f"Pressure prediction {label} corrected={bool(apply_correction)} "
                    f"differs from Java: expected {expected}, got {actual}"
                )
            print(
                f"Pressure prediction {label} corrected={bool(apply_correction)}: "
                f"{actual:.15g}"
            )
    simulation_statistics_cases = {
        "1D reflecting": (
            (70e-9,), "reflecting", 0.0, None, 841.905487008152,
            (57301173375.8868, 50467896668.6721), 54567862693.0009,
            9.37400925322581e-13,
        ),
        "1D periodic": (
            (70e-9,), "periodic", 0.0, None, 841.905487008152,
            (55906005904.0038, 49511539210.5595), 53348219226.6261,
            9.15974618458065e-13,
        ),
        "2D reflecting": (
            (70e-9, 50e-9), "reflecting", 0.0, None, 420.952743504076,
            (2285938332.91838, 2827325564.30993), 2502493225.47500,
            8.82754085412083e-6,
        ),
        "2D periodic": (
            (70e-9, 50e-9), "periodic", 0.0, None, 420.952743504076,
            (2138505639.00870, 2695895210.91919), 2361461467.77289,
            8.35573341129598e-6,
        ),
        "3D reflecting": (
            (70e-9, 50e-9, 40e-9), "reflecting", 0.0, None, 280.635162336051,
            (143585007.675659, 235209603.933665), 180234846.178861,
            152.653899226430,
        ),
        "3D divided": (
            (70e-9, 50e-9, 40e-9), "divided", 1.0e-9, None, 280.635162336051,
            (305817883.424622, 496593522.123534), 382128138.904186,
            322.651706783365,
        ),
        "3D divided with hole": (
            (70e-9, 50e-9, 40e-9), "divided", 1.0e-9, 10e-9, 280.635162336051,
            (143585007.675659, 235209603.933665), 180234846.178861,
            152.653899226430,
        ),
    }
    for label, case in simulation_statistics_cases.items():
        arena_sizes, boundary_mode, divider_half_width, hole_diameter = case[:4]
        expected_temperature, expected_type_rates, expected_average_rate, expected_pressure = case[4:]
        dimension = len(arena_sizes)
        x_positions = (5e-9, 15e-9, 25e-9, 45e-9, 55e-9)
        particles = []
        for particle_index, x_position in enumerate(x_positions):
            species = "red" if particle_index < 3 else "blue"
            mass_amu = 20.0 if species == "red" else 40.0
            radius = 0.5e-9 if species == "red" else 1.25e-9
            position = [x_position]
            velocity = [500.0]
            if dimension > 1:
                position.append(arena_sizes[1] / 2.0)
                velocity.append(0.0)
            if dimension > 2:
                position.append(arena_sizes[2] / 2.0)
                velocity.append(0.0)
            particles.append(
                Particle(
                    tuple(position),
                    tuple(velocity),
                    mass=mass_amu,
                    radius=radius,
                    species=species,
                )
            )
        simulation = Simulation(
            particles,
            arena_sizes,
            boundary_mode=boundary_mode,
            divider_half_width=divider_half_width,
            hole_diameter=hole_diameter,
        )
        statistics = calculate_simulation_statistics(
            simulation,
            real_gas_corrections=True,
        )
        values = (
            statistics.temperature_kelvin,
            statistics.particle_types[0].predicted_collision_rate_per_particle,
            statistics.particle_types[1].predicted_collision_rate_per_particle,
            statistics.average_collision_rate_per_particle,
            statistics.predicted_pressure,
        )
        expected_values = (
            expected_temperature,
            *expected_type_rates,
            expected_average_rate,
            expected_pressure,
        )
        for name, actual, expected in zip(
            ("temperature", "red rate", "blue rate", "average rate", "pressure"),
            values,
            expected_values,
        ):
            if abs(actual - expected) > abs(expected) * 1e-12:
                raise AssertionError(
                    f"Simulation statistics {label} {name} differs from Java: "
                    f"expected {expected}, got {actual}"
                )
        print(
            f"Simulation statistics {label}: T={statistics.temperature_kelvin:.15g} K "
            f"average collision rate={statistics.average_collision_rate_per_particle:.15g} s^-1 "
            f"pressure={statistics.predicted_pressure:.15g}"
        )
    for dimension, expected in java_real_gas_b2_outputs.items():
        actual = real_gas_b2(0.5e-9, 1.25e-9, dimension)
        if abs(actual - expected) > abs(expected) * 1e-12:
            raise AssertionError(
                f"{dimension}D real-gas B2 differs from Java: "
                f"expected {expected}, got {actual}"
            )
        print(f"Real-gas B2 {dimension}D: {actual:.15g}")
    for dimension, expected_values in java_real_gas_qx_outputs.items():
        for x, expected in zip((0.0, 0.1, 0.5, 1.0), expected_values):
            actual = real_gas_correction_factor(x, dimension)
            if abs(actual - expected) > abs(expected) * 1e-12:
                raise AssertionError(
                    f"{dimension}D real-gas Q({x}) differs from Java: "
                    f"expected {expected}, got {actual}"
                )
            print(f"Real-gas Q(x) {dimension}D x={x:.1f}: {actual:.15g}")
    try:
        real_gas_correction_factor(0.1, 1)
    except ValueError:
        pass
    else:
        raise AssertionError("Real-gas Q(x) should reject dimensions other than 2D and 3D")
    for dimension in range(1, 4):
        baseline = average_kinetic_energy_joules(temperature, dimension)
        corrected = finite_system_corrected_kinetic_energy_joules(
            particle_mass_kg,
            temperature,
            dimension,
            total_particles,
            total_mass_kg,
        )
        if abs(corrected - baseline * 0.96) > abs(baseline) * 1e-12:
            raise AssertionError(
                f"{dimension}D finite-system correction: expected {baseline * 0.96}, got {corrected}"
            )
        print(
            f"Finite-system KE {dimension}D: baseline={baseline:.15g} J "
            f"corrected={corrected:.15g} J"
        )
        width = kinetic_energy_width_joules(temperature, dimension)
        corrected_width = finite_system_corrected_kinetic_energy_width_joules(
            temperature,
            dimension,
            total_particles,
        )
        if not 0.0 < corrected_width < width:
            raise AssertionError(
                f"{dimension}D finite-system KE width should be positive and reduced"
            )
        print(
            f"Finite-system KE width {dimension}D: baseline={width:.15g} J "
            f"corrected={corrected_width:.15g} J"
        )
        average_speed = average_speed_meters_per_second(
            temperature,
            particle_mass_kg,
            dimension,
        )
        finite_nonperiodic_speed = finite_system_corrected_average_speed_meters_per_second(
            temperature,
            particle_mass_kg,
            dimension,
            False,
            total_particles,
            total_mass_kg,
        )
        finite_periodic_speed = finite_system_corrected_average_speed_meters_per_second(
            temperature,
            particle_mass_kg,
            dimension,
            True,
            total_particles,
            total_mass_kg,
        )
        if not (
            average_speed > 0.0
            and finite_nonperiodic_speed > 0.0
            and finite_periodic_speed > 0.0
        ):
            raise AssertionError(f"{dimension}D average-speed predictions must be positive")
        for label, actual, expected in zip(
            ("baseline", "nonperiodic", "periodic"),
            (average_speed, finite_nonperiodic_speed, finite_periodic_speed),
            java_average_speed_outputs[dimension],
        ):
            if abs(actual - expected) > abs(expected) * 1e-12:
                raise AssertionError(
                    f"{dimension}D {label} speed differs from Java: "
                    f"expected {expected}, got {actual}"
                )
        print(
            f"Finite-system average speed {dimension}D: baseline={average_speed:.15g} m/s "
            f"nonperiodic={finite_nonperiodic_speed:.15g} m/s "
            f"periodic={finite_periodic_speed:.15g} m/s"
        )
        rms_speed = rms_speed_meters_per_second(
            temperature,
            particle_mass_kg,
            dimension,
        )
        corrected_rms_speed = finite_system_corrected_rms_speed_meters_per_second(
            temperature,
            particle_mass_kg,
            dimension,
            False,
        )
        if corrected_rms_speed != rms_speed:
            raise AssertionError(
                f"{dimension}D nonperiodic corrected RMS speed should match Java's "
                "ordinary RMS speed"
            )
        try:
            finite_system_corrected_rms_speed_meters_per_second(
                temperature,
                particle_mass_kg,
                dimension,
                True,
            )
        except NotImplementedError:
            pass
        else:
            raise AssertionError(
                f"{dimension}D periodic corrected RMS speed should remain unsupported"
            )
        print(
            f"Finite-system RMS speed {dimension}D: "
            f"nonperiodic={corrected_rms_speed:.15g} m/s periodic=unsupported"
        )
        corrected_velocity_width = (
            finite_system_corrected_velocity_width_meters_per_second(
                temperature,
                particle_mass_kg,
                dimension,
                False,
                total_particles,
                total_mass_kg,
            )
        )
        expected_width = java_corrected_velocity_width_outputs[dimension]
        if abs(corrected_velocity_width - expected_width) > abs(expected_width) * 1e-12:
            raise AssertionError(
                f"{dimension}D corrected velocity width differs from Java: "
                f"expected {expected_width}, got {corrected_velocity_width}"
            )
        try:
            finite_system_corrected_velocity_width_meters_per_second(
                temperature,
                particle_mass_kg,
                dimension,
                True,
                total_particles,
                total_mass_kg,
            )
        except NotImplementedError:
            pass
        else:
            raise AssertionError(
                f"{dimension}D periodic corrected velocity width should remain unsupported"
            )
        print(
            f"Finite-system corrected velocity width {dimension}D: "
            f"nonperiodic={corrected_velocity_width:.15g} m/s periodic=unsupported"
        )
    print("Finite-system average-speed comparison PASS in 1D/2D/3D")
    print("Finite-system RMS-speed behavior matches Java in 1D/2D/3D")
    print("Finite-system corrected velocity width matches Java in 1D/2D/3D")
    print("Finite-system kinetic-energy correction PASS in 1D/2D/3D")
    print("Real-gas Q(x) comparison PASS in 2D/3D")
    print("Real-gas B2 comparison PASS in 1D/2D/3D")
    print("Real-gas B2 correction comparison PASS for arena cases")
    print("Effective-volume comparison PASS for arena cases")
    print("Real-gas X comparison PASS for multi-type arena cases")
    print("Collision-rate prediction comparison PASS with real-gas option")
    print("Pressure prediction comparison PASS with real-gas option")
    print("Simulation-level statistics comparison PASS with real-gas corrections")


if __name__ == "__main__":
    main()
