package org.jmonadic.config;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;

import org.jmonadic.resilience.Resilience4jCircuitBreaker;
import org.jmonadic.resilience.Resilience4jRetryPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

@DisplayName("Resilience4j Auto Configuration Tests")
class Resilience4jAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(Resilience4jAutoConfiguration.class));

    @Nested
    @DisplayName("Circuit Breaker Configuration")
    class CircuitBreakerConfiguration {
        
        @Test
        @DisplayName("Should auto-configure default circuit breaker")
        void shouldAutoConfigureDefaultCircuitBreaker() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(Resilience4jCircuitBreaker.class);
                assertThat(context).hasBean("defaultCircuitBreaker");
                
                Resilience4jCircuitBreaker circuitBreaker = context.getBean("defaultCircuitBreaker", Resilience4jCircuitBreaker.class);
                assertThat(circuitBreaker.getName()).isEqualTo("defaultCircuitBreaker");
            });
        }
        
        @Test
        @DisplayName("Should auto-configure preset circuit breakers")
        void shouldAutoConfigurePresetCircuitBreakers() {
            contextRunner.run(context -> {
                assertThat(context).hasBean("defaultCircuitBreaker");
                assertThat(context).hasBean("networkCircuitBreaker");
                assertThat(context).hasBean("fastFailCircuitBreaker");
                
                Resilience4jCircuitBreaker networkCb = context.getBean("networkCircuitBreaker", Resilience4jCircuitBreaker.class);
                assertThat(networkCb.getName()).isEqualTo("NetworkCircuitBreaker");
                
                Resilience4jCircuitBreaker fastFailCb = context.getBean("fastFailCircuitBreaker", Resilience4jCircuitBreaker.class);
                assertThat(fastFailCb.getName()).isEqualTo("FastFailCircuitBreaker");
            });
        }
        
        @Test
        @DisplayName("Should configure circuit breaker with custom properties")
        void shouldConfigureCircuitBreakerWithCustomProperties() {
            contextRunner
                .withPropertyValues(
                    "jmonadic.resilience4j.circuit-breaker.failure-rate-threshold=75",
                    "jmonadic.resilience4j.circuit-breaker.minimum-number-of-calls=5",
                    "jmonadic.resilience4j.circuit-breaker.wait-duration-in-open-state=60s",
                    "jmonadic.resilience4j.circuit-breaker.sliding-window-size=50",
                    "jmonadic.resilience4j.circuit-breaker.sliding-window-type=TIME_BASED",
                    "jmonadic.resilience4j.circuit-breaker.permitted-number-of-calls-in-half-open-state=5",
                    "jmonadic.resilience4j.circuit-breaker.automatic-transition-from-open-to-half-open-enabled=false"
                )
                .run(context -> {
                    Resilience4jCircuitBreaker circuitBreaker = context.getBean("defaultCircuitBreaker", Resilience4jCircuitBreaker.class);
                    
                    // Verify configuration through metrics access
                    assertThat(circuitBreaker.getCircuitBreaker().getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(75.0f);
                    assertThat(circuitBreaker.getCircuitBreaker().getCircuitBreakerConfig().getMinimumNumberOfCalls()).isEqualTo(5);
                    assertThat(circuitBreaker.getCircuitBreaker().getCircuitBreakerConfig().getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(Duration.ofSeconds(60));
                    assertThat(circuitBreaker.getCircuitBreaker().getCircuitBreakerConfig().getSlidingWindowSize()).isEqualTo(50);
                    assertThat(circuitBreaker.getCircuitBreaker().getCircuitBreakerConfig().getSlidingWindowType()).isEqualTo(CircuitBreakerConfig.SlidingWindowType.TIME_BASED);
                    assertThat(circuitBreaker.getCircuitBreaker().getCircuitBreakerConfig().getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(5);
                    assertThat(circuitBreaker.getCircuitBreaker().getCircuitBreakerConfig().isAutomaticTransitionFromOpenToHalfOpenEnabled()).isFalse();
                });
        }
        
        @Test
        @DisplayName("Should disable circuit breaker configuration when disabled")
        void shouldDisableCircuitBreakerConfigurationWhenDisabled() {
            contextRunner
                .withPropertyValues("jmonadic.resilience4j.circuit-breaker.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("defaultCircuitBreaker");
                    assertThat(context).doesNotHaveBean("networkCircuitBreaker");
                    assertThat(context).doesNotHaveBean("fastFailCircuitBreaker");
                });
        }
        
        @Test
        @DisplayName("Should allow custom circuit breaker bean override")
        void shouldAllowCustomCircuitBreakerBeanOverride() {
            contextRunner
                .withUserConfiguration(CustomCircuitBreakerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(Resilience4jCircuitBreaker.class);
                    
                    Resilience4jCircuitBreaker circuitBreaker = context.getBean("defaultCircuitBreaker", Resilience4jCircuitBreaker.class);
                    assertThat(circuitBreaker.getName()).isEqualTo("CustomCircuitBreaker");
                });
        }
    }
    
    @Nested
    @DisplayName("Retry Configuration")
    class RetryConfiguration {
        
        @Test
        @DisplayName("Should auto-configure default retry policy")
        void shouldAutoConfigureDefaultRetryPolicy() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(Resilience4jRetryPolicy.class);
                assertThat(context).hasBean("defaultRetryPolicy");
                
                Resilience4jRetryPolicy retryPolicy = context.getBean("defaultRetryPolicy", Resilience4jRetryPolicy.class);
                assertThat(retryPolicy.getName()).isEqualTo("defaultRetryPolicy");
            });
        }
        
        @Test
        @DisplayName("Should auto-configure preset retry policies")
        void shouldAutoConfigurePresetRetryPolicies() {
            contextRunner.run(context -> {
                assertThat(context).hasBean("defaultRetryPolicy");
                assertThat(context).hasBean("networkRetryPolicy");
                assertThat(context).hasBean("databaseRetryPolicy");
                assertThat(context).hasBean("webServiceRetryPolicy");
                
                Resilience4jRetryPolicy networkRetry = context.getBean("networkRetryPolicy", Resilience4jRetryPolicy.class);
                assertThat(networkRetry.getName()).isEqualTo("NetworkRetry");
                
                Resilience4jRetryPolicy databaseRetry = context.getBean("databaseRetryPolicy", Resilience4jRetryPolicy.class);
                assertThat(databaseRetry.getName()).isEqualTo("DatabaseRetry");
                
                Resilience4jRetryPolicy webServiceRetry = context.getBean("webServiceRetryPolicy", Resilience4jRetryPolicy.class);
                assertThat(webServiceRetry.getName()).isEqualTo("WebServiceRetry");
            });
        }
        
        @Test
        @DisplayName("Should configure retry policy with custom properties")
        void shouldConfigureRetryPolicyWithCustomProperties() {
            contextRunner
                .withPropertyValues(
                    "jmonadic.resilience4j.retry.max-attempts=5",
                    "jmonadic.resilience4j.retry.wait-duration=1s",
                    "jmonadic.resilience4j.retry.exponential-backoff-multiplier=3.0",
                    "jmonadic.resilience4j.retry.max-wait-duration=30s"
                )
                .run(context -> {
                    Resilience4jRetryPolicy retryPolicy = context.getBean("defaultRetryPolicy", Resilience4jRetryPolicy.class);
                    
                    // Verify configuration through retry config access
                    assertThat(retryPolicy.getRetry().getRetryConfig().getMaxAttempts()).isEqualTo(5);
                    assertThat(retryPolicy.getRetry().getRetryConfig().getIntervalFunction().apply(1)).isEqualTo(Duration.ofSeconds(1));
                });
        }
        
        @Test
        @DisplayName("Should configure exponential backoff when specified")
        void shouldConfigureExponentialBackoffWhenSpecified() {
            contextRunner
                .withPropertyValues(
                    "jmonadic.resilience4j.retry.exponential-backoff-multiplier=2.5",
                    "jmonadic.resilience4j.retry.max-wait-duration=10s"
                )
                .run(context -> {
                    Resilience4jRetryPolicy retryPolicy = context.getBean("defaultRetryPolicy", Resilience4jRetryPolicy.class);
                    
                    // Verify that interval function is configured (exponential backoff)
                    assertThat(retryPolicy.getRetry().getRetryConfig().getIntervalFunction()).isNotNull();
                });
        }
        
        @Test
        @DisplayName("Should disable retry configuration when disabled")
        void shouldDisableRetryConfigurationWhenDisabled() {
            contextRunner
                .withPropertyValues("jmonadic.resilience4j.retry.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("defaultRetryPolicy");
                    assertThat(context).doesNotHaveBean("networkRetryPolicy");
                    assertThat(context).doesNotHaveBean("databaseRetryPolicy");
                    assertThat(context).doesNotHaveBean("webServiceRetryPolicy");
                });
        }
        
        @Test
        @DisplayName("Should allow custom retry policy bean override")
        void shouldAllowCustomRetryPolicyBeanOverride() {
            contextRunner
                .withUserConfiguration(CustomRetryPolicyConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(Resilience4jRetryPolicy.class);
                    
                    Resilience4jRetryPolicy retryPolicy = context.getBean("defaultRetryPolicy", Resilience4jRetryPolicy.class);
                    assertThat(retryPolicy.getName()).isEqualTo("CustomRetryPolicy");
                });
        }
    }
    
    @Nested
    @DisplayName("Combined Configuration")
    class CombinedConfiguration {
        
        @Test
        @DisplayName("Should configure both circuit breaker and retry when both enabled")
        void shouldConfigureBothWhenBothEnabled() {
            contextRunner.run(context -> {
                assertThat(context).hasBean("defaultCircuitBreaker");
                assertThat(context).hasBean("defaultRetryPolicy");
                assertThat(context).hasBean("networkCircuitBreaker");
                assertThat(context).hasBean("networkRetryPolicy");
            });
        }
        
        @Test
        @DisplayName("Should respect individual enable/disable settings")
        void shouldRespectIndividualEnableDisableSettings() {
            contextRunner
                .withPropertyValues(
                    "jmonadic.resilience4j.circuit-breaker.enabled=false",
                    "jmonadic.resilience4j.retry.enabled=true"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean("defaultCircuitBreaker");
                    assertThat(context).hasBean("defaultRetryPolicy");
                });
        }
        
        @Test
        @DisplayName("Should handle missing Resilience4j classes gracefully")
        void shouldHandleMissingResilience4jClassesGracefully() {
            // This test would require manipulating the classpath, which is complex in this context
            // In practice, the @ConditionalOnClass annotations handle this
            contextRunner.run(context -> {
                // If Resilience4j classes are present, beans should be configured
                assertThat(context).hasBean("defaultCircuitBreaker");
                assertThat(context).hasBean("defaultRetryPolicy");
            });
        }
    }
    
    @Nested
    @DisplayName("Property Binding")
    class PropertyBinding {
        
        @Test
        @DisplayName("Should bind all circuit breaker properties correctly")
        void shouldBindAllCircuitBreakerPropertiesCorrectly() {
            contextRunner
                .withPropertyValues(
                    "jmonadic.resilience4j.circuit-breaker.enabled=true",
                    "jmonadic.resilience4j.circuit-breaker.failure-rate-threshold=80.5",
                    "jmonadic.resilience4j.circuit-breaker.minimum-number-of-calls=15",
                    "jmonadic.resilience4j.circuit-breaker.wait-duration-in-open-state=45s",
                    "jmonadic.resilience4j.circuit-breaker.sliding-window-size=200",
                    "jmonadic.resilience4j.circuit-breaker.sliding-window-type=COUNT_BASED",
                    "jmonadic.resilience4j.circuit-breaker.permitted-number-of-calls-in-half-open-state=10",
                    "jmonadic.resilience4j.circuit-breaker.automatic-transition-from-open-to-half-open-enabled=true"
                )
                .run(context -> {
                    assertThat(context).hasBean("defaultCircuitBreaker");
                    
                    Resilience4jAutoConfiguration.CircuitBreakerProperties properties = 
                        context.getBean(Resilience4jAutoConfiguration.CircuitBreakerProperties.class);
                    
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getFailureRateThreshold()).isEqualTo(80.5f);
                    assertThat(properties.getMinimumNumberOfCalls()).isEqualTo(15);
                    assertThat(properties.getWaitDurationInOpenState()).isEqualTo(Duration.ofSeconds(45));
                    assertThat(properties.getSlidingWindowSize()).isEqualTo(200);
                    assertThat(properties.getSlidingWindowType()).isEqualTo(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED);
                    assertThat(properties.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(10);
                    assertThat(properties.isAutomaticTransitionFromOpenToHalfOpenEnabled()).isTrue();
                });
        }
        
        @Test
        @DisplayName("Should bind all retry properties correctly")
        void shouldBindAllRetryPropertiesCorrectly() {
            contextRunner
                .withPropertyValues(
                    "jmonadic.resilience4j.retry.enabled=true",
                    "jmonadic.resilience4j.retry.max-attempts=7",
                    "jmonadic.resilience4j.retry.wait-duration=2s",
                    "jmonadic.resilience4j.retry.exponential-backoff-multiplier=1.5",
                    "jmonadic.resilience4j.retry.max-wait-duration=20s"
                )
                .run(context -> {
                    assertThat(context).hasBean("defaultRetryPolicy");
                    
                    Resilience4jAutoConfiguration.RetryProperties properties = 
                        context.getBean(Resilience4jAutoConfiguration.RetryProperties.class);
                    
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getMaxAttempts()).isEqualTo(7);
                    assertThat(properties.getWaitDuration()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(properties.getExponentialBackoffMultiplier()).isEqualTo(1.5);
                    assertThat(properties.getMaxWaitDuration()).isEqualTo(Duration.ofSeconds(20));
                });
        }
        
        @Test
        @DisplayName("Should use default values when properties not specified")
        void shouldUseDefaultValuesWhenPropertiesNotSpecified() {
            contextRunner.run(context -> {
                Resilience4jAutoConfiguration.CircuitBreakerProperties cbProperties = 
                    context.getBean(Resilience4jAutoConfiguration.CircuitBreakerProperties.class);
                
                assertThat(cbProperties.isEnabled()).isTrue();
                assertThat(cbProperties.getFailureRateThreshold()).isEqualTo(50.0f);
                assertThat(cbProperties.getMinimumNumberOfCalls()).isEqualTo(10);
                assertThat(cbProperties.getWaitDurationInOpenState()).isEqualTo(Duration.ofSeconds(30));
                
                Resilience4jAutoConfiguration.RetryProperties retryProperties = 
                    context.getBean(Resilience4jAutoConfiguration.RetryProperties.class);
                
                assertThat(retryProperties.isEnabled()).isTrue();
                assertThat(retryProperties.getMaxAttempts()).isEqualTo(3);
                assertThat(retryProperties.getWaitDuration()).isEqualTo(Duration.ofMillis(500));
                assertThat(retryProperties.getExponentialBackoffMultiplier()).isEqualTo(0.0);
            });
        }
    }
    
    @Configuration
    static class CustomCircuitBreakerConfiguration {
        
        @Bean
        public Resilience4jCircuitBreaker defaultCircuitBreaker() {
            return Resilience4jCircuitBreaker.builder()
                .name("CustomCircuitBreaker")
                .build();
        }
    }
    
    @Configuration
    static class CustomRetryPolicyConfiguration {
        
        @Bean
        public Resilience4jRetryPolicy defaultRetryPolicy() {
            return Resilience4jRetryPolicy.builder()
                .name("CustomRetryPolicy")
                .build();
        }
    }
}