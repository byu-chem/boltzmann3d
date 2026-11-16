"""Physics model for the Boltzmann 3D application."""

from .particle import NO_COLLISION_TIME, Particle
from .simulation import Simulation, SimulationEvent

__all__ = ["NO_COLLISION_TIME", "Particle", "Simulation", "SimulationEvent"]
