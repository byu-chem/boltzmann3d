import unittest

from boltzmann3d.physics import NO_COLLISION_TIME, Particle


class ParticleMovementTests(unittest.TestCase):
    def test_moves_in_one_dimension(self) -> None:
        particle = Particle(position=(2.0,), velocity=(3.0,))

        particle.move_to_time(4.0)

        self.assertEqual(particle.position, (14.0,))
        self.assertEqual(particle.velocity, (3.0,))
        self.assertEqual(particle.time, 4.0)
        self.assertEqual(particle.time_since_collision, 4.0)

    def test_moves_in_two_dimensions(self) -> None:
        particle = Particle(position=(1.0, 5.0), velocity=(2.0, -1.0))

        particle.move_to_time(3.0)

        self.assertEqual(particle.position, (7.0, 2.0))

    def test_moves_in_three_dimensions(self) -> None:
        particle = Particle(
            position=(1.0, 2.0, 3.0),
            velocity=(2.0, -1.0, 0.5),
        )

        particle.move_to_time(2.0)

        self.assertEqual(particle.position, (5.0, 0.0, 4.0))

    def test_repeated_moves_use_elapsed_time_since_last_update(self) -> None:
        particle = Particle(position=(0.0,), velocity=(2.0,))

        particle.move_to_time(1.5)
        particle.move_to_time(4.0)

        self.assertEqual(particle.position, (8.0,))
        self.assertEqual(particle.time, 4.0)
        self.assertEqual(particle.time_since_collision, 4.0)

    def test_fractional_values_preserve_float_arithmetic(self) -> None:
        particle = Particle(position=(0.1,), velocity=(0.2,), time=0.1)

        particle.move_to_time(0.4)

        self.assertEqual(particle.position, (0.16000000000000003,))

    def test_one_dimensional_elastic_collision_matches_java_case(self) -> None:
        first = Particle(position=(0.0,), velocity=(3.0,), mass=1.0)
        second = Particle(position=(4.0,), velocity=(-1.0,), mass=2.0)

        first.collide_1d(second, collision_time=0.5)

        self.assertEqual(first.position, (1.5,))
        self.assertEqual(second.position, (3.5,))
        self.assertAlmostEqual(first.velocity[0], -7.0 / 3.0)
        self.assertAlmostEqual(second.velocity[0], 5.0 / 3.0)
        self.assertEqual(first.time, 0.5)
        self.assertEqual(second.time, 0.5)
        self.assertEqual(first.time_since_collision, 0.0)
        self.assertEqual(second.time_since_collision, 0.0)

        initial_momentum = 1.0 * 3.0 + 2.0 * -1.0
        final_momentum = first.mass * first.velocity[0] + second.mass * second.velocity[0]
        self.assertAlmostEqual(final_momentum, initial_momentum)

        initial_energy = 0.5 * 1.0 * 3.0**2 + 0.5 * 2.0 * (-1.0) ** 2
        final_energy = (
            0.5 * first.mass * first.velocity[0] ** 2
            + 0.5 * second.mass * second.velocity[0] ** 2
        )
        self.assertAlmostEqual(final_energy, initial_energy)

    def test_one_dimensional_collision_rejects_other_dimensions(self) -> None:
        first = Particle(position=(0.0, 0.0), velocity=(1.0, 0.0))
        second = Particle(position=(1.0,), velocity=(-1.0,))

        with self.assertRaises(ValueError):
            first.collide_1d(second, collision_time=1.0)

    def test_predicts_approaching_particle_collision(self) -> None:
        first = Particle(position=(0.0,), velocity=(1.0,), radius=1.0)
        second = Particle(position=(5.0,), velocity=(-1.0,), radius=1.0)

        collision_time = first.predict_collision_time_1d(second)

        self.assertEqual(collision_time, 1.5)
        self.assertEqual(first.position, (0.0,))
        self.assertEqual(second.position, (5.0,))

    def test_does_not_predict_particles_moving_apart(self) -> None:
        first = Particle(position=(0.0,), velocity=(-1.0,), radius=1.0)
        second = Particle(position=(5.0,), velocity=(1.0,), radius=1.0)

        self.assertEqual(
            first.predict_collision_time_1d(second), NO_COLLISION_TIME
        )

    def test_does_not_predict_collision_with_no_relative_motion(self) -> None:
        first = Particle(position=(0.0,), velocity=(2.0,), radius=1.0)
        second = Particle(position=(5.0,), velocity=(2.0,), radius=1.0)

        self.assertEqual(
            first.predict_collision_time_1d(second), NO_COLLISION_TIME
        )

    def test_prediction_aligns_particles_at_their_latest_state_time(self) -> None:
        first = Particle(position=(1.0,), velocity=(1.0,), time=1.0, radius=1.0)
        second = Particle(position=(5.0,), velocity=(-1.0,), time=0.0, radius=1.0)

        self.assertEqual(first.predict_collision_time_1d(second), 1.5)

    def test_collision_prediction_rejects_other_dimensions(self) -> None:
        first = Particle(position=(0.0, 0.0), velocity=(1.0, 0.0))
        second = Particle(position=(1.0,), velocity=(-1.0,))

        with self.assertRaises(ValueError):
            first.predict_collision_time_1d(second)

    def test_rejects_different_position_and_velocity_dimensions(self) -> None:
        with self.assertRaises(ValueError):
            Particle(position=(1.0, 2.0), velocity=(3.0,))


if __name__ == "__main__":
    unittest.main()
