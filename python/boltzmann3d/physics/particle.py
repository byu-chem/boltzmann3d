"""Basic particle state and straight-line movement."""

from dataclasses import dataclass
import math
import sys


NO_COLLISION_TIME = sys.float_info.max
_KILOJOULE_PER_MOLE_TO_AMU_M2_PER_S2 = (
    1_000.0 / 6.02214179e23 / 1.660538782e-27
)


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
    energy_well_depth: float = 0.0

    def __post_init__(self) -> None:
        self.position = tuple(float(component) for component in self.position)
        self.velocity = tuple(float(component) for component in self.velocity)
        self.time = float(self.time)
        self.time_since_collision = float(self.time_since_collision)
        self.mass = float(self.mass)
        self.radius = float(self.radius)
        self.energy_well_depth = float(self.energy_well_depth)

        if not self.position:
            raise ValueError("A particle must have at least one position component")
        if len(self.position) != len(self.velocity):
            raise ValueError("Position and velocity must have the same dimension")
        if self.mass <= 0.0:
            raise ValueError("Particle mass must be positive")
        if self.radius < 0.0:
            raise ValueError("Particle radius cannot be negative")
        if not math.isfinite(self.energy_well_depth) or self.energy_well_depth < 0.0:
            raise ValueError("Particle energy-well depth must be finite and nonnegative")

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

    def collide(
        self,
        other: "Particle",
        collision_time: float,
        image_offset: tuple[float, ...] | None = None,
    ) -> None:
        """Resolve an ordinary elastic hard-sphere collision in 1D, 2D, or 3D.

        The caller predicts the event time. Both particles are advanced to
        that time, and only their velocity components along the center-to-
        center collision direction change, matching Java's ordinary
        ``Particle2D/3D.collideWith`` behavior. For periodic collisions,
        ``image_offset`` shifts the second particle to its colliding image.
        Attractive wells and reactions are outside this method.
        """
        dimension = len(self.position)
        if dimension not in (1, 2, 3) or len(other.position) != dimension:
            raise ValueError("collide requires particles with matching 1D, 2D, or 3D states")

        self.move_to_time(collision_time)
        other.move_to_time(collision_time)

        if image_offset is None:
            image_offset = (0.0,) * dimension
        if len(image_offset) != dimension:
            raise ValueError("Image offset dimension must match the particles")
        image_offset = tuple(float(component) for component in image_offset)

        normal = tuple(
            other_coordinate + offset - self_coordinate
            for self_coordinate, other_coordinate, offset in zip(
                self.position, other.position, image_offset
            )
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

    def collide_with_energy_well(
        self, other: "Particle", collision_time: float, *, entering: bool
    ) -> bool:
        """Apply Java's 1D, 2D, or 3D square-well velocity change to a pair.

        The well depth is the sum of both particles' ``energy_well_depth``
        values. If the pair lacks enough relative kinetic energy to exit the
        well, they undergo an ordinary elastic collision and remain inside.
        In 2D and 3D, only the velocity component along the center-to-center
        axis changes; perpendicular components are preserved.
        """
        dimension = len(self.position)
        if dimension not in (1, 2, 3) or len(other.position) != dimension:
            raise ValueError("Energy-well collisions require matching 1D, 2D, or 3D particles")

        self.move_to_time(collision_time)
        other.move_to_time(collision_time)

        well_depth = self.energy_well_depth + other.energy_well_depth
        if well_depth == 0.0:
            return not entering

        if dimension == 1:
            normal = (1.0,)
            first_normal_speed = self.velocity[0]
            other_normal_speed = other.velocity[0]
        else:
            center_delta = tuple(
                other_coordinate - self_coordinate
                for self_coordinate, other_coordinate in zip(
                    self.position, other.position
                )
            )
            center_distance = math.sqrt(
                sum(component * component for component in center_delta)
            )
            if center_distance == 0.0:
                raise ValueError("Cannot resolve a well event at coincident particle centers")
            normal = tuple(component / center_distance for component in center_delta)
            first_normal_speed = sum(
                speed * component for speed, component in zip(self.velocity, normal)
            )
            other_normal_speed = sum(
                speed * component for speed, component in zip(other.velocity, normal)
            )
            # Particle2D and Particle3D convert the UI's kJ/mol well depth
            # into the amu·m²/s² units used by velocity and mass values.
            well_depth *= _KILOJOULE_PER_MOLE_TO_AMU_M2_PER_S2

        mass = self.mass
        other_mass = other.mass
        combined_mass = mass + other_mass
        reduced_mass = mass * other_mass / combined_mass
        momentum = mass * first_normal_speed + other_mass * other_normal_speed
        velocity_difference = other_normal_speed - first_normal_speed

        if entering:
            squared_relative_speed = (
                velocity_difference * velocity_difference + well_depth / reduced_mass
            )
        else:
            squared_relative_speed = (
                velocity_difference * velocity_difference - well_depth / reduced_mass
            )
            if squared_relative_speed < 0.0:
                self.collide(other, collision_time)
                return False

        relative_speed = math.sqrt(squared_relative_speed)
        first_root = relative_speed
        second_root = relative_speed
        if first_normal_speed < other_normal_speed:
            first_root = -first_root
        else:
            second_root = -second_root

        new_first_normal_speed = (momentum + other_mass * first_root) / combined_mass
        new_other_normal_speed = (momentum + mass * second_root) / combined_mass
        self.velocity = tuple(
            speed + (new_first_normal_speed - first_normal_speed) * component
            for speed, component in zip(self.velocity, normal)
        )
        other.velocity = tuple(
            speed + (new_other_normal_speed - other_normal_speed) * component
            for speed, component in zip(other.velocity, normal)
        )
        self.time_since_collision = 0.0
        other.time_since_collision = 0.0
        return not entering

    def predict_collision_time(
        self,
        other: "Particle",
        image_offset: tuple[float, ...] | None = None,
    ) -> float:
        """Predict the next ordinary hard-sphere collision in 1D, 2D, or 3D.

        Returns ``NO_COLLISION_TIME`` when the particles are stationary
        relative to one another, moving apart, or missing each other. The
        particle states are not changed. Supplying ``image_offset`` checks a
        periodic image of the other particle. This follows Java's
        ``Particle1D/2D/3D.PredCol`` for ordinary particle collisions.
        """
        dimension = len(self.position)
        if dimension not in (1, 2, 3) or len(other.position) != dimension:
            raise ValueError("predict_collision_time requires matching 1D, 2D, or 3D particles")
        if image_offset is None:
            image_offset = (0.0,) * dimension
        if len(image_offset) != dimension:
            raise ValueError("Image offset dimension must match the particles")
        image_offset = tuple(float(component) for component in image_offset)

        if self.time > other.time:
            current_time = self.time
            displacement = tuple(
                self_coordinate
                - (other_coordinate + offset + other_speed * (current_time - other.time))
                for self_coordinate, other_coordinate, offset, other_speed in zip(
                    self.position, other.position, image_offset, other.velocity
                )
            )
        else:
            current_time = other.time
            displacement = tuple(
                (self_coordinate + self_speed * (current_time - self.time))
                - (other_coordinate + offset)
                for self_coordinate, self_speed, other_coordinate, offset in zip(
                    self.position, self.velocity, other.position, image_offset
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

    def _validate_divider(
        self, arena_sizes: tuple[float, ...], divider_half_width: float
    ) -> tuple[tuple[float, ...], float, float]:
        sizes = self._validate_wall_arena(arena_sizes)
        divider_half_width = float(divider_half_width)
        if not math.isfinite(divider_half_width) or divider_half_width < 0.0:
            raise ValueError("Divider half-width must be finite and nonnegative")
        divider_x = sizes[0] / 2.0
        left_limit = divider_x - divider_half_width - self.radius
        right_limit = divider_x + divider_half_width + self.radius
        if left_limit <= self.radius or right_limit >= sizes[0] - self.radius:
            raise ValueError("Divider must leave room for particles on both sides")
        x = self.position[0]
        if left_limit < x < right_limit:
            raise ValueError("Particle overlaps the solid divider")
        return sizes, left_limit, right_limit

    def predict_divider_collision(
        self,
        arena_sizes: tuple[float, ...],
        divider_half_width: float = 0.0,
    ) -> tuple[float, str | None]:
        """Predict collision with a solid divider centered across the x axis."""
        _, left_limit, right_limit = self._validate_divider(
            arena_sizes, divider_half_width
        )
        x = self.position[0]
        x_speed = self.velocity[0]
        if x_speed > 0.0 and x <= left_limit:
            elapsed_time = (left_limit - x) / x_speed
            return self.time + elapsed_time, "left"
        if x_speed < 0.0 and x >= right_limit:
            elapsed_time = (right_limit - x) / x_speed
            return self.time + elapsed_time, "right"
        return NO_COLLISION_TIME, None

    def collide_with_divider(
        self,
        collision_time: float,
        arena_sizes: tuple[float, ...],
        side: str,
        divider_half_width: float = 0.0,
    ) -> None:
        """Advance to a solid divider and reflect the x velocity."""
        _, left_limit, right_limit = self._validate_divider(
            arena_sizes, divider_half_width
        )
        x_speed = self.velocity[0]
        if side == "left" and x_speed > 0.0:
            x_position = left_limit
        elif side == "right" and x_speed < 0.0:
            x_position = right_limit
        else:
            raise ValueError("Particle velocity does not point toward the divider side")

        self.move_to_time(collision_time)
        position = list(self.position)
        velocity = list(self.velocity)
        position[0] = x_position
        velocity[0] = -x_speed
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

    def predict_periodic_wrap(
        self, arena_sizes: tuple[float, ...]
    ) -> tuple[float, int | None, float | None]:
        """Predict the next crossing of a periodic edge.

        Returns ``(absolute_time, axis, wrapped_coordinate)``. With no moving
        axis, returns ``(NO_COLLISION_TIME, None, None)``. Positions use the
        canonical arena coordinates from zero up to each arena size.
        """
        if len(self.position) not in (1, 2, 3) or len(arena_sizes) != len(self.position):
            raise ValueError("Arena dimensions must match a 1D, 2D, or 3D particle")
        sizes = tuple(float(size) for size in arena_sizes)
        if any(size <= 0.0 for size in sizes):
            raise ValueError("Periodic arena sizes must be positive")
        for coordinate, size in zip(self.position, sizes):
            if coordinate < 0.0 or coordinate > size:
                raise ValueError("Particle must be inside the periodic arena")

        candidates: list[tuple[float, int, float]] = []
        for axis, (coordinate, speed, size) in enumerate(
            zip(self.position, self.velocity, sizes)
        ):
            if speed < 0.0:
                elapsed_time = -coordinate / speed
                wrapped_coordinate = size
            elif speed > 0.0:
                elapsed_time = (size - coordinate) / speed
                wrapped_coordinate = 0.0
            else:
                continue
            candidates.append((self.time + elapsed_time, axis, wrapped_coordinate))

        if not candidates:
            return NO_COLLISION_TIME, None, None
        time, axis, coordinate = min(candidates)
        return time, axis, coordinate

    def wrap_periodic(
        self,
        crossing_time: float,
        arena_sizes: tuple[float, ...],
        axis: int,
        wrapped_coordinate: float,
    ) -> None:
        """Advance to a periodic edge crossing and move to the opposite edge."""
        if len(self.position) not in (1, 2, 3) or len(arena_sizes) != len(self.position):
            raise ValueError("Arena dimensions must match a 1D, 2D, or 3D particle")
        if not isinstance(axis, int) or axis < 0 or axis >= len(self.position):
            raise ValueError("Periodic wrap axis must identify a particle coordinate")
        sizes = tuple(float(size) for size in arena_sizes)
        speed = self.velocity[axis]
        if speed == 0.0:
            raise ValueError("A stationary axis cannot cross a periodic edge")
        expected_coordinate = sizes[axis] if speed < 0.0 else 0.0
        if float(wrapped_coordinate) != expected_coordinate:
            raise ValueError("Wrapped coordinate must be the opposite arena edge")

        self.move_to_time(crossing_time)
        position = list(self.position)
        position[axis] = expected_coordinate
        self.position = tuple(position)
