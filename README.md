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

On both targets, manually check:

- Startup and particle rendering in 1D, 2D and 3D.
- Rotation with arrow keys, window resizing and high-DPI displays.
- Pause/resume, single stepping, speed changes and statistics.
- Reflecting/periodic walls, dividers, holes, piston and trace mode.
- Settings loading/saving and restarting the application.

## Application packaging

After `ant dist`, use the selected JDK's jpackage to make an application image
on the target operating system. For example, on macOS:

```sh
jpackage --type app-image --name Boltzmann3D \
  --input dist --main-jar Boltzmann3D.jar \
  --dest packages \
  --java-options="--enable-native-access=ALL-UNNAMED" \
  --java-options="--add-exports=java.desktop/sun.awt=ALL-UNNAMED" \
  --java-options="--add-exports=java.desktop/sun.java2d=ALL-UNNAMED"
```

This bundles Java with the app. A macOS image must be built with an ARM64 JDK;
a Windows image must be built on Windows with an x64 JDK. Installer creation,
macOS signing/notarization and interactive platform validation are follow-up
work; this initial build produces runnable JAR distributions.

Original contact: Derek Manwaring, manwaringsandals@gmail.com.
