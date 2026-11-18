"""Thermodynamic prediction formulas ported from Java's ``Formulas`` class."""

from math import exp, inf, isfinite, log, pi, sqrt

BOLTZMANN_CONSTANT_JOULES_PER_KELVIN = 1.3806504e-23


def real_gas_correction_factor(x: float, dimension: int) -> float:
    """Return Java's ``Formulas.realGasQx`` correction for 2D or 3D.

    The correction is a rational polynomial fit. Computing ``x`` from arena
    and particle data is a separate step.
    """
    if dimension == 2:
        e1, e2, e3 = -0.25233539, 0.02704323, 0.00077137
        f1, f2, f3 = -1.03433982, 0.30366975, -0.01974721
    elif dimension == 3:
        e1, e2, e3 = 1.30542183, 0.11627913, 0.02452331
        f1, f2, f3 = 0.68042183, -0.59593402, 0.09148337
    else:
        raise ValueError("Java's real-gas correction factor supports only 2D and 3D")
    return (1.0 + e1 * x + e2 * x**2 + e3 * x**3) / (
        1.0 + f1 * x + f2 * x**2 + f3 * x**3
    )


def real_gas_b2(radius1: float, radius2: float, dimension: int) -> float:
    """Return Java's pairwise second-virial term ``Formulas.realGasB2``."""
    radii_sum = radius1 + radius2
    if dimension == 1:
        return radii_sum
    if dimension == 2:
        return pi * radii_sum * radii_sum / 2.0
    # Java's switch uses the 3D expression as its default branch.
    return (2.0 / 3.0) * pi * radii_sum * radii_sum * radii_sum


def real_gas_b2_correction(
    radius1: float,
    radius2: float,
    arena_sizes: tuple[float, ...],
    periodic: bool = False,
    divided: bool = False,
    divider_radius: float = 1.0e-9,
) -> float:
    """Return Java's finite-arena multiplier for the pairwise ``B2`` term.

    Radii, arena lengths, and divider radius must use the same length unit.
    Java returns 1 in 1D and for periodic arenas. In divided arenas it halves
    the usable X length and subtracts the divider radius, including for
    ``DIVIDED_ARENA`` (not the divided arena with a hole).
    """
    dimension = len(arena_sizes)
    if dimension not in (1, 2, 3):
        raise ValueError("Real-gas B2 correction requires a 1D, 2D, or 3D arena")
    if dimension == 1 or periodic:
        return 1.0

    sigma = radius1 + radius2
    lengths = list(arena_sizes)
    if divided:
        lengths[0] = lengths[0] / 2.0 - divider_radius

    if dimension == 2:
        lx, ly = lengths
        return (
            1.0
            - (2.0 / pi) * (sigma / lx + sigma / ly)
            + (6.0 / pi) * ((sigma / lx) * (sigma / ly))
        )

    lx, ly, lz = lengths
    return (
        1.0
        - (sigma / 2.0) * (1.0 / lx + 1.0 / ly + 1.0 / lz)
        + (11.0 * sigma * sigma / (20.0 * pi))
        * (1.0 / (lx * ly) + 1.0 / (lx * lz) + 1.0 / (ly * lz))
        + (15.0 * sigma**3 / (2.0 * pi * lx * ly * lz))
    )


def real_gas_effective_volume(
    arena_sizes: tuple[float, ...],
    particle_radius: float,
    periodic: bool = False,
    divided: bool = False,
    divider_radius: float = 1.0e-9,
) -> float:
    """Return Java's effective arena volume for a particle type.

    Arena dimensions, particle radius, and divider radius must use the same
    length unit. Reflecting walls remove one particle diameter from each axis;
    periodic walls do not. A solid divided arena uses half the X length minus
    the divider radius, matching Java's ``DIVIDED_ARENA`` branch.
    """
    dimension = len(arena_sizes)
    if dimension not in (1, 2, 3):
        raise ValueError("Effective volume requires a 1D, 2D, or 3D arena")
    loss_to_radius = 0.0 if periodic else 2.0 * particle_radius
    effective_x = arena_sizes[0]
    if divided:
        effective_x = effective_x / 2.0 - divider_radius
    effective_length = effective_x - loss_to_radius
    if dimension == 1:
        return effective_length
    effective_height = arena_sizes[1] - loss_to_radius
    if dimension == 2:
        return effective_length * effective_height
    effective_depth = arena_sizes[2] - loss_to_radius
    return effective_length * effective_height * effective_depth


def real_gas_x(
    target_type_index: int,
    particle_radii: tuple[float, ...],
    particle_counts: tuple[int, ...],
    arena_sizes: tuple[float, ...],
    periodic: bool = False,
    divided: bool = False,
    divider_radius: float = 1.0e-9,
) -> float:
    """Return Java's ``Formulas.realGasX`` for a particle type.

    Each index identifies one particle type across ``particle_radii`` and
    ``particle_counts``. Length values must use the same unit. For a
    nonperiodic arena Java excludes the target particle from collisions with
    its own type; periodic arenas retain the full same-type count.
    """
    if len(particle_radii) != len(particle_counts):
        raise ValueError("Particle radii and counts must have the same number of types")
    if not 0 <= target_type_index < len(particle_counts):
        raise ValueError("Target particle type index is out of range")
    if particle_counts[target_type_index] == 0:
        return 0.0

    target_radius = particle_radii[target_type_index]
    total_x = 0.0
    for collision_type_index, (collision_radius, collision_count) in enumerate(
        zip(particle_radii, particle_counts)
    ):
        available_collisions = collision_count - (
            1 if collision_type_index == target_type_index and not periodic else 0
        )
        b2 = real_gas_b2(target_radius, collision_radius, len(arena_sizes))
        b2_correction = real_gas_b2_correction(
            target_radius,
            collision_radius,
            arena_sizes,
            periodic=periodic,
            divided=divided,
            divider_radius=divider_radius,
        )
        effective_volume = real_gas_effective_volume(
            arena_sizes,
            collision_radius,
            periodic=periodic,
            divided=divided,
            divider_radius=divider_radius,
        )
        total_x += b2 * b2_correction * available_collisions / effective_volume
    return total_x


def predict_collision_rate_for_type(
    target_type_index: int,
    particle_radii: tuple[float, ...],
    particle_masses_kg: tuple[float, ...],
    particle_counts: tuple[int, ...],
    temperature_kelvin: float,
    arena_sizes: tuple[float, ...],
    periodic: bool = False,
    divided: bool = False,
    real_gas_corrections: bool = False,
    divider_radius: float = 1.0e-9,
) -> float:
    """Predict a particle type's collision rate, optionally using Java's real-gas factor.

    Returns collisions per second when lengths are in meters, masses are in
    kilograms, and temperature is in kelvin. As in Java, the pair-rate
    calculation subtracts one same-type particle even for periodic arenas;
    ``realGasX`` follows its own periodic self-count rule.
    """
    dimension = len(arena_sizes)
    type_count = len(particle_counts)
    if not (len(particle_radii) == len(particle_masses_kg) == type_count):
        raise ValueError("Radii, masses, and counts must describe the same particle types")
    if not 0 <= target_type_index < type_count:
        raise ValueError("Target particle type index is out of range")
    if dimension not in (1, 2, 3):
        raise ValueError("Collision-rate prediction requires a 1D, 2D, or 3D arena")

    target_radius = particle_radii[target_type_index]
    target_mass = particle_masses_kg[target_type_index]
    collision_rate = 0.0
    for collision_type_index, (radius, mass, count) in enumerate(
        zip(particle_radii, particle_masses_kg, particle_counts)
    ):
        collision_count = count - (1 if collision_type_index == target_type_index else 0)
        radii_sum = target_radius + radius
        if dimension == 1:
            collision_cross_section = 1.0
        elif dimension == 2:
            collision_cross_section = 2.0 * radii_sum
        else:
            collision_cross_section = pi * radii_sum * radii_sum

        reduced_mass = target_mass * mass / (target_mass + mass)
        if dimension == 3:
            relative_speed = sqrt(
                8.0
                * BOLTZMANN_CONSTANT_JOULES_PER_KELVIN
                * temperature_kelvin
                / (pi * reduced_mass)
            )
        else:
            relative_speed = sqrt(
                pi
                * BOLTZMANN_CONSTANT_JOULES_PER_KELVIN
                * temperature_kelvin
                / (2.0 * reduced_mass)
            )
        volume = real_gas_effective_volume(
            arena_sizes,
            radius,
            periodic=periodic,
            divided=divided,
            divider_radius=divider_radius,
        )
        collision_rate += collision_count * collision_cross_section * relative_speed / volume

    if real_gas_corrections and dimension > 1:
        x = real_gas_x(
            target_type_index,
            particle_radii,
            particle_counts,
            arena_sizes,
            periodic=periodic,
            divided=divided,
            divider_radius=divider_radius,
        )
        collision_rate *= real_gas_correction_factor(x, dimension)
    return collision_rate


def predict_average_collision_rate(
    selected_type_indices: tuple[int, ...],
    particle_radii: tuple[float, ...],
    particle_masses_kg: tuple[float, ...],
    particle_counts: tuple[int, ...],
    temperature_kelvin: float,
    arena_sizes: tuple[float, ...],
    periodic: bool = False,
    divided: bool = False,
    real_gas_corrections: bool = False,
    divider_radius: float = 1.0e-9,
) -> float:
    """Return Java's particle-count-weighted mean collision rate."""
    total_particles = sum(particle_counts[index] for index in selected_type_indices)
    if total_particles == 0:
        return 0.0
    weighted_rate = sum(
        predict_collision_rate_for_type(
            index,
            particle_radii,
            particle_masses_kg,
            particle_counts,
            temperature_kelvin,
            arena_sizes,
            periodic=periodic,
            divided=divided,
            real_gas_corrections=real_gas_corrections,
            divider_radius=divider_radius,
        )
        * particle_counts[index]
        for index in selected_type_indices
    )
    return weighted_rate / total_particles


def predict_pressure(
    selected_type_indices: tuple[int, ...],
    particle_radii: tuple[float, ...],
    particle_counts: tuple[int, ...],
    temperature_kelvin: float,
    arena_sizes: tuple[float, ...],
    periodic: bool = False,
    divided: bool = False,
    real_gas_corrections: bool = False,
    divider_radius: float = 1.0e-9,
) -> float:
    """Predict pressure, applying Java's dimension-specific real-gas correction.

    The returned unit is the ideal-gas pressure unit for the dimension: N in
    1D, N/m in 2D, or Pa in 3D. Lengths must be meters. Java's 1D correction
    uses an excluded-volume adjustment; 2D/3D use ``1 + x * Q(x)``.
    """
    if len(particle_radii) != len(particle_counts):
        raise ValueError("Particle radii and counts must have the same number of types")
    dimension = len(arena_sizes)
    if dimension not in (1, 2, 3):
        raise ValueError("Pressure prediction requires a 1D, 2D, or 3D arena")

    pressure_by_type: dict[int, float] = {}
    for type_index in selected_type_indices:
        volume = real_gas_effective_volume(
            arena_sizes,
            particle_radii[type_index],
            periodic=periodic,
            divided=divided,
            divider_radius=divider_radius,
        )
        pressure_by_type[type_index] = (
            particle_counts[type_index]
            * BOLTZMANN_CONSTANT_JOULES_PER_KELVIN
            * temperature_kelvin
            / volume
        )

    if real_gas_corrections:
        if dimension > 1:
            for type_index in selected_type_indices:
                x = real_gas_x(
                    type_index,
                    particle_radii,
                    particle_counts,
                    arena_sizes,
                    periodic=periodic,
                    divided=divided,
                    divider_radius=divider_radius,
                )
                pressure_by_type[type_index] *= 1.0 + x * real_gas_correction_factor(
                    x, dimension
                )
        else:
            arena_length = arena_sizes[0]
            if divided:
                arena_length = arena_length / 2.0 - divider_radius
            excluded_length = 2.0 * sum(
                particle_radii[index] * particle_counts[index]
                for index in selected_type_indices
            )
            selected_count = sum(particle_counts[index] for index in selected_type_indices)
            corrected_volume = arena_length - excluded_length
            for type_index in selected_type_indices:
                try:
                    pressure_by_type[type_index] *= real_gas_effective_volume(
                        arena_sizes,
                        particle_radii[type_index],
                        periodic=periodic,
                        divided=divided,
                        divider_radius=divider_radius,
                    )
                    pressure_by_type[type_index] /= corrected_volume
                except (ArithmeticError, ValueError):
                    pressure_by_type[type_index] = 0.0
            if periodic and selected_count > 0 and arena_length > 0.0:
                periodic_factor = 1.0 - excluded_length / (selected_count * arena_length)
                for type_index in selected_type_indices:
                    pressure_by_type[type_index] *= periodic_factor

    return sum(pressure_by_type.values())


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
