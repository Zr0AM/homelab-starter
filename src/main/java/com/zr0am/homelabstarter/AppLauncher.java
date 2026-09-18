package com.zr0am.homelabstarter;

import com.zr0am.homelabstarter.api.advice.ApiExceptionHandlerAdvice;
import com.zr0am.homelabstarter.api.advice.ApiResponseAdvice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {ApiExceptionHandlerAdvice.class, ApiResponseAdvice.class}
))
@Slf4j
public class AppLauncher {

    public static void main(String[] args) {
        log.info("Starting application");
        SpringApplication.run(AppLauncher.class, args);
        log.info("Application started");
    }

}
