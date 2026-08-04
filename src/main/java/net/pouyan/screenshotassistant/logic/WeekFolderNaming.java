package net.pouyan.screenshotassistant.logic;

import net.pouyan.screenshotassistant.config.CalendarType;
import net.pouyan.screenshotassistant.config.ModConfig;
import net.pouyan.screenshotassistant.config.WeekNamingMode;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;

/**
 * Computes the name of the "current week" folder, based on:
 *  - which day of week the week starts ({@link ModConfig#weekStartDayOfWeek})
 *  - which hour the new week kicks in  ({@link ModConfig#weekStartHour})
 *  - the naming mode (date-range or week-number)
 *  - the calendar type (Gregorian or Jalali)
 */
public final class WeekFolderNaming {

    private static final DateTimeFormatter GREGORIAN_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private WeekFolderNaming() {}

    /**
     * Returns the folder name for the week that contains {@code now}.
     *
     * @param now    the current date-time
     * @param config the mod config
     */
    public static String currentWeekFolderName(LocalDateTime now, ModConfig config) {
        // Adjust for "week starts at hour X" – if current time is before the
        // start hour we are still in the previous week's bucket.
        LocalDate effectiveDate = now.getHour() < config.weekStartHour
                ? now.toLocalDate().minusDays(1)
                : now.toLocalDate();

        LocalDate weekStart = startOfWeek(effectiveDate, config.weekStartDayOfWeek);

        CalendarType cal = config.calendarType == null ? CalendarType.GREGORIAN : config.calendarType;
        WeekNamingMode mode = config.weekNamingMode == null ? WeekNamingMode.DATE_RANGE : config.weekNamingMode;

        if (mode == WeekNamingMode.WEEK_NUMBER) {
            return buildWeekNumberName(weekStart, cal);
        } else {
            return buildDateRangeName(weekStart, cal);
        }
    }

    // ----------------------------------------------------------------- private

    /** Returns the start of the week (weekStartDay) on/before {@code date}. */
    private static LocalDate startOfWeek(LocalDate date, int weekStartDayOfWeek) {
        // Java DayOfWeek: Monday=1 … Sunday=7
        int todayValue = date.getDayOfWeek().getValue();
        int startValue = clampDay(weekStartDayOfWeek);
        int diff = Math.floorMod(todayValue - startValue, 7);
        return date.minusDays(diff);
    }

    private static int clampDay(int d) {
        if (d < 1 || d > 7) return 4; // fallback to Thursday
        return d;
    }

    // -------------------------------------------------------- DATE_RANGE mode

    private static String buildDateRangeName(LocalDate weekStart, CalendarType cal) {
        LocalDate weekEnd = weekStart.plusDays(6);
        String start = formatDate(weekStart, cal);
        String end   = formatDate(weekEnd,   cal);
        return start + "_to_" + end;
    }

    private static String formatDate(LocalDate date, CalendarType cal) {
        if (cal == CalendarType.JALALI) {
            return JalaliCalendarUtil.formatJalali(date);
        }
        return date.format(GREGORIAN_FMT);
    }

    // -------------------------------------------------------- WEEK_NUMBER mode

    private static String buildWeekNumberName(LocalDate weekStart, CalendarType cal) {
        if (cal == CalendarType.JALALI) {
            int[] j = JalaliCalendarUtil.toJalali(weekStart);
            int weekNum = JalaliCalendarUtil.jalaliWeekOfYear(weekStart);
            // e.g. "هفته-14-1404"
            return String.format("\u0647\u0641\u062a\u0647-%d-%d", weekNum, j[0]);
        } else {
            // ISO week of year + ISO week-based year
            int weekNum  = weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            int weekYear = weekStart.get(IsoFields.WEEK_BASED_YEAR);
            return String.format("Week-%d-%d", weekNum, weekYear);
        }
    }
}
