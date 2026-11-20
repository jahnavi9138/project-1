package college.register;

import com.google.gson.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

final class Input {
    private Input() { }

    static String raw(JsonObject json, String key) {
        JsonElement e = json.get(key);
        if (e == null || e.isJsonNull()) return "";
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw Problem.invalid("Invalid " + key + ".");
        return e.getAsString();
    }

    static String text(JsonObject json, String key, String label, int limit, boolean required) {
        String value = raw(json, key).strip();
        if (required && value.isEmpty()) throw Problem.invalid("Enter " + label + ".");
        if (value.length() > limit || value.chars().anyMatch(Character::isISOControl)) {
            throw Problem.invalid("Use up to " + limit + " printable characters for " + label + ".");
        }
        return value;
    }

    static BigDecimal number(JsonObject json, String key, String label) {
        JsonElement e = json.get(key);
        if (e != null && e.isJsonPrimitive()) {
            String value = e.getAsString();
            if (value.matches("[0-9]{1,5}([.][0-9]{1,2})?")) return new BigDecimal(value);
        }
        throw Problem.invalid("Enter a number up to 99999.99 for " + label + ".");
    }

    static int integer(JsonObject json, String key, int min, int max) {
        JsonElement e = json.get(key);
        if (e != null && e.isJsonPrimitive()) return integer(e.getAsString(), key, min, max);
        throw Problem.invalid("Missing " + key + ".");
    }

    static int integer(String value, String label, int min, int max) {
        try {
            int n = Integer.parseInt(value);
            if (n >= min && n <= max) return n;
        } catch (NumberFormatException ignored) { }
        throw Problem.invalid("Choose a valid " + label + ".");
    }

    static String choice(String value, String label, String... choices) {
        if (Arrays.asList(choices).contains(value)) return value;
        throw Problem.invalid("Choose a valid " + label + ".");
    }

    static LocalDate date(String value) {
        try {
            LocalDate d = LocalDate.parse(value);
            if (d.isAfter(LocalDate.now())) throw Problem.invalid("Choose today or an earlier date.");
            if (d.getYear() < 1 || d.getYear() > 9999) throw Problem.invalid("Choose a valid date.");
            return d;
        } catch (DateTimeParseException | NullPointerException e) { throw Problem.invalid("Choose a valid date."); }
    }
}
