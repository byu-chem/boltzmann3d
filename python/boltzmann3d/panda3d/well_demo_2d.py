"""Top-down Panda3D visualization of the 2D attractive-particle well.

Run with ``PYTHONPATH=python python -m boltzmann3d.panda3d.well_demo_2d``.
"""

import math

from direct.gui.OnscreenText import OnscreenText
from direct.showbase.ShowBase import ShowBase
from direct.showbase.ShowBaseGlobal import globalClock
from direct.task import Task
from panda3d.core import AmbientLight, DirectionalLight, LineSegs, TextNode, WindowProperties

from ..physics import Particle, Piston, PistonMode, ReactionRule, Simulation


class EnergyWell2DScene:
    """Animate a pair of 2D particles inside a reflecting rectangular arena."""

    ARENA_WIDTH = 64.0
    ARENA_HEIGHT = 40.0
    PARTICLE_RADIUS = 0.75
    INTERACTION_RADIUS_MULTIPLIER = 2.0
    WELL_RADIUS = INTERACTION_RADIUS_MULTIPLIER * 2.0 * PARTICLE_RADIUS
    WELL_DEPTH = 0.0001  # kJ/mol, sized for the demo's displayed velocities.
    WALL_WELL_WIDTH = 6.0
    RESERVOIR_TEMPERATURE = 315.0
    REACTION_RULE = ReactionRule(0.0001, 0.00005)
    SIMULATION_SPEED = 1.0
    PISTON_SPEED = 8.0
    PISTON_COMPRESSED_LEVEL = 0.2

    def __init__(self, app: ShowBase) -> None:
        self.app = app
        self.root = app.render.attachNewNode("energy-well-2d-demo")
        self.paused = False
        self.wall_well_enabled = False
        self.heat_reservoir_enabled = False
        self.mode = "pass"
        self.piston_mode = PistonMode.ADIABATIC
        self.last_event = "Approaching the interaction well"

        self._add_lighting()
        self._add_arena()
        self._add_wall_well_marker()
        self._add_particles()
        self._add_instructions()
        self._reset_simulation("pass")

        app.accept("1", self._reset_simulation, ["pass"])
        app.accept("2", self._reset_simulation, ["trap"])
        app.accept("3", self._reset_simulation, ["forward_reaction"])
        app.accept("4", self._reset_simulation, ["reverse_reaction"])
        app.accept("r", self._reset_current_mode)
        app.accept("space", self._toggle_pause)
        app.accept("w", self._toggle_wall_well)
        app.accept("h", self._toggle_heat_reservoir)
        app.accept("p", self._toggle_piston)
        app.accept("i", self._toggle_piston_mode)

    def _add_lighting(self) -> None:
        ambient = AmbientLight("well-2d-ambient")
        ambient.setColor((0.58, 0.58, 0.62, 1.0))
        self.root.setLight(self.root.attachNewNode(ambient))

        key_light = DirectionalLight("well-2d-key-light")
        key_light.setColor((0.95, 0.9, 0.85, 1.0))
        key_path = self.root.attachNewNode(key_light)
        key_path.setHpr(-35, -45, 0)
        self.root.setLight(key_path)

    def _add_arena(self) -> None:
        arena = LineSegs("well-2d-arena")
        arena.setThickness(2.0)
        arena.setColor(0.52, 0.58, 0.68, 1.0)
        corners = (
            (0.0, -0.02, 0.0),
            (self.ARENA_WIDTH, -0.02, 0.0),
            (self.ARENA_WIDTH, -0.02, self.ARENA_HEIGHT),
            (0.0, -0.02, self.ARENA_HEIGHT),
        )
        arena.moveTo(*corners[0])
        for corner in corners[1:] + corners[:1]:
            arena.drawTo(*corner)
        self.root.attachNewNode(arena.create())

        piston = LineSegs("movable-piston-marker")
        piston.setThickness(4.0)
        piston.setColor(1.0, 0.72, 0.25, 1.0)
        piston.moveTo(0.0, -0.06, 0.0)
        piston.drawTo(self.ARENA_WIDTH, -0.06, 0.0)
        self.piston_marker = self.root.attachNewNode(piston.create())

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
        marker = LineSegs("attractive-wall-well-marker")
        marker.setThickness(2.0)
        marker.setColor(1.0, 0.72, 0.25, 1.0)
        marker.moveTo(0.0, -0.04, self.WALL_WELL_WIDTH)
        marker.drawTo(self.ARENA_WIDTH, -0.04, self.WALL_WELL_WIDTH)
        self.wall_well_marker = self.root.attachNewNode(marker.create())
        self.wall_well_marker.hide()

    def _add_instructions(self) -> None:
        self.instructions = OnscreenText(
            text="1/2: well  3/4: reaction  P: piston  I: piston mode  W: wall well  H: reservoir  SPACE: pause  R: reset",
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
        self.app.taskMgr.remove("energy-well-2d-update")

        if mode == "pass":
            # The paths are offset in both coordinates so the well collision
            # changes the velocities along a 2D line of centers.
            positions = ((10.0, 12.0), (50.0, 22.0))
            velocities = ((20.0, 4.0), (-20.0, -4.0))
            self.last_event = "Approaching the interaction well"
            self.mode_label = "PASS AND EXIT"
            species = ("red", "red")
        elif mode == "trap":
            # Start within the well and move outward with insufficient energy
            # to escape its depth.
            positions = ((30.0, 18.0), (32.0, 19.0))
            velocities = ((-5.0, -2.0), (5.0, 2.0))
            self.last_event = "Pair starts inside the well with too little escape energy"
            self.mode_label = "TRAPPED PAIR"
            species = ("red", "red")
        else:
            positions = ((20.0, 20.0), (44.0, 20.0))
            velocities = ((16.0, 0.0), (-16.0, 0.0))
            if mode == "forward_reaction":
                species = ("red", "red")
                self.last_event = "Red reactants approach: a collision can make blue products"
                self.mode_label = "FORWARD REACTION"
            else:
                species = ("blue", "blue")
                self.last_event = "Blue products approach: a collision can make red reactants"
                self.mode_label = "REVERSE REACTION"

        particles = [
            Particle(
                position=position,
                velocity=velocity,
                mass=1.0,
                radius=self.PARTICLE_RADIUS,
                energy_well_depth=(
                    self.WELL_DEPTH if mode in ("pass", "trap") else 0.0
                ),
                species=particle_species,
            )
            for position, velocity, particle_species in zip(positions, velocities, species)
        ]
        self.simulation = Simulation(
            particles,
            (self.ARENA_WIDTH, self.ARENA_HEIGHT),
            interaction_radius_multiplier=self.INTERACTION_RADIUS_MULTIPLIER,
            wall_well_width=(self.WALL_WELL_WIDTH if self.wall_well_enabled else None),
            wall_well_depth=(self.WELL_DEPTH if self.wall_well_enabled else None),
            heat_reservoir_temperature=(
                self.RESERVOIR_TEMPERATURE if self.heat_reservoir_enabled else None
            ),
            reaction_rule=(
                self.REACTION_RULE if mode in ("forward_reaction", "reverse_reaction") else None
            ),
            piston=Piston(
                self.ARENA_HEIGHT,
                speed=self.PISTON_SPEED,
            ),
        )
        if self.piston_mode is PistonMode.ISOTHERMAL:
            self.simulation.set_piston_mode(self.piston_mode)
        if self.wall_well_enabled:
            self.wall_well_marker.show()
        else:
            self.wall_well_marker.hide()
        self._sync_particle_nodes()
        self.app.taskMgr.add(self._update, "energy-well-2d-update")

    def _sync_particle_nodes(self) -> None:
        for particle, node in zip(self.simulation.particles, self.particle_nodes):
            # Simulation (x, y) maps to Panda3D's horizontal (x, z) plane.
            node.setPos(particle.position[0], 0.0, particle.position[1])
            node.setColor(
                (0.96, 0.18, 0.22, 1.0)
                if particle.species == "red"
                else (0.2, 0.62, 1.0, 1.0)
            )
        self.piston_marker.setZ(self.simulation.piston.position)

    def destroy(self) -> None:
        """Remove this scene before the Panda3D app switches dimensions."""
        self.app.taskMgr.remove("energy-well-2d-update")
        for key in ("1", "2", "3", "4", "p", "i", "r", "space", "w", "h"):
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

    def _toggle_piston(self) -> None:
        target = self.simulation.piston.target_level
        next_target = 0.0 if target > 0.0 else self.PISTON_COMPRESSED_LEVEL
        self.simulation.set_piston_target_level(
            next_target, speed=self.PISTON_SPEED
        )
        direction = "outward" if next_target == 0.0 else "inward"
        self.last_event = f"Piston moving {direction}"

    def _toggle_piston_mode(self) -> None:
        self.piston_mode = (
            PistonMode.ADIABATIC
            if self.piston_mode is PistonMode.ISOTHERMAL
            else PistonMode.ISOTHERMAL
        )
        self.simulation.set_piston_mode(self.piston_mode)
        self.last_event = f"Piston mode set to {self.piston_mode.value}"

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
                    if self.mode == "forward_reaction":
                        self.last_event = "Forward reaction: red reactants became blue products"
                    elif self.mode == "reverse_reaction":
                        self.last_event = "Reverse reaction: blue products became red reactants"
                    else:
                        self.last_event = "Hard-sphere collision"
                elif event.kind == "wall":
                    self.last_event = "Particle reflected from the arena wall"
                elif event.kind == "wall_well_entry":
                    self.last_event = "Entered attractive wall well: vertical speed increased"
                elif event.kind == "wall_well_exit":
                    self.last_event = "Crossed out of attractive wall well: vertical speed changed"
                elif event.kind == "piston":
                    self.last_event = "Particle collided with the moving piston"
            self._sync_particle_nodes()

        piston = self.simulation.piston
        self.piston_marker.setZ(piston.position)
        thermostat = self.simulation.thermostat
        delta_ke = 0.0 if thermostat is None else thermostat.delta_ke_joules
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
            f"{self.mode_label}  |  {pause_label}  |  PISTON {self.piston_mode.value.upper()} "
            f"Y={piston.position:.1f}  |  ΔKE={delta_ke:+.2e} J  |  "
            f"WALL WELL {wall_label}  |  "
            f"RESERVOIR {reservoir_label}  |  {state}  |  "
            f"separation={separation:.2f}  |  t={self.simulation.current_time:.2f}\n"
            f"{self.last_event}"
        )
        return Task.cont


class EnergyWell2DDemoApp(ShowBase):
    """Standalone desktop window for the 2D energy-well visualization."""

    def __init__(self) -> None:
        super().__init__()
        properties = WindowProperties()
        properties.setTitle("Boltzmann 3D — 2D Energy Well")
        self.win.requestProperties(properties)
        self.setBackgroundColor(0.035, 0.045, 0.065, 1.0)
        self.disableMouse()
        self.camera.setPos(
            EnergyWell2DScene.ARENA_WIDTH / 2.0,
            -72.0,
            EnergyWell2DScene.ARENA_HEIGHT / 2.0,
        )
        self.camera.lookAt(
            EnergyWell2DScene.ARENA_WIDTH / 2.0,
            0.0,
            EnergyWell2DScene.ARENA_HEIGHT / 2.0,
        )
        self.scene = EnergyWell2DScene(self)


def main() -> None:
    """Launch the standalone 2D energy-well visualization."""
    EnergyWell2DDemoApp().run()


if __name__ == "__main__":
    main()
