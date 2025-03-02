package net.urosk.taskomir.core.config;


import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

@Configuration
public class MessageConfig {

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        // Ime datoteke z lastnostmi (brez končnice .properties)
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        // Če ključ ni najden, vrne sam ključ
        messageSource.setUseCodeAsDefaultMessage(true);
        return messageSource;
    }
}