"""Thermodynamic prediction formulas ported from Java's ``Formulas`` class."""

from math import exp, inf, isfinite, log, pi, sqrt

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


def average_speed_meters_per_second(
    temperature_kelvin: float,
    mass_kg: float,
    dimension: int,
) -> float:
    """Return Java's Maxwell-Boltzmann average-speed prediction."""
    if dimension == 1:
        return sqrt(
            (2.0 * BOLTZMANN_CONSTANT_JOULES_PER_KELVIN * temperature_kelvin)
            / (pi * mass_kg)
        )
    if dimension == 2:
        return sqrt(
            (pi * BOLTZMANN_CONSTANT_JOULES_PER_KELVIN * temperature_kelvin)
            / (2.0 * mass_kg)
        )
    if dimension == 3:
        return sqrt(
            (8.0 * BOLTZMANN_CONSTANT_JOULES_PER_KELVIN * temperature_kelvin)
            / (pi * mass_kg)
        )
    return 0.0


def rms_speed_meters_per_second(
    temperature_kelvin: float,
    mass_kg: float,
    dimension: int,
) -> float:
    """Return Java's uncorrected RMS-speed prediction."""
    return sqrt(
        dimension
        * BOLTZMANN_CONSTANT_JOULES_PER_KELVIN
        * temperature_kelvin
        / mass_kg
    )


def finite_system_corrected_rms_speed_meters_per_second(
    temperature_kelvin: float,
    mass_kg: float,
    dimension: int,
    periodic: bool,
) -> float:
    """Match Java's finite-correction RMS-speed behavior, including its gap.

    Java returns the ordinary RMS speed for nonperiodic arenas and throws an
    ``UnsupportedOperationException`` for periodic arenas. Preserve that
    behavior until a separate, verified periodic correction is implemented.
    """
    if periodic:
        raise NotImplementedError(
            "Java's finite-system corrected RMS speed is unsupported for periodic arenas"
        )
    return rms_speed_meters_per_second(temperature_kelvin, mass_kg, dimension)


def finite_system_corrected_velocity_width_meters_per_second(
    temperature_kelvin: float,
    mass_kg: float,
    dimension: int,
    periodic: bool,
    total_particles: int,
    total_mass_kg: float,
) -> float:
    """Return Java's finite-system corrected width of the speed distribution.

    Java calculates this as ``sqrt(corrected_rms_speed**2 -
    corrected_average_speed**2)``. Its corrected RMS speed is unsupported for
    periodic arenas, so this function preserves that behavior.
    """
    corrected_rms_speed = finite_system_corrected_rms_speed_meters_per_second(
        temperature_kelvin,
        mass_kg,
        dimension,
        periodic,
    )
    corrected_average_speed = finite_system_corrected_average_speed_meters_per_second(
        temperature_kelvin,
        mass_kg,
        dimension,
        periodic,
        total_particles,
        total_mass_kg,
    )
    return sqrt(corrected_rms_speed**2 - corrected_average_speed**2)


def _java_gamma(value: float) -> float:
    """Reproduce the recursive ``Formulas.gamma`` used by Java's gammaFrac."""
    if value <= 0.0:
        return inf
    if value == 1.0 or value == 2.0:
        return 1.0
    if value == 0.5:
        return sqrt(pi)
    return (value - 1.0) * _java_gamma(value - 1.0)


def _java_log_gamma(value: float) -> float:
    if value > 25.0:
        return (
            (value - 0.5) * log(value)
            - value
            + 0.5 * log(2.0 * pi)
            + 1.0 / (12.0 * value)
            - 1.0 / (360.0 * value**3)
            + 1.0 / (1260.0 * value**5)
        )
    return log(_java_gamma(value))


def _java_gamma_fraction(numerator: float, denominator: float) -> float:
    if numerator == 0.0 or denominator == 0.0:
        return 0.0
    try:
        value = exp(_java_log_gamma(numerator) - _java_log_gamma(denominator))
    except OverflowError:
        return 0.0
    return value if isfinite(value) else 0.0


def finite_system_corrected_average_speed_meters_per_second(
    temperature_kelvin: float,
    mass_kg: float,
    dimension: int,
    periodic: bool,
    total_particles: int,
    total_mass_kg: float,
) -> float:
    """Return Java's finite-system prediction for average particle speed."""
    effective_particles = total_particles - (1 if periodic else 0)
    if dimension == 1:
        value = sqrt(
            effective_particles
            * BOLTZMANN_CONSTANT_JOULES_PER_KELVIN
            * temperature_kelvin
            / (pi * mass_kg)
        ) * _java_gamma_fraction(
            effective_particles / 2.0,
            (effective_particles + 1.0) / 2.0,
        )
    elif dimension == 2:
        value = sqrt(
            pi
            * effective_particles
            * BOLTZMANN_CONSTANT_JOULES_PER_KELVIN
            * temperature_kelvin
            / (2.0 * mass_kg)
        ) * _java_gamma_fraction(
            effective_particles,
            effective_particles + 0.5,
        )
    elif dimension == 3:
        value = sqrt(
            12.0
            * effective_particles
            * BOLTZMANN_CONSTANT_JOULES_PER_KELVIN
            * temperature_kelvin
            / (pi * mass_kg)
        ) * _java_gamma_fraction(
            3.0 * effective_particles / 2.0,
            (3.0 * effective_particles + 1.0) / 2.0,
        )
    else:
        return 0.0

    if periodic:
        value *= sqrt(
            ((total_mass_kg - mass_kg) / total_mass_kg)
            * (total_particles / float(total_particles - 1))
        )
    return value
