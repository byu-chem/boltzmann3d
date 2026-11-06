package edu.byu.chem.boltzmann.controller;

import edu.byu.chem.boltzmann.model.io.Load;
import edu.byu.chem.boltzmann.model.physics.FrameInfo;
import edu.byu.chem.boltzmann.resources.SimulationPresets;
import edu.byu.chem.boltzmann.utils.data.SimulationInfo;
import edu.byu.chem.boltzmann.view.maingui.MainGuiController;
import edu.byu.chem.boltzmann.view.maingui.MainGuiView;
import edu.byu.chem.boltzmann.view.render.lwjgl.GLPanel;
import edu.byu.chem.boltzmann.view.simulationsettings.SimSettingsView;
import edu.byu.chem.boltzmann.view.piston.PistonView;
import edu.byu.chem.boltzmann.view.heatreservoir.HeatResView;
import edu.byu.chem.boltzmann.view.plotsettings.PlotSettingsView;
import edu.byu.chem.boltzmann.view.statisticsettings.StatSettingsView;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Replaces running/paused simulations, including an in-flight synchronous AWT render. */
public final class ThreadLifecycleSmokeTest {
    public static void main(String[] args) throws Exception {
        boolean render = args.length > 0;
        AtomicReference<Throwable> failure = new AtomicReference<>();
        var oldHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> failure.compareAndSet(null, error));
        TestView view = new TestView();
        TestController[] holder = new TestController[1];
        SwingUtilities.invokeAndWait(() -> {
            if (render) {
                view.panel = new GLPanel(null);
                view.window = new JFrame("Preset switching check");
                view.window.add(view.panel);
                view.window.setSize(640, 480);
                view.window.setVisible(true);
            }
            holder[0] = new TestController(view);
        });
        TestController controller = holder[0];
        try {
            // Hold an AWT render open while loading a different preset.
            controller.setSimulationInfo(load(0));
            await(view.renderEntered, "first render");
            awaitSettingsWorker();
            SimulationInfo next = load(14);
            controller.setSimulationInfo(next);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (!view.interrupted && !stopping() && System.nanoTime() < deadline) Thread.sleep(5);
            Thread.sleep(100); // Keep AWT occupied while shutdown wakes its workers.
            if (view.interrupted) throw new AssertionError("Shutdown interrupted an in-flight AWT render");
            if (!stopping()) throw new AssertionError("Replacement did not start stopping threads");
            view.releaseRender.countDown();
            waitForFrame(view, next, failure);
            for (int i = 0; i < SimulationPresets.ALL.size(); i++) {
                controller.setPaused(i % 2 == 0);
                next = load(i);
                controller.setSimulationInfo(next);
                controller.advanceOneFrame();
                waitForFrame(view, next, failure);
            }
            controller.setPaused(true);
        } finally {
            view.releaseRender.countDown();
            controller.exitBoltzmann();
            SwingUtilities.invokeAndWait(() -> { if (view.window != null) view.window.dispose(); });
            Thread.setDefaultUncaughtExceptionHandler(oldHandler);
        }
        if (failure.get() != null) throw new AssertionError("Worker failed", failure.get());
        System.out.println("PASS: live preset replacement, paused replacement and in-flight "
                + (render ? "OpenGL" : "AWT") + " render shutdown");
    }

    private static boolean stopping() throws Exception {
        var running = ThreadController.class.getDeclaredField("runSimulationThreads");
        running.setAccessible(true);
        return !running.getBoolean(ThreadController.getInstance());
    }
    private static SimulationInfo load(int index) throws Exception {
        return new Load(null).loadFile(SimulationPresets.ALL.get(index).source());
    }
    private static void await(CountDownLatch latch, String action) throws Exception {
        if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Timed out: " + action);
    }
    private static void waitForFrame(TestView view, SimulationInfo expected,
            AtomicReference<Throwable> failure) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (view.displayed != expected && failure.get() == null && System.nanoTime() < deadline)
            Thread.sleep(5);
        if (failure.get() != null) throw new AssertionError("Worker failed", failure.get());
        if (view.displayed != expected) throw new AssertionError("Preset never reached the display");
        awaitSettingsWorker();
    }

    private static void awaitSettingsWorker() throws Exception {
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getName().equals("Set Simulation Info")) {
                thread.join(5000);
                if (thread.isAlive()) throw new AssertionError("Settings worker did not finish");
            }
        }
    }

    private static final class TestView implements MainGuiView {
        MainGuiController controller;
        volatile SimulationInfo current, displayed;
        volatile boolean interrupted;
        boolean first = true;
        final CountDownLatch renderEntered = new CountDownLatch(1);
        final CountDownLatch releaseRender = new CountDownLatch(1);
        GLPanel panel;
        JFrame window;
        public void attachController(MainGuiController c) { controller = c; }
        public MainGuiController getController() { return controller; }
        public void setSimulationInfo(SimulationInfo info) {
            current = info;
            if (panel != null) panel.setSimulationInfo(info);
        }
        public void setNextFrameToDisplay(FrameInfo frame) {
            if (panel != null) panel.setNextFrameToDisplay(frame);
        }
        public void displayFrame() {
            if (first) {
                first = false;
                try {
                    SwingUtilities.invokeAndWait(() -> {
                        renderEntered.countDown();
                        try { await(releaseRender, "render release"); }
                        catch (Exception e) { throw new RuntimeException(e); }
                    });
                } catch (Exception e) {
                    interrupted = true;
                    throw new RuntimeException(e);
                }
            }
            if (panel != null) panel.drawFrame();
            displayed = current;
        }
        public void setPaused(boolean paused) {}
        public void displayStatistics() {}
        public void showLicenses() {}
    }

    private static final class TestController extends Controller {
        TestController(TestView view) {
            super(view, (SimSettingsView) Proxy.newProxyInstance(SimSettingsView.class.getClassLoader(),
                    new Class<?>[] {SimSettingsView.class}, (p, m, a) -> null),
                    PistonView.NO_VIEW, StatSettingsView.NO_VIEW, HeatResView.NO_VIEW, PlotSettingsView.NO_VIEW);
        }
        public void showSimSettings() {}
        public void hideSimSettings() {}
        public void showStatSettings() {}
        public void hideStatSettings() {}
        public void showPistonControls() {}
        public void hidePistonControls() {}
        public void showReservoirControls() {}
        public void hideReservoirControls() {}
        public void showPlotSettings() {}
        public void hidePlotSettings() {}
        protected void finishBoltzmannExit() {}
    }
}
