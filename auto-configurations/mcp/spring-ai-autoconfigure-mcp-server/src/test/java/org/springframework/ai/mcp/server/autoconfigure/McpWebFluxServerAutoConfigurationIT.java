package org.springframework.ai.mcp.server.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.transport.WebFluxSseServerTransportProvider;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.reactive.function.server.RouterFunction;

import static org.assertj.core.api.Assertions.assertThat;

class McpWebFluxServerAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withConfiguration(
			AutoConfigurations.of(McpWebFluxServerAutoConfiguration.class, McpServerAutoConfiguration.class));

	@Test
	void defaultConfiguration() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(WebFluxSseServerTransportProvider.class);
			assertThat(context).hasSingleBean(RouterFunction.class);
		});
	}

	@Test
	void objectMapperConfiguration() {
		this.contextRunner.withBean(ObjectMapper.class, ObjectMapper::new).run(context -> {
			assertThat(context).hasSingleBean(WebFluxSseServerTransportProvider.class);
			assertThat(context).hasSingleBean(RouterFunction.class);
		});
	}

	@Test
	void stdioEnabledConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.stdio=true").run(context -> {
			assertThat(context).doesNotHaveBean(WebFluxSseServerTransportProvider.class);
		});
	}

}
