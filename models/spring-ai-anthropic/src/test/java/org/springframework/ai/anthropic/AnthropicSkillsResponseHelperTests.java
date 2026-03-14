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

package org.springframework.ai.anthropic;

import java.util.List;
import java.util.Optional;

import com.anthropic.models.messages.Container;
import com.anthropic.models.messages.ContainerUploadBlock;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link AnthropicSkillsResponseHelper}.
 *
 * @author Soby Chacko
 */
@ExtendWith(MockitoExtension.class)
class AnthropicSkillsResponseHelperTests {

	@Test
	void extractFileIdsReturnsEmptyForNullResponse() {
		assertThat(AnthropicSkillsResponseHelper.extractFileIds(null)).isEmpty();
	}

	@Test
	void extractFileIdsReturnsEmptyForNullMetadata() {
		ChatResponse response = mock(ChatResponse.class);
		given(response.getMetadata()).willReturn(null);
		assertThat(AnthropicSkillsResponseHelper.extractFileIds(response)).isEmpty();
	}

	@Test
	void extractFileIdsReturnsEmptyForNonMessageMetadata() {
		ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
		given(metadata.get("anthropic-response")).willReturn("not a message");
		ChatResponse response = mock(ChatResponse.class);
		given(response.getMetadata()).willReturn(metadata);
		assertThat(AnthropicSkillsResponseHelper.extractFileIds(response)).isEmpty();
	}

	@Test
	void extractFileIdsFindsContainerUploadBlocks() {
		ContainerUploadBlock uploadBlock1 = mock(ContainerUploadBlock.class);
		given(uploadBlock1.fileId()).willReturn("file-abc-123");
		ContainerUploadBlock uploadBlock2 = mock(ContainerUploadBlock.class);
		given(uploadBlock2.fileId()).willReturn("file-def-456");

		ContentBlock block1 = mock(ContentBlock.class);
		given(block1.isContainerUpload()).willReturn(true);
		given(block1.asContainerUpload()).willReturn(uploadBlock1);

		ContentBlock block2 = mock(ContentBlock.class);
		given(block2.isContainerUpload()).willReturn(true);
		given(block2.asContainerUpload()).willReturn(uploadBlock2);

		Message message = mock(Message.class);
		given(message.content()).willReturn(List.of(block1, block2));

		ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
		given(metadata.get("anthropic-response")).willReturn(message);
		ChatResponse response = mock(ChatResponse.class);
		given(response.getMetadata()).willReturn(metadata);

		List<String> fileIds = AnthropicSkillsResponseHelper.extractFileIds(response);
		assertThat(fileIds).containsExactly("file-abc-123", "file-def-456");
	}

	@Test
	void extractFileIdsSkipsNonContainerUploadBlocks() {
		ContentBlock textBlock = mock(ContentBlock.class);
		given(textBlock.isContainerUpload()).willReturn(false);

		Message message = mock(Message.class);
		given(message.content()).willReturn(List.of(textBlock));

		ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
		given(metadata.get("anthropic-response")).willReturn(message);
		ChatResponse response = mock(ChatResponse.class);
		given(response.getMetadata()).willReturn(metadata);

		assertThat(AnthropicSkillsResponseHelper.extractFileIds(response)).isEmpty();
	}

	@Test
	void extractContainerIdReturnsNullForNullResponse() {
		assertThat(AnthropicSkillsResponseHelper.extractContainerId(null)).isNull();
	}

	@Test
	void extractContainerIdReturnsIdWhenPresent() {
		Container container = mock(Container.class);
		given(container.id()).willReturn("cntr-abc-123");

		Message message = mock(Message.class);
		given(message.container()).willReturn(Optional.of(container));

		ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
		given(metadata.get("anthropic-response")).willReturn(message);
		ChatResponse response = mock(ChatResponse.class);
		given(response.getMetadata()).willReturn(metadata);

		assertThat(AnthropicSkillsResponseHelper.extractContainerId(response)).isEqualTo("cntr-abc-123");
	}

	@Test
	void extractContainerIdReturnsNullWhenNoContainer() {
		Message message = mock(Message.class);
		given(message.container()).willReturn(Optional.empty());

		ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
		given(metadata.get("anthropic-response")).willReturn(message);
		ChatResponse response = mock(ChatResponse.class);
		given(response.getMetadata()).willReturn(metadata);

		assertThat(AnthropicSkillsResponseHelper.extractContainerId(response)).isNull();
	}

}
