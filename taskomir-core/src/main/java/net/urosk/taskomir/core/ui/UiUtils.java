package net.urosk.taskomir.core.ui;

import org.springframework.context.i18n.LocaleContextHolder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class UiUtils {

    public static String formatTime(Long epochMillis) {
        if (epochMillis == null) {
            return "";
        }

        Locale userLocale = LocaleContextHolder.getLocale();

        // Tvorimo svoj pattern, npr. "dd.MM.yyyy HH:mm:ss", a z upoštevanjem userLocale
        DateTimeFormatter dtFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", userLocale);

        // Pretvorimo epochMillis -> LocalDateTime, nato formatiramo
        Instant instant = Instant.ofEpochMilli(epochMillis);
        LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        return dateTime.format(dtFormatter);
    }

}
