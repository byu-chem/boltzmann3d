"""Panda3D application entry point."""

from direct.showbase.ShowBase import ShowBase
from direct.gui.OnscreenText import OnscreenText
from panda3d.core import NativeWindowHandle, TextNode, WindowProperties, loadPrcFileData

from .well_demo import EnergyWellScene
from .well_demo_2d import EnergyWell2DScene
from .well_demo_3d import EnergyWell3DScene


class BoltzmannApp(ShowBase):
    """Panda3D window for the Python desktop application."""

    def __init__(self, parent_window: int | None = None) -> None:
        if parent_window is not None:
            # Qt owns the outer window and event loop; Panda creates a child
            # render window that is stepped by the Qt timer.
            loadPrcFileData("boltzmann3d-qt", "window-type none\naudio-library-name null\n")
        super().__init__(windowType="none" if parent_window is not None else None)

        if parent_window is not None:
            properties = WindowProperties()
            properties.setParentWindow(NativeWindowHandle.makeInt(parent_window))
            properties.setOrigin(0, 0)
            properties.setSize(800, 600)
            self.openWindow(
                props=properties,
                type="onscreen",
                makeCamera=True,
                requireWindow=True,
            )

        window_properties = WindowProperties()
        window_properties.setTitle("Boltzmann 3D")
        self.win.requestProperties(window_properties)
        self.setBackgroundColor(0.04, 0.04, 0.06, 1.0)
        self.disableMouse()
        self.view_help = OnscreenText(
            text="F1: 1D     F2: 2D     F3: 3D",
            pos=(-1.28, 0.98),
            scale=0.04,
            align=TextNode.ALeft,
            fg=(0.9, 0.93, 0.98, 1.0),
            mayChange=False,
        )
        self.active_dimension = None
        self.scene = None
        self.accept("f1", self.switch_dimension, [1])
        self.accept("f2", self.switch_dimension, [2])
        self.accept("f3", self.switch_dimension, [3])
        self.switch_dimension(2 if parent_window is not None else 3)

    def switch_dimension(self, dimension: int) -> None:
        """Replace the active particle scene with its requested dimension."""
        scenes = {
            1: (EnergyWellScene, (32.0, -78.0, 18.0), (32.0, 0.0, 10.0)),
            2: (EnergyWell2DScene, (32.0, -72.0, 20.0), (32.0, 0.0, 20.0)),
            3: (EnergyWell3DScene, (32.0, -92.0, 56.0), (32.0, 20.0, 20.0)),
        }
        if dimension not in scenes or dimension == self.active_dimension:
            return

        if self.scene is not None:
            self.scene.destroy()

        scene_type, camera_position, camera_target = scenes[dimension]
        self.camera.setPos(*camera_position)
        self.camera.lookAt(*camera_target)
        self.scene = scene_type(self)
        self.active_dimension = dimension


def main() -> None:
    """Create the app and enter Panda3D's event loop."""
    app = BoltzmannApp()
    app.run()
