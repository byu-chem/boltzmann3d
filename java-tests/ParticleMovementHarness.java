import edu.byu.chem.boltzmann.model.physics.Particle1D;
import edu.byu.chem.boltzmann.model.physics.Particle2D;
import edu.byu.chem.boltzmann.model.physics.Particle3D;
import edu.byu.chem.boltzmann.model.physics.Collision;
import edu.byu.chem.boltzmann.model.physics.EventInfo;
import edu.byu.chem.boltzmann.utils.data.ParticleType;
import edu.byu.chem.boltzmann.utils.data.SimulationInfo;
import java.awt.Color;
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

    private static void assertClose(String label, double actual, double expected) {
        if (Math.abs(actual - expected) > TOLERANCE) {
            throw new AssertionError(
                    label + " expected " + expected + " but got " + actual);
        }
    }
}
