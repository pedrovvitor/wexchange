package com.pedrolima.wexchange.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.util.List;

public class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JavaTimeModule());

    public static <T> List<T> extractDataList(final String json, final Class<T> clazz) throws IOException {
        JsonNode rootNode = OBJECT_MAPPER.readTree(json);
        JsonNode dataArray = rootNode.path("data");
        JavaType type = TypeFactory.defaultInstance().constructCollectionType(List.class, clazz);

        return OBJECT_MAPPER.readValue(dataArray.toString(), type);
    }
}

