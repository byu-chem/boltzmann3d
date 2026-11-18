"""Wall thermostat behavior ported from the Java ``WallController``."""

from dataclasses import dataclass
import math


_ATOMIC_MASS_UNIT_KILOGRAMS = 1.660538782e-27
_MINIMUM_GAMMA_SQUARED = 0.5
_MAXIMUM_GAMMA_SQUARED = 1.5


@dataclass
class WallThermostat:
    """Adjust normal speed at reflecting walls to offset accumulated energy.

    ``delta_ke_joules`` is the accumulated change in particle kinetic energy,
    matching Java's ``Thermostat.deltaKE``. Java's isothermal piston updates
    this value as piston collisions add or remove energy. Selecting
    ``PistonMode.ISOTHERMAL`` enables the same accumulator for piston motion.
    """

    enabled: bool = False
    delta_ke_joules: float = 0.0
    damp_factor: float = 0.1

    def __post_init__(self) -> None:
        self.delta_ke_joules = float(self.delta_ke_joules)
        self.damp_factor = float(self.damp_factor)
        if not isinstance(self.enabled, bool):
            raise ValueError("Thermostat enabled setting must be a boolean")
        if not math.isfinite(self.delta_ke_joules):
            raise ValueError("Thermostat energy change must be finite")
        if not math.isfinite(self.damp_factor) or self.damp_factor < 0.0:
            raise ValueError("Thermostat damp factor must be finite and nonnegative")

    def reset(self, enabled: bool = False) -> None:
        """Reset the accumulated energy and restore Java's default damp factor."""
        if not isinstance(enabled, bool):
            raise ValueError("Thermostat enabled setting must be a boolean")
        self.enabled = enabled
        self.delta_ke_joules = 0.0
        self.damp_factor = 0.1

    @property
    def active(self) -> bool:
        """Whether the accumulated energy is large enough to adjust a collision."""
        epsilon = _ATOMIC_MASS_UNIT_KILOGRAMS
        return self.enabled and abs(self.delta_ke_joules) > epsilon

    def velocity_multiplier(self, mass_amu: float, normal_velocity: float) -> float:
        """Return Java's clipped wall-collision velocity adjustment factor."""
        if not self.active:
            return 1.0
        mass_amu = float(mass_amu)
        normal_velocity = float(normal_velocity)
        if not math.isfinite(mass_amu) or mass_amu <= 0.0:
            raise ValueError("Particle mass must be finite and positive")
        if not math.isfinite(normal_velocity) or normal_velocity == 0.0:
            raise ValueError("A thermostat wall collision needs finite nonzero normal speed")

        normal_ke = (
            0.5
            * mass_amu
            * normal_velocity
            * normal_velocity
            * _ATOMIC_MASS_UNIT_KILOGRAMS
        )
        if self.delta_ke_joules < normal_ke:
            gamma_squared = 1.0 - self.delta_ke_joules / normal_ke
        else:
            gamma_squared = (
                1.0 - self.damp_factor * self.delta_ke_joules / normal_ke
            )
        gamma_squared = min(
            _MAXIMUM_GAMMA_SQUARED,
            max(_MINIMUM_GAMMA_SQUARED, gamma_squared),
        )
        return math.sqrt(gamma_squared)

    def record_energy_change(self, delta_ke_joules: float) -> None:
        """Accumulate a collision's kinetic-energy change while enabled."""
        delta_ke_joules = float(delta_ke_joules)
        if not math.isfinite(delta_ke_joules):
            raise ValueError("Kinetic-energy change must be finite")
        if self.enabled:
            self.delta_ke_joules += delta_ke_joules
