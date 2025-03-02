package net.urosk.taskomir.core.config;

import com.vaadin.flow.i18n.I18NProvider;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component
public class TaskomirI18NProvider implements I18NProvider {

    private final MessageSource messageSource;
    private static final Locale DEFAULT_LOCALE = new Locale.Builder().setLanguage("en").setRegion("EN").build();


    public TaskomirI18NProvider(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Override
    public List<Locale> getProvidedLocales() {

        return Arrays.asList(DEFAULT_LOCALE, new Locale.Builder().setLanguage("sl").setRegion("SL").build(), Locale.GERMAN);
    }

    @Override
    public String getTranslation(String key, Locale locale, Object... params) {

        Locale targetLocale = locale != null ? locale : DEFAULT_LOCALE;
        return messageSource.getMessage(key, params, targetLocale);
    }
}