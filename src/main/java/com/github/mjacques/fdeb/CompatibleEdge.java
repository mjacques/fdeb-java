package com.github.mjacques.fdeb;

/**
 * A compatible edge reference used in the force computation loop.
 *
 * @param index index of the compatible edge in the global edge array
 * @param flip  true if the compatible edge runs antiparallel (dot product of direction vectors < 0),
 *              meaning subdivision point indices must be mirrored when computing forces
 */
record CompatibleEdge(int index, boolean flip) {
}
