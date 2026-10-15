package com.naengjang_goat.inventory_system.batch.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

class KamisApiClientTest {
    RestTemplate http;
    KamisApiClient client;

    @BeforeEach void setup() {
        http = mock(RestTemplate.class);
        client = new KamisApiClient(http, Clock.fixed(Instant.parse("2026-10-12T03:00:00Z"), ZoneId.of("Asia/Seoul")));
        ReflectionTestUtils.setField(client, "baseUrl", "https://fixture.invalid/kamis");
        ReflectionTestUtils.setField(client, "apiKey", "fixture-secret");
        ReflectionTestUtils.setField(client, "apiId", "fixture-id");
        ReflectionTestUtils.setField(client, "returnType", "xml");
        ReflectionTestUtils.setField(client, "dailyAction", "dailyPriceByCategoryList");
        ReflectionTestUtils.setField(client, "productClass", "02");
    }

    static String xml(String dpr1, String day1, String dpr2, String day2) {
        return "<document><data><error_code>000</error_code><item>"
                + "<item_code>211</item_code><item_name>배추</item_name><kind_code>01</kind_code>"
                + "<kind_name>가을</kind_name><rank_code>01</rank_code><rank>상품</rank><unit>10kg</unit>"
                + "<dpr1>" + dpr1 + "</dpr1><day1>" + day1 + "</day1>"
                + "<dpr2>" + dpr2 + "</dpr2><day2>" + day2 + "</day2><dpr4>1,000</dpr4>"
                + "</item></data></document>";
    }

    @Test void mondayUsesTheActualFridayRequestDateAndOfficialParameters() {
        when(http.getForObject(anyString(), eq(String.class))).thenAnswer(call -> {
            String url = call.getArgument(0);
            assertThat(url).contains("p_regday=2026-10-09", "p_item_category_code=", "p_convert_kg_yn=Y", "p_product_cls_code=02");
            return xml("2,000", "당일", "1,900", "1일전");
        });
        var dto = client.fetchAllCategories().getFirst();
        assertThat(dto.getObservedDate()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(dto.getRequestedDate()).isEqualTo(dto.getObservedDate());
        assertThat(dto.getCurrentPrice()).isEqualTo("2000");
        assertThat(dto.getPriceBasis()).isEqualTo("WHOLESALE");
        assertThat(dto.getUnit()).isEqualTo("kg");
    }

    @Test void holidayFallbackKeepsTheSuccessfulOlderRequestDate() {
        when(http.getForObject(anyString(), eq(String.class))).thenAnswer(call ->
                ((String) call.getArgument(0)).contains("p_regday=2026-10-09")
                        ? "<document><data><error_code>200</error_code></data></document>"
                        : xml("2000", "당일", "-", "1일전"));
        assertThat(client.fetchAllCategories().getFirst().getObservedDate()).isEqualTo(LocalDate.of(2026, 10, 8));
    }

    @Test void fallbackPriceUsesExplicitResponseDate() {
        when(http.getForObject(anyString(), eq(String.class))).thenReturn(xml("-", "당일", "1900", "10/08"));
        var dto = client.fetchAllCategories().getFirst();
        assertThat(dto.getCurrentPrice()).isEqualTo("1900");
        assertThat(dto.getObservedDate()).isEqualTo(LocalDate.of(2026, 10, 8));
    }

    @Test void relativePreviousDayIsNotGuessedAcrossWeekendsOrHolidays() {
        when(http.getForObject(anyString(), eq(String.class))).thenAnswer(call ->
                ((String) call.getArgument(0)).contains("p_regday=2026-10-09")
                        ? xml("-", "당일", "1900", "1일전") : xml("1800", "당일", "-", "1일전"));
        var dto = client.fetchAllCategories().getFirst();
        assertThat(dto.getCurrentPrice()).isEqualTo("1800");
        assertThat(dto.getObservedDate()).isEqualTo(LocalDate.of(2026, 10, 8));
    }

    @Test void retailRequestCarriesRetailBasis() {
        ReflectionTestUtils.setField(client, "productClass", "01");
        when(http.getForObject(anyString(), eq(String.class))).thenReturn(xml("2000", "당일", "-", "1일전"));
        assertThat(client.fetchAllCategories().getFirst().getPriceBasis()).isEqualTo("RETAIL");
    }

    @Test void failedNetworkAndInvalidCredentialsAreNotReportedAsAnEmptySuccessfulCollection() {
        when(http.getForObject(anyString(), eq(String.class))).thenThrow(new ResourceAccessException("fixture-secret in remote URL"));
        assertThatThrownBy(client::fetchAllCategories).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KAMIS 접근 실패").hasMessageNotContaining("fixture-secret");
        when(http.getForObject(anyString(), eq(String.class)))
                .thenReturn("<document><data><error_code>100</error_code></data></document>");
        assertThatThrownBy(client::fetchAllCategories).hasMessageContaining("응답 오류");
    }

    @Test void decimalKgPriceIsRetainedWithoutIntegerParsingFailure() {
        when(http.getForObject(anyString(), eq(String.class))).thenReturn(xml("1,234.50", "당일", "-", "1일전"));
        assertThat(client.fetchAllCategories().getFirst().getCurrentPrice()).isEqualTo("1234.5");
    }
}
