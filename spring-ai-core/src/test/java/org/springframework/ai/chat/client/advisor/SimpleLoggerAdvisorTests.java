/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.chat.client.advisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;

import reactor.core.publisher.Flux;

/**
 * @author Christian Tzolov
 */
@ExtendWith({ MockitoExtension.class, OutputCaptureExtension.class })
@ActiveProfiles("logging-test")
public class SimpleLoggerAdvisorTests {

	@Mock
	ChatModel chatModel;

	@Captor
	ArgumentCaptor<Prompt> promptCaptor;

	@Test
	public void callLogging(CapturedOutput output) {

		when(chatModel.call(promptCaptor.capture()))
			.thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Your answer is ZXY")))));

		var loggerAdvisor = new SimpleLoggerAdvisor();

		var chatClient = ChatClient.builder(chatModel).defaultAdvisors(loggerAdvisor).build();

		var content = chatClient.prompt().user("Please answer my question XYZ").call().content();

		validate(content, output);
	}

	@Test
	public void streamLogging(CapturedOutput output) {

		when(chatModel.stream(promptCaptor.capture())).thenReturn(Flux.generate(
				() -> new ChatResponse(List.of(new Generation(new AssistantMessage("Your answer is ZXY")))),
				(state, sink) -> {
					sink.next(state);
					sink.complete();
					return state;
				}));

		var loggerAdvisor = new SimpleLoggerAdvisor();

		var chatClient = ChatClient.builder(chatModel).defaultAdvisors(loggerAdvisor).build();

		String content = join(chatClient.prompt().user("Please answer my question XYZ").stream().content());

		validate(content, output);
	}

	private void validate(String content, CapturedOutput output) {
		assertThat(content).isEqualTo("Your answer is ZXY");

		UserMessage userMessage = (UserMessage) promptCaptor.getValue().getInstructions().get(0);
		assertThat(userMessage.getContent()).isEqualToIgnoringWhitespace("Please answer my question XYZ");

		assertThat(output.getOut()).contains("request: AdvisedRequest", "userText=Please answer my question XYZ");
		assertThat(output.getOut()).contains("response:", "finishReason");
	}

	private String join(Flux<String> fluxContent) {
		return fluxContent.collectList().block().stream().collect(Collectors.joining());
	}

}
