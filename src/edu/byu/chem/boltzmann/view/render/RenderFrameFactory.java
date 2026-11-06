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

import edu.byu.chem.boltzmann.model.physics.FrameInfo;
import edu.byu.chem.boltzmann.model.physics.PartState;
import edu.byu.chem.boltzmann.model.physics.Particle;
import edu.byu.chem.boltzmann.model.physics.Wall;
import edu.byu.chem.boltzmann.utils.data.SimulationInfo;
import edu.byu.chem.boltzmann.utils.data.SimulationInfo.ArenaType;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** Converts the simulation model and display settings into an immutable view snapshot. */
public final class RenderFrameFactory {

    private RenderFrameFactory() {
    }

    public static RenderFrame create(FrameInfo frameInfo, SimulationInfo simulationInfo,
            RenderFrame.Camera camera, RenderFrame.Trace trace,
            RenderFrame.Palette palette, boolean lightingEnabled,
            ParticleColorResolver colorResolver) {
        if (frameInfo == null) {
            throw new IllegalArgumentException("frameInfo must not be null");
        }
        if (simulationInfo == null) {
            throw new IllegalArgumentException("simulationInfo must not be null");
        }

        List<RenderFrame.Particle> particles = new ArrayList<RenderFrame.Particle>();
        for (Particle particle : frameInfo.getParticles()) {
            PartState state = frameInfo.getParticleState(particle);
            if (state == null) {
                continue;
            }

            Color particleColor = colorResolver == null
                    ? state.color : colorResolver.getParticleColor(state);
            if (particleColor == null) {
                particleColor = state.color;
            }
            int boundaryFlags = state.bFlag;
            particles.add(new RenderFrame.Particle(
                    state.x, state.y, state.z, state.rad, color(particleColor),
                    (boundaryFlags & Wall.LEFT) == Wall.LEFT,
                    (boundaryFlags & Wall.BOTTOM) == Wall.BOTTOM,
                    (boundaryFlags & Wall.BACK) == Wall.BACK));
        }

        boolean hasPiston = simulationInfo.arenaType == ArenaType.MOVABLE_PISTON;
        double pistonPosition = hasPiston
                ? frameInfo.getPistonPosition(frameInfo.endTime) : 0.0;
        RenderFrame.Piston piston = new RenderFrame.Piston(
                hasPiston, pistonPosition, SimulationInfo.PISTON_VALVE_RADIUS,
                SimulationInfo.PISTON_SHAFT_RADIUS);

        RenderFrame.Arena arena = new RenderFrame.Arena(
                arenaShape(simulationInfo.arenaType),
                simulationInfo.arenaXSize,
                simulationInfo.dimension > 1 ? simulationInfo.arenaYSize : 0.0,
                simulationInfo.dimension > 2 ? simulationInfo.arenaZSize : 0.0,
                simulationInfo.holeDiameter,
                SimulationInfo.ARENA_DIVIDER_RADIUS * 2.0,
                simulationInfo.maxwellDemonMode,
                frameInfo.isArenaHoleOpen(),
                piston);

        return new RenderFrame(frameInfo.endTime, simulationInfo.dimension,
                particles, arena, camera, trace, palette, lightingEnabled);
    }

    private static RenderFrame.Arena.Shape arenaShape(ArenaType arenaType) {
        switch (arenaType) {
            case PERIODIC_BOUNDARIES:
                return RenderFrame.Arena.Shape.PERIODIC;
            case DIVIDED_ARENA:
                return RenderFrame.Arena.Shape.DIVIDED;
            case DIVIDED_ARENA_WITH_HOLE:
                return RenderFrame.Arena.Shape.DIVIDED_WITH_HOLE;
            case MOVABLE_PISTON:
                return RenderFrame.Arena.Shape.MOVABLE_PISTON;
            case REFLECTING_BOUNDARIES:
            default:
                return RenderFrame.Arena.Shape.REFLECTING;
        }
    }

    private static RenderFrame.Color color(Color color) {
        if (color == null) {
            return new RenderFrame.Color(0.0f, 0.0f, 0.0f, 1.0f);
        }
        return new RenderFrame.Color(color.getRed() / 255.0f,
                color.getGreen() / 255.0f, color.getBlue() / 255.0f,
                color.getAlpha() / 255.0f);
    }
}
