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
import java.util.Arrays;
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
                for (int dimension : new int[] {2, 3, 2, 3, 2, 1, 2}) {
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
                        for (String boundary : new String[] {"PERIODIC", "DIVIDED", "HOLE", "PISTON"}) {
                            SimulationInfo variant = settings(3, boundary);
                            panel.setSimulationInfo(variant);
                            panel.setNextFrameToDisplay(centeredParticle(variant));
                            checkParticlePixels(panel, "3D " + boundary);
                        }
                    }
                }
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            } finally {
                window.dispose();
            }
        });
        System.out.println("PASS: visible particles and 3D walls, rotation, resizing, boundary modes and dimension switching");
    }

    private static SimulationInfo settings(int dimension) throws Exception {
        return settings(dimension, "REFLECTING");
    }

    private static SimulationInfo settings(int dimension, String boundary) throws Exception {
        String text;
        try (var input = ResourceLoader.getDefaultSetFileURL().openStream()) {
            text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        Path file = Files.createTempFile("Boltzmann render settings ", ".set2");
        try {
            Files.writeString(file, text.replace("dimensions 2", "dimensions " + dimension)
                    .replace("boundary REFLECTING", "boundary " + boundary));
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
            checkError(gl, label + " rendering before readback");
            if (label.startsWith("3D")) {
                double[] projection = new double[16];
                gl.glGetDoublev(GL2.GL_PROJECTION_MATRIX, projection, 0);
                // In the test frustum clip W must be -eye Z, without a unit-conversion scale.
                if (Math.abs(projection[11] + 1.0) > 1e-6) {
                    throw new AssertionError(label + ": unit conversion leaked into the projection matrix");
                }
            }
            // macOS may render GLCanvas through an FBO, whose color attachment
            // must be selected instead of the window-system GL_BACK buffer.
            int readBuffer = drawable.getContext().getDefaultReadBuffer();
            gl.glReadBuffer(readBuffer);
            checkError(gl, label + " selecting read buffer 0x" + Integer.toHexString(readBuffer));
            gl.glReadPixels(0, 0, width, height, GL2.GL_RGBA, GL2.GL_UNSIGNED_BYTE, pixels);
            checkError(gl, label + " reading pixels");
            int redPixels = 0;
            int coloredPixels = 0;
            int wallPixels = 0;
            for (int offset = 0; offset < pixels.capacity(); offset += 4) {
                int red = Byte.toUnsignedInt(pixels.get(offset));
                int green = Byte.toUnsignedInt(pixels.get(offset + 1));
                int blue = Byte.toUnsignedInt(pixels.get(offset + 2));
                if (red + green + blue > 30) {
                    coloredPixels++;
                }
                if (red > 40 && red > green * 1.5 && red > blue * 1.5) {
                    redPixels++;
                }
                // The fixture uses a red sphere, gold/tan arena walls, and gray/white
                // piston/divider surfaces. Count wall colors independently of particles.
                if (green > 20 && red > green && green > blue && red < green * 1.5) {
                    wallPixels++;
                }
            }
            if (!label.startsWith("3D") && redPixels > width * height / 4) {
                throw new AssertionError(label + ": particle fills the screen (" + redPixels + " pixels)");
            }
            if (redPixels < 20) {
                double[] modelview = new double[16];
                double[] projection = new double[16];
                gl.glGetDoublev(GL2.GL_MODELVIEW_MATRIX, modelview, 0);
                gl.glGetDoublev(GL2.GL_PROJECTION_MATRIX, projection, 0);
                System.err.println(label + " modelview: " + Arrays.toString(modelview));
                System.err.println(label + " projection: " + Arrays.toString(projection));
                System.err.println(label + " read framebuffer: " + drawable.getContext().getDefaultReadFramebuffer()
                        + ", read buffer: 0x" + Integer.toHexString(readBuffer));
                System.err.println(label + " nonblack pixels: " + coloredPixels);
                throw new AssertionError(label + ": particle is invisible (" + redPixels + " red pixels)");
            }
            if (label.startsWith("3D") && wallPixels < 100) {
                double[] depthRange = new double[2];
                int[] depthBits = new int[1];
                gl.glGetDoublev(GL2.GL_DEPTH_RANGE, depthRange, 0);
                gl.glGetIntegerv(GL2.GL_DEPTH_BITS, depthBits, 0);
                System.err.println(label + " depth range: " + Arrays.toString(depthRange)
                        + ", depth bits: " + depthBits[0]);
                throw new AssertionError(label + ": arena walls are invisible (" + wallPixels + " wall pixels)");
            }
            System.out.println(label + ": " + redPixels + " particle pixels, " + wallPixels + " wall pixels");
            return true;
        });
        if (!invoked) {
            throw new AssertionError(label + ": OpenGL canvas is not realized");
        }
    }

    private static void checkError(GL2 gl, String stage) {
        int error = gl.glGetError();
        if (error != GL2.GL_NO_ERROR) {
            throw new AssertionError(stage + ": OpenGL error 0x" + Integer.toHexString(error));
        }
    }
}
