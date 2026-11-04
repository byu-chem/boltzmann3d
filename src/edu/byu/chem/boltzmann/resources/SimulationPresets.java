package edu.byu.chem.boltzmann.resources;

import java.net.URL;
import java.util.List;
import java.util.Objects;

/** Original teaching simulations, available even when running from an installed JAR. */
public final class SimulationPresets {
    private SimulationPresets() {}

    public record Preset(String filename, String label, int dimensions) {
        public URL source() {
            return Objects.requireNonNull(SimulationPresets.class.getResource(
                    "simsettingsfiles/presets/" + filename), "Missing preset: " + filename);
        }
    }

    public static final List<Preset> ALL = List.of(
        new Preset("2Dbrownian.set", "Brownian motion", 2),
        new Preset("2Ddiffuse.set", "Diffusion", 2),
        new Preset("2Deffuse.set", "Effusion", 2),
        new Preset("2Dentropy.set", "Entropy", 2),
        new Preset("2Dequal.set", "Equal masses", 2),
        new Preset("2Dequil.set", "Equilibration", 2),
        new Preset("2Dhotcold.set", "Hot and cold", 2),
        new Preset("2Dhyd100.set", "Hydrogen, 100 K", 2),
        new Preset("2Dhyd1000.set", "Hydrogen, 1000 K", 2),
        new Preset("2Dliquid.set", "Liquid", 2),
        new Preset("2Dmixture.set", "Hydrogen / oxygen mixture", 2),
        new Preset("2Doxy100.set", "Oxygen, 100 K", 2),
        new Preset("2Doxy1000.set", "Oxygen, 1000 K", 2),
        new Preset("2Dpresnum.set", "Pressure and particle number", 2),
        new Preset("3Dbrownian.set", "Brownian motion", 3),
        new Preset("3Ddiffuse.set", "Diffusion", 3),
        new Preset("3Deffuse.set", "Effusion", 3),
        new Preset("3Dentropy.set", "Entropy", 3),
        new Preset("3Dequil.set", "Equilibration", 3),
        new Preset("3Dhotcold.set", "Hot and cold", 3),
        new Preset("3Dhyd100.set", "Hydrogen, 100 K", 3),
        new Preset("3Dhyd1000.set", "Hydrogen, 1000 K", 3),
        new Preset("3Dmixture.set", "Hydrogen / oxygen mixture", 3),
        new Preset("3Doxy100.set", "Oxygen, 100 K", 3),
        new Preset("3Doxy1000.set", "Oxygen, 1000 K", 3),
        new Preset("3Dpresnum.set", "Pressure and particle number", 3),
        new Preset("default.set", "Original default", 2));
}
