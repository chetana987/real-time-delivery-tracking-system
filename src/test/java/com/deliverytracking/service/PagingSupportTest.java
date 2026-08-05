package com.deliverytracking.service;

import com.deliverytracking.dto.PageParams;
import com.deliverytracking.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PagingSupportTest {

    private static final Set<String> FIELDS = Set.of("id", "createdAt");

    @Test
    void pageRequest_appliesDefaultsWhenParamsAreNull() {
        Pageable pageable = PagingSupport.pageRequest(
                new PageParams(0, 10, null, null), FIELDS, "createdAt", "desc");

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort()).containsExactly(Sort.Order.desc("createdAt"));
    }

    @Test
    void pageRequest_honorsExplicitPageSizeAndSort() {
        Pageable pageable = PagingSupport.pageRequest(
                new PageParams(2, 25, "id", "asc"), FIELDS, "createdAt", "desc");

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(25);
        assertThat(pageable.getSort()).containsExactly(Sort.Order.asc("id"));
    }

    @Test
    void pageRequest_acceptsUpperAndLowerCaseDirections() {
        assertThat(PagingSupport.pageRequest(
                new PageParams(0, 10, "createdAt", "ASC"), FIELDS, "createdAt", "desc").getSort())
                .containsExactly(Sort.Order.asc("createdAt"));
        assertThat(PagingSupport.pageRequest(
                new PageParams(0, 10, "createdAt", "Desc"), FIELDS, "createdAt", "desc").getSort())
                .containsExactly(Sort.Order.desc("createdAt"));
    }

    @Test
    void pageRequest_invalidDirection_throwsBadRequest() {
        assertThatThrownBy(() -> PagingSupport.pageRequest(
                new PageParams(0, 10, "id", "sideways"), FIELDS, "createdAt", "desc"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid sort direction");
    }

    @Test
    void pageRequest_sortFieldOutsideWhitelist_throwsBadRequest() {
        assertThatThrownBy(() -> PagingSupport.pageRequest(
                new PageParams(0, 10, "password", "asc"), FIELDS, "createdAt", "desc"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot sort by 'password'");
    }
}
