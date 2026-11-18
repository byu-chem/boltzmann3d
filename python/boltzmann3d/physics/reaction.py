"""Reaction settings for collision-triggered two-particle reactions."""

from dataclasses import dataclass
import math


@dataclass(frozen=True)
class ReactionRule:
    """Define Java-style ``A + A <-> B + B`` reaction thresholds.

    Activation energies are in kJ/mol. Java's collision code checks the
    forward and reverse thresholds and suppresses the reverse reaction when
    requested. It currently does not consult its forward-suppression flag;
    the Python conversion follows that behavior for parity.
    """

    forward_activation_energy: float
    reverse_activation_energy: float
    reactant_species: str = "red"
    product_species: str = "blue"
    suppress_forward: bool = False
    suppress_reverse: bool = False

    def __post_init__(self) -> None:
        object.__setattr__(
            self, "forward_activation_energy", float(self.forward_activation_energy)
        )
        object.__setattr__(
            self, "reverse_activation_energy", float(self.reverse_activation_energy)
        )
        if (
            not math.isfinite(self.forward_activation_energy)
            or self.forward_activation_energy < 0.0
        ):
            raise ValueError("Forward activation energy must be finite and nonnegative")
        if (
            not math.isfinite(self.reverse_activation_energy)
            or self.reverse_activation_energy < 0.0
        ):
            raise ValueError("Reverse activation energy must be finite and nonnegative")
        if not self.reactant_species or not self.product_species:
            raise ValueError("Reaction species names must be nonempty")
        if self.reactant_species == self.product_species:
            raise ValueError("Reactant and product species must differ")
        if not isinstance(self.suppress_forward, bool) or not isinstance(
            self.suppress_reverse, bool
        ):
            raise ValueError("Reaction suppression settings must be booleans")
