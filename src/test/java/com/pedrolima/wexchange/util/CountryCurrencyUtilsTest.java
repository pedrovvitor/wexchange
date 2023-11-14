package com.pedrolima.wexchange.util;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class CountryCurrencyUtilsTest {

    private record TestCase(String input, String expectedOutput) { }

    static Stream<TestCase> testCases() {
        return Stream.of(
                new TestCase("BraZIl-Real", "Brazil-Real,BRAZIL-REAL"),
                new TestCase("BelArUse-NeW RubLe", "Belaruse-New Ruble,BELARUSE-NEW RUBLE")
//                new TestCase("BurkinA FaSo-CfA FraNc", "Burkina Faso-CFA Franc,BURKINA FASO-CFA FRANC"),
//                new TestCase("ANTIGUA-BARBUDA-E. CARIBBEAN DOLLAR", "Antigua-Barbuda-E. Carribean Dollar," +
//                        "ANTIGUA-BARBUDA-E. CARIBBEAN DOLLAR")
        );
    }

    @ParameterizedTest
    @MethodSource("testCases")
    public void givenAValidCountryCurrency_whenCallFormatCountryCurrency_thenReturnFormattedCountryCurrency(TestCase testCase) {
        final var actual = CountryCurrencyUtils.formatCountryCurrency(testCase.input);

        Assertions.assertEquals(testCase.expectedOutput, actual);
    }

    @Test
    public void givenAnInvalidEmptyCountryCurrency_whenCallFormatCountryCurrency_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> CountryCurrencyUtils.formatCountryCurrency(StringUtils.EMPTY));
    }

    @Test
    public void givenAnInvalidCountryCurrency_whenCallFormatCountryCurrency_thenThrowsIllegalArgumentException() {
        final var aInvalidFormat = "InvalidFormat";
        assertThrows(IllegalArgumentException.class, () -> CountryCurrencyUtils.formatCountryCurrency(aInvalidFormat));
    }
}
