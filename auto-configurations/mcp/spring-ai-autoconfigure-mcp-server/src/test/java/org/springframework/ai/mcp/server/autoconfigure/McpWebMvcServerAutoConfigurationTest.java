package org.springframework.ai.mcp.server.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.servlet.function.RouterFunction;

import static org.assertj.core.api.Assertions.assertThat;

class McpWebMvcServerAutoConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withConfiguration(
			AutoConfigurations.of(McpWebMvcServerAutoConfiguration.class, McpServerAutoConfiguration.class));

	@Test
	void defaultConfiguration() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(WebMvcSseServerTransportProvider.class);
			assertThat(context).hasSingleBean(RouterFunction.class);
		});
	}

	@Test
	void objectMapperConfiguration() {
		this.contextRunner.withBean(ObjectMapper.class, ObjectMapper::new).run(context -> {
			assertThat(context).hasSingleBean(WebMvcSseServerTransportProvider.class);
			assertThat(context).hasSingleBean(RouterFunction.class);
		});
	}

	@Test
	void stdioEnabledConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.stdio=true").run(context -> {
			assertThat(context).doesNotHaveBean(WebMvcSseServerTransportProvider.class);
		});
	}

}
