package edu.byu.chem.boltzmann.tests;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
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
import edu.byu.chem.boltzmann.view.render.lwjgl.GLPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Requires a display and a working OpenGL driver; checks actual framebuffer pixels. */
public final class RenderingSmokeTest {
    public static void main(String[] args) throws Exception {
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
                SimulationInfo traceInfo = settings(2);
                panel.setSimulationInfo(traceInfo);
                panel.setTraceMode(true, 8);
                panel.setNextFrameToDisplay(centeredParticle(traceInfo));
                checkParticlePixels(panel, "2D trace");
                panel.setTraceMode(false, 2);
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            } finally {
                window.dispose();
            }
        });
        edu.byu.chem.boltzmann.controller.ThreadLifecycleSmokeTest.main(new String[] {"render"});
        System.out.println("PASS: particles, walls, rotation, resizing, boundaries, dimension switching and trace points");
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
        boolean invoked;
        try {
            invoked = panel.canvas.executeInContext(() -> {
            GL.createCapabilities();
            int width = panel.canvas.getFramebufferWidth();
            int height = panel.canvas.getFramebufferHeight();
            ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
            checkError(label + " rendering before readback");
            // AWTGLCanvas uses a double-buffered window surface. Read the frame
            // just presented by the renderer from its front buffer.
            GL11.glReadBuffer(GL11.GL_FRONT);
            checkError(label + " selecting front buffer");
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            checkError(label + " reading pixels");
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
                System.err.println(label + " read buffer: GL_FRONT");
                System.err.println(label + " nonblack pixels: " + coloredPixels);
                throw new AssertionError(label + ": particle is invisible (" + redPixels + " red pixels)");
            }
            if (label.startsWith("3D") && wallPixels < 100) {
                IntBuffer depthBits = BufferUtils.createIntBuffer(1);
                GL11.glGetIntegerv(GL11.GL_DEPTH_BITS, depthBits);
                System.err.println(label + " depth bits: " + depthBits.get(0));
                throw new AssertionError(label + ": arena walls are invisible (" + wallPixels + " wall pixels)");
            }
            System.out.println(label + ": " + redPixels + " particle pixels, " + wallPixels + " wall pixels");
            return true;
            });
        } catch (Exception failure) {
            throw new RuntimeException("Could not read the LWJGL framebuffer", failure);
        }
        if (!invoked) {
            throw new AssertionError(label + ": OpenGL canvas is not realized");
        }
    }

    private static void checkError(String stage) {
        int error = GL11.glGetError();
        if (error != GL11.GL_NO_ERROR) {
            throw new AssertionError(stage + ": OpenGL error 0x" + Integer.toHexString(error));
        }
    }
}
