/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.mcp.client.common.autoconfigure;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.aot.generate.ClassNameGenerator;
import org.springframework.aot.generate.DefaultGenerationContext;
import org.springframework.aot.generate.GeneratedFiles;
import org.springframework.aot.generate.InMemoryGeneratedFiles;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.aot.ApplicationContextAotGenerator;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.javapoet.ClassName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AOT tests for {@link McpClientAutoConfiguration}.
 *
 * @author Taewoong Kim
 */
class McpClientAutoConfigurationAotTests {

	@Test
	void generatesRegistrationForNamedClientBean() throws IOException {
		GenericApplicationContext applicationContext = new GenericApplicationContext();
		applicationContext.getEnvironment()
			.getPropertySources()
			.addFirst(new MapPropertySource("test", Map.of("spring.ai.mcp.client.initialized", "false",
					"spring.ai.mcp.client.streamable-http.connections.alpha.url", "http://localhost:8080")));
		new AnnotatedBeanDefinitionReader(applicationContext).register(McpClientAutoConfiguration.class);

		InMemoryGeneratedFiles generatedFiles = new InMemoryGeneratedFiles();
		DefaultGenerationContext generationContext = new DefaultGenerationContext(
				new ClassNameGenerator(ClassName.get(getClass())), generatedFiles);

		try {
			new ApplicationContextAotGenerator().processAheadOfTime(applicationContext, generationContext);
			generationContext.writeGeneratedContent();

			Set<String> generatedSourcePaths = generatedFiles.getGeneratedFiles(GeneratedFiles.Kind.SOURCE).keySet();
			assertThat(generatedSourcePaths)
				.anySatisfy(path -> assertThat(generatedFiles.getGeneratedFileContent(GeneratedFiles.Kind.SOURCE, path))
					.contains("mcpSyncClient_alpha", "setDefaultCandidate(false)",
							"org.springframework.ai.mcp.annotation.spring.McpClient",
							"org.springframework.beans.factory.annotation.Qualifier"));
			assertThat(generatedSourcePaths)
				.allSatisfy(path -> assertThat(generatedFiles.getGeneratedFileContent(GeneratedFiles.Kind.SOURCE, path))
					.doesNotContain("mcpConnectionBeanRegistrar"));
		}
		finally {
			applicationContext.close();
		}
	}

}
