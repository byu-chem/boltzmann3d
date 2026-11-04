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
package edu.byu.chem.boltzmann.view.maingui.components;

import edu.byu.chem.boltzmann.model.physics.FrameInfo;
import edu.byu.chem.boltzmann.model.physics.PartState;
import edu.byu.chem.boltzmann.model.physics.Particle;
import edu.byu.chem.boltzmann.model.physics.Wall;
import edu.byu.chem.boltzmann.utils.Units;
import edu.byu.chem.boltzmann.utils.data.SimulationInfo;
import edu.byu.chem.boltzmann.utils.data.SimulationInfo.ArenaType;
import edu.byu.chem.boltzmann.view.maingui.MainGuiView;
import java.awt.*;
import java.awt.event.*;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.BevelBorder;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.DebugGL2;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;
import static com.jogamp.opengl.GL2.*;

/**
 * Responsible for displaying the current state of the particles.  The 2D and 3D
 * drawing methods are contained here, along with the window resizing handler.
 */
public class GLPanel extends JPanel {
    
        public enum ColorMode {
            NORMAL_COLORING,
            COLOR_BY_SPEED,
            COLOR_BY_KE
        }
    
        public interface ParticleColorer {
            public Color getParticleColor(PartState state);
        }
        
        // Offers optional coloring by speed or other statistic. Null uses default colors.
        private ParticleColorer colorer = null;
    
        private SimulationInfo simulationInfo = new SimulationInfo();
	//For serialization (probably never used).
	private static final long serialVersionUID = 0x1;
	
	//Forces the piston to display; used for loading paused piston mode files.
	public static boolean drawPistonOverride = false;
	public static double drawPistonOverrideValue = 0.0;
	
	//The drawing surface.
	public final GLCanvas canvas;

        // Set only inside JOGL callbacks while the canvas context is current.
        private GL2 gl;
        private final GLU glu = new GLU();
        
        //whether initGL() has initialized the display
        public boolean initialized;

	//OpenGL constant.
	private final static int CUBE = 1;
	
	//OpenGL shape definitions.
	private GLUquadric sphere;
        private GLUquadric cylinder;
        private GLUquadric disk;
	
	//Loosely follows main.traceMode; used in an unusual way to clear the screen...
	public boolean inTraceMode=false;
	
	//Somehow used to determine whether 3D lighting mode is enabled.
	private boolean lightsOn=true;

	//The OpenGL library can draw spheres as meshes of polygons, but it
	//needs to know how fine to make the mesh.  The function getPrecision()
	//will calculate these numbers for a given radius.  We want to cache the
	//value to avoid recalculating each frameTime.
	//
	//Holds precision numbers (stacks/slices per sphere) depending on the size
	private HashMap<Double,Point> precMap = new HashMap<Double,Point>();
	

	//The distance from the walls in 3D to the clipping planes
	private double clipDistance = Units.convert("nm", "m", 0.1);
        
	/** Point size, effective in trace mode.  Follows main.tracePointSize. */
	private int curPointSize = -1;
        
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
        private float[] colorComponents;
        //Flag indicating whether or not we're using trajectory trace mode
        private boolean traceMode;
        
        //Currently selected getColor, current camera angle
        private int camAngleX=0, camAngleY=0;
        private boolean lighting = true;

        private volatile FrameInfo currentFrame;
	
        private DoubleBuffer doubleBuffer;
        private FloatBuffer floatBuffer;
        
        private FloatBuffer toBuffer(float[] array){//perform myBuffer.flip() before using
            return Buffers.newDirectFloatBuffer(array.length).put(array);}
        private DoubleBuffer toBuffer(double[] array){//perform myBuffer.flip() before using
            return Buffers.newDirectDoubleBuffer(array.length).put(array);}
        
        /** 3D OpenGL uses nanometers; the established 1D/2D paths still use meters. */
        private double renderLength(double meters) {
            return simulationInfo.dimension == 3 ? meters * 1.0e9 : meters;
        }

        private void translate(double x, double y, double z) {
            gl.glTranslated(renderLength(x), renderLength(y), renderLength(z));
        }

        private void vertex(double x, double y, double z) {
            gl.glVertex3d(renderLength(x), renderLength(y), renderLength(z));
        }

        private void drawSphereSurface(double radius, int slices, int stacks) {
            glu.gluSphere(sphere, renderLength(radius), slices, stacks);
        }

        private void drawCylinder(double base, double top, double height, int slices, int stacks) {
            glu.gluCylinder(cylinder, renderLength(base), renderLength(top), renderLength(height), slices, stacks);
        }

        private void drawDisk(double inner, double outer, int slices, int loops) {
            glu.gluDisk(disk, renderLength(inner), renderLength(outer), slices, loops);
        }

        private void clipPlane(int plane, double x, double y, double z, double distance) {
            doubleBuffer = toBuffer(new double[] {x, y, z, renderLength(distance)});
            doubleBuffer.flip();
            gl.glClipPlane(plane, doubleBuffer);
        }

	/** 
	 * Returns the # of stacks & slices that should be used to draw
	 * a sphere of the given radius.
	 */
	// stacks : (int) PI*sqrt(radius+1)
	// slices : 7
	// radius is in meters
	public Point getPrecision (double radius)
	{
                radius = Units.convert("m", "nm", radius);
		Double f = Double.valueOf(radius * 10.0);
		if (precMap.containsKey(f))
			return (Point)precMap.get(f);
		else
		{
			int x = (int)(Math.sqrt((radius * 10.0)+1)*Math.PI);
			// slices: half-circumference, in pixels, divided by 10.0 leads to very smooth spheres
			int y = Math.max(8, (int) ((radius * 10.0) * Math.PI / 10.0) );
			if (y % 2 == 1) y ++; // ensure an even value
			//int y = 8; // 7
			Point p = new Point(x, y);
			precMap.put (f, p);
			return p;
		}
	}

        private final MainGuiView mainView;
        
        /**
         * Whether the hole is open in Maxwell demon mode
         */
        private boolean arenaHoleOpen = true;
                
	/**
	 * Initializes a canvas with hardware acceleration and
	 * double buffering enabled. Adds the arrow keys to the action map
	 * to rotate the view in 3D.
	 */
	@SuppressWarnings("serial")
	public GLPanel(MainGuiView view) {
		super(new BorderLayout());
                
                this.mainView = view;
                
		if (System.getProperty("os.name").indexOf("Linux") >= 0)
			clipDistance = 7.0d;

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
                
                // GL2 preserves the existing fixed-function renderer during migration.
                // Request it explicitly: a core profile cannot render these primitives.
                GLCapabilities capabilities = new GLCapabilities(GLProfile.get(GLProfile.GL2));
                capabilities.setDoubleBuffered(true);
                capabilities.setDepthBits(24);
                canvas = new GLCanvas(capabilities);
                canvas.addGLEventListener(new GLEventListener() {
                    @Override
                    public void init(GLAutoDrawable drawable) {
                        if (Boolean.getBoolean("boltzmann.opengl.debug")) {
                            drawable.setGL(new DebugGL2(drawable.getGL().getGL2()));
                        }
                        gl = drawable.getGL().getGL2();
                        GLPanel.this.init();
                        initialized = true;
                    }

                    @Override
                    public void display(GLAutoDrawable drawable) {
                        gl = drawable.getGL().getGL2();
                        if (drawable.getSurfaceWidth() <= 0 || drawable.getSurfaceHeight() <= 0) {
                            return;
                        }
                        if (currentFrame == null) {
                            gl.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                            return;
                        }
                        // Surface dimensions include Retina scaling; Swing sizes do not.
                        reshapeThis(0, 0, drawable.getSurfaceWidth(), drawable.getSurfaceHeight());
                        GLPanel.this.display();
                        // GLCanvas swaps buffers automatically after this callback.
                    }

                    @Override
                    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
                        // Projection also depends on simulation settings, so refresh per frame.
                    }

                    @Override
                    public void dispose(GLAutoDrawable drawable) {
                        gl = drawable.getGL().getGL2();
                        deleteQuadrics();
                        gl.glDeleteLists(CUBE, 1);
                        initialized = false;
                        gl = null;
                    }
                });
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
		canvas.setIgnoreRepaint(false);
                
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

        /** Initializes the canvas by setting the viewport, lighting, etc. */
        public void init() {
            gl.glShadeModel(GL_SMOOTH);
            gl.glMatrixMode(GL_MODELVIEW);
            gl.glLoadIdentity();
            sphere = glu.gluNewQuadric();
            cylinder = glu.gluNewQuadric();
            disk = glu.gluNewQuadric();
            glu.gluQuadricNormals(sphere, GLU.GLU_SMOOTH);
            glu.gluQuadricNormals(cylinder, GLU.GLU_SMOOTH);
            glu.gluQuadricNormals(disk, GLU.GLU_SMOOTH);
            
            //GL_LIGHT0 is a very dim backlight; with it I can only see silhouetted spheres against oblique walls.
            //GL_LIGHT0 is enabled only in 3D mode.
            floatBuffer = toBuffer(new float[] {0.3f, 0.3f, 0.3f, 1.0f});
            floatBuffer.flip();
            //gl.glLightfv(GL_LIGHT0, GL_AMBIENT_AND_DIFFUSE, floatBuffer); // RGBA values
            gl.glLightfv(GL_LIGHT0, GL_AMBIENT, floatBuffer); // RGBA values
            gl.glLightfv(GL_LIGHT0, GL_DIFFUSE, floatBuffer); // RGBA values
            floatBuffer = toBuffer(new float[] {0.6f, 0.6f, 0.6f, 0.3f});
            floatBuffer.flip();
            gl.glLightfv(GL_LIGHT0, GL_SPECULAR, floatBuffer);
            floatBuffer = toBuffer(new float[] {0.0f, 0.0f, -1.0f, 0.0f});
            floatBuffer.flip();
            gl.glLightfv(GL_LIGHT0, GL_POSITION, floatBuffer); // position (directional if last coord is 0)

            //GL_LIGHT1 is enabled only in 3D mode, and its position is set elsewhere
            floatBuffer = toBuffer(new float[] {0.3f, 0.3f, 0.3f, 1.0f});
            floatBuffer.flip();
            gl.glLightfv(GL_LIGHT1, GL_AMBIENT, floatBuffer); // very dim gray walls; spheres invisible
            floatBuffer = toBuffer(new float[] {0.8f, 0.8f, 0.8f, 1.0f});
            floatBuffer.flip();
            gl.glLightfv(GL_LIGHT1, GL_DIFFUSE, floatBuffer); // this is most of what we see
            floatBuffer = toBuffer(new float[] {1.0f, 1.0f, 1.0f, 1.0f});
            floatBuffer.flip();
            gl.glLightfv(GL_LIGHT1, GL_SPECULAR, floatBuffer); // no noticeable contribution - it looks black!

            floatBuffer = toBuffer(new float[] {0.0f, 0.0f, -1.0f, 1.0f});
            floatBuffer.flip();
            gl.glLightfv(GL_LIGHT1, GL_SPOT_DIRECTION, floatBuffer);
            gl.glLightf(GL_LIGHT1, GL_SPOT_CUTOFF, 180);
            gl.glLightf(GL_LIGHT1, GL_LINEAR_ATTENUATION, 0.5E-5f);
            gl.glLightf(GL_LIGHT1, GL_SPOT_EXPONENT, 64);

            //GL_LIGHT2 is only enabled in 1D and 2D modes.
            floatBuffer = toBuffer(new float[] {0.1f, 0.1f, 0.1f, 1.0f});
            floatBuffer.flip();
            gl.glLightfv(GL_LIGHT2, GL_AMBIENT, floatBuffer);
            floatBuffer = toBuffer(new float[] {1.0f, 1.0f, 1.0f, 1.0f});
            floatBuffer.flip();
            gl.glLightfv(GL_LIGHT2, GL_DIFFUSE, floatBuffer);
            floatBuffer = toBuffer(new float[] {0.0f, 0.0f, 0.0f, 1.0f});
            floatBuffer.flip();
            gl.glLightfv(GL_LIGHT2, GL_SPECULAR, floatBuffer);
            floatBuffer = toBuffer(new float[] {0.0f, 0.0f, 1.0f, 0.0f});
            floatBuffer.flip();
            gl.glLightfv(GL_LIGHT2, GL_POSITION, floatBuffer);

            gl.glEnable(GL_LIGHTING);
            gl.glColorMaterial(GL_FRONT, GL_DIFFUSE);
            gl.glEnable(GL_COLOR_MATERIAL);
            gl.glEnable(GL_DEPTH_TEST);
            gl.glEnable(GL_NORMALIZE);
            gl.glCullFace(GL_BACK);
            gl.glEnable(GL_CULL_FACE);
            gl.glEnable(GL_POLYGON_SMOOTH);
            gl.glEnable(GL_LINE_SMOOTH);
            gl.glEnable(GL_POINT_SMOOTH);
            gl.glEnable(GL_BLEND);
            gl.glEnable(GL_DITHER);
            gl.glPointSize(tracePointSize);
            curPointSize=tracePointSize;

            //Print valuable OpenGL driver info to the console for debugging.
            System.out.println(gl.glGetString(GL_VENDOR));
            System.out.println(gl.glGetString(GL_RENDERER));
            System.out.println(gl.glGetString(GL_VERSION));
            makeCube();
	}

	public void makeCube()
	{
                gl.glNewList(GLPanel.CUBE, GL_COMPILE);
                colorComponents = left3DColor.getComponents(null);
                gl.glColor3f(colorComponents[0], colorComponents[1], colorComponents[2]);

		//Draw the sides of the cube
                gl.glBegin(GL_QUADS);				//Start Drawing Quads
		
		//Front Face
                gl.glNormal3f( 0.0f, 0.0f, 1.0f);		//Normal Facing Forward
                gl.glVertex3f(-0.5f, -0.5f,  0.5f);	//Bottom Left Of The Texture and Quad
                gl.glVertex3f( 0.5f, -0.5f,  0.5f);	//Bottom Right Of The Texture and Quad
                gl.glVertex3f( 0.5f,  0.5f,  0.5f);	//Top Right Of The Texture and Quad
                gl.glVertex3f(-0.5f,  0.5f,  0.5f);	//Top Left Of The Texture and Quad
		
		//Back Face
                gl.glNormal3f( 0.0f, 0.0f,-1.0f);		//Normal Facing Away
                gl.glVertex3f(-0.5f, -0.5f, -0.5f);	//Bottom Right Of The Texture and Quad
                gl.glVertex3f(-0.5f,  0.5f, -0.5f);	//Top Right Of The Texture and Quad
                gl.glVertex3f( 0.5f,  0.5f, -0.5f);	//Top Left Of The Texture and Quad
                gl.glVertex3f( 0.5f, -0.5f, -0.5f);	//Bottom Left Of The Texture and Quad
		
		//Top Face
                gl.glNormal3f( 0.0f, 1.0f, 0.0f);		//Normal Facing Up
                gl.glVertex3f(-0.5f,  0.5f, -0.5f);	//Top Left Of The Texture and Quad
                gl.glVertex3f(-0.5f,  0.5f,  0.5f);	//Bottom Left Of The Texture and Quad
                gl.glVertex3f( 0.5f,  0.5f,  0.5f);	//Bottom Right Of The Texture and Quad
                gl.glVertex3f( 0.5f,  0.5f, -0.5f);	//Top Right Of The Texture and Quad
		
		//Bottom Face
                gl.glNormal3f( 0.0f,-1.0f, 0.0f);		//Normal Facing Down
                gl.glVertex3f(-0.5f, -0.5f, -0.5f);	//Top Right Of The Texture and Quad
                gl.glVertex3f( 0.5f, -0.5f, -0.5f);	//Top Left Of The Texture and Quad
                gl.glVertex3f( 0.5f, -0.5f,  0.5f);	//Bottom Left Of The Texture and Quad
                gl.glVertex3f(-0.5f, -0.5f,  0.5f);	//Bottom Right Of The Texture and Quad
		
		//Right face
                gl.glNormal3f( 1.0f, 0.0f, 0.0f);		//Normal Facing Right
                gl.glVertex3f( 0.5f, -0.5f, -0.5f);	//Bottom Right Of The Texture and Quad
                gl.glVertex3f( 0.5f,  0.5f, -0.5f);	//Top Right Of The Texture and Quad
                gl.glVertex3f( 0.5f,  0.5f,  0.5f);	//Top Left Of The Texture and Quad
                gl.glVertex3f( 0.5f, -0.5f,  0.5f);	//Bottom Left Of The Texture and Quad
		
		//Left Face
                gl.glNormal3f(-1.0f, 0.0f, 0.0f);		//Normal Facing Left
                gl.glVertex3f(-0.5f, -0.5f, -0.5f);	//Bottom Left Of The Texture and Quad
                gl.glVertex3f(-0.5f, -0.5f,  0.5f);	//Bottom Right Of The Texture and Quad
                gl.glVertex3f(-0.5f,  0.5f,  0.5f);	//Top Right Of The Texture and Quad
                gl.glVertex3f(-0.5f,  0.5f, -0.5f);	//Top Left Of The Texture and Quad
			
                gl.glEnd();
                gl.glEndList();
	}
	
	/**
	 * Draws a 3D sphere at the specified coordinates with the specified size.
	 * @param glDrawable
	 * @param x  X position, in simulation units
	 * @param y  Y position, in simulation units
	 * @param z  Z position, in simulation units
	 * @param rad  Radius, in simulation units
	 * @param prec
	 */
	private void drawSphere (double x, double y, double z, double rad, Point prec)
	{		
		if (traceMode) {
                    gl.glDisable(GL_LIGHTING);
                    gl.glBegin(GL_POINTS);
                    vertex(x,y,z);
                    gl.glEnd();
                    gl.glEnable(GL_LIGHTING);
		} else {

                    gl.glPushMatrix();
                        translate(x, y, z);
                        drawSphereSurface((float)rad, prec.x, prec.y);

                        //Draw an inside-out sphere in 3D periodic mode so you can
                        //see inside the spheres that cross the wall boundary.
                        if(simulationInfo.dimension == 3 && (simulationInfo.arenaType == ArenaType.PERIODIC_BOUNDARIES))
                        {
                                glu.gluQuadricOrientation(sphere, GLU.GLU_INSIDE);
                                drawSphereSurface((float)rad, prec.x, prec.y);
                                glu.gluQuadricOrientation(sphere, GLU.GLU_OUTSIDE);
                        }
                    gl.glPopMatrix();
		}
	}
	
	//Draws all the spheres from the current SimulationFrame
	private void drawSpheres ()
	{
		// arena size (for drawing images)
		double w = simulationInfo.arenaXSize, h = simulationInfo.arenaYSize, d = simulationInfo.arenaZSize;
		//double w2 = w / 2;
                if (simulationInfo.dimension == 1) {
                    translate(0.0, canvas.getHeight() / 2.0 * (simulationInfo.arenaXSize / canvas.getWidth()), 0.0);
                }
		
		if (currentFrame != null) {
			// iterate through the particles
                        Map<Particle, PartState> particleStates = currentFrame.getParticleStates();

			for (Particle particle: currentFrame.getParticles()) {
                            PartState particleState = particleStates.get(particle);

                            // draw the sphere
                            //Point prec=getPrecision(ps.rad);
                            
                            // set the color                            
                            Color particleColor = particleState.color;
                            if (colorer != null) {
                                particleColor = colorer.getParticleColor(particleState);
                            }
                            colorComponents = particleColor.getComponents(null);
                            gl.glColor3f(colorComponents[0], colorComponents[1], colorComponents[2]);
                            Point prec = getPrecision (particleState.rad);

                            drawSphere (particleState.x, particleState.y, particleState.z, particle.radius, prec);

                            // if periodic boundaries then draw "shadow" particle(s)
                                    if ((simulationInfo.arenaType == ArenaType.PERIODIC_BOUNDARIES) && particleState.bFlag > 0) {
                                        if ((particleState.bFlag & Wall.LEFT) == Wall.LEFT) {
                                            drawSphere (particleState.x + w, particleState.y, particleState.z, particleState.rad, prec);
                                        }
                                        if ((particleState.bFlag & Wall.BOTTOM) == Wall.BOTTOM) {
                                            drawSphere (particleState.x, particleState.y + h, particleState.z, particleState.rad, prec);
                                        }
                                        if ((particleState.bFlag & Wall.BACK) == Wall.BACK) {
                                            drawSphere (particleState.x, particleState.y, particleState.z + d, particleState.rad, prec);
                                        }
                                        if ((particleState.bFlag & Wall.LEFT) == Wall.LEFT && (particleState.bFlag & Wall.BOTTOM) == Wall.BOTTOM) {
                                            drawSphere (particleState.x + w, particleState.y + h, particleState.z, particleState.rad, prec);
                                        }
                                        if ((particleState.bFlag & Wall.BACK) == Wall.BACK && (particleState.bFlag & Wall.BOTTOM) == Wall.BOTTOM) {
                                            drawSphere (particleState.x, particleState.y + h, particleState.z + d, particleState.rad, prec);
                                        }
                                        if ((particleState.bFlag & Wall.LEFT) == Wall.LEFT && (particleState.bFlag & Wall.BACK) == Wall.BACK) {
                                            drawSphere (particleState.x + w, particleState.y, particleState.z + d, particleState.rad, prec);
                                        }
                                        if ((particleState.bFlag & Wall.LEFT) == Wall.LEFT && (particleState.bFlag & Wall.BACK) == Wall.BACK && (particleState.bFlag & Wall.BOTTOM) == Wall.BOTTOM) {
                                            drawSphere (particleState.x + w, particleState.y + h, particleState.z + d, particleState.rad, prec);
                                        }
                                    }
                            }
			
		}
	}
	
    private void drawPiston () {
        if (currentFrame != null) {
            Point prec = getPrecision (SimulationInfo.PISTON_SHAFT_RADIUS);
            double w = simulationInfo.arenaXSize, h = simulationInfo.arenaYSize, d = simulationInfo.arenaZSize;
            double w2 = w / 2.0, d2 = d / 2.0; //, h2 = h / 2.0
            double pistonPos = currentFrame.getPistonPosition(currentFrame.endTime);//currentFrame.pistonPos;
            colorComponents = pistonColor.getComponents(null);
            gl.glColor3f(colorComponents[0], colorComponents[1], colorComponents[2]);
            switch (simulationInfo.dimension) {
                case 1:
                    double relW = w;
                    double relH = canvas.getHeight() * (simulationInfo.arenaXSize / canvas.getWidth());

                    gl.glPushMatrix();
                        translate(pistonPos + SimulationInfo.PISTON_VALVE_RADIUS, relH, 0);
                        gl.glRotatef(90,1.0f,0.0f, 0.0f);
                        drawCylinder((float)SimulationInfo.PISTON_VALVE_RADIUS, (float)SimulationInfo.PISTON_VALVE_RADIUS, (float)relH, prec.x, 1);
                    gl.glPopMatrix();

                    gl.glPushMatrix();
                        translate(pistonPos + 2.0 * SimulationInfo.PISTON_VALVE_RADIUS , relH / 2.0, 0.0);
                        gl.glRotatef(90, 0.0f,1.0f, 0.0f);
                        drawCylinder((float)SimulationInfo.PISTON_SHAFT_RADIUS, (float)SimulationInfo.PISTON_SHAFT_RADIUS, (float)(relW - pistonPos), prec.y, 1);
                    gl.glPopMatrix();
                    break;

                case 2:
                    relW = w;
                    relH = h;

                    // draw the piston valve
                    gl.glPushMatrix();
                        translate(0, pistonPos + SimulationInfo.PISTON_VALVE_RADIUS, 0);
                        gl.glRotatef(90, 0.0f, 1.0f, 0.0f);
                        drawCylinder((float)SimulationInfo.PISTON_VALVE_RADIUS, (float)SimulationInfo.PISTON_VALVE_RADIUS, (float)relW, prec.y, 1);
                    gl.glPopMatrix();

                    // draw the piston shaft
                    gl.glPushMatrix();
                        translate(relW / 2.0, relH, 0);
                        gl.glRotatef(90, 1.0f, 0.0f, 0.0f);
                        drawCylinder((float)SimulationInfo.PISTON_SHAFT_RADIUS, (float)SimulationInfo.PISTON_SHAFT_RADIUS, (float)(relH - pistonPos - 2.0*SimulationInfo.PISTON_VALVE_RADIUS), prec.x, 1);
                    gl.glPopMatrix();
                    break;

                case 3:
                    gl.glPushMatrix();
                        translate(w2,pistonPos+SimulationInfo.PISTON_VALVE_RADIUS,d2);
                        gl.glScaled(renderLength(w), renderLength(2.0 * SimulationInfo.PISTON_VALVE_RADIUS), renderLength(d));
                        colorComponents = left3DColor.getComponents(null);
                        gl.glColor3f(colorComponents[0], colorComponents[1], colorComponents[2]);
                        glutSolidCube(1.0f);
                    gl.glPopMatrix();

                    gl.glPushMatrix();
                        translate(w2, pistonPos + SimulationInfo.PISTON_VALVE_RADIUS , d2);
                        gl.glRotatef(-90, 1.0f, 0.0f, 0.0f);
                        drawCylinder((float)SimulationInfo.PISTON_SHAFT_RADIUS, (float)SimulationInfo.PISTON_SHAFT_RADIUS, (float)(h - pistonPos + SimulationInfo.PISTON_VALVE_RADIUS), prec.x, 1);
                    gl.glPopMatrix();
            }
        }
    }
	
	public void drawPlane(double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4, int cuts)
	{
	    double dx1=x2-x1, dy1=y2-y1, dz1=z2-z1;
	    double dx2=x3-x2, dy2=y3-y2, dz2=z3-z2;
	    double sx1=dx1/cuts, sy1=dy1/cuts, sz1=dz1/cuts;
	    double sx2=dx2/cuts, sy2=dy2/cuts, sz2=dz2/cuts;
		
	    //Draws the wall using a large number of points.
	    for(int i=0;i<cuts;i++)
	    {	
	        for(int j=0;j<cuts;j++)
	        {
	            double x=x1+(sx1*i)+(sx2*j), y=y1+(sy1*i)+(sy2*j), z=z1+(sz1*i)+(sz2*j);
                    vertex(x,y,z);
                    vertex(x+sx1,y+sy1,z+sz1);
                    vertex(x+sx1+sx2,y+sy1+sy2,z+sz1+sz2);
                    vertex(x+sx2,y+sy2,z+sz2);
	        }
	    }
	}
	
	/** 
	 * Renders the current scene in 3D.  This includes drawing
	 * six walls, the divider (if necessary), and any spheres.
	 * @param glDrawable
	 */
	private void display3D()
	{
            try
            {
                //Clear the screen.
                if (inTraceMode)
                    gl.glClear(GL_DEPTH_BUFFER_BIT);
                else
                    gl.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

                gl.glMatrixMode(GL_MODELVIEW);
                gl.glLoadIdentity();
                double w = simulationInfo.arenaXSize;
                double h = simulationInfo.arenaYSize;
                double d = simulationInfo.arenaZSize; // (already in simulation units!)
                double halfWidth = w / 2.0;
                double halfHeight = h / 2.0;
                double halfDepth = d / 2.0;

                //Set the light position.
                floatBuffer = toBuffer(new float[] { 0.0f, 0.0f, (float)renderLength(-camz + d), 1});
                floatBuffer.flip();
                gl.glLightfv(GL_LIGHT1, GL_POSITION, floatBuffer);

                // The camera looks straight down -Z, with +Y up. Its view rotation
                // is identity, so translating by the negative eye position is
                // exactly equivalent to lookAt. JOGL's float lookAt normalizes
                // nanometer-scale vectors to zero and collapses the view matrix.
                translate(-halfWidth, -halfHeight, -camz);

                //Rotate the world to make it look like the camera moved.
                translate(halfWidth,halfHeight,halfDepth);
                gl.glRotated(camAngleY,0,1, 0);
                double angle=camAngleY*Math.PI/180.0;
                gl.glRotated(camAngleX,Math.cos(angle),0, Math.sin(angle));
                translate(-halfWidth,-halfHeight,-halfDepth);

                // Define all half-spaces directly in rendering units. Keeping the
                // modelview rotation unchanged also avoids roundoff from 180-degree turns.
                clipPlane(GL_CLIP_PLANE0, 1, 0, 0, clipDistance);
                clipPlane(GL_CLIP_PLANE1, -1, 0, 0, w + clipDistance);
                clipPlane(GL_CLIP_PLANE2, 0, 1, 0, clipDistance);
                clipPlane(GL_CLIP_PLANE3, 0, -1, 0, h + clipDistance);
                clipPlane(GL_CLIP_PLANE4, 0, 0, 1, clipDistance);
                clipPlane(GL_CLIP_PLANE5, 0, 0, -1, d + clipDistance);

                //Disable the clipping planes until the walls are drawn.
                gl.glDisable(GL_CLIP_PLANE0);
                gl.glDisable(GL_CLIP_PLANE1);
                gl.glDisable(GL_CLIP_PLANE2);
                gl.glDisable(GL_CLIP_PLANE3);
                gl.glDisable(GL_CLIP_PLANE4);
                gl.glDisable(GL_CLIP_PLANE5);

                //Use a disk to draw the walls.
                glu.gluQuadricOrientation(disk, GLU.GLU_OUTSIDE);
                Point prec = getPrecision(simulationInfo.holeDiameter/2);
                double outerRadius = Math.sqrt(Math.pow(halfWidth,2)+Math.pow(halfDepth,2));

                //Redraw the walls.
                if(!inTraceMode) {
                    //Draw the walls further back to avoid z-fighting.
                          gl.glDepthRange(0.01, 1.0);

                    //Box
                    gl.glBegin(GL_QUADS);
                    //Left Side
                    colorComponents = left3DColor.getComponents(null);
                    gl.glColor3f(colorComponents[0], colorComponents[1], colorComponents[2]);
                    gl.glNormal3f(1.0f, 0.0f, 0.0f);
                    drawPlane(0,0,0,0,h,0,0,h,d,0,0,d,10);
                    //Right Side
                    colorComponents = right3DColor.getComponents(null);
                    gl.glColor3f(colorComponents[0], colorComponents[1], colorComponents[2]);
                    gl.glNormal3f(-1.0f, 0.0f, 0.0f);
                    drawPlane(w,0,d,w,h,d,w,h,0,w,0,0,10);
                    //Front Side
                    colorComponents = front3DColor.getComponents(null);
                    gl.glColor3f(colorComponents[0], colorComponents[1], colorComponents[2]);
                    gl.glNormal3f(0.0f, 0.0f, -1.0f);
                    drawPlane(0,0,d,0,h,d,w,h,d,w,0,d,10);
                    //Back Side
                    colorComponents = back3DColor.getComponents(null);
                    gl.glColor3f(colorComponents[0], colorComponents[1], colorComponents[2]);
                    gl.glNormal3f(0.0f, 0.0f, 1.0f);
                    drawPlane(w,0,0,w,h,0,0,h,0,0,0,0,10);
                    //Top Side
                    colorComponents = top3DColor.getComponents(null);
                    gl.glColor3f(colorComponents[0], colorComponents[1], colorComponents[2]);
                    gl.glNormal3f(0.0f, -1.0f, 0.0f);
                    drawPlane(w,h,d,0,h,d,0,h,0,w,h,0,10);
                    //Bottom Side
                    colorComponents = bottom3DColor.getComponents(null);
                    gl.glColor3f(colorComponents[0], colorComponents[1], colorComponents[2]);
                    gl.glNormal3f(0.0f, 1.0f, 0.0f);
                    drawPlane(w,0,0,0,0,0,0,0,d,w,0,d,10);
                    gl.glEnd();

                    //Draw everything else closer up to avoid z-fighting.
                          gl.glDepthRange(0.0, 0.99);
                }
	
			//Draw the piston.
        	if (simulationInfo.arenaType == ArenaType.MOVABLE_PISTON) {
        		drawPiston();
                }
        	
//        	//Re-enable the clipping planes before spheres are added.
                gl.glEnable(GL_CLIP_PLANE0);
                gl.glEnable(GL_CLIP_PLANE1);
                gl.glEnable(GL_CLIP_PLANE2);
                gl.glEnable(GL_CLIP_PLANE3);
                gl.glEnable(GL_CLIP_PLANE4);
                gl.glEnable(GL_CLIP_PLANE5);
			
			//Draw the divider.
                colorComponents = divColor.getComponents(null);
                gl.glColor4f(colorComponents[0], colorComponents[1], colorComponents[2], colorComponents[3]);

    		if (simulationInfo.arenaType == ArenaType.DIVIDED_ARENA || 
                        (simulationInfo.maxwellDemonMode && !currentFrame.isArenaHoleOpen())) {
    			//Save matrix state.
                        gl.glPushMatrix();
                        double dr=SimulationInfo.ARENA_DIVIDER_RADIUS;
        		
        		//Divider (cube)
                        //translate(halfWidth, halfHeight, halfDepth);
                        //gl.glScaled(SimulationInfo.ARENA_DIVIDER_RADIUS * 2.0, h, d);
                        //glutSolidCube(1.0f);
                        gl.glBegin(GL_QUADS);
                        drawPlane(halfWidth+dr,0,d,halfWidth+dr,h,d,halfWidth-dr,h,d,halfWidth-dr,0,d,10);
                        drawPlane(halfWidth-dr,0,0,halfWidth-dr,h,0,halfWidth+dr,h,0,halfWidth+dr,0,0,10);
                        drawPlane(halfWidth+dr,h,0,halfWidth-dr,h,0,halfWidth-dr,h,d,halfWidth+dr,h,d,10);
                        drawPlane(halfWidth+dr,0,d,halfWidth-dr,0,d,halfWidth-dr,0,0,halfWidth+dr,0,0,10);
                        gl.glEnd();
                        translate(halfWidth+SimulationInfo.ARENA_DIVIDER_RADIUS,halfHeight,halfDepth);
                        gl.glRotatef(90,0,1,0);
                                glu.gluQuadricOrientation(disk, GLU.GLU_OUTSIDE);
                        drawDisk(0.0f, (float)outerRadius, prec.x*2, prec.y);
                                //Left Wall (with hole)
                        translate(0,0,-2*SimulationInfo.ARENA_DIVIDER_RADIUS);
                                glu.gluQuadricOrientation(disk, GLU.GLU_OUTSIDE);
                                glu.gluQuadricOrientation(disk, GLU.GLU_INSIDE);
                        drawDisk(0.0f, (float)outerRadius, prec.x*2, prec.y);
        		
    			//Restore matrix state.
                        gl.glPopMatrix();
	        } else if (simulationInfo.arenaType == ArenaType.DIVIDED_ARENA_WITH_HOLE) {
                    //Save matrix state.
                    gl.glPushMatrix();

                    //Draw the small sides of the divider.
                    double dr=SimulationInfo.ARENA_DIVIDER_RADIUS;
                    gl.glBegin(GL_QUADS);
                    drawPlane(halfWidth+dr,0,d,halfWidth+dr,h,d,halfWidth-dr,h,d,halfWidth-dr,0,d,10);
                    drawPlane(halfWidth-dr,0,0,halfWidth-dr,h,0,halfWidth+dr,h,0,halfWidth+dr,0,0,10);
                    drawPlane(halfWidth+dr,h,0,halfWidth-dr,h,0,halfWidth-dr,h,d,halfWidth+dr,h,d,10);
                    drawPlane(halfWidth+dr,0,d,halfWidth-dr,0,d,halfWidth-dr,0,0,halfWidth+dr,0,0,10);
                    gl.glEnd();

                    //Right Wall (with hole)
                    translate(halfWidth+SimulationInfo.ARENA_DIVIDER_RADIUS,halfHeight,halfDepth);
                    gl.glRotatef(90,0,1,0);
                            glu.gluQuadricOrientation(disk, GLU.GLU_OUTSIDE);
                    drawDisk((float)(simulationInfo.holeDiameter/2.0), (float)outerRadius, prec.x*2, prec.y);

                            //Left Wall (with hole)
                    translate(0,0,-2*SimulationInfo.ARENA_DIVIDER_RADIUS);
                            glu.gluQuadricOrientation(disk, GLU.GLU_OUTSIDE);
                            glu.gluQuadricOrientation(disk, GLU.GLU_INSIDE);
                    drawDisk((float)(simulationInfo.holeDiameter/2.0), (float)outerRadius, prec.x*2, prec.y);

                        //Ring in Middle
                    translate(0,0,SimulationInfo.ARENA_DIVIDER_RADIUS);
                    
                    prec = getPrecision(simulationInfo.holeDiameter / 2.0 + SimulationInfo.ARENA_DIVIDER_RADIUS * 2.0);
                    
                    Point prec2 = getPrecision(SimulationInfo.ARENA_DIVIDER_RADIUS);
                    glutSolidTorus((float)SimulationInfo.ARENA_DIVIDER_RADIUS, (float)(simulationInfo.holeDiameter/2.0), prec2.x, prec.x + 5);

                    //Restore matrix state.
                    gl.glPopMatrix();
                }

	    	//Draw all the spheres.
                drawSpheres();
		    
                gl.glFlush();
	        if (traceMode) {
	        	inTraceMode = true;
                } else {
        		inTraceMode = false;
                }
	     } catch(Exception e) {
	     	throw new RuntimeException(null, e);
	     }
	}
        /** 
	 * Pure OpenGL implementation of GLUT's method of the same name, since
	 * LWJGL, unlike the previously used JOGL library, doesn't use GLUT. From
         * http://pastebin.com/yPryH519
	 */
        private void glutSolidCube(float size)
        {
            float[][] vertices = {
                {-0.5f, -0.5f, -0.5f}, // 0
                {0.5f, -0.5f, -0.5f},
                {0.5f, 0.5f, -0.5f},
                {-0.5f, 0.5f, -0.5f}, // 3
                {-0.5f, -0.5f, 0.5f}, // 4
                {0.5f, -0.5f, 0.5f},
                {0.5f, 0.5f, 0.5f},
                {-0.5f, 0.5f, 0.5f} // 7
            }, normals = {
                {0, 0, -1},
                {0, 0, 1},
                {0, -1, 0},
                {0, 1, 0},
                {-1, 0, 0},
                {1, 0, 0}
            };
            byte[][] indices = {
                {0, 3, 2, 1},
                {4, 5, 6, 7},
                {0, 1, 5, 4},
                {3, 7, 6, 2},
                {0, 4, 7, 3},
                {1, 2, 6, 5}
            };
            for (int i = 0; i < 6; i++) {
                gl.glBegin(GL_QUADS);
                for (int m = 0; m < 4; m++) {
                    float[] temp = vertices[indices[i][m]];
                    gl.glNormal3f(normals[i][0], normals[i][1], normals[i][2]);
                    gl.glVertex3f(temp[0] * size, temp[1] * size, temp[2] * size);
                }
                gl.glEnd();
            }
        }
        
        /** 
	 * Pure OpenGL implementation of GLUT's method of the same name, since
	 * LWJGL, unlike the previously used JOGL library, doesn't use GLUT. From
         * http://dev.eclipse.org/viewcvs/viewvc.cgi/org.eclipse.swt.snippets/src/org/eclipse/swt/snippets/Snippet195.java?view=co
	 */
        private void glutSolidTorus(float innerRadius, float outerRadius, int nsides, int rings)
        {
            innerRadius = (float) renderLength(innerRadius);
            outerRadius = (float) renderLength(outerRadius);
	    float ringDelta = 2.0f * (float) Math.PI / rings;
	    float sideDelta = 2.0f * (float) Math.PI / nsides;
	    float theta = 0.0f, cosTheta = 1.0f, sinTheta = 0.0f;
	    for (int i = rings - 1; i >= 0; i--) {
	        float theta1 = theta + ringDelta;
	        float cosTheta1 = (float) Math.cos(theta1);
	        float sinTheta1 = (float) Math.sin(theta1);
                gl.glBegin(GL_QUAD_STRIP);
	        float phi = 0.0f;
	        for (int j = nsides; j >= 0; j--) {
	            phi += sideDelta;
	            float cosPhi = (float) Math.cos(phi);
	            float sinPhi = (float) Math.sin(phi);
	            float dist = outerRadius + innerRadius * cosPhi;
                    gl.glNormal3f(cosTheta1 * cosPhi, -sinTheta1 * cosPhi, sinPhi);
                    gl.glVertex3f(cosTheta1 * dist, -sinTheta1 * dist, innerRadius * sinPhi);
                    gl.glNormal3f(cosTheta * cosPhi, -sinTheta * cosPhi, sinPhi);
                    gl.glVertex3f(cosTheta * dist, -sinTheta * dist, innerRadius * sinPhi);
	        }
                gl.glEnd();
	        theta = theta1;
	        cosTheta = cosTheta1;
	        sinTheta = sinTheta1;
            }
        }

	/**
	 * Renders the scene in 2D by drawing the divider if necessary and then
	 * calls drawSpheres.  This also handles 1D mode.
	 * 
	 * @param glDrawable
	 */
	private void display2D()
	{
		// TODO: These are in pixels (this frameTime), so zoom needs to be taken into account whenever they are used!
		//float w=getSize().width;
		double w2=simulationInfo.arenaXSize / 2.0;
                double h=simulationInfo.arenaYSize;
                
		try
		{
			//Clear the screen.
			if (inTraceMode) {
                            gl.glClear(GL_DEPTH_BUFFER_BIT);
                        } else {
                            gl.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
			}
			inTraceMode = traceMode;
                        gl.glMatrixMode(GL_MODELVIEW);
                        gl.glLoadIdentity();
                        
			//Draw the piston.
			if (simulationInfo.arenaType == ArenaType.MOVABLE_PISTON) {
                            drawPiston();
                        } else if (simulationInfo.arenaType == ArenaType.DIVIDED_ARENA || 
                                (simulationInfo.maxwellDemonMode && !currentFrame.isArenaHoleOpen())) { //If there is a divider, then draw it using a cylinder.
                            colorComponents = divColor.getComponents(null);
                            gl.glColor3f(colorComponents[0], colorComponents[1], colorComponents[2]);
                            Point prec=getPrecision(SimulationInfo.ARENA_DIVIDER_RADIUS);
                            gl.glPushMatrix();
                                    translate(w2, h, 0);
                                    gl.glRotatef(90,1.0f,0.0f, 0.0f);
                                    drawCylinder((float)SimulationInfo.ARENA_DIVIDER_RADIUS, (float)SimulationInfo.ARENA_DIVIDER_RADIUS, (float)h*2, prec.x, 1);
                            gl.glPopMatrix();
			} else if (simulationInfo.arenaType == ArenaType.DIVIDED_ARENA_WITH_HOLE &&
                                simulationInfo.dimension > 1) {
                            //Draw the divider with a hole by using 2 cylinders and 2 spheres.
                            double holeDiameter = simulationInfo.holeDiameter;
                            colorComponents = divColor.getComponents(null);
                            gl.glColor3f(colorComponents[0], colorComponents[1], colorComponents[2]);
                            Point prec=getPrecision(SimulationInfo.ARENA_DIVIDER_RADIUS);
                            gl.glPushMatrix();
                                    translate(w2, h, 0);
                                    gl.glRotatef(90,1.0f,0.0f, 0.0f);
                                    drawCylinder((float)SimulationInfo.ARENA_DIVIDER_RADIUS, (float)SimulationInfo.ARENA_DIVIDER_RADIUS, (float)((h-holeDiameter)/2-SimulationInfo.ARENA_DIVIDER_RADIUS), prec.x, 1);
                            gl.glPopMatrix();
                            gl.glPushMatrix();
                                    translate(w2, ((h+holeDiameter)/2+SimulationInfo.ARENA_DIVIDER_RADIUS), 0);
                                    drawSphereSurface((float)SimulationInfo.ARENA_DIVIDER_RADIUS, prec.x, prec.y);
                            gl.glPopMatrix();
                            gl.glPushMatrix();
                                    translate(w2, ((h-holeDiameter)/2-SimulationInfo.ARENA_DIVIDER_RADIUS), 0);
                                    gl.glRotatef(90,1.0f,0.0f, 0.0f);
                                    drawCylinder((float)SimulationInfo.ARENA_DIVIDER_RADIUS, (float)SimulationInfo.ARENA_DIVIDER_RADIUS, (float)((h-holeDiameter)/2-SimulationInfo.ARENA_DIVIDER_RADIUS), prec.x, 1);
                            gl.glPopMatrix();
                            gl.glPushMatrix();
                                    translate(w2, ((h-holeDiameter)/2-SimulationInfo.ARENA_DIVIDER_RADIUS), 0);
                                    drawSphereSurface((float)SimulationInfo.ARENA_DIVIDER_RADIUS, prec.x, prec.y);
                            gl.glPopMatrix();
			}
			
			//Draw the spheres.                       
			drawSpheres();
			
                        gl.glFlush();
		}
	     catch(Exception e) {
	     	throw new RuntimeException(null, e);
	     }
	}

	//Calls the appropriate display function depending on the current dimension.
	public void display()
	{
                if (simulationInfo.maxwellDemonMode) {
                    arenaHoleOpen = currentFrame.isArenaHoleOpen();
                }
                
                if (dimensionChanged) {
                    dimensionChanged = false;
                    rescaleGLArena();
                }
		//Rotate the arena due to the arrow keys being pressed, if necessary.
		rotate();

                if (simulationInfo.dimension != 3) {
                    gl.glScaled(1.0 / simulationInfo.arenaXSize, 1.0 / simulationInfo.arenaXSize, 1.0);
                }
                
		//Update the point size.
		if (tracePointSize!=curPointSize) {
			curPointSize = tracePointSize;
                        gl.glPointSize(curPointSize);
		}
		//Set the background clear color.
		float[] comps=background.getComponents(null);
                gl.glClearColor(comps[0],comps[1],comps[2],comps[3]);
                
		//Switch lighting modes, if necessary.
		if (!lightsOn&&lighting) {
                    gl.glEnable(GL_LIGHTING);
                    lightsOn=true;
		} else if(lightsOn&&!lighting&& simulationInfo.dimension == 2) {
                    gl.glDisable(GL_LIGHTING);
                    lightsOn=false;
		}		
		
		//Call the appropriate display function.
		if (simulationInfo.dimension == 3) {
                    gl.glEnable(GL_LIGHT1);
                    gl.glDisable(GL_LIGHT2);
                    gl.glEnable(GL_CLIP_PLANE0);
                    gl.glEnable(GL_CLIP_PLANE1);
                    gl.glEnable(GL_CLIP_PLANE2);
                    gl.glEnable(GL_CLIP_PLANE3);
                    gl.glEnable(GL_CLIP_PLANE4);
                    gl.glEnable(GL_CLIP_PLANE5);
                    gl.glEnable(GL_LIGHTING);
                    lightsOn = true;
                    display3D();
                } else {
                    gl.glDisable(GL_LIGHT0);
                    gl.glDisable(GL_LIGHT1);
                    gl.glEnable(GL_LIGHT2);
                    gl.glDisable(GL_CLIP_PLANE0);
                    gl.glDisable(GL_CLIP_PLANE1);
                    gl.glDisable(GL_CLIP_PLANE2);
                    gl.glDisable(GL_CLIP_PLANE3);
                    gl.glDisable(GL_CLIP_PLANE4);
                    gl.glDisable(GL_CLIP_PLANE5);
                    display2D();
                }
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

            inTraceMode = false;
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
	
        public void rescaleGLArena() {            
            //reshapeThis(0, 0, canvas.getWidth(), canvas.getHeight());
        }
        
	/** Called during zooms, arena resizes, and program initialization. */
	public void reshapeThis(int x, int y, int width, int height) {
		//Change the viewport.
                gl.glViewport(0, 0, width, height);
                gl.glMatrixMode(GL_PROJECTION);
                gl.glLoadIdentity();

                double viewingAngle = 30.0;
                double complementTangent = Math.tan(Math.PI * (90.0 - viewingAngle / 2.0) / 180.0);
                camz = (simulationInfo.arenaYSize / 2.0) * complementTangent + simulationInfo.arenaZSize;

                double ySize = Units.convert("m", "nm", simulationInfo.arenaYSize);
                double xSize = Units.convert("m", "nm", simulationInfo.arenaXSize);
                
		//Set the projection mode.
                if (simulationInfo.dimension != 3) {
                    if (simulationInfo.dimension == 2) {
                        gl.glOrtho(0, 1.0, 0, ySize / xSize, -200-simulationInfo.arenaZSize, 200);
                    } else {
                        ySize = xSize / width * height;
                        gl.glOrtho(0, 1.0, 0, ySize / xSize, -200-simulationInfo.arenaZSize, 200);
                    }
                } else {
                    // Geometry, camera and clipping distances now all use nanometers.
                    // Build the frustum directly instead of multiplying a projection by 1e8.
                    double extent = Math.sqrt(xSize * xSize + ySize * ySize
                            + Math.pow(renderLength(simulationInfo.arenaZSize), 2));
                    double near = Math.max(0.001, extent * 0.0001);
                    double far = renderLength(camz) + extent;
                    double top = near * Math.tan(Math.toRadians(viewingAngle / 2.0));
                    double right = top * width / height;
                    gl.glFrustum(-right, right, -top, top, near, far);
                }
                
                // set readings on scale sidebars
                double verticalScale = ySize / canvas.getHeight();
                double horizontalScale = xSize / canvas.getWidth();
                if (height == 0 || width == 0) { 
                    verticalScale = 0;
                    horizontalScale = 0;
                }
                verticalScalePanel.setScale(verticalScale);
                horizontalScalePanel.setScale(horizontalScale);
                
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        revalidate();
                    }
                });
	}

        boolean dimensionChanged = false;
        
        public void setSimulationInfo(SimulationInfo info){
            if (simulationInfo.dimension != info.dimension) {
                dimensionChanged = true;
            }

            simulationInfo = info;
        }

        public void rescaleArena(int availableWidth, int availableHeight) {
            int oldHeight = getHeight();
            int oldWidth = getWidth();
            
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

            if (oldHeight == getHeight() && oldWidth == getWidth()) {
                //this.revalidate(); // Make sure the event to call reshapeThis() is fired so the glPanel is rescaled
            }
        }
    
    public void drawFrame() {
        // JOGL acquires/releases the context and serializes rendering with AWT.
        canvas.display();
    }

    public void destroyGLContext() {
        // The dispose callback releases resources while the context is current.
        canvas.destroy();
    }

    private void deleteQuadrics() {
        if (sphere != null) {
            glu.gluDeleteQuadric(sphere);
            sphere = null;
        }
        if (cylinder != null) {
            glu.gluDeleteQuadric(cylinder);
            cylinder = null;
        }
        if (disk != null) {
            glu.gluDeleteQuadric(disk);
            disk = null;
        }
    }

    public void setNextFrameToDisplay(FrameInfo frame) {
        currentFrame = frame;
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
            private int step=50;


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
    
    public void setParticleColorer(ParticleColorer colorer) {
        this.colorer = colorer;
    }
}

