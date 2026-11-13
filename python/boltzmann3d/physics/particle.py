"""Basic particle state and straight-line movement."""

from dataclasses import dataclass
import math
import sys


NO_COLLISION_TIME = sys.float_info.max


@dataclass
class Particle:
    """A particle with position and velocity components for each dimension.

    Position, velocity, and time must use consistent units. The particle moves
    in straight lines between events. Collision and wall methods advance the
    particle to an event and update its velocity.
    """

    position: tuple[float, ...]
    velocity: tuple[float, ...]
    time: float = 0.0
    time_since_collision: float = 0.0
    mass: float = 1.0
    radius: float = 1.0

    def __post_init__(self) -> None:
        self.position = tuple(float(component) for component in self.position)
        self.velocity = tuple(float(component) for component in self.velocity)
        self.time = float(self.time)
        self.time_since_collision = float(self.time_since_collision)
        self.mass = float(self.mass)
        self.radius = float(self.radius)

        if not self.position:
            raise ValueError("A particle must have at least one position component")
        if len(self.position) != len(self.velocity):
            raise ValueError("Position and velocity must have the same dimension")
        if self.mass <= 0.0:
            raise ValueError("Particle mass must be positive")
        if self.radius < 0.0:
            raise ValueError("Particle radius cannot be negative")

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

    def collide(self, other: "Particle", collision_time: float) -> None:
        """Resolve an ordinary elastic hard-sphere collision in 1D, 2D, or 3D.

        The caller predicts the event time. Both particles are advanced to
        that time, and only their velocity components along the center-to-
        center collision direction change, matching Java's ordinary
        ``Particle2D/3D.collideWith`` behavior. Attractive wells, periodic
        images, and reactions are outside this method.
        """
        dimension = len(self.position)
        if dimension not in (1, 2, 3) or len(other.position) != dimension:
            raise ValueError("collide requires particles with matching 1D, 2D, or 3D states")

        self.move_to_time(collision_time)
        other.move_to_time(collision_time)

        normal = tuple(
            other_coordinate - self_coordinate
            for self_coordinate, other_coordinate in zip(self.position, other.position)
        )
        distance_squared = sum(component * component for component in normal)
        if distance_squared == 0.0:
            raise ValueError("Cannot resolve a collision when particle centers coincide")
        distance = math.sqrt(distance_squared)
        normal = tuple(component / distance for component in normal)

        relative_normal_speed = sum(
            (other_speed - self_speed) * component
            for self_speed, other_speed, component in zip(
                self.velocity, other.velocity, normal
            )
        )
        inverse_mass_sum = (1.0 / self.mass) + (1.0 / other.mass)
        impulse = -2.0 * relative_normal_speed / inverse_mass_sum

        self.velocity = tuple(
            speed - (impulse / self.mass) * component
            for speed, component in zip(self.velocity, normal)
        )
        other.velocity = tuple(
            speed + (impulse / other.mass) * component
            for speed, component in zip(other.velocity, normal)
        )
        self.time_since_collision = 0.0
        other.time_since_collision = 0.0

    def collide_1d(self, other: "Particle", collision_time: float) -> None:
        """Compatibility wrapper for resolving a 1D elastic collision."""
        if len(self.position) != 1 or len(other.position) != 1:
            raise ValueError("collide_1d requires two one-dimensional particles")
        self.collide(other, collision_time)

    def predict_collision_time(self, other: "Particle") -> float:
        """Predict the next ordinary hard-sphere collision in 1D, 2D, or 3D.

        Returns ``NO_COLLISION_TIME`` when the particles are stationary
        relative to one another, moving apart, or missing each other. The
        particle states are not changed. This follows Java's ``Particle1D/2D/3D``
        ``PredCol`` for ordinary, non-periodic particle collisions.
        """
        dimension = len(self.position)
        if dimension not in (1, 2, 3) or len(other.position) != dimension:
            raise ValueError("predict_collision_time requires matching 1D, 2D, or 3D particles")

        if self.time > other.time:
            current_time = self.time
            displacement = tuple(
                self_coordinate
                - (other_coordinate + other_speed * (current_time - other.time))
                for self_coordinate, other_coordinate, other_speed in zip(
                    self.position, other.position, other.velocity
                )
            )
        else:
            current_time = other.time
            displacement = tuple(
                (self_coordinate + self_speed * (current_time - self.time))
                - other_coordinate
                for self_coordinate, self_speed, other_coordinate in zip(
                    self.position, self.velocity, other.position
                )
            )

        relative_velocity = tuple(
            self_speed - other_speed
            for self_speed, other_speed in zip(self.velocity, other.velocity)
        )
        contact_distance = self.radius + other.radius
        b = sum(
            coordinate * speed
            for coordinate, speed in zip(displacement, relative_velocity)
        )

        if b >= 0.0:
            return NO_COLLISION_TIME

        a = sum(speed * speed for speed in relative_velocity)
        c = (
            sum(coordinate * coordinate for coordinate in displacement)
            - contact_distance * contact_distance
        )
        radical = b * b - a * c
        if radical < 0.0 or a == 0.0:
            return NO_COLLISION_TIME

        elapsed_time = -(b + math.sqrt(radical)) / a
        return current_time + elapsed_time if elapsed_time >= 0.0 else current_time

    def predict_collision_time_1d(self, other: "Particle") -> float:
        """Compatibility wrapper for predicting a 1D hard-sphere collision."""
        if len(self.position) != 1 or len(other.position) != 1:
            raise ValueError(
                "predict_collision_time_1d requires two one-dimensional particles"
            )
        return self.predict_collision_time(other)

    def _validate_wall_arena(self, arena_sizes: tuple[float, ...]) -> tuple[float, ...]:
        if len(self.position) not in (1, 2, 3):
            raise ValueError("Wall handling supports one, two, or three dimensions")
        if len(arena_sizes) != len(self.position):
            raise ValueError("Arena size count must match the particle dimension")

        sizes = tuple(float(size) for size in arena_sizes)
        if any(size <= 2.0 * self.radius for size in sizes):
            raise ValueError("Arena must be wider than the particle diameter on every axis")
        for coordinate, size in zip(self.position, sizes):
            if coordinate < self.radius or coordinate > size - self.radius:
                raise ValueError("Particle must be inside the arena when predicting a wall collision")
        return sizes

    def predict_wall_collision(self, arena_sizes: tuple[float, ...]) -> tuple[float, int | None]:
        """Return the soonest reflecting-wall event as ``(time, axis)``.

        ``arena_sizes`` gives the arena length on each coordinate axis. The
        returned time is absolute, and axis is zero-based. A stationary
        particle returns ``(NO_COLLISION_TIME, None)``. The state is unchanged.
        """
        sizes = self._validate_wall_arena(arena_sizes)
        candidates: list[tuple[float, int]] = []

        for axis, (coordinate, speed, size) in enumerate(
            zip(self.position, self.velocity, sizes)
        ):
            if speed < 0.0:
                elapsed_time = (self.radius - coordinate) / speed
            elif speed > 0.0:
                elapsed_time = (size - self.radius - coordinate) / speed
            else:
                continue
            candidates.append((self.time + elapsed_time, axis))

        return min(candidates) if candidates else (NO_COLLISION_TIME, None)

    def collide_with_wall(
        self, collision_time: float, arena_sizes: tuple[float, ...], axis: int
    ) -> None:
        """Advance to a predicted reflecting wall and reverse its axis velocity."""
        sizes = self._validate_wall_arena(arena_sizes)
        if not isinstance(axis, int) or axis < 0 or axis >= len(self.position):
            raise ValueError("Wall axis must identify a particle coordinate")
        speed = self.velocity[axis]
        if speed == 0.0:
            raise ValueError("A stationary axis has no wall collision to resolve")

        self.move_to_time(collision_time)
        position = list(self.position)
        velocity = list(self.velocity)
        position[axis] = self.radius if speed < 0.0 else sizes[axis] - self.radius
        velocity[axis] = -speed
        self.position = tuple(position)
        self.velocity = tuple(velocity)

    def predict_wall_collision_time_1d(self, arena_length: float) -> float:
        """Compatibility wrapper for predicting a 1D reflecting-wall event."""
        if len(self.position) != 1:
            raise ValueError("predict_wall_collision_time_1d requires a 1D particle")
        return self.predict_wall_collision((arena_length,))[0]

    def collide_with_wall_1d(self, collision_time: float, arena_length: float) -> None:
        """Compatibility wrapper for resolving a 1D reflecting-wall event."""
        if len(self.position) != 1:
            raise ValueError("collide_with_wall_1d requires a 1D particle")
        self.collide_with_wall(collision_time, (arena_length,), axis=0)
