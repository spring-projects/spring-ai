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
package org.springframework.ai.model.chat.memory.repository.file.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.file.FileChatMemoryRepository;
import org.springframework.ai.model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author John Dahle
 */
@AutoConfiguration(before = ChatMemoryAutoConfiguration.class)
@EnableConfigurationProperties(FileChatMemoryRepositoryProperties.class)
@ConditionalOnProperty(prefix = FileChatMemoryRepositoryProperties.CONFIG_PREFIX, name = "enabled",
		havingValue = "true", matchIfMissing = true)
public class FileChatMemoryRepositoryAutoConfiguration {

	private final FileChatMemoryRepositoryProperties props;

	public FileChatMemoryRepositoryAutoConfiguration(FileChatMemoryRepositoryProperties props) {
		this.props = props;
	}

	@Bean
	@ConditionalOnMissingBean(ChatMemoryRepository.class)
	public FileChatMemoryRepository fileChatMemoryRepository(ObjectMapper objectMapper) {
		Path baseDir = Paths.get(props.getBaseDir());
		return new FileChatMemoryRepository(baseDir, objectMapper);
	}

}
