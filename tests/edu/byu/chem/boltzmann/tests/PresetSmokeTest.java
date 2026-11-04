package edu.byu.chem.boltzmann.tests;

import edu.byu.chem.boltzmann.model.io.Load;
import edu.byu.chem.boltzmann.model.physics.Physics;
import edu.byu.chem.boltzmann.resources.SimulationPresets;
import edu.byu.chem.boltzmann.utils.data.StatSettingsInfo;
import java.util.Locale;

/** Load every shipped legacy preset and advance its actual physics. */
public final class PresetSmokeTest {
    public static void main(String[] args) throws Exception {
        Locale original = Locale.getDefault();
        try {
            for (Locale locale : new Locale[] {Locale.US, Locale.GERMANY}) {
                Locale.setDefault(locale);
                for (var preset : SimulationPresets.ALL) {
                    var info = new Load(null).loadFile(preset.source());
                    if (info == null || info.dimension != preset.dimensions() || info.totalNumParticles <= 0)
                        throw new AssertionError("Invalid preset: " + preset.filename());
                    var physics = new Physics(info, new StatSettingsInfo());
                    physics.advanceToNextFrame();
                    physics.advanceToNextFrame();
                    var frame = physics.getCurrentFrame();
                    if (!(frame.endTime > 0) || frame.getParticles().size() != info.totalNumParticles)
                        throw new AssertionError("Preset failed to advance: " + preset.filename());
                    for (var state : frame.getParticleStates().values()) {
                        if (!Double.isFinite(state.x) || !Double.isFinite(state.y) || !Double.isFinite(state.z))
                            throw new AssertionError("Invalid positions: " + preset.filename());
                    }
                    System.out.println("Loaded " + preset.filename() + " (" + info.totalNumParticles + " particles)");
                }
            }
        } finally {
            Locale.setDefault(original);
        }
        System.out.println("PASS: all " + SimulationPresets.ALL.size() + " presets load and run in US/German locales");
    }
}
