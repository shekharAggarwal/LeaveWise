package dev.leavewise.holidays;

import dev.leavewise.planner.Holiday;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The bundled holiday lists, loaded and checked once (ADR 0007). Files are JSON documents named
 * {@code india-YYYY.json} in the format shown in ADR 0007.
 */
public final class HolidayRepository {

  private static final Pattern FILE_NAME = Pattern.compile("india-(\\d{4})\\.json");
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String GOVERNMENT = "government";
  private static final String COMPANY = "company";

  private final Map<Integer, HolidayList> byYear;

  private HolidayRepository(Map<Integer, HolidayList> byYear) {
    this.byYear = Map.copyOf(byYear);
  }

  /**
   * Loads every resource matching {@code locationPattern} (a Spring resource pattern such as {@code
   * classpath*:holidays/india-*.json}).
   *
   * @param allowTestData when false, a file marked {@code "testData": true} is refused; only tests
   *     pass true
   * @throws IllegalStateException if a file can't be read or breaks a rule: {@code kind} not
   *     government or company; {@code source.url} not https, and for a government list not on a
   *     gov.in or nic.in host (always checked, test data too); a date outside {@code year}; a blank
   *     name; a file name year that differs from {@code year}, two files for one year, or test data
   *     when not allowed
   */
  public static HolidayRepository load(String locationPattern, boolean allowTestData) {
    Resource[] resources;
    try {
      resources = new PathMatchingResourcePatternResolver().getResources(locationPattern);
    } catch (IOException e) {
      throw new IllegalStateException("cannot list holiday files at " + locationPattern, e);
    }
    Map<Integer, HolidayList> byYear = new TreeMap<>();
    for (Resource resource : resources) {
      HolidayList list = read(resource, allowTestData);
      if (byYear.putIfAbsent(list.year(), list) != null) {
        throw new IllegalStateException("two holiday files for " + list.year());
      }
    }
    return new HolidayRepository(byYear);
  }

  private static HolidayList read(Resource resource, boolean allowTestData) {
    String name = String.valueOf(resource.getFilename());
    Matcher m = FILE_NAME.matcher(name);
    if (!m.matches()) {
      throw new IllegalStateException("holiday file name must be india-YYYY.json: " + name);
    }
    JsonNode root;
    try (InputStream in = resource.getInputStream()) {
      root = JSON.readTree(in);
    } catch (IOException | JacksonException e) {
      throw new IllegalStateException("cannot read holiday file " + name, e);
    }
    if (root == null || !root.isObject()) {
      throw new IllegalStateException(name + ": not a JSON object");
    }
    if (root.path("testData").asBoolean(false) && !allowTestData) {
      throw new IllegalStateException(name + ": test data is not allowed here");
    }
    int year = requireInt(root, "year", name);
    if (year != Integer.parseInt(m.group(1))) {
      throw new IllegalStateException(name + ": file name year differs from \"year\" " + year);
    }
    String kind = root.has("kind") ? requireText(root, "kind", name) : GOVERNMENT;
    if (!kind.equals(GOVERNMENT) && !kind.equals(COMPANY)) {
      throw new IllegalStateException(name + ": \"kind\" must be government or company");
    }
    JsonNode source = root.path("source");
    String url = requireText(source, "url", name);
    HolidaySource holidaySource =
        new HolidaySource(
            requireText(source, "publisher", name),
            requireText(source, "title", name),
            kind.equals(GOVERNMENT) ? requireOfficialUrl(url, name) : requireHttps(url, name),
            requireIsoDate(requireText(source, "retrievedOn", name), "retrievedOn", name));
    List<Holiday> holidays = new ArrayList<>();
    List<Holiday> optional = new ArrayList<>();
    JsonNode items = root.path("holidays");
    if (!items.isArray()) {
      throw new IllegalStateException(name + ": \"holidays\" must be an array");
    }
    for (JsonNode item : items) {
      LocalDate date;
      try {
        date = LocalDate.parse(requireText(item, "date", name));
      } catch (DateTimeException e) {
        throw new IllegalStateException(name + ": bad date " + item.path("date"), e);
      }
      if (date.getYear() != year) {
        throw new IllegalStateException(name + ": " + date + " is outside " + year);
      }
      Holiday holiday = new Holiday(date, requireText(item, "name", name));
      holidays.add(holiday);
      if (item.path("optional").asBoolean(false)) {
        optional.add(holiday);
      }
    }
    return new HolidayList(
        year, kind, requireText(root, "list", name), holidaySource, holidays, optional);
  }

  private static int requireInt(JsonNode node, String field, String file) {
    JsonNode value = node.path(field);
    if (!value.isIntegralNumber() || !value.canConvertToInt()) {
      throw new IllegalStateException(file + ": \"" + field + "\" must be a whole number");
    }
    return value.intValue();
  }

  private static String requireText(JsonNode node, String field, String file) {
    JsonNode value = node.path(field);
    if (!value.isString() || value.stringValue().isBlank()) {
      throw new IllegalStateException(file + ": \"" + field + "\" must be non-blank text");
    }
    return value.stringValue().strip();
  }

  private static String requireIsoDate(String text, String field, String file) {
    try {
      LocalDate.parse(text);
      return text;
    } catch (DateTimeException e) {
      throw new IllegalStateException(
          file + ": \"" + field + "\" must be a date like 2026-10-20", e);
    }
  }

  /** A company list (ADR 0007 amendment) still names its source over https. */
  private static String requireHttps(String url, String file) {
    try {
      URI uri = new URI(url);
      if ("https".equals(uri.getScheme()) && uri.getHost() != null) {
        return url;
      }
    } catch (URISyntaxException e) {
      // falls through to the error below
    }
    throw new IllegalStateException(file + ": source.url must be https");
  }

  /** ADR 0007: only https addresses on gov.in or nic.in hosts count as an official source. */
  private static String requireOfficialUrl(String url, String file) {
    try {
      URI uri = new URI(url);
      String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(java.util.Locale.ROOT);
      boolean official =
          host.equals("gov.in")
              || host.endsWith(".gov.in")
              || host.equals("nic.in")
              || host.endsWith(".nic.in");
      if ("https".equals(uri.getScheme()) && official) {
        return url;
      }
    } catch (URISyntaxException e) {
      // falls through to the error below
    }
    throw new IllegalStateException(file + ": source.url must be https on a gov.in or nic.in host");
  }

  /** The bundled list for a year, if any. */
  public Optional<HolidayList> forYear(int year) {
    return Optional.ofNullable(byYear.get(year));
  }
}
