"""Print finite-system kinetic-energy and speed predictions for Java comparison.

Run from the repository root with:
    PYTHONPATH=python python python/tests/finite_system_harness.py

Compare with the finite-system output from ``ParticleMovementHarness``.
"""

from boltzmann3d.physics import (
    average_speed_meters_per_second,
    average_kinetic_energy_joules,
    finite_system_corrected_average_speed_meters_per_second,
    finite_system_corrected_kinetic_energy_joules,
    finite_system_corrected_kinetic_energy_width_joules,
    finite_system_corrected_rms_speed_meters_per_second,
    finite_system_corrected_velocity_width_meters_per_second,
    kinetic_energy_width_joules,
    rms_speed_meters_per_second,
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


if __name__ == "__main__":
    main()
