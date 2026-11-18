"""Panda3D visualization of the 3D attractive-particle interaction well.

Run with ``PYTHONPATH=python python -m boltzmann3d.panda3d.well_demo_3d``.
"""

import math

from direct.gui.OnscreenText import OnscreenText
from direct.showbase.ShowBase import ShowBase
from direct.showbase.ShowBaseGlobal import globalClock
from direct.task import Task
from panda3d.core import (
    AmbientLight,
    DirectionalLight,
    LineSegs,
    TextNode,
    WindowProperties,
)

from ..physics import Particle, Simulation


class EnergyWell3DScene:
    """Animate two 3D particles in a reflecting box with square-well physics."""

    ARENA_SIZES = (64.0, 40.0, 40.0)
    PARTICLE_RADIUS = 0.75
    INTERACTION_RADIUS_MULTIPLIER = 2.0
    WELL_RADIUS = INTERACTION_RADIUS_MULTIPLIER * 2.0 * PARTICLE_RADIUS
    WELL_DEPTH = 0.0001  # kJ/mol, sized for the demo's displayed velocities.
    WALL_WELL_WIDTH = 6.0
    RESERVOIR_TEMPERATURE = 315.0
    SIMULATION_SPEED = 1.0

    def __init__(self, app: ShowBase) -> None:
        self.app = app
        self.root = app.render.attachNewNode("energy-well-3d-demo")
        self.paused = False
        self.wall_well_enabled = False
        self.heat_reservoir_enabled = False
        self.mode = "pass"
        self.last_event = "Approaching the interaction well in 3D"

        self._add_lighting()
        self._add_arena()
        self._add_wall_well_marker()
        self._add_particles()
        self._add_instructions()
        self._reset_simulation("pass")

        app.accept("1", self._reset_simulation, ["pass"])
        app.accept("2", self._reset_simulation, ["trap"])
        app.accept("r", self._reset_current_mode)
        app.accept("space", self._toggle_pause)
        app.accept("w", self._toggle_wall_well)
        app.accept("h", self._toggle_heat_reservoir)

    def _add_lighting(self) -> None:
        ambient = AmbientLight("well-3d-ambient")
        ambient.setColor((0.58, 0.58, 0.62, 1.0))
        self.root.setLight(self.root.attachNewNode(ambient))

        key_light = DirectionalLight("well-3d-key-light")
        key_light.setColor((0.95, 0.9, 0.85, 1.0))
        key_path = self.root.attachNewNode(key_light)
        key_path.setHpr(-35, -45, 0)
        self.root.setLight(key_path)

    def _add_arena(self) -> None:
        width, depth, height = self.ARENA_SIZES
        corners = (
            (0.0, 0.0, 0.0),
            (width, 0.0, 0.0),
            (width, depth, 0.0),
            (0.0, depth, 0.0),
            (0.0, 0.0, height),
            (width, 0.0, height),
            (width, depth, height),
            (0.0, depth, height),
        )
        edges = (
            (0, 1), (1, 2), (2, 3), (3, 0),
            (4, 5), (5, 6), (6, 7), (7, 4),
            (0, 4), (1, 5), (2, 6), (3, 7),
        )
        arena = LineSegs("well-3d-arena")
        arena.setThickness(1.5)
        arena.setColor(0.52, 0.58, 0.68, 1.0)
        for start, end in edges:
            arena.moveTo(*corners[start])
            arena.drawTo(*corners[end])
        self.root.attachNewNode(arena.create())

    def _add_particles(self) -> None:
        sphere_model = self.app.loader.loadModel("models/misc/sphere")
        self.particle_nodes = []
        for color in ((0.96, 0.18, 0.22, 1.0), (0.2, 0.62, 1.0, 1.0)):
            sphere = sphere_model.copyTo(self.root)
            sphere.setScale(self.PARTICLE_RADIUS)
            sphere.setColor(*color)
            self.particle_nodes.append(sphere)
        sphere_model.removeNode()

    def _add_wall_well_marker(self) -> None:
        width, _, height = self.ARENA_SIZES
        y = self.WALL_WELL_WIDTH
        corners = ((0.0, y, 0.0), (width, y, 0.0), (width, y, height), (0.0, y, height))
        marker = LineSegs("attractive-wall-well-marker")
        marker.setThickness(2.0)
        marker.setColor(1.0, 0.72, 0.25, 1.0)
        marker.moveTo(*corners[0])
        for corner in corners[1:] + corners[:1]:
            marker.drawTo(*corner)
        self.wall_well_marker = self.root.attachNewNode(marker.create())
        self.wall_well_marker.hide()

    def _add_instructions(self) -> None:
        self.instructions = OnscreenText(
            text="1: pass and exit     2: trapped pair     W: wall well     H: heat reservoir     SPACE: pause     R: reset",
            pos=(-1.28, 0.92),
            scale=0.045,
            align=TextNode.ALeft,
            fg=(0.9, 0.93, 0.98, 1.0),
            mayChange=False,
        )
        self.status = OnscreenText(
            text="",
            pos=(-1.28, 0.83),
            scale=0.042,
            align=TextNode.ALeft,
            fg=(0.74, 0.9, 0.88, 1.0),
            mayChange=True,
        )

    def _reset_simulation(self, mode: str) -> None:
        self.mode = mode
        self.paused = False
        self.app.taskMgr.remove("energy-well-3d-update")

        if mode == "pass":
            # The particles miss direct contact but cross the well boundary
            # with enough radial energy to leave it again.
            positions = ((10.0, 10.0, 10.0), (50.0, 20.0, 22.0))
            velocities = ((20.0, 4.0, 6.0), (-20.0, -4.0, -6.0))
            self.last_event = "Approaching the interaction well in 3D"
            self.mode_label = "PASS AND EXIT"
        else:
            # The pair begins inside the well and moves outward below the
            # escape-energy threshold.
            positions = ((30.0, 18.0, 18.0), (32.0, 19.0, 19.0))
            velocities = ((-5.0, -2.5, -2.5), (5.0, 2.5, 2.5))
            self.last_event = "Pair starts inside the well with too little escape energy"
            self.mode_label = "TRAPPED PAIR"

        particles = [
            Particle(
                position=position,
                velocity=velocity,
                mass=1.0,
                radius=self.PARTICLE_RADIUS,
                energy_well_depth=self.WELL_DEPTH,
            )
            for position, velocity in zip(positions, velocities)
        ]
        self.simulation = Simulation(
            particles,
            self.ARENA_SIZES,
            interaction_radius_multiplier=self.INTERACTION_RADIUS_MULTIPLIER,
            wall_well_width=(self.WALL_WELL_WIDTH if self.wall_well_enabled else None),
            wall_well_depth=(self.WELL_DEPTH if self.wall_well_enabled else None),
            heat_reservoir_temperature=(
                self.RESERVOIR_TEMPERATURE if self.heat_reservoir_enabled else None
            ),
        )
        if self.wall_well_enabled:
            self.wall_well_marker.show()
        else:
            self.wall_well_marker.hide()
        self._sync_particle_nodes()
        self.app.taskMgr.add(self._update, "energy-well-3d-update")

    def _sync_particle_nodes(self) -> None:
        for particle, node in zip(self.simulation.particles, self.particle_nodes):
            node.setPos(*particle.position)

    def destroy(self) -> None:
        """Remove this scene before the Panda3D app switches dimensions."""
        self.app.taskMgr.remove("energy-well-3d-update")
        for key in ("1", "2", "r", "space", "w", "h"):
            self.app.ignore(key)
        self.instructions.destroy()
        self.status.destroy()
        self.root.removeNode()

    def _toggle_pause(self) -> None:
        self.paused = not self.paused

    def _reset_current_mode(self) -> None:
        self._reset_simulation(self.mode)

    def _toggle_wall_well(self) -> None:
        self.wall_well_enabled = not self.wall_well_enabled
        if not self.wall_well_enabled:
            self.heat_reservoir_enabled = False
        self._reset_simulation(self.mode)

    def _toggle_heat_reservoir(self) -> None:
        self.heat_reservoir_enabled = not self.heat_reservoir_enabled
        if self.heat_reservoir_enabled:
            self.wall_well_enabled = True
        self._reset_simulation(self.mode)

    def _update(self, task: Task) -> int:
        if not self.paused:
            frame_time = min(globalClock.getDt(), 0.1)
            target_time = (
                self.simulation.current_time + frame_time * self.SIMULATION_SPEED
            )
            events = self.simulation.advance_to(target_time)
            for event in events:
                if event.kind == "well_entry":
                    self.last_event = "Entered well: radial relative speed increased"
                elif event.kind == "well_exit":
                    pair = event.particle_indices
                    if pair in self.simulation._inside_interaction_wells:
                        self.last_event = "Escape blocked: pair reflected and remains trapped"
                    else:
                        self.last_event = "Exited well: radial relative speed decreased"
                elif event.kind == "particle":
                    self.last_event = "Hard-sphere collision"
                elif event.kind == "wall":
                    self.last_event = "Particle reflected from the arena wall"
                elif event.kind == "wall_well_entry":
                    self.last_event = "Entered attractive wall well: vertical speed increased"
                elif event.kind == "wall_well_exit":
                    self.last_event = "Crossed out of attractive wall well: vertical speed changed"
            self._sync_particle_nodes()

        first, second = self.simulation.particles
        separation = math.dist(first.position, second.position)
        state = "INSIDE WELL" if separation <= self.WELL_RADIUS else "OUTSIDE WELL"
        pause_label = "PAUSED" if self.paused else "RUNNING"
        wall_label = "ON" if self.wall_well_enabled else "OFF"
        reservoir_label = (
            f"ON ({self.RESERVOIR_TEMPERATURE:.0f} K)"
            if self.heat_reservoir_enabled
            else "OFF"
        )
        self.status.setText(
            f"{self.mode_label}  |  {pause_label}  |  WALL WELL {wall_label}  |  "
            f"RESERVOIR {reservoir_label}  |  {state}  |  "
            f"separation={separation:.2f}  |  t={self.simulation.current_time:.2f}\n"
            f"{self.last_event}"
        )
        return Task.cont


class EnergyWell3DDemoApp(ShowBase):
    """Standalone desktop window for the 3D energy-well visualization."""

    def __init__(self) -> None:
        super().__init__()
        properties = WindowProperties()
        properties.setTitle("Boltzmann 3D — 3D Energy Well")
        self.win.requestProperties(properties)
        self.setBackgroundColor(0.035, 0.045, 0.065, 1.0)
        self.disableMouse()
        width, depth, height = EnergyWell3DScene.ARENA_SIZES
        target = (width / 2.0, depth / 2.0, height / 2.0)
        self.camera.setPos(target[0], -92.0, target[2] + 36.0)
        self.camera.lookAt(*target)
        self.scene = EnergyWell3DScene(self)


def main() -> None:
    """Launch the standalone 3D energy-well visualization."""
    EnergyWell3DDemoApp().run()


if __name__ == "__main__":
    main()
