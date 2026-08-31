package com.github.mjacques.fdeb;

import org.locationtech.jts.geom.LineString;

import java.util.List;

/**
 * Bundles a collection of edges (trajectories) into visually grouped bundles.
 * <p>
 * Input edges are JTS {@link LineString}s (only source and target endpoints are used).
 * Output edges are subdivided {@link LineString}s whose interior points have been
 * displaced to form smooth bundles, while endpoints remain fixed.
 */
public interface EdgeBundler {

    List<LineString> bundle(List<LineString> edges);
}
