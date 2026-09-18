package com.zr0am.homelabstarter;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * Test-only context root for {@code @SpringBootTest}.
 * <p>
 * Deliberately not {@code @SpringBootApplication}: that would add {@code @ComponentScan}, which
 * picks up {@code ApiResponseAdvice} and {@code ApiExceptionHandlerAdvice} directly (they are
 * {@code @Component}s by virtue of {@code @RestControllerAdvice}) and bypasses the
 * auto-configuration this library actually ships. Without the scan, the beans can only arrive
 * via {@code ApiStarterAutoConfiguration} — which is what consumers exercise.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class TestApplication {
}
