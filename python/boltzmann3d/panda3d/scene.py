"""Static 2D arena and particle display for the Panda3D prototype."""

import random

from panda3d.core import AmbientLight, DirectionalLight, LineSegs, NodePath


class ArenaScene:
    """A flat arena containing stationary red particle spheres."""

    ARENA_WIDTH = 70.0
    ARENA_HEIGHT = 50.0
    PARTICLE_RADIUS = 1.0
    PARTICLE_COUNT = 50

    def __init__(self, render: NodePath, loader) -> None:
        self.root = render.attachNewNode("arena-scene")
        self.loader = loader
        self._add_lighting()
        self._add_arena()
        self._add_particles()

    def _add_lighting(self) -> None:
        ambient = AmbientLight("ambient-light")
        ambient.setColor((0.38, 0.38, 0.42, 1.0))
        self.root.setLight(self.root.attachNewNode(ambient))

        key_light = DirectionalLight("key-light")
        key_light.setColor((0.95, 0.82, 0.82, 1.0))
        key_light_path = self.root.attachNewNode(key_light)
        key_light_path.setHpr(-35, -45, 0)
        self.root.setLight(key_light_path)

    def _add_arena(self) -> None:
        """Draw the rectangular boundary in the arena's 2D plane."""
        lines = LineSegs("arena-boundary")
        lines.setThickness(1.5)
        lines.setColor(0.62, 0.66, 0.72, 1.0)

        corners = (
            (0.0, 0.0, 0.0),
            (self.ARENA_WIDTH, 0.0, 0.0),
            (self.ARENA_WIDTH, 0.0, self.ARENA_HEIGHT),
            (0.0, 0.0, self.ARENA_HEIGHT),
        )
        lines.moveTo(*corners[0])
        for corner in corners[1:] + corners[:1]:
            lines.drawTo(*corner)

        self.root.attachNewNode(lines.create())

    def _particle_positions(self) -> list[tuple[float, float]]:
        """Create a repeatable, well-spaced sample layout."""
        rng = random.Random(24)
        positions = []
        min_distance = self.PARTICLE_RADIUS * 2.15
        min_distance_squared = min_distance * min_distance

        while len(positions) < self.PARTICLE_COUNT:
            candidate = (
                rng.uniform(self.PARTICLE_RADIUS, self.ARENA_WIDTH - self.PARTICLE_RADIUS),
                rng.uniform(self.PARTICLE_RADIUS, self.ARENA_HEIGHT - self.PARTICLE_RADIUS),
            )
            if all(
                (candidate[0] - x) ** 2 + (candidate[1] - z) ** 2 >= min_distance_squared
                for x, z in positions
            ):
                positions.append(candidate)

        return positions

    def _add_particles(self) -> None:
        """Place red spheres in the arena; physics will move them later."""
        sphere_model = self.loader.loadModel("models/misc/sphere")
        for x, z in self._particle_positions():
            sphere = sphere_model.copyTo(self.root)
            sphere.setPos(x, 0.0, z)
            sphere.setScale(self.PARTICLE_RADIUS)
            sphere.setColor(0.96, 0.16, 0.20, 1.0)
