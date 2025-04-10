/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.model.chat.memory.neo4j.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.neo4j.Neo4jChatMemoryConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Enrico Rampazzo
 * @since 1.0.0
 */
class Neo4jChatMemoryPropertiesTest {

	@Test
	void defaultValues() {
		var props = new Neo4jChatMemoryProperties();
		assertThat(props.getMediaLabel()).isEqualTo(Neo4jChatMemoryConfig.DEFAULT_MEDIA_LABEL);
		assertThat(props.getMessageLabel()).isEqualTo(Neo4jChatMemoryConfig.DEFAULT_MESSAGE_LABEL);
		assertThat(props.getMetadataLabel()).isEqualTo(Neo4jChatMemoryConfig.DEFAULT_METADATA_LABEL);
		assertThat(props.getSessionLabel()).isEqualTo(Neo4jChatMemoryConfig.DEFAULT_SESSION_LABEL);
		assertThat(props.getToolCallLabel()).isEqualTo(Neo4jChatMemoryConfig.DEFAULT_TOOL_CALL_LABEL);
		assertThat(props.getToolResponseLabel()).isEqualTo(Neo4jChatMemoryConfig.DEFAULT_TOOL_RESPONSE_LABEL);
	}

}
