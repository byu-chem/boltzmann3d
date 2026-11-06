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

/**
 * Backend-neutral lifecycle for drawing a {@link RenderFrame}.
 *
 * Implementations own their graphics surface and any backend-specific context
 * callbacks. This interface describes only the scene and its lifecycle; it
 * does not expose graphics API objects, constants, or resource handles.
 */
public interface Renderer {

    /** Prepare backend resources before the first frame is drawn. */
    void initialize();

    /** Notify the renderer that its drawable area changed, in pixels. */
    void resizeSurface(int width, int height);

    /** Draw one immutable scene snapshot. */
    void render(RenderFrame frame);

    /** Release backend resources. Safe to call as part of surface disposal. */
    void dispose();
}
