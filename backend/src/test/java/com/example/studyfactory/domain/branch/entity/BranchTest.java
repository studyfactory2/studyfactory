package com.example.studyfactory.domain.branch.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.studyfactory.domain.branch.dto.BranchCreateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("지점 도메인 테스트")
class BranchTest {

    @Test
    @DisplayName("지점 생성 요청 DTO로 지점 엔티티를 생성한다")
    void createBranchFromRequest() {
        BranchCreateRequest request = new BranchCreateRequest(" 강남점 ", " 서울 강남구 ");

        Branch branch = request.toEntity();

        assertThat(branch.getName()).isEqualTo("강남점");
        assertThat(branch.getAddress()).isEqualTo("서울 강남구");
    }

    @Test
    @DisplayName("주소가 공백이면 null로 저장한다")
    void createBranchWithoutAddress() {
        BranchCreateRequest request = new BranchCreateRequest("강남점", " ");

        Branch branch = request.toEntity();

        assertThat(branch.getAddress()).isNull();
    }
}
