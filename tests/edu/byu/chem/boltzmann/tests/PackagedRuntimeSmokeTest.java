package edu.byu.chem.boltzmann.tests;

import com.jogamp.common.os.Platform;
import java.nio.file.Files;
import java.nio.file.Path;

/** Executes with the shipped runtime and application JARs, not the build JDK. */
public final class PackagedRuntimeSmokeTest {
    public static void main(String[] args) throws Exception {
        Path expected = Path.of(args[0]);
        Path actual = Path.of(System.getProperty("java.home"));
        if (!Files.isSameFile(expected, actual)) {
            throw new AssertionError("Tests are not using the packaged runtime: " + actual);
        }
        int expectedVersion = Integer.parseInt(args[1]);
        if (Runtime.version().feature() != expectedVersion) {
            throw new AssertionError("Expected bundled Java " + expectedVersion + ", got " + Runtime.version());
        }
        // Forces extraction and loading of GlueGen's architecture-specific JNI library.
        Platform.initSingleton();
        System.out.println("Bundled Java: " + Runtime.version() + " (" + System.getProperty("os.arch") + ")");
        System.out.println("Bundled runtime: " + actual);
        ModernizationSmokeTest.main(new String[0]);
        PresetSmokeTest.main(new String[0]);
        Path application = Path.of(edu.byu.chem.boltzmann.resources.SimulationPresets.class
                .getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
        for (var preset : edu.byu.chem.boltzmann.resources.SimulationPresets.ALL) {
            Path file = application.resolve("presets").resolve(preset.filename());
            try (var embedded = preset.source().openStream()) {
                if (!java.util.Arrays.equals(embedded.readAllBytes(), Files.readAllBytes(file)))
                    throw new AssertionError("Packaged preset differs from embedded resource: " + file);
            }
            if (new edu.byu.chem.boltzmann.model.io.Load(null).loadFile(file.toFile()) == null)
                throw new AssertionError("Packaged preset did not load: " + file);
        }
        System.out.println("PASS: packaged runtime, application resources and native library loading");
    }
}
