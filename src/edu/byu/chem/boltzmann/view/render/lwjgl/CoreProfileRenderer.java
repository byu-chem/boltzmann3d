/*
 * Boltzmann 3D, a kinetic theory demonstrator
 * Copyright (C) 2013 Dr. Randall B. Shirts
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package edu.byu.chem.boltzmann.view.render.lwjgl;

import edu.byu.chem.boltzmann.view.render.RenderFrame;
import edu.byu.chem.boltzmann.view.render.Renderer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

/**
 * Core-profile renderer. It owns GL resources and draws from backend-neutral
 * frame snapshots; all rendering GL calls stay inside this implementation.
 */
public final class CoreProfileRenderer implements Renderer {

    private static final int SPHERE_SLICES = 24;
    private static final int SPHERE_STACKS = 16;
    private static final double METERS_TO_NANOMETERS = 1.0e9;
    private static final RenderFrame.Color HOLE_EDGE_COLOR =
            new RenderFrame.Color(0.5f, 0.5f, 0.5f, 1.0f);

    private static final String VERTEX_SHADER =
            "#version 150 core\n"
            + "in vec3 inPosition;\n"
            + "in vec3 inNormal;\n"
            + "in vec4 inColor;\n"
            + "uniform mat4 uMvp;\n"
            + "uniform mat4 uModelView;\n"
            + "uniform mat4 uModel;\n"
            + "uniform vec4 uTint;\n"
            + "uniform int uUseVertexColor;\n"
            + "uniform float uPointSize;\n"
            + "uniform int uClipParticles;\n"
            + "uniform int uClipDimension;\n"
            + "uniform vec3 uArenaSize;\n"
            + "out vec4 passColor;\n"
            + "out vec3 passNormal;\n"
            + "void main() {\n"
            + "    gl_Position = uMvp * vec4(inPosition, 1.0);\n"
            + "    passColor = uUseVertexColor == 1 ? inColor : uTint;\n"
            + "    passNormal = mat3(uModelView) * inNormal;\n"
            + "    gl_PointSize = uPointSize;\n"
            + "    vec3 worldPosition = (uModel * vec4(inPosition, 1.0)).xyz;\n"
            + "    gl_ClipDistance[0] = uClipParticles == 1 ? worldPosition.x : 1.0;\n"
            + "    gl_ClipDistance[1] = uClipParticles == 1 ? uArenaSize.x - worldPosition.x : 1.0;\n"
            + "    gl_ClipDistance[2] = uClipParticles == 1 && uClipDimension > 1 ? worldPosition.y : 1.0;\n"
            + "    gl_ClipDistance[3] = uClipParticles == 1 && uClipDimension > 1 ? uArenaSize.y - worldPosition.y : 1.0;\n"
            + "    gl_ClipDistance[4] = uClipParticles == 1 && uClipDimension > 2 ? worldPosition.z : 1.0;\n"
            + "    gl_ClipDistance[5] = uClipParticles == 1 && uClipDimension > 2 ? uArenaSize.z - worldPosition.z : 1.0;\n"
            + "}\n";

    private static final String FRAGMENT_SHADER =
            "#version 150 core\n"
            + "in vec4 passColor;\n"
            + "in vec3 passNormal;\n"
            + "uniform int uLightingEnabled;\n"
            + "out vec4 fragColor;\n"
            + "void main() {\n"
            + "    float shade = 1.0;\n"
            + "    if (uLightingEnabled == 1) {\n"
            + "        vec3 normal = normalize(passNormal);\n"
            + "        vec3 lightDirection = normalize(vec3(-0.35, 0.65, 1.0));\n"
            + "        shade = 0.28 + 0.72 * max(dot(normal, lightDirection), 0.0);\n"
            + "    }\n"
            + "    fragColor = vec4(passColor.rgb * shade, passColor.a);\n"
            + "}\n";

    private static final String COMPOSITE_VERTEX_SHADER =
            "#version 150 core\n"
            + "in vec2 inPosition;\n"
            + "in vec2 inTexCoord;\n"
            + "out vec2 passTexCoord;\n"
            + "void main() {\n"
            + "    gl_Position = vec4(inPosition, 0.0, 1.0);\n"
            + "    passTexCoord = inTexCoord;\n"
            + "}\n";

    private static final String COMPOSITE_FRAGMENT_SHADER =
            "#version 150 core\n"
            + "uniform sampler2D uTraceTexture;\n"
            + "in vec2 passTexCoord;\n"
            + "out vec4 fragColor;\n"
            + "void main() {\n"
            + "    fragColor = texture(uTraceTexture, passTexCoord);\n"
            + "}\n";

    private int program;
    private int sphereVertexArray;
    private int sphereVertexBuffer;
    private int sphereIndexBuffer;
    private int sphereIndexCount;
    private int wallVertexArray;
    private int wallVertexBuffer;
    private int traceVertexArray;
    private int traceVertexBuffer;
    private int traceCompositeVertexArray;
    private int traceCompositeVertexBuffer;
    private int traceCompositeProgram;
    private int traceTextureLocation;
    private final int[] traceFramebuffers = new int[2];
    private final int[] traceTextures = new int[2];
    private int traceTargetWidth;
    private int traceTargetHeight;
    private int traceReadIndex;
    private boolean traceWasEnabled;
    private long previousTraceResetGeneration = Long.MIN_VALUE;
    private int previousTraceDimension = -1;
    private double previousTraceRotationX = Double.NaN;
    private double previousTraceRotationY = Double.NaN;
    private double previousTraceSimulationTime = Double.NaN;
    private RenderFrame.Camera.Projection previousTraceProjection;
    private int viewportWidth = 1;
    private int viewportHeight = 1;

    private int mvpLocation;
    private int modelViewLocation;
    private int modelLocation;
    private int tintLocation;
    private int useVertexColorLocation;
    private int lightingLocation;
    private int pointSizeLocation;
    private int clipParticlesLocation;
    private int clipDimensionLocation;
    private int arenaSizeLocation;

    @Override
    public void initialize() {
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        mvpLocation = GL20.glGetUniformLocation(program, "uMvp");
        modelViewLocation = GL20.glGetUniformLocation(program, "uModelView");
        modelLocation = GL20.glGetUniformLocation(program, "uModel");
        tintLocation = GL20.glGetUniformLocation(program, "uTint");
        useVertexColorLocation = GL20.glGetUniformLocation(program, "uUseVertexColor");
        lightingLocation = GL20.glGetUniformLocation(program, "uLightingEnabled");
        pointSizeLocation = GL20.glGetUniformLocation(program, "uPointSize");
        clipParticlesLocation = GL20.glGetUniformLocation(program, "uClipParticles");
        clipDimensionLocation = GL20.glGetUniformLocation(program, "uClipDimension");
        arenaSizeLocation = GL20.glGetUniformLocation(program, "uArenaSize");

        createSphereMesh();
        createWallBuffer();
        createTracePointBuffer();
        createTraceCompositeResources();

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glCullFace(GL11.GL_BACK);
        GL11.glEnable(GL32.GL_PROGRAM_POINT_SIZE);
        setClipDistancesEnabled(true);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public void resizeSurface(int width, int height) {
        viewportWidth = Math.max(1, width);
        viewportHeight = Math.max(1, height);
        GL11.glViewport(0, 0, viewportWidth, viewportHeight);
    }

    @Override
    public void render(RenderFrame frame) {
        if (frame == null || program == 0) {
            return;
        }

        GL11.glClearColor(frame.palette.background.red,
                frame.palette.background.green,
                frame.palette.background.blue,
                frame.palette.background.alpha);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        float[] view = viewMatrix(frame);
        float[] projection = projectionMatrix(frame);
        GL20.glUseProgram(program);
        GL20.glUniform1i(lightingLocation,
                frame.dimension == 3 || frame.lightingEnabled ? 1 : 0);
        GL20.glUniform1i(clipDimensionLocation, frame.dimension);
        GL20.glUniform3f(arenaSizeLocation,
                (float) metersToNanometers(frame.arena.widthMeters),
                (float) metersToNanometers(frame.arena.heightMeters),
                (float) metersToNanometers(frame.arena.depthMeters));

        drawArena(frame, view, projection);
        if (frame.trace.enabled) {
            updateTraceHistory(frame, view, projection);
            compositeTraceHistory();
        } else {
            if (traceWasEnabled && traceFramebuffers[0] != 0) {
                clearTraceTargets();
            }
            traceWasEnabled = false;
            drawParticles(frame, view, projection);
        }

        GL30.glBindVertexArray(0);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL20.glUseProgram(0);
    }

    private void updateTraceHistory(RenderFrame frame, float[] view, float[] projection) {
        boolean resized = ensureTraceTargets();
        boolean cameraChanged = previousTraceDimension != frame.dimension
                || previousTraceRotationX != frame.camera.rotationXDegrees
                || previousTraceRotationY != frame.camera.rotationYDegrees
                || previousTraceProjection != frame.camera.projection;
        boolean simulationRestarted = !Double.isNaN(previousTraceSimulationTime)
                && frame.simulationTimeSeconds + 1.0e-12 < previousTraceSimulationTime;
        boolean reset = resized || !traceWasEnabled || cameraChanged
                || simulationRestarted
                || previousTraceResetGeneration != frame.trace.resetGeneration;

        if (reset) {
            clearTraceTargets();
        }
        if (!reset) {
            int writeIndex = 1 - traceReadIndex;
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,
                    traceFramebuffers[traceReadIndex]);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,
                    traceFramebuffers[writeIndex]);
            GL30.glBlitFramebuffer(0, 0, traceTargetWidth, traceTargetHeight,
                    0, 0, traceTargetWidth, traceTargetHeight,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        }

        int writeIndex = 1 - traceReadIndex;
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, traceFramebuffers[writeIndex]);
        GL11.glViewport(0, 0, traceTargetWidth, traceTargetHeight);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        drawTracePoints(frame, view, projection);
        traceReadIndex = writeIndex;
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GL11.glViewport(0, 0, viewportWidth, viewportHeight);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);

        traceWasEnabled = true;
        previousTraceResetGeneration = frame.trace.resetGeneration;
        previousTraceDimension = frame.dimension;
        previousTraceRotationX = frame.camera.rotationXDegrees;
        previousTraceRotationY = frame.camera.rotationYDegrees;
        previousTraceSimulationTime = frame.simulationTimeSeconds;
        previousTraceProjection = frame.camera.projection;
    }

    private void drawTracePoints(RenderFrame frame, float[] view, float[] projection) {
        List<Float> vertices = new ArrayList<Float>(frame.particles.size() * 10);
        double width = frame.arena.widthMeters;
        double height = frame.arena.heightMeters;
        double depth = frame.arena.depthMeters;
        double yOffset = frame.dimension == 1
                ? width * viewportHeight / (2.0 * viewportWidth) : 0.0;
        boolean periodic = frame.arena.shape == RenderFrame.Arena.Shape.PERIODIC;

        for (RenderFrame.Particle particle : frame.particles) {
            double y = particle.yMeters + yOffset;
            addTracePoint(vertices, particle, particle.xMeters, y, particle.zMeters);
            if (!periodic) {
                continue;
            }
            boolean left = particle.hasImageOnLeft;
            boolean bottom = particle.hasImageOnBottom && frame.dimension > 1;
            boolean back = particle.hasImageAtBack && frame.dimension > 2;
            if (left) addTracePoint(vertices, particle, particle.xMeters + width, y, particle.zMeters);
            if (bottom) addTracePoint(vertices, particle, particle.xMeters, y + height, particle.zMeters);
            if (back) addTracePoint(vertices, particle, particle.xMeters, y, particle.zMeters + depth);
            if (left && bottom) addTracePoint(vertices, particle,
                    particle.xMeters + width, y + height, particle.zMeters);
            if (left && back) addTracePoint(vertices, particle,
                    particle.xMeters + width, y, particle.zMeters + depth);
            if (bottom && back) addTracePoint(vertices, particle,
                    particle.xMeters, y + height, particle.zMeters + depth);
            if (left && bottom && back) addTracePoint(vertices, particle,
                    particle.xMeters + width, y + height, particle.zMeters + depth);
        }

        if (vertices.isEmpty()) {
            return;
        }
        float[] data = toFloatArray(vertices);
        GL20.glUseProgram(program);
        GL20.glUniform1i(useVertexColorLocation, 1);
        GL20.glUniform1i(lightingLocation, 0);
        GL20.glUniform1i(clipParticlesLocation, 1);
        GL20.glUniformMatrix4fv(modelLocation, false, buffer(identity()));
        GL20.glUniform1f(pointSizeLocation, Math.max(1, frame.trace.pointSize));
        GL20.glUniformMatrix4fv(modelViewLocation, false, buffer(view));
        GL20.glUniformMatrix4fv(mvpLocation, false, buffer(multiply(projection, view)));
        GL30.glBindVertexArray(traceVertexArray);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, traceVertexBuffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer(data), GL15.GL_DYNAMIC_DRAW);
        GL11.glDrawArrays(GL11.GL_POINTS, 0, data.length / 10);
        GL30.glBindVertexArray(0);
    }

    private static void addTracePoint(List<Float> vertices, RenderFrame.Particle particle,
            double xMeters, double yMeters, double zMeters) {
        vertices.add((float) metersToNanometers(xMeters));
        vertices.add((float) metersToNanometers(yMeters));
        vertices.add((float) metersToNanometers(zMeters));
        vertices.add(0.0f);
        vertices.add(0.0f);
        vertices.add(1.0f);
        vertices.add(particle.color.red);
        vertices.add(particle.color.green);
        vertices.add(particle.color.blue);
        vertices.add(particle.color.alpha);
    }

    private void compositeTraceHistory() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GL11.glViewport(0, 0, viewportWidth, viewportHeight);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        setClipDistancesEnabled(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, traceTextures[traceReadIndex]);
        GL20.glUseProgram(traceCompositeProgram);
        GL20.glUniform1i(traceTextureLocation, 0);
        GL30.glBindVertexArray(traceCompositeVertexArray);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        GL30.glBindVertexArray(0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL20.glUseProgram(0);
        setClipDistancesEnabled(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    @Override
    public void dispose() {
        if (sphereIndexBuffer != 0) {
            GL15.glDeleteBuffers(sphereIndexBuffer);
            sphereIndexBuffer = 0;
        }
        if (sphereVertexBuffer != 0) {
            GL15.glDeleteBuffers(sphereVertexBuffer);
            sphereVertexBuffer = 0;
        }
        if (wallVertexBuffer != 0) {
            GL15.glDeleteBuffers(wallVertexBuffer);
            wallVertexBuffer = 0;
        }
        if (traceVertexBuffer != 0) {
            GL15.glDeleteBuffers(traceVertexBuffer);
            traceVertexBuffer = 0;
        }
        if (traceCompositeVertexBuffer != 0) {
            GL15.glDeleteBuffers(traceCompositeVertexBuffer);
            traceCompositeVertexBuffer = 0;
        }
        if (sphereVertexArray != 0) {
            GL30.glDeleteVertexArrays(sphereVertexArray);
            sphereVertexArray = 0;
        }
        if (wallVertexArray != 0) {
            GL30.glDeleteVertexArrays(wallVertexArray);
            wallVertexArray = 0;
        }
        if (traceVertexArray != 0) {
            GL30.glDeleteVertexArrays(traceVertexArray);
            traceVertexArray = 0;
        }
        if (traceCompositeVertexArray != 0) {
            GL30.glDeleteVertexArrays(traceCompositeVertexArray);
            traceCompositeVertexArray = 0;
        }
        deleteTraceTargets();
        if (traceCompositeProgram != 0) {
            GL20.glDeleteProgram(traceCompositeProgram);
            traceCompositeProgram = 0;
        }
        if (program != 0) {
            GL20.glDeleteProgram(program);
            program = 0;
        }
    }

    private void createSphereMesh() {
        int verticesPerRow = SPHERE_SLICES + 1;
        int vertexCount = (SPHERE_STACKS + 1) * verticesPerRow;
        float[] vertices = new float[vertexCount * 6];
        int vertexOffset = 0;
        for (int stack = 0; stack <= SPHERE_STACKS; stack++) {
            double latitude = -Math.PI / 2.0 + Math.PI * stack / SPHERE_STACKS;
            double ringRadius = Math.cos(latitude);
            double y = Math.sin(latitude);
            for (int slice = 0; slice <= SPHERE_SLICES; slice++) {
                double longitude = 2.0 * Math.PI * slice / SPHERE_SLICES;
                float x = (float) (ringRadius * Math.cos(longitude));
                float z = (float) (ringRadius * Math.sin(longitude));
                vertices[vertexOffset++] = x;
                vertices[vertexOffset++] = (float) y;
                vertices[vertexOffset++] = z;
                vertices[vertexOffset++] = x;
                vertices[vertexOffset++] = (float) y;
                vertices[vertexOffset++] = z;
            }
        }

        sphereIndexCount = SPHERE_STACKS * SPHERE_SLICES * 6;
        IntBuffer indices = BufferUtils.createIntBuffer(sphereIndexCount);
        for (int stack = 0; stack < SPHERE_STACKS; stack++) {
            for (int slice = 0; slice < SPHERE_SLICES; slice++) {
                int lowerLeft = stack * verticesPerRow + slice;
                int upperLeft = lowerLeft + verticesPerRow;
                int lowerRight = lowerLeft + 1;
                int upperRight = upperLeft + 1;
                indices.put(lowerLeft).put(upperLeft).put(upperRight);
                indices.put(lowerLeft).put(upperRight).put(lowerRight);
            }
        }
        indices.flip();

        sphereVertexArray = GL30.glGenVertexArrays();
        sphereVertexBuffer = GL15.glGenBuffers();
        sphereIndexBuffer = GL15.glGenBuffers();
        GL30.glBindVertexArray(sphereVertexArray);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, sphereVertexBuffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer(vertices), GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false,
                6 * Float.BYTES, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false,
                6 * Float.BYTES, 3L * Float.BYTES);

        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, sphereIndexBuffer);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indices, GL15.GL_STATIC_DRAW);
        GL30.glBindVertexArray(0);
    }

    private void createWallBuffer() {
        wallVertexArray = GL30.glGenVertexArrays();
        wallVertexBuffer = GL15.glGenBuffers();
        GL30.glBindVertexArray(wallVertexArray);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, wallVertexBuffer);

        int stride = 10 * Float.BYTES;
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false,
                stride, 3L * Float.BYTES);
        GL20.glEnableVertexAttribArray(2);
        GL20.glVertexAttribPointer(2, 4, GL11.GL_FLOAT, false,
                stride, 6L * Float.BYTES);

        GL30.glBindVertexArray(0);
    }

    private void createTracePointBuffer() {
        traceVertexArray = GL30.glGenVertexArrays();
        traceVertexBuffer = GL15.glGenBuffers();
        GL30.glBindVertexArray(traceVertexArray);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, traceVertexBuffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 10L * Float.BYTES, GL15.GL_DYNAMIC_DRAW);

        int stride = 10 * Float.BYTES;
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false,
                stride, 3L * Float.BYTES);
        GL20.glEnableVertexAttribArray(2);
        GL20.glVertexAttribPointer(2, 4, GL11.GL_FLOAT, false,
                stride, 6L * Float.BYTES);
        GL30.glBindVertexArray(0);
    }

    private void createTraceCompositeResources() {
        traceCompositeProgram = createProgram(COMPOSITE_VERTEX_SHADER,
                COMPOSITE_FRAGMENT_SHADER);
        traceTextureLocation = GL20.glGetUniformLocation(
                traceCompositeProgram, "uTraceTexture");
        int texCoordLocation = GL20.glGetAttribLocation(
                traceCompositeProgram, "inTexCoord");

        float[] vertices = {
            -1, -1, 0, 0,
             1, -1, 1, 0,
             1,  1, 1, 1,
            -1, -1, 0, 0,
             1,  1, 1, 1,
            -1,  1, 0, 1
        };
        traceCompositeVertexArray = GL30.glGenVertexArrays();
        traceCompositeVertexBuffer = GL15.glGenBuffers();
        GL30.glBindVertexArray(traceCompositeVertexArray);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, traceCompositeVertexBuffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer(vertices), GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false,
                4 * Float.BYTES, 0L);
        GL20.glEnableVertexAttribArray(texCoordLocation);
        GL20.glVertexAttribPointer(texCoordLocation, 2, GL11.GL_FLOAT, false,
                4 * Float.BYTES, 2L * Float.BYTES);
        GL30.glBindVertexArray(0);
    }

    private boolean ensureTraceTargets() {
        if (traceFramebuffers[0] != 0
                && traceTargetWidth == viewportWidth
                && traceTargetHeight == viewportHeight) {
            return false;
        }

        deleteTraceTargets();
        traceTargetWidth = viewportWidth;
        traceTargetHeight = viewportHeight;
        for (int index = 0; index < 2; index++) {
            traceTextures[index] = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, traceTextures[index]);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8,
                    traceTargetWidth, traceTargetHeight, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);

            traceFramebuffers[index] = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, traceFramebuffers[index]);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,
                    GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D,
                    traceTextures[index], 0);
            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                deleteTraceTargets();
                throw new IllegalStateException("Trace framebuffer is incomplete: 0x"
                        + Integer.toHexString(status));
            }
        }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        clearTraceTargets();
        return true;
    }

    private void clearTraceTargets() {
        float[] clearColor = new float[4];
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
        GL11.glClearColor(0f, 0f, 0f, 0f);
        for (int framebuffer : traceFramebuffers) {
            if (framebuffer == 0) {
                continue;
            }
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GL11.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
        traceReadIndex = 0;
    }

    private void deleteTraceTargets() {
        for (int index = 0; index < 2; index++) {
            if (traceFramebuffers[index] != 0) {
                GL30.glDeleteFramebuffers(traceFramebuffers[index]);
                traceFramebuffers[index] = 0;
            }
            if (traceTextures[index] != 0) {
                GL11.glDeleteTextures(traceTextures[index]);
                traceTextures[index] = 0;
            }
        }
        traceTargetWidth = 0;
        traceTargetHeight = 0;
    }

    private void drawArena(RenderFrame frame, float[] view, float[] projection) {
        List<Float> vertices = new ArrayList<Float>();
        double width = metersToNanometers(frame.arena.widthMeters);
        double height = metersToNanometers(frame.arena.heightMeters);
        double depth = metersToNanometers(frame.arena.depthMeters);

        if (frame.dimension == 3) {
            addQuad(vertices, 0, 0, 0, 0, height, 0, 0, height, depth, 0, 0, depth,
                    1, 0, 0, frame.palette.leftWall);
            addQuad(vertices, width, 0, depth, width, height, depth, width, height, 0, width, 0, 0,
                    -1, 0, 0, frame.palette.rightWall);
            addQuad(vertices, 0, 0, depth, 0, height, depth, width, height, depth, width, 0, depth,
                    0, 0, -1, frame.palette.frontWall);
            addQuad(vertices, width, 0, 0, width, height, 0, 0, height, 0, 0, 0, 0,
                    0, 0, 1, frame.palette.backWall);
            addQuad(vertices, 0, height, depth, 0, height, 0, width, height, 0, width, height, depth,
                    0, -1, 0, frame.palette.topWall);
            addQuad(vertices, width, 0, 0, 0, 0, 0, 0, 0, depth, width, 0, depth,
                    0, 1, 0, frame.palette.bottomWall);
        }

        boolean dividerClosed = frame.arena.shape == RenderFrame.Arena.Shape.DIVIDED
                || (frame.arena.maxwellDemonMode && !frame.arena.dividerHoleOpen);
        if (frame.dimension == 1) {
            double laneHeight = width * viewportHeight / viewportWidth;
            if (frame.arena.piston.visible) {
                addPiston1D(vertices, frame, width, laneHeight);
            } else if (dividerClosed
                    || frame.arena.shape == RenderFrame.Arena.Shape.DIVIDED_WITH_HOLE) {
                double halfThickness = metersToNanometers(
                        frame.arena.dividerThicknessMeters) / 2.0;
                addRectangle(vertices, width / 2.0 - halfThickness, 0,
                        width / 2.0 + halfThickness, laneHeight, frame.palette.divider);
            }
        } else if (frame.dimension == 2) {
            if (frame.arena.piston.visible) {
                addPiston2D(vertices, frame, width, height);
            } else if (dividerClosed) {
                addRectangle(vertices, width / 2.0 - frame.arena.dividerThicknessMeters
                        * METERS_TO_NANOMETERS / 2.0, 0,
                        width / 2.0 + frame.arena.dividerThicknessMeters
                        * METERS_TO_NANOMETERS / 2.0, height, frame.palette.divider);
            } else if (frame.arena.shape == RenderFrame.Arena.Shape.DIVIDED_WITH_HOLE) {
                addDividerWithHole2D(vertices, frame, width, height);
            }
        } else if (frame.dimension == 3) {
            if (frame.arena.piston.visible) {
                addPiston3D(vertices, frame, width, height, depth);
            }
            if (dividerClosed) {
                double halfThickness = metersToNanometers(frame.arena.dividerThicknessMeters) / 2.0;
                addBox(vertices, width / 2.0 - halfThickness, 0, 0,
                        width / 2.0 + halfThickness, height, depth, frame.palette.divider);
                if (frame.arena.shape == RenderFrame.Arena.Shape.DIVIDED_WITH_HOLE) {
                    addDividerHoleEdgeMarker3D(vertices, frame, width, height, depth);
                }
            } else if (frame.arena.shape == RenderFrame.Arena.Shape.DIVIDED_WITH_HOLE) {
                addDividerWithHole3D(vertices, frame, width, height, depth);
            }
        }

        if (vertices.isEmpty()) {
            return;
        }
        float[] data = toFloatArray(vertices);
        GL30.glBindVertexArray(wallVertexArray);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, wallVertexBuffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer(data), GL15.GL_DYNAMIC_DRAW);
        GL20.glUniform1i(useVertexColorLocation, 1);
        GL20.glUniform1i(clipParticlesLocation, 0);
        GL20.glUniformMatrix4fv(modelLocation, false, buffer(identity()));
        GL20.glUniformMatrix4fv(modelViewLocation, false, buffer(view));
        GL20.glUniformMatrix4fv(mvpLocation, false,
                buffer(multiply(projection, view)));
        GL20.glUniform4f(tintLocation, 1f, 1f, 1f, 1f);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, data.length / 10);
    }

    private static void addRectangle(List<Float> vertices, double x0, double y0,
            double x1, double y1, RenderFrame.Color color) {
        addQuad(vertices, x0, y0, 0, x1, y0, 0, x1, y1, 0, x0, y1, 0,
                0, 0, 1, color);
    }

    private static void addDividerWithHole2D(List<Float> vertices, RenderFrame frame,
            double width, double height) {
        double halfThickness = metersToNanometers(frame.arena.dividerThicknessMeters) / 2.0;
        double holeRadius = metersToNanometers(frame.arena.holeDiameterMeters) / 2.0;
        double centerY = height / 2.0;
        double lowerEdge = Math.max(0.0, centerY - holeRadius - halfThickness);
        double upperEdge = Math.min(height, centerY + holeRadius + halfThickness);
        double centerX = width / 2.0;

        if (lowerEdge >= upperEdge) {
            addRectangle(vertices, centerX - halfThickness, 0,
                    centerX + halfThickness, height, frame.palette.divider);
            return;
        }
        addRectangle(vertices, centerX - halfThickness, 0,
                centerX + halfThickness, lowerEdge, frame.palette.divider);
        addRectangle(vertices, centerX - halfThickness, upperEdge,
                centerX + halfThickness, height, frame.palette.divider);
    }

    private static void addDividerWithHole3D(List<Float> vertices, RenderFrame frame,
            double width, double height, double depth) {
        double halfThickness = metersToNanometers(frame.arena.dividerThicknessMeters) / 2.0;
        double holeRadius = metersToNanometers(frame.arena.holeDiameterMeters) / 2.0;
        double halfHeight = height / 2.0;
        double halfDepth = depth / 2.0;
        double remainingBorder = Math.min(halfHeight, halfDepth) - holeRadius;

        if (holeRadius <= 0.0 || remainingBorder <= 0.0) {
            addBox(vertices, width / 2.0 - halfThickness, 0, 0,
                    width / 2.0 + halfThickness, height, depth, frame.palette.divider);
            return;
        }

        // A narrow gray band and the gray tunnel surface make the hole edge
        // visible against the divider face from either side.
        double rimWidth = Math.min(0.25,
                Math.min(remainingBorder / 2.0, Math.max(0.08, halfThickness / 4.0)));
        double x0 = width / 2.0 - halfThickness;
        double x1 = width / 2.0 + halfThickness;
        double centerY = halfHeight;
        double centerZ = halfDepth;
        List<Double> angles = dividerHoleAngles(halfHeight, halfDepth);

        addHoleFaceBand(vertices, x0, centerY, centerZ, halfHeight, halfDepth,
                holeRadius, holeRadius + rimWidth, false, -1, angles, HOLE_EDGE_COLOR);
        addHoleFaceBand(vertices, x1, centerY, centerZ, halfHeight, halfDepth,
                holeRadius, holeRadius + rimWidth, false, 1, angles, HOLE_EDGE_COLOR);
        addHoleFaceBand(vertices, x0, centerY, centerZ, halfHeight, halfDepth,
                holeRadius + rimWidth, 0.0, true, -1, angles, frame.palette.divider);
        addHoleFaceBand(vertices, x1, centerY, centerZ, halfHeight, halfDepth,
                holeRadius + rimWidth, 0.0, true, 1, angles, frame.palette.divider);

        addHoleTunnel(vertices, x0, x1, centerY, centerZ,
                holeRadius, angles, HOLE_EDGE_COLOR);
        addDividerOuterFaces(vertices, x0, x1, height, depth,
                centerY, holeRadius, frame.palette.divider, HOLE_EDGE_COLOR);
    }

    private static void addDividerHoleEdgeMarker3D(List<Float> vertices,
            RenderFrame frame, double width, double height, double depth) {
        double halfThickness = metersToNanometers(frame.arena.dividerThicknessMeters) / 2.0;
        double holeRadius = metersToNanometers(frame.arena.holeDiameterMeters) / 2.0;
        double centerY = height / 2.0;
        double lowerY = Math.max(0.0, centerY - holeRadius);
        double upperY = Math.min(height, centerY + holeRadius);
        double x0 = width / 2.0 - halfThickness;
        double x1 = width / 2.0 + halfThickness;

        addDividerEdgeAtDepth(vertices, x0, x1, 0, lowerY, upperY, -1, HOLE_EDGE_COLOR);
        addDividerEdgeAtDepth(vertices, x0, x1, depth, lowerY, upperY, 1, HOLE_EDGE_COLOR);
    }

    private static List<Double> dividerHoleAngles(double halfHeight, double halfDepth) {
        final double fullCircle = 2.0 * Math.PI;
        List<Double> angles = new ArrayList<Double>();
        for (int slice = 0; slice < 96; slice++) {
            angles.add(fullCircle * slice / 96.0);
        }
        for (int verticalSign = -1; verticalSign <= 1; verticalSign += 2) {
            for (int depthSign = -1; depthSign <= 1; depthSign += 2) {
                double angle = Math.atan2(verticalSign * halfHeight,
                        depthSign * halfDepth);
                if (angle < 0.0) {
                    angle += fullCircle;
                }
                angles.add(angle);
            }
        }
        Collections.sort(angles);

        List<Double> uniqueAngles = new ArrayList<Double>();
        for (Double angle : angles) {
            if (uniqueAngles.isEmpty()
                    || angle - uniqueAngles.get(uniqueAngles.size() - 1) > 1.0e-8) {
                uniqueAngles.add(angle);
            }
        }
        return uniqueAngles;
    }

    private static void addHoleFaceBand(List<Float> vertices, double x,
            double centerY, double centerZ, double halfHeight, double halfDepth,
            double innerRadius, double fixedOuterRadius, boolean reachesWallEdge,
            int normalX, List<Double> angles, RenderFrame.Color color) {
        final double fullCircle = 2.0 * Math.PI;
        for (int index = 0; index < angles.size(); index++) {
            double angle0 = angles.get(index);
            double angle1 = index + 1 < angles.size()
                    ? angles.get(index + 1) : angles.get(0) + fullCircle;
            double outerRadius0 = reachesWallEdge
                    ? radiusToDividerEdge(angle0, halfHeight, halfDepth) : fixedOuterRadius;
            double outerRadius1 = reachesWallEdge
                    ? radiusToDividerEdge(angle1, halfHeight, halfDepth) : fixedOuterRadius;

            double innerY0 = centerY + innerRadius * Math.sin(angle0);
            double innerZ0 = centerZ + innerRadius * Math.cos(angle0);
            double innerY1 = centerY + innerRadius * Math.sin(angle1);
            double innerZ1 = centerZ + innerRadius * Math.cos(angle1);
            double outerY0 = centerY + outerRadius0 * Math.sin(angle0);
            double outerZ0 = centerZ + outerRadius0 * Math.cos(angle0);
            double outerY1 = centerY + outerRadius1 * Math.sin(angle1);
            double outerZ1 = centerZ + outerRadius1 * Math.cos(angle1);

            if (normalX < 0) {
                addQuad(vertices, x, innerY0, innerZ0, x, outerY0, outerZ0,
                        x, outerY1, outerZ1, x, innerY1, innerZ1,
                        -1, 0, 0, color);
            } else {
                addQuad(vertices, x, innerY0, innerZ0, x, innerY1, innerZ1,
                        x, outerY1, outerZ1, x, outerY0, outerZ0,
                        1, 0, 0, color);
            }
        }
    }

    private static double radiusToDividerEdge(double angle,
            double halfHeight, double halfDepth) {
        double sin = Math.abs(Math.sin(angle));
        double cos = Math.abs(Math.cos(angle));
        double verticalHit = sin < 1.0e-12 ? Double.POSITIVE_INFINITY : halfHeight / sin;
        double depthHit = cos < 1.0e-12 ? Double.POSITIVE_INFINITY : halfDepth / cos;
        return Math.min(verticalHit, depthHit);
    }

    private static void addHoleTunnel(List<Float> vertices, double x0, double x1,
            double centerY, double centerZ, double radius, List<Double> angles,
            RenderFrame.Color color) {
        final double fullCircle = 2.0 * Math.PI;
        for (int index = 0; index < angles.size(); index++) {
            double angle0 = angles.get(index);
            double angle1 = index + 1 < angles.size()
                    ? angles.get(index + 1) : angles.get(0) + fullCircle;
            float sin0 = (float) Math.sin(angle0);
            float cos0 = (float) Math.cos(angle0);
            float sin1 = (float) Math.sin(angle1);
            float cos1 = (float) Math.cos(angle1);
            double y0 = centerY + radius * sin0;
            double z0 = centerZ + radius * cos0;
            double y1 = centerY + radius * sin1;
            double z1 = centerZ + radius * cos1;

            addVertex(vertices, x0, y0, z0, 0, -sin0, -cos0, color);
            addVertex(vertices, x0, y1, z1, 0, -sin1, -cos1, color);
            addVertex(vertices, x1, y1, z1, 0, -sin1, -cos1, color);
            addVertex(vertices, x0, y0, z0, 0, -sin0, -cos0, color);
            addVertex(vertices, x1, y1, z1, 0, -sin1, -cos1, color);
            addVertex(vertices, x1, y0, z0, 0, -sin0, -cos0, color);
        }
    }

    private static void addDividerOuterFaces(List<Float> vertices,
            double x0, double x1, double height, double depth,
            double holeCenterY, double holeRadius, RenderFrame.Color color,
            RenderFrame.Color holeEdgeColor) {
        addQuad(vertices, x0, 0, 0, x1, 0, 0, x1, 0, depth, x0, 0, depth,
                0, -1, 0, color);
        addQuad(vertices, x0, height, depth, x1, height, depth,
                x1, height, 0, x0, height, 0, 0, 1, 0, color);
        double holeBottom = Math.max(0.0, holeCenterY - holeRadius);
        double holeTop = Math.min(height, holeCenterY + holeRadius);
        addDividerEdgeAtDepth(vertices, x0, x1, 0, 0, holeBottom, -1, color);
        addDividerEdgeAtDepth(vertices, x0, x1, 0, holeBottom, holeTop, -1, holeEdgeColor);
        addDividerEdgeAtDepth(vertices, x0, x1, 0, holeTop, height, -1, color);
        addDividerEdgeAtDepth(vertices, x0, x1, depth, 0, holeBottom, 1, color);
        addDividerEdgeAtDepth(vertices, x0, x1, depth, holeBottom, holeTop, 1, holeEdgeColor);
        addDividerEdgeAtDepth(vertices, x0, x1, depth, holeTop, height, 1, color);
    }

    private static void addDividerEdgeAtDepth(List<Float> vertices,
            double x0, double x1, double z, double y0, double y1,
            int normalZ, RenderFrame.Color color) {
        if (y1 <= y0) {
            return;
        }
        if (normalZ < 0) {
            addQuad(vertices, x0, y0, z, x0, y1, z, x1, y1, z, x1, y0, z,
                    0, 0, -1, color);
        } else {
            addQuad(vertices, x1, y0, z, x1, y1, z, x0, y1, z, x0, y0, z,
                    0, 0, 1, color);
        }
    }

    private static void addPiston2D(List<Float> vertices, RenderFrame frame,
            double width, double height) {
        double valveRadius = metersToNanometers(frame.arena.piston.valveRadiusMeters);
        double shaftRadius = metersToNanometers(frame.arena.piston.shaftRadiusMeters);
        double pistonPosition = metersToNanometers(frame.arena.piston.positionMeters);
        double valveTop = pistonPosition + 2.0 * valveRadius;

        addRectangle(vertices, 0, pistonPosition, width, valveTop, frame.palette.piston);
        addRectangle(vertices, width / 2.0 - shaftRadius, valveTop,
                width / 2.0 + shaftRadius, height, frame.palette.piston);
    }

    private static void addPiston1D(List<Float> vertices, RenderFrame frame,
            double width, double laneHeight) {
        double valveRadius = metersToNanometers(frame.arena.piston.valveRadiusMeters);
        double shaftRadius = metersToNanometers(frame.arena.piston.shaftRadiusMeters);
        double pistonPosition = metersToNanometers(frame.arena.piston.positionMeters);
        double valveTop = pistonPosition + 2.0 * valveRadius;

        addRectangle(vertices, pistonPosition, 0, valveTop, laneHeight, frame.palette.piston);
        addRectangle(vertices, valveTop, laneHeight / 2.0 - shaftRadius,
                width, laneHeight / 2.0 + shaftRadius, frame.palette.piston);
    }

    private static void addPiston3D(List<Float> vertices, RenderFrame frame,
            double width, double height, double depth) {
        double valveRadius = metersToNanometers(frame.arena.piston.valveRadiusMeters);
        double shaftRadius = metersToNanometers(frame.arena.piston.shaftRadiusMeters);
        double pistonPosition = metersToNanometers(frame.arena.piston.positionMeters);
        double valveTop = pistonPosition + 2.0 * valveRadius;
        RenderFrame.Color color = frame.palette.pistonInThreeDimensions;

        addBox(vertices, 0, pistonPosition, 0, width, valveTop, depth, color);
        addCylinderY(vertices, width / 2.0, depth / 2.0, shaftRadius,
                valveTop, height, color);
    }

    private static void addCylinderY(List<Float> vertices, double centerX,
            double centerZ, double radius, double y0, double y1,
            RenderFrame.Color color) {
        if (radius <= 0.0 || y1 <= y0) {
            return;
        }

        final int slices = 24;
        for (int slice = 0; slice < slices; slice++) {
            double angle0 = 2.0 * Math.PI * slice / slices;
            double angle1 = 2.0 * Math.PI * (slice + 1) / slices;
            float xNormal0 = (float) Math.cos(angle0);
            float zNormal0 = (float) Math.sin(angle0);
            float xNormal1 = (float) Math.cos(angle1);
            float zNormal1 = (float) Math.sin(angle1);
            double x0 = centerX + radius * xNormal0;
            double z0 = centerZ + radius * zNormal0;
            double x1 = centerX + radius * xNormal1;
            double z1 = centerZ + radius * zNormal1;

            addVertex(vertices, x0, y0, z0, xNormal0, 0, zNormal0, color);
            addVertex(vertices, x0, y1, z0, xNormal0, 0, zNormal0, color);
            addVertex(vertices, x1, y1, z1, xNormal1, 0, zNormal1, color);
            addVertex(vertices, x0, y0, z0, xNormal0, 0, zNormal0, color);
            addVertex(vertices, x1, y1, z1, xNormal1, 0, zNormal1, color);
            addVertex(vertices, x1, y0, z1, xNormal1, 0, zNormal1, color);

            // Bottom cap faces -Y; top cap faces +Y.
            addVertex(vertices, centerX, y0, centerZ, 0, -1, 0, color);
            addVertex(vertices, x0, y0, z0, 0, -1, 0, color);
            addVertex(vertices, x1, y0, z1, 0, -1, 0, color);
            addVertex(vertices, centerX, y1, centerZ, 0, 1, 0, color);
            addVertex(vertices, x1, y1, z1, 0, 1, 0, color);
            addVertex(vertices, x0, y1, z0, 0, 1, 0, color);
        }
    }

    private void drawParticles(RenderFrame frame, float[] view, float[] projection) {
        GL30.glBindVertexArray(sphereVertexArray);
        GL20.glUniform1i(useVertexColorLocation, 0);
        GL20.glUniform1i(clipParticlesLocation, 1);

        double width = frame.arena.widthMeters;
        double height = frame.arena.heightMeters;
        double depth = frame.arena.depthMeters;
        double yOffset = frame.dimension == 1
                ? width * viewportHeight / (2.0 * viewportWidth) : 0.0;
        boolean periodic = frame.arena.shape == RenderFrame.Arena.Shape.PERIODIC;

        for (RenderFrame.Particle particle : frame.particles) {
            double y = particle.yMeters + yOffset;
            drawParticle(particle, particle.xMeters, y, particle.zMeters,
                    view, projection);
            if (!periodic) {
                continue;
            }
            boolean left = particle.hasImageOnLeft;
            boolean bottom = particle.hasImageOnBottom && frame.dimension > 1;
            boolean back = particle.hasImageAtBack && frame.dimension > 2;
            if (left) drawParticle(particle, particle.xMeters + width, y, particle.zMeters, view, projection);
            if (bottom) drawParticle(particle, particle.xMeters, y + height, particle.zMeters, view, projection);
            if (back) drawParticle(particle, particle.xMeters, y, particle.zMeters + depth, view, projection);
            if (left && bottom) drawParticle(particle, particle.xMeters + width, y + height, particle.zMeters, view, projection);
            if (left && back) drawParticle(particle, particle.xMeters + width, y, particle.zMeters + depth, view, projection);
            if (bottom && back) drawParticle(particle, particle.xMeters, y + height, particle.zMeters + depth, view, projection);
            if (left && bottom && back) drawParticle(particle, particle.xMeters + width, y + height, particle.zMeters + depth, view, projection);
        }
    }

    private void drawParticle(RenderFrame.Particle particle, double xMeters,
            double yMeters, double zMeters, float[] view, float[] projection) {
        double radius = metersToNanometers(particle.radiusMeters);
        float[] model = multiply(translation(metersToNanometers(xMeters),
                metersToNanometers(yMeters), metersToNanometers(zMeters)),
                scale(radius, radius, radius));
        float[] modelView = multiply(view, model);
        GL20.glUniformMatrix4fv(modelViewLocation, false, buffer(modelView));
        GL20.glUniformMatrix4fv(modelLocation, false, buffer(model));
        GL20.glUniformMatrix4fv(mvpLocation, false,
                buffer(multiply(projection, modelView)));
        GL20.glUniform4f(tintLocation, particle.color.red, particle.color.green,
                particle.color.blue, particle.color.alpha);
        GL11.glDrawElements(GL11.GL_TRIANGLES, sphereIndexCount,
                GL11.GL_UNSIGNED_INT, 0L);
    }

    private float[] projectionMatrix(RenderFrame frame) {
        double aspect = (double) viewportWidth / viewportHeight;
        double width = Math.max(1.0e-6, metersToNanometers(frame.arena.widthMeters));
        double height = metersToNanometers(frame.arena.heightMeters);
        double depth = metersToNanometers(frame.arena.depthMeters);

        if (frame.camera.projection == RenderFrame.Camera.Projection.ORTHOGRAPHIC) {
            if (frame.dimension == 1) {
                height = width / aspect;
            }
            height = Math.max(height, 1.0e-6);
            double extent = Math.max(width, height);
            return orthographic(0, width, 0, height, -2 * extent, 2 * extent);
        }

        double fieldOfView = Math.max(1.0, frame.camera.verticalFieldOfViewDegrees);
        double distance = metersToNanometers(frame.camera.distanceMeters);
        double extent = Math.sqrt(width * width + height * height + depth * depth);
        distance = Math.max(distance, extent * 1.25);
        double near = Math.max(0.01, distance - extent);
        double far = Math.max(near + 1.0, distance + extent);
        return perspective(fieldOfView, aspect, near, far);
    }

    private static void setClipDistancesEnabled(boolean enabled) {
        for (int index = 0; index < 6; index++) {
            int capability = GL30.GL_CLIP_DISTANCE0 + index;
            if (enabled) {
                GL11.glEnable(capability);
            } else {
                GL11.glDisable(capability);
            }
        }
    }

    private float[] viewMatrix(RenderFrame frame) {
        if (frame.camera.projection == RenderFrame.Camera.Projection.ORTHOGRAPHIC) {
            return identity();
        }

        double width = metersToNanometers(frame.arena.widthMeters);
        double height = metersToNanometers(frame.arena.heightMeters);
        double depth = metersToNanometers(frame.arena.depthMeters);
        double distance = metersToNanometers(frame.camera.distanceMeters);
        double extent = Math.sqrt(width * width + height * height + depth * depth);
        distance = Math.max(distance, extent * 1.25);

        float[] center = translation(width / 2.0, height / 2.0, depth / 2.0);
        float[] orientation = multiply(rotationY(frame.camera.rotationYDegrees),
                rotationX(frame.camera.rotationXDegrees));
        float[] inverseCenter = translation(-width / 2.0, -height / 2.0, -depth / 2.0);
        // Center x/y as well as moving the camera down -Z. Without the first
        // two translations, a perspective projection centered on the origin
        // places the arena in the upper-right of the viewport.
        float[] cameraTransform = translation(-width / 2.0, -height / 2.0, -distance);
        return multiply(cameraTransform,
                multiply(center, multiply(orientation, inverseCenter)));
    }

    private static void addBox(List<Float> vertices, double x0, double y0, double z0,
            double x1, double y1, double z1, RenderFrame.Color color) {
        addQuad(vertices, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, -1, 0, 0, color);
        addQuad(vertices, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, 1, 0, 0, color);
        addQuad(vertices, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, 0, 0, -1, color);
        addQuad(vertices, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, 0, 0, 1, color);
        addQuad(vertices, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0, color);
        addQuad(vertices, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, color);
    }

    private static void addQuad(List<Float> vertices,
            double x0, double y0, double z0,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            double x3, double y3, double z3,
            float nx, float ny, float nz, RenderFrame.Color color) {
        addVertex(vertices, x0, y0, z0, nx, ny, nz, color);
        addVertex(vertices, x1, y1, z1, nx, ny, nz, color);
        addVertex(vertices, x2, y2, z2, nx, ny, nz, color);
        addVertex(vertices, x0, y0, z0, nx, ny, nz, color);
        addVertex(vertices, x2, y2, z2, nx, ny, nz, color);
        addVertex(vertices, x3, y3, z3, nx, ny, nz, color);
    }

    private static void addVertex(List<Float> vertices, double x, double y, double z,
            float nx, float ny, float nz, RenderFrame.Color color) {
        vertices.add((float) x);
        vertices.add((float) y);
        vertices.add((float) z);
        vertices.add(nx);
        vertices.add(ny);
        vertices.add(nz);
        vertices.add(color.red);
        vertices.add(color.green);
        vertices.add(color.blue);
        vertices.add(color.alpha);
    }

    private static float[] toFloatArray(List<Float> values) {
        float[] data = new float[values.size()];
        for (int index = 0; index < values.size(); index++) {
            data[index] = values.get(index);
        }
        return data;
    }

    private int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = compileShader(GL20.GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, fragmentSource);
        int shaderProgram = GL20.glCreateProgram();
        GL20.glAttachShader(shaderProgram, vertexShader);
        GL20.glAttachShader(shaderProgram, fragmentShader);
        GL20.glBindAttribLocation(shaderProgram, 0, "inPosition");
        GL20.glBindAttribLocation(shaderProgram, 1, "inNormal");
        GL20.glBindAttribLocation(shaderProgram, 2, "inColor");
        GL20.glLinkProgram(shaderProgram);
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);
        if (GL20.glGetProgrami(shaderProgram, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String message = GL20.glGetProgramInfoLog(shaderProgram);
            GL20.glDeleteProgram(shaderProgram);
            throw new IllegalStateException("Could not link core renderer shaders: " + message);
        }
        return shaderProgram;
    }

    private static int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String message = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("Could not compile core renderer shader: " + message);
        }
        return shader;
    }

    private static double metersToNanometers(double meters) {
        return meters * METERS_TO_NANOMETERS;
    }

    private static FloatBuffer buffer(float[] values) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(values.length);
        buffer.put(values).flip();
        return buffer;
    }

    private static float[] identity() {
        return new float[] {
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
        };
    }

    private static float[] translation(double x, double y, double z) {
        float[] matrix = identity();
        matrix[12] = (float) x;
        matrix[13] = (float) y;
        matrix[14] = (float) z;
        return matrix;
    }

    private static float[] scale(double x, double y, double z) {
        float[] matrix = new float[16];
        matrix[0] = (float) x;
        matrix[5] = (float) y;
        matrix[10] = (float) z;
        matrix[15] = 1;
        return matrix;
    }

    private static float[] rotationX(double degrees) {
        double angle = Math.toRadians(degrees);
        float cosine = (float) Math.cos(angle);
        float sine = (float) Math.sin(angle);
        float[] matrix = identity();
        matrix[5] = cosine;
        matrix[6] = sine;
        matrix[9] = -sine;
        matrix[10] = cosine;
        return matrix;
    }

    private static float[] rotationY(double degrees) {
        double angle = Math.toRadians(degrees);
        float cosine = (float) Math.cos(angle);
        float sine = (float) Math.sin(angle);
        float[] matrix = identity();
        matrix[0] = cosine;
        matrix[2] = -sine;
        matrix[8] = sine;
        matrix[10] = cosine;
        return matrix;
    }

    private static float[] orthographic(double left, double right, double bottom,
            double top, double near, double far) {
        float[] matrix = new float[16];
        matrix[0] = (float) (2.0 / (right - left));
        matrix[5] = (float) (2.0 / (top - bottom));
        matrix[10] = (float) (-2.0 / (far - near));
        matrix[12] = (float) (-(right + left) / (right - left));
        matrix[13] = (float) (-(top + bottom) / (top - bottom));
        matrix[14] = (float) (-(far + near) / (far - near));
        matrix[15] = 1;
        return matrix;
    }

    private static float[] perspective(double fieldOfViewDegrees, double aspect,
            double near, double far) {
        double factor = 1.0 / Math.tan(Math.toRadians(fieldOfViewDegrees) / 2.0);
        float[] matrix = new float[16];
        matrix[0] = (float) (factor / aspect);
        matrix[5] = (float) factor;
        matrix[10] = (float) ((far + near) / (near - far));
        matrix[11] = -1;
        matrix[14] = (float) ((2.0 * far * near) / (near - far));
        return matrix;
    }

    private static float[] multiply(float[] left, float[] right) {
        float[] result = new float[16];
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                float value = 0;
                for (int index = 0; index < 4; index++) {
                    value += left[index * 4 + row] * right[column * 4 + index];
                }
                result[column * 4 + row] = value;
            }
        }
        return result;
    }
}
