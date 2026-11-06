/*
 * Boltzmann 3D, a kinetic theory demonstrator
 * Copyright (C) 2013 Dr. Randall B. Shirts
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package edu.byu.chem.boltzmann.view.render.lwjgl;

import edu.byu.chem.boltzmann.model.physics.FrameInfo;
import edu.byu.chem.boltzmann.utils.Units;
import edu.byu.chem.boltzmann.utils.data.SimulationInfo;
import edu.byu.chem.boltzmann.view.render.ParticleColorResolver;
import edu.byu.chem.boltzmann.view.render.RenderFrame;
import edu.byu.chem.boltzmann.view.render.RenderFrameFactory;
import edu.byu.chem.boltzmann.view.render.Renderer;
import edu.byu.chem.boltzmann.view.maingui.MainGuiView;
import java.awt.*;
import java.awt.event.*;
import java.text.DecimalFormat;
import javax.swing.*;
import javax.swing.border.BevelBorder;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.awt.AWTGLCanvas;
import org.lwjgl.opengl.awt.GLData;

/**
 * Swing surface adapter for the LWJGL renderer. It owns the AWT context and
 * submits backend-neutral frame snapshots to a {@link Renderer}.
 */
public class LwjglRenderSurface extends JPanel {

        // An optional simulation statistic can override each particle's default color.
        private volatile ParticleColorResolver colorResolver = null;

        private volatile SimulationInfo simulationInfo = new SimulationInfo();
	//For serialization (probably never used).
	private static final long serialVersionUID = 0x1;

	//The drawing surface.
	public final AWTGLCanvas canvas;
        private GLCapabilities capabilities;
        private final Renderer renderer = new CoreProfileRenderer();

        //whether initGL() has initialized the display
        private boolean initialized;

	private volatile boolean isRotatingLeft = false;
	private volatile boolean isRotatingRight = false;
	private volatile boolean isRotatingUp = false;
	private volatile boolean isRotatingDown = false;

        private double camz = 250;

        private final ScalePanel verticalScalePanel;
        private final ScalePanel horizontalScalePanel;
        private int tracePointSize=2;
        private Color red, blue, background, divColor;
        private Color left3DColor, right3DColor, top3DColor, bottom3DColor, front3DColor, back3DColor, pistonColor;
        //Flag indicating whether or not we're using trajectory trace mode
        private volatile boolean traceMode;
        private volatile long traceResetGeneration;

        //Currently selected getColor, current camera angle
        private int camAngleX=0, camAngleY=0;
        private boolean lighting = true;

        private volatile FrameInfo currentFrame;










        private final MainGuiView mainView;

        /**
         * Whether the hole is open in Maxwell demon mode
         */
        private boolean arenaHoleOpen = true;

	/**
	 * Initializes the OpenGL canvas and adds the arrow keys to the action map
	 * for rotating the 3D view.
	 */
	@SuppressWarnings("serial")
	public LwjglRenderSurface(MainGuiView view) {
		super(new BorderLayout());

                this.mainView = view;

                red = Color.RED;
                blue = Color.BLUE;
                background = Color.BLACK;
                divColor = Color.WHITE;
                pistonColor = Color.GRAY;
                Color tan = new Color(224, 192, 160);
                Color gold = new Color(255, 224, 128);
                left3DColor = gold;
                right3DColor = gold;
                top3DColor = tan;
                bottom3DColor = tan;
                front3DColor = gold;
                back3DColor = gold;

                GLData contextData = new GLData();
                contextData.majorVersion = 3;
                contextData.minorVersion = 2;
                contextData.profile = GLData.Profile.CORE;
                contextData.forwardCompatible = true;
                contextData.doubleBuffer = true;
                contextData.depthSize = 24;
                canvas = new AWTGLCanvas(contextData) {
                    @Override
                    public void initGL() {
                        capabilities = GL.createCapabilities();
                        renderer.initialize();
                        initialized = true;
                    }

                    @Override
                    public void paintGL() {
                        // LWJGL capabilities are thread-local. Install this
                        // context's capabilities for the current render thread.
                        GL.setCapabilities(capabilities);
                        int width = getFramebufferWidth();
                        int height = getFramebufferHeight();
                        if (width <= 0 || height <= 0) {
                            return;
                        }
                        if (currentFrame == null) {
                            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
                            swapBuffers();
                            return;
                        }
                        LwjglRenderSurface.this.resizeSurface(width, height);
                        LwjglRenderSurface.this.rotate();
                        LwjglRenderSurface.this.render(LwjglRenderSurface.this.createRenderFrameSnapshot());
                        swapBuffers();
                    }

                    @Override
                    public void disposeGL() {
                        if (capabilities == null) {
                            return;
                        }
                        GL.setCapabilities(capabilities);
                        try {
                            renderer.dispose();
                            initialized = false;
                        } finally {
                            GL.setCapabilities(null);
                            capabilities = null;
                        }
                    }

                    @Override
                    protected void afterRender() {
                        try {
                            super.afterRender();
                        } finally {
                            GL.setCapabilities(null);
                        }
                    }
                };
                canvas.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseReleased(MouseEvent e) {
                        if (simulationInfo.maxwellDemonMode) {
                            arenaHoleOpen = !arenaHoleOpen;
                            mainView.getController().setArenaHoleOpen(arenaHoleOpen);
                        }
                    }
                });

                initialized=false;

                add(canvas, BorderLayout.CENTER);
                canvas.setFocusable(true);
		canvas.requestFocus();

                verticalScalePanel = new ScalePanel(ScalePanel.Y_AXIS, 0.1, "nm");
                horizontalScalePanel = new ScalePanel(ScalePanel.X_AXIS, 0.1, "nm");
                add(verticalScalePanel, BorderLayout.EAST);
                add(horizontalScalePanel, BorderLayout.SOUTH);

                //Set actions for rotation in 3D mode.
                ActionMap actionMap = getActionMap();
                Action left = new AbstractAction(){public void actionPerformed(ActionEvent e){
                    isRotatingRight = false;
                    isRotatingLeft = true;
                }};
                Action right = new AbstractAction(){public void actionPerformed(ActionEvent e){
                    isRotatingLeft = false;
                    isRotatingRight = true;
                }};
                Action up = new AbstractAction(){public void actionPerformed(ActionEvent e){
                    isRotatingDown = false;
                    isRotatingUp = true;
                }};
                Action down = new AbstractAction(){public void actionPerformed(ActionEvent e){
                    isRotatingUp = false;
                    isRotatingDown = true;
                }};
                Action leftReleased = new AbstractAction(){public void actionPerformed(ActionEvent e){
                    isRotatingLeft = false;
                }};
                Action rightReleased = new AbstractAction(){public void actionPerformed(ActionEvent e){
                    isRotatingRight = false;
                }};
                Action upReleased = new AbstractAction(){public void actionPerformed(ActionEvent e){
                    isRotatingUp = false;
                }};
                Action downReleased = new AbstractAction(){public void actionPerformed(ActionEvent e){
                    isRotatingDown = false;
                }};

                actionMap.put("rotateLeft", left);
                actionMap.put("rotateRight", right);
                actionMap.put("rotateUp", up);
                actionMap.put("rotateDown", down);
                actionMap.put("stopLeft", leftReleased);
                actionMap.put("stopRight", rightReleased);
                actionMap.put("stopUp", upReleased);
                actionMap.put("stopDown", downReleased);
                InputMap inputMap = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
                inputMap.put(KeyStroke.getKeyStroke("pressed LEFT"),"rotateLeft");
                inputMap.put(KeyStroke.getKeyStroke("pressed RIGHT"),"rotateRight");
                inputMap.put(KeyStroke.getKeyStroke("pressed UP"),"rotateUp");
                inputMap.put(KeyStroke.getKeyStroke("pressed DOWN"),"rotateDown");
                inputMap.put(KeyStroke.getKeyStroke("released LEFT"),"stopLeft");
                inputMap.put(KeyStroke.getKeyStroke("released RIGHT"),"stopRight");
                inputMap.put(KeyStroke.getKeyStroke("released UP"),"stopUp");
                inputMap.put(KeyStroke.getKeyStroke("released DOWN"),"stopDown");
        }

        private void resizeSurface(int width, int height) {
            renderer.resizeSurface(width, height);
            updateScaleRulers();
        }

        private void updateScaleRulers() {
            int width = canvas.getWidth();
            int height = canvas.getHeight();
            if (width <= 0 || height <= 0) {
                verticalScalePanel.setScale(0.0);
                horizontalScalePanel.setScale(0.0);
                return;
            }

            double widthNm = Units.convert("m", "nm", simulationInfo.arenaXSize);
            double heightNm = simulationInfo.dimension == 1
                    ? widthNm * height / width
                    : Units.convert("m", "nm", simulationInfo.arenaYSize);
            verticalScalePanel.setScale(heightNm / height);
            horizontalScalePanel.setScale(widthNm / width);
        }

        private void render(RenderFrame frame) {
            renderer.render(frame);
        }

        private void dispose() {
            renderer.dispose();
            initialized = false;
        }


	/**
	 *  Relies on booleans that track which arrow keys are currently held down.  More reliable and
	 *  responsive than using a per-keystroke basis, which encounters an initial delay and may vary
	 *  from machine to machine.
	 */
	private void rotate()
	{
            if (simulationInfo.dimension < 3) {
                    return;
            }
            if (isRotatingLeft)
            {
                camAngleY -= 1.0;
                if(camAngleY < 0) {
                    camAngleY += 360;
                }
            } else if (isRotatingRight) {
                camAngleY += 1.0;
                if(camAngleY > 369) {
                    camAngleY -= 360;
                }
            }

            if (isRotatingUp) {
                camAngleX -= 1.0;
                if(camAngleX < 0) {
                    camAngleX += 360;
                }
            } else if (isRotatingDown) {
                camAngleX += 1.0;
                if(camAngleX > 359) {
                    camAngleX -= 360;
                }
            }
	}


        /** Keep the default camera distance consistent with the current arena. */
        private void updateCameraDistance(double viewingAngle) {
            double complementTangent = Math.tan(
                    Math.PI * (90.0 - viewingAngle / 2.0) / 180.0);
            camz = (simulationInfo.arenaYSize / 2.0) * complementTangent
                    + simulationInfo.arenaZSize;
        }

        public void setSimulationInfo(SimulationInfo info){
            simulationInfo = info;
            updateCameraDistance(30.0);
            traceResetGeneration++;
            currentFrame = null;
            updateScaleRulers();
        }

        public void setTraceMode(boolean enabled, int pointSize) {
            int clampedPointSize = Math.max(1, pointSize);
            if (traceMode != enabled || tracePointSize != clampedPointSize) {
                traceResetGeneration++;
            }
            traceMode = enabled;
            tracePointSize = clampedPointSize;
        }

        public void rescaleArena(int availableWidth, int availableHeight) {
            Insets borderInsets = this.getInsets();
            int lostWidth = verticalScalePanel.getWidth() + borderInsets.right + borderInsets.left;
            int lostHeight = horizontalScalePanel.getHeight() + borderInsets.bottom + borderInsets.top;

            int availableArenaWidth = availableWidth - lostWidth;
            int availableArenaHeight = availableHeight - lostHeight;

            double ySize = simulationInfo.arenaYSize;
            if (simulationInfo.dimension == 1) {
                ySize = availableArenaHeight / (availableArenaWidth / simulationInfo.arenaXSize);
            }

            double horizontalScale = availableArenaWidth / simulationInfo.arenaXSize;
            double verticalScale = availableArenaHeight / ySize;

            if (verticalScale > horizontalScale) {
                availableArenaHeight = (int)Math.floor(ySize * horizontalScale);
            } else {
                availableArenaWidth = (int)Math.floor(simulationInfo.arenaXSize * verticalScale);
            }

            setSize(new Dimension(availableArenaWidth + lostWidth, availableArenaHeight + lostHeight));
            setPreferredSize(new Dimension(availableArenaWidth + lostWidth, availableArenaHeight + lostHeight));

        }

    public void drawFrame() {
        // AWTGLCanvas makes its context current for this callback and clears it
        // again when render() returns.
        if (canvas.isDisplayable() && canvas.getWidth() > 0 && canvas.getHeight() > 0) {
            canvas.render();
        }
    }

    public void disposeRenderSurface() {
        // The dispose callback releases context-bound resources while current.
        canvas.disposeCanvas();
    }

    public void setNextFrameToDisplay(FrameInfo frame) {
        currentFrame = frame;
        if (frame != null && simulationInfo.maxwellDemonMode) {
            arenaHoleOpen = frame.isArenaHoleOpen();
        }
    }

    /**
     * Copies the currently displayed simulation and view state into a graphics-
     * library-independent snapshot. Call this on the view thread so the camera,
     * trace, and color settings are read consistently with the displayed frame.
     */
    public RenderFrame createRenderFrameSnapshot() {
        FrameInfo frame = currentFrame;
        if (frame == null) {
            return null;
        }

        boolean perspective = simulationInfo.dimension == 3;
        RenderFrame.Camera camera = new RenderFrame.Camera(
                camz, camAngleX, camAngleY,
                perspective ? RenderFrame.Camera.Projection.PERSPECTIVE
                        : RenderFrame.Camera.Projection.ORTHOGRAPHIC,
                perspective ? 30.0 : 0.0);
        RenderFrame.Trace trace = new RenderFrame.Trace(
                traceMode, traceMode, tracePointSize, traceResetGeneration);
        RenderFrame.Palette palette = new RenderFrame.Palette(
                toRenderColor(background),
                toRenderColor(left3DColor),
                toRenderColor(right3DColor),
                toRenderColor(top3DColor),
                toRenderColor(bottom3DColor),
                toRenderColor(front3DColor),
                toRenderColor(back3DColor),
                toRenderColor(divColor),
                toRenderColor(pistonColor),
                toRenderColor(left3DColor));

        return RenderFrameFactory.create(frame, simulationInfo, camera, trace,
                palette, lighting, colorResolver);
    }

    private static RenderFrame.Color toRenderColor(Color color) {
        float[] components = color.getRGBColorComponents(null);
        return new RenderFrame.Color(components[0], components[1], components[2],
                color.getAlpha() / 255.0f);
    }

    class ScalePanel extends JPanel implements MouseListener
    {
            private static final long serialVersionUID = 1L;

            //Variables: dir		the direction: either Y_AXIS or X_AXIS
            //			 scale		the scale in units/pixel
            //			 unit		the name of the unit being used
            //			 step		the amout of pixels between ticks
            public final static int Y_AXIS=0, X_AXIS=1;
            private int dir;
            private double scale;
            private String unit;
            private DecimalFormat df;
            //Constructor
            public ScalePanel(int direction, double scale, String unit)
            {
                    dir=direction;
                    this.scale=scale;
                    this.unit=unit;
                    if(dir==Y_AXIS)
                            setPreferredSize(new Dimension(50,1));
                    else
                            setPreferredSize(new Dimension(1,30));
                    setBackground(Color.WHITE);
                    setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
                    //setInsets(0,0,0,0);
                    df=new DecimalFormat("####.#");
                    setFont(new Font("Arial", Font.PLAIN, 10));
                    addMouseListener(this);
            }
            public void mouseClicked(MouseEvent e)
            {
                    grabFocus();
            }

            public void setScale(double newScale) {
                this.scale = newScale;
                repaint();
            }

            public void mousePressed(MouseEvent e){}
            public void mouseReleased(MouseEvent e){}
            public void mouseEntered(MouseEvent e){}
            public void mouseExited(MouseEvent e){}
            //overwritten paintComponent
            @Override
            public void paintComponent(Graphics g)
            {
                    super.paintComponent(g);
                    g.setColor(Color.BLACK);
                    int pixelPos=0;
                    int maxPixelPos; //, deltaPixelPos;
                    if(dir==Y_AXIS)
                            maxPixelPos=getSize().height;
                    else
                            maxPixelPos=getSize().width - MINIMUM_STEP_SIZE;
                    double valueStep = calculateStepSize(scale * maxPixelPos, maxPixelPos);

                    int borderSize=2;

                    double currentValue = 0.0;
                    while (currentValue < (scale * maxPixelPos)) {
                            pixelPos = (int)Math.floor(currentValue / scale);

                            String s=df.format(currentValue)+" "+unit;
                            if(dir==Y_AXIS)
                            {
                                    g.drawLine(0, getSize().height-pixelPos-borderSize, 10, getSize().height-pixelPos-borderSize);
                                    g.drawString(s,12, getSize().height-pixelPos+3-borderSize);
                            }
                            else
                            {
                                    g.drawLine(pixelPos+borderSize, 0, pixelPos+borderSize, 10);
                                    g.drawString(s,pixelPos+borderSize, 25);
                            }

                            currentValue += valueStep;
                    }
            }
    }

    private static final int MINIMUM_STEP_SIZE = 50;

    private static double calculateStepSize(double numberRange, int pixelRange) {
        if (Double.isInfinite(numberRange)) {
            throw new IllegalArgumentException("Infinite number range given");
        }

        double stepSize = 0.0;

        int numberOfPossibleSteps = pixelRange / MINIMUM_STEP_SIZE; //Integer division always rounds down

        if (numberOfPossibleSteps >= 2) {
            double proposedStepSize = numberRange / numberOfPossibleSteps;

            if (proposedStepSize > 10.0) {
                stepSize = calculateStepSize(numberRange / 10.0, pixelRange) * 10.0;
            } else if (proposedStepSize < 1.0) {
                stepSize = 1.0;
            } else if (proposedStepSize < 2.0) {
                stepSize = 2.0;
            } else if (proposedStepSize < 5.0) {
                stepSize = 5.0;
            } else {
                stepSize = 10.0;
            }

        } else {
            stepSize = numberRange;
        }


        return stepSize;
    }

    public int getTracePointSize() {
        return tracePointSize;
    }

    public Color getBack3DColor() {
        return back3DColor;
    }

    public Color getBg() {
        return background;
    }

    public Color getBlue() {
        return blue;
    }

    public Color getBottom3DColor() {
        return bottom3DColor;
    }

    public Color getDivColor() {
        return divColor;
    }

    public Color getFront3DColor() {
        return front3DColor;
    }

    public Color getLeft3DColor() {
        return left3DColor;
    }

    public Color getRight3DColor() {
        return right3DColor;
    }

    public Color getTop3DColor() {
        return top3DColor;
    }

    public Color getPistonColor() {
        return pistonColor;
    }

    public Color getRed() {
        return red;
    }

    public boolean isTraceMode() {
        return traceMode;
    }

    public int getCamAngleX() {
        return camAngleX;
    }

    public int getCamAngleY() {
        return camAngleY;
    }

    public boolean isLighting() {
        return lighting;
    }

    public void setParticleColorResolver(ParticleColorResolver colorResolver) {
        this.colorResolver = colorResolver;
    }
}
