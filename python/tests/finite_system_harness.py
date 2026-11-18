"""Print finite-system kinetic-energy predictions for Java comparison.

Run from the repository root with:
    PYTHONPATH=python python python/tests/finite_system_harness.py

Compare with the finite-system output from ``ParticleMovementHarness``.
"""

from boltzmann3d.physics import (
    average_kinetic_energy_joules,
    finite_system_corrected_kinetic_energy_joules,
    finite_system_corrected_kinetic_energy_width_joules,
    kinetic_energy_width_joules,
)


def main() -> None:
    temperature = 300.0
    particle_mass_kg = 2.0e-26
    total_mass_kg = 1.0e-25
    total_particles = 6
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
    print("Finite-system kinetic-energy correction PASS in 1D/2D/3D")


if __name__ == "__main__":
    main()
