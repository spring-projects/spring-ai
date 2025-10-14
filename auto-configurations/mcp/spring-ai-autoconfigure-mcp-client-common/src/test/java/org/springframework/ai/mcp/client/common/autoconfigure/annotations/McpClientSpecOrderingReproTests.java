/*
 * Copyright 2025-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.mcp.client.common.autoconfigure.annotations;

import java.util.List;

import io.modelcontextprotocol.spec.McpSchema.ProgressNotification;
import org.junit.jupiter.api.Test;
import org.springaicommunity.mcp.annotation.McpProgress;
import org.springaicommunity.mcp.method.progress.SyncProgressSpecification;

import org.springframework.ai.mcp.client.common.autoconfigure.annotations.McpClientAnnotationScannerAutoConfiguration.ClientMcpAnnotatedBeans;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduction test for ordering bug where McpClientSpecificationFactoryAutoConfiguration
 * is created before any @Component beans with @McpProgress (or other MCP annotations) are
 * instantiated, resulting in empty specification lists.
 */
class McpClientSpecOrderingReproTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(McpClientAnnotationScannerAutoConfiguration.class,
				McpClientSpecificationFactoryAutoConfiguration.class))
		.withUserConfiguration(ScanConfig.class);

	@Test
	void progressSpecsIncludeScannedComponent_evenWhenCreatedAfterSpecsBean() {
		this.runner.run(ctx -> {
			// 1) Trigger spec list bean creation early
			@SuppressWarnings("unchecked")
			List<SyncProgressSpecification> specs = (List<SyncProgressSpecification>) ctx.getBean("progressSpecs");

			// 2) Now force creation of the scanned @Component (post-processor runs here)
			ctx.getBean(ScannedClientHandlers.class);

			// 3) Registry sees the component…
			ClientMcpAnnotatedBeans registry = ctx.getBean(ClientMcpAnnotatedBeans.class);
			assertThat(registry.getBeansByAnnotation(McpProgress.class)).hasSize(1);

			// 4) Expected behavior: specs reflect newly-registered handler
			// Under the bug, this assertion fails (list stays empty)
			assertThat(specs).hasSize(1);
		});
	}

	@Configuration
	@ComponentScan(basePackageClasses = ScannedClientHandlers.class)
	static class ScanConfig {

	}

	@Component
	@Lazy
	static class ScannedClientHandlers {

		@McpProgress(clients = "server1")
		public void onProgress(ProgressNotification pn) {
		}

	}

}
