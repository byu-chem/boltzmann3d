package edu.byu.chem.boltzmann.tests;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLProfile;
import edu.byu.chem.boltzmann.model.io.Load;
import edu.byu.chem.boltzmann.model.physics.FrameInfo;
import edu.byu.chem.boltzmann.model.physics.PartState;
import edu.byu.chem.boltzmann.model.physics.Particle;
import edu.byu.chem.boltzmann.model.physics.Particle1D;
import edu.byu.chem.boltzmann.model.physics.Particle2D;
import edu.byu.chem.boltzmann.model.physics.Particle3D;
import edu.byu.chem.boltzmann.resources.ResourceLoader;
import edu.byu.chem.boltzmann.utils.data.ParticleType;
import edu.byu.chem.boltzmann.utils.data.SimulationInfo;
import edu.byu.chem.boltzmann.view.maingui.components.GLPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Requires a display and a working OpenGL driver; checks actual framebuffer pixels. */
public final class RenderingSmokeTest {
    public static void main(String[] args) throws Exception {
        GLProfile.initSingleton();
        SwingUtilities.invokeAndWait(() -> {
            JFrame window = new JFrame("Boltzmann3D rendering check");
            try {
                GLPanel panel = new GLPanel(null);
                panel.setPreferredSize(new Dimension(640, 480));
                window.setContentPane(panel);
                window.pack();
                window.setVisible(true);
                // Switching back to 2D also checks that 3D state does not leak.
                for (int dimension : new int[] {2, 3, 1, 2}) {
                    SimulationInfo info = settings(dimension);
                    panel.setSimulationInfo(info);
                    panel.setNextFrameToDisplay(centeredParticle(info));
                    checkParticlePixels(panel, dimension + "D");
                    if (dimension == 3) {
                        panel.getActionMap().get("rotateRight").actionPerformed(null);
                        for (int turn = 0; turn < 45; turn++) {
                            panel.drawFrame();
                        }
                        panel.getActionMap().get("stopRight").actionPerformed(null);
                        checkParticlePixels(panel, "3D after rotation");
                        window.setSize(500, 400);
                        window.validate();
                        checkParticlePixels(panel, "3D after resizing");
                    }
                }
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            } finally {
                window.dispose();
            }
        });
        System.out.println("PASS: visible particle pixels in 1D/2D/3D, rotation, resizing and dimension switching");
    }

    private static SimulationInfo settings(int dimension) throws Exception {
        String text;
        try (var input = ResourceLoader.getDefaultSetFileURL().openStream()) {
            text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        Path file = Files.createTempFile("Boltzmann render settings ", ".set2");
        try {
            Files.writeString(file, text.replace("dimensions 2", "dimensions " + dimension));
            return new Load(null).loadFile(file.toFile());
        } finally {
            Files.delete(file);
        }
    }

    private static FrameInfo centeredParticle(SimulationInfo info) {
        double radius = info.arenaXSize * 0.04;
        ParticleType type = new ParticleType(2, radius, Color.RED, "Rendering check");
        double[] position = {info.arenaXSize / 2, info.arenaYSize / 2, info.arenaZSize / 2};
        double[] velocity = {0, 0, 0};
        Particle particle = switch (info.dimension) {
            case 1 -> new Particle1D(position, velocity, 0, type, info);
            case 2 -> new Particle2D(position, velocity, 0, type, info);
            case 3 -> new Particle3D(position, velocity, 0, type, info);
            default -> throw new AssertionError("Unexpected dimension");
        };
        PartState state = particle.getState(new PartState(0, 0, 0, radius, Color.RED, 0));
        FrameInfo frame = new FrameInfo(0, 1);
        frame.setEndingStates(Map.of(particle, state));
        return frame;
    }

    private static void checkParticlePixels(GLPanel panel, String label) {
        panel.drawFrame();
        boolean invoked = panel.canvas.invoke(true, drawable -> {
            GL2 gl = drawable.getGL().getGL2();
            int width = drawable.getSurfaceWidth();
            int height = drawable.getSurfaceHeight();
            ByteBuffer pixels = Buffers.newDirectByteBuffer(width * height * 4);
            gl.glReadBuffer(GL2.GL_BACK);
            gl.glReadPixels(0, 0, width, height, GL2.GL_RGBA, GL2.GL_UNSIGNED_BYTE, pixels);
            int error = gl.glGetError();
            if (error != GL2.GL_NO_ERROR) {
                throw new AssertionError(label + ": OpenGL error 0x" + Integer.toHexString(error));
            }
            int redPixels = 0;
            for (int offset = 0; offset < pixels.capacity(); offset += 4) {
                int red = Byte.toUnsignedInt(pixels.get(offset));
                int green = Byte.toUnsignedInt(pixels.get(offset + 1));
                int blue = Byte.toUnsignedInt(pixels.get(offset + 2));
                if (red > 40 && red > green * 1.5 && red > blue * 1.5) {
                    redPixels++;
                }
            }
            if (redPixels < 20) {
                throw new AssertionError(label + ": particle is invisible (" + redPixels + " red pixels)");
            }
            System.out.println(label + ": " + redPixels + " particle pixels");
            return true;
        });
        if (!invoked) {
            throw new AssertionError(label + ": OpenGL canvas is not realized");
        }
    }
}
