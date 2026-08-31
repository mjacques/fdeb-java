package com.github.mjacques.fdeb;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Computes pairwise edge compatibility for FDEB.
 * <p>
 * Implements the four compatibility measures from Holten & van Wijk (2009), §4:
 * <ul>
 *   <li><b>Ca</b> — Angle compatibility: |cos(θ)| between edge direction vectors</li>
 *   <li><b>Cs</b> — Scale compatibility: similarity of edge lengths</li>
 *   <li><b>Cp</b> — Position compatibility: proximity of edge midpoints relative to average length</li>
 *   <li><b>Cv</b> — Visibility compatibility: how much the edges "see" each other via orthogonal projection</li>
 * </ul>
 * Two edges interact only if their combined score Ca * Cs * Cp * Cv ≥ threshold.
 * <p>
 * The computation is O(N²) over edge pairs. Each edge i only stores compatible edges j > i
 * (upper triangle), which is later mirrored into a symmetric adjacency list.
 * Parallelized via {@code IntStream.parallel()}.
 * <p>
 * The flip flag (antiparallel direction) is computed once per pair during compatibility
 * and stored in {@link CompatibleEdge}, avoiding redundant dot-product recomputation
 * during every force iteration.
 */
final class CompatibilityComputer {

    private CompatibilityComputer() {}

    /**
     * Computes, for each edge i, the list of compatible edges j > i with precomputed flip flags.
     * Uses early-exit: if the running product Ca*Cs*... drops below threshold,
     * the more expensive measures (especially Cv) are skipped.
     */
    static List<List<CompatibleEdge>> computeCompatibilityLists(Vec2[] sources, Vec2[] targets, FdebConfig config) {
        int n = sources.length;
        double threshold = config.compatibilityThreshold();
        double eps = config.epsilon();

        // Precompute direction vectors, lengths and midpoints for all edges
        Vec2[] vecs = new Vec2[n];
        double[] lengths = new double[n];
        Vec2[] midpoints = new Vec2[n];

        for (int i = 0; i < n; i++) {
            vecs[i] = targets[i].sub(sources[i]);
            lengths[i] = vecs[i].length();
            midpoints[i] = Vec2.midpoint(sources[i], targets[i]);
        }

        return new ArrayList<>(
                IntStream.range(0, n)
                        .parallel()
                        .mapToObj(i -> computeCompatibleEdges(i, n, vecs, lengths, midpoints, sources, targets, threshold, eps))
                        .toList()
        );
    }

    /**
     * For edge i, tests all edges j > i and returns compatible ones with flip flags.
     * Measures are evaluated cheapest-first with early-exit on the running product.
     */
    private static List<CompatibleEdge> computeCompatibleEdges(
            int i, int n, Vec2[] vecs, double[] lengths, Vec2[] midpoints,
            Vec2[] sources, Vec2[] targets, double threshold, double eps) {

        var compatList = new ArrayList<CompatibleEdge>();
        for (int j = i + 1; j < n; j++) {
            // Ca — angle: cheapest measure, evaluated first
            double ca = angleCompatibility(vecs[i], vecs[j], lengths[i], lengths[j], eps);
            if (ca < threshold) continue;

            // Cs — scale: still cheap (only uses precomputed lengths)
            double cs = scaleCompatibility(lengths[i], lengths[j], eps);
            if (ca * cs < threshold) continue;

            // Cp — position: uses precomputed midpoints
            double cp = positionCompatibility(lengths[i], lengths[j], midpoints[i], midpoints[j], eps);
            if (ca * cs * cp < threshold) continue;

            // Cv — visibility: most expensive (requires projections), evaluated last
            double cv = visibilityCompatibility(sources[i], targets[i], sources[j], targets[j], vecs[i], vecs[j], midpoints[i], midpoints[j], eps);
            if (ca * cs * cp * cv >= threshold) {
                // Precompute flip: edges with opposing directions need mirrored point mapping
                boolean flip = vecs[i].dot(vecs[j]) < 0;
                compatList.add(new CompatibleEdge(j, flip));
            }
        }
        return compatList;
    }

    /**
     * Ca = |cos(θ)| = |dot(P,Q)| / (|P|·|Q|).
     * Returns 1 for parallel/antiparallel edges, 0 for perpendicular.
     */
    static double angleCompatibility(Vec2 vecP, Vec2 vecQ, double lenP, double lenQ, double eps) {
        return Math.abs(vecP.dot(vecQ)) / (lenP * lenQ + eps);
    }

    /**
     * Cs = 2 / (lavg/min(|P|,|Q|) + max(|P|,|Q|)/lavg).
     * Returns 1 for equal-length edges, approaches 0 for very different lengths.
     */
    static double scaleCompatibility(double lenP, double lenQ, double eps) {
        double lAvg = (lenP + lenQ) / 2.0;
        double minLen = Math.min(lenP, lenQ);
        double maxLen = Math.max(lenP, lenQ);
        return 2.0 / (lAvg / (minLen + eps) + maxLen / (lAvg + eps) + eps);
    }

    /**
     * Cp = lavg / (lavg + ||midP - midQ||).
     * Returns 1 for coincident midpoints, decays toward 0 with distance.
     */
    static double positionCompatibility(double lenP, double lenQ, Vec2 midP, Vec2 midQ, double eps) {
        double lAvg = (lenP + lenQ) / 2.0;
        double midDist = midP.distanceTo(midQ);
        return lAvg / (lAvg + midDist + eps);
    }

    /**
     * Cv = min(V(P,Q), V(Q,P)) — symmetric visibility.
     * V(P,Q) projects Q's endpoints onto line P, compares the projection midpoint
     * to P's midpoint: V = max(0, 1 - 2·||Pm - Im|| / ||I0 - I1||).
     * Prevents bundling of edges that would create misleading visual crossings.
     */
    static double visibilityCompatibility(Vec2 srcP, Vec2 tgtP, Vec2 srcQ, Vec2 tgtQ,
                                           Vec2 vecP, Vec2 vecQ, Vec2 midP, Vec2 midQ, double eps) {
        double vpq = visibility(srcP, tgtP, vecP, midP, srcQ, tgtQ, eps);
        double vqp = visibility(srcQ, tgtQ, vecQ, midQ, srcP, tgtP, eps);
        return Math.min(vpq, vqp);
    }

    /**
     * One-directional visibility: V(P, Q).
     * Projects Q's source and target onto the line supporting P,
     * then measures how far the projection midpoint Im is from P's midpoint Pm.
     */
    private static double visibility(Vec2 srcP, Vec2 tgtP, Vec2 vecP, Vec2 midP,
                                     Vec2 srcQ, Vec2 tgtQ, double eps) {
        double vecPLenSq = vecP.lengthSq();

        // I0, I1 = orthogonal projections of Q's endpoints onto line(P)
        Vec2 i0 = projectOntoLine(srcQ, srcP, vecP, vecPLenSq);
        Vec2 i1 = projectOntoLine(tgtQ, srcP, vecP, vecPLenSq);
        Vec2 im = Vec2.midpoint(i0, i1);

        double denom = i0.distanceTo(i1);
        double num = 2.0 * midP.distanceTo(im);

        return Math.max(0.0, 1.0 - num / (denom + eps));
    }

    /** Orthogonal projection of a point onto a line defined by origin + direction. */
    private static Vec2 projectOntoLine(Vec2 point, Vec2 lineOrigin, Vec2 lineDir, double lineDirLenSq) {
        Vec2 ap = point.sub(lineOrigin);
        double t = ap.dot(lineDir) / (lineDirLenSq + 1e-12);
        return lineOrigin.add(lineDir.scale(t));
    }
}
