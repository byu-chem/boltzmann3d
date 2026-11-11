"""Panda3D application entry point."""

from direct.showbase.ShowBase import ShowBase
from panda3d.core import WindowProperties

from .scene import ArenaScene


class BoltzmannApp(ShowBase):
    """Panda3D window for the Python desktop application."""

    def __init__(self) -> None:
        super().__init__()
        window_properties = WindowProperties()
        window_properties.setTitle("Boltzmann 3D")
        self.win.requestProperties(window_properties)
        self.setBackgroundColor(0.04, 0.04, 0.06, 1.0)
        self.disableMouse()
        self.camera.setPos(35, -95, 25)
        self.camera.lookAt(35, 0, 25)
        self.scene = ArenaScene(self.render, self.loader)


def main() -> None:
    """Create the app and enter Panda3D's event loop."""
    app = BoltzmannApp()
    app.run()
