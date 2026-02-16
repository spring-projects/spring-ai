package org.springframework.ai.tool.autoconfigure.annotation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SpringBootTest(classes = EnableToolCallbackAutoRegistrationIT.Config.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EnableToolCallbackAutoRegistrationIT {

	private static final CountDownLatch latch = new CountDownLatch(1);

	@Autowired
	private ApplicationContext context;

	@Test
	void shouldRegisterToolCallbackProviderBean() throws InterruptedException {
		if (!latch.await(3, TimeUnit.SECONDS)) {
			fail("Application context was not fully refreshed in time");
		}

		ToolCallbackProvider provider = context.getBean(MethodToolCallbackProvider.class);

		assertThat(provider.getToolCallbacks()).extracting(FunctionCallback::getName).contains("echo");

		assertThat(provider.getToolCallbacks()).extracting(FunctionCallback::getDescription)
			.contains("This is a description");
	}

	@Configuration
	@EnableToolCallbackAutoRegistration
	static class Config {

		@Bean
		public EchoTool echoTool() {
			return new EchoTool();
		}

		@Bean
		public ApplicationListener<ContextRefreshedEvent> latchReleaser() {
			return event -> latch.countDown();
		}

	}

	static class EchoTool {

		@Tool(description = "This is a description")
		public String echo(String input) {
			return input;
		}

	}

}
