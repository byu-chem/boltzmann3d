# Boltzmann 3D

A kinetic theory demonstrator. Copyright (C) 2013 Dr. Randall B. Shirts.
Licensed under GPL-3.0; see LICENSE.

## Java 25 / 64-bit desktop port

This branch migrates the Swing application's graphics from LWJGL 2 to JOGL
2.6.0 and GlueGen 2.6.0. The initial targets are:

- Apple Silicon macOS, using an ARM64 JDK and JogAmp's universal macOS natives.
- Windows 10/11 x64, using an x64 JDK and the Windows amd64 natives.

The renderer requests an OpenGL GL2 compatibility profile to preserve its
fixed-function drawing code. A core-only OpenGL context is insufficient.
This is an initial migration: compilation and headless checks do not establish
that rendering works on every target GPU. Interactive validation is required.

The Swing forms and bundled swing-layout-1.0.4.jar are retained for this pass.
The obsolete applet modules and two legacy pressure implementations retain
their original build exclusions. Simulation equations have not been changed.
Historical LWJGL license/logo resources remain with the existing About UI;
LWJGL, JInput and OpenAL are no longer runtime dependencies.

## Requirements

Install a JDK 25 and Apache Ant 1.10.x. On Apple Silicon with Homebrew:

```sh
brew install --cask temurin@25
brew install ant
export JAVA_HOME=$(/usr/libexec/java_home -v 25 -a arm64)
export PATH="$JAVA_HOME/bin:$PATH"
```

On Windows, install the x64 Temurin 25 JDK and Apache Ant, set JAVA_HOME to the
JDK directory, and put their bin directories on PATH.

## Build and run

```sh
ant clean test dist
ant run
```

The first build downloads the pinned Java libraries from Maven Central into
build/deps. Running or distributing the application also downloads native JARs
for the host target. JOGL extracts the correct native binaries automatically;
no java.library.path setting or system-wide JOGL installation is needed.
`ant clean` preserves the dependency cache for offline rebuilds.

The resulting dist directory contains Boltzmann3D.jar, its lib directory and
LICENSE. Keep them together. With Java 25 selected, run:

```sh
java -jar dist/Boltzmann3D.jar
```

Both launch routes grant native access and the AWT exports used by JOGL.

## Checks

`ant test` runs without an OpenGL context. It checks loading default settings
from a resource URL and filesystem paths with spaces, accepts both .set and
.set2 extensions, repeats under US/German locales, and checks particle counts,
finite positions, advancing time and kinetic-energy conservation over 20 frames.
These are loading tests, not save/load round-trip tests.

GitHub Actions builds and runs these checks with Java 25 on macOS ARM64 and
Windows x64, then uploads each dist directory. Hosted builds do not validate
Windows 10 or interactive graphics.

With a graphical desktop and working OpenGL driver, run `ant test-render` for
the graphics regression check. It briefly opens a test window, reads particle
pixels from the framebuffer in 1D/2D/3D, counts 3D wall pixels independently,
and checks 3D rotation, resizing and
periodic/divided/hole/piston boundaries, then switching back to 2D. The 3D
renderer converts physical lengths to nanometers before sending them to OpenGL;
the projection matrix contains only the camera frustum. Run this from a normal desktop terminal; the headless
`ant test` checks do not open a window.

On both targets, manually check:

- Startup and particle rendering in 1D, 2D and 3D.
- Rotation with arrow keys, window resizing and high-DPI displays.
- Pause/resume, single stepping, speed changes and statistics.
- Reflecting/periodic walls, dividers, holes, piston and trace mode.
- Settings loading/saving and restarting the application.

## Application packaging

Packages include their own Java 25 runtime, JOGL libraries and matching natives.
Users do not need Java, Ant or Homebrew installed. The package version
is 3.1.1; override it with `-Dapp.version=3.1.2` for a subsequent package.

On Apple Silicon, select the ARM64 JDK explicitly (Homebrew's Ant may otherwise
choose its own newer JDK), then build:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 25 -a arm64)
export PATH="$JAVA_HOME/bin:$PATH"
ant package
```

Outputs:

- `packages/images/Boltzmann3D.app` — the standalone Mac app.
- `packages/installers/Boltzmann3D-3.1.1.dmg` — open it and drag the app to Applications.

`ant package` needs normal desktop access for macOS disk-image tooling.
`ant package-image` builds just the app; `ant test-packaged` checks the shipped
runtime, resources, native library loading, settings and physics.
`ant package-verified` additionally checks actual particles and arena walls
using the bundled runtime before building the installer. Run this from a
normal graphical desktop terminal.

On Windows x64, set JAVA_HOME to a JDK 25 installation and put Ant and WiX 5
on PATH. One way to install WiX with the .NET SDK is:

```powershell
dotnet tool install --global wix --version 5.0.2
wix extension add --global WixToolset.UI.wixext/5.0.2
wix extension add --global WixToolset.Util.wixext/5.0.2
ant package
```

Outputs:

- `packages/images/Boltzmann3D/` — a portable app directory with Boltzmann3D.exe.
- `packages/installers/Boltzmann3D-3.1.1.exe` — a per-user installer with a Start
  menu entry, desktop shortcut and uninstall support.

Packages must be built on their target operating system. GitHub Actions builds
both installers and uploads them as `Boltzmann3D-macos-arm64-installer` and
`Boltzmann3D-windows-x64-installer` artifacts. It verifies each bundled runtime
and checks packaged Mac graphics. The Windows installer is also installed into
a temporary directory, checked with its installed runtime, then uninstalled.
Installer archives preserve the permissions
needed by the Mac app; use the DMG artifact instead of downloading an app folder
as a generic ZIP. `ant clean-packages` removes generated package output.

These initial builds do not have publisher certificates or Apple notarization.
The Mac application uses the local signing performed by jpackage, rather than
a Developer ID signature. Signing/notarization for public distribution is a
separate release step.

Original contact: Derek Manwaring, manwaringsandals@gmail.com.

### Particle controls (3.1 development)

Use **Particles & temperature…** above the statistics panel to open the particle
settings directly. Each row edits a particle type's name, color, count, radius in
nanometers, mass in atomic mass units and initial temperature in kelvin. The top
temperature control sets all groups at once; individual row values can then be
adjusted. **Add particle type** adds a row; the minus button removes it.
**Apply** restarts the simulation with the new initial conditions. Requests that
exceed the arena capacity show an error so that counts are not silently reduced.
The Arena tab controls dimensions, arena size, boundary conditions, and divider
heat transfer. An insulating divider reflects particles without transferring
energy through its surface. A conducting divider exchanges normal kinetic energy
between particles approaching it from opposite sides. An open hole still lets
particles cross between compartments.

`ant test` checks typed particle edits, decimal masses, temperature, capacity
validation and settings/physics round trips through 2D and 3D in US and German
locales. `ant test-render` additionally checks repeated dimension transitions and
rejects a particle that fills the 2D framebuffer.

### Original preset simulations

File → Preset simulations provides the 27 original `.set` simulations, grouped into 2D and 3D examples. They are based on files recovered from the original Boltzmann.app distribution (V2.43–V2.48 settings). The current startup default remains separate; “Original default” opens the older saved example.

The 2D “Hot and cold” preset initializes red particles at 500 K and blue
particles at 300 K. The preset stores those temperatures alongside each
particle group; they are also used when restarting from the particle controls.

Presets are embedded in the application JAR and also shipped as ordinary files in `presets/` beside the JAR (on macOS, inside `Boltzmann3D.app/Contents/app/`). Use File → Load to open an edited copy.

### Preset loading fix (3.1.1)

Simulation replacement wakes the display worker and lets its current OpenGL call finish, rather than interrupting macOS graphics dispatch. Shutdown flags are visible across threads, paused physics waits are released, and queued statistics updates are limited to their original simulation. The lifecycle regression switches all presets while running and paused, including shutdown during an AWT render; macOS graphics checks also exercise live preset replacement.
