package edu.byu.chem.boltzmann.model.statistics;

import edu.byu.chem.boltzmann.utils.data.ParticleType;
import edu.byu.chem.boltzmann.utils.data.SimulationInfo;
import edu.byu.chem.boltzmann.utils.data.SimulationInfo.ArenaType;
import edu.byu.chem.boltzmann.utils.data.SimulationInfoSupplier;
import java.awt.Color;
import java.lang.reflect.Proxy;
import java.util.Locale;

/** Java reference values for the finite-arena real-gas B2 correction. */
public final class RealGasB2CorrectionHarness {

    private RealGasB2CorrectionHarness() {
    }

    public static void main(String[] args) {
        ParticleType first = new ParticleType(1.0, 0.5e-9, Color.RED, "Harness A");
        ParticleType second = new ParticleType(1.0, 1.25e-9, Color.BLUE, "Harness B");

        print("1D reflecting", info(ArenaType.REFLECTING_BOUNDARIES, 1, 70e-9), first, second);
        print("2D reflecting", info(ArenaType.REFLECTING_BOUNDARIES, 2, 70e-9, 50e-9), first, second);
        print("3D reflecting", info(ArenaType.REFLECTING_BOUNDARIES, 3, 70e-9, 50e-9, 40e-9), first, second);
        print("2D periodic", info(ArenaType.PERIODIC_BOUNDARIES, 2, 70e-9, 50e-9), first, second);
        print("3D divided", info(ArenaType.DIVIDED_ARENA, 3, 70e-9, 50e-9, 40e-9), first, second);
        print("3D divided with hole", info(ArenaType.DIVIDED_ARENA_WITH_HOLE, 3, 70e-9, 50e-9, 40e-9), first, second);
    }

    private static SimulationInfo info(ArenaType arenaType, int dimension, double... sizes) {
        SimulationInfoSupplier supplier = (SimulationInfoSupplier) Proxy.newProxyInstance(
                SimulationInfoSupplier.class.getClassLoader(),
                new Class<?>[] {SimulationInfoSupplier.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getArenaType": return arenaType;
                        case "getDimension": return dimension;
                        case "getArenaXSize": return sizes[0];
                        case "getArenaYSize": return dimension > 1 ? sizes[1] : -1.0;
                        case "getArenaZSize": return dimension > 2 ? sizes[2] : -1.0;
                        case "getHoleDiameter": return -1.0;
                        case "getNumberOfParticleTypes": return 0;
                        case "getNumberOfReactionRelationships": return 0;
                        case "getRecordedStatistics": return new edu.byu.chem.boltzmann.model.statistics.interfaces.StatisticID[0];
                        case "isMaxwellDemonModeSelected":
                        case "isReactionModeSelected":
                        case "areAttractiveInteractionsAllowed":
                        case "includeAttractiveWall":
                        case "includeHeatReservoir": return false;
                        case "getWallWellWidth":
                        case "getWallWellDepth":
                        case "getInitialReservoirTemperature":
                        case "getRadiusOfInteractionMultiplier": return 0.0;
                        default:
                            throw new UnsupportedOperationException(
                                    "Unexpected fixture call: " + method.getName());
                    }
                });
        return new SimulationInfo(supplier);
    }

    private static void print(
            String label, SimulationInfo info, ParticleType first, ParticleType second) {
        System.out.printf(
                Locale.ROOT,
                "Real-gas B2 correction %s: %.15g%n",
                label,
                Formulas.realGasB2Correction(info, first, second));
    }
}
