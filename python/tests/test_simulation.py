import math
import unittest

from boltzmann3d.physics import Particle, Simulation


class SimulationEventLoopTests(unittest.TestCase):
    def test_next_event_selects_earliest_particle_or_wall_collision(self) -> None:
        wall_particle = Particle(position=(2.0,), velocity=(-1.0,), radius=1.0)
        collision_first = Particle(position=(3.0,), velocity=(1.0,), radius=1.0)
        collision_second = Particle(position=(8.0,), velocity=(-1.0,), radius=1.0)
        simulation = Simulation(
            [wall_particle, collision_first, collision_second], (10.0,)
        )

        event = simulation.next_event()

        self.assertIsNotNone(event)
        self.assertEqual(event.kind, "wall")
        self.assertEqual(event.time, 1.0)
        self.assertEqual(event.particle_indices, (0,))
        self.assertEqual(event.axis, 0)
        self.assertEqual(wall_particle.position, (2.0,))

    def test_advance_to_processes_multiple_successive_wall_bounces(self) -> None:
        particle = Particle(position=(5.0,), velocity=(2.0,), radius=1.0)
        simulation = Simulation([particle], (10.0,))

        events = simulation.advance_to(7.0)

        self.assertEqual([(event.time, event.axis) for event in events], [(2.0, 0), (6.0, 0)])
        self.assertEqual(particle.position, (3.0,))
        self.assertEqual(particle.velocity, (2.0,))
        self.assertEqual(particle.time, 7.0)
        self.assertEqual(simulation.current_time, 7.0)

    def test_advance_to_processes_particle_collision_then_continues_motion(self) -> None:
        first = Particle(position=(2.0,), velocity=(1.0,), radius=1.0)
        second = Particle(position=(8.0,), velocity=(-1.0,), radius=1.0)
        simulation = Simulation([first, second], (20.0,))

        events = simulation.advance_to(3.0)

        self.assertEqual(len(events), 1)
        self.assertEqual((events[0].kind, events[0].time), ("particle", 2.0))
        self.assertEqual(first.position, (3.0,))
        self.assertEqual(second.position, (7.0,))
        self.assertEqual(first.velocity, (-1.0,))
        self.assertEqual(second.velocity, (1.0,))

    def test_advance_to_processes_wall_before_later_particle_collision(self) -> None:
        first = Particle(position=(2.0,), velocity=(-1.0,), radius=1.0)
        second = Particle(position=(6.0,), velocity=(-1.0,), radius=1.0)
        simulation = Simulation([first, second], (10.0,))

        events = simulation.advance_to(2.0)

        self.assertEqual(
            [(event.kind, event.time) for event in events],
            [("wall", 1.0), ("particle", 2.0)],
        )
        self.assertEqual(first.position, (2.0,))
        self.assertEqual(first.velocity, (-1.0,))
        self.assertEqual(second.position, (4.0,))
        self.assertEqual(second.velocity, (1.0,))

    def test_simulation_handles_2d_and_3d_events(self) -> None:
        two_d = Particle(position=(3.0, 4.0), velocity=(-2.0, 1.0), radius=1.0)
        three_d = Particle(
            position=(3.0, 4.0, 5.0), velocity=(-2.0, 1.0, 0.0), radius=1.0
        )
        two_d_simulation = Simulation([two_d], (10.0, 10.0))
        three_d_simulation = Simulation([three_d], (10.0, 10.0, 10.0))

        two_d_events = two_d_simulation.advance_to(1.5)
        three_d_events = three_d_simulation.advance_to(1.5)

        self.assertEqual([(event.time, event.axis) for event in two_d_events], [(1.0, 0)])
        self.assertEqual(two_d.position, (2.0, 5.5))
        self.assertEqual(two_d.velocity, (2.0, 1.0))
        self.assertEqual([(event.time, event.axis) for event in three_d_events], [(1.0, 0)])
        self.assertEqual(three_d.position, (2.0, 5.5, 5.0))
        self.assertEqual(three_d.velocity, (2.0, 1.0, 0.0))

    def test_simulation_advances_particles_without_any_events(self) -> None:
        particle = Particle(position=(5.0,), velocity=(0.0,), radius=1.0)
        simulation = Simulation([particle], (10.0,))

        self.assertEqual(simulation.advance_to(4.0), ())
        self.assertEqual(particle.position, (5.0,))
        self.assertEqual(particle.time, 4.0)

    def test_simulation_aligns_particles_with_different_initial_times(self) -> None:
        earlier = Particle(position=(1.0,), velocity=(2.0,), time=0.0, radius=1.0)
        later = Particle(position=(8.0,), velocity=(0.0,), time=2.0, radius=1.0)

        simulation = Simulation([earlier, later], (10.0,))

        self.assertEqual(simulation.current_time, 2.0)
        self.assertEqual(earlier.position, (5.0,))
        self.assertEqual(earlier.time, 2.0)

    def test_simulation_rejects_advancing_backwards(self) -> None:
        simulation = Simulation(
            [Particle(position=(5.0,), velocity=(0.0,), radius=1.0)], (10.0,)
        )

        with self.assertRaises(ValueError):
            simulation.advance_to(-1.0)

    def test_simulation_requires_matching_arena_dimension(self) -> None:
        with self.assertRaises(ValueError):
            Simulation(
                [Particle(position=(5.0, 5.0), velocity=(0.0, 0.0), radius=1.0)],
                (10.0,),
            )

    def test_periodic_particle_wraps_from_right_edge_and_keeps_velocity(self) -> None:
        particle = Particle(position=(9.0,), velocity=(2.0,), radius=1.0)
        simulation = Simulation([particle], (10.0,), boundary_mode="periodic")

        events = simulation.advance_to(1.0)

        self.assertEqual([(event.kind, event.time) for event in events], [("wrap", 0.5)])
        self.assertEqual(particle.position, (1.0,))
        self.assertEqual(particle.velocity, (2.0,))

    def test_periodic_particle_wraps_from_left_edge_and_keeps_velocity(self) -> None:
        particle = Particle(position=(1.0,), velocity=(-2.0,), radius=1.0)
        simulation = Simulation([particle], (10.0,), boundary_mode="periodic")

        events = simulation.advance_to(1.0)

        self.assertEqual([(event.kind, event.time) for event in events], [("wrap", 0.5)])
        self.assertEqual(particle.position, (9.0,))
        self.assertEqual(particle.velocity, (-2.0,))

    def test_periodic_particles_collide_across_an_arena_edge(self) -> None:
        first = Particle(position=(1.5,), velocity=(-1.0,), radius=1.0)
        second = Particle(position=(8.5,), velocity=(1.0,), radius=1.0)
        simulation = Simulation(
            [first, second], (10.0,), boundary_mode="periodic"
        )

        events = simulation.advance_to(1.0)

        self.assertEqual(len(events), 1)
        self.assertEqual((events[0].kind, events[0].time), ("particle", 0.5))
        self.assertEqual(events[0].image_offset, (-10.0,))
        self.assertEqual(first.position, (1.5,))
        self.assertEqual(second.position, (8.5,))
        self.assertEqual(first.velocity, (1.0,))
        self.assertEqual(second.velocity, (-1.0,))

    def test_periodic_image_collisions_work_in_two_and_three_dimensions(self) -> None:
        for dimension in (2, 3):
            with self.subTest(dimension=dimension):
                first = Particle(
                    position=(2.0,) + (5.0,) * (dimension - 1),
                    velocity=(-1.0,) + (0.0,) * (dimension - 1),
                    radius=1.0,
                )
                second = Particle(
                    position=(9.5,) + (5.0,) * (dimension - 1),
                    velocity=(1.0,) + (0.0,) * (dimension - 1),
                    radius=1.0,
                )
                simulation = Simulation(
                    [first, second],
                    (10.0,) * dimension,
                    boundary_mode="periodic",
                )

                events = simulation.advance_to(0.25)

                self.assertEqual(len(events), 1)
                self.assertEqual(events[0].kind, "particle")
                self.assertEqual(events[0].time, 0.25)
                self.assertEqual(events[0].image_offset, (-10.0,) + (0.0,) * (dimension - 1))
                self.assertEqual(first.velocity, (1.0,) + (0.0,) * (dimension - 1))
                self.assertEqual(second.velocity, (-1.0,) + (0.0,) * (dimension - 1))

    def test_periodic_wraps_independently_on_each_axis(self) -> None:
        particle = Particle(
            position=(9.5, 0.5), velocity=(1.0, -1.0), radius=1.0
        )
        simulation = Simulation(
            [particle], (10.0, 10.0), boundary_mode="periodic"
        )

        events = simulation.advance_to(1.0)

        self.assertEqual(
            [(event.kind, event.time, event.axis) for event in events],
            [("wrap", 0.5, 0), ("wrap", 0.5, 1)],
        )
        self.assertEqual(particle.position, (0.5, 9.5))

    def test_periodic_mode_rejects_a_particle_larger_than_an_arena_axis(self) -> None:
        particle = Particle(position=(1.0,), velocity=(0.0,), radius=1.0)

        with self.assertRaises(ValueError):
            Simulation([particle], (2.0,), boundary_mode="periodic")

    def test_divided_arena_particle_bounces_from_left_side(self) -> None:
        particle = Particle(position=(2.0,), velocity=(1.0,), radius=1.0)
        simulation = Simulation(
            [particle], (10.0,), boundary_mode="divided", divider_half_width=0.25
        )

        events = simulation.advance_to(3.0)

        self.assertEqual([(event.kind, event.time, event.side) for event in events], [
            ("divider", 1.75, "left")
        ])
        self.assertEqual(particle.position, (2.5,))
        self.assertEqual(particle.velocity, (-1.0,))

    def test_divided_arena_particle_bounces_from_right_side(self) -> None:
        particle = Particle(position=(8.0,), velocity=(-1.0,), radius=1.0)
        simulation = Simulation(
            [particle], (10.0,), boundary_mode="divided", divider_half_width=0.25
        )

        events = simulation.advance_to(3.0)

        self.assertEqual([(event.kind, event.time, event.side) for event in events], [
            ("divider", 1.75, "right")
        ])
        self.assertEqual(particle.position, (7.5,))
        self.assertEqual(particle.velocity, (1.0,))

    def test_particles_on_opposite_sides_cannot_collide_through_divider(self) -> None:
        left = Particle(position=(3.0,), velocity=(1.0,), radius=1.0)
        right = Particle(position=(7.0,), velocity=(-1.0,), radius=1.0)
        simulation = Simulation([left, right], (10.0,), boundary_mode="divided")

        events = simulation.advance_to(1.0)

        self.assertEqual([event.kind for event in events], ["divider", "divider"])
        self.assertEqual(left.position, (4.0,))
        self.assertEqual(right.position, (6.0,))
        self.assertEqual(left.velocity, (-1.0,))
        self.assertEqual(right.velocity, (1.0,))

    def test_divider_prediction_uses_java_divider_half_width(self) -> None:
        particle = Particle(position=(2.0,), velocity=(1.0,), radius=1.0)
        simulation = Simulation(
            [particle], (10.0,), boundary_mode="divided", divider_half_width=1.0e-9
        )

        event = simulation.next_event()

        self.assertIsNotNone(event)
        self.assertEqual(event.kind, "divider")
        self.assertAlmostEqual(event.time, 2.0 - 1.0e-9, places=12)
        self.assertEqual(event.side, "left")

    def test_divided_arena_preserves_tangent_motion_in_2d_and_3d(self) -> None:
        for dimension in (2, 3):
            with self.subTest(dimension=dimension):
                particle = Particle(
                    position=(2.0,) + (4.0,) * (dimension - 1),
                    velocity=(1.0,) + (2.0,) * (dimension - 1),
                    radius=1.0,
                )
                simulation = Simulation(
                    [particle],
                    (10.0,) * dimension,
                    boundary_mode="divided",
                    divider_half_width=0.25,
                )

                events = simulation.advance_to(2.0)

                self.assertEqual([(event.kind, event.time, event.side) for event in events], [
                    ("divider", 1.75, "left")
                ])
                self.assertEqual(particle.position, (3.5,) + (8.0,) * (dimension - 1))
                self.assertEqual(particle.velocity, (-1.0,) + (2.0,) * (dimension - 1))

    def test_divided_arena_rejects_particle_overlapping_divider(self) -> None:
        particle = Particle(position=(5.0,), velocity=(0.0,), radius=1.0)

        with self.assertRaises(ValueError):
            Simulation([particle], (10.0,), boundary_mode="divided")

    def test_divided_arena_requires_room_on_both_sides(self) -> None:
        particle = Particle(position=(2.0,), velocity=(0.0,), radius=1.0)

        with self.assertRaises(ValueError):
            Simulation(
                [particle], (10.0,), boundary_mode="divided", divider_half_width=4.0
            )

    def test_particle_passes_through_center_of_divider_hole_in_2d_and_3d(self) -> None:
        for dimension in (2, 3):
            with self.subTest(dimension=dimension):
                particle = Particle(
                    position=(2.0,) + (5.0,) * (dimension - 1),
                    velocity=(1.0,) + (0.0,) * (dimension - 1),
                    radius=0.5,
                )
                simulation = Simulation(
                    [particle],
                    (10.0,) * dimension,
                    boundary_mode="divided",
                    divider_half_width=0.1,
                    hole_diameter=4.0,
                )

                events = simulation.advance_to(4.0)

                self.assertEqual(events, ())
                self.assertEqual(particle.position, (6.0,) + (5.0,) * (dimension - 1))
                self.assertEqual(particle.velocity, (1.0,) + (0.0,) * (dimension - 1))

    def test_particle_hits_solid_divider_outside_hole(self) -> None:
        particle = Particle(position=(2.0, 8.0), velocity=(1.0, 0.0), radius=0.5)
        simulation = Simulation(
            [particle],
            (10.0, 10.0),
            boundary_mode="divided",
            divider_half_width=0.1,
            hole_diameter=4.0,
        )

        events = simulation.advance_to(3.0)

        self.assertEqual(len(events), 1)
        self.assertEqual((events[0].kind, events[0].side), ("divider", "left"))
        self.assertAlmostEqual(events[0].time, 2.4)
        self.assertAlmostEqual(particle.position[0], 3.8)
        self.assertEqual(particle.velocity, (-1.0, 0.0))

    def test_particle_reflects_from_hole_edge(self) -> None:
        particle = Particle(position=(2.0, 6.9), velocity=(1.0, 0.0), radius=0.5)
        simulation = Simulation(
            [particle],
            (10.0, 10.0),
            boundary_mode="divided",
            divider_half_width=0.1,
            hole_diameter=4.0,
        )

        events = simulation.advance_to(3.0)

        self.assertEqual(len(events), 1)
        self.assertEqual(events[0].kind, "edge")
        self.assertAlmostEqual(events[0].time, 3.0 - math.sqrt(0.32), places=12)
        self.assertAlmostEqual(
            sum(component * component for component in particle.velocity), 1.0
        )
        self.assertLess(particle.velocity[0], 0.0)
        self.assertNotEqual(particle.velocity[1], 0.0)

    def test_closing_and_reopening_hole_recomputes_next_event(self) -> None:
        particle = Particle(position=(2.0, 5.0), velocity=(1.0, 0.0), radius=0.5)
        simulation = Simulation(
            [particle],
            (10.0, 10.0),
            boundary_mode="divided",
            divider_half_width=0.1,
            hole_diameter=4.0,
        )

        open_event = simulation.next_event()
        self.assertIsNotNone(open_event)
        self.assertEqual(open_event.kind, "wall")
        self.assertAlmostEqual(open_event.time, 7.5)

        simulation.set_hole_open(False)
        closed_event = simulation.next_event()
        self.assertIsNotNone(closed_event)
        self.assertEqual(closed_event.kind, "divider")
        self.assertAlmostEqual(closed_event.time, 2.4)

        simulation.set_hole_open(True)
        reopened_event = simulation.next_event()
        self.assertIsNotNone(reopened_event)
        self.assertEqual(reopened_event.kind, "wall")
        self.assertAlmostEqual(reopened_event.time, 7.5)

    def test_cannot_close_hole_while_particle_overlaps_divider(self) -> None:
        particle = Particle(position=(5.0, 5.0), velocity=(0.0, 0.0), radius=0.5)
        simulation = Simulation(
            [particle],
            (10.0, 10.0),
            boundary_mode="divided",
            divider_half_width=0.1,
            hole_diameter=4.0,
        )

        with self.assertRaises(ValueError):
            simulation.set_hole_open(False)
        self.assertTrue(simulation.hole_open)

    def test_hole_state_requires_a_hole_and_a_boolean(self) -> None:
        particle_without_hole = Particle(
            position=(2.0,), velocity=(0.0,), radius=0.5
        )
        solid = Simulation([particle_without_hole], (10.0,), boundary_mode="divided")
        with self.assertRaises(ValueError):
            solid.set_hole_open(False)

        hole_particle = Particle(position=(2.0, 5.0), velocity=(0.0, 0.0), radius=0.5)
        with self.assertRaises(ValueError):
            Simulation(
                [hole_particle],
                (10.0, 10.0),
                boundary_mode="divided",
                hole_diameter=4.0,
                hole_open=1,
            )

    def test_energy_well_entry_changes_1d_velocities_at_well_radius(self) -> None:
        first = Particle(
            position=(2.0,), velocity=(1.0,), mass=1.0, radius=1.0,
            energy_well_depth=1.0,
        )
        second = Particle(
            position=(8.0,), velocity=(-1.0,), mass=1.0, radius=1.0,
            energy_well_depth=1.0,
        )
        simulation = Simulation(
            [first, second], (30.0,), interaction_radius_multiplier=2.0
        )

        events = simulation.advance_to(1.0)

        self.assertEqual([(event.kind, event.time) for event in events], [
            ("well_entry", 1.0)
        ])
        self.assertAlmostEqual(first.position[0], 3.0)
        self.assertAlmostEqual(second.position[0], 7.0)
        self.assertAlmostEqual(first.velocity[0], math.sqrt(2.0))
        self.assertAlmostEqual(second.velocity[0], -math.sqrt(2.0))
        self.assertAlmostEqual(first.mass * first.velocity[0] + second.mass * second.velocity[0], 0.0)

    def test_energy_well_exit_slows_particles_when_they_have_enough_energy(self) -> None:
        first = Particle(
            position=(2.0,), velocity=(-1.0,), mass=1.0, radius=1.0,
            energy_well_depth=0.5,
        )
        second = Particle(
            position=(5.5,), velocity=(1.0,), mass=1.0, radius=1.0,
            energy_well_depth=0.5,
        )
        simulation = Simulation(
            [first, second], (30.0,), interaction_radius_multiplier=2.0
        )

        events = simulation.advance_to(0.25)

        self.assertEqual([(event.kind, event.time) for event in events], [
            ("well_exit", 0.25)
        ])
        self.assertAlmostEqual(first.velocity[0], -math.sqrt(2.0) / 2.0)
        self.assertAlmostEqual(second.velocity[0], math.sqrt(2.0) / 2.0)
        self.assertNotIn((0, 1), simulation._inside_interaction_wells)

    def test_energy_well_exit_reflects_particles_without_enough_energy(self) -> None:
        first = Particle(
            position=(2.0,), velocity=(-1.0,), mass=1.0, radius=1.0,
            energy_well_depth=4.0,
        )
        second = Particle(
            position=(5.5,), velocity=(1.0,), mass=1.0, radius=1.0,
            energy_well_depth=4.0,
        )
        simulation = Simulation(
            [first, second], (30.0,), interaction_radius_multiplier=2.0
        )

        events = simulation.advance_to(0.25)

        self.assertEqual([(event.kind, event.time) for event in events], [
            ("well_exit", 0.25)
        ])
        self.assertEqual(first.velocity, (1.0,))
        self.assertEqual(second.velocity, (-1.0,))
        self.assertIn((0, 1), simulation._inside_interaction_wells)
        next_event = simulation.next_event()
        self.assertIsNotNone(next_event)
        self.assertEqual(next_event.kind, "particle")
        self.assertAlmostEqual(next_event.time, 1.25)

    def test_energy_well_settings_support_1d_2d_and_3d_but_not_periodic(self) -> None:
        with self.assertRaises(ValueError):
            Particle(position=(0.0,), velocity=(0.0,), energy_well_depth=-1.0)

        particle_2d = Particle(position=(5.0, 5.0), velocity=(0.0, 0.0))
        Simulation(
            [particle_2d],
            (10.0, 10.0),
            interaction_radius_multiplier=2.0,
        )

        particle_3d = Particle(position=(5.0, 5.0, 5.0), velocity=(0.0, 0.0, 0.0))
        Simulation(
            [particle_3d],
            (10.0, 10.0, 10.0),
            interaction_radius_multiplier=2.0,
        )

        particle_1d = Particle(position=(5.0,), velocity=(0.0,))
        with self.assertRaises(ValueError):
            Simulation(
                [particle_1d],
                (10.0,),
                boundary_mode="periodic",
                interaction_radius_multiplier=2.0,
            )

    def test_divider_hole_rejects_1d_and_invalid_sizes(self) -> None:
        particle_1d = Particle(position=(2.0,), velocity=(1.0,), radius=0.5)
        with self.assertRaises(ValueError):
            Simulation(
                [particle_1d], (10.0,), boundary_mode="divided", hole_diameter=2.0
            )

        particle_2d = Particle(position=(2.0, 5.0), velocity=(0.0, 0.0), radius=0.5)
        with self.assertRaises(ValueError):
            Simulation(
                [particle_2d],
                (10.0, 10.0),
                boundary_mode="divided",
                hole_diameter=10.0,
            )

    def test_simulation_rejects_unknown_boundary_mode(self) -> None:
        with self.assertRaises(ValueError):
            Simulation(
                [Particle(position=(5.0,), velocity=(0.0,), radius=1.0)],
                (10.0,),
                boundary_mode="open",
            )


if __name__ == "__main__":
    unittest.main()
