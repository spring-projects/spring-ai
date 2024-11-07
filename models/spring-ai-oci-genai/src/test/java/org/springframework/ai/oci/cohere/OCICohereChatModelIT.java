/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.oci.cohere;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.oci.BaseOCIGenAITest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.oci.BaseOCIGenAITest.OCI_CHAT_MODEL_ID_KEY;
import static org.springframework.ai.oci.BaseOCIGenAITest.OCI_COMPARTMENT_ID_KEY;

@EnabledIfEnvironmentVariable(named = OCI_COMPARTMENT_ID_KEY, matches = ".+")
@EnabledIfEnvironmentVariable(named = OCI_CHAT_MODEL_ID_KEY, matches = ".+")
public class OCICohereChatModelIT extends BaseOCIGenAITest {

	private static final ChatModel chatModel = new OCICohereChatModel(getGenerativeAIClient(), options().build());

	@Test
	void chatSimple() {
		String response = chatModel.call("Tell me a random fact about Canada");
		assertThat(response).isNotBlank();
	}

	@Test
	void chatMessages() {
		String response = chatModel.call(new UserMessage("Tell me a random fact about the Arctic Circle"),
				new SystemMessage("You are a helpful assistant"));
		assertThat(response).isNotBlank();
	}

	@Test
	void chatPrompt() {
		ChatResponse response = chatModel.call(new Prompt("What's the difference between Top P and Top K sampling?"));
		assertThat(response).isNotNull();
		assertThat(response.getMetadata().getModel()).isEqualTo(CHAT_MODEL_ID);
		assertThat(response.getResult()).isNotNull();
		assertThat(response.getResult().getOutput().getContent()).isNotBlank();
	}

}
