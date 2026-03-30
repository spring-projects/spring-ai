package org.springframework.ai.autoconfigure.finops;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Spring AI FinOps token-usage observability.
 *
 * Wires a TokenUsageMeteringAdvisor into every ChatClient.Builder in the application
 * context. The advisor intercepts the raw ChatResponse before structured-output parsing
 * discards it, reads the Usage, and publishes Micrometer metrics:
 *
 *   spring.ai.token.usage{model, operation, type=[prompt|completion|total|cached]}
 *   spring.ai.token.cost.estimated.usd{model, tier=[prompt|completion]}
 *
 * Enable via: spring.ai.finops.enabled=true
 *
 * Addresses:
 *   https://github.com/spring-projects/spring-ai/issues/3895
 *   https://github.com/spring-projects/spring-ai/issues/186
 *   https://github.com/spring-projects/spring-ai/issues/1506
 */
@AutoConfiguration
@ConditionalOnClass({ChatClient.class, MeterRegistry.class})
@ConditionalOnProperty(prefix = "spring.ai.finops", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(FinOpsProperties.class)
public class TokenUsageObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TokenUsageMeteringAdvisor tokenUsageMeteringAdvisor(
            MeterRegistry meterRegistry,
            FinOpsProperties properties) {
        return new TokenUsageMeteringAdvisor(meterRegistry, properties);
    }

    /**
     * Post-processes every ChatClient.Builder to install the metering advisor.
     * This is the idiomatic Spring AI extension point -- it runs before the builder
     * is exposed to application code.
     */
    @Bean
    public ChatClient.Builder.BuilderCustomizer finOpsMeteringCustomizer(
            TokenUsageMeteringAdvisor advisor) {
        return builder -> builder.defaultAdvisors(advisor);
    }
}
