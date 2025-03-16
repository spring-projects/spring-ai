package org.springframework.ai.autoconfigure.mcp.server;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.server.transport.WebFluxSseServerTransport;

class McpWebFluxServerAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    McpWebFluxServerAutoConfiguration.class,
                    JacksonAutoConfiguration.class,
                    TestConfiguration.class)); // Add test configuration

    @Test
    void shouldConfigureWebFluxTransportWithCustomObjectMapper() {
        this.contextRunner.run((context) -> {
            assertThat(context).hasSingleBean(WebFluxSseServerTransport.class);
            assertThat(context).hasSingleBean(RouterFunction.class);
            assertThat(context).hasSingleBean(McpServerProperties.class);

            WebFluxSseServerTransport transport = context.getBean(WebFluxSseServerTransport.class);
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);

            // Verify that the ObjectMapper is configured to ignore unknown properties
            assertThat(objectMapper.getDeserializationConfig().isEnabled(
                    com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
                    .isFalse();

            // Test with a JSON payload containing unknown fields
            String jsonWithUnknownField = """
                    {
                        "tools": ["tool1", "tool2"],
                        "name": "test",
                        "unknownField": "value"
                    }
                    """;

            // This should not throw an exception
            TestMessage message = objectMapper.readValue(jsonWithUnknownField, TestMessage.class);
            assertThat(message.getName()).isEqualTo("test");
        });
    }

    // Test configuration to enable McpServerProperties
    @Configuration
    @EnableConfigurationProperties(McpServerProperties.class)
    static class TestConfiguration {
    }

    // Test class to simulate the actual message structure
    static class TestMessage {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
