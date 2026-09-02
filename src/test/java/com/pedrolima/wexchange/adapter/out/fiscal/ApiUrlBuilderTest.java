package com.pedrolima.wexchange.adapter.out.fiscal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.pedrolima.wexchange.adapter.out.fiscal.ApiUrlBuilder.FieldType.COUNTRY;
import static com.pedrolima.wexchange.adapter.out.fiscal.ApiUrlBuilder.FieldType.COUNTRY_CURRENCY;
import static com.pedrolima.wexchange.adapter.out.fiscal.ApiUrlBuilder.FieldType.EFFECTIVE_DATE;
import static com.pedrolima.wexchange.adapter.out.fiscal.ApiUrlBuilder.FieldType.EXCHANGE_RATE;
import static com.pedrolima.wexchange.adapter.out.fiscal.ApiUrlBuilder.PageType.SIZE;
import static com.pedrolima.wexchange.adapter.out.fiscal.ApiUrlBuilder.ParamComparator.GTE;
import static com.pedrolima.wexchange.adapter.out.fiscal.ApiUrlBuilder.ParamComparator.IN;
import static com.pedrolima.wexchange.adapter.out.fiscal.ApiUrlBuilder.ParamComparator.LTE;
import static com.pedrolima.wexchange.adapter.out.fiscal.ApiUrlBuilder.SortOrder.ASC;
import static com.pedrolima.wexchange.adapter.out.fiscal.ApiUrlBuilder.SortOrder.DESC;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Characterization of the Fiscal Data query string. The upstream API rejects or
 * silently ignores malformed parameters, so the exact separator placement is
 * behaviour, not formatting.
 */
class ApiUrlBuilderTest {

    private static final String BASE_URL = "https://api.example.test/v1/rates";

    @Test
    @DisplayName("a builder with no parameters returns the base URL untouched")
    void givenNoParameters_whenBuilding_thenBaseUrlIsReturned() {
        assertEquals(BASE_URL, new ApiUrlBuilder(BASE_URL).build());
    }

    @Test
    @DisplayName("the first parameter is introduced with ? and later ones with &")
    void givenSeveralParameterGroups_whenBuilding_thenSeparatorsAlternateCorrectly() {
        final var url = new ApiUrlBuilder(BASE_URL)
                .addFields(EXCHANGE_RATE, EFFECTIVE_DATE)
                .addFilter(EFFECTIVE_DATE, GTE, "2024-01-01")
                .addSorting(DESC, EFFECTIVE_DATE)
                .build();

        assertEquals(BASE_URL
                        + "?fields=exchange_rate,effective_date"
                        + "&filter=effective_date:gte:2024-01-01"
                        + "&sort=-effective_date",
                url);
    }

    @Test
    @DisplayName("pagination applied first still produces a single leading ?")
    void givenPaginationFirst_whenBuilding_thenOnlyOneQuestionMarkIsEmitted() {
        final var url = new ApiUrlBuilder(BASE_URL)
                .addPagination(SIZE, ApiUrlBuilder.PAGE_SIZE_MAX_VALUE)
                .addFields(COUNTRY)
                .build();

        assertEquals(BASE_URL + "?page[size]=10000&fields=country", url);
    }

    @Test
    @DisplayName("an IN comparison wraps its values in parentheses; other comparators do not")
    void givenInComparator_whenBuilding_thenValuesAreParenthesised() {
        final var url = new ApiUrlBuilder(BASE_URL)
                .addFilter(COUNTRY_CURRENCY, IN, "Brazil-Real,Canada-Dollar")
                .addFilter(EFFECTIVE_DATE, LTE, "2024-06-30")
                .build();

        assertEquals(BASE_URL
                        + "?filter=country_currency_desc:in:(Brazil-Real,Canada-Dollar),"
                        + "effective_date:lte:2024-06-30",
                url);
    }

    @Test
    @DisplayName("ascending sorting carries no prefix, descending carries a minus")
    void givenBothSortOrders_whenBuilding_thenOnlyDescendingIsPrefixed() {
        final var url = new ApiUrlBuilder(BASE_URL)
                .addSorting(ASC, COUNTRY)
                .addSorting(DESC, EFFECTIVE_DATE)
                .build();

        assertEquals(BASE_URL + "?sort=country,-effective_date", url);
    }

    @Test
    @DisplayName("filters and sorts are emitted even when no fields were requested")
    void givenNoFields_whenBuilding_thenRemainingGroupsStillAppear() {
        final var url = new ApiUrlBuilder(BASE_URL)
                .addFilter(EFFECTIVE_DATE, GTE, "2024-01-01")
                .build();

        assertEquals(BASE_URL + "?filter=effective_date:gte:2024-01-01", url);
    }
}
