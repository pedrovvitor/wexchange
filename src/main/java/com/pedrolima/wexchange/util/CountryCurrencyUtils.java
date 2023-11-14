package com.pedrolima.wexchange.util;

public final class CountryCurrencyUtils {

    private CountryCurrencyUtils() {
    }

    public static String formatCurrencyName(String input) {
        final var cleanedInput =
                input.replaceAll("[^a-zA-Z-\\s]", "").replaceAll("\\s+", "");

        final var parts = cleanedInput.split("-");

        if (parts.length != 2) {
            throw new IllegalArgumentException("Input %s should be in '{country_name}-{currency_name}' format".formatted(input));
        }

        String part1 = capitalizeFirstLetter(parts[0]);
        String part2 = capitalizeFirstLetter(parts[1]);
        String formatted = part1 + "-" + part2;
        String upperCaseFormatted = formatted.toUpperCase();

        return formatted + "," + upperCaseFormatted;
    }

    private static String capitalizeFirstLetter(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
    }
}
