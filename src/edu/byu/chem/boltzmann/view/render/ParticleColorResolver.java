/*
 * Boltzmann 3D, a kinetic theory demonstrator
 * Copyright (C) 2013 Dr. Randall B. Shirts
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package edu.byu.chem.boltzmann.view.render;

import edu.byu.chem.boltzmann.model.physics.PartState;
import java.awt.Color;

/** Resolves a simulation particle state to the color shown by the renderer. */
public interface ParticleColorResolver {
    Color getParticleColor(PartState state);
}
