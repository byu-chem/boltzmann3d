package edu.byu.chem.boltzmann.model.statistics;

import edu.byu.chem.boltzmann.utils.data.SimulationInfo.ArenaType;
import java.util.Locale;

/** Java reference values for effective arena volumes used by real-gas formulas. */
public final class RealGasEffectiveVolumeHarness {

    private RealGasEffectiveVolumeHarness() {
    }

    public static void main(String[] args) {
        double radius = 1.25e-9;
        double dividerRadius = 1.0e-9;
        print("1D reflecting", 1, ArenaType.REFLECTING_BOUNDARIES, radius, dividerRadius);
        print("1D periodic", 1, ArenaType.PERIODIC_BOUNDARIES, radius, dividerRadius);
        print("2D reflecting", 2, ArenaType.REFLECTING_BOUNDARIES, radius, dividerRadius);
        print("2D periodic", 2, ArenaType.PERIODIC_BOUNDARIES, radius, dividerRadius);
        print("3D reflecting", 3, ArenaType.REFLECTING_BOUNDARIES, radius, dividerRadius);
        print("3D divided", 3, ArenaType.DIVIDED_ARENA, radius, dividerRadius);
        print("3D divided with hole", 3, ArenaType.DIVIDED_ARENA_WITH_HOLE, radius, dividerRadius);
    }

    private static void print(
            String label, int dimension, ArenaType arenaType, double radius, double dividerRadius) {
        double value = Formulas.effectiveVolume(
                70e-9,
                dimension > 1 ? 50e-9 : -1.0,
                dimension > 2 ? 40e-9 : -1.0,
                radius,
                dimension,
                arenaType,
                dividerRadius);
        System.out.printf(Locale.ROOT, "Effective volume %s: %.15g%n", label, value);
    }
}
