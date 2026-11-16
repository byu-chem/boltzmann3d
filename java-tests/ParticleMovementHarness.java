import edu.byu.chem.boltzmann.model.physics.Particle1D;
import edu.byu.chem.boltzmann.model.physics.Particle2D;
import edu.byu.chem.boltzmann.model.physics.Particle3D;
import edu.byu.chem.boltzmann.model.physics.Collision;
import edu.byu.chem.boltzmann.model.physics.Calendar;
import edu.byu.chem.boltzmann.model.physics.EventInfo;
import edu.byu.chem.boltzmann.model.physics.Thermostat;
import edu.byu.chem.boltzmann.model.physics.Wall;
import edu.byu.chem.boltzmann.utils.data.ParticleType;
import edu.byu.chem.boltzmann.utils.data.SimulationInfo;
import edu.byu.chem.boltzmann.utils.data.SimulationInfoSupplier;
import edu.byu.chem.boltzmann.model.statistics.interfaces.StatisticID;
import java.awt.Color;
import java.lang.reflect.Proxy;
import java.util.Locale;

/**
 * Small standalone check of the Java particle movement methods.
 *
 * Run from the repository root after compiling the project:
 *   ant compile
 *   javac -cp "bin:lib/*" -d bin/test-harness java-tests/ParticleMovementHarness.java
 *   java -cp "bin/test-harness:bin:lib/*" ParticleMovementHarness
 */
public final class ParticleMovementHarness {

    private static final double TOLERANCE = 1.0e-12;

    private ParticleMovementHarness() {
    }

    public static void main(String[] args) {
        SimulationInfo simulationInfo = new SimulationInfo();
        ParticleType testType = new ParticleType(1.0, 1.0, Color.RED, "test");

        testOneDimension(simulationInfo, testType);
        testTwoDimensions(simulationInfo, testType);
        testThreeDimensions(simulationInfo, testType);
        testRepeatedMoves(simulationInfo, testType);
        testFractionalValues(simulationInfo, testType);
        testOneDimensionalElasticCollision(simulationInfo);
        testOneDimensionalCollisionPrediction(simulationInfo);
        testOneDimensionalEnergyWell(testType);
        testTwoDimensionalParticleCollision();
        testThreeDimensionalParticleCollision();
        testPeriodicParticleCollisionAcrossBoundary();
        testOneDimensionalWallHandling();
        testTwoDimensionalWallHandling();
        testThreeDimensionalWallHandling();
        testOneDimensionalDividerHandling();
        testTwoDimensionalDividerHandling();
        testThreeDimensionalDividerHandling();
        testTwoDimensionalDividerHole();

        System.out.println("All Java particle movement checks passed.");
    }

    private static void testOneDimension(
            SimulationInfo simulationInfo, ParticleType particleType) {
        Particle1D particle = new Particle1D(
                new double[]{2.0}, new double[]{3.0}, 0.0,
                particleType, simulationInfo);

        particle.moveToTime(4.0);

        assertClose("1D position", particle.getX(), 14.0);
        assertClose("1D x velocity", particle.getXVel(), 3.0);
        System.out.println("1D PASS: position=14.0 velocity=3.0 time=4.0");
    }

    private static void testTwoDimensions(
            SimulationInfo simulationInfo, ParticleType particleType) {
        Particle2D particle = new Particle2D(
                new double[]{1.0, 5.0}, new double[]{2.0, -1.0}, 0.0,
                particleType, simulationInfo);

        particle.moveToTime(3.0);

        assertClose("2D x position", particle.getX(), 7.0);
        assertClose("2D y position", particle.getY(), 2.0);
        assertClose("2D x velocity", particle.getXVel(), 2.0);
        assertClose("2D y velocity", particle.getYVel(), -1.0);
        System.out.println("2D PASS: position=(7.0, 2.0) velocity=(2.0, -1.0) time=3.0");
    }

    private static void testThreeDimensions(
            SimulationInfo simulationInfo, ParticleType particleType) {
        Particle3D particle = new Particle3D(
                new double[]{1.0, 2.0, 3.0},
                new double[]{2.0, -1.0, 0.5}, 0.0,
                particleType, simulationInfo);

        particle.moveToTime(2.0);

        assertClose("3D x position", particle.getX(), 5.0);
        assertClose("3D y position", particle.getY(), 0.0);
        assertClose("3D z position", particle.getZ(), 4.0);
        assertClose("3D x velocity", particle.getXVel(), 2.0);
        assertClose("3D y velocity", particle.getYVel(), -1.0);
        assertClose("3D z velocity", particle.getZVel(), 0.5);
        System.out.println("3D PASS: position=(5.0, 0.0, 4.0) velocity=(2.0, -1.0, 0.5) time=2.0");
    }

    private static void testRepeatedMoves(
            SimulationInfo simulationInfo, ParticleType particleType) {
        Particle1D particle = new Particle1D(
                new double[]{0.0}, new double[]{2.0}, 0.0,
                particleType, simulationInfo);

        particle.moveToTime(1.5);
        particle.moveToTime(4.0);

        assertClose("repeated-move position", particle.getX(), 8.0);
        System.out.println("Repeated moves PASS: position=8.0 after time=4.0");
    }

    private static void testFractionalValues(
            SimulationInfo simulationInfo, ParticleType particleType) {
        Particle1D particle = new Particle1D(
                new double[]{0.1}, new double[]{0.2}, 0.1,
                particleType, simulationInfo);

        particle.moveToTime(0.4);

        assertClose("fractional position", particle.getX(), 0.16000000000000003);
        System.out.printf(Locale.ROOT, "Fractional position: %.17g%n", particle.getX());
    }

    private static void testOneDimensionalElasticCollision(
            SimulationInfo simulationInfo) {
        ParticleType lightType = new ParticleType(1.0, 1.0, Color.RED, "light");
        ParticleType heavyType = new ParticleType(2.0, 1.0, Color.BLUE, "heavy");
        Particle1D first = new Particle1D(
                new double[]{0.0}, new double[]{3.0}, 0.0,
                lightType, simulationInfo);
        Particle1D second = new Particle1D(
                new double[]{4.0}, new double[]{-1.0}, 0.0,
                heavyType, simulationInfo);
        EventInfo collision = new EventInfo(Collision.PARTICLE, 0.5, 0, 1, 0);

        first.collideWith(second, collision);

        assertClose("collision first position", first.getX(), 1.5);
        assertClose("collision second position", second.getX(), 3.5);
        assertClose("collision first velocity", first.getXVel(), -7.0 / 3.0);
        assertClose("collision second velocity", second.getXVel(), 5.0 / 3.0);
        System.out.printf(Locale.ROOT,
                "1D elastic collision PASS: positions=(%.17g, %.17g) velocities=(%.17g, %.17g)%n",
                first.getX(), second.getX(), first.getXVel(), second.getXVel());
    }

    private static void testOneDimensionalCollisionPrediction(
            SimulationInfo simulationInfo) {
        ParticleType particleType = new ParticleType(1.0, 1.0, Color.RED, "test");
        Particle1D approachingFirst = new Particle1D(
                new double[]{0.0}, new double[]{1.0}, 0.0,
                particleType, simulationInfo);
        Particle1D approachingSecond = new Particle1D(
                new double[]{5.0}, new double[]{-1.0}, 0.0,
                particleType, simulationInfo);
        EventInfo predicted = new EventInfo(
                Collision.PARTICLE, Calendar.MAXTIME, 0, 1, 0);

        approachingFirst.predCol(approachingSecond, null, predicted);
        assertClose("approaching collision time", predicted.colTime, 1.5);

        Particle1D recedingFirst = new Particle1D(
                new double[]{0.0}, new double[]{-1.0}, 0.0,
                particleType, simulationInfo);
        Particle1D recedingSecond = new Particle1D(
                new double[]{5.0}, new double[]{1.0}, 0.0,
                particleType, simulationInfo);
        EventInfo noCollision = new EventInfo(
                Collision.PARTICLE, Calendar.MAXTIME, 0, 1, 0);

        recedingFirst.predCol(recedingSecond, null, noCollision);
        if (noCollision.colTime != Calendar.MAXTIME) {
            throw new AssertionError(
                    "receding particles expected no collision but got "
                    + noCollision.colTime);
        }

        Particle1D laterState = new Particle1D(
                new double[]{1.0}, new double[]{1.0}, 1.0,
                particleType, simulationInfo);
        Particle1D earlierState = new Particle1D(
                new double[]{5.0}, new double[]{-1.0}, 0.0,
                particleType, simulationInfo);
        EventInfo asynchronousPrediction = new EventInfo(
                Collision.PARTICLE, Calendar.MAXTIME, 0, 1, 0);

        laterState.predCol(earlierState, null, asynchronousPrediction);
        assertClose("asynchronous collision time", asynchronousPrediction.colTime, 1.5);

        System.out.println(
                "1D collision prediction PASS: approaching=1.5 receding=no collision asynchronous=1.5");
    }

    private static void testOneDimensionalEnergyWell(ParticleType particleType) {
        SimulationInfo entryInfo = createSimulationInfo(
                particleType, 1, 30.0, 0.0, 0.0,
                SimulationInfo.ArenaType.REFLECTING_BOUNDARIES,
                0.0, false, true, 2.0, 1.0);
        Particle1D enteringFirst = new Particle1D(
                new double[]{2.0}, new double[]{1.0}, 0.0, particleType, entryInfo);
        Particle1D enteringSecond = new Particle1D(
                new double[]{8.0}, new double[]{-1.0}, 0.0, particleType, entryInfo);
        EventInfo entry = new EventInfo(Collision.PARTICLE, Calendar.MAXTIME, 0, 1, 0);
        enteringFirst.predCol(enteringSecond, null, entry);
        if (entry.colType != Collision.ENTER_WELL) {
            throw new AssertionError("expected ENTER_WELL but got collision type " + entry.colType);
        }
        assertClose("well entry time", entry.colTime, 1.0);
        enteringFirst.collideWith(enteringSecond, entry);
        assertClose("well entry first velocity", enteringFirst.getXVel(), Math.sqrt(2.0));
        assertClose("well entry second velocity", enteringSecond.getXVel(), -Math.sqrt(2.0));

        SimulationInfo exitInfo = createSimulationInfo(
                particleType, 1, 30.0, 0.0, 0.0,
                SimulationInfo.ArenaType.REFLECTING_BOUNDARIES,
                0.0, false, true, 2.0, 0.5);
        Particle1D exitingFirst = new Particle1D(
                new double[]{2.0}, new double[]{-1.0}, 0.0, particleType, exitInfo);
        Particle1D exitingSecond = new Particle1D(
                new double[]{5.5}, new double[]{1.0}, 0.0, particleType, exitInfo);
        EventInfo lastEntry = new EventInfo(Collision.ENTER_WELL, Calendar.MINTIME, 0, 1, 0);
        EventInfo exit = new EventInfo(Collision.PARTICLE, Calendar.MAXTIME, 0, 1, 0);
        exitingFirst.predCol(exitingSecond, lastEntry, exit);
        if (exit.colType != Collision.EXIT_WELL) {
            throw new AssertionError("expected EXIT_WELL but got collision type " + exit.colType);
        }
        assertClose("well exit time", exit.colTime, 0.25);
        exitingFirst.collideWith(exitingSecond, exit);
        assertClose("well exit first velocity", exitingFirst.getXVel(), -Math.sqrt(2.0) / 2.0);
        assertClose("well exit second velocity", exitingSecond.getXVel(), Math.sqrt(2.0) / 2.0);

        SimulationInfo trappedInfo = createSimulationInfo(
                particleType, 1, 30.0, 0.0, 0.0,
                SimulationInfo.ArenaType.REFLECTING_BOUNDARIES,
                0.0, false, true, 2.0, 4.0);
        Particle1D trappedFirst = new Particle1D(
                new double[]{2.0}, new double[]{-1.0}, 0.0, particleType, trappedInfo);
        Particle1D trappedSecond = new Particle1D(
                new double[]{5.5}, new double[]{1.0}, 0.0, particleType, trappedInfo);
        EventInfo trappedLastEntry = new EventInfo(
                Collision.ENTER_WELL, Calendar.MINTIME, 0, 1, 0);
        EventInfo trappedExit = new EventInfo(Collision.PARTICLE, Calendar.MAXTIME, 0, 1, 0);
        trappedFirst.predCol(trappedSecond, trappedLastEntry, trappedExit);
        assertClose("trapped well exit time", trappedExit.colTime, 0.25);
        trappedFirst.collideWith(trappedSecond, trappedExit);
        if (trappedExit.colType != Collision.ENTER_WELL) {
            throw new AssertionError("insufficient exit energy should leave the pair in the well");
        }
        assertClose("trapped first velocity", trappedFirst.getXVel(), 1.0);
        assertClose("trapped second velocity", trappedSecond.getXVel(), -1.0);
        System.out.println(
                "1D energy well PASS: entry, exit, and insufficient-energy reflection match Java equations");
    }

    private static void testTwoDimensionalParticleCollision() {
        ParticleType particleType = new ParticleType(1.0, 1.0, Color.RED, "test");
        SimulationInfo reflectingInfo = createReflectingSimulationInfo(
                particleType, 2, 10.0, 10.0, 10.0);
        Particle2D first = new Particle2D(
                new double[]{0.0, 0.0}, new double[]{1.0, 0.0}, 0.0,
                particleType, reflectingInfo);
        Particle2D second = new Particle2D(
                new double[]{5.0, 1.0}, new double[]{-1.0, 0.0}, 0.0,
                particleType, reflectingInfo);
        EventInfo collision = new EventInfo(Collision.PARTICLE, Calendar.MAXTIME, 0, 1, 0);

        first.predCol(second, null, collision);
        assertClose("2D particle collision time", collision.colTime, (5.0 - Math.sqrt(3.0)) / 2.0);
        first.collideWith(second, collision);
        assertClose("2D first x velocity", first.getXVel(), -0.5);
        assertClose("2D first y velocity", first.getYVel(), -Math.sqrt(3.0) / 2.0);
        assertClose("2D second x velocity", second.getXVel(), 0.5);
        assertClose("2D second y velocity", second.getYVel(), Math.sqrt(3.0) / 2.0);
        System.out.printf(Locale.ROOT,
                "2D particle collision PASS: time=%.12f velocities=(%.12f, %.12f), (%.12f, %.12f)%n",
                collision.colTime, first.getXVel(), first.getYVel(),
                second.getXVel(), second.getYVel());
    }

    private static void testThreeDimensionalParticleCollision() {
        ParticleType particleType = new ParticleType(1.0, 1.0, Color.RED, "test");
        SimulationInfo reflectingInfo = createReflectingSimulationInfo(
                particleType, 3, 10.0, 10.0, 10.0);
        Particle3D first = new Particle3D(
                new double[]{0.0, 0.0, 0.0}, new double[]{1.0, 0.0, 0.0}, 0.0,
                particleType, reflectingInfo);
        Particle3D second = new Particle3D(
                new double[]{5.0, 1.0, 1.0}, new double[]{-1.0, 0.0, 0.0}, 0.0,
                particleType, reflectingInfo);
        EventInfo collision = new EventInfo(Collision.PARTICLE, Calendar.MAXTIME, 0, 1, 0);

        first.predCol(second, null, collision);
        assertClose("3D particle collision time", collision.colTime, (5.0 - Math.sqrt(2.0)) / 2.0);
        first.collideWith(second, collision);
        assertClose("3D first x velocity", first.getXVel(), 0.0);
        assertClose("3D first y velocity", first.getYVel(), -Math.sqrt(2.0) / 2.0);
        assertClose("3D first z velocity", first.getZVel(), -Math.sqrt(2.0) / 2.0);
        assertClose("3D second x velocity", second.getXVel(), 0.0);
        assertClose("3D second y velocity", second.getYVel(), Math.sqrt(2.0) / 2.0);
        assertClose("3D second z velocity", second.getZVel(), Math.sqrt(2.0) / 2.0);
        System.out.printf(Locale.ROOT,
                "3D particle collision PASS: time=%.12f velocities=(%.12f, %.12f, %.12f), (%.12f, %.12f, %.12f)%n",
                collision.colTime, first.getXVel(), first.getYVel(), first.getZVel(),
                second.getXVel(), second.getYVel(), second.getZVel());
    }

    private static void testPeriodicParticleCollisionAcrossBoundary() {
        ParticleType particleType = new ParticleType(1.0, 1.0, Color.RED, "test");
        SimulationInfo periodicInfo = createSimulationInfo(
                particleType, 1, 10.0, 10.0, 10.0,
                SimulationInfo.ArenaType.PERIODIC_BOUNDARIES);
        Particle1D first = new Particle1D(
                new double[]{2.0}, new double[]{-1.0}, 0.0,
                particleType, periodicInfo);
        Particle1D second = new Particle1D(
                new double[]{9.5}, new double[]{1.0}, 0.0,
                particleType, periodicInfo);
        EventInfo collision = new EventInfo(Collision.PARTICLE, Calendar.MAXTIME, 0, 1, 0);

        first.predCol(second, null, collision);
        assertClose("periodic collision time", collision.colTime, 0.25);
        first.collideWith(second, collision);
        assertClose("periodic first velocity", first.getXVel(), 1.0);
        assertClose("periodic second velocity", second.getXVel(), -1.0);

        SimulationInfo periodic2DInfo = createSimulationInfo(
                particleType, 2, 10.0, 10.0, 10.0,
                SimulationInfo.ArenaType.PERIODIC_BOUNDARIES);
        Particle2D first2D = new Particle2D(
                new double[]{2.0, 5.0}, new double[]{-1.0, 0.0}, 0.0,
                particleType, periodic2DInfo);
        Particle2D second2D = new Particle2D(
                new double[]{9.5, 5.0}, new double[]{1.0, 0.0}, 0.0,
                particleType, periodic2DInfo);
        EventInfo collision2D = new EventInfo(Collision.PARTICLE, Calendar.MAXTIME, 0, 1, 0);
        first2D.predCol(second2D, null, collision2D);
        assertClose("2D periodic collision time", collision2D.colTime, 0.25);
        first2D.collideWith(second2D, collision2D);
        assertClose("2D periodic first velocity", first2D.getXVel(), 1.0);
        assertClose("2D periodic second velocity", second2D.getXVel(), -1.0);

        SimulationInfo periodic3DInfo = createSimulationInfo(
                particleType, 3, 10.0, 10.0, 10.0,
                SimulationInfo.ArenaType.PERIODIC_BOUNDARIES);
        Particle3D first3D = new Particle3D(
                new double[]{2.0, 5.0, 5.0}, new double[]{-1.0, 0.0, 0.0}, 0.0,
                particleType, periodic3DInfo);
        Particle3D second3D = new Particle3D(
                new double[]{9.5, 5.0, 5.0}, new double[]{1.0, 0.0, 0.0}, 0.0,
                particleType, periodic3DInfo);
        EventInfo collision3D = new EventInfo(Collision.PARTICLE, Calendar.MAXTIME, 0, 1, 0);
        first3D.predCol(second3D, null, collision3D);
        assertClose("3D periodic collision time", collision3D.colTime, 0.25);
        first3D.collideWith(second3D, collision3D);
        assertClose("3D periodic first velocity", first3D.getXVel(), 1.0);
        assertClose("3D periodic second velocity", second3D.getXVel(), -1.0);
        System.out.println(
                "Periodic image collision PASS in 1D/2D/3D: time=0.25 velocities=(1.0, -1.0)");
    }

    private static void testOneDimensionalWallHandling() {
        ParticleType particleType = new ParticleType(1.0, 1.0, Color.RED, "test");
        SimulationInfo reflectingInfo = createReflectingSimulationInfo(particleType, 1, 10.0, 10.0, 10.0);

        Particle1D leftMoving = new Particle1D(
                new double[]{3.0}, new double[]{-2.0}, 0.0,
                particleType, reflectingInfo);
        EventInfo leftWall = leftMoving.predBoundaryCol(null, false);
        assertClose("left wall time", leftWall.colTime, 1.0);
        if (leftWall.colType != Collision.WALL || leftWall.side != Wall.LEFT) {
            throw new AssertionError("expected a left-wall collision");
        }
        leftMoving.boundaryCollide(leftWall, null, new Thermostat(false));
        assertClose("left wall position", leftMoving.getX(), 1.0);
        assertClose("left wall velocity", leftMoving.getXVel(), 2.0);

        Particle1D rightMoving = new Particle1D(
                new double[]{3.0}, new double[]{2.0}, 2.0,
                particleType, reflectingInfo);
        EventInfo rightWall = rightMoving.predBoundaryCol(null, false);
        assertClose("right wall time", rightWall.colTime, 5.0);
        if (rightWall.colType != Collision.WALL || rightWall.side != Wall.RIGHT) {
            throw new AssertionError("expected a right-wall collision");
        }
        rightMoving.boundaryCollide(rightWall, null, new Thermostat(false));
        assertClose("right wall position", rightMoving.getX(), 9.0);
        assertClose("right wall velocity", rightMoving.getXVel(), -2.0);

        Particle1D stationary = new Particle1D(
                new double[]{3.0}, new double[]{0.0}, 0.0,
                particleType, reflectingInfo);
        EventInfo noWall = stationary.predBoundaryCol(null, false);
        if (noWall.colTime != Calendar.MAXTIME) {
            throw new AssertionError("stationary particle should not have a wall event");
        }

        System.out.println(
                "1D wall handling PASS: left=(time 1.0, x 1.0, v 2.0) "
                + "right=(time 5.0, x 9.0, v -2.0) stationary=no collision");
    }

    private static void testTwoDimensionalWallHandling() {
        ParticleType particleType = new ParticleType(1.0, 1.0, Color.RED, "test");
        SimulationInfo reflectingInfo = createReflectingSimulationInfo(
                particleType, 2, 10.0, 8.0, 10.0);
        Particle2D particle = new Particle2D(
                new double[]{3.0, 4.0}, new double[]{-2.0, 1.0}, 0.0,
                particleType, reflectingInfo);
        EventInfo wall = particle.predBoundaryCol(null, false);

        assertClose("2D wall time", wall.colTime, 1.0);
        if (wall.colType != Collision.WALL || wall.side != Wall.LEFT) {
            throw new AssertionError("expected the 2D particle to hit the left wall first");
        }
        particle.boundaryCollide(wall, null, new Thermostat(false));
        assertClose("2D wall x position", particle.getX(), 1.0);
        assertClose("2D wall y position", particle.getY(), 5.0);
        assertClose("2D wall x velocity", particle.getXVel(), 2.0);
        assertClose("2D wall y velocity", particle.getYVel(), 1.0);
        System.out.println(
                "2D wall handling PASS: time=1.0 position=(1.0, 5.0) velocity=(2.0, 1.0)");
    }

    private static void testThreeDimensionalWallHandling() {
        ParticleType particleType = new ParticleType(1.0, 1.0, Color.RED, "test");
        SimulationInfo reflectingInfo = createReflectingSimulationInfo(
                particleType, 3, 10.0, 10.0, 10.0);
        Particle3D particle = new Particle3D(
                new double[]{2.0, 3.0, 4.0}, new double[]{0.0, -1.0, 2.0}, 0.0,
                particleType, reflectingInfo);
        EventInfo wall = particle.predBoundaryCol(null, false);

        assertClose("3D wall time", wall.colTime, 2.0);
        if (wall.colType != Collision.WALL || wall.side != Wall.BOTTOM) {
            throw new AssertionError("expected the 3D particle to hit the bottom wall first");
        }
        particle.boundaryCollide(wall, null, new Thermostat(false));
        assertClose("3D wall x position", particle.getX(), 2.0);
        assertClose("3D wall y position", particle.getY(), 1.0);
        assertClose("3D wall z position", particle.getZ(), 8.0);
        assertClose("3D wall x velocity", particle.getXVel(), 0.0);
        assertClose("3D wall y velocity", particle.getYVel(), 1.0);
        assertClose("3D wall z velocity", particle.getZVel(), 2.0);
        System.out.println(
                "3D wall handling PASS: time=2.0 position=(2.0, 1.0, 8.0) velocity=(0.0, 1.0, 2.0)");
    }

    private static void testOneDimensionalDividerHandling() {
        ParticleType particleType = new ParticleType(1.0, 1.0, Color.RED, "test");
        SimulationInfo dividedInfo = createSimulationInfo(
                particleType, 1, 10.0, 10.0, 10.0,
                SimulationInfo.ArenaType.DIVIDED_ARENA);
        Particle1D particle = new Particle1D(
                new double[]{2.0}, new double[]{1.0}, 0.0,
                particleType, dividedInfo);
        EventInfo divider = particle.predBoundaryCol(null, false);

        assertClose("1D divider time", divider.colTime, 2.0 - SimulationInfo.ARENA_DIVIDER_RADIUS);
        if (divider.colType != Collision.BARRIER || divider.side != Wall.LEFT) {
            throw new AssertionError("expected a left-side divider collision");
        }
        particle.boundaryCollide(divider, null, new Thermostat(false));
        assertClose("1D divider position", particle.getX(), 4.0 - SimulationInfo.ARENA_DIVIDER_RADIUS);
        assertClose("1D divider velocity", particle.getXVel(), -1.0);

        Particle1D rightSide = new Particle1D(
                new double[]{8.0}, new double[]{-1.0}, 0.0,
                particleType, dividedInfo);
        EventInfo rightDivider = rightSide.predBoundaryCol(null, false);
        assertClose("1D right divider time", rightDivider.colTime,
                2.0 - SimulationInfo.ARENA_DIVIDER_RADIUS);
        if (rightDivider.colType != Collision.BARRIER || rightDivider.side != Wall.RIGHT) {
            throw new AssertionError("expected a right-side divider collision");
        }
        rightSide.boundaryCollide(rightDivider, null, new Thermostat(false));
        assertClose("1D right divider position", rightSide.getX(),
                6.0 + SimulationInfo.ARENA_DIVIDER_RADIUS);
        assertClose("1D right divider velocity", rightSide.getXVel(), 1.0);
        System.out.println("1D divider handling PASS: left and right impacts");
    }

    private static void testTwoDimensionalDividerHandling() {
        ParticleType particleType = new ParticleType(1.0, 1.0, Color.RED, "test");
        SimulationInfo dividedInfo = createSimulationInfo(
                particleType, 2, 10.0, 10.0, 10.0,
                SimulationInfo.ArenaType.DIVIDED_ARENA);
        Particle2D particle = new Particle2D(
                new double[]{2.0, 4.0}, new double[]{1.0, 0.5}, 0.0,
                particleType, dividedInfo);
        EventInfo divider = particle.predBoundaryCol(null, false);

        assertClose("2D divider time", divider.colTime, 2.0 - SimulationInfo.ARENA_DIVIDER_RADIUS);
        if (divider.colType != Collision.BARRIER || divider.side != Wall.LEFT) {
            throw new AssertionError("expected a 2D left-side divider collision");
        }
        particle.boundaryCollide(divider, null, new Thermostat(false));
        assertClose("2D divider x position", particle.getX(), 4.0 - SimulationInfo.ARENA_DIVIDER_RADIUS);
        assertClose("2D divider y position", particle.getY(), 5.0 - 0.5 * SimulationInfo.ARENA_DIVIDER_RADIUS);
        assertClose("2D divider x velocity", particle.getXVel(), -1.0);
        assertClose("2D divider y velocity", particle.getYVel(), 0.5);
        System.out.println("2D divider handling PASS: x reflects and tangent motion continues");
    }

    private static void testThreeDimensionalDividerHandling() {
        ParticleType particleType = new ParticleType(1.0, 1.0, Color.RED, "test");
        SimulationInfo dividedInfo = createSimulationInfo(
                particleType, 3, 10.0, 10.0, 10.0,
                SimulationInfo.ArenaType.DIVIDED_ARENA);
        Particle3D particle = new Particle3D(
                new double[]{2.0, 4.0, 3.0}, new double[]{1.0, 0.5, -0.25}, 0.0,
                particleType, dividedInfo);
        EventInfo divider = particle.predBoundaryCol(null, false);

        assertClose("3D divider time", divider.colTime, 2.0 - SimulationInfo.ARENA_DIVIDER_RADIUS);
        if (divider.colType != Collision.BARRIER || divider.side != Wall.LEFT) {
            throw new AssertionError("expected a 3D left-side divider collision");
        }
        particle.boundaryCollide(divider, null, new Thermostat(false));
        assertClose("3D divider x position", particle.getX(), 4.0 - SimulationInfo.ARENA_DIVIDER_RADIUS);
        assertClose("3D divider y position", particle.getY(), 5.0 - 0.5 * SimulationInfo.ARENA_DIVIDER_RADIUS);
        assertClose("3D divider z position", particle.getZ(), 2.5 + 0.25 * SimulationInfo.ARENA_DIVIDER_RADIUS);
        assertClose("3D divider x velocity", particle.getXVel(), -1.0);
        assertClose("3D divider y velocity", particle.getYVel(), 0.5);
        assertClose("3D divider z velocity", particle.getZVel(), -0.25);
        System.out.println("3D divider handling PASS: x reflects and tangent motion continues");
    }

    private static void testTwoDimensionalDividerHole() {
        ParticleType particleType = new ParticleType(1.0, 1.0, Color.RED, "test");
        SimulationInfo holeInfo = createSimulationInfo(
                particleType, 2, 10.0, 10.0, 10.0,
                SimulationInfo.ArenaType.DIVIDED_ARENA_WITH_HOLE, 4.0);

        Particle2D throughHole = new Particle2D(
                new double[]{2.0, 5.0}, new double[]{1.0, 0.0}, 0.0,
                particleType, holeInfo);
        EventInfo passage = throughHole.predBoundaryCol(null, false);
        assertClose("2D hole next boundary time", passage.colTime, 7.0);
        if (passage.colType != Collision.WALL || passage.side != Wall.RIGHT) {
            throw new AssertionError("centered particle should pass through the divider hole");
        }
        throughHole.moveToTime(4.0);
        assertClose("2D hole passage x", throughHole.getX(), 6.0);
        assertClose("2D hole passage x velocity", throughHole.getXVel(), 1.0);

        Particle2D solidPart = new Particle2D(
                new double[]{2.0, 8.0}, new double[]{1.0, 0.0}, 0.0,
                particleType, holeInfo);
        EventInfo barrier = solidPart.predBoundaryCol(null, false);
        assertClose("2D hole solid-part time", barrier.colTime,
                2.0 - SimulationInfo.ARENA_DIVIDER_RADIUS);
        if (barrier.colType != Collision.BARRIER || barrier.side != Wall.LEFT) {
            throw new AssertionError("particle outside the opening should hit the divider");
        }
        solidPart.boundaryCollide(barrier, null, new Thermostat(false));
        assertClose("2D hole solid-part velocity", solidPart.getXVel(), -1.0);

        Particle2D holeEdge = new Particle2D(
                new double[]{2.0, 6.9}, new double[]{1.0, 0.0}, 0.0,
                particleType, holeInfo);
        EventInfo edge = holeEdge.predBoundaryCol(null, false);
        if (edge.colType != Collision.EDGE) {
            throw new AssertionError("particle near the opening edge should hit its edge particle");
        }
        double initialXVelocity = holeEdge.getXVel();
        holeEdge.boundaryCollide(edge, null, new Thermostat(false));
        if (Math.abs(holeEdge.getXVel() - initialXVelocity) < TOLERANCE) {
            throw new AssertionError("hole-edge collision should change the velocity direction");
        }

        SimulationInfo demonInfo = createSimulationInfo(
                particleType, 2, 10.0, 10.0, 10.0,
                SimulationInfo.ArenaType.DIVIDED_ARENA_WITH_HOLE, 4.0, true);
        Particle2D demonParticle = new Particle2D(
                new double[]{2.0, 5.0}, new double[]{1.0, 0.0}, 0.0,
                particleType, demonInfo);
        EventInfo openDemonHole = demonParticle.predBoundaryCol(null, true);
        assertClose("open demon-hole event time", openDemonHole.colTime, 7.0);
        if (openDemonHole.colType != Collision.WALL) {
            throw new AssertionError("open Maxwell-demon hole should allow passage");
        }
        EventInfo closedDemonHole = demonParticle.predBoundaryCol(null, false);
        assertClose("closed demon-hole event time", closedDemonHole.colTime,
                2.0 - SimulationInfo.ARENA_DIVIDER_RADIUS);
        if (closedDemonHole.colType != Collision.BARRIER) {
            throw new AssertionError("closed Maxwell-demon hole should act as a divider");
        }
        System.out.println(
                "2D divider hole PASS: passage, solid divider, edge reflection, open/closed state");
    }

    private static SimulationInfo createReflectingSimulationInfo(
            final ParticleType particleType,
            final int dimension,
            final double arenaXSize,
            final double arenaYSize,
            final double arenaZSize) {
        return createSimulationInfo(
                particleType, dimension, arenaXSize, arenaYSize, arenaZSize,
                SimulationInfo.ArenaType.REFLECTING_BOUNDARIES);
    }

    private static SimulationInfo createSimulationInfo(
            final ParticleType particleType,
            final int dimension,
            final double arenaXSize,
            final double arenaYSize,
            final double arenaZSize,
            final SimulationInfo.ArenaType arenaType) {
        return createSimulationInfo(
                particleType, dimension, arenaXSize, arenaYSize, arenaZSize,
                arenaType, 0.0);
    }

    private static SimulationInfo createSimulationInfo(
            final ParticleType particleType,
            final int dimension,
            final double arenaXSize,
            final double arenaYSize,
            final double arenaZSize,
            final SimulationInfo.ArenaType arenaType,
            final double holeDiameter) {
        return createSimulationInfo(
                particleType, dimension, arenaXSize, arenaYSize, arenaZSize,
                arenaType, holeDiameter, false);
    }

    private static SimulationInfo createSimulationInfo(
            final ParticleType particleType,
            final int dimension,
            final double arenaXSize,
            final double arenaYSize,
            final double arenaZSize,
            final SimulationInfo.ArenaType arenaType,
            final double holeDiameter,
            final boolean maxwellDemonMode) {
        return createSimulationInfo(
                particleType, dimension, arenaXSize, arenaYSize, arenaZSize,
                arenaType, holeDiameter, maxwellDemonMode, false, 0.0, 0.0);
    }

    private static SimulationInfo createSimulationInfo(
            final ParticleType particleType,
            final int dimension,
            final double arenaXSize,
            final double arenaYSize,
            final double arenaZSize,
            final SimulationInfo.ArenaType arenaType,
            final double holeDiameter,
            final boolean maxwellDemonMode,
            final boolean attractiveInteractions,
            final double interactionRadiusMultiplier,
            final double energyWellDepth) {
        SimulationInfoSupplier supplier = (SimulationInfoSupplier) Proxy.newProxyInstance(
                SimulationInfoSupplier.class.getClassLoader(),
                new Class<?>[]{SimulationInfoSupplier.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getArenaType".equals(name)) {
                        return arenaType;
                    }
                    if ("getDimension".equals(name)) {
                        return dimension;
                    }
                    if ("getArenaXSize".equals(name)) {
                        return arenaXSize;
                    }
                    if ("getArenaYSize".equals(name)) {
                        return arenaYSize;
                    }
                    if ("getArenaZSize".equals(name)) {
                        return arenaZSize;
                    }
                    if ("getHoleDiameter".equals(name)) {
                        return holeDiameter;
                    }
                    if ("isMaxwellDemonModeSelected".equals(name)) {
                        return maxwellDemonMode;
                    }
                    if ("areAttractiveInteractionsAllowed".equals(name)) {
                        return attractiveInteractions;
                    }
                    if ("getRadiusOfInteractionMultiplier".equals(name)) {
                        return interactionRadiusMultiplier;
                    }
                    if ("getEnergyWellDepth".equals(name)) {
                        return energyWellDepth;
                    }
                    if ("getNumberOfParticleTypes".equals(name)) {
                        return 1;
                    }
                    if ("getParticleType".equals(name)) {
                        return particleType;
                    }
                    if ("getNumberOfParticles".equals(name)) {
                        return 1;
                    }
                    if ("getParticlePosition".equals(name)) {
                        return new double[]{5.0, 0.0, 0.0};
                    }
                    if ("getParticleVelocity".equals(name)) {
                        return new double[]{0.0, 0.0, 0.0};
                    }
                    if ("getRecordedStatistics".equals(name)) {
                        return new StatisticID[0];
                    }

                    Class<?> returnType = method.getReturnType();
                    if (returnType == boolean.class) {
                        return false;
                    }
                    if (returnType == int.class) {
                        return 0;
                    }
                    if (returnType == double.class) {
                        return 0.0;
                    }
                    return null;
                });
        return new SimulationInfo(supplier);
    }

    private static void assertClose(String label, double actual, double expected) {
        if (Math.abs(actual - expected) > TOLERANCE) {
            throw new AssertionError(
                    label + " expected " + expected + " but got " + actual);
        }
    }
}
