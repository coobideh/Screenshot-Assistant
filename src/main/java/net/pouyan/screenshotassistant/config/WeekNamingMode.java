package net.pouyan.screenshotassistant.config;

/**
 * How weekly rotation folders are named.
 *
 * DATE_RANGE : "2026-07-31_to_2026-08-06" (Gregorian) or "1405-05-09_to_1405-05-15" (Jalali)
 * WEEK_NUMBER: "Week-31-2026" (Gregorian) or "هفته-31-1404" (Jalali)
 */
public enum WeekNamingMode {
    DATE_RANGE,
    WEEK_NUMBER
}
