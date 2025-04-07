package io.wrtn.util;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import static io.wrtn.util.Constants.Config.INDEX_BUILDER_TIMEOUT;

public class TimeUtil {

    public static long getCurrentUnixEpoch() {
        return Instant.now().getEpochSecond();
    }

    public static int getIndexBuilderFailureTimeout() {
        return INDEX_BUILDER_TIMEOUT + 60;
    }

    public static OffsetDateTime stringToOffsetDateTime(String dateTime)
        throws GlobalExceptionHandler {
        try {
            return OffsetDateTime.parse(dateTime,
                DateTimeFormatter.ISO_DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new GlobalExceptionHandler(
                "Invalid datetime format: " + dateTime + ", ex)'2024-12-16T15:58:01.772Z'",
                StatusCode.INVALID_INPUT_VALUE);
        }
    }

    public static long offsetDateTimeToTime(OffsetDateTime dateTime) {
        return dateTime.atZoneSameInstant(ZoneId.of("UTC")).toInstant().toEpochMilli();
    }

    public static OffsetDateTime timeToOffsetDateTime(long time) {
        return Instant.ofEpochMilli(time).atZone(ZoneId.of("UTC")).toOffsetDateTime();
    }

    public static String offsetDateTimeToString(OffsetDateTime dateTime) {
        return dateTime.format(DateTimeFormatter.ISO_DATE_TIME);
    }

    public static long stringToTime(String dateTime) throws GlobalExceptionHandler {
        try {
            return offsetDateTimeToTime(stringToOffsetDateTime(dateTime));
        } catch (DateTimeParseException e) {
            throw new GlobalExceptionHandler(
                "Invalid datetime format: " + dateTime + ", ex)'2024-12-16T15:58:01.772Z'",
                StatusCode.INVALID_INPUT_VALUE);
        }
    }

    public static String timeToString(long time) {
        return offsetDateTimeToString(timeToOffsetDateTime(time));
    }
}
