package edu.byu.chem.boltzmann.model.statistics;

import edu.byu.chem.boltzmann.model.statistics.interfaces.StatisticID;
import edu.byu.chem.boltzmann.utils.data.ParticleType;
import edu.byu.chem.boltzmann.utils.data.SimulationInfo;
import edu.byu.chem.boltzmann.utils.data.SimulationInfo.ArenaType;
import edu.byu.chem.boltzmann.utils.data.SimulationInfoSupplier;
import edu.byu.chem.boltzmann.utils.Units.PressureUnit;
import java.awt.Color;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Java reference values for real-gas X with multiple particle types. */
public final class RealGasXHarness {

    private static final ParticleType[] TYPES = {
        new ParticleType(20.0, 0.5e-9, Color.RED, "Harness A"),
        new ParticleType(40.0, 1.25e-9, Color.BLUE, "Harness B")
    };
    private static final int[] COUNTS = {3, 2};

    private RealGasXHarness() {
    }

    public static void main(String[] args) {
        print("1D reflecting", info(ArenaType.REFLECTING_BOUNDARIES, 1, 70e-9));
        print("1D periodic", info(ArenaType.PERIODIC_BOUNDARIES, 1, 70e-9));
        print("2D reflecting", info(ArenaType.REFLECTING_BOUNDARIES, 2, 70e-9, 50e-9));
        print("3D reflecting", info(ArenaType.REFLECTING_BOUNDARIES, 3, 70e-9, 50e-9, 40e-9));
        print("2D periodic", info(ArenaType.PERIODIC_BOUNDARIES, 2, 70e-9, 50e-9));
        print("3D divided", info(ArenaType.DIVIDED_ARENA, 3, 70e-9, 50e-9, 40e-9));
        print("3D divided with hole", info(ArenaType.DIVIDED_ARENA_WITH_HOLE, 3, 70e-9, 50e-9, 40e-9));
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
                        case "getNumberOfParticleTypes": return TYPES.length;
                        case "getParticleType": return TYPES[(Integer) args[0]];
                        case "getNumberOfParticles": return COUNTS[(Integer) args[0]];
                        case "getParticlePosition": return new double[dimension];
                        case "getParticleVelocity": return new double[] {500.0, 0.0, 0.0};
                        case "getNumberOfReactionRelationships": return 0;
                        case "getRecordedStatistics": return new StatisticID[0];
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

    private static void print(String label, SimulationInfo info) {
        System.out.printf(Locale.ROOT, "Collision prediction temperature %s: %.15g K%n", label, info.initialTemperature);
        for (ParticleType type : TYPES) {
            System.out.printf(
                    Locale.ROOT,
                    "Real-gas X %s target=%s: %.15g%n",
                    label,
                    type.displayName,
                    Formulas.realGasX(info, type));
            System.out.printf(
                    Locale.ROOT,
                    "Collision rate %s target=%s: uncorrected=%.15g corrected=%.15g%n",
                    label,
                    type.displayName,
                    Formulas.predictCollisionRate(info, type, false),
                    Formulas.predictCollisionRate(info, type, true));
        }
        Set<ParticleType> selectedTypes = new HashSet<ParticleType>(Arrays.asList(TYPES));
        System.out.printf(
                Locale.ROOT,
                "Average collision rate %s: uncorrected=%.15g corrected=%.15g%n",
                label,
                Formulas.predictCollisionRate(info, selectedTypes, false),
                Formulas.predictCollisionRate(info, selectedTypes, true));
        Pressure pressure = new Pressure(info, selectedTypes);
        PressureUnit unit = PressureUnit.getBaseUnit(info.dimension);
        pressure.setRealGasCorrections(false);
        double uncorrectedPressure = pressure.getPredictionForAverage(unit);
        pressure.setRealGasCorrections(true);
        double correctedPressure = pressure.getPredictionForAverage(unit);
        System.out.printf(
                Locale.ROOT,
                "Pressure prediction %s: uncorrected=%.15g corrected=%.15g%n",
                label,
                uncorrectedPressure,
                correctedPressure);
    }
}
