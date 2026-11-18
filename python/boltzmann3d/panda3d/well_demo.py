"""Panda3D visualization of the 1D attractive-particle interaction well.

Run with ``PYTHONPATH=python python -m boltzmann3d.panda3d.well_demo``.
"""

from direct.showbase.ShowBase import ShowBase
from direct.showbase.ShowBaseGlobal import globalClock
from direct.gui.OnscreenText import OnscreenText
from direct.task import Task
from panda3d.core import (
    AmbientLight,
    DirectionalLight,
    LineSegs,
    TextNode,
    WindowProperties,
)

from ..physics import Particle, Piston, Simulation


class EnergyWellScene:
    """Animate two particles and draw their 1D square-well boundary."""

    ARENA_WIDTH = 64.0
    ARENA_HEIGHT = 20.0
    PARTICLE_RADIUS = 0.75
    INTERACTION_RADIUS_MULTIPLIER = 2.0
    WELL_RADIUS = INTERACTION_RADIUS_MULTIPLIER * 2.0 * PARTICLE_RADIUS
    SIMULATION_SPEED = 1.0
    PISTON_SPEED = 8.0
    PISTON_COMPRESSED_LEVEL = 0.2

    def __init__(self, app: ShowBase) -> None:
        self.app = app
        self.root = app.render.attachNewNode("energy-well-demo")
        self.paused = False
        self.last_event = "Approaching the interaction well"
        self.mode = "pass"

        self._add_lighting()
        self._add_arena_and_well_markers()
        self._add_particles()
        self._add_instructions()
        self._reset_simulation("pass")

        app.accept("1", self._reset_simulation, ["pass"])
        app.accept("2", self._reset_simulation, ["trap"])
        app.accept("r", self._reset_current_mode)
        app.accept("space", self._toggle_pause)
        app.accept("p", self._toggle_piston)

    def _add_lighting(self) -> None:
        ambient = AmbientLight("well-demo-ambient")
        ambient.setColor((0.58, 0.58, 0.62, 1.0))
        self.root.setLight(self.root.attachNewNode(ambient))

        key_light = DirectionalLight("well-demo-key-light")
        key_light.setColor((0.95, 0.9, 0.85, 1.0))
        key_path = self.root.attachNewNode(key_light)
        key_path.setHpr(-35, -45, 0)
        self.root.setLight(key_path)

    def _add_arena_and_well_markers(self) -> None:
        arena = LineSegs("well-demo-arena")
        arena.setThickness(1.5)
        arena.setColor(0.52, 0.58, 0.68, 1.0)
        corners = (
            (0.0, 0.0, 0.0),
            (self.ARENA_WIDTH, 0.0, 0.0),
            (self.ARENA_WIDTH, 0.0, self.ARENA_HEIGHT),
            (0.0, 0.0, self.ARENA_HEIGHT),
        )
        arena.moveTo(*corners[0])
        for corner in corners[1:] + corners[:1]:
            arena.drawTo(*corner)
        self.root.attachNewNode(arena.create())

        # Equal-mass particles keep their center of mass at the arena center.
        # These lines mark the center-to-center distance at which well events occur.
        center = self.ARENA_WIDTH / 2.0
        marker_left = center - self.WELL_RADIUS / 2.0
        marker_right = center + self.WELL_RADIUS / 2.0
        markers = LineSegs("interaction-well-boundary")
        markers.setThickness(2.0)
        markers.setColor(0.25, 0.86, 0.78, 1.0)
        markers.moveTo(marker_left, -0.05, 2.0)
        markers.drawTo(marker_left, -0.05, self.ARENA_HEIGHT - 2.0)
        markers.moveTo(marker_right, -0.05, 2.0)
        markers.drawTo(marker_right, -0.05, self.ARENA_HEIGHT - 2.0)
        self.root.attachNewNode(markers.create())

        piston_marker = LineSegs("movable-piston-marker")
        piston_marker.setThickness(4.0)
        piston_marker.setColor(1.0, 0.72, 0.25, 1.0)
        piston_marker.moveTo(0.0, -0.08, 0.0)
        piston_marker.drawTo(0.0, -0.08, self.ARENA_HEIGHT)
        self.piston_marker = self.root.attachNewNode(piston_marker.create())

    def _add_particles(self) -> None:
        self.sphere_model = self.app.loader.loadModel("models/misc/sphere")
        self.particle_nodes = []
        for color in ((0.96, 0.18, 0.22, 1.0), (0.2, 0.62, 1.0, 1.0)):
            sphere = self.sphere_model.copyTo(self.root)
            sphere.setScale(self.PARTICLE_RADIUS)
            sphere.setColor(*color)
            self.particle_nodes.append(sphere)
        self.sphere_model.removeNode()

    def _add_instructions(self) -> None:
        self.instructions = OnscreenText(
            text="1: pass     2: trapped     P: reverse piston direction     SPACE: pause     R: reset",
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
        self.app.taskMgr.remove("energy-well-demo-update")

        if mode == "pass":
            positions = (22.0, 42.0)
            velocities = (3.0, -3.0)
            well_depth = 0.5
            self.last_event = "Approaching the interaction well"
            mode_label = "PASS AND EXIT"
        else:
            # Start inside the well, moving outward too slowly to escape it.
            positions = (31.0, 33.0)
            velocities = (-0.5, 0.5)
            well_depth = 5.0
            self.last_event = "Pair starts inside the well with too little escape energy"
            mode_label = "TRAPPED PAIR"

        particles = [
            Particle(
                position=(position,),
                velocity=(velocity,),
                mass=1.0,
                radius=self.PARTICLE_RADIUS,
                energy_well_depth=well_depth,
            )
            for position, velocity in zip(positions, velocities)
        ]
        self.simulation = Simulation(
            particles,
            (self.ARENA_WIDTH,),
            interaction_radius_multiplier=self.INTERACTION_RADIUS_MULTIPLIER,
            piston=Piston(
                self.ARENA_WIDTH,
                speed=self.PISTON_SPEED,
            ),
        )
        self.mode_label = mode_label
        self._sync_particle_nodes()
        self.app.taskMgr.add(self._update, "energy-well-demo-update")

    def _sync_particle_nodes(self) -> None:
        for particle, node in zip(self.simulation.particles, self.particle_nodes):
            node.setPos(particle.position[0], 0.0, self.ARENA_HEIGHT / 2.0)
        self.piston_marker.setX(self.simulation.piston.position)

    def destroy(self) -> None:
        """Remove this scene before the Panda3D app switches dimensions."""
        self.app.taskMgr.remove("energy-well-demo-update")
        for key in ("1", "2", "p", "r", "space"):
            self.app.ignore(key)
        self.instructions.destroy()
        self.status.destroy()
        self.root.removeNode()

    def _toggle_pause(self) -> None:
        self.paused = not self.paused

    def _reset_current_mode(self) -> None:
        self._reset_simulation(self.mode)

    def _toggle_piston(self) -> None:
        target = self.simulation.piston.target_level
        next_target = 0.0 if target > 0.0 else self.PISTON_COMPRESSED_LEVEL
        self.simulation.set_piston_target_level(
            next_target, speed=self.PISTON_SPEED
        )
        direction = "outward" if next_target == 0.0 else "inward"
        self.last_event = f"Piston moving {direction}"

    def _update(self, task: Task) -> int:
        if not self.paused:
            frame_time = min(globalClock.getDt(), 0.1)
            target_time = (
                self.simulation.current_time + frame_time * self.SIMULATION_SPEED
            )
            events = self.simulation.advance_to(target_time)
            for event in events:
                if event.kind == "well_entry":
                    self.last_event = "Entered well: relative speed increased"
                elif event.kind == "well_exit":
                    if (0, 1) in self.simulation._inside_interaction_wells:
                        self.last_event = "Escape blocked: pair reflected and remains trapped"
                    else:
                        self.last_event = "Exited well: relative speed decreased"
                elif event.kind == "particle":
                    self.last_event = "Hard-sphere collision"
                elif event.kind == "piston":
                    self.last_event = "Particle collided with the moving piston"
            self._sync_particle_nodes()

        piston = self.simulation.piston
        self.piston_marker.setX(piston.position)

        first, second = self.simulation.particles
        separation = abs(first.position[0] - second.position[0])
        state = "INSIDE WELL" if separation <= self.WELL_RADIUS else "OUTSIDE WELL"
        pause_label = "PAUSED" if self.paused else "RUNNING"
        self.status.setText(
            f"{self.mode_label}  |  {pause_label}  |  PISTON X={piston.position:.1f}  |  {state}  |  "
            f"v=({first.velocity[0]:+.2f}, {second.velocity[0]:+.2f})  |  "
            f"t={self.simulation.current_time:.2f} s\n{self.last_event}"
        )
        return Task.cont


class EnergyWellDemoApp(ShowBase):
    """Standalone desktop window for the 1D energy-well demonstration."""

    def __init__(self) -> None:
        super().__init__()
        properties = WindowProperties()
        properties.setTitle("Boltzmann 3D — 1D Energy Well")
        self.win.requestProperties(properties)
        self.setBackgroundColor(0.035, 0.045, 0.065, 1.0)
        self.disableMouse()
        self.camera.setPos(
            EnergyWellScene.ARENA_WIDTH / 2.0,
            -78.0,
            EnergyWellScene.ARENA_HEIGHT / 2.0 + 8.0,
        )
        self.camera.lookAt(
            EnergyWellScene.ARENA_WIDTH / 2.0,
            0.0,
            EnergyWellScene.ARENA_HEIGHT / 2.0,
        )
        self.scene = EnergyWellScene(self)


def main() -> None:
    """Launch the standalone energy-well visualization."""
    EnergyWellDemoApp().run()


if __name__ == "__main__":
    main()
