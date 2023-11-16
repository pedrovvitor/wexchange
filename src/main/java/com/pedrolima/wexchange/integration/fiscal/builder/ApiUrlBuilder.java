package com.pedrolima.wexchange.integration.fiscal.builder;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ApiUrlBuilder {

    public static final int PAGE_SIZE_MAX_VALUE = 10000;

    private final StringBuilder urlBuilder;

    private final List<String> fields;

    private final List<String> filters;

    private final List<String> sorts;

    private boolean firstParam;

    public ApiUrlBuilder(final String baseUrl) {
        this.urlBuilder = new StringBuilder(baseUrl);
        this.fields = new ArrayList<>();
        this.sorts = new ArrayList<>();
        this.filters = new ArrayList<>();
        this.firstParam = true;
    }

    private void appendParam(final String name, final String value) {
        if (firstParam) {
            urlBuilder.append("?").append(name).append("=").append(value);
            firstParam = false;
        } else {
            urlBuilder.append("&").append(name).append("=").append(value);
        }
    }

    public ApiUrlBuilder addFields(final FieldType... fields) {
        String value = Arrays.stream(fields)
                .map(FieldType::getAlias)
                .collect(Collectors.joining(","));
        this.fields.add(value);
        return this;
    }

    public ApiUrlBuilder addFilter(final FieldType field, final ParamComparator comparator, final String value) {
        if (comparator.equals(ParamComparator.IN)) {
            filters.add(field.alias + comparator.alias + "(" + value + ")");
        } else {
            filters.add(field.alias + comparator.alias + value);
        }
        return this;
    }

    public ApiUrlBuilder addSorting(final SortOrder order, final FieldType field) {
        String value = order.alias.concat(field.alias);
        sorts.add(value);
        return this;
    }

    public ApiUrlBuilder addPagination(final PageType pageType, final int value) {
        appendParam("page[" + pageType.alias + "]", String.valueOf(value));
        return this;
    }

    public String build() {
        if (!fields.isEmpty()) {
            String filterValue = String.join(",", fields);
            appendParam("fields", filterValue);
        }
        if (!filters.isEmpty()) {
            String filterValue = String.join(",", filters);
            appendParam("filter", filterValue);
        }
        if (!sorts.isEmpty()) {
            String filterValue = String.join(",", sorts);
            appendParam("sort", filterValue);
        }

        return urlBuilder.toString();
    }

    @Getter
    public enum PageType {
        SIZE("size"),
        NUMBER("number");

        private final String alias;

        PageType(final String alias) {
            this.alias = alias;
        }
    }

    @Getter
    public enum FieldType {
        RECORD_DATE("record_date"),
        COUNTRY("country"),
        CURRENCY("currency"),
        COUNTRY_CURRENCY("country_currency_desc"),
        EXCHANGE_RATE("exchange_rate"),
        EFFECTIVE_DATE("effective_date");

        private final String alias;

        FieldType(final String alias) {
            this.alias = alias;
        }
    }

    @Getter
    public enum SortOrder {
        ASC(""),
        DESC("-");

        private final String alias;

        SortOrder(final String alias) {
            this.alias = alias;
        }
    }

    @Getter
    public enum ParamComparator {
        EQL("="),
        LT(":lt:"),
        LTE(":lte:"),
        GT(":gt:"),
        GTE(":gte:"),
        EQ(":eq:"),
        IN(":in:");

        private final String alias;

        ParamComparator(final String alias) {
            this.alias = alias;
        }
    }
}
