package com.github.mjacques.fdeb;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CompatibilityComputerTest {

    private static final double EPS = 1e-8;

    @Test
    void angleCompatibility_parallelEdges_shouldBeOne() {
        Vec2 v1 = new Vec2(1, 0);
        Vec2 v2 = new Vec2(2, 0);
        double ca = CompatibilityComputer.angleCompatibility(v1, v2, v1.length(), v2.length(), EPS);
        assertThat(ca).isCloseTo(1.0, within(1e-6));
    }

    @Test
    void angleCompatibility_antiparallelEdges_shouldBeOne() {
        Vec2 v1 = new Vec2(1, 0);
        Vec2 v2 = new Vec2(-3, 0);
        double ca = CompatibilityComputer.angleCompatibility(v1, v2, v1.length(), v2.length(), EPS);
        assertThat(ca).isCloseTo(1.0, within(1e-6));
    }

    @Test
    void angleCompatibility_perpendicularEdges_shouldBeZero() {
        Vec2 v1 = new Vec2(1, 0);
        Vec2 v2 = new Vec2(0, 1);
        double ca = CompatibilityComputer.angleCompatibility(v1, v2, v1.length(), v2.length(), EPS);
        assertThat(ca).isCloseTo(0.0, within(1e-6));
    }

    @Test
    void angleCompatibility_45degrees_shouldBeCosine() {
        Vec2 v1 = new Vec2(1, 0);
        Vec2 v2 = new Vec2(1, 1);
        double ca = CompatibilityComputer.angleCompatibility(v1, v2, v1.length(), v2.length(), EPS);
        assertThat(ca).isCloseTo(Math.cos(Math.PI / 4), within(1e-6));
    }

    @Test
    void scaleCompatibility_equalLengths_shouldBeOne() {
        double cs = CompatibilityComputer.scaleCompatibility(5.0, 5.0, EPS);
        assertThat(cs).isCloseTo(1.0, within(1e-4));
    }

    @Test
    void scaleCompatibility_veryDifferentLengths_shouldBeLow() {
        double cs = CompatibilityComputer.scaleCompatibility(1.0, 100.0, EPS);
        assertThat(cs).isLessThan(0.1);
    }

    @Test
    void scaleCompatibility_isSymmetric() {
        double cs1 = CompatibilityComputer.scaleCompatibility(3.0, 7.0, EPS);
        double cs2 = CompatibilityComputer.scaleCompatibility(7.0, 3.0, EPS);
        assertThat(cs1).isCloseTo(cs2, within(1e-10));
    }

    @Test
    void positionCompatibility_samePosition_shouldBeOne() {
        Vec2 mid = new Vec2(5, 5);
        double cp = CompatibilityComputer.positionCompatibility(10.0, 10.0, mid, mid, EPS);
        assertThat(cp).isCloseTo(1.0, within(1e-4));
    }

    @Test
    void positionCompatibility_farApart_shouldBeLow() {
        Vec2 mid1 = new Vec2(0, 0);
        Vec2 mid2 = new Vec2(1000, 1000);
        double cp = CompatibilityComputer.positionCompatibility(1.0, 1.0, mid1, mid2, EPS);
        assertThat(cp).isLessThan(0.01);
    }

    @Test
    void visibilityCompatibility_overlappingEdges_shouldBeHigh() {
        Vec2 src1 = new Vec2(0, 0);
        Vec2 tgt1 = new Vec2(10, 0);
        Vec2 src2 = new Vec2(0, 1);
        Vec2 tgt2 = new Vec2(10, 1);
        Vec2 vec1 = tgt1.sub(src1);
        Vec2 vec2 = tgt2.sub(src2);
        Vec2 mid1 = Vec2.midpoint(src1, tgt1);
        Vec2 mid2 = Vec2.midpoint(src2, tgt2);

        double cv = CompatibilityComputer.visibilityCompatibility(
                src1, tgt1, src2, tgt2, vec1, vec2, mid1, mid2, EPS);
        assertThat(cv).isGreaterThan(0.8);
    }

    @Test
    void visibilityCompatibility_nonOverlapping_shouldBeLow() {
        Vec2 src1 = new Vec2(0, 0);
        Vec2 tgt1 = new Vec2(1, 0);
        Vec2 src2 = new Vec2(100, 0);
        Vec2 tgt2 = new Vec2(101, 0);
        Vec2 vec1 = tgt1.sub(src1);
        Vec2 vec2 = tgt2.sub(src2);
        Vec2 mid1 = Vec2.midpoint(src1, tgt1);
        Vec2 mid2 = Vec2.midpoint(src2, tgt2);

        double cv = CompatibilityComputer.visibilityCompatibility(
                src1, tgt1, src2, tgt2, vec1, vec2, mid1, mid2, EPS);
        assertThat(cv).isCloseTo(0.0, within(0.05));
    }

    @Test
    void computeCompatibilityLists_parallelCloseEdges_shouldBeCompatible() {
        Vec2[] sources = { new Vec2(0, 0), new Vec2(0, 1) };
        Vec2[] targets = { new Vec2(10, 0), new Vec2(10, 1) };
        FdebConfig config = FdebConfig.defaults();

        List<List<CompatibleEdge>> lists = CompatibilityComputer.computeCompatibilityLists(sources, targets, config);

        assertThat(lists).hasSize(2);
        assertThat(lists.get(0)).extracting(CompatibleEdge::index).contains(1);
    }

    @Test
    void computeCompatibilityLists_parallelEdges_flipShouldBeFalse() {
        Vec2[] sources = { new Vec2(0, 0), new Vec2(0, 1) };
        Vec2[] targets = { new Vec2(10, 0), new Vec2(10, 1) };
        FdebConfig config = FdebConfig.defaults();

        List<List<CompatibleEdge>> lists = CompatibilityComputer.computeCompatibilityLists(sources, targets, config);

        assertThat(lists.get(0).get(0).flip()).isFalse();
    }

    @Test
    void computeCompatibilityLists_antiparallelEdges_flipShouldBeTrue() {
        Vec2[] sources = { new Vec2(0, 0), new Vec2(10, 1) };
        Vec2[] targets = { new Vec2(10, 0), new Vec2(0, 1) };
        FdebConfig config = FdebConfig.defaults();

        List<List<CompatibleEdge>> lists = CompatibilityComputer.computeCompatibilityLists(sources, targets, config);

        assertThat(lists.get(0)).isNotEmpty();
        assertThat(lists.get(0).get(0).flip()).isTrue();
    }

    @Test
    void computeCompatibilityLists_perpendicularEdges_shouldNotBeCompatible() {
        Vec2[] sources = { new Vec2(0, 0), new Vec2(5, -5) };
        Vec2[] targets = { new Vec2(10, 0), new Vec2(5, 5) };
        FdebConfig config = FdebConfig.defaults();

        List<List<CompatibleEdge>> lists = CompatibilityComputer.computeCompatibilityLists(sources, targets, config);

        assertThat(lists.get(0)).isEmpty();
    }
}
