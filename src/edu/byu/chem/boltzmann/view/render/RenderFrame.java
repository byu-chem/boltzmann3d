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
package edu.byu.chem.boltzmann.view.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable, graphics-library-independent description of one simulation view.
 * Positions, lengths, and camera distance are in meters; simulation time is in
 * seconds. Colors contain normalized red, green, blue, and alpha components.
 */
public final class RenderFrame {

    public final double simulationTimeSeconds;
    public final int dimension;
    public final List<Particle> particles;
    public final Arena arena;
    public final Camera camera;
    public final Trace trace;
    public final Palette palette;
    public final boolean lightingEnabled;

    public RenderFrame(double simulationTimeSeconds, int dimension,
            List<Particle> particles, Arena arena, Camera camera, Trace trace,
            Palette palette, boolean lightingEnabled) {
        this.simulationTimeSeconds = simulationTimeSeconds;
        this.dimension = dimension;
        this.particles = Collections.unmodifiableList(new ArrayList<Particle>(particles));
        this.arena = arena;
        this.camera = camera;
        this.trace = trace;
        this.palette = palette;
        this.lightingEnabled = lightingEnabled;
    }

    /** One particle's visible state, including any periodic-boundary images. */
    public static final class Particle {
        public final double xMeters;
        public final double yMeters;
        public final double zMeters;
        public final double radiusMeters;
        public final Color color;
        public final boolean hasImageOnLeft;
        public final boolean hasImageOnBottom;
        public final boolean hasImageAtBack;

        public Particle(double xMeters, double yMeters, double zMeters,
                double radiusMeters, Color color, boolean hasImageOnLeft,
                boolean hasImageOnBottom, boolean hasImageAtBack) {
            this.xMeters = xMeters;
            this.yMeters = yMeters;
            this.zMeters = zMeters;
            this.radiusMeters = radiusMeters;
            this.color = color;
            this.hasImageOnLeft = hasImageOnLeft;
            this.hasImageOnBottom = hasImageOnBottom;
            this.hasImageAtBack = hasImageAtBack;
        }
    }

    /** Arena shape, physical bounds, and the current piston/divider state. */
    public static final class Arena {
        public enum Shape {
            PERIODIC,
            REFLECTING,
            DIVIDED,
            DIVIDED_WITH_HOLE,
            MOVABLE_PISTON
        }

        public final Shape shape;
        public final double widthMeters;
        public final double heightMeters;
        public final double depthMeters;
        public final double holeDiameterMeters;
        public final double dividerThicknessMeters;
        public final boolean maxwellDemonMode;
        public final boolean dividerHoleOpen;
        public final Piston piston;

        public Arena(Shape shape, double widthMeters, double heightMeters,
                double depthMeters, double holeDiameterMeters,
                double dividerThicknessMeters, boolean maxwellDemonMode,
                boolean dividerHoleOpen, Piston piston) {
            this.shape = shape;
            this.widthMeters = widthMeters;
            this.heightMeters = heightMeters;
            this.depthMeters = depthMeters;
            this.holeDiameterMeters = holeDiameterMeters;
            this.dividerThicknessMeters = dividerThicknessMeters;
            this.maxwellDemonMode = maxwellDemonMode;
            this.dividerHoleOpen = dividerHoleOpen;
            this.piston = piston;
        }
    }

    /** Current piston placement and its physical dimensions. */
    public static final class Piston {
        public final boolean visible;
        public final double positionMeters;
        public final double valveRadiusMeters;
        public final double shaftRadiusMeters;

        public Piston(boolean visible, double positionMeters,
                double valveRadiusMeters, double shaftRadiusMeters) {
            this.visible = visible;
            this.positionMeters = positionMeters;
            this.valveRadiusMeters = valveRadiusMeters;
            this.shaftRadiusMeters = shaftRadiusMeters;
        }
    }

    /** Camera pose and projection setting; angles are in degrees. */
    public static final class Camera {
        public enum Projection {
            ORTHOGRAPHIC,
            PERSPECTIVE
        }

        public final double distanceMeters;
        public final double rotationXDegrees;
        public final double rotationYDegrees;
        public final Projection projection;
        public final double verticalFieldOfViewDegrees;

        public Camera(double distanceMeters, double rotationXDegrees,
                double rotationYDegrees, Projection projection,
                double verticalFieldOfViewDegrees) {
            this.distanceMeters = distanceMeters;
            this.rotationXDegrees = rotationXDegrees;
            this.rotationYDegrees = rotationYDegrees;
            this.projection = projection;
            this.verticalFieldOfViewDegrees = verticalFieldOfViewDegrees;
        }
    }

    /**
     * Trace display settings. Historical positions remain renderer-owned
     * presentation state rather than simulation coordinates in this frame.
     */
    public static final class Trace {
        public final boolean enabled;
        public final boolean accumulating;
        public final int pointSize;
        public final long resetGeneration;

        public Trace(boolean enabled, boolean accumulating, int pointSize) {
            this(enabled, accumulating, pointSize, 0L);
        }

        public Trace(boolean enabled, boolean accumulating, int pointSize,
                long resetGeneration) {
            this.enabled = enabled;
            this.accumulating = accumulating;
            this.pointSize = pointSize;
            this.resetGeneration = resetGeneration;
        }
    }

    /** Colors used for the arena surfaces and background. */
    public static final class Palette {
        public final Color background;
        public final Color leftWall;
        public final Color rightWall;
        public final Color topWall;
        public final Color bottomWall;
        public final Color frontWall;
        public final Color backWall;
        public final Color divider;
        public final Color piston;
        public final Color pistonInThreeDimensions;

        public Palette(Color background, Color leftWall, Color rightWall,
                Color topWall, Color bottomWall, Color frontWall, Color backWall,
                Color divider, Color piston, Color pistonInThreeDimensions) {
            this.background = background;
            this.leftWall = leftWall;
            this.rightWall = rightWall;
            this.topWall = topWall;
            this.bottomWall = bottomWall;
            this.frontWall = frontWall;
            this.backWall = backWall;
            this.divider = divider;
            this.piston = piston;
            this.pistonInThreeDimensions = pistonInThreeDimensions;
        }
    }

    /** Normalized RGBA color value, independent of AWT and OpenGL types. */
    public static final class Color {
        public final float red;
        public final float green;
        public final float blue;
        public final float alpha;

        public Color(float red, float green, float blue, float alpha) {
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.alpha = alpha;
        }
    }
}
