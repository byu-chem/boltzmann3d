package edu.byu.chem.boltzmann.tests;

import edu.byu.chem.boltzmann.fullapplication.view.simulationsettings.SimSettingsFullView;
import edu.byu.chem.boltzmann.fullapplication.view.simulationsettings.components.ArenaSettingsTab;
import edu.byu.chem.boltzmann.model.io.Load;
import edu.byu.chem.boltzmann.model.physics.Physics;
import edu.byu.chem.boltzmann.resources.ResourceLoader;
import edu.byu.chem.boltzmann.utils.data.ParticleType;
import edu.byu.chem.boltzmann.utils.data.SimulationInfo;
import edu.byu.chem.boltzmann.utils.data.StatSettingsInfo;
import edu.byu.chem.boltzmann.view.simulationsettings.SimSettingsController;
import edu.byu.chem.boltzmann.view.simulationsettings.components.TypeDescriptorImpl;
import java.awt.Component;
import java.awt.Container;
import java.text.ParseException;
import java.util.Locale;
import javax.swing.JButton;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;

/** Exercises typed Swing edits all the way through initial conditions and physics. */
public final class ParticleControlsSmokeTest {
    public static void main(String[] args) throws Exception {
        Locale original = Locale.getDefault();
        try {
            for (Locale locale : new Locale[] {Locale.US, Locale.GERMANY}) {
                Locale.setDefault(locale);
                SwingUtilities.invokeAndWait(() -> {
                    try { checkEditor(); } catch (Exception failure) { throw new RuntimeException(failure); }
                });
            }
        } finally { Locale.setDefault(original); }
        System.out.println("PASS: particle add/remove, decimal mass, typed count/radius/temperature, capacity validation and dimension round trips");
    }

    private static void checkEditor() throws Exception {
        SimSettingsFullView view = new SimSettingsFullView();
        SimSettingsController controller = new SimSettingsController(null, view);
        view.attachController(controller);
        SimulationInfo initial = new Load(null).loadFile(ResourceLoader.getDefaultSetFileURL());
        view.setSimulationInfo(initial);
        view.selectParticleSettings();
        TypeDescriptorImpl first = (TypeDescriptorImpl) view.getTypeDescriptors().get(0);
        typed(first, "particleCount", "8");
        typed(first, "particleMass", decimal("1.00794"));
        typed(first, "particleRadius", decimal("0.75"));
        typed(view, "particleTemperature", "425");
        ((JButton) named(view, "addParticleType")).doClick();
        int typeCount = view.getTypeDescriptors().size();
        TypeDescriptorImpl added = (TypeDescriptorImpl) view.getTypeDescriptors().get(typeCount - 1);
        typed(added, "particleCount", "6");
        view.commitParticleChanges();
        SimulationInfo changed = controller.createSimulationInfo();
        ParticleType type = changed.getParticleTypes().get(0);
        require(changed.getNumberOfParticles(type) == 8, "Typed count was ignored");
        close(type.particleMass, 1.00794, "Decimal mass changed");
        close(type.particleRadius * 1e9, .75, "Radius conversion changed");
        close(changed.initialTemperature, 425, "Typed temperature was ignored");
        require(changed.getParticleTypes().size() == typeCount, "Added type missing");
        // The same editor and controller are reused, as in the actual application.
        for (int dimension : new int[] {3, 2, 3, 2}) {
            ArenaSettingsTab arena = find(view, ArenaSettingsTab.class);
            arena.setDimension(SimulationInfo.intToDimension(dimension));
            view.commitParticleChanges();
            SimulationInfo switched = controller.createSimulationInfo();
            close(switched.getParticleTypes().get(0).particleRadius * 1e9, .75, "Radius changed with dimension");
            require(switched.totalNumParticles == changed.totalNumParticles, "Dimension switch changed count");
            Physics physics = new Physics(switched, new StatSettingsInfo());
            physics.advanceToNextFrame();
            view.setSimulationInfo(switched);
            first = (TypeDescriptorImpl) view.getTypeDescriptors().get(0);
            close(first.getTypeDescribed().particleMass, 1.00794, "Reload rounded mass");
        }
        // Invalid text must stay in the editor instead of applying the old value.
        typed(first, "particleRadius", "invalid");
        try { view.commitParticleChanges(); throw new AssertionError("Invalid radius accepted"); }
        catch (ParseException expected) { }
        typed(first, "particleRadius", "99999");
        view.commitParticleChanges();
        try { controller.createSimulationInfo(); throw new AssertionError("Oversized particles silently accepted"); }
        catch (IllegalArgumentException expected) { }
        view.removeTypeDescriptor(first);
        require(view.getTypeDescriptors().size() == typeCount - 1, "Remove failed");
    }

    private static String decimal(String value) {
        return Locale.getDefault().equals(Locale.GERMANY) ? value.replace('.', ',') : value;
    }
    private static void typed(Container parent, String name, String text) {
        JSpinner spinner = (JSpinner) named(parent, name);
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setText(text);
    }
    private static Component named(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container container) {
                Component result = named(container, name);
                if (result != null) return result;
            }
        }
        return null;
    }
    private static <T> T find(Container parent, Class<T> type) {
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container container) {
                T result = find(container, type);
                if (result != null) return result;
            }
        }
        return null;
    }
    private static void close(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < Math.max(1, Math.abs(expected)) * 1e-7,
                message + ": " + actual);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
