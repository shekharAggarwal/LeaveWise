package dev.leavewise.api;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Builds plan request bodies as raw JSON so tests can send values of the wrong type. */
public final class PlanJson {

  private final Map<String, String> fields = new LinkedHashMap<>();

  private PlanJson() {}

  /** A valid 2027 request with the PRD defaults and no holidays. */
  public static PlanJson valid() {
    PlanJson p = new PlanJson();
    p.fields.put("year", "2027");
    p.fields.put("leaveDays", "12");
    p.fields.put("weeklyOffs", "[\"SATURDAY\",\"SUNDAY\"]");
    p.fields.put("breakStyle", "\"LONG_WEEKENDS\"");
    p.fields.put("holidays", "[]");
    return p;
  }

  /** Sets a field to a raw JSON value, e.g. {@code with("leaveDays", "12.5")}. */
  public PlanJson with(String field, String rawJson) {
    fields.put(field, rawJson);
    return this;
  }

  public PlanJson without(String field) {
    fields.remove(field);
    return this;
  }

  /** Sets holidays from alternating date, name arguments. */
  public PlanJson holidays(String... dateNamePairs) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < dateNamePairs.length; i += 2) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append("{\"date\":\"")
          .append(dateNamePairs[i])
          .append("\",\"name\":\"")
          .append(dateNamePairs[i + 1])
          .append("\"}");
    }
    return with("holidays", sb.append(']').toString());
  }

  /** Sets {@code count} holidays on consecutive days of 2027 starting 1 January. */
  public PlanJson manyHolidays(int count) {
    String[] pairs = new String[count * 2];
    for (int i = 0; i < count; i++) {
      pairs[2 * i] = LocalDate.of(2027, 1, 1).plusDays(i).toString();
      pairs[2 * i + 1] = "Fixture Holiday " + i;
    }
    return holidays(pairs);
  }

  public String json() {
    return fields.entrySet().stream()
        .map(e -> "\"" + e.getKey() + "\":" + e.getValue())
        .collect(Collectors.joining(",", "{", "}"));
  }
}
