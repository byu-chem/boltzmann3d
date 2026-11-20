Boltzmann 3D, a kinetic theory demonstrator
Copyright (C) 2013 Dr. Randall B. Shirts

Contact info:
  www.chem.byu.edu/boltzmann3d
  Derek Manwaring, manwaringsandals@gmail.com

To run Boltzmann 3D, use:
  ant run

To install the Python desktop dependencies, use:
  python -m pip install -r requirements.txt

To run the Python desktop interface from the repository root, use:
  PYTHONPATH=python python -m boltzmann3d.PySide6

The desktop interface uses PySide6 for menus and controls, Panda3D for the
simulation view, and Matplotlib for the particle-speed graph. The standalone
Panda3D demos remain available, for example:
  PYTHONPATH=python python -m boltzmann3d.panda3d.well_demo_2d
