"""PySide6 desktop interface for the Boltzmann 3D Panda3D prototype."""

from __future__ import annotations

import math
import os
import sys

# Panda3D's Linux GLX window uses X11.  If Qt chooses Wayland while an
# XWayland display is available, QWidget.winId() is a Wayland handle rather
# than an X11 window ID, and Panda cannot use it as a parent drawable.
if sys.platform.startswith("linux") and os.environ.get("DISPLAY"):
    os.environ["QT_QPA_PLATFORM"] = "xcb"

from matplotlib.backends.backend_qtagg import FigureCanvasQTAgg
from matplotlib.figure import Figure
from PySide6.QtCore import QTimer, Qt
from PySide6.QtGui import QAction
from PySide6.QtWidgets import (
    QApplication,
    QComboBox,
    QHBoxLayout,
    QLabel,
    QMainWindow,
    QMessageBox,
    QPushButton,
    QSplitter,
    QVBoxLayout,
    QWidget,
)

from ..panda3d.app import BoltzmannApp


_DIMENSIONS = {"1D": 1, "2D": 2, "3D": 3}
_SCENARIOS = {
    "Pass through well": "pass",
    "Trapped in well": "trap",
    "Forward reaction": "forward_reaction",
    "Reverse reaction": "reverse_reaction",
}


class PandaViewport(QWidget):
    """A Qt widget that hosts the Panda3D child window."""

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setAttribute(Qt.WidgetAttribute.WA_NativeWindow, True)
        self.setMinimumSize(480, 360)
        self._panda: BoltzmannApp | None = None
        self._timer = QTimer(self)
        self._timer.setInterval(16)
        self._timer.timeout.connect(self._step_panda)

    @property
    def panda(self) -> BoltzmannApp | None:
        return self._panda

    def showEvent(self, event) -> None:  # noqa: N802 - Qt override name
        super().showEvent(event)
        if self._panda is None:
            self._panda = BoltzmannApp(parent_window=int(self.winId()))
            self._timer.start()

    def resizeEvent(self, event) -> None:  # noqa: N802 - Qt override name
        super().resizeEvent(event)
        if self._panda is not None and self._panda.win is not None:
            properties = self._panda.win.getProperties()
            properties.setSize(max(1, event.size().width()), max(1, event.size().height()))
            self._panda.win.requestProperties(properties)

    def _step_panda(self) -> None:
        if self._panda is not None:
            self._panda.taskMgr.step()

    def close_panda(self) -> None:
        self._timer.stop()
        if self._panda is not None:
            self._panda.destroy()
            self._panda = None


class SpeedPlot(FigureCanvasQTAgg):
    """Matplotlib speed histogram for particles in the active demo."""

    def __init__(self, parent: QWidget | None = None) -> None:
        self.figure = Figure(figsize=(4.0, 3.0), tight_layout=True)
        self.axes = self.figure.add_subplot(111)
        super().__init__(self.figure)
        self.setParent(parent)
        self.axes.set_title("Particle speed distribution")
        self.axes.set_xlabel("Speed")
        self.axes.set_ylabel("Particles")
        self.axes.grid(axis="y", alpha=0.25)
        self.draw_idle()

    def update_values(self, values: list[float], label: str) -> None:
        self.axes.clear()
        self.axes.set_title(label)
        self.axes.set_xlabel(label)
        self.axes.set_ylabel("Particles")
        self.axes.grid(axis="y", alpha=0.25)
        if values:
            bins = min(12, max(4, math.ceil(math.sqrt(len(values)))))
            self.axes.hist(values, bins=bins, color="#d7474d", edgecolor="#762b30")
        else:
            self.axes.text(0.5, 0.5, "No particles", ha="center", va="center")
        self.draw_idle()


class BoltzmannMainWindow(QMainWindow):
    """First desktop shell around the existing Panda3D physics demos."""

    def __init__(self) -> None:
        super().__init__()
        self.setWindowTitle("Boltzmann 3D")
        self.resize(1200, 760)

        self.viewport = PandaViewport()
        self.speed_plot = SpeedPlot()
        self.particle_combo = QComboBox()
        self.particle_combo.addItems(("All particles", "Red", "Blue"))
        self.plot_combo = QComboBox()
        self.plot_combo.addItems(("Instantaneous speed", "X velocity", "Y velocity", "Z velocity"))
        self.dimension_combo = QComboBox()
        self.dimension_combo.addItems(_DIMENSIONS)
        self.dimension_combo.setCurrentText("2D")
        self.scenario_combo = QComboBox()
        self.pause_button = QPushButton("Pause")
        self.reset_button = QPushButton("Reset")
        self.time_label = QLabel("Simulation time: 0.000")

        self._build_menus()
        self._build_layout()
        self.dimension_combo.currentTextChanged.connect(self._change_dimension)
        self.scenario_combo.currentTextChanged.connect(self._change_scenario)
        self.particle_combo.currentTextChanged.connect(self._refresh_readouts)
        self.plot_combo.currentTextChanged.connect(self._refresh_readouts)
        self.pause_button.clicked.connect(self._toggle_pause)
        self.reset_button.clicked.connect(self._reset_scene)

        self._refresh_scenarios()
        self._ui_timer = QTimer(self)
        self._ui_timer.setInterval(300)
        self._ui_timer.timeout.connect(self._refresh_readouts)
        self._ui_timer.start()

    def _build_menus(self) -> None:
        file_menu = self.menuBar().addMenu("&File")
        exit_action = QAction("E&xit", self)
        exit_action.setShortcut("Ctrl+Q")
        exit_action.triggered.connect(self.close)
        file_menu.addAction(exit_action)

        simulation_menu = self.menuBar().addMenu("&Simulation")
        self.pause_action = QAction("&Pause", self)
        self.pause_action.setShortcut("Space")
        self.pause_action.triggered.connect(self._toggle_pause)
        simulation_menu.addAction(self.pause_action)
        reset_action = QAction("&Reset", self)
        reset_action.setShortcut("Ctrl+R")
        reset_action.triggered.connect(self._reset_scene)
        simulation_menu.addAction(reset_action)

        view_menu = self.menuBar().addMenu("&View")
        for label, dimension in _DIMENSIONS.items():
            action = QAction(label, self)
            action.setShortcut(f"Ctrl+{dimension}")
            action.triggered.connect(
                lambda checked=False, name=label: self.dimension_combo.setCurrentText(name)
            )
            view_menu.addAction(action)

        help_menu = self.menuBar().addMenu("&Help")
        about_action = QAction("&About", self)
        about_action.triggered.connect(self._show_about)
        help_menu.addAction(about_action)

    def _build_layout(self) -> None:
        central = QWidget()
        outer_layout = QVBoxLayout(central)
        controls = QHBoxLayout()
        controls.addWidget(QLabel("Dimension:"))
        controls.addWidget(self.dimension_combo)
        controls.addSpacing(16)
        controls.addWidget(QLabel("Scenario:"))
        controls.addWidget(self.scenario_combo, 1)
        controls.addWidget(self.pause_button)
        controls.addWidget(self.reset_button)
        outer_layout.addLayout(controls)

        splitter = QSplitter()
        sidebar = QWidget()
        sidebar.setMinimumWidth(245)
        sidebar.setMaximumWidth(330)
        graph_layout = QVBoxLayout(sidebar)
        graph_layout.addWidget(QLabel("Particles:"))
        graph_layout.addWidget(self.particle_combo)
        graph_layout.addSpacing(8)
        graph_layout.addWidget(QLabel("Plot:"))
        graph_layout.addWidget(self.plot_combo)
        graph_layout.addWidget(self.speed_plot, 1)
        graph_layout.addWidget(self.time_label)
        splitter.addWidget(sidebar)
        splitter.addWidget(self.viewport)
        splitter.setStretchFactor(0, 0)
        splitter.setStretchFactor(1, 1)
        splitter.setSizes([280, 900])
        outer_layout.addWidget(splitter, 1)
        self.setCentralWidget(central)
        self.statusBar().showMessage("Ready")

    def _refresh_scenarios(self) -> None:
        dimension = _DIMENSIONS[self.dimension_combo.currentText()]
        previous = self.scenario_combo.currentText()
        labels = tuple(_SCENARIOS)
        if dimension == 1:
            labels = labels[:2]
        self.scenario_combo.blockSignals(True)
        self.scenario_combo.clear()
        self.scenario_combo.addItems(labels)
        if previous in labels:
            self.scenario_combo.setCurrentText(previous)
        self.scenario_combo.blockSignals(False)

    def _change_dimension(self, label: str) -> None:
        if self.viewport.panda is None:
            return
        self._refresh_scenarios()
        dimension = _DIMENSIONS[label]
        self.viewport.panda.switch_dimension(dimension)
        self._apply_scenario()
        self._set_paused(False)

    def _change_scenario(self, _label: str) -> None:
        self._apply_scenario()

    def _apply_scenario(self) -> None:
        panda = self.viewport.panda
        if panda is None or panda.scene is None or not self.scenario_combo.currentText():
            return
        scenario = _SCENARIOS[self.scenario_combo.currentText()]
        if scenario in ("forward_reaction", "reverse_reaction") and panda.active_dimension == 1:
            return
        panda.scene._reset_simulation(scenario)
        self._set_paused(False)

    def _set_paused(self, paused: bool) -> None:
        panda = self.viewport.panda
        if panda is not None and panda.scene is not None:
            panda.scene.paused = paused
        label = "Resume" if paused else "Pause"
        self.pause_button.setText(label)
        self.pause_action.setText(label)
        self.statusBar().showMessage("Paused" if paused else "Running")

    def _toggle_pause(self) -> None:
        panda = self.viewport.panda
        if panda is not None and panda.scene is not None:
            self._set_paused(not panda.scene.paused)

    def _reset_scene(self) -> None:
        self._apply_scenario()

    def _refresh_readouts(self) -> None:
        panda = self.viewport.panda
        if panda is None or panda.scene is None:
            return
        simulation = panda.scene.simulation
        selected_species = self.particle_combo.currentText().lower()
        particles = [
            particle
            for particle in simulation.particles
            if selected_species == "all particles" or particle.species == selected_species
        ]
        plot = self.plot_combo.currentText()
        if plot == "Instantaneous speed":
            values = [
                math.sqrt(sum(component * component for component in particle.velocity))
                for particle in particles
            ]
            axis_label = "Speed"
        else:
            axis = {"X velocity": 0, "Y velocity": 1, "Z velocity": 2}[plot]
            values = [
                particle.velocity[axis]
                for particle in particles
                if len(particle.velocity) > axis
            ]
            axis_label = plot
        self.speed_plot.update_values(values, axis_label)
        self.time_label.setText(
            f"Particles: {len(particles)}\nSimulation time: {simulation.current_time:.3f}"
        )

    def _show_about(self) -> None:
        QMessageBox.about(
            self,
            "About Boltzmann 3D",
            "Boltzmann 3D\nPython desktop prototype using PySide6, Panda3D, and Matplotlib.",
        )

    def closeEvent(self, event) -> None:  # noqa: N802 - Qt override name
        self._ui_timer.stop()
        self.viewport.close_panda()
        super().closeEvent(event)


def main() -> None:
    """Start the PySide6 desktop application."""
    application = QApplication(sys.argv)
    application.setApplicationName("Boltzmann 3D")
    window = BoltzmannMainWindow()
    window.show()
    sys.exit(application.exec())


if __name__ == "__main__":
    main()
