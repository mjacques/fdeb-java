package com.github.mjacques.fdeb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForceDirectedEdgeBundlerTest {

    private GeometryFactory gf;
    private ForceDirectedEdgeBundler bundler;

    @BeforeEach
    void setUp() {
        gf = new GeometryFactory();
        bundler = new ForceDirectedEdgeBundler(FdebConfig.builder()
                .cycles(3)
                .initialIterations(30)
                .build());
    }

    private LineString line(double x1, double y1, double x2, double y2) {
        return gf.createLineString(new Coordinate[]{
                new Coordinate(x1, y1),
                new Coordinate(x2, y2)
        });
    }

    @Test
    void bundle_nullInput_returnsNull() {
        assertThat(bundler.bundle(null)).isNull();
    }

    @Test
    void bundle_singleEdge_returnsUnchanged() {
        List<LineString> input = List.of(line(0, 0, 10, 0));
        List<LineString> result = bundler.bundle(input);
        assertThat(result).hasSize(1);
    }

    @Test
    void bundle_parallelEdges_shouldConverge() {
        List<LineString> input = List.of(
                line(0, 0, 100, 0),
                line(0, 5, 100, 5)
        );

        List<LineString> result = bundler.bundle(input);

        assertThat(result).hasSize(2);

        Coordinate[] coords0 = result.get(0).getCoordinates();
        Coordinate[] coords1 = result.get(1).getCoordinates();

        assertThat(coords0[0].x).isEqualTo(0.0);
        assertThat(coords0[0].y).isEqualTo(0.0);
        assertThat(coords0[coords0.length - 1].x).isEqualTo(100.0);
        assertThat(coords0[coords0.length - 1].y).isEqualTo(0.0);

        assertThat(coords1[0].x).isEqualTo(0.0);
        assertThat(coords1[0].y).isEqualTo(5.0);
        assertThat(coords1[coords1.length - 1].x).isEqualTo(100.0);
        assertThat(coords1[coords1.length - 1].y).isEqualTo(5.0);

        int midIdx0 = coords0.length / 2;
        int midIdx1 = coords1.length / 2;
        double originalGap = 5.0;
        double bundledGap = Math.abs(coords1[midIdx1].y - coords0[midIdx0].y);

        assertThat(bundledGap).isLessThan(originalGap);
    }

    @Test
    void bundle_preservesEndpoints() {
        List<LineString> input = List.of(
                line(10, 20, 50, 80),
                line(10, 22, 50, 82),
                line(10, 24, 50, 84)
        );

        List<LineString> result = bundler.bundle(input);

        for (int i = 0; i < input.size(); i++) {
            Coordinate[] original = input.get(i).getCoordinates();
            Coordinate[] bundled = result.get(i).getCoordinates();

            assertThat(bundled[0].x).isEqualTo(original[0].x);
            assertThat(bundled[0].y).isEqualTo(original[0].y);
            assertThat(bundled[bundled.length - 1].x).isEqualTo(original[original.length - 1].x);
            assertThat(bundled[bundled.length - 1].y).isEqualTo(original[original.length - 1].y);
        }
    }

    @Test
    void bundle_resultLineStringsAreValid() {
        List<LineString> input = List.of(
                line(0, 0, 100, 0),
                line(0, 3, 100, 3),
                line(0, 6, 100, 6)
        );

        List<LineString> result = bundler.bundle(input);

        for (LineString ls : result) {
            assertThat(ls.isValid()).isTrue();
            assertThat(ls.getNumPoints()).isGreaterThanOrEqualTo(2);
            assertThat(ls.isEmpty()).isFalse();
        }
    }

    @Test
    void bundle_subdivisionIncreasesPointCount() {
        List<LineString> input = List.of(
                line(0, 0, 100, 0),
                line(0, 2, 100, 2)
        );

        List<LineString> result = bundler.bundle(input);

        for (LineString ls : result) {
            assertThat(ls.getNumPoints()).isGreaterThan(2);
        }
    }

    @Test
    void bundle_perpendicularEdges_shouldNotBundle() {
        List<LineString> input = List.of(
                line(0, 50, 100, 50),
                line(50, 0, 50, 100)
        );

        FdebConfig strictConfig = FdebConfig.builder()
                .cycles(3)
                .initialIterations(30)
                .compatibilityThreshold(0.6)
                .build();
        ForceDirectedEdgeBundler strictBundler = new ForceDirectedEdgeBundler(strictConfig);

        List<LineString> result = strictBundler.bundle(input);

        assertThat(result).hasSize(2);

        Coordinate[] coords0 = result.get(0).getCoordinates();
        int mid0 = coords0.length / 2;
        assertThat(coords0[mid0].y).isCloseTo(50.0, org.assertj.core.data.Offset.offset(1.0));
    }

    @Test
    void bundle_manyParallelEdges_shouldAllConverge() {
        List<LineString> input = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            input.add(line(0, i * 2, 200, i * 2));
        }

        FdebConfig config = FdebConfig.builder()
                .cycles(4)
                .initialIterations(40)
                .build();
        ForceDirectedEdgeBundler b = new ForceDirectedEdgeBundler(config);

        List<LineString> result = b.bundle(input);

        assertThat(result).hasSize(20);
        for (LineString ls : result) {
            assertThat(ls.isValid()).isTrue();
        }

        Coordinate[] first = result.get(0).getCoordinates();
        Coordinate[] last = result.get(19).getCoordinates();
        int mid = first.length / 2;
        double bundledSpread = Math.abs(last[mid].y - first[mid].y);
        double originalSpread = 38.0;
        assertThat(bundledSpread).isLessThan(originalSpread);
    }

    @Test
    void bundle_nullEdgeInList_shouldThrow() {
        List<LineString> input = new ArrayList<>();
        input.add(line(0, 0, 10, 0));
        input.add(null);

        assertThatThrownBy(() -> bundler.bundle(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("index 1");
    }

    @Test
    void bundle_antiparallelEdges_shouldStillBundle() {
        List<LineString> input = List.of(
                line(0, 0, 100, 0),
                line(100, 3, 0, 3)
        );

        List<LineString> result = bundler.bundle(input);

        assertThat(result).hasSize(2);
        Coordinate[] coords0 = result.get(0).getCoordinates();
        Coordinate[] coords1 = result.get(1).getCoordinates();

        assertThat(coords0[0].x).isEqualTo(0.0);
        assertThat(coords1[0].x).isEqualTo(100.0);

        int mid0 = coords0.length / 2;
        int mid1 = coords1.length / 2;
        double gap = Math.abs(coords1[mid1].y - coords0[mid0].y);
        assertThat(gap).isLessThan(3.0);
    }
}
