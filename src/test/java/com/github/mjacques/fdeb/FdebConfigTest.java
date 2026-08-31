package com.github.mjacques.fdeb;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FdebConfigTest {

    @Test
    void defaults_shouldBuildWithoutError() {
        FdebConfig config = FdebConfig.defaults();
        assertThat(config.springConstant()).isEqualTo(0.1);
        assertThat(config.cycles()).isEqualTo(6);
        assertThat(config.compatibilityThreshold()).isEqualTo(0.6);
    }

    @Test
    void builder_shouldAcceptValidCustomValues() {
        FdebConfig config = FdebConfig.builder()
                .springConstant(0.5)
                .stepSize(0.1)
                .cycles(3)
                .initialIterations(30)
                .compatibilityThreshold(0.8)
                .build();

        assertThat(config.springConstant()).isEqualTo(0.5);
        assertThat(config.cycles()).isEqualTo(3);
    }

    @Test
    void builder_negativeSpringConstant_shouldThrow() {
        assertThatThrownBy(() -> FdebConfig.builder().springConstant(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("springConstant");
    }

    @Test
    void builder_zeroStepSize_shouldThrow() {
        assertThatThrownBy(() -> FdebConfig.builder().stepSize(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stepSize");
    }

    @Test
    void builder_zeroCycles_shouldThrow() {
        assertThatThrownBy(() -> FdebConfig.builder().cycles(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycles");
    }

    @Test
    void builder_negativeThreshold_shouldThrow() {
        assertThatThrownBy(() -> FdebConfig.builder().compatibilityThreshold(-0.1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("compatibilityThreshold");
    }

    @Test
    void builder_thresholdAboveOne_shouldThrow() {
        assertThatThrownBy(() -> FdebConfig.builder().compatibilityThreshold(1.5).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("compatibilityThreshold");
    }

    @Test
    void builder_stepSizeReductionAboveOne_shouldThrow() {
        assertThatThrownBy(() -> FdebConfig.builder().stepSizeReduction(1.5).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stepSizeReduction");
    }

    @Test
    void builder_zeroIterations_shouldThrow() {
        assertThatThrownBy(() -> FdebConfig.builder().initialIterations(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("initialIterations");
    }

    @Test
    void builder_zeroSubdivisionPoints_shouldThrow() {
        assertThatThrownBy(() -> FdebConfig.builder().initialSubdivisionPoints(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("initialSubdivisionPoints");
    }

    @Test
    void builder_edgeValues_shouldBeAccepted() {
        // Boundary values that should pass validation
        FdebConfig config = FdebConfig.builder()
                .compatibilityThreshold(0.0)
                .stepSizeReduction(1.0)
                .iterationReduction(1.0)
                .build();

        assertThat(config.compatibilityThreshold()).isEqualTo(0.0);
        assertThat(config.stepSizeReduction()).isEqualTo(1.0);
    }
}
