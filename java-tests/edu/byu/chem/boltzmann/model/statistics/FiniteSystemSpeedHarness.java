package edu.byu.chem.boltzmann.model.statistics;

import java.util.Locale;

/** Standalone output for comparing Java's finite-system average-speed formulas. */
public final class FiniteSystemSpeedHarness {

    private FiniteSystemSpeedHarness() {
    }

    public static void main(String[] args) {
        double temperature = 300.0;
        double particleMassKg = 2.0e-26;
        double totalMassKg = 1.0e-25;
        int totalParticles = 6;

        for (int dimension = 1; dimension <= 3; dimension++) {
            double baseline = Formulas.averageVelocity(
                    temperature, particleMassKg, dimension);
            double finiteNonperiodic = Formulas.correctedAverageVelocity(
                    temperature,
                    particleMassKg,
                    dimension,
                    false,
                    totalParticles,
                    totalMassKg);
            double finitePeriodic = Formulas.correctedAverageVelocity(
                    temperature,
                    particleMassKg,
                    dimension,
                    true,
                    totalParticles,
                    totalMassKg);
            double rmsBaseline = Formulas.rmsVelocity(
                    temperature, particleMassKg, dimension);
            double rmsNonperiodic = Formulas.correctedRMSVel(
                    temperature, particleMassKg, dimension, false);
            boolean periodicRmsUnsupported = false;
            try {
                Formulas.correctedRMSVel(
                        temperature, particleMassKg, dimension, true);
            } catch (UnsupportedOperationException expected) {
                periodicRmsUnsupported = true;
            }
            if (rmsBaseline != rmsNonperiodic || !periodicRmsUnsupported) {
                throw new AssertionError(
                        "Corrected RMS-speed behavior changed in " + dimension + "D");
            }
            double correctedVelocityWidth = Formulas.correctedVelocityWidth(
                    temperature,
                    particleMassKg,
                    dimension,
                    false,
                    totalParticles,
                    totalMassKg);
            boolean periodicVelocityWidthUnsupported = false;
            try {
                Formulas.correctedVelocityWidth(
                        temperature,
                        particleMassKg,
                        dimension,
                        true,
                        totalParticles,
                        totalMassKg);
            } catch (UnsupportedOperationException expected) {
                periodicVelocityWidthUnsupported = true;
            }
            if (!periodicVelocityWidthUnsupported) {
                throw new AssertionError(
                        "Periodic corrected velocity width should remain unsupported in "
                        + dimension + "D");
            }
            System.out.printf(
                    Locale.ROOT,
                    "Finite-system average speed %dD: baseline=%.15g m/s "
                    + "nonperiodic=%.15g m/s periodic=%.15g m/s%n",
                    dimension,
                    baseline,
                    finiteNonperiodic,
                    finitePeriodic);
            System.out.printf(
                    Locale.ROOT,
                    "Finite-system RMS speed %dD: baseline=%.15g m/s "
                    + "nonperiodic=%.15g m/s periodic=unsupported%n",
                    dimension,
                    rmsBaseline,
                    rmsNonperiodic);
            System.out.printf(
                    Locale.ROOT,
                    "Finite-system corrected velocity width %dD: "
                    + "nonperiodic=%.15g m/s periodic=unsupported%n",
                    dimension,
                    correctedVelocityWidth);
        }
        System.out.println("Finite-system RMS-speed behavior matches Java in 1D/2D/3D");
    }
}
