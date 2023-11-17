package com.pedrolima.wexchange.api.purchase;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.pedrolima.wexchange.utils.CustomLocalDateDeserializer;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreatePurchaseApiInput(
        @JsonProperty("description") @Size(min = 3, max = 50) @NotBlank String description,
        @JsonProperty("date")
        @JsonDeserialize(using = CustomLocalDateDeserializer.class)
        @NotNull LocalDate date,
        @JsonProperty("amount") @DecimalMin("0.00") @NotNull BigDecimal amount
) {

}
