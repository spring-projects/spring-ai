/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.chat.client;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisorChain;
import org.springframework.ai.chat.client.observation.ChatClientObservationConvention;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.DeveloperMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link DefaultChatClient}.
 *
 * @author Thomas Vitale
 * @author Andres da Silva Santos
 */
class DefaultChatClientTests {

	// Constructor

	@Test
	void whenChatClientRequestIsNullThenThrow() {
		assertThatThrownBy(() -> new DefaultChatClient(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("defaultChatClientRequest cannot be null");
	}

	// ChatClient

	@Test
	void whenPromptThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThat(spec).isNotNull();
	}

	@Test
	void whenPromptContentIsEmptyThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		assertThatThrownBy(() -> chatClient.prompt("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("content cannot be null or empty");
	}

	@Test
	void whenPromptContentThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		DefaultChatClient.DefaultChatClientRequestSpec spec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt("my question");
		assertThat(spec.getMessages()).hasSize(1);
		assertThat(spec.getMessages().get(0).getText()).isEqualTo("my question");
	}

	@Test
	void whenPromptWithMessagesThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		Prompt prompt = new Prompt(new SystemMessage("instructions"), new UserMessage("my question"));
		DefaultChatClient.DefaultChatClientRequestSpec spec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt(prompt);
		assertThat(spec.getMessages()).hasSize(2);
		assertThat(spec.getMessages().get(0).getText()).isEqualTo("instructions");
		assertThat(spec.getMessages().get(1).getText()).isEqualTo("my question");
		assertThat(spec.getChatOptions()).isNull();
	}

	@Test
	void whenPromptWithDeveloperAndMessagesThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		Prompt prompt = new Prompt(new DeveloperMessage("instructions"), new UserMessage("my question"));
		DefaultChatClient.DefaultChatClientRequestSpec spec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt(prompt);
		assertThat(spec.getMessages()).hasSize(2);
		assertThat(spec.getMessages().get(0).getText()).isEqualTo("instructions");
		assertThat(spec.getMessages().get(1).getText()).isEqualTo("my question");
		assertThat(spec.getChatOptions()).isNull();
	}

	@Test
	void whenPromptWithOptionsThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatOptions chatOptions = ChatOptions.builder().build();
		Prompt prompt = new Prompt(List.of(), chatOptions);
		DefaultChatClient.DefaultChatClientRequestSpec spec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt(prompt);
		assertThat(spec.getMessages()).isEmpty();
		assertThat(spec.getChatOptions()).isEqualTo(chatOptions);
	}

	@Test
	void whenMutateChatClientRequest() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		DefaultChatClient.DefaultChatClientRequestSpec spec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt()
			.user("my question");

		ChatClient.Builder newChatClientBuilder = spec.mutate();
		newChatClientBuilder.defaultUser("another question");
		ChatClient newChatClient = newChatClientBuilder.build();
		DefaultChatClient.DefaultChatClientRequestSpec newSpec = (DefaultChatClient.DefaultChatClientRequestSpec) newChatClient
			.prompt();

		assertThat(spec.getUserText()).isEqualTo("my question");
		assertThat(newSpec.getUserText()).isEqualTo("another question");
	}

	// DefaultPromptUserSpec

	@Test
	void buildPromptUserSpec() {
		DefaultChatClient.DefaultPromptUserSpec spec = new DefaultChatClient.DefaultPromptUserSpec();
		assertThat(spec).isNotNull();
		assertThat(spec.media()).isNotNull();
		assertThat(spec.params()).isNotNull();
		assertThat(spec.text()).isNull();
	}

	@Test
	void whenUserMediaIsNullThenThrow() {
		DefaultChatClient.DefaultPromptUserSpec spec = new DefaultChatClient.DefaultPromptUserSpec();
		assertThatThrownBy(() -> spec.media((Media[]) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("media cannot be null");
	}

	@Test
	void whenUserMediaContainsNullElementsThenThrow() {
		DefaultChatClient.DefaultPromptUserSpec spec = new DefaultChatClient.DefaultPromptUserSpec();
		assertThatThrownBy(() -> spec.media(null, (Media) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("media cannot contain null elements");
	}

	@Test
	void whenUserMediaThenReturn() throws MalformedURLException {
		DefaultChatClient.DefaultPromptUserSpec spec = new DefaultChatClient.DefaultPromptUserSpec();
		URI mediaUri = URI.create("http://example.com/image.png");
		spec = (DefaultChatClient.DefaultPromptUserSpec) spec
			.media(Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(mediaUri).build());
		assertThat(spec.media()).hasSize(1);
		assertThat(spec.media().get(0).getMimeType()).isEqualTo(MimeTypeUtils.IMAGE_PNG);
		assertThat(spec.media().get(0).getData()).isEqualTo(mediaUri.toString());
	}

	@Test
	void whenUserMediaMimeTypeIsNullWithUrlThenThrow() throws MalformedURLException {
		DefaultChatClient.DefaultPromptUserSpec spec = new DefaultChatClient.DefaultPromptUserSpec();
		URL mediaUrl = URI.create("http://example.com/image.png").toURL();
		assertThatThrownBy(() -> spec.media(null, mediaUrl)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("mimeType cannot be null");
	}

	@Test
	void whenUserMediaUrlIsNullThenThrow() {
		DefaultChatClient.DefaultPromptUserSpec spec = new DefaultChatClient.DefaultPromptUserSpec();
		assertThatThrownBy(() -> spec.media(MimeTypeUtils.IMAGE_PNG, (URL) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("url cannot be null");
	}

	@Test
	void whenUserMediaMimeTypeAndUrlThenReturn() throws MalformedURLException {
		DefaultChatClient.DefaultPromptUserSpec spec = new DefaultChatClient.DefaultPromptUserSpec();
		URL mediaUrl = URI.create("http://example.com/image.png").toURL();
		spec = (DefaultChatClient.DefaultPromptUserSpec) spec.media(MimeTypeUtils.IMAGE_PNG, mediaUrl);
		assertThat(spec.media()).hasSize(1);
		assertThat(spec.media().get(0).getMimeType()).isEqualTo(MimeTypeUtils.IMAGE_PNG);
		assertThat(spec.media().get(0).getData()).isEqualTo(mediaUrl.toString());
	}

	@Test
	void whenUserMediaMimeTypeIsNullWithResourceThenThrow() throws MalformedURLException {
		DefaultChatClient.DefaultPromptUserSpec spec = new DefaultChatClient.DefaultPromptUserSpec();
		assertThatThrownBy(() -> spec.media(null, new ClassPathResource("image.png")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("mimeType cannot be null");
	}

	@Test
	void whenUserMediaResourceIsNullThenThrow() {
		DefaultChatClient.DefaultPromptUserSpec spec = new DefaultChatClient.DefaultPromptUserSpec();
		assertThatThrownBy(() -> spec.media(MimeTypeUtils.IMAGE_PNG, (Resource) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("resource cannot be null");
	}

	@Test
	void whenUserMediaMimeTypeAndResourceThenReturn() {
		DefaultChatClient.DefaultPromptUserSpec spec = new DefaultChatClient.DefaultPromptUserSpec();
		Resource imageResource = new ClassPathResource("tabby-cat.png");
		spec = (DefaultChatClient.DefaultPromptUserSpec) spec.media(MimeTypeUtils.IMAGE_PNG, imageResource);
		assertThat(spec.media()).hasSize(1);
		assertThat(spec.media().get(0).getMimeType()).isEqualTo(MimeTypeUtils.IMAGE_PNG);
		assertThat(spec.media().get(0).getData()).isNotNull();
	}

	@Test
	void whenUserTextStringIsNullThenThrow() {
		DefaultChatClient.DefaultPromptUserSpec spec = new DefaultChatClient.DefaultPromptUserSpec();
		assertThatThrownBy(() -> spec.text((String) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("text cannot be null or empty");
	}

	@Test
	void whenUserTextStringIsEmptyThenThrow() {
		DefaultChatClient.DefaultPromptUserSpec spec = new DefaultChatClient.DefaultPromptUserSpec();
		assertThatThrownBy(() -> spec.text("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("text cannot be null or empty");
	}

	@Test
	void whenUserTextStringThenReturn() {
		DefaultChatClient.DefaultPromptUserSpec spec = new DefaultChatClient.DefaultPromptUserSpec();
		spec = (DefaultChatClient.DefaultPromptUserSpec) spec.text("my question");
		assertThat(spec.text()).isEqualTo("my question");
	}

	@Test
	void whenUserTextResourceIsNullWithCharsetThenThrow() {
		DefaultChatClient.DefaultPromptUserSpec spec = new DefaultChatClient.DefaultPromptUserSpec();
		assertThatThrownBy(() -> spec.text(null, Charset.defaultCharset())).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("text cannot be null");
	}

	@Test
	void whenUserTextCharsetIsNullWithResourceThenThrow() {
		DefaultChatClient.DefaultPromptUserSpec spec = new DefaultChatClient.DefaultPromptUserSpec();
		Resource textResource = new ClassPathResource("user-prompt.txt");
		assertThatThrownBy(() -> spec.text(textResource, null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("charset cannot be null");
	}

	@Test
	void whenUserTextResourceAndCharsetThenReturn() {
		DefaultChatClient.DefaultPromptUserSpec spec = new DefaultChatClient.DefaultPromptUserSpec();
		Resource textResource = new ClassPathResource("user-prompt.txt");
		spec = (DefaultChatClient.DefaultPromptUserSpec) spec.text(textResource, Charset.defaultCharset());
		assertThat(spec.text()).isEqualTo("my question");
	}

	@Test
	void whenUserTextResourceIsNullThenThrow() {
		DefaultChatClient.DefaultPromptUserSpec spec = new DefaultChatClient.DefaultPromptUserSpec();
		assertThatThrownBy(() -> spec.text((Resource) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("text cannot be null");
	}

	@Test
	void whenUserTextResourceThenReturn() {
		DefaultChatClient.DefaultPromptUserSpec spec = new DefaultChatClient.DefaultPromptUserSpec();
		Resource textResource = new ClassPathResource("user-prompt.txt");
		spec = (DefaultChatClient.DefaultPromptUserSpec) spec.text(textResource);
		assertThat(spec.text()).isEqualTo("my question");
	}

	@Test
	void whenUserParamKeyIsNullThenThrow() {
		DefaultChatClient.DefaultPromptUserSpec spec = new DefaultChatClient.DefaultPromptUserSpec();
		assertThatThrownBy(() -> spec.param(null, "value")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("key cannot be null or empty");
	}

	@Test
	void whenUserParamKeyIsEmptyThenThrow() {
		DefaultChatClient.DefaultPromptUserSpec spec = new DefaultChatClient.DefaultPromptUserSpec();
		assertThatThrownBy(() -> spec.param("", "value")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("key cannot be null or empty");
	}

	@Test
	void whenUserParamValueIsNullThenThrow() {
		DefaultChatClient.DefaultPromptUserSpec spec = new DefaultChatClient.DefaultPromptUserSpec();
		assertThatThrownBy(() -> spec.param("key", null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("value cannot be null");
	}

	@Test
	void whenUserParamKeyValueThenReturn() {
		DefaultChatClient.DefaultPromptUserSpec spec = new DefaultChatClient.DefaultPromptUserSpec();
		spec = (DefaultChatClient.DefaultPromptUserSpec) spec.param("key", "value");
		assertThat(spec.params()).containsEntry("key", "value");
	}

	@Test
	void whenUserParamsIsNullThenThrow() {
		DefaultChatClient.DefaultPromptUserSpec spec = new DefaultChatClient.DefaultPromptUserSpec();
		assertThatThrownBy(() -> spec.params(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("params cannot be null");
	}

	@Test
	void whenUserParamsKeyIsNullThenThrow() {
		DefaultChatClient.DefaultPromptUserSpec spec = new DefaultChatClient.DefaultPromptUserSpec();
		Map<String, Object> params = new HashMap<>();
		params.put(null, "value");
		assertThatThrownBy(() -> spec.params(params)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("param keys cannot contain null elements");
	}

	@Test
	void whenUserParamsValueIsNullThenThrow() {
		DefaultChatClient.DefaultPromptUserSpec spec = new DefaultChatClient.DefaultPromptUserSpec();
		Map<String, Object> params = new HashMap<>();
		params.put("key", null);
		assertThatThrownBy(() -> spec.params(params)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("param values cannot contain null elements");
	}

	@Test
	void whenUserParamsThenReturn() {
		DefaultChatClient.DefaultPromptUserSpec spec = new DefaultChatClient.DefaultPromptUserSpec();
		spec = (DefaultChatClient.DefaultPromptUserSpec) spec.params(Map.of("key", "value"));
		assertThat(spec.params()).containsEntry("key", "value");
	}

	// DefaultPromptSystemSpec

	@Test
	void buildPromptSystemSpec() {
		DefaultChatClient.DefaultPromptSystemSpec spec = new DefaultChatClient.DefaultPromptSystemSpec();
		assertThat(spec).isNotNull();
		assertThat(spec.params()).isNotNull();
		assertThat(spec.text()).isNull();
	}

	@Test
	void whenSystemTextStringIsNullThenThrow() {
		DefaultChatClient.DefaultPromptSystemSpec spec = new DefaultChatClient.DefaultPromptSystemSpec();
		assertThatThrownBy(() -> spec.text((String) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("text cannot be null or empty");
	}

	@Test
	void whenSystemTextStringIsEmptyThenThrow() {
		DefaultChatClient.DefaultPromptSystemSpec spec = new DefaultChatClient.DefaultPromptSystemSpec();
		assertThatThrownBy(() -> spec.text("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("text cannot be null or empty");
	}

	@Test
	void whenSystemTextStringThenReturn() {
		DefaultChatClient.DefaultPromptSystemSpec spec = new DefaultChatClient.DefaultPromptSystemSpec();
		spec = (DefaultChatClient.DefaultPromptSystemSpec) spec.text("instructions");
		assertThat(spec.text()).isEqualTo("instructions");
	}

	@Test
	void whenSystemTextResourceIsNullWithCharsetThenThrow() {
		DefaultChatClient.DefaultPromptSystemSpec spec = new DefaultChatClient.DefaultPromptSystemSpec();
		assertThatThrownBy(() -> spec.text(null, Charset.defaultCharset())).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("text cannot be null");
	}

	@Test
	void whenSystemTextCharsetIsNullWithResourceThenThrow() {
		DefaultChatClient.DefaultPromptSystemSpec spec = new DefaultChatClient.DefaultPromptSystemSpec();
		Resource textResource = new ClassPathResource("system-prompt.txt");
		assertThatThrownBy(() -> spec.text(textResource, null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("charset cannot be null");
	}

	@Test
	void whenSystemTextResourceAndCharsetThenReturn() {
		DefaultChatClient.DefaultPromptSystemSpec spec = new DefaultChatClient.DefaultPromptSystemSpec();
		Resource textResource = new ClassPathResource("system-prompt.txt");
		spec = (DefaultChatClient.DefaultPromptSystemSpec) spec.text(textResource, Charset.defaultCharset());
		assertThat(spec.text()).isEqualTo("instructions");
	}

	@Test
	void whenSystemTextResourceIsNullThenThrow() {
		DefaultChatClient.DefaultPromptSystemSpec spec = new DefaultChatClient.DefaultPromptSystemSpec();
		assertThatThrownBy(() -> spec.text((Resource) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("text cannot be null");
	}

	@Test
	void whenSystemTextResourceThenReturn() {
		DefaultChatClient.DefaultPromptSystemSpec spec = new DefaultChatClient.DefaultPromptSystemSpec();
		Resource textResource = new ClassPathResource("system-prompt.txt");
		spec = (DefaultChatClient.DefaultPromptSystemSpec) spec.text(textResource);
		assertThat(spec.text()).isEqualTo("instructions");
	}

	@Test
	void whenSystemParamKeyIsNullThenThrow() {
		DefaultChatClient.DefaultPromptSystemSpec spec = new DefaultChatClient.DefaultPromptSystemSpec();
		assertThatThrownBy(() -> spec.param(null, "value")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("key cannot be null or empty");
	}

	@Test
	void whenSystemParamKeyIsEmptyThenThrow() {
		DefaultChatClient.DefaultPromptSystemSpec spec = new DefaultChatClient.DefaultPromptSystemSpec();
		assertThatThrownBy(() -> spec.param("", "value")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("key cannot be null or empty");
	}

	@Test
	void whenSystemParamValueIsNullThenThrow() {
		DefaultChatClient.DefaultPromptSystemSpec spec = new DefaultChatClient.DefaultPromptSystemSpec();
		assertThatThrownBy(() -> spec.param("key", null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("value cannot be null");
	}

	@Test
	void whenSystemParamKeyValueThenReturn() {
		DefaultChatClient.DefaultPromptSystemSpec spec = new DefaultChatClient.DefaultPromptSystemSpec();
		spec = (DefaultChatClient.DefaultPromptSystemSpec) spec.param("key", "value");
		assertThat(spec.params()).containsEntry("key", "value");
	}

	@Test
	void whenSystemParamsIsNullThenThrow() {
		DefaultChatClient.DefaultPromptSystemSpec spec = new DefaultChatClient.DefaultPromptSystemSpec();
		assertThatThrownBy(() -> spec.params(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("params cannot be null");
	}

	@Test
	void whenSystemParamsKeyIsNullThenThrow() {
		DefaultChatClient.DefaultPromptSystemSpec spec = new DefaultChatClient.DefaultPromptSystemSpec();
		Map<String, Object> params = new HashMap<>();
		params.put(null, "value");
		assertThatThrownBy(() -> spec.params(params)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("param keys cannot contain null elements");
	}

	@Test
	void whenSystemParamsValueIsNullThenThrow() {
		DefaultChatClient.DefaultPromptSystemSpec spec = new DefaultChatClient.DefaultPromptSystemSpec();
		Map<String, Object> params = new HashMap<>();
		params.put("key", null);
		assertThatThrownBy(() -> spec.params(params)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("param values cannot contain null elements");
	}

	@Test
	void whenSystemParamsThenReturn() {
		DefaultChatClient.DefaultPromptSystemSpec spec = new DefaultChatClient.DefaultPromptSystemSpec();
		spec = (DefaultChatClient.DefaultPromptSystemSpec) spec.params(Map.of("key", "value"));
		assertThat(spec.params()).containsEntry("key", "value");
	}

	// DefaultPromptDeveloperSpec

	@Test
	void buildPromptDeveloperSpec() {
		DefaultChatClient.DefaultPromptDeveloperSpec spec = new DefaultChatClient.DefaultPromptDeveloperSpec();
		assertThat(spec).isNotNull();
		assertThat(spec.params()).isNotNull();
		assertThat(spec.text()).isNull();
	}

	@Test
	void whenDeveloperTextStringIsNullThenThrow() {
		DefaultChatClient.DefaultPromptDeveloperSpec spec = new DefaultChatClient.DefaultPromptDeveloperSpec();
		assertThatThrownBy(() -> spec.text((String) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("text cannot be null or empty");
	}

	@Test
	void whenDeveloperTextStringIsEmptyThenThrow() {
		DefaultChatClient.DefaultPromptDeveloperSpec spec = new DefaultChatClient.DefaultPromptDeveloperSpec();
		assertThatThrownBy(() -> spec.text("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("text cannot be null or empty");
	}

	@Test
	void whenDeveloperTextStringThenReturn() {
		DefaultChatClient.DefaultPromptDeveloperSpec spec = new DefaultChatClient.DefaultPromptDeveloperSpec();
		spec = (DefaultChatClient.DefaultPromptDeveloperSpec) spec.text("developer instructions");
		assertThat(spec.text()).isEqualTo("developer instructions");
	}

	@Test
	void whenDeveloperTextResourceIsNullWithCharsetThenThrow() {
		DefaultChatClient.DefaultPromptDeveloperSpec spec = new DefaultChatClient.DefaultPromptDeveloperSpec();
		assertThatThrownBy(() -> spec.text(null, Charset.defaultCharset())).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("text cannot be null");
	}

	@Test
	void whenDeveloperTextResourceAndCharsetThenReturn() throws Exception {
		Resource textResource = new ClassPathResource("developer-prompt.txt");
		DefaultChatClient.DefaultPromptDeveloperSpec spec = new DefaultChatClient.DefaultPromptDeveloperSpec();
		spec = (DefaultChatClient.DefaultPromptDeveloperSpec) spec.text(textResource, Charset.defaultCharset());
		assertThat(spec.text()).isEqualTo("instructions");
	}

	@Test
	void whenDeveloperTextResourceIsNullThenThrow() {
		DefaultChatClient.DefaultPromptDeveloperSpec spec = new DefaultChatClient.DefaultPromptDeveloperSpec();
		assertThatThrownBy(() -> spec.text((Resource) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("text cannot be null");
	}

	@Test
	void whenDeveloperTextResourceThenReturn() throws Exception {
		Resource textResource = new ClassPathResource("developer-prompt.txt");
		DefaultChatClient.DefaultPromptDeveloperSpec spec = new DefaultChatClient.DefaultPromptDeveloperSpec();
		spec = (DefaultChatClient.DefaultPromptDeveloperSpec) spec.text(textResource);
		assertThat(spec.text()).isEqualTo("instructions");
	}

	@Test
	void whenDeveloperParamKeyIsNullThenThrow() {
		DefaultChatClient.DefaultPromptDeveloperSpec spec = new DefaultChatClient.DefaultPromptDeveloperSpec();
		assertThatThrownBy(() -> spec.param(null, "value")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("key cannot be null or empty");
	}

	@Test
	void whenDeveloperParamKeyIsEmptyThenThrow() {
		DefaultChatClient.DefaultPromptDeveloperSpec spec = new DefaultChatClient.DefaultPromptDeveloperSpec();
		assertThatThrownBy(() -> spec.param("", "value")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("key cannot be null or empty");
	}

	@Test
	void whenDeveloperParamValueIsNullThenThrow() {
		DefaultChatClient.DefaultPromptDeveloperSpec spec = new DefaultChatClient.DefaultPromptDeveloperSpec();
		assertThatThrownBy(() -> spec.param("key", null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("value cannot be null");
	}

	@Test
	void whenDeveloperParamKeyValueThenReturn() {
		DefaultChatClient.DefaultPromptDeveloperSpec spec = new DefaultChatClient.DefaultPromptDeveloperSpec();
		spec = (DefaultChatClient.DefaultPromptDeveloperSpec) spec.param("key", "value");
		assertThat(spec.params()).containsEntry("key", "value");
	}

	@Test
	void whenDeveloperParamsIsNullThenThrow() {
		DefaultChatClient.DefaultPromptDeveloperSpec spec = new DefaultChatClient.DefaultPromptDeveloperSpec();
		assertThatThrownBy(() -> spec.params(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("params cannot be null");
	}

	@Test
	void whenDeveloperParamsContainsNullKeyThenThrow() {
		DefaultChatClient.DefaultPromptDeveloperSpec spec = new DefaultChatClient.DefaultPromptDeveloperSpec();
		Map<String, Object> params = new HashMap<>();
		params.put(null, "value");
		assertThatThrownBy(() -> spec.params(params)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("param keys cannot contain null elements");
	}

	@Test
	void whenDeveloperParamsContainsNullValueThenThrow() {
		DefaultChatClient.DefaultPromptDeveloperSpec spec = new DefaultChatClient.DefaultPromptDeveloperSpec();
		Map<String, Object> params = new HashMap<>();
		params.put("key", null);
		assertThatThrownBy(() -> spec.params(params)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("param values cannot contain null elements");
	}

	@Test
	void whenDeveloperParamsThenReturn() {
		DefaultChatClient.DefaultPromptDeveloperSpec spec = new DefaultChatClient.DefaultPromptDeveloperSpec();
		spec = (DefaultChatClient.DefaultPromptDeveloperSpec) spec.params(Map.of("key", "value"));
		assertThat(spec.params()).containsEntry("key", "value");
	}

	// DefaultAdvisorSpec

	@Test
	void buildAdvisorSpec() {
		DefaultChatClient.DefaultAdvisorSpec spec = new DefaultChatClient.DefaultAdvisorSpec();
		assertThat(spec).isNotNull();
		assertThat(spec.getAdvisors()).isNotNull();
		assertThat(spec.getParams()).isNotNull();
	}

	@Test
	void whenAdvisorParamKeyIsNullThenThrow() {
		DefaultChatClient.DefaultAdvisorSpec spec = new DefaultChatClient.DefaultAdvisorSpec();
		assertThatThrownBy(() -> spec.param(null, "value")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("key cannot be null or empty");
	}

	@Test
	void whenAdvisorParamKeyIsEmptyThenThrow() {
		DefaultChatClient.DefaultAdvisorSpec spec = new DefaultChatClient.DefaultAdvisorSpec();
		assertThatThrownBy(() -> spec.param("", "value")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("key cannot be null or empty");
	}

	@Test
	void whenAdvisorParamValueIsNullThenThrow() {
		DefaultChatClient.DefaultAdvisorSpec spec = new DefaultChatClient.DefaultAdvisorSpec();
		assertThatThrownBy(() -> spec.param("key", null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("value cannot be null");
	}

	@Test
	void whenAdvisorParamKeyValueThenReturn() {
		DefaultChatClient.DefaultAdvisorSpec spec = new DefaultChatClient.DefaultAdvisorSpec();
		spec = (DefaultChatClient.DefaultAdvisorSpec) spec.param("key", "value");
		assertThat(spec.getParams()).containsEntry("key", "value");
	}

	@Test
	void whenAdvisorParamsIsNullThenThrow() {
		DefaultChatClient.DefaultAdvisorSpec spec = new DefaultChatClient.DefaultAdvisorSpec();
		assertThatThrownBy(() -> spec.params(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("params cannot be null");
	}

	@Test
	void whenAdvisorKeyIsNullThenThrow() {
		DefaultChatClient.DefaultAdvisorSpec spec = new DefaultChatClient.DefaultAdvisorSpec();
		Map<String, Object> params = new HashMap<>();
		params.put(null, "value");
		assertThatThrownBy(() -> spec.params(params)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("param keys cannot contain null elements");
	}

	@Test
	void whenAdvisorParamsValueIsNullThenThrow() {
		DefaultChatClient.DefaultAdvisorSpec spec = new DefaultChatClient.DefaultAdvisorSpec();
		Map<String, Object> params = new HashMap<>();
		params.put("key", null);
		assertThatThrownBy(() -> spec.params(params)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("param values cannot contain null elements");
	}

	@Test
	void whenAdvisorParamsThenReturn() {
		DefaultChatClient.DefaultAdvisorSpec spec = new DefaultChatClient.DefaultAdvisorSpec();
		spec = (DefaultChatClient.DefaultAdvisorSpec) spec.params(Map.of("key", "value"));
		assertThat(spec.getParams()).containsEntry("key", "value");
	}

	@Test
	void whenAdvisorsIsNullThenThrow() {
		DefaultChatClient.DefaultAdvisorSpec spec = new DefaultChatClient.DefaultAdvisorSpec();
		assertThatThrownBy(() -> spec.advisors((Advisor[]) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("advisors cannot be null");
	}

	@Test
	void whenAdvisorsContainsNullElementsThenThrow() {
		DefaultChatClient.DefaultAdvisorSpec spec = new DefaultChatClient.DefaultAdvisorSpec();
		assertThatThrownBy(() -> spec.advisors(null, null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("advisors cannot contain null elements");
	}

	@Test
	void whenAdvisorsThenReturn() {
		DefaultChatClient.DefaultAdvisorSpec spec = new DefaultChatClient.DefaultAdvisorSpec();
		Advisor advisor = new SimpleLoggerAdvisor();
		spec = (DefaultChatClient.DefaultAdvisorSpec) spec.advisors(advisor);
		assertThat(spec.getAdvisors()).hasSize(1);
		assertThat(spec.getAdvisors().get(0)).isEqualTo(advisor);
	}

	@Test
	void whenAdvisorListIsNullThenThrow() {
		DefaultChatClient.DefaultAdvisorSpec spec = new DefaultChatClient.DefaultAdvisorSpec();
		assertThatThrownBy(() -> spec.advisors((List<Advisor>) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("advisors cannot be null");
	}

	@Test
	void whenAdvisorListContainsNullElementsThenThrow() {
		DefaultChatClient.DefaultAdvisorSpec spec = new DefaultChatClient.DefaultAdvisorSpec();
		List<Advisor> advisors = new ArrayList<>();
		advisors.add(null);
		assertThatThrownBy(() -> spec.advisors(advisors)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("advisors cannot contain null elements");
	}

	@Test
	void whenAdvisorListThenReturn() {
		DefaultChatClient.DefaultAdvisorSpec spec = new DefaultChatClient.DefaultAdvisorSpec();
		Advisor advisor = new SimpleLoggerAdvisor();
		spec = (DefaultChatClient.DefaultAdvisorSpec) spec.advisors(List.of(advisor));
		assertThat(spec.getAdvisors()).hasSize(1);
		assertThat(spec.getAdvisors().get(0)).isEqualTo(advisor);
	}

	// DefaultCallResponseSpec

	@Test
	void buildCallResponseSpec() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt("question");
		DefaultChatClient.DefaultCallResponseSpec spec = (DefaultChatClient.DefaultCallResponseSpec) chatClientRequestSpec
			.call();
		assertThat(spec).isNotNull();
	}

	@Test
	void buildCallResponseSpecWithNullRequest() {
		assertThatThrownBy(() -> new DefaultChatClient.DefaultCallResponseSpec(null, mock(BaseAdvisorChain.class),
				mock(ObservationRegistry.class), mock(ChatClientObservationConvention.class)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("chatClientRequest cannot be null");
	}

	@Test
	void buildCallResponseSpecWithNullAdvisorChain() {
		assertThatThrownBy(() -> new DefaultChatClient.DefaultCallResponseSpec(mock(ChatClientRequest.class), null,
				mock(ObservationRegistry.class), mock(ChatClientObservationConvention.class)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("advisorChain cannot be null");
	}

	@Test
	void buildCallResponseSpecWithNullObservationRegistry() {
		assertThatThrownBy(() -> new DefaultChatClient.DefaultCallResponseSpec(mock(ChatClientRequest.class),
				mock(BaseAdvisorChain.class), null, mock(ChatClientObservationConvention.class)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("observationRegistry cannot be null");
	}

	@Test
	void buildCallResponseSpecWithNullObservationConvention() {
		assertThatThrownBy(() -> new DefaultChatClient.DefaultCallResponseSpec(mock(ChatClientRequest.class),
				mock(BaseAdvisorChain.class), mock(ObservationRegistry.class), null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("observationConvention cannot be null");
	}

	@Test
	void whenSimplePromptThenChatClientResponse() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.call(promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		ChatClient chatClient = new DefaultChatClientBuilder(chatModel).build();
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt("my question");
		DefaultChatClient.DefaultCallResponseSpec spec = (DefaultChatClient.DefaultCallResponseSpec) chatClientRequestSpec
			.call();

		ChatClientResponse chatClientResponse = spec.chatClientResponse();
		assertThat(chatClientResponse).isNotNull();

		ChatResponse chatResponse = chatClientResponse.chatResponse();
		assertThat(chatResponse).isNotNull();
		assertThat(chatResponse.getResult().getOutput().getText()).isEqualTo("response");

		Prompt actualPrompt = promptCaptor.getValue();
		assertThat(actualPrompt.getInstructions()).hasSize(1);
		assertThat(actualPrompt.getInstructions().get(0).getText()).isEqualTo("my question");
	}

	@Test
	void whenSimplePromptThenChatResponse() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.call(promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		ChatClient chatClient = new DefaultChatClientBuilder(chatModel).build();
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt("my question");
		DefaultChatClient.DefaultCallResponseSpec spec = (DefaultChatClient.DefaultCallResponseSpec) chatClientRequestSpec
			.call();

		ChatResponse chatResponse = spec.chatResponse();
		assertThat(chatResponse).isNotNull();
		assertThat(chatResponse.getResult().getOutput().getText()).isEqualTo("response");

		Prompt actualPrompt = promptCaptor.getValue();
		assertThat(actualPrompt.getInstructions()).hasSize(1);
		assertThat(actualPrompt.getInstructions().get(0).getText()).isEqualTo("my question");
	}

	@Test
	void whenFullPromptThenChatResponse() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.call(promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		ChatClient chatClient = new DefaultChatClientBuilder(chatModel).build();
		Prompt prompt = new Prompt(new SystemMessage("instructions"), new UserMessage("my question"));
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt(prompt);
		DefaultChatClient.DefaultCallResponseSpec spec = (DefaultChatClient.DefaultCallResponseSpec) chatClientRequestSpec
			.call();

		ChatResponse chatResponse = spec.chatResponse();
		assertThat(chatResponse).isNotNull();
		assertThat(chatResponse.getResult().getOutput().getText()).isEqualTo("response");

		Prompt actualPrompt = promptCaptor.getValue();
		assertThat(actualPrompt.getInstructions()).hasSize(2);
		assertThat(actualPrompt.getInstructions().get(0).getText()).isEqualTo("instructions");
		assertThat(actualPrompt.getInstructions().get(1).getText()).isEqualTo("my question");
	}

	@Test
	void whenFullPromptWithDeveloperThenChatResponse() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.call(promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		ChatClient chatClient = new DefaultChatClientBuilder(chatModel).build();
		Prompt prompt = new Prompt(new DeveloperMessage("developer instructions"), new UserMessage("my question"));
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt(prompt);
		DefaultChatClient.DefaultCallResponseSpec spec = (DefaultChatClient.DefaultCallResponseSpec) chatClientRequestSpec
			.call();

		ChatResponse chatResponse = spec.chatResponse();
		assertThat(chatResponse).isNotNull();
		assertThat(chatResponse.getResult().getOutput().getText()).isEqualTo("response");

		Prompt actualPrompt = promptCaptor.getValue();
		assertThat(actualPrompt.getInstructions()).hasSize(2);
		assertThat(actualPrompt.getInstructions().get(0).getText()).isEqualTo("developer instructions");
		assertThat(actualPrompt.getInstructions().get(1).getText()).isEqualTo("my question");
	}

	@Test
	void whenPromptAndUserTextThenChatResponse() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.call(promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		ChatClient chatClient = new DefaultChatClientBuilder(chatModel).build();
		Prompt prompt = new Prompt(new SystemMessage("instructions"), new UserMessage("my question"));
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt(prompt)
			.user("another question");
		DefaultChatClient.DefaultCallResponseSpec spec = (DefaultChatClient.DefaultCallResponseSpec) chatClientRequestSpec
			.call();

		ChatResponse chatResponse = spec.chatResponse();
		assertThat(chatResponse).isNotNull();
		assertThat(chatResponse.getResult().getOutput().getText()).isEqualTo("response");

		Prompt actualPrompt = promptCaptor.getValue();
		assertThat(actualPrompt.getInstructions()).hasSize(3);
		assertThat(actualPrompt.getInstructions().get(0).getText()).isEqualTo("instructions");
		assertThat(actualPrompt.getInstructions().get(1).getText()).isEqualTo("my question");
		assertThat(actualPrompt.getInstructions().get(2).getText()).isEqualTo("another question");
	}

	@Test
	void whenPromptWithDeveloperAndUserTextThenChatResponse() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.call(promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		ChatClient chatClient = new DefaultChatClientBuilder(chatModel).build();
		Prompt prompt = new Prompt(new DeveloperMessage("developer instructions"), new UserMessage("my question"));
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt(prompt);
		DefaultChatClient.DefaultCallResponseSpec spec = (DefaultChatClient.DefaultCallResponseSpec) chatClientRequestSpec
			.call();

		ChatResponse chatResponse = spec.chatResponse();
		assertThat(chatResponse).isNotNull();
		assertThat(chatResponse.getResult().getOutput().getText()).isEqualTo("response");

		Prompt actualPrompt = promptCaptor.getValue();
		assertThat(actualPrompt.getInstructions()).hasSize(2);
		assertThat(actualPrompt.getInstructions().get(0).getText()).isEqualTo("developer instructions");
		assertThat(actualPrompt.getInstructions().get(1).getText()).isEqualTo("my question");
	}

	@Test
	void whenUserTextAndMessagesThenChatResponse() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.call(promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		ChatClient chatClient = new DefaultChatClientBuilder(chatModel).build();
		List<Message> messages = List.of(new SystemMessage("instructions"), new UserMessage("my question"));
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt()
			.user("another question")
			.messages(messages);
		DefaultChatClient.DefaultCallResponseSpec spec = (DefaultChatClient.DefaultCallResponseSpec) chatClientRequestSpec
			.call();

		ChatResponse chatResponse = spec.chatResponse();
		assertThat(chatResponse).isNotNull();
		assertThat(chatResponse.getResult().getOutput().getText()).isEqualTo("response");

		Prompt actualPrompt = promptCaptor.getValue();
		assertThat(actualPrompt.getInstructions()).hasSize(3);
		assertThat(actualPrompt.getInstructions().get(0).getText()).isEqualTo("instructions");
		assertThat(actualPrompt.getInstructions().get(1).getText()).isEqualTo("my question");
		assertThat(actualPrompt.getInstructions().get(2).getText()).isEqualTo("another question");
	}

	@Test
	void whenDeveloperMessageAndMessagesThenChatResponse() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.call(promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("response")))));

		ChatClient chatClient = new DefaultChatClientBuilder(chatModel).build();
		List<Message> messages = List.of(new DeveloperMessage("developer instructions"), new UserMessage("my question"),
				new UserMessage("additional question"));
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt()
			.user("additional user message")
			.messages(messages);
		DefaultChatClient.DefaultCallResponseSpec spec = (DefaultChatClient.DefaultCallResponseSpec) chatClientRequestSpec
			.call();

		ChatResponse chatResponse = spec.chatResponse();
		assertThat(chatResponse).isNotNull();
		assertThat(chatResponse.getResult().getOutput().getText()).isEqualTo("response");

		Prompt actualPrompt = promptCaptor.getValue();
		assertThat(actualPrompt.getInstructions()).hasSize(4);
		assertThat(actualPrompt.getInstructions().get(0).getText()).isEqualTo("developer instructions");
		assertThat(actualPrompt.getInstructions().get(1).getText()).isEqualTo("my question");
		assertThat(actualPrompt.getInstructions().get(2).getText()).isEqualTo("additional question");
		assertThat(actualPrompt.getInstructions().get(3).getText()).isEqualTo("additional user message");
	}

	@Test
	void whenChatResponseIsNull() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.call(promptCaptor.capture())).willReturn(null);

		ChatClient chatClient = new DefaultChatClientBuilder(chatModel).build();
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt("my question");
		DefaultChatClient.DefaultCallResponseSpec spec = (DefaultChatClient.DefaultCallResponseSpec) chatClientRequestSpec
			.call();

		ChatResponse chatResponse = spec.chatResponse();
		assertThat(chatResponse).isNull();
	}

	@Test
	void whenChatResponseContentIsNull() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.call(promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(null)))));

		ChatClient chatClient = new DefaultChatClientBuilder(chatModel).build();
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt("my question");
		DefaultChatClient.DefaultCallResponseSpec spec = (DefaultChatClient.DefaultCallResponseSpec) chatClientRequestSpec
			.call();

		String content = spec.content();
		assertThat(content).isNull();
	}

	@Test
	void whenResponseEntityWithParameterizedTypeIsNull() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt("my question");
		DefaultChatClient.DefaultCallResponseSpec spec = (DefaultChatClient.DefaultCallResponseSpec) chatClientRequestSpec
			.call();

		assertThatThrownBy(() -> spec.responseEntity((ParameterizedTypeReference<?>) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("type cannot be null");
	}

	@Test
	void whenResponseEntityWithParameterizedTypeAndChatResponseContentNull() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.call(promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(null)))));

		ChatClient chatClient = new DefaultChatClientBuilder(chatModel).build();
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt("my question");
		DefaultChatClient.DefaultCallResponseSpec spec = (DefaultChatClient.DefaultCallResponseSpec) chatClientRequestSpec
			.call();

		ResponseEntity<ChatResponse, List<String>> responseEntity = spec
			.responseEntity(new ParameterizedTypeReference<>() {
			});
		assertThat(responseEntity).isNotNull();
		assertThat(responseEntity.response()).isNotNull();
		assertThat(responseEntity.entity()).isNull();
	}

	@Test
	void whenResponseEntityWithParameterizedType() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.call(promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("""
					[
						{ "name": "James Bond" },
						{ "name": "Ethan Hunt" },
						{ "name": "Jason Bourne" }
					]
					""")))));

		ChatClient chatClient = new DefaultChatClientBuilder(chatModel).build();
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt("my question");
		DefaultChatClient.DefaultCallResponseSpec spec = (DefaultChatClient.DefaultCallResponseSpec) chatClientRequestSpec
			.call();

		ResponseEntity<ChatResponse, List<Person>> responseEntity = spec
			.responseEntity(new ParameterizedTypeReference<>() {
			});
		assertThat(responseEntity.response()).isNotNull();
		assertThat(responseEntity.entity()).hasSize(3);
	}

	@Test
	void whenResponseEntityWithConverterIsNull() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt("my question");
		DefaultChatClient.DefaultCallResponseSpec spec = (DefaultChatClient.DefaultCallResponseSpec) chatClientRequestSpec
			.call();

		assertThatThrownBy(() -> spec.responseEntity((StructuredOutputConverter<?>) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("structuredOutputConverter cannot be null");
	}

	@Test
	void whenResponseEntityWithConverterAndChatResponseContentNull() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.call(promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(null)))));

		ChatClient chatClient = new DefaultChatClientBuilder(chatModel).build();
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt("my question");
		DefaultChatClient.DefaultCallResponseSpec spec = (DefaultChatClient.DefaultCallResponseSpec) chatClientRequestSpec
			.call();

		ResponseEntity<ChatResponse, List<String>> responseEntity = spec
			.responseEntity(new ListOutputConverter(new DefaultConversionService()));
		assertThat(responseEntity.response()).isNotNull();
		assertThat(responseEntity.entity()).isNull();
	}

	@Test
	void whenResponseEntityWithConverter() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.call(promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("""
					James Bond, Ethan Hunt, Jason Bourne
					""")))));

		ChatClient chatClient = new DefaultChatClientBuilder(chatModel).build();
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt("my question");
		DefaultChatClient.DefaultCallResponseSpec spec = (DefaultChatClient.DefaultCallResponseSpec) chatClientRequestSpec
			.call();

		ResponseEntity<ChatResponse, List<String>> responseEntity = spec
			.responseEntity(new ListOutputConverter(new DefaultConversionService()));
		assertThat(responseEntity.response()).isNotNull();
		assertThat(responseEntity.entity()).hasSize(3);
	}

	@Test
	void whenResponseEntityWithTypeIsNull() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt("my question");
		DefaultChatClient.DefaultCallResponseSpec spec = (DefaultChatClient.DefaultCallResponseSpec) chatClientRequestSpec
			.call();

		assertThatThrownBy(() -> spec.responseEntity((Class) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("type cannot be null");
	}

	@Test
	void whenResponseEntityWithTypeAndChatResponseContentNull() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.call(promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(null)))));

		ChatClient chatClient = new DefaultChatClientBuilder(chatModel).build();
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt("my question");
		DefaultChatClient.DefaultCallResponseSpec spec = (DefaultChatClient.DefaultCallResponseSpec) chatClientRequestSpec
			.call();

		ResponseEntity<ChatResponse, String> responseEntity = spec.responseEntity(String.class);
		assertThat(responseEntity.response()).isNotNull();
		assertThat(responseEntity.entity()).isNull();
	}

	@Test
	void whenResponseEntityWithType() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.call(promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("""
					{ "name": "James Bond" }
					""")))));

		ChatClient chatClient = new DefaultChatClientBuilder(chatModel).build();
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt("my question");
		DefaultChatClient.DefaultCallResponseSpec spec = (DefaultChatClient.DefaultCallResponseSpec) chatClientRequestSpec
			.call();

		ResponseEntity<ChatResponse, Person> responseEntity = spec.responseEntity(Person.class);
		assertThat(responseEntity.response()).isNotNull();
		assertThat(responseEntity.entity()).isNotNull();
		assertThat(responseEntity.entity().name).isEqualTo("James Bond");
	}

	@Test
	void whenEntityWithParameterizedTypeIsNull() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt("my question");
		DefaultChatClient.DefaultCallResponseSpec spec = (DefaultChatClient.DefaultCallResponseSpec) chatClientRequestSpec
			.call();

		assertThatThrownBy(() -> spec.entity((ParameterizedTypeReference<?>) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("type cannot be null");
	}

	@Test
	void whenEntityWithParameterizedTypeAndChatResponseContentNull() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.call(promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(null)))));

		ChatClient chatClient = new DefaultChatClientBuilder(chatModel).build();
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt("my question");
		DefaultChatClient.DefaultCallResponseSpec spec = (DefaultChatClient.DefaultCallResponseSpec) chatClientRequestSpec
			.call();

		List<String> entity = spec.entity(new ParameterizedTypeReference<>() {
		});
		assertThat(entity).isNull();
	}

	@Test
	void whenEntityWithParameterizedType() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.call(promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("""
					[
						{ "name": "James Bond" },
						{ "name": "Ethan Hunt" },
						{ "name": "Jason Bourne" }
					]
					""")))));

		ChatClient chatClient = new DefaultChatClientBuilder(chatModel).build();
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt("my question");
		DefaultChatClient.DefaultCallResponseSpec spec = (DefaultChatClient.DefaultCallResponseSpec) chatClientRequestSpec
			.call();

		List<Person> entity = spec.entity(new ParameterizedTypeReference<>() {
		});
		assertThat(entity).hasSize(3);
	}

	@Test
	void whenEntityWithConverterIsNull() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt("my question");
		DefaultChatClient.DefaultCallResponseSpec spec = (DefaultChatClient.DefaultCallResponseSpec) chatClientRequestSpec
			.call();

		assertThatThrownBy(() -> spec.entity((StructuredOutputConverter<?>) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("structuredOutputConverter cannot be null");
	}

	@Test
	void whenEntityWithConverterAndChatResponseContentNull() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt("my question");
		DefaultChatClient.DefaultCallResponseSpec spec = (DefaultChatClient.DefaultCallResponseSpec) chatClientRequestSpec
			.call();

		List<String> entity = spec.entity(new ListOutputConverter(new DefaultConversionService()));
		assertThat(entity).isNull();
	}

	@Test
	void whenEntityWithConverter() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.call(promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("""
					James Bond, Ethan Hunt, Jason Bourne
					""")))));

		ChatClient chatClient = new DefaultChatClientBuilder(chatModel).build();
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt("my question");
		DefaultChatClient.DefaultCallResponseSpec spec = (DefaultChatClient.DefaultCallResponseSpec) chatClientRequestSpec
			.call();

		List<String> entity = spec.entity(new ListOutputConverter(new DefaultConversionService()));
		assertThat(entity).hasSize(3);
	}

	@Test
	void whenEntityWithTypeIsNull() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt("my question");
		DefaultChatClient.DefaultCallResponseSpec spec = (DefaultChatClient.DefaultCallResponseSpec) chatClientRequestSpec
			.call();

		assertThatThrownBy(() -> spec.entity((Class<?>) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("type cannot be null");
	}

	@Test
	void whenEntityWithTypeAndChatResponseContentNull() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.call(promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(null)))));

		ChatClient chatClient = new DefaultChatClientBuilder(chatModel).build();
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt("my question");
		DefaultChatClient.DefaultCallResponseSpec spec = (DefaultChatClient.DefaultCallResponseSpec) chatClientRequestSpec
			.call();

		String entity = spec.entity(String.class);
		assertThat(entity).isNull();
	}

	@Test
	void whenEntityWithType() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.call(promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("""
					{ "name": "James Bond" }
					""")))));

		ChatClient chatClient = new DefaultChatClientBuilder(chatModel).build();
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt("my question");
		DefaultChatClient.DefaultCallResponseSpec spec = (DefaultChatClient.DefaultCallResponseSpec) chatClientRequestSpec
			.call();

		Person entity = spec.entity(Person.class);
		assertThat(entity).isNotNull();
		assertThat(entity.name()).isEqualTo("James Bond");
	}

	// DefaultStreamResponseSpec

	@Test
	void buildStreamResponseSpec() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt("question");
		DefaultChatClient.DefaultStreamResponseSpec spec = (DefaultChatClient.DefaultStreamResponseSpec) chatClientRequestSpec
			.stream();
		assertThat(spec).isNotNull();
	}

	@Test
	void buildStreamResponseSpecWithNullRequest() {
		assertThatThrownBy(() -> new DefaultChatClient.DefaultStreamResponseSpec(null, mock(BaseAdvisorChain.class),
				mock(ObservationRegistry.class), mock(ChatClientObservationConvention.class)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("chatClientRequest cannot be null");
	}

	@Test
	void buildStreamResponseSpecWithNullAdvisorChain() {
		assertThatThrownBy(() -> new DefaultChatClient.DefaultStreamResponseSpec(mock(ChatClientRequest.class), null,
				mock(ObservationRegistry.class), mock(ChatClientObservationConvention.class)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("advisorChain cannot be null");
	}

	@Test
	void buildStreamResponseSpecWithNullObservationRegistry() {
		assertThatThrownBy(() -> new DefaultChatClient.DefaultStreamResponseSpec(mock(ChatClientRequest.class),
				mock(BaseAdvisorChain.class), null, mock(ChatClientObservationConvention.class)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("observationRegistry cannot be null");
	}

	@Test
	void buildStreamResponseSpecWithNullObservationConvention() {
		assertThatThrownBy(() -> new DefaultChatClient.DefaultStreamResponseSpec(mock(ChatClientRequest.class),
				mock(BaseAdvisorChain.class), mock(ObservationRegistry.class), null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("observationConvention cannot be null");
	}

	@Test
	void whenSimplePromptThenFluxChatClientResponse() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.stream(promptCaptor.capture()))
			.willReturn(Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("response"))))));

		ChatClient chatClient = new DefaultChatClientBuilder(chatModel).build();
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt("my question");
		DefaultChatClient.DefaultStreamResponseSpec spec = (DefaultChatClient.DefaultStreamResponseSpec) chatClientRequestSpec
			.stream();

		ChatClientResponse chatClientResponse = spec.chatClientResponse().blockLast();
		assertThat(chatClientResponse).isNotNull();

		ChatResponse chatResponse = chatClientResponse.chatResponse();
		assertThat(chatResponse).isNotNull();
		assertThat(chatResponse.getResult().getOutput().getText()).isEqualTo("response");

		Prompt actualPrompt = promptCaptor.getValue();
		assertThat(actualPrompt.getInstructions()).hasSize(1);
		assertThat(actualPrompt.getInstructions().get(0).getText()).isEqualTo("my question");
	}

	@Test
	void whenSimplePromptThenFluxChatResponse() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.stream(promptCaptor.capture()))
			.willReturn(Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("response"))))));

		ChatClient chatClient = new DefaultChatClientBuilder(chatModel).build();
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt("my question");
		DefaultChatClient.DefaultStreamResponseSpec spec = (DefaultChatClient.DefaultStreamResponseSpec) chatClientRequestSpec
			.stream();

		ChatResponse chatResponse = spec.chatResponse().blockLast();
		assertThat(chatResponse).isNotNull();
		assertThat(chatResponse.getResult().getOutput().getText()).isEqualTo("response");

		Prompt actualPrompt = promptCaptor.getValue();
		assertThat(actualPrompt.getInstructions()).hasSize(1);
		assertThat(actualPrompt.getInstructions().get(0).getText()).isEqualTo("my question");
	}

	@Test
	void whenFullPromptThenFluxChatResponse() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.stream(promptCaptor.capture()))
			.willReturn(Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("response"))))));

		ChatClient chatClient = new DefaultChatClientBuilder(chatModel).build();
		Prompt prompt = new Prompt(new SystemMessage("instructions"), new UserMessage("my question"));
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt(prompt);
		DefaultChatClient.DefaultStreamResponseSpec spec = (DefaultChatClient.DefaultStreamResponseSpec) chatClientRequestSpec
			.stream();

		ChatResponse chatResponse = spec.chatResponse().blockLast();
		assertThat(chatResponse).isNotNull();
		assertThat(chatResponse.getResult().getOutput().getText()).isEqualTo("response");

		Prompt actualPrompt = promptCaptor.getValue();
		assertThat(actualPrompt.getInstructions()).hasSize(2);
		assertThat(actualPrompt.getInstructions().get(0).getText()).isEqualTo("instructions");
		assertThat(actualPrompt.getInstructions().get(1).getText()).isEqualTo("my question");
	}

	@Test
	void whenPromptAndUserTextThenFluxChatResponse() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.stream(promptCaptor.capture()))
			.willReturn(Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("response"))))));

		ChatClient chatClient = new DefaultChatClientBuilder(chatModel).build();
		Prompt prompt = new Prompt(new SystemMessage("instructions"), new UserMessage("my question"));
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt(prompt)
			.user("another question");
		DefaultChatClient.DefaultStreamResponseSpec spec = (DefaultChatClient.DefaultStreamResponseSpec) chatClientRequestSpec
			.stream();

		ChatResponse chatResponse = spec.chatResponse().blockLast();
		assertThat(chatResponse).isNotNull();
		assertThat(chatResponse.getResult().getOutput().getText()).isEqualTo("response");

		Prompt actualPrompt = promptCaptor.getValue();
		assertThat(actualPrompt.getInstructions()).hasSize(3);
		assertThat(actualPrompt.getInstructions().get(0).getText()).isEqualTo("instructions");
		assertThat(actualPrompt.getInstructions().get(1).getText()).isEqualTo("my question");
		assertThat(actualPrompt.getInstructions().get(2).getText()).isEqualTo("another question");
	}

	@Test
	void whenUserTextAndMessagesThenFluxChatResponse() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.stream(promptCaptor.capture()))
			.willReturn(Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("response"))))));

		ChatClient chatClient = new DefaultChatClientBuilder(chatModel).build();
		List<Message> messages = List.of(new SystemMessage("instructions"), new UserMessage("my question"));
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt()
			.user("another question")
			.messages(messages);

		DefaultChatClient.DefaultStreamResponseSpec spec = (DefaultChatClient.DefaultStreamResponseSpec) chatClientRequestSpec
			.stream();

		ChatResponse chatResponse = spec.chatResponse().blockLast();
		assertThat(chatResponse).isNotNull();
		assertThat(chatResponse.getResult().getOutput().getText()).isEqualTo("response");

		Prompt actualPrompt = promptCaptor.getValue();
		assertThat(actualPrompt.getInstructions()).hasSize(3);
		assertThat(actualPrompt.getInstructions().get(0).getText()).isEqualTo("instructions");
		assertThat(actualPrompt.getInstructions().get(1).getText()).isEqualTo("my question");
		assertThat(actualPrompt.getInstructions().get(2).getText()).isEqualTo("another question");
	}

	@Test
	void whenFullPromptWithDeveloperThenFluxChatResponse() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.stream(promptCaptor.capture()))
			.willReturn(Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("response"))))));

		ChatClient chatClient = new DefaultChatClientBuilder(chatModel).build();
		Prompt prompt = new Prompt(new DeveloperMessage("developer instructions"), new UserMessage("my question"));
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt(prompt);
		DefaultChatClient.DefaultStreamResponseSpec spec = (DefaultChatClient.DefaultStreamResponseSpec) chatClientRequestSpec
			.stream();

		ChatResponse chatResponse = spec.chatResponse().blockLast();
		assertThat(chatResponse).isNotNull();
		assertThat(chatResponse.getResult().getOutput().getText()).isEqualTo("response");

		Prompt actualPrompt = promptCaptor.getValue();
		assertThat(actualPrompt.getInstructions()).hasSize(2);
		assertThat(actualPrompt.getInstructions().get(0).getText()).isEqualTo("developer instructions");
		assertThat(actualPrompt.getInstructions().get(1).getText()).isEqualTo("my question");
	}

	@Test
	void whenPromptAndUserTextWithDeveloperThenFluxChatResponse() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.stream(promptCaptor.capture()))
			.willReturn(Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("response"))))));

		ChatClient chatClient = new DefaultChatClientBuilder(chatModel).build();
		Prompt prompt = new Prompt(new DeveloperMessage("developer instructions"), new UserMessage("my question"));
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt(prompt)
			.user("another question");
		DefaultChatClient.DefaultStreamResponseSpec spec = (DefaultChatClient.DefaultStreamResponseSpec) chatClientRequestSpec
			.stream();

		ChatResponse chatResponse = spec.chatResponse().blockLast();
		assertThat(chatResponse).isNotNull();
		assertThat(chatResponse.getResult().getOutput().getText()).isEqualTo("response");

		Prompt actualPrompt = promptCaptor.getValue();
		assertThat(actualPrompt.getInstructions()).hasSize(3);
		assertThat(actualPrompt.getInstructions().get(0).getText()).isEqualTo("developer instructions");
		assertThat(actualPrompt.getInstructions().get(1).getText()).isEqualTo("my question");
		assertThat(actualPrompt.getInstructions().get(2).getText()).isEqualTo("another question");
	}

	@Test
	void whenUserTextAndMessagesWithDeveloperThenFluxChatResponse() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.stream(promptCaptor.capture()))
			.willReturn(Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("response"))))));

		ChatClient chatClient = new DefaultChatClientBuilder(chatModel).build();
		List<Message> messages = List.of(new DeveloperMessage("developer instructions"),
				new UserMessage("my question"));
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt()
			.user("another question")
			.messages(messages);

		DefaultChatClient.DefaultStreamResponseSpec spec = (DefaultChatClient.DefaultStreamResponseSpec) chatClientRequestSpec
			.stream();

		ChatResponse chatResponse = spec.chatResponse().blockLast();

		assertThat(chatResponse).isNotNull();
		assertThat(chatResponse.getResult().getOutput().getText()).isEqualTo("response");

		Prompt actualPrompt = promptCaptor.getValue();
		assertThat(actualPrompt.getInstructions()).hasSize(3);
		assertThat(actualPrompt.getInstructions().get(0).getText()).isEqualTo("developer instructions");
		assertThat(actualPrompt.getInstructions().get(1).getText()).isEqualTo("my question");
	}

	@Test
	void whenChatResponseContentIsNullThenReturnFlux() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.stream(promptCaptor.capture()))
			.willReturn(Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage(null))))));

		ChatClient chatClient = new DefaultChatClientBuilder(chatModel).build();
		DefaultChatClient.DefaultChatClientRequestSpec chatClientRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) chatClient
			.prompt("my question");
		DefaultChatClient.DefaultStreamResponseSpec spec = (DefaultChatClient.DefaultStreamResponseSpec) chatClientRequestSpec
			.stream();

		String content = spec.content().blockLast();
		assertThat(content).isNull();
	}

	// DefaultChatClientRequestSpec

	@Test
	void buildChatClientRequestSpec() {
		ChatModel chatModel = mock(ChatModel.class);
		DefaultChatClient.DefaultChatClientRequestSpec spec = new DefaultChatClient.DefaultChatClientRequestSpec(
				chatModel, null, Map.of(), null, Map.of(), null, Map.of(), List.of(), List.of(), List.of(), List.of(),
				null, List.of(), Map.of(), ObservationRegistry.NOOP, null, Map.of(), null);
		assertThat(spec).isNotNull();
	}

	@Test
	void whenChatModelIsNullThenThrow() {
		assertThatThrownBy(() -> new DefaultChatClient.DefaultChatClientRequestSpec(null, null, Map.of(), null,
				Map.of(), null, Map.of(), List.of(), List.of(), List.of(), List.of(), null, List.of(), Map.of(),
				ObservationRegistry.NOOP, null, Map.of(), null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("chatModel cannot be null");
	}

	@Test
	void whenObservationRegistryIsNullThenThrow() {
		assertThatThrownBy(() -> new DefaultChatClient.DefaultChatClientRequestSpec(mock(ChatModel.class), null,
				Map.of(), null, Map.of(), null, Map.of(), List.of(), List.of(), List.of(), List.of(), null, List.of(),
				Map.of(), null, null, Map.of(), null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("observationRegistry cannot be null");
	}

	@Test
	void whenAdvisorConsumerIsNullThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThatThrownBy(() -> spec.advisors((Consumer<ChatClient.AdvisorSpec>) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("consumer cannot be null");
	}

	@Test
	void whenAdvisorConsumerThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		Advisor loggerAdvisor = new SimpleLoggerAdvisor();
		spec = spec.advisors(advisor -> advisor.advisors(loggerAdvisor).param("topic", "AI"));
		DefaultChatClient.DefaultChatClientRequestSpec defaultSpec = (DefaultChatClient.DefaultChatClientRequestSpec) spec;
		assertThat(defaultSpec.getAdvisors()).contains(loggerAdvisor);
		assertThat(defaultSpec.getAdvisorParams()).containsEntry("topic", "AI");
	}

	@Test
	void whenRequestAdvisorsWithNullElementsThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThatThrownBy(() -> spec.advisors((Advisor) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("advisors cannot contain null elements");
	}

	@Test
	void whenRequestAdvisorsThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		Advisor advisor = new SimpleLoggerAdvisor();
		spec = spec.advisors(advisor);
		DefaultChatClient.DefaultChatClientRequestSpec defaultSpec = (DefaultChatClient.DefaultChatClientRequestSpec) spec;
		assertThat(defaultSpec.getAdvisors()).contains(advisor);
	}

	@Test
	void whenRequestAdvisorListIsNullThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThatThrownBy(() -> spec.advisors((List<Advisor>) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("advisors cannot be null");
	}

	@Test
	void whenRequestAdvisorListWithNullElementsThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		List<Advisor> advisors = new ArrayList<>();
		advisors.add(null);
		assertThatThrownBy(() -> spec.advisors(advisors)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("advisors cannot contain null elements");
	}

	@Test
	void whenRequestAdvisorListThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		List<Advisor> advisors = List.of(new SimpleLoggerAdvisor());
		spec = spec.advisors(advisors);
		DefaultChatClient.DefaultChatClientRequestSpec defaultSpec = (DefaultChatClient.DefaultChatClientRequestSpec) spec;
		assertThat(defaultSpec.getAdvisors()).containsAll(advisors);
	}

	@Test
	void whenMessagesWithNullElementsThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThatThrownBy(() -> spec.messages((Message) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("messages cannot contain null elements");
	}

	@Test
	void whenMessagesThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		Message message = new UserMessage("question");
		spec = spec.messages(message);
		DefaultChatClient.DefaultChatClientRequestSpec defaultSpec = (DefaultChatClient.DefaultChatClientRequestSpec) spec;
		assertThat(defaultSpec.getMessages()).contains(message);
	}

	@Test
	void whenMessageListIsNullThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThatThrownBy(() -> spec.messages((List<Message>) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("messages cannot be null");
	}

	@Test
	void whenMessageListWithNullElementsThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		List<Message> messages = new ArrayList<>();
		messages.add(null);
		assertThatThrownBy(() -> spec.messages(messages)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("messages cannot contain null elements");
	}

	@Test
	void whenMessageListThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		List<Message> messages = List.of(new UserMessage("question"));
		spec = spec.messages(messages);
		DefaultChatClient.DefaultChatClientRequestSpec defaultSpec = (DefaultChatClient.DefaultChatClientRequestSpec) spec;
		assertThat(defaultSpec.getMessages()).containsAll(messages);
	}

	@Test
	void whenOptionsIsNullThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThatThrownBy(() -> spec.options(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("options cannot be null");
	}

	@Test
	void whenOptionsThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		ChatOptions options = ChatOptions.builder().build();
		spec = spec.options(options);
		DefaultChatClient.DefaultChatClientRequestSpec defaultSpec = (DefaultChatClient.DefaultChatClientRequestSpec) spec;
		assertThat(defaultSpec.getChatOptions()).isEqualTo(options);
	}

	@Test
	void whenToolNamesElementIsNullThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThatThrownBy(() -> spec.toolNames("myTool", null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("toolNames cannot contain null elements");
	}

	@Test
	void whenToolNamesThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		String toolName = "myTool";
		spec = spec.toolNames(toolName);
		DefaultChatClient.DefaultChatClientRequestSpec defaultSpec = (DefaultChatClient.DefaultChatClientRequestSpec) spec;
		assertThat(defaultSpec.getToolNames()).contains(toolName);
	}

	@Test
	void whenToolCallbacksElementIsNullThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThatThrownBy(() -> spec.toolCallbacks(mock(ToolCallback.class), null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("toolCallbacks cannot contain null elements");
	}

	@Test
	void whenToolCallbacksThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		ToolCallback toolCallback = mock(ToolCallback.class);
		spec = spec.toolCallbacks(toolCallback);
		DefaultChatClient.DefaultChatClientRequestSpec defaultSpec = (DefaultChatClient.DefaultChatClientRequestSpec) spec;
		assertThat(defaultSpec.getToolCallbacks()).contains(toolCallback);
	}

	@Test
	void whenFunctionNameIsNullThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThatThrownBy(() -> spec.toolCallbacks(FunctionToolCallback.builder(null, input -> "hello")
			.description("description")
			.inputType(String.class)
			.build())).isInstanceOf(IllegalArgumentException.class).hasMessage("name cannot be null or empty");
	}

	@Test
	void whenFunctionNameIsEmptyThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThatThrownBy(() -> spec.toolCallbacks(FunctionToolCallback.builder("", input -> "hello")
			.description("description")
			.inputType(String.class)
			.build())).isInstanceOf(IllegalArgumentException.class).hasMessage("name cannot be null or empty");
	}

	@Test
	@Disabled("This fails now as the FunctionToolCallback description is allowed to be empty")
	void whenFunctionDescriptionIsNullThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThatThrownBy(() -> spec.toolCallbacks(FunctionToolCallback.builder("name", input -> "hello")
			.description(null)
			.inputType(String.class)
			.build())).isInstanceOf(IllegalArgumentException.class).hasMessage("Description must not be empty");
	}

	@Test
	@Disabled("This fails now as the FunctionToolCallback description is allowed to be empty")
	void whenFunctionDescriptionIsEmptyThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThatThrownBy(() -> spec.toolCallbacks(
				FunctionToolCallback.builder("name", input -> "hello").description("").inputType(String.class).build()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Description must not be empty");
	}

	@Test
	void whenFunctionThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		spec = spec.toolCallbacks(FunctionToolCallback.builder("name", input -> "hello")
			.inputType(String.class)
			.description("description")
			.build());
		DefaultChatClient.DefaultChatClientRequestSpec defaultSpec = (DefaultChatClient.DefaultChatClientRequestSpec) spec;
		assertThat(defaultSpec.getToolCallbacks())
			.anyMatch(callback -> callback.getToolDefinition().name().equals("name"));
	}

	@Test
	void whenFunctionAndInputTypeThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		spec = spec.toolCallbacks(FunctionToolCallback.builder("name", input -> "hello")
			.inputType(String.class)
			.description("description")
			.build());
		DefaultChatClient.DefaultChatClientRequestSpec defaultSpec = (DefaultChatClient.DefaultChatClientRequestSpec) spec;
		assertThat(defaultSpec.getToolCallbacks())
			.anyMatch(callback -> callback.getToolDefinition().name().equals("name"));
	}

	@Test
	void whenBiFunctionNameIsNullThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThatThrownBy(() -> spec.toolCallbacks(
				FunctionToolCallback.builder(null, (input, ctx) -> "hello").description("description").build()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("name cannot be null or empty");
	}

	@Test
	void whenBiFunctionNameIsEmptyThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThatThrownBy(() -> spec.toolCallbacks(
				FunctionToolCallback.builder("", (input, ctx) -> "hello").description("description").build()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("name cannot be null or empty");
	}

	@Test
	@Disabled("This fails now as the FunctionToolCallback description is allowed to be empty")
	void whenBiFunctionDescriptionIsNullThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThatThrownBy(() -> spec.toolCallbacks(FunctionToolCallback.builder("name", (input, ctx) -> "hello")
			.inputType(String.class)
			.description(null)
			.build())).isInstanceOf(IllegalArgumentException.class).hasMessage("Description must not be empty");
	}

	@Test
	@Disabled("This fails now as the FunctionToolCallback description is allowed to be empty")
	void whenBiFunctionDescriptionIsEmptyThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThatThrownBy(() -> spec
			.toolCallbacks(FunctionToolCallback.builder("name", (input, ctx) -> "hello").description("").build()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Description must not be empty");
	}

	@Test
	void whenBiFunctionThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		spec = spec.toolCallbacks(FunctionToolCallback.builder("name", (input, ctx) -> "hello")
			.description("description")
			.inputType(String.class)
			.build());
		DefaultChatClient.DefaultChatClientRequestSpec defaultSpec = (DefaultChatClient.DefaultChatClientRequestSpec) spec;
		assertThat(defaultSpec.getToolCallbacks())
			.anyMatch(callback -> callback.getToolDefinition().name().equals("name"));
	}

	@Test
	void whenFunctionBeanNamesElementIsNullThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThatThrownBy(() -> spec.toolNames("myFunction", null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("toolNames cannot contain null elements");
	}

	@Test
	void whenFunctionBeanNamesThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		String functionBeanName = "myFunction";
		spec = spec.toolNames(functionBeanName);
		DefaultChatClient.DefaultChatClientRequestSpec defaultSpec = (DefaultChatClient.DefaultChatClientRequestSpec) spec;
		assertThat(defaultSpec.getToolNames()).contains(functionBeanName);
	}

	@Test
	void whenFunctionToolCallbacksElementIsNullThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThatThrownBy(() -> spec.toolCallbacks(mock(FunctionToolCallback.class), null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("toolCallbacks cannot contain null elements");
	}

	@Test
	void whenFunctionToolCallbacksThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		FunctionToolCallback functionToolCallback = mock(FunctionToolCallback.class);
		spec = spec.toolCallbacks(functionToolCallback);
		DefaultChatClient.DefaultChatClientRequestSpec defaultSpec = (DefaultChatClient.DefaultChatClientRequestSpec) spec;
		assertThat(defaultSpec.getToolCallbacks()).contains(functionToolCallback);
	}

	@Test
	void whenToolContextIsNullThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThatThrownBy(() -> spec.toolContext(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("toolContext cannot be null");
	}

	@Test
	void whenToolContextKeyIsNullThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		Map<String, Object> toolContext = new HashMap<>();
		toolContext.put(null, "value");
		assertThatThrownBy(() -> spec.toolContext(toolContext)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("toolContext keys cannot contain null elements");
	}

	@Test
	void whenToolContextValueIsNullThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		Map<String, Object> toolContext = new HashMap<>();
		toolContext.put("key", null);
		assertThatThrownBy(() -> spec.toolContext(toolContext)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("toolContext values cannot contain null elements");
	}

	@Test
	void whenToolContextThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		Map<String, Object> toolContext = Map.of("key", "value");
		spec = spec.toolContext(toolContext);
		DefaultChatClient.DefaultChatClientRequestSpec defaultSpec = (DefaultChatClient.DefaultChatClientRequestSpec) spec;
		assertThat(defaultSpec.getToolContext()).containsEntry("key", "value");
	}

	@Test
	void whenSystemTextIsNullThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThatThrownBy(() -> spec.system((String) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("text cannot be null or empty");
	}

	@Test
	void whenSystemTextIsEmptyThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThatThrownBy(() -> spec.system("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("text cannot be null or empty");
	}

	@Test
	void whenSystemTextThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		spec = spec.system(system -> system.text("instructions"));
		DefaultChatClient.DefaultChatClientRequestSpec defaultSpec = (DefaultChatClient.DefaultChatClientRequestSpec) spec;
		assertThat(defaultSpec.getSystemText()).isEqualTo("instructions");
	}

	@Test
	void whenSystemResourceIsNullWithCharsetThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThatThrownBy(() -> spec.system(null, Charset.defaultCharset()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("text cannot be null");
	}

	@Test
	void whenSystemCharsetIsNullWithResourceThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThatThrownBy(() -> spec.system(new ClassPathResource("system-prompt.txt"), null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("charset cannot be null");
	}

	@Test
	void whenSystemResourceAndCharsetThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		spec = spec.system(system -> system.text(new ClassPathResource("system-prompt.txt"), Charset.defaultCharset()));
		DefaultChatClient.DefaultChatClientRequestSpec defaultSpec = (DefaultChatClient.DefaultChatClientRequestSpec) spec;
		assertThat(defaultSpec.getSystemText()).isEqualTo("instructions");
	}

	@Test
	void whenSystemResourceIsNullThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThatThrownBy(() -> spec.system((Resource) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("text cannot be null");
	}

	@Test
	void whenSystemResourceThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		spec = spec.system(systemSpec -> systemSpec.text(new ClassPathResource("system-prompt.txt")));
		DefaultChatClient.DefaultChatClientRequestSpec defaultSpec = (DefaultChatClient.DefaultChatClientRequestSpec) spec;
		assertThat(defaultSpec.getSystemText()).isEqualTo("instructions");
	}

	@Test
	void whenSystemConsumerIsNullThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThatThrownBy(() -> spec.system((Consumer<ChatClient.PromptSystemSpec>) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("consumer cannot be null");
	}

	@Test
	void whenSystemConsumerThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		spec = spec.system(system -> system.text("my instruction about {topic}").param("topic", "AI"));
		DefaultChatClient.DefaultChatClientRequestSpec defaultSpec = (DefaultChatClient.DefaultChatClientRequestSpec) spec;
		assertThat(defaultSpec.getSystemText()).isEqualTo("my instruction about {topic}");
		assertThat(defaultSpec.getSystemParams()).containsEntry("topic", "AI");
	}

	@Test
	void whenSystemConsumerWithExistingSystemTextThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt().system("my instruction");
		spec = spec.system(system -> system.text("my instruction about {topic}").param("topic", "AI"));
		DefaultChatClient.DefaultChatClientRequestSpec defaultSpec = (DefaultChatClient.DefaultChatClientRequestSpec) spec;
		assertThat(defaultSpec.getSystemText()).isEqualTo("my instruction about {topic}");
		assertThat(defaultSpec.getSystemParams()).containsEntry("topic", "AI");
	}

	@Test
	void whenSystemConsumerWithoutSystemTextThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt().system("my instruction about {topic}");
		spec = spec.system(system -> system.param("topic", "AI"));
		DefaultChatClient.DefaultChatClientRequestSpec defaultSpec = (DefaultChatClient.DefaultChatClientRequestSpec) spec;
		assertThat(defaultSpec.getSystemText()).isEqualTo("my instruction about {topic}");
		assertThat(defaultSpec.getSystemParams()).containsEntry("topic", "AI");
	}

	@Test
	void whenDeveloperTextIsEmptyThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		assertThatThrownBy(() -> chatClient.prompt().developer("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("text cannot be null or empty");
	}

	@Test
	void whenDeveloperTextThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		var spec = chatClient.prompt().developer(dev -> dev.text("instructions"));
		DefaultChatClient.DefaultChatClientRequestSpec defaultSpec = (DefaultChatClient.DefaultChatClientRequestSpec) spec;
		assertThat(defaultSpec.getDeveloperText()).isEqualTo("instructions");
	}

	@Test
	void whenDeveloperResourceIsNullWithCharsetThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		assertThatThrownBy(() -> chatClient.prompt().developer(null, Charset.defaultCharset()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("text cannot be null");
	}

	@Test
	void whenDeveloperCharsetIsNullWithResourceThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		Resource textResource = new ClassPathResource("developer-prompt.txt");
		assertThatThrownBy(() -> chatClient.prompt().developer(textResource, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("charset cannot be null");
	}

	@Test
	void whenDeveloperResourceAndCharsetThenReturn() {
		Resource textResource = new ClassPathResource("developer-prompt.txt");
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		var spec = chatClient.prompt().developer(textResource, Charset.defaultCharset());
		DefaultChatClient.DefaultChatClientRequestSpec defaultSpec = (DefaultChatClient.DefaultChatClientRequestSpec) spec;
		assertThat(defaultSpec.getDeveloperText()).isEqualTo("instructions");
	}

	@Test
	void whenDeveloperResourceThenReturn() {
		Resource textResource = new ClassPathResource("developer-prompt.txt");
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		var spec = chatClient.prompt().developer(textResource);
		DefaultChatClient.DefaultChatClientRequestSpec defaultSpec = (DefaultChatClient.DefaultChatClientRequestSpec) spec;
		assertThat(defaultSpec.getDeveloperText()).isEqualTo("instructions");
	}

	@Test
	void whenDeveloperConsumerIsNullThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		assertThatThrownBy(() -> chatClient.prompt().developer((Consumer<ChatClient.PromptDeveloperSpec>) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("consumer cannot be null");
	}

	@Test
	void whenDeveloperConsumerThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		var spec = chatClient.prompt().developer(dev -> dev.text("my instruction about {topic}").param("topic", "AI"));
		DefaultChatClient.DefaultChatClientRequestSpec defaultSpec = (DefaultChatClient.DefaultChatClientRequestSpec) spec;
		assertThat(defaultSpec.getDeveloperText()).isEqualTo("my instruction about {topic}");
		assertThat(defaultSpec.getDeveloperParams()).containsEntry("topic", "AI");
	}

	@Test
	void whenUserTextIsNullThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThatThrownBy(() -> spec.user((String) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("text cannot be null or empty");
	}

	@Test
	void whenUserTextIsEmptyThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThatThrownBy(() -> spec.user("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("text cannot be null or empty");
	}

	@Test
	void whenUserTextThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		spec = spec.user(user -> user.text("my question"));
		DefaultChatClient.DefaultChatClientRequestSpec defaultSpec = (DefaultChatClient.DefaultChatClientRequestSpec) spec;
		assertThat(defaultSpec.getUserText()).isEqualTo("my question");
	}

	@Test
	void whenUserResourceIsNullWithCharsetThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThatThrownBy(() -> spec.user(null, Charset.defaultCharset())).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("text cannot be null");
	}

	@Test
	void whenUserCharsetIsNullWithResourceThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThatThrownBy(() -> spec.user(new ClassPathResource("user-prompt.txt"), null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("charset cannot be null");
	}

	@Test
	void whenUserResourceAndCharsetThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		spec = spec.user(user -> user.text(new ClassPathResource("user-prompt.txt"), Charset.defaultCharset()));
		DefaultChatClient.DefaultChatClientRequestSpec defaultSpec = (DefaultChatClient.DefaultChatClientRequestSpec) spec;
		assertThat(defaultSpec.getUserText()).isEqualTo("my question");
	}

	@Test
	void whenUserResourceIsNullThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThatThrownBy(() -> spec.user((Resource) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("text cannot be null");
	}

	@Test
	void whenUserResourceThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		spec = spec.user(user -> user.text(new ClassPathResource("user-prompt.txt")));
		DefaultChatClient.DefaultChatClientRequestSpec defaultSpec = (DefaultChatClient.DefaultChatClientRequestSpec) spec;
		assertThat(defaultSpec.getUserText()).isEqualTo("my question");
	}

	@Test
	void whenUserConsumerIsNullThenThrow() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		assertThatThrownBy(() -> spec.user((Consumer<ChatClient.PromptUserSpec>) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("consumer cannot be null");
	}

	@Test
	void whenUserConsumerThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		spec = spec.user(user -> user.text("my question about {topic}")
			.param("topic", "AI")
			.media(MimeTypeUtils.IMAGE_PNG, new ClassPathResource("tabby-cat.png")));
		DefaultChatClient.DefaultChatClientRequestSpec defaultSpec = (DefaultChatClient.DefaultChatClientRequestSpec) spec;
		assertThat(defaultSpec.getUserText()).isEqualTo("my question about {topic}");
		assertThat(defaultSpec.getUserParams()).containsEntry("topic", "AI");
		assertThat(defaultSpec.getMedia()).hasSize(1);
	}

	@Test
	void whenUserConsumerWithExistingUserTextThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt().user("my question");
		spec = spec.user(user -> user.text("my question about {topic}")
			.param("topic", "AI")
			.media(MimeTypeUtils.IMAGE_PNG, new ClassPathResource("tabby-cat.png")));
		DefaultChatClient.DefaultChatClientRequestSpec defaultSpec = (DefaultChatClient.DefaultChatClientRequestSpec) spec;
		assertThat(defaultSpec.getUserText()).isEqualTo("my question about {topic}");
		assertThat(defaultSpec.getUserParams()).containsEntry("topic", "AI");
		assertThat(defaultSpec.getMedia()).hasSize(1);
	}

	@Test
	void whenUserConsumerWithoutUserTextThenReturn() {
		ChatClient chatClient = new DefaultChatClientBuilder(mock(ChatModel.class)).build();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt().user("my question about {topic}");
		spec = spec.user(user -> user.param("topic", "AI")
			.media(MimeTypeUtils.IMAGE_PNG, new ClassPathResource("tabby-cat.png")));
		DefaultChatClient.DefaultChatClientRequestSpec defaultSpec = (DefaultChatClient.DefaultChatClientRequestSpec) spec;
		assertThat(defaultSpec.getUserText()).isEqualTo("my question about {topic}");
		assertThat(defaultSpec.getUserParams()).containsEntry("topic", "AI");
		assertThat(defaultSpec.getMedia()).hasSize(1);
	}

	record Person(String name) {
	}

}
