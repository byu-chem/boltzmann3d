"""Basic particle state and straight-line movement."""

from dataclasses import dataclass


@dataclass
class Particle:
    """A particle with position and velocity components for each dimension.

    Position, velocity, and time must use consistent units. The particle moves
    in a straight line until another part of the simulation changes its
    velocity, such as when a collision is later implemented.
    """

    position: tuple[float, ...]
    velocity: tuple[float, ...]
    time: float = 0.0
    time_since_collision: float = 0.0
    mass: float = 1.0

    def __post_init__(self) -> None:
        self.position = tuple(float(component) for component in self.position)
        self.velocity = tuple(float(component) for component in self.velocity)
        self.time = float(self.time)
        self.time_since_collision = float(self.time_since_collision)
        self.mass = float(self.mass)

        if not self.position:
            raise ValueError("A particle must have at least one position component")
        if len(self.position) != len(self.velocity):
            raise ValueError("Position and velocity must have the same dimension")
        if self.mass <= 0.0:
            raise ValueError("Particle mass must be positive")

    def move_to_time(self, target_time: float) -> None:
        """Advance the particle along its current velocity to ``target_time``."""
        target_time = float(target_time)
        elapsed_time = target_time - self.time

        self.position = tuple(
            coordinate + speed * elapsed_time
            for coordinate, speed in zip(self.position, self.velocity)
        )
        self.time = target_time
        self.time_since_collision += elapsed_time

    def collide_1d(self, other: "Particle", collision_time: float) -> None:
        """Resolve a 1D elastic particle collision at ``collision_time``.

        The caller is responsible for predicting that a collision occurs at
        that time. This method advances both particles to the event and applies
        the ordinary particle-collision rule, without attractive wells or
        reactions.
        """
        if len(self.position) != 1 or len(other.position) != 1:
            raise ValueError("collide_1d requires two one-dimensional particles")

        self.move_to_time(collision_time)
        other.move_to_time(collision_time)

        first_velocity = self.velocity[0]
        second_velocity = other.velocity[0]
        total_mass = self.mass + other.mass

        new_first_velocity = (
            2.0 * other.mass * second_velocity
            + (self.mass - other.mass) * first_velocity
        ) / total_mass
        new_second_velocity = (
            2.0 * self.mass * first_velocity
            + (other.mass - self.mass) * second_velocity
        ) / total_mass

        self.velocity = (new_first_velocity,)
        other.velocity = (new_second_velocity,)
        self.time_since_collision = 0.0
        other.time_since_collision = 0.0
