package dev.oreandorder;

import com.google.gson.*;
import java.math.BigDecimal;
import java.util.Set;
import java.util.function.Predicate;

public record TaskPlan(String item, int quantity, String deliverTo) {
    public static TaskPlan parse(String json, Predicate<String> itemExists) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            if (!o.keySet().equals(Set.of("task", "item", "quantity", "deliver_to")))
                throw new IllegalArgumentException();
            if (!string(o, "task").equals("acquire") || !string(o, "deliver_to").equals("base_storage"))
                throw new IllegalArgumentException();
            String item = string(o, "item");
            if (!item.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || !itemExists.test(item))
                throw new IllegalArgumentException();
            JsonPrimitive count = o.getAsJsonPrimitive("quantity");
            if (!count.isNumber()) throw new IllegalArgumentException();
            int quantity = new BigDecimal(count.getAsString()).intValueExact();
            if (quantity < 1 || quantity > 4096) throw new IllegalArgumentException();
            return new TaskPlan(item, quantity, "base_storage");
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Model returned an unsupported or invalid task. Try an explicit item and quantity.");
        }
    }

    private static String string(JsonObject o, String key) {
        JsonPrimitive p = o.getAsJsonPrimitive(key);
        if (!p.isString()) throw new IllegalArgumentException();
        return p.getAsString();
    }
}
