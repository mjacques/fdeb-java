# fdeb-java

A high-performance Java library implementing the **Force-Directed Edge Bundling (FDEB)** algorithm for grouping trajectories into smooth bundles. Designed for air traffic visualization with up to 12,000 trajectories.

Based on the paper: *Holten & van Wijk, "Force-Directed Edge Bundling for Graph Visualization", Computer Graphics Forum, Vol. 28(3), 2009.*

## Features

- **JTS integration** — input/output as `List<LineString>` for direct GeoJSON serialization
- **Multi-threaded** — parallel compatibility computation (O(N²)) and force simulation via `IntStream.parallel()`
- **Four compatibility measures** — angle (Ca), scale (Cs), position (Cp), visibility (Cv) with early-exit pruning
- **Configurable** — all algorithm parameters exposed via `FdebConfig.Builder`
- **Antiparallel support** — correctly bundles edges running in opposite directions

## Quick Start

### Gradle

```groovy
dependencies {
    implementation 'com.github.mjacques.fdeb:fdeb-java:0.1'
}
```

### Usage

```java
import com.github.mjacques.fdeb.*;
import org.locationtech.jts.geom.*;

// Prepare edges as JTS LineStrings (only source/target endpoints matter)
GeometryFactory gf = new GeometryFactory();
List<LineString> trajectories = List.of(
    gf.createLineString(new Coordinate[]{ new Coordinate(0, 0), new Coordinate(100, 0) }),
    gf.createLineString(new Coordinate[]{ new Coordinate(0, 5), new Coordinate(100, 5) }),
    gf.createLineString(new Coordinate[]{ new Coordinate(0, 10), new Coordinate(100, 10) })
);

// Bundle with default parameters
EdgeBundler bundler = new ForceDirectedEdgeBundler();
List<LineString> bundled = bundler.bundle(trajectories);

// Each bundled LineString has subdivided points forming a smooth curve
// Endpoints are preserved exactly — only interior points are displaced
```

### Custom Configuration

```java
FdebConfig config = FdebConfig.builder()
    .springConstant(0.1)          // K — global spring stiffness (default: 0.1)
    .stepSize(0.04)               // S — displacement step size (default: 0.04)
    .cycles(6)                    // number of refinement cycles (default: 6)
    .initialIterations(60)        // iterations in the first cycle (default: 60)
    .compatibilityThreshold(0.6)  // minimum Ca*Cs*Cp*Cv to interact (default: 0.6)
    .build();

EdgeBundler bundler = new ForceDirectedEdgeBundler(config);
```

## Algorithm Overview

1. **Compatibility filtering** (once, O(N²), parallelized) — For each pair of edges, compute four compatibility measures and keep only pairs whose product exceeds the threshold. Measures are evaluated cheapest-first with early-exit.

2. **Iterative force simulation** (per cycle) — For each interior subdivision point:
   - **Spring force** (Hooke's law) pulls toward neighboring points on the same edge
   - **Electrostatic force** pulls toward corresponding points on compatible edges (unit attraction)
   - Points are displaced by `stepSize × (Fs + Fe)` using Jacobi-style updates

3. **Progressive refinement** — After each cycle:
   - Step size is halved
   - Iteration count is reduced (×2/3)
   - Subdivision points are doubled by equal arc-length resampling

Endpoints remain fixed throughout the process.

## Configuration Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `springConstant` | 0.1 | Spring stiffness K — higher values resist bundling |
| `stepSize` | 0.04 | Initial displacement per iteration |
| `stepSizeReduction` | 0.5 | Step size multiplier after each cycle |
| `cycles` | 6 | Number of refinement cycles |
| `initialIterations` | 60 | Iterations in the first cycle |
| `iterationReduction` | 2/3 | Iteration count multiplier after each cycle |
| `initialSubdivisionPoints` | 1 | Interior points at cycle 0 (1 = midpoint only) |
| `subdivisionPointRate` | 2 | Subdivision point multiplier between cycles |
| `compatibilityThreshold` | 0.6 | Minimum compatibility score for edge interaction |

## Requirements

- Java 17+
- JTS Core 1.20.0+

## Building

```bash
./gradlew build
```

## License

Apache 2.0 — see [LICENSE](LICENSE)
