package com.x8ing.mtl.server.mtlserver.web.global;

import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.module.SimpleModule;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.Formatter;
import org.springframework.format.datetime.standard.DateTimeFormatterRegistrar;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.format.support.FormattingConversionService;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;


/**
 * *************************************************************
 * That config only works, if nowhere else a plain ObjectMapper is passed as bean to spring which would override
 * Careful with "MyObjectMapper"
 * *************************************************************
 * https://www.baeldung.com/spring-boot-formatting-json-dates
 */
@Configuration
public class DateTimeConfig {


    private static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter dateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final String DATE_TIME_SIMPLE_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"; // CAREFUL: very similar to DateFormatter, but SimpleDateFormat does NOT support XXX


    @Bean
    public FormattingConversionService conversionService() {
        DefaultFormattingConversionService conversionService =
                new DefaultFormattingConversionService(false);

        DateTimeFormatterRegistrar registrar = new DateTimeFormatterRegistrar();
        registrar.setDateFormatter(dateFormat);
        registrar.setDateTimeFormatter(dateTimeFormat);
        registrar.registerFormatters(conversionService);

        // other desired formatters

        return conversionService;
    }


    @Bean
    public JsonMapperBuilderCustomizer jsonCustomizer() {
        return builder -> {
            SimpleModule dateTimeModule = new SimpleModule("MTL date and time");
            dateTimeModule.addSerializer(LocalDate.class, new LocalDateSerializer(dateFormat));
            dateTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(dateTimeFormat));
            builder.defaultDateFormat(new SimpleDateFormat(DATE_TIME_SIMPLE_DATE_FORMAT));
            builder.addModule(dateTimeModule);
        };
    }


    @Bean
    public Formatter<LocalDate> localDateFormatter() {
        return new Formatter<LocalDate>() {
            @Override
            public LocalDate parse(String text, Locale locale) throws ParseException {
                return LocalDate.parse(text, dateFormat);
            }

            @Override
            public String print(LocalDate object, Locale locale) {
                return dateFormat.format(object);
            }
        };
    }

    @Bean
    public Formatter<LocalDateTime> localDateTimeFormatter() {
        return new Formatter<LocalDateTime>() {
            @Override
            public LocalDateTime parse(String text, Locale locale) throws ParseException {
                return LocalDateTime.parse(text, dateTimeFormat);
            }

            @Override
            public String print(LocalDateTime object, Locale locale) {
                return dateTimeFormat.format(object);
            }
        };
    }


    @Bean
    public Formatter<Date> dateFormatter() {
        return new Formatter<Date>() {
            @Override
            public Date parse(String text, Locale locale) throws ParseException {
                return new SimpleDateFormat(dateTimeFormat.toString()).parse(text);
            }

            @Override
            public String print(Date object, Locale locale) {
                return new SimpleDateFormat(dateTimeFormat.toString()).format(object);
            }
        };
    }
}
