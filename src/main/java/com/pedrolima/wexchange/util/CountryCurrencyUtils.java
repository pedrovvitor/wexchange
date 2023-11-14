package com.pedrolima.wexchange.util;

import org.apache.commons.lang3.StringUtils;

public final class CountryCurrencyUtils {

    private CountryCurrencyUtils() {
    }

    public static String formatCountryCurrency(String input) {
        if (StringUtils.isBlank(input)) {
            throw new IllegalArgumentException("Input %s should be in '{country_name}-{currency_name}' format".formatted(input));
        }
        String[] parts = input.split("((?<=-)|(?=-))|((?<= )|(?= ))");
        StringBuilder capitalizedWords = new StringBuilder();

        for (String part : parts) {
            if (part.equals("-") || part.equals(" ")) {
                capitalizedWords.append(part);
            } else {
                capitalizedWords.append(Character.toUpperCase(part.charAt(0)))
                        .append(part.substring(1).toLowerCase());
            }
        }

        capitalizedWords.append(",").append(input.toUpperCase());

        return capitalizedWords.toString();
    }
}
