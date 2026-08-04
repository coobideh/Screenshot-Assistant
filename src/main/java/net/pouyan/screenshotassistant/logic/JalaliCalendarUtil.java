package net.pouyan.screenshotassistant.logic;

import java.time.LocalDate;

/**
 * Minimal Gregorian → Jalali (Solar Hijri / Persian) calendar converter.
 * No external dependency; valid for all modern dates this mod will encounter.
 */
public final class JalaliCalendarUtil {

    private JalaliCalendarUtil() {}

    /** @return {year, month, day} in the Jalali calendar */
    public static int[] toJalali(LocalDate date) {
        return gregorianToJalali(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
    }

    public static String formatJalali(LocalDate date) {
        int[] j = toJalali(date);
        return String.format("%04d-%02d-%02d", j[0], j[1], j[2]);
    }

    /**
     * Returns the ISO week-of-Jalali-year (1–53) for the given date.
     *
     * The Jalali year starts on Farvardin 1 (around March 20 Gregorian).
     * Months 1–6 have 31 days; months 7–11 have 30 days; month 12 has
     * 29 or 30. Week 1 is the week containing Farvardin 1.
     */
    public static int jalaliWeekOfYear(LocalDate date) {
        int[] jToday = toJalali(date);
        // Farvardin 1 of the same Jalali year
        LocalDate nowruz = jalaliToGregorian(jToday[0], 1, 1);
        // Day-of-year in Jalali (1-based)
        int dayOfYear = (int) (date.toEpochDay() - nowruz.toEpochDay()) + 1;
        if (dayOfYear < 1) dayOfYear = 1;
        return (dayOfYear - 1) / 7 + 1;
    }

    // ----------------------------------------------------------------- private

    private static int[] gregorianToJalali(int gy, int gm, int gd) {
        final int[] gDaysInMonth = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};

        int jy;
        int gyLocal = gy;
        if (gyLocal > 1600) {
            jy = 979;
            gyLocal -= 1600;
        } else {
            jy = 0;
            gyLocal -= 621;
        }

        int gy2 = (gm > 2) ? (gyLocal + 1) : gyLocal;
        int days = (365 * gyLocal)
                + ((gy2 + 3) / 4)
                - ((gy2 + 99) / 100)
                + ((gy2 + 399) / 400)
                - 80 + gd + gDaysInMonth[gm - 1];

        jy += 33 * (days / 12053);
        days %= 12053;
        jy += 4 * (days / 1461);
        days %= 1461;

        if (days > 365) {
            jy += (days - 1) / 365;
            days = (days - 1) % 365;
        }

        int jm;
        int jd;
        if (days < 186) {
            jm = 1 + days / 31;
            jd = 1 + (days % 31);
        } else {
            jm = 7 + (days - 186) / 30;
            jd = 1 + ((days - 186) % 30);
        }
        return new int[]{jy, jm, jd};
    }

    /**
     * Converts a Jalali date back to LocalDate (Gregorian).
     * Used to find Nowruz (Farvardin 1) of a given Jalali year.
     */
    private static LocalDate jalaliToGregorian(int jy, int jm, int jd) {
        int jy1 = jy - 979;
        int jm1 = jm - 1;
        int jd1 = jd - 1;

        int j_day_no = 365 * jy1 + (jy1 / 33) * 8 + (jy1 % 33 + 3) / 4;
        for (int i = 0; i < jm1; i++) {
            j_day_no += (i < 6) ? 31 : 30;
        }
        j_day_no += jd1;

        int g_day_no = j_day_no + 79;
        int gy = 1600 + 400 * (g_day_no / 146097);
        g_day_no = g_day_no % 146097;

        boolean leap = true;
        if (g_day_no >= 36525) {
            g_day_no--;
            gy += 100 * (g_day_no / 36524);
            g_day_no = g_day_no % 36524;
            if (g_day_no >= 365) g_day_no++;
            else leap = false;
        }

        gy += 4 * (g_day_no / 1461);
        g_day_no %= 1461;

        if (g_day_no >= 366) {
            leap = false;
            g_day_no--;
            gy += g_day_no / 365;
            g_day_no = g_day_no % 365;
        }

        final int[] gDays = {31, (leap ? 29 : 28), 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        int gm = 0;
        for (int i = 0; i < 12; i++) {
            if (g_day_no < gDays[i]) {
                gm = i + 1;
                break;
            }
            g_day_no -= gDays[i];
        }
        int gd = g_day_no + 1;
        return LocalDate.of(gy, gm, gd);
    }
}
