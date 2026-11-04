package edu.byu.chem.boltzmann.tests;

import edu.byu.chem.boltzmann.model.io.Load;
import edu.byu.chem.boltzmann.model.physics.FrameInfo;
import edu.byu.chem.boltzmann.model.physics.PartState;
import edu.byu.chem.boltzmann.model.physics.Particle;
import edu.byu.chem.boltzmann.model.physics.Physics;
import edu.byu.chem.boltzmann.resources.ResourceLoader;
import edu.byu.chem.boltzmann.utils.data.SimulationInfo;
import edu.byu.chem.boltzmann.utils.data.StatSettingsInfo;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Behavioral checks that do not create an OpenGL context or Swing window. */
public final class ModernizationSmokeTest {
    public static void main(String[] args) throws Exception {
        Locale original = Locale.getDefault();
        try {
            for (Locale locale : new Locale[] {Locale.US, Locale.GERMANY}) {
                Locale.setDefault(locale);
                URL source = ResourceLoader.getDefaultSetFileURL();
                SimulationInfo fromResource = new Load(null).loadFile(source);
                checkSimulation(fromResource);
                // Exercise both accepted extensions, including a path with spaces.
                Path directory = Files.createTempDirectory("Boltzmann settings ");
                try {
                    for (String suffix : new String[] {".set", ".set2"}) {
                        Path settings = directory.resolve("default settings" + suffix);
                        try (var input = source.openStream()) {
                            Files.copy(input, settings);
                        }
                        SimulationInfo fromFile = new Load(null).loadFile(settings.toFile());
                        require(fromFile.totalNumParticles == fromResource.totalNumParticles,
                                "Particle count changed when loading a file");
                        require(fromFile.dimension == fromResource.dimension,
                                "Dimension changed when loading a file");
                        checkSimulation(fromFile);
                        SimulationInfo fromURL = new Load(null).loadFile(settings.toUri().toURL());
                        require(fromURL.totalNumParticles == fromResource.totalNumParticles,
                                "Particle count changed when loading a file URL");
                        // Windows rejects deletion if either loader leaves a stream open.
                        Files.delete(settings);
                    }
                } finally {
                    try (var files = Files.list(directory)) {
                        for (Path file : files.toList()) {
                            Files.deleteIfExists(file);
                        }
                    }
                    Files.delete(directory);
                }
            }
        } finally {
            Locale.setDefault(original);
        }
        System.out.println("PASS: settings URL/files (.set/.set2), US/German locales, physics and energy conservation");
    }

    private static void checkSimulation(SimulationInfo info) {
        require(info != null && info.totalNumParticles > 0, "Default settings did not load");
        Physics physics = new Physics(info, new StatSettingsInfo());
        double initialEnergy = energy(physics);
        require(Double.isFinite(initialEnergy) && initialEnergy > 0, "Invalid initial energy");
        physics.advanceToNextFrame();
        double previousTime = physics.getCurrentFrame().endTime;
        for (int step = 0; step < 20; step++) {
            physics.advanceToNextFrame();
            FrameInfo frame = physics.getCurrentFrame();
            require(frame.endTime > previousTime, "Simulation time did not advance");
            previousTime = frame.endTime;
            require(frame.getParticles().size() == info.totalNumParticles, "Particle count changed");
            for (PartState state : frame.getParticleStates().values()) {
                require(Double.isFinite(state.x) && Double.isFinite(state.y) && Double.isFinite(state.z),
                        "Non-finite particle position");
            }
            require(Math.abs(energy(physics) - initialEnergy) / initialEnergy < 1e-9,
                    "Reflecting-wall simulation did not conserve kinetic energy");
        }
    }

    private static double energy(Physics physics) {
        double total = 0;
        for (Particle particle : physics.getParticles()) {
            total += particle.getKE();
        }
        return total;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
