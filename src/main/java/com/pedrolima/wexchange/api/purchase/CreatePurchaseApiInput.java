package com.pedrolima.wexchange.api.purchase;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.pedrolima.wexchange.util.CustomLocalDateDeserializer;
import jakarta.validation.constraints.DecimalMin;
import org.wildfly.common.annotation.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreatePurchaseApiInput(
        @JsonProperty("description") String description,
        @JsonProperty("date")
        @JsonDeserialize(using = CustomLocalDateDeserializer.class)
        @NotNull LocalDate date,
        @JsonProperty("amount") @DecimalMin("0.00") @NotNull BigDecimal amount
) {

}
