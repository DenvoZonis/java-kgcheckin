package xyz.denvo.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;

import java.math.BigDecimal;

public final class JsonHelper {

    public static final Gson GSON = new GsonBuilder()
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .create();

    private JsonHelper() {}

    public static String toJson(Object obj) {
        return GSON.toJson(obj);
    }

    public static String safeNumberString(Object value) {
        if (value == null) return "";
        if (value instanceof Number) {
            return new BigDecimal(value.toString()).toPlainString();
        }
        return value.toString();
    }
}