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

package edu.byu.chem.boltzmann.controller;

import java.util.Timer;
import java.util.TimerTask;
import javax.swing.SwingUtilities;

/**
 *
 * @author Derek Manwaring
 * 18 Apr 2012
 */
class ThreadController {
    
    private static final int FRAME_DURATION = 37; //Duration of a displayed frame in the simulation in milliseconds
    
    private ThreadController(Controller controller) {
        this.root = controller;        
        frameRateTimer.schedule(frameRateTask, FRAME_DURATION, FRAME_DURATION);
    }
    
    private static ThreadController singleton = null;
    private static Controller rootController = null;
    
    protected static synchronized void setRootController(Controller controller) {
        rootController = controller;
    }
    
    protected static synchronized ThreadController getInstance() {
        if (rootController == null) {
            throw new IllegalStateException("Root Controller must be set before getting an instance of ThreadController");
        }
        
        if (singleton == null) {
            singleton = new ThreadController(rootController);
        }
        
        return singleton;
    }
    
    private final Controller root;
    private volatile long generation;
    
    private volatile boolean frameAvailable = false;
    private volatile boolean runSimulationThreads = false;
    
    private final BinarySemaphore lastFrameDisplayed = new BinarySemaphore(false);
    private final BinarySemaphore frameReady = new BinarySemaphore(false);
    
    private final BinarySemaphore displayFrameRate = new BinarySemaphore(true);
    private final BinarySemaphore physicsFrameRate = new BinarySemaphore(true);
    
    private final BinarySemaphore statisticsLock = new BinarySemaphore(true);
    
    private final Timer frameRateTimer = new Timer("Frame Rate", true);
    
    private final TimerTask frameRateTask = new TimerTask() {
        @Override
        public void run() {
            displayFrameRate.release();
            if (!paused) {
                physicsFrameRate.release();
            }
        }
    };

    protected synchronized void requestGLRepaint() {
    }

    protected void requestGLRescale() {
    }

    protected void addGLRotationRequester() {
    }

    protected void removeGLRotationRequester() {
    }
    
    private abstract class SimulationThread extends Thread {
    
        public SimulationThread(String name) {
            super(name);
        }

        @Override
        public void run() {
            try {
                runThrowingInterruptedException();
            } catch (InterruptedException e) {
                if (runSimulationThreads) {
                    throw new RuntimeException("Simulation thread was interrupted while it should be running", e);
                }
            }
        }

        protected abstract void runThrowingInterruptedException() throws InterruptedException;
        
    }
    
    private class PhysicsThread extends SimulationThread {
        
        private PhysicsThread() {
            super("Physics Thread");
        }
        
        @Override
        protected void runThrowingInterruptedException() throws InterruptedException {
            while (runSimulationThreads) {
                physicsFrameRate.acquire();

                if (!runSimulationThreads) break;
                statisticsLock.acquire();
                try {
                    if (!runSimulationThreads) break;
                    root.advancePhysicsToNextFrame();
                } finally {
                    statisticsLock.release();
                }

                lastFrameDisplayed.acquire();
                if (!runSimulationThreads) break;
                root.giveFrameToViewForDisplaying();
                frameAvailable = true;
                frameReady.release();
            }
        }
    }
    
    private class DisplayTimingThread extends SimulationThread {

        private DisplayTimingThread() {
            super("Display Timing");
        }
        
        @Override
        protected void runThrowingInterruptedException() throws InterruptedException {
            lastFrameDisplayed.release();
            
            while (runSimulationThreads) {
                displayFrameRate.acquire();
                if (!runSimulationThreads) break;

                boolean newFrame = frameReady.tryAcquire();
                if (frameAvailable) {
                    root.displayFrame();
                    if (newFrame) {
                        lastFrameDisplayed.release();
                    }
                }
            }
        }        
    }
        
    private final int STATISTICS_DISPLAY_PERIOD = 1000; // milliseconds
        
    private class StatisticsDisplayThread extends SimulationThread {
        
        private StatisticsDisplayThread() {
            super("Statistics Display");
        }
        
        @Override
        protected void runThrowingInterruptedException() throws InterruptedException {
            while (runSimulationThreads) {
                Thread.sleep(STATISTICS_DISPLAY_PERIOD);
                final long scheduledGeneration = generation;
                if (runSimulationThreads && frameAvailable) {
                    SwingUtilities.invokeLater(() -> {
                        // A queued update must not reach a replacement simulation.
                        // Never wait for physics while holding up the AWT event queue.
                        if (!runSimulationThreads || generation != scheduledGeneration
                                || !statisticsLock.tryAcquire()) return;
                        try {
                            root.displayStatistics();
                        } finally {
                            statisticsLock.release();
                        }
                    });
                }
            } 
        }
    }

    private void resetThreadVariables() {
        generation++;
        statisticsLock.release();
                
        lastFrameDisplayed.exhaust();
        frameReady.exhaust();
        displayFrameRate.release();
        physicsFrameRate.release();
        
        frameAvailable = false;
        
        runSimulationThreads = true;
    }
      
    private PhysicsThread physicsThread = null;
    private DisplayTimingThread displayTiming = null;  
    private StatisticsDisplayThread statisticsDisplay = null;
    
    protected void startSimulationThreads() {
        resetThreadVariables();
        
        physicsThread = new PhysicsThread();
        displayTiming = new DisplayTimingThread();
        statisticsDisplay = new StatisticsDisplayThread();
        
        physicsThread.start();   
        displayTiming.start();
        statisticsDisplay.start();
    }

    private volatile boolean paused = false;
    
    protected synchronized void setPaused(boolean paused) {        
        this.paused = paused;
        if (paused) {
            physicsFrameRate.exhaust();
        }
    }

    protected void advanceOneFrame() {
        physicsFrameRate.release();
    }
    
    protected class CouldNotStopThreadsException extends RuntimeException {
        
        public CouldNotStopThreadsException(String message) {
            super(message);
        }
        
        public CouldNotStopThreadsException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    protected class CouldNotStopPhysicsException extends CouldNotStopThreadsException {
        protected CouldNotStopPhysicsException() {
            super("Physics thread took too long to stop");
        }
    }
    
    protected void stopSimulationThreads(int waitTime) throws CouldNotStopThreadsException {
        try {
            runSimulationThreads = false;

            // Wake our own waits instead of interrupting JOGL's synchronous
            // macOS/AWT dispatch. Interrupting a render can throw from JOGL and
            // leave this thread blocked in the application's error dialog.
            displayFrameRate.release();
            physicsFrameRate.release();
            lastFrameDisplayed.release();
            statisticsDisplay.interrupt(); // Only sleeps; never renders.

            displayTiming.join(waitTime);
            if (displayTiming.isAlive()) {
                throw new CouldNotStopThreadsException("Display timing thread did not stop");
            }

            statisticsDisplay.join(waitTime);
            if (statisticsDisplay.isAlive()) {
                throw new CouldNotStopThreadsException("Statistics display thread did not stop");
            }

            physicsThread.join(waitTime); 
            if (physicsThread.isAlive()) {
                throw new CouldNotStopPhysicsException();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CouldNotStopThreadsException("Interruption while stopping Boltzmann's threads", e);
        } 
    }    
}
