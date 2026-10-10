package dev.leavewise.holidays;

/**
 * Where a bundled holiday list comes from (ADR 0007). {@code url} is an official gov.in or nic.in
 * address.
 */
public record HolidaySource(String publisher, String title, String url, String retrievedOn) {}
