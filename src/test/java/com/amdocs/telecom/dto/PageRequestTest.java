package com.amdocs.telecom.dto;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PageRequestTest {

    @ParameterizedTest
    @CsvSource({"0,0", "-1,0", "101,0", "20,-1"})
    void rejectsInvalidPageBounds(int limit, int offset) {
        assertThatIllegalArgumentException().isThrownBy(() -> new PageRequest(limit, offset));
    }

    @ParameterizedTest
    @CsvSource({"1,0", "100,0", "20,200"})
    void preservesValidBounds(int limit, int offset) {
        PageRequest page = new PageRequest(limit, offset);
        assertThat(page.getLimit()).isEqualTo(limit);
        assertThat(page.getOffset()).isEqualTo(offset);
    }
}
