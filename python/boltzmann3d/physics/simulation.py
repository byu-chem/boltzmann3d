"""Small event-driven simulation for particle and arena-boundary physics."""

from dataclasses import dataclass
from itertools import product
import math
from typing import Literal

from .particle import NO_COLLISION_TIME, Particle
from .piston import Piston
from .reaction import ReactionRule
from .thermostat import WallThermostat


BoundaryMode = Literal["reflecting", "periodic", "divided"]
EventKind = Literal[
    "particle", "wall", "piston", "wrap", "divider", "edge", "well_entry", "well_exit",
    "wall_well_entry", "wall_well_exit",
]


@dataclass(frozen=True)
class SimulationEvent:
    """A predicted collision event in a :class:`Simulation`."""

    time: float
    kind: EventKind
    particle_indices: tuple[int, ...]
    axis: int | None = None
    image_offset: tuple[float, ...] | None = None
    wrapped_coordinate: float | None = None
    side: str | None = None
    obstacle_position: tuple[float, ...] | None = None


class Simulation:
    """Advance particles by repeatedly processing the earliest collision.

    ``boundary_mode`` may be ``"reflecting"``, ``"periodic"``, or
    ``"divided"``. A divided arena may include a ``hole_diameter`` in 2D/3D.
    Divider dimensions use the same units as particle coordinates and radii.
    In 1D, 2D, and 3D, ``interaction_radius_multiplier`` enables pairwise square
    wells; each pair's well radius is this multiplier times the sum of its radii.

    It recomputes all candidate events after each collision, which is simple
    and suitable for validating the physics before replacing it with an
    optimized event calendar.
    """

    def __init__(
        self,
        particles: list[Particle],
        arena_sizes: tuple[float, ...],
        boundary_mode: BoundaryMode = "reflecting",
        divider_half_width: float = 0.0,
        hole_diameter: float | None = None,
        hole_open: bool = True,
        interaction_radius_multiplier: float | None = None,
        wall_well_width: float | None = None,
        wall_well_depth: float | None = None,
        heat_reservoir_temperature: float | None = None,
        reaction_rule: ReactionRule | None = None,
        thermostat: WallThermostat | None = None,
        piston: Piston | None = None,
    ):
        self.particles = list(particles)
        if reaction_rule is not None and not isinstance(reaction_rule, ReactionRule):
            raise ValueError("reaction_rule must be a ReactionRule")
        self.reaction_rule = reaction_rule
        if thermostat is not None and not isinstance(thermostat, WallThermostat):
            raise ValueError("thermostat must be a WallThermostat")
        self.thermostat = thermostat
        if piston is not None and not isinstance(piston, Piston):
            raise ValueError("piston must be a Piston")
        self.piston = piston
        if boundary_mode not in ("reflecting", "periodic", "divided"):
            raise ValueError("Boundary mode must be 'reflecting', 'periodic', or 'divided'")
        self.boundary_mode = boundary_mode
        self.divider_half_width = float(divider_half_width)
        if not math.isfinite(self.divider_half_width) or self.divider_half_width < 0.0:
            raise ValueError("Divider half-width must be finite and nonnegative")
        self.hole_diameter = None if hole_diameter is None else float(hole_diameter)
        if not isinstance(hole_open, bool):
            raise ValueError("hole_open must be a boolean")
        self.hole_open = hole_open
        self.interaction_radius_multiplier = (
            None
            if interaction_radius_multiplier is None
            else float(interaction_radius_multiplier)
        )
        if self.interaction_radius_multiplier is not None:
            if (
                not math.isfinite(self.interaction_radius_multiplier)
                or self.interaction_radius_multiplier <= 1.0
            ):
                raise ValueError("Interaction-radius multiplier must be finite and greater than 1")
            if boundary_mode == "periodic":
                raise ValueError("Attractive interactions are not supported with periodic boundaries")
        if (wall_well_width is None) != (wall_well_depth is None):
            raise ValueError("Wall well width and depth must be supplied together")
        self.wall_well_width = (
            None if wall_well_width is None else float(wall_well_width)
        )
        self.wall_well_depth = (
            None if wall_well_depth is None else float(wall_well_depth)
        )
        if self.wall_well_width is not None:
            if (
                not math.isfinite(self.wall_well_width)
                or self.wall_well_width <= 0.0
            ):
                raise ValueError("Wall well width must be finite and positive")
            if (
                not math.isfinite(self.wall_well_depth)
                or self.wall_well_depth < 0.0
            ):
                raise ValueError("Wall well depth must be finite and nonnegative")
            if boundary_mode == "periodic":
                raise ValueError("Attractive wall wells require a nonperiodic arena")
        self.heat_reservoir_temperature = (
            None
            if heat_reservoir_temperature is None
            else float(heat_reservoir_temperature)
        )
        if self.heat_reservoir_temperature is not None:
            if self.wall_well_width is None:
                raise ValueError("A heat reservoir requires an attractive wall well")
            if (
                not math.isfinite(self.heat_reservoir_temperature)
                or self.heat_reservoir_temperature < 0.0
            ):
                raise ValueError("Heat-reservoir temperature must be finite and nonnegative")
        self._inside_interaction_wells: set[tuple[int, int]] = set()
        if self.hole_diameter is not None:
            if boundary_mode != "divided":
                raise ValueError("A divider hole requires boundary_mode='divided'")
            if not math.isfinite(self.hole_diameter) or self.hole_diameter <= 0.0:
                raise ValueError("Hole diameter must be finite and positive")
        if not self.particles:
            if not arena_sizes:
                raise ValueError("Arena dimensions are required, even for an empty simulation")
            self.arena_sizes = tuple(float(size) for size in arena_sizes)
            if len(self.arena_sizes) not in (1, 2, 3) or any(
                size <= 0 for size in self.arena_sizes
            ):
                raise ValueError("Arena must have one, two, or three positive dimensions")
            if self.wall_well_width is not None and len(self.arena_sizes) not in (2, 3):
                raise ValueError("Attractive wall wells require a 2D or 3D simulation")
            self.current_time = max(0.0, self.piston.time if self.piston else 0.0)
            if self.piston is not None and self.piston.time < self.current_time:
                self.piston.move_to_time(self.current_time)
            self._validate_piston(len(self.arena_sizes))
            self._validate_hole_size(len(self.arena_sizes))
            return

        dimension = len(self.particles[0].position)
        if dimension not in (1, 2, 3):
            raise ValueError("Simulation supports one, two, or three dimensions")
        if self.wall_well_width is not None and dimension not in (2, 3):
            raise ValueError("Attractive wall wells require a 2D or 3D simulation")
        if any(len(particle.position) != dimension for particle in self.particles):
            raise ValueError("All particles must have the same dimension")
        if len(arena_sizes) != dimension:
            raise ValueError("Arena size count must match the particle dimension")

        self.arena_sizes = tuple(float(size) for size in arena_sizes)
        if any(size <= 0.0 for size in self.arena_sizes):
            raise ValueError("Arena dimensions must be positive")
        self.current_time = max(particle.time for particle in self.particles)
        if self.piston is not None:
            self.current_time = max(self.current_time, self.piston.time)
            if self.piston.time < self.current_time:
                self.piston.move_to_time(self.current_time)
        for particle in self.particles:
            if particle.time < self.current_time:
                particle.move_to_time(self.current_time)
        self._validate_piston(dimension)
        self._validate_hole_size(dimension)
        if self.boundary_mode == "reflecting":
            for particle in self.particles:
                particle._validate_wall_arena(self.arena_sizes)
        elif self.boundary_mode == "periodic":
            for particle in self.particles:
                if any(size <= 2.0 * particle.radius for size in self.arena_sizes):
                    raise ValueError(
                        "Periodic arena must be wider than each particle diameter"
                    )
                particle.position = tuple(
                    coordinate % size
                    for coordinate, size in zip(particle.position, self.arena_sizes)
                )
        else:
            for particle in self.particles:
                if self.hole_diameter is None:
                    particle._validate_divider(self.arena_sizes, self.divider_half_width)
                elif not self.hole_open:
                    particle._validate_divider(self.arena_sizes, self.divider_half_width)
                else:
                    self._validate_hole_particle(particle)
        self._initialize_interaction_well_states()

    def _validate_piston(self, dimension: int) -> None:
        if self.piston is None:
            return
        if self.boundary_mode != "reflecting":
            raise ValueError("A movable piston requires reflecting arena boundaries")
        if self.hole_diameter is not None:
            raise ValueError("A movable piston cannot be combined with a divider hole")
        axis = 0 if dimension == 1 else 1
        if not 1 <= dimension <= 3:
            raise ValueError("A movable piston requires a 1D, 2D, or 3D simulation")
        if not math.isclose(
            self.piston.arena_length, self.arena_sizes[axis], rel_tol=0.0, abs_tol=1e-12
        ):
            raise ValueError("Piston arena length must match the size along its axis")
        if self.particles:
            for particle in self.particles:
                if (
                    particle.position[axis] + particle.radius
                    > self.piston.position + 1e-12
                ):
                    raise ValueError("Particle overlaps or starts beyond the piston")

    def _initialize_interaction_well_states(self) -> None:
        if self.interaction_radius_multiplier is None:
            return
        for first_index, first in enumerate(self.particles):
            for second_index in range(first_index + 1, len(self.particles)):
                second = self.particles[second_index]
                if first.energy_well_depth + second.energy_well_depth == 0.0:
                    continue
                well_radius = self.interaction_radius_multiplier * (
                    first.radius + second.radius
                )
                separation_squared = sum(
                    (first_coordinate - second_coordinate) ** 2
                    for first_coordinate, second_coordinate in zip(
                        first.position, second.position
                    )
                )
                if separation_squared < well_radius * well_radius:
                    self._inside_interaction_wells.add((first_index, second_index))

    def _predict_interaction_well_event(
        self,
        first_index: int,
        second_index: int,
        first: Particle,
        second: Particle,
    ) -> SimulationEvent | None:
        if self.interaction_radius_multiplier is None:
            return None
        if first.energy_well_depth + second.energy_well_depth == 0.0:
            return None

        current_time = max(first.time, second.time)
        if first.time > second.time:
            displacement = tuple(
                first_coordinate
                - (
                    second_coordinate
                    + second_speed * (current_time - second.time)
                )
                for first_coordinate, second_coordinate, second_speed in zip(
                    first.position, second.position, second.velocity
                )
            )
        else:
            displacement = tuple(
                first_coordinate + first_speed * (current_time - first.time)
                - second_coordinate
                for first_coordinate, first_speed, second_coordinate in zip(
                    first.position, first.velocity, second.position
                )
            )
        relative_velocity = tuple(
            first_speed - second_speed
            for first_speed, second_speed in zip(first.velocity, second.velocity)
        )
        a = sum(speed * speed for speed in relative_velocity)
        if a == 0.0:
            return None

        well_radius = self.interaction_radius_multiplier * (
            first.radius + second.radius
        )
        b = sum(
            coordinate * speed
            for coordinate, speed in zip(displacement, relative_velocity)
        )
        c = (
            sum(coordinate * coordinate for coordinate in displacement)
            - well_radius * well_radius
        )
        discriminant = b * b - a * c
        if discriminant < 0.0:
            return None
        root = math.sqrt(discriminant)
        pair = (first_index, second_index)
        if pair in self._inside_interaction_wells:
            elapsed_time = (-b + root) / a
            kind: EventKind = "well_exit"
        else:
            if b >= 0.0:
                return None
            elapsed_time = (-b - root) / a
            kind = "well_entry"
        if elapsed_time < 0.0:
            return None
        return SimulationEvent(current_time + elapsed_time, kind, pair)

    def set_hole_open(self, is_open: bool) -> None:
        """Change the hole state before predicting the next simulation events."""
        if self.hole_diameter is None:
            raise ValueError("This simulation has no divider hole to open or close")
        if not isinstance(is_open, bool):
            raise ValueError("Hole state must be a boolean")
        if is_open == self.hole_open:
            return
        if not is_open:
            divider_center = self.arena_sizes[0] / 2.0
            for particle in self.particles:
                overlap_distance = self.divider_half_width + particle.radius
                if abs(particle.position[0] - divider_center) < overlap_distance:
                    raise ValueError(
                        "Cannot close the hole while a particle overlaps the divider"
                    )
        self.hole_open = is_open

    def set_piston_target_level(
        self, target_level: float, *, speed: float | None = None
    ) -> None:
        """Start moving the configured piston toward a new level."""
        if self.piston is None:
            raise ValueError("This simulation has no movable piston")
        self.piston.set_target_level(
            target_level, at_time=self.current_time, speed=speed
        )

    def _validate_hole_size(self, dimension: int) -> None:
        if self.hole_diameter is None:
            return
        if dimension not in (2, 3):
            raise ValueError("A divider hole is supported only in 2D or 3D")
        transverse_sizes = self.arena_sizes[1:dimension]
        if any(self.hole_diameter >= size for size in transverse_sizes):
            raise ValueError("Hole diameter must be smaller than each transverse arena size")

    def _hole_center_radius(self, dimension: int) -> float:
        """Return the Java-style radius used to decide if the divider is crossed."""
        if self.hole_diameter is None:
            raise ValueError("This simulation has no divider hole")
        radius = 0.5 * self.hole_diameter
        # Java's 2D barrier check uses holeDiameter/2 directly. Its 3D check
        # uses ArenaInfo.holeRadius, which adds the divider edge radius.
        if dimension == 3:
            radius += self.divider_half_width
        return radius

    def _divider_edge_positions(self) -> tuple[tuple[float, ...], ...]:
        """Return the fixed point-particle locations lining the hole edge."""
        if self.hole_diameter is None or not self.hole_open:
            return ()
        dimension = len(self.arena_sizes)
        center = self.arena_sizes[0] / 2.0
        hole_radius = 0.5 * self.hole_diameter + self.divider_half_width
        if dimension == 2:
            middle_y = self.arena_sizes[1] / 2.0
            return (
                (center, middle_y + hole_radius),
                (center, middle_y - hole_radius),
            )

        edge_radius = self.divider_half_width
        if edge_radius == 0.0:
            # A zero-thickness divider has no finite edge-particle size. Use
            # a modest ring to preserve a usable opening boundary.
            count = 32
        else:
            count = max(1, round(4.0 * math.pi * hole_radius / edge_radius))
        middle_y = self.arena_sizes[1] / 2.0
        middle_z = self.arena_sizes[2] / 2.0
        return tuple(
            (
                center,
                middle_y + hole_radius * math.cos(2.0 * math.pi * index / count),
                middle_z + hole_radius * math.sin(2.0 * math.pi * index / count),
            )
            for index in range(count)
        )

    def _validate_hole_particle(self, particle: Particle) -> None:
        sizes = particle._validate_wall_arena(self.arena_sizes)
        divider_center = sizes[0] / 2.0
        half_span = self.divider_half_width + particle.radius
        x = particle.position[0]
        if abs(x - divider_center) >= half_span:
            return
        dimension = len(sizes)
        transverse = tuple(
            coordinate - 0.5 * size
            for coordinate, size in zip(particle.position[1:], sizes[1:])
        )
        radial_squared = sum(component * component for component in transverse)
        hole_radius = self._hole_center_radius(dimension)
        if radial_squared >= hole_radius * hole_radius:
            raise ValueError("Particle overlaps the solid part of the divider")
        contact_distance = particle.radius + self.divider_half_width
        for edge_position in self._divider_edge_positions():
            distance_squared = sum(
                (coordinate - edge_coordinate) ** 2
                for coordinate, edge_coordinate in zip(particle.position, edge_position)
            )
            if distance_squared < contact_distance * contact_distance:
                raise ValueError("Particle overlaps the divider hole edge")

    def _divider_event_for(self, index: int, particle: Particle) -> SimulationEvent | None:
        divider_center = self.arena_sizes[0] / 2.0
        left_limit = divider_center - self.divider_half_width - particle.radius
        right_limit = divider_center + self.divider_half_width + particle.radius
        x, x_speed = particle.position[0], particle.velocity[0]
        if x_speed > 0.0 and x <= left_limit:
            event_time = particle.time + (left_limit - x) / x_speed
            side = "left"
        elif x_speed < 0.0 and x >= right_limit:
            event_time = particle.time + (right_limit - x) / x_speed
            side = "right"
        else:
            return None
        if self.hole_diameter is not None and self.hole_open:
            dimension = len(self.arena_sizes)
            coordinates = tuple(
                coordinate + speed * (event_time - particle.time)
                for coordinate, speed in zip(particle.position, particle.velocity)
            )
            transverse = tuple(
                coordinate - 0.5 * size
                for coordinate, size in zip(coordinates[1:], self.arena_sizes[1:])
            )
            radial_squared = sum(component * component for component in transverse)
            hole_radius = self._hole_center_radius(dimension)
            crosses_hole = (
                radial_squared < hole_radius * hole_radius
                if dimension == 2
                else radial_squared <= hole_radius * hole_radius
            )
            if crosses_hole:
                return None
        return SimulationEvent(event_time, "divider", (index,), side=side)

    @staticmethod
    def _predict_fixed_sphere_collision(
        particle: Particle,
        center: tuple[float, ...],
        contact_distance: float,
    ) -> float:
        displacement = tuple(
            coordinate - obstacle_coordinate
            for coordinate, obstacle_coordinate in zip(particle.position, center)
        )
        a = sum(speed * speed for speed in particle.velocity)
        b = sum(
            coordinate * speed
            for coordinate, speed in zip(displacement, particle.velocity)
        )
        c = sum(coordinate * coordinate for coordinate in displacement) - (
            contact_distance * contact_distance
        )
        if a == 0.0 or b >= 0.0:
            return NO_COLLISION_TIME
        discriminant = b * b - a * c
        if discriminant < 0.0:
            return NO_COLLISION_TIME
        elapsed = (-b - math.sqrt(discriminant)) / a
        return particle.time + elapsed if elapsed >= 0.0 else NO_COLLISION_TIME

    def _collision_is_blocked_by_divider(
        self, first: Particle, second: Particle, collision_time: float
    ) -> bool:
        if self.hole_diameter is None or not self.hole_open:
            return True
        first_at_collision = tuple(
            coordinate + speed * (collision_time - first.time)
            for coordinate, speed in zip(first.position, first.velocity)
        )
        second_at_collision = tuple(
            coordinate + speed * (collision_time - second.time)
            for coordinate, speed in zip(second.position, second.velocity)
        )
        center_x = self.arena_sizes[0] / 2.0
        delta_x = second_at_collision[0] - first_at_collision[0]
        if delta_x == 0.0:
            return True
        fraction = (center_x - first_at_collision[0]) / delta_x
        if not 0.0 <= fraction <= 1.0:
            return True
        radial_squared = sum(
            (
                first_at_collision[axis]
                + fraction * (second_at_collision[axis] - first_at_collision[axis])
                - 0.5 * self.arena_sizes[axis]
            )
            ** 2
            for axis in range(1, len(self.arena_sizes))
        )
        hole_radius = self._hole_center_radius(len(self.arena_sizes))
        if len(self.arena_sizes) == 2:
            return radial_squared >= hole_radius * hole_radius
        return radial_squared > hole_radius * hole_radius

    def _reflect_from_fixed_sphere(
        self,
        particle: Particle,
        collision_time: float,
        center: tuple[float, ...],
    ) -> None:
        particle.move_to_time(collision_time)
        normal = tuple(
            coordinate - obstacle_coordinate
            for coordinate, obstacle_coordinate in zip(particle.position, center)
        )
        distance = math.sqrt(sum(component * component for component in normal))
        if distance == 0.0:
            raise ValueError("Cannot resolve a fixed-edge collision at coincident centers")
        normal = tuple(component / distance for component in normal)
        contact_distance = particle.radius + self.divider_half_width
        particle.position = tuple(
            obstacle_coordinate + component * contact_distance
            for obstacle_coordinate, component in zip(center, normal)
        )
        normal_speed = sum(
            speed * component for speed, component in zip(particle.velocity, normal)
        )
        particle.velocity = tuple(
            speed - 2.0 * normal_speed * component
            for speed, component in zip(particle.velocity, normal)
        )

    def _predict_piston_event(
        self, particle_index: int, particle: Particle
    ) -> SimulationEvent | None:
        if self.piston is None:
            return None
        axis = 0 if len(self.arena_sizes) == 1 else 1
        start_time = particle.time
        piston_position = self.piston.position_at(start_time)
        piston_velocity = self.piston.velocity_at(start_time)
        gap = piston_position - particle.position[axis] - particle.radius
        tolerance = 1e-12 * max(1.0, self.arena_sizes[axis])
        if gap < -tolerance:
            raise ValueError("Particle has passed through the movable piston")
        gap = max(0.0, gap)

        relative_closing_speed = particle.velocity[axis] - piston_velocity
        piston_is_moving = self.piston.is_moving(start_time)
        recompute_after_stop = False
        if relative_closing_speed > 0.0:
            collision_time = start_time + gap / relative_closing_speed
        elif piston_is_moving and particle.velocity[axis] > 0.0:
            # The piston can move away from a particle, stop, and then be
            # caught by that particle. Java predicts this second segment too.
            collision_time = self.piston.stop_time
            recompute_after_stop = True
        else:
            return None

        if piston_is_moving:
            stop_time = self.piston.stop_time
            if recompute_after_stop or collision_time > stop_time:
                gap_at_stop = (
                    self.piston.position_at(stop_time)
                    - (
                        particle.position[axis]
                        + particle.velocity[axis] * (stop_time - start_time)
                        + particle.radius
                    )
                )
                if particle.velocity[axis] <= 0.0 or gap_at_stop < -tolerance:
                    return None
                collision_time = stop_time + max(0.0, gap_at_stop) / particle.velocity[axis]

        return SimulationEvent(
            collision_time, "piston", (particle_index,), axis=axis
        )

    def next_event(self) -> SimulationEvent | None:
        """Return the earliest predicted event without changing particle state."""
        candidates: list[SimulationEvent] = []

        for index, particle in enumerate(self.particles):
            if self.boundary_mode in ("reflecting", "divided"):
                event_time, axis = particle.predict_wall_collision(self.arena_sizes)
                if event_time != NO_COLLISION_TIME:
                    candidates.append(
                        SimulationEvent(event_time, "wall", (index,), axis)
                    )
                piston_event = self._predict_piston_event(index, particle)
                if piston_event is not None:
                    candidates.append(piston_event)
                if (
                    self.wall_well_width is not None
                    and len(particle.position) >= 2
                    and particle.velocity[1] != 0.0
                ):
                    well_time = particle.time + (
                        self.wall_well_width - particle.position[1]
                    ) / particle.velocity[1]
                    if well_time > particle.time:
                        kind: EventKind | None = None
                        if particle.velocity[1] < 0.0:
                            kind = "wall_well_entry"
                        elif particle.position[1] < self.wall_well_width:
                            kind = "wall_well_exit"
                        if kind is not None:
                            candidates.append(
                                SimulationEvent(well_time, kind, (index,), axis=1)
                            )
            if self.boundary_mode == "divided":
                divider_event = self._divider_event_for(index, particle)
                if divider_event is not None:
                    candidates.append(divider_event)
                if self.hole_diameter is not None and self.hole_open:
                    for edge_position in self._divider_edge_positions():
                        edge_time = self._predict_fixed_sphere_collision(
                            particle,
                            edge_position,
                            particle.radius + self.divider_half_width,
                        )
                        if edge_time != NO_COLLISION_TIME:
                            candidates.append(
                                SimulationEvent(
                                    edge_time,
                                    "edge",
                                    (index,),
                                    obstacle_position=edge_position,
                                )
                            )
            elif self.boundary_mode == "periodic":
                event_time, axis, wrapped_coordinate = particle.predict_periodic_wrap(
                    self.arena_sizes
                )
                if event_time != NO_COLLISION_TIME:
                    candidates.append(
                        SimulationEvent(
                            event_time,
                            "wrap",
                            (index,),
                            axis,
                            wrapped_coordinate=wrapped_coordinate,
                        )
                    )

        for first_index, first in enumerate(self.particles):
            for second_index in range(first_index + 1, len(self.particles)):
                second = self.particles[second_index]
                on_opposite_sides = False
                if self.boundary_mode == "divided":
                    divider_center = self.arena_sizes[0] / 2.0
                    on_opposite_sides = (first.position[0] < divider_center) != (
                        second.position[0] < divider_center
                    )
                    if on_opposite_sides and (
                        self.hole_diameter is None or not self.hole_open
                    ):
                        continue
                if self.boundary_mode in ("reflecting", "divided"):
                    image_offsets = ((0.0,) * len(self.arena_sizes),)
                else:
                    image_offsets = product(
                        *(
                            (-size, 0.0, size)
                            for size in self.arena_sizes
                        )
                    )
                for image_offset in image_offsets:
                    collision_time = first.predict_collision_time(
                        second, image_offset=image_offset
                    )
                    if collision_time != NO_COLLISION_TIME:
                        if (
                            self.boundary_mode == "divided"
                            and on_opposite_sides
                            and self._collision_is_blocked_by_divider(
                                first, second, collision_time
                            )
                        ):
                            continue
                        candidates.append(
                            SimulationEvent(
                                collision_time,
                                "particle",
                                (first_index, second_index),
                                image_offset=tuple(image_offset),
                            )
                        )
                well_event = self._predict_interaction_well_event(
                    first_index, second_index, first, second
                )
                if well_event is not None:
                    well_crosses_solid_divider = (
                        self.boundary_mode == "divided"
                        and on_opposite_sides
                        and self._collision_is_blocked_by_divider(
                            first, second, well_event.time
                        )
                    )
                    if not well_crosses_solid_divider:
                        candidates.append(well_event)

        if not candidates:
            return None
        # Deterministic tie order: particle/piston collisions, wall/divider/well events,
        # then edge and periodic-wrap events; indices break remaining ties.
        return min(
            candidates,
            key=lambda event: (
                event.time,
                {
                    "particle": 0,
                    "piston": 1,
                    "wall": 1,
                    "divider": 1,
                    "well_entry": 1,
                    "well_exit": 1,
                    "wall_well_entry": 1,
                    "wall_well_exit": 1,
                    "edge": 2,
                    "wrap": 3,
                }[event.kind],
                event.particle_indices,
                -1 if event.axis is None else event.axis,
                () if event.image_offset is None else event.image_offset,
            ),
        )

    def _process_event(self, event: SimulationEvent) -> None:
        if event.kind == "particle":
            first_index, second_index = event.particle_indices
            self.particles[first_index].collide(
                self.particles[second_index],
                event.time,
                event.image_offset,
                reaction_rule=self.reaction_rule,
            )
        elif event.kind in ("well_entry", "well_exit"):
            first_index, second_index = event.particle_indices
            first = self.particles[first_index]
            second = self.particles[second_index]
            exited = first.collide_with_energy_well(
                second, event.time, entering=event.kind == "well_entry"
            )
            pair = (first_index, second_index)
            if event.kind == "well_entry":
                self._inside_interaction_wells.add(pair)
            elif exited:
                self._inside_interaction_wells.discard(pair)
        elif event.kind in ("wall_well_entry", "wall_well_exit"):
            (particle_index,) = event.particle_indices
            if self.wall_well_width is None or self.wall_well_depth is None:
                raise ValueError("Wall well event has no configured well")
            self.particles[particle_index].cross_attractive_wall_well(
                event.time, self.wall_well_width, self.wall_well_depth
            )
        elif event.kind == "wall":
            (particle_index,) = event.particle_indices
            if event.axis is None:
                raise ValueError("Wall event is missing its axis")
            self.particles[particle_index].collide_with_wall(
                event.time,
                self.arena_sizes,
                event.axis,
                heat_reservoir_temperature=(
                    self.heat_reservoir_temperature
                    if event.axis == 1
                    else None
                ),
                thermostat=self.thermostat,
            )
        elif event.kind == "piston":
            (particle_index,) = event.particle_indices
            if self.piston is None or event.axis is None:
                raise ValueError("Piston event has no configured piston or collision axis")
            self.particles[particle_index].collide_with_piston(
                event.time,
                event.axis,
                self.piston.position_at(event.time),
                self.piston.velocity_at(event.time),
                thermostat=self.thermostat,
            )
        elif event.kind == "divider":
            (particle_index,) = event.particle_indices
            if event.side is None:
                raise ValueError("Divider event is missing its side")
            self.particles[particle_index].collide_with_divider(
                event.time,
                self.arena_sizes,
                event.side,
                self.divider_half_width,
            )
        elif event.kind == "edge":
            (particle_index,) = event.particle_indices
            if event.obstacle_position is None:
                raise ValueError("Edge event is missing its obstacle position")
            self._reflect_from_fixed_sphere(
                self.particles[particle_index], event.time, event.obstacle_position
            )
        elif event.kind == "wrap":
            (particle_index,) = event.particle_indices
            if event.axis is None or event.wrapped_coordinate is None:
                raise ValueError("Periodic event is missing wrap information")
            self.particles[particle_index].wrap_periodic(
                event.time,
                self.arena_sizes,
                event.axis,
                event.wrapped_coordinate,
            )
        else:
            raise ValueError(f"Unknown simulation event kind: {event.kind}")
        self.current_time = event.time

    def advance_to(self, target_time: float) -> tuple[SimulationEvent, ...]:
        """Process every event up to ``target_time``, then advance all particles.

        Returns the events processed, in chronological order. The supplied
        time must not be earlier than the simulation's current time.
        """
        target_time = float(target_time)
        if not math.isfinite(target_time):
            raise ValueError("Target time must be finite")
        if target_time < self.current_time:
            raise ValueError("Cannot advance a simulation to a time in the past")

        processed: list[SimulationEvent] = []
        while True:
            event = self.next_event()
            if event is None or event.time > target_time:
                break
            self._process_event(event)
            processed.append(event)

        for particle in self.particles:
            particle.move_to_time(target_time)
        if self.piston is not None:
            self.piston.move_to_time(target_time)
        self.current_time = target_time
        return tuple(processed)
