"""Kinematics for the Java-style movable piston."""

from dataclasses import dataclass
from enum import Enum
import math


class PistonMode(Enum):
    """Supported moving-piston thermostat modes from the Java application."""

    ADIABATIC = "adiabatic"
    ISOTHERMAL = "isothermal"


@dataclass
class Piston:
    """A plane piston that moves at constant speed between target levels.

    Levels are fractions of the arena length: level 0 places the piston at
    the far edge, while increasing the level moves it toward the origin.
    Java uses the X axis in 1D and the Y axis in 2D/3D; ``Simulation`` applies
    that convention when predicting collisions.
    """

    arena_length: float
    current_level: float = 0.0
    target_level: float = 0.0
    speed: float = 200.0
    time: float = 0.0

    def __post_init__(self) -> None:
        self.arena_length = float(self.arena_length)
        self.current_level = float(self.current_level)
        self.target_level = float(self.target_level)
        self.speed = float(self.speed)
        self.time = float(self.time)
        if not math.isfinite(self.arena_length) or self.arena_length <= 0.0:
            raise ValueError("Piston arena length must be finite and positive")
        if not math.isfinite(self.speed) or self.speed <= 0.0:
            raise ValueError("Piston speed must be finite and positive")
        if not math.isfinite(self.time):
            raise ValueError("Piston time must be finite")
        self._validate_level(self.current_level)
        self._validate_level(self.target_level)

    @staticmethod
    def _validate_level(level: float) -> None:
        if not math.isfinite(level) or not 0.0 <= level <= 1.0:
            raise ValueError("Piston level must be between 0 and 1")

    @property
    def position(self) -> float:
        """Current position along the piston axis."""
        return (1.0 - self.current_level) * self.arena_length

    @property
    def stop_time(self) -> float:
        """Absolute time when the piston reaches its current target."""
        if self.current_level == self.target_level:
            return self.time
        duration = (
            abs(self.target_level - self.current_level)
            * self.arena_length
            / self.speed
        )
        return self.time + duration

    def is_moving(self, time: float) -> bool:
        time = float(time)
        if not math.isfinite(time) or time < self.time:
            raise ValueError("Piston query time must be finite and not in the past")
        return self.current_level != self.target_level and time < self.stop_time

    def position_at(self, time: float) -> float:
        """Return the piston position at an absolute time without changing state."""
        time = float(time)
        if not math.isfinite(time) or time < self.time:
            raise ValueError("Piston position time must be finite and not in the past")
        if not self.is_moving(time):
            return (1.0 - self.target_level) * self.arena_length
        level_direction = 1.0 if self.target_level > self.current_level else -1.0
        level = self.current_level + (
            level_direction * self.speed / self.arena_length * (time - self.time)
        )
        return (1.0 - level) * self.arena_length

    def velocity_at(self, time: float) -> float:
        """Return the signed piston velocity, or zero after it stops."""
        if not self.is_moving(float(time)):
            return 0.0
        return -math.copysign(self.speed, self.target_level - self.current_level)

    def move_to_time(self, time: float) -> None:
        """Advance the stored piston state, clamping at the target level."""
        time = float(time)
        old_stop_time = self.stop_time
        position = self.position_at(time)
        if self.current_level != self.target_level and time >= old_stop_time:
            self.current_level = self.target_level
        else:
            self.current_level = 1.0 - position / self.arena_length
        self.time = time

    def set_target_level(
        self, target_level: float, *, at_time: float | None = None, speed: float | None = None
    ) -> None:
        """Set a new target, first advancing the piston to ``at_time``."""
        target_level = float(target_level)
        self._validate_level(target_level)
        if speed is not None:
            speed = float(speed)
            if not math.isfinite(speed) or speed <= 0.0:
                raise ValueError("Piston speed must be finite and positive")
        update_time = self.time if at_time is None else float(at_time)
        self.move_to_time(update_time)
        self.target_level = target_level
        if speed is not None:
            self.speed = speed
