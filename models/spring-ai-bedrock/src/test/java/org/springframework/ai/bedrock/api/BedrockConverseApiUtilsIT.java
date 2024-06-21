/*
 * Copyright 2023 - 2024 the original author or authors.
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
package org.springframework.ai.bedrock.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import com.fasterxml.jackson.annotation.JsonProperty;

import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock.Type;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;

/**
 * @author Wei Jiang
 * @since 1.0.0
 */
public class BedrockConverseApiUtilsIT {

	private static final String FAKE_MODEL_ID = "FAKE_MODEL_ID";

	@Test
	public void testCreateConverseRequestWithNoOptions() {
		Prompt prompt = new Prompt("hello world");

		ConverseRequest converseRequest = BedrockConverseApiUtils.createConverseRequest(FAKE_MODEL_ID, prompt);

		assertThat(converseRequest).isNotNull();
		assertThat(converseRequest.system()).isEmpty();
		assertThat(converseRequest.inferenceConfig()).isNull();
		assertThat(converseRequest.toolConfig()).isNull();
		assertThat(converseRequest.additionalModelRequestFields()).isNull();
		assertThat(converseRequest.additionalModelResponseFieldPaths()).isEmpty();
		assertThat(converseRequest.modelId()).isEqualTo(FAKE_MODEL_ID);
		assertThat(converseRequest.messages()).hasSize(1);
		assertThat(converseRequest.messages().get(0).content()).hasSize(1);
		assertThat(converseRequest.messages().get(0).role()).isEqualTo(ConversationRole.USER);
		assertThat(converseRequest.messages().get(0).content().get(0).text()).isEqualTo("hello world");
		assertThat(converseRequest.messages().get(0).content().get(0).type()).isEqualTo(Type.TEXT);
	}

	@Test
	public void testCreateConverseRequestWithMultipleMessagesAndNoOptions() {
		Prompt prompt = new Prompt(List.of(new UserMessage("hello world1"), new UserMessage("hello world2")));

		ConverseRequest converseRequest = BedrockConverseApiUtils.createConverseRequest(FAKE_MODEL_ID, prompt);

		assertThat(converseRequest).isNotNull();
		assertThat(converseRequest.system()).isEmpty();
		assertThat(converseRequest.inferenceConfig()).isNull();
		assertThat(converseRequest.toolConfig()).isNull();
		assertThat(converseRequest.additionalModelRequestFields()).isNull();
		assertThat(converseRequest.additionalModelResponseFieldPaths()).isEmpty();
		assertThat(converseRequest.modelId()).isEqualTo(FAKE_MODEL_ID);
		assertThat(converseRequest.messages()).hasSize(2);
		assertThat(converseRequest.messages().get(0).content()).hasSize(1);
		assertThat(converseRequest.messages().get(0).role()).isEqualTo(ConversationRole.USER);
		assertThat(converseRequest.messages().get(0).content().get(0).text()).isEqualTo("hello world1");
		assertThat(converseRequest.messages().get(0).content().get(0).type()).isEqualTo(Type.TEXT);
		assertThat(converseRequest.messages().get(1).content()).hasSize(1);
		assertThat(converseRequest.messages().get(1).role()).isEqualTo(ConversationRole.USER);
		assertThat(converseRequest.messages().get(1).content().get(0).text()).isEqualTo("hello world2");
		assertThat(converseRequest.messages().get(1).content().get(0).type()).isEqualTo(Type.TEXT);
	}

	@Test
	public void testCreateConverseRequestWithMultipleMessageRolesAndNoOptions() {
		Prompt prompt = new Prompt(List.of(new UserMessage("hello world1"), new AssistantMessage("hello world2")));

		ConverseRequest converseRequest = BedrockConverseApiUtils.createConverseRequest(FAKE_MODEL_ID, prompt);

		assertThat(converseRequest).isNotNull();
		assertThat(converseRequest.system()).isEmpty();
		assertThat(converseRequest.inferenceConfig()).isNull();
		assertThat(converseRequest.toolConfig()).isNull();
		assertThat(converseRequest.additionalModelRequestFields()).isNull();
		assertThat(converseRequest.additionalModelResponseFieldPaths()).isEmpty();
		assertThat(converseRequest.modelId()).isEqualTo(FAKE_MODEL_ID);
		assertThat(converseRequest.messages()).hasSize(2);
		assertThat(converseRequest.messages().get(0).content()).hasSize(1);
		assertThat(converseRequest.messages().get(0).role()).isEqualTo(ConversationRole.USER);
		assertThat(converseRequest.messages().get(0).content().get(0).text()).isEqualTo("hello world1");
		assertThat(converseRequest.messages().get(0).content().get(0).type()).isEqualTo(Type.TEXT);
		assertThat(converseRequest.messages().get(1).content()).hasSize(1);
		assertThat(converseRequest.messages().get(1).role()).isEqualTo(ConversationRole.ASSISTANT);
		assertThat(converseRequest.messages().get(1).content().get(0).text()).isEqualTo("hello world2");
		assertThat(converseRequest.messages().get(1).content().get(0).type()).isEqualTo(Type.TEXT);
	}

	@Test
	public void testCreateConverseRequestWithSystemMessageAndNoOptions() {
		Prompt prompt = new Prompt(
				List.of(new UserMessage("hello world"), new SystemMessage("example system message")));

		ConverseRequest converseRequest = BedrockConverseApiUtils.createConverseRequest(FAKE_MODEL_ID, prompt);

		assertThat(converseRequest).isNotNull();
		assertThat(converseRequest.inferenceConfig()).isNull();
		assertThat(converseRequest.toolConfig()).isNull();
		assertThat(converseRequest.additionalModelRequestFields()).isNull();
		assertThat(converseRequest.additionalModelResponseFieldPaths()).isEmpty();
		assertThat(converseRequest.modelId()).isEqualTo(FAKE_MODEL_ID);
		assertThat(converseRequest.messages()).hasSize(1);
		assertThat(converseRequest.messages().get(0).content()).hasSize(1);
		assertThat(converseRequest.messages().get(0).role()).isEqualTo(ConversationRole.USER);
		assertThat(converseRequest.messages().get(0).content().get(0).text()).isEqualTo("hello world");
		assertThat(converseRequest.messages().get(0).content().get(0).type()).isEqualTo(Type.TEXT);
		assertThat(converseRequest.system()).hasSize(1);
		assertThat(converseRequest.system().get(0).text()).isEqualTo("example system message");
	}

	@Test
	public void testOptionsToAdditionalModelRequestFields() {
		Prompt prompt = new Prompt("hello world");

		ConverseRequest converseRequest = BedrockConverseApiUtils.createConverseRequest(FAKE_MODEL_ID, prompt,
				new MockChatOptions());

		Document requestFields = converseRequest.additionalModelRequestFields();

		assertThat(converseRequest).isNotNull();
		assertThat(converseRequest.system()).isEmpty();
		assertThat(converseRequest.inferenceConfig()).isNull();
		assertThat(converseRequest.toolConfig()).isNull();
		assertThat(requestFields).isNotNull();
		assertThat(requestFields.asMap()).hasSize(12);
		assertThat(requestFields.asMap().get("temperature").asNumber().floatValue()).isEqualTo(0.1F);
		assertThat(requestFields.asMap().get("top_p").asNumber().floatValue()).isEqualTo(0.2F);
		assertThat(requestFields.asMap().get("top_k").asNumber().intValue()).isEqualTo(3);
		assertThat(requestFields.asMap().get("string_value").asString()).isEqualTo("stringValue");
		assertThat(requestFields.asMap().get("boolean_value").asBoolean()).isEqualTo(true);
		assertThat(requestFields.asMap().get("long_value").asNumber().longValue()).isEqualTo(4);
		assertThat(requestFields.asMap().get("float_value").asNumber().floatValue()).isEqualTo(0.5F);
		assertThat(requestFields.asMap().get("double_value").asNumber().doubleValue()).isEqualTo(0.6);
		assertThat(requestFields.asMap().get("big_decimal_value").asNumber().bigDecimalValue())
			.isEqualTo(BigDecimal.valueOf(7));
		assertThat(requestFields.asMap().get("big_intege_value").asNumber().bigDecimalValue().intValue()).isEqualTo(8);
		assertThat(requestFields.asMap().get("list_value").asList()).hasSize(2);
		assertThat(requestFields.asMap().get("list_value").asList().get(0).asString()).isEqualTo("hello");
		assertThat(requestFields.asMap().get("map_value").asMap()).hasSize(1);
		assertThat(requestFields.asMap().get("map_value").asMap().get("hello").asString()).isEqualTo("world");
	}

	@Test
	public void testCreateConverseRequestWithRuntimeOptions() {
		MockChatOptions runtimeOptions = new MockChatOptions();
		runtimeOptions.setTemperature(50F);

		Prompt prompt = new Prompt("hello world", runtimeOptions);

		ConverseRequest converseRequest = BedrockConverseApiUtils.createConverseRequest(FAKE_MODEL_ID, prompt,
				new MockChatOptions());

		Document requestFields = converseRequest.additionalModelRequestFields();

		assertThat(converseRequest).isNotNull();
		assertThat(converseRequest.system()).isEmpty();
		assertThat(converseRequest.inferenceConfig()).isNull();
		assertThat(converseRequest.toolConfig()).isNull();
		assertThat(requestFields).isNotNull();
		assertThat(requestFields.asMap()).hasSize(12);
		assertThat(requestFields.asMap().get("temperature").asNumber().floatValue()).isEqualTo(50F);
		assertThat(requestFields.asMap().get("top_p").asNumber().floatValue()).isEqualTo(0.2F);
	}

	@Test
	public void testCreateConverseStreamRequestWithRuntimeOptions() {
		MockChatOptions runtimeOptions = new MockChatOptions();
		runtimeOptions.setTemperature(50F);

		Prompt prompt = new Prompt("hello world", runtimeOptions);

		ConverseStreamRequest converseStreamRequest = BedrockConverseApiUtils.createConverseStreamRequest(FAKE_MODEL_ID,
				prompt, new MockChatOptions());

		Document requestFields = converseStreamRequest.additionalModelRequestFields();

		assertThat(converseStreamRequest).isNotNull();
		assertThat(converseStreamRequest.system()).isEmpty();
		assertThat(converseStreamRequest.inferenceConfig()).isNull();
		assertThat(converseStreamRequest.additionalModelResponseFieldPaths()).isEmpty();
		assertThat(converseStreamRequest.modelId()).isEqualTo(FAKE_MODEL_ID);
		assertThat(converseStreamRequest.messages()).hasSize(1);
		assertThat(converseStreamRequest.messages().get(0).content()).hasSize(1);
		assertThat(converseStreamRequest.messages().get(0).role()).isEqualTo(ConversationRole.USER);
		assertThat(converseStreamRequest.messages().get(0).content().get(0).text()).isEqualTo("hello world");
		assertThat(converseStreamRequest.messages().get(0).content().get(0).type()).isEqualTo(Type.TEXT);
		assertThat(requestFields.asMap()).hasSize(12);
		assertThat(requestFields.asMap().get("temperature").asNumber().floatValue()).isEqualTo(50F);
		assertThat(requestFields.asMap().get("top_p").asNumber().floatValue()).isEqualTo(0.2F);
	}

	class MockChatOptions implements ChatOptions {

		private @JsonProperty("temperature") Float temperature = 0.1F;

		private @JsonProperty("top_p") Float topP = 0.2F;

		private @JsonProperty("top_k") Integer topK = 3;

		private @JsonProperty("string_value") String stringValue = "stringValue";

		private @JsonProperty("boolean_value") Boolean booleanValue = true;

		private @JsonProperty("long_value") Long longValue = 4L;

		private @JsonProperty("float_value") Float floatValue = 0.5F;

		private @JsonProperty("double_value") Double doubleValue = 0.6;

		private @JsonProperty("big_decimal_value") BigDecimal bigDecimalValue = BigDecimal.valueOf(7);

		private @JsonProperty("big_intege_value") BigInteger bigIntegerValue = BigInteger.valueOf(8);

		private @JsonProperty("list_value") List<String> listValue = List.of("hello", "world");

		private @JsonProperty("map_value") Map<String, Object> mapValue = Map.of("hello", "world");

		@Override
		public Float getTemperature() {
			return temperature;
		}

		@Override
		public Float getTopP() {
			return topP;
		}

		@Override
		public Integer getTopK() {
			return topK;
		}

		public String getStringValue() {
			return stringValue;
		}

		public void setStringValue(String stringValue) {
			this.stringValue = stringValue;
		}

		public Boolean getBooleanValue() {
			return booleanValue;
		}

		public void setBooleanValue(Boolean booleanValue) {
			this.booleanValue = booleanValue;
		}

		public Long getLongValue() {
			return longValue;
		}

		public void setLongValue(Long longValue) {
			this.longValue = longValue;
		}

		public Float getFloatValue() {
			return floatValue;
		}

		public void setFloatValue(Float floatValue) {
			this.floatValue = floatValue;
		}

		public Double getDoubleValue() {
			return doubleValue;
		}

		public void setDoubleValue(Double doubleValue) {
			this.doubleValue = doubleValue;
		}

		public BigDecimal getBigDecimalValue() {
			return bigDecimalValue;
		}

		public void setBigDecimalValue(BigDecimal bigDecimalValue) {
			this.bigDecimalValue = bigDecimalValue;
		}

		public BigInteger getBigIntegerValue() {
			return bigIntegerValue;
		}

		public void setBigIntegerValue(BigInteger bigIntegerValue) {
			this.bigIntegerValue = bigIntegerValue;
		}

		public List<String> getListValue() {
			return listValue;
		}

		public void setListValue(List<String> listValue) {
			this.listValue = listValue;
		}

		public Map<String, Object> getMapValue() {
			return mapValue;
		}

		public void setMapValue(Map<String, Object> mapValue) {
			this.mapValue = mapValue;
		}

		public void setTemperature(Float temperature) {
			this.temperature = temperature;
		}

		public void setTopP(Float topP) {
			this.topP = topP;
		}

		public void setTopK(Integer topK) {
			this.topK = topK;
		}

	}

}
