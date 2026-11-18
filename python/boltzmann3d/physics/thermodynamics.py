"""Thermodynamic prediction formulas ported from Java's ``Formulas`` class."""

from math import sqrt

BOLTZMANN_CONSTANT_JOULES_PER_KELVIN = 1.3806504e-23


def average_kinetic_energy_joules(temperature_kelvin: float, dimension: int) -> float:
    """Return Java's equipartition prediction for mean kinetic energy."""
    return (
        dimension
        * BOLTZMANN_CONSTANT_JOULES_PER_KELVIN
        * temperature_kelvin
        / 2.0
    )


def finite_system_corrected_kinetic_energy_joules(
    mass_kg: float,
    temperature_kelvin: float,
    dimension: int,
    total_particles: int,
    total_mass_kg: float,
) -> float:
    """Return Java's finite-system mean kinetic-energy prediction.

    Java uses this correction for periodic simulations when finite-system
    corrections are enabled. Masses are in kilograms and the result is joules.
    """
    return average_kinetic_energy_joules(temperature_kelvin, dimension) * (
        (total_mass_kg - mass_kg) / total_mass_kg
    ) * (total_particles / float(total_particles - 1))


def kinetic_energy_width_joules(temperature_kelvin: float, dimension: int) -> float:
    """Return Java's uncorrected standard deviation of kinetic energy."""
    return (
        BOLTZMANN_CONSTANT_JOULES_PER_KELVIN
        * temperature_kelvin
        * sqrt(dimension / 2.0)
    )


def finite_system_corrected_kinetic_energy_width_joules(
    temperature_kelvin: float,
    dimension: int,
    total_particles: int,
) -> float:
    """Return Java's finite-system correction for kinetic-energy width.

    Java's ``correctedKEWidth`` accepts particle mass, periodicity, and total
    mass, but its formula does not use those arguments. Keep the behavior
    dimension and particle-count dependent, as in that method.
    """
    if dimension == 1:
        factor = sqrt((total_particles + 1) / float(2 * (total_particles + 2)))
        return BOLTZMANN_CONSTANT_JOULES_PER_KELVIN * temperature_kelvin * factor
    if dimension == 2:
        factor = sqrt((total_particles - 1) / float(total_particles + 1))
        return BOLTZMANN_CONSTANT_JOULES_PER_KELVIN * temperature_kelvin * factor
    factor = sqrt(
        2 * (total_particles - 1) / float(3 * (total_particles + 2))
    )
    return 1.5 * BOLTZMANN_CONSTANT_JOULES_PER_KELVIN * temperature_kelvin * factor
