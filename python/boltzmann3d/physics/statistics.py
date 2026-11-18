"""Simulation-level thermodynamic and real-gas prediction summaries."""

from dataclasses import dataclass
from typing import TYPE_CHECKING

from .particle import Particle
from .thermodynamics import (
    BOLTZMANN_CONSTANT_JOULES_PER_KELVIN,
    predict_average_collision_rate,
    predict_collision_rate_for_type,
    predict_pressure,
)

if TYPE_CHECKING:
    from .simulation import Simulation


ATOMIC_MASS_UNIT_KILOGRAMS = 1.660538782e-27


@dataclass(frozen=True)
class ParticleTypeStatistics:
    """Count and predicted collision rate for one simulated particle type."""

    species: str
    mass_amu: float
    radius: float
    count: int
    predicted_collision_rate_per_particle: float


@dataclass(frozen=True)
class SimulationStatistics:
    """Thermodynamic predictions derived from a simulation's current particles."""

    temperature_kelvin: float
    particle_types: tuple[ParticleTypeStatistics, ...]
    average_collision_rate_per_particle: float
    predicted_pressure: float
    real_gas_corrections: bool


def calculate_simulation_statistics(
    simulation: "Simulation",
    *,
    real_gas_corrections: bool = False,
) -> SimulationStatistics:
    """Calculate temperature and Java-style pressure/collision predictions.

    Particle mass is interpreted as amu, particle velocity as m/s, and
    particle radius and arena dimensions as meters. Particle types are grouped
    by species, mass, and radius so distinct physical types remain separate.
    """
    dimension = len(simulation.arena_sizes)
    grouped: dict[tuple[str, float, float], list[Particle]] = {}
    total_kinetic_energy_joules = 0.0
    for particle in simulation.particles:
        key = (particle.species, particle.mass, particle.radius)
        grouped.setdefault(key, []).append(particle)
        mass_kg = particle.mass * ATOMIC_MASS_UNIT_KILOGRAMS
        speed_squared = sum(component * component for component in particle.velocity)
        total_kinetic_energy_joules += 0.5 * mass_kg * speed_squared

    total_particle_count = len(simulation.particles)
    if total_particle_count == 0:
        temperature_kelvin = 0.0
    else:
        temperature_kelvin = (
            2.0
            * total_kinetic_energy_joules
            / (
                total_particle_count
                * dimension
                * BOLTZMANN_CONSTANT_JOULES_PER_KELVIN
            )
        )

    type_keys = tuple(grouped)
    type_radii = tuple(key[2] for key in type_keys)
    type_masses_kg = tuple(
        key[1] * ATOMIC_MASS_UNIT_KILOGRAMS for key in type_keys
    )
    type_counts = tuple(len(grouped[key]) for key in type_keys)
    periodic = simulation.boundary_mode == "periodic"
    solid_divider = (
        simulation.boundary_mode == "divided"
        and simulation.hole_diameter is None
    )
    divider_radius = simulation.divider_half_width

    type_statistics = tuple(
        ParticleTypeStatistics(
            species=species,
            mass_amu=mass_amu,
            radius=radius,
            count=type_counts[index],
            predicted_collision_rate_per_particle=predict_collision_rate_for_type(
                index,
                type_radii,
                type_masses_kg,
                type_counts,
                temperature_kelvin,
                simulation.arena_sizes,
                periodic=periodic,
                divided=solid_divider,
                real_gas_corrections=real_gas_corrections,
                divider_radius=divider_radius,
            ),
        )
        for index, (species, mass_amu, radius) in enumerate(type_keys)
    )
    selected_type_indices = tuple(range(len(type_keys)))

    return SimulationStatistics(
        temperature_kelvin=temperature_kelvin,
        particle_types=type_statistics,
        average_collision_rate_per_particle=predict_average_collision_rate(
            selected_type_indices,
            type_radii,
            type_masses_kg,
            type_counts,
            temperature_kelvin,
            simulation.arena_sizes,
            periodic=periodic,
            divided=solid_divider,
            real_gas_corrections=real_gas_corrections,
            divider_radius=divider_radius,
        ),
        predicted_pressure=predict_pressure(
            selected_type_indices,
            type_radii,
            type_counts,
            temperature_kelvin,
            simulation.arena_sizes,
            periodic=periodic,
            divided=solid_divider,
            real_gas_corrections=real_gas_corrections,
            divider_radius=divider_radius,
        ),
        real_gas_corrections=real_gas_corrections,
    )
