package dev.leavewise.holidays;

/**
 * Where a bundled holiday list comes from (ADR 0007). {@code url} is https: on a gov.in or nic.in
 * host for a government list, any host for the owner's company list.
 */
public record HolidaySource(String publisher, String title, String url, String retrievedOn) {}
