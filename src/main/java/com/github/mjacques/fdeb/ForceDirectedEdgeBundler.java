package com.github.mjacques.fdeb;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Force-Directed Edge Bundling (FDEB) implementation.
 * <p>
 * Reference: Holten & van Wijk, "Force-Directed Edge Bundling for Graph Visualization",
 * Computer Graphics Forum, Vol. 28(3), 2009.
 * <p>
 * Algorithm overview:
 * <ol>
 *   <li>Precompute pairwise edge compatibility (O(N²), done once, parallelized)</li>
 *   <li>For each cycle:
 *     <ul>
 *       <li>Run I iterations of force simulation on interior subdivision points</li>
 *       <li>Each iteration: compute spring forces (pull toward neighbors on same edge)
 *           and electrostatic forces (pull toward corresponding point on compatible edges),
 *           then displace points by stepSize * totalForce</li>
 *       <li>After the cycle: halve step size, reduce iteration count (×2/3),
 *           double the number of subdivision points by resampling along the polyline</li>
 *     </ul>
 *   </li>
 * </ol>
 * Endpoints (source/target) are always fixed — only interior points move.
 * <p>
 * X and Y coordinates are stored in separate flat arrays (SoA layout) for better
 * cache locality during the inner force loop which iterates over coordinate components.
 */
public final class ForceDirectedEdgeBundler implements EdgeBundler {

    private final FdebConfig config;
    private final GeometryFactory geometryFactory;

    public ForceDirectedEdgeBundler(FdebConfig config) {
        this.config = config;
        this.geometryFactory = new GeometryFactory();
    }

    public ForceDirectedEdgeBundler() {
        this(FdebConfig.defaults());
    }

    @Override
    public List<LineString> bundle(List<LineString> edges) {
        if (edges == null || edges.size() < 2) {
            return edges;
        }

        int n = edges.size();
        Vec2[] sources = new Vec2[n];
        Vec2[] targets = new Vec2[n];
        double[] originalLengths = new double[n];

        // Extract source/target endpoints from JTS LineStrings and validate
        for (int i = 0; i < n; i++) {
            LineString ls = edges.get(i);
            if (ls == null || ls.isEmpty()) {
                throw new IllegalArgumentException("Edge at index " + i + " is null or empty");
            }
            if (ls.getNumPoints() < 2) {
                throw new IllegalArgumentException("Edge at index " + i + " must have at least 2 points");
            }
            Coordinate src = ls.getCoordinateN(0);
            Coordinate tgt = ls.getCoordinateN(ls.getNumPoints() - 1);
            sources[i] = new Vec2(src.x, src.y);
            targets[i] = new Vec2(tgt.x, tgt.y);
            originalLengths[i] = sources[i].distanceTo(targets[i]);
        }

        // Step 1: compute pairwise compatibility (O(N²), parallelized, done once)
        // Each CompatibleEdge carries the target index + precomputed flip flag
        List<List<CompatibleEdge>> compatibilityLists =
                CompatibilityComputer.computeCompatibilityLists(sources, targets, config);

        // Mirror upper-triangle into symmetric adjacency: if i↔j compatible, both lists contain the other
        CompatibleEdge[][] symmetricCompat = buildSymmetricCompatibility(compatibilityLists, n);

        // Step 2: initialize subdivision points — each edge starts as [source, ..., target]
        // subdivX[i][k] = x-coordinate of the k-th point on edge i
        double[][] subdivX = new double[n][];
        double[][] subdivY = new double[n][];

        int subdivPoints = config.initialSubdivisionPoints();
        for (int i = 0; i < n; i++) {
            subdivX[i] = new double[subdivPoints + 2]; // +2 for source and target
            subdivY[i] = new double[subdivPoints + 2];
            initializeSubdivisionPoints(sources[i], targets[i], subdivX[i], subdivY[i]);
        }

        // Step 3: main cycle loop
        double stepSize = config.stepSize();
        int iterations = config.initialIterations();

        for (int cycle = 0; cycle < config.cycles(); cycle++) {
            // Run force iterations at current resolution
            for (int iter = 0; iter < iterations; iter++) {
                applyForces(subdivX, subdivY, originalLengths, symmetricCompat, stepSize);
            }

            // Decay parameters for next cycle
            stepSize *= config.stepSizeReduction();
            iterations = Math.max(1, (int) (iterations * config.iterationReduction()));

            // Double the number of subdivision points by resampling along the current polyline
            int newSubdivPoints = subdivPoints * config.subdivisionPointRate();
            double[][][] resubdivided = resubdivide2D(subdivX, subdivY, newSubdivPoints + 2);
            subdivX = resubdivided[0];
            subdivY = resubdivided[1];
            subdivPoints = newSubdivPoints;
        }

        return buildResult(subdivX, subdivY, n);
    }

    /** Place points uniformly along the straight line from source to target. */
    private void initializeSubdivisionPoints(Vec2 src, Vec2 tgt, double[] xArr, double[] yArr) {
        int total = xArr.length;
        for (int k = 0; k < total; k++) {
            double t = (double) k / (total - 1);
            xArr[k] = src.x() + t * (tgt.x() - src.x());
            yArr[k] = src.y() + t * (tgt.y() - src.y());
        }
    }

    /**
     * One iteration of force simulation: compute forces then displace interior points.
     * <p>
     * For each interior point p_i of edge P:
     * <ul>
     *   <li>Spring force Fs = kp · ((p_{i-1} - p_i) + (p_{i+1} - p_i))
     *       — pulls toward neighbors on the same edge (Hooke's law)</li>
     *   <li>Electrostatic force Fe = Σ_Q (q_i - p_i) / ||q_i - p_i||
     *       — unit attraction toward corresponding point on each compatible edge Q</li>
     *   <li>Displacement: p_i += stepSize · (Fs + Fe)</li>
     * </ul>
     * Forces are accumulated in a separate buffer, then applied in a second pass
     * so that all edges see the same positions during force computation (Jacobi-style).
     */
    private void applyForces(double[][] subdivX, double[][] subdivY,
                             double[] originalLengths,
                             CompatibleEdge[][] compatLists, double stepSize) {
        int n = subdivX.length;
        int numPoints = subdivX[0].length;
        double eps = config.epsilon();

        // Force buffers (separate from positions for Jacobi-style update)
        double[][] forceX = new double[n][numPoints];
        double[][] forceY = new double[n][numPoints];

        // Compute forces in parallel across edges
        IntStream.range(0, n).parallel().forEach(i -> {
            // kp = K / (edgeLength × numSegments) — shorter edges have stiffer springs
            int numSegments = numPoints - 1;
            double kp = config.springConstant() / (originalLengths[i] * numSegments + eps);

            // Skip endpoints (p=0 and p=numPoints-1): they are fixed
            for (int p = 1; p < numPoints - 1; p++) {
                // Spring force: Hooke's law toward neighboring points on the same edge
                double fsx = kp * ((subdivX[i][p - 1] - subdivX[i][p]) + (subdivX[i][p + 1] - subdivX[i][p]));
                double fsy = kp * ((subdivY[i][p - 1] - subdivY[i][p]) + (subdivY[i][p + 1] - subdivY[i][p]));

                // Electrostatic force: unit attraction toward each compatible edge's corresponding point
                double fex = 0, fey = 0;
                CompatibleEdge[] compatEdges = compatLists[i];
                for (CompatibleEdge ce : compatEdges) {
                    double[] jx = subdivX[ce.index()];
                    double[] jy = subdivY[ce.index()];

                    // Map point index using precomputed flip flag (no dot product recomputation)
                    int jp = correspondingPoint(p, numPoints, jx.length, ce.flip());

                    double dx = jx[jp] - subdivX[i][p];
                    double dy = jy[jp] - subdivY[i][p];
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    if (dist > eps) {
                        // Unit direction vector: attracts without distance-dependent magnitude
                        fex += dx / dist;
                        fey += dy / dist;
                    }
                }

                forceX[i][p] = fsx + fex;
                forceY[i][p] = fsy + fey;
            }
        });

        // Apply accumulated forces (second pass, Jacobi-style)
        IntStream.range(0, n).parallel().forEach(i -> {
            for (int p = 1; p < numPoints - 1; p++) {
                subdivX[i][p] += stepSize * forceX[i][p];
                subdivY[i][p] += stepSize * forceY[i][p];
            }
        });
    }

    /**
     * Maps subdivision point index p on edge I to the corresponding index on edge J.
     * For antiparallel edges (flip=true), the index is mirrored so that
     * "near source of I" maps to "near target of J" and vice versa.
     * The flip flag is precomputed in {@link CompatibleEdge} to avoid redundant dot products.
     */
    private int correspondingPoint(int p, int lenI, int lenJ, boolean flip) {
        if (lenI == lenJ) {
            return flip ? (lenJ - 1 - p) : p;
        }

        // Different subdivision counts: interpolate by normalized position along the edge
        double t = (double) p / (lenI - 1);
        if (flip) t = 1.0 - t;
        return (int) Math.round(t * (lenJ - 1));
    }

    /**
     * Resamples each polyline to {@code newLen} points, placing them at equal arc-length
     * intervals along the current (curved) polyline. This preserves the deformed shape
     * while doubling resolution for the next cycle.
     *
     * @return [0] = new X arrays, [1] = new Y arrays
     */
    private double[][][] resubdivide2D(double[][] oldX, double[][] oldY, int newLen) {
        int n = oldX.length;
        double[][] resultX = new double[n][newLen];
        double[][] resultY = new double[n][newLen];

        IntStream.range(0, n).parallel().forEach(i -> {
            int oldLen = oldX[i].length;

            // Compute 2D segment lengths along the current polyline
            double[] segLengths = new double[oldLen - 1];
            double totalLen = 0;
            for (int k = 0; k < oldLen - 1; k++) {
                double dx = oldX[i][k + 1] - oldX[i][k];
                double dy = oldY[i][k + 1] - oldY[i][k];
                segLengths[k] = Math.sqrt(dx * dx + dy * dy);
                totalLen += segLengths[k];
            }

            // Preserve endpoints exactly
            resultX[i][0] = oldX[i][0];
            resultY[i][0] = oldY[i][0];
            resultX[i][newLen - 1] = oldX[i][oldLen - 1];
            resultY[i][newLen - 1] = oldY[i][oldLen - 1];

            if (totalLen < 1e-15) {
                // Degenerate edge (zero length): all points collapse to source
                Arrays.fill(resultX[i], oldX[i][0]);
                Arrays.fill(resultY[i], oldY[i][0]);
                return;
            }

            // Walk along the polyline, placing new points at equal arc-length intervals
            double step = totalLen / (newLen - 1);
            for (int k = 1; k < newLen - 1; k++) {
                double remaining = step * k;
                int seg = 0;
                for (seg = 0; seg < oldLen - 1; seg++) {
                    if (remaining <= segLengths[seg] + 1e-15) {
                        break;
                    }
                    remaining -= segLengths[seg];
                }
                if (seg >= oldLen - 1) {
                    resultX[i][k] = oldX[i][oldLen - 1];
                    resultY[i][k] = oldY[i][oldLen - 1];
                } else {
                    // Linear interpolation within the segment
                    double t = (segLengths[seg] > 1e-15) ? remaining / segLengths[seg] : 0;
                    resultX[i][k] = oldX[i][seg] + t * (oldX[i][seg + 1] - oldX[i][seg]);
                    resultY[i][k] = oldY[i][seg] + t * (oldY[i][seg + 1] - oldY[i][seg]);
                }
            }
        });

        return new double[][][] { resultX, resultY };
    }

    /**
     * Converts the upper-triangle compatibility lists into a full symmetric adjacency structure.
     * If edge i is compatible with edge j (j > i), both i→j and j→i are stored.
     * The flip flag is preserved symmetrically (if i→j flips, j→i flips too).
     */
    private CompatibleEdge[][] buildSymmetricCompatibility(List<List<CompatibleEdge>> upperTriangle, int n) {
        List<List<CompatibleEdge>> lists = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            lists.add(new ArrayList<>());
        }
        for (int i = 0; i < n; i++) {
            for (CompatibleEdge ce : upperTriangle.get(i)) {
                lists.get(i).add(ce);
                // Flip is symmetric: if i→j is antiparallel, j→i is too
                lists.get(ce.index()).add(new CompatibleEdge(i, ce.flip()));
            }
        }
        CompatibleEdge[][] result = new CompatibleEdge[n][];
        for (int i = 0; i < n; i++) {
            result[i] = lists.get(i).toArray(CompatibleEdge[]::new);
        }
        return result;
    }

    /** Converts internal SoA arrays back to JTS LineStrings. */
    private List<LineString> buildResult(double[][] subdivX, double[][] subdivY, int n) {
        LineString[] result = new LineString[n];
        IntStream.range(0, n).parallel().forEach(i -> {
            int len = subdivX[i].length;
            Coordinate[] coords = new Coordinate[len];
            for (int k = 0; k < len; k++) {
                coords[k] = new Coordinate(subdivX[i][k], subdivY[i][k]);
            }
            result[i] = geometryFactory.createLineString(coords);
        });
        return Arrays.asList(result);
    }
}
