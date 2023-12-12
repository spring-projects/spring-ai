package org.springframework.ai.openai.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;
import java.util.Map;

@JsonDeserialize(builder = ChatCompletionsRequest.Builder.class)
public class ChatCompletionsRequest {

	private final List<OpenAiChatMessage> messages;

	private final String model;

	private final Integer frequencyPenalty;

	private final Map<String, String> logitBias;

	private final Integer maxTokens;

	private final Integer n;

	private final Integer presencePenalty;

	private final ResponseFormat responseFormat;

	private final Integer seed;

	private final List<String> stop;

	private final Boolean stream;

	private final Double temperature;

	private final Integer topP;

	private final List<Tool> tools;

	private final String toolChoice;

	private final String user;

	private ChatCompletionsRequest(Builder builder) {
		this.messages = builder.messages;
		this.model = builder.model;
		this.frequencyPenalty = builder.frequencyPenalty;
		this.logitBias = builder.logitBias;
		this.maxTokens = builder.maxTokens;
		this.n = builder.n;
		this.presencePenalty = builder.presencePenalty;
		this.responseFormat = builder.responseFormat;
		this.seed = builder.seed;
		this.stop = builder.stop;
		this.stream = builder.stream;
		this.temperature = builder.temperature;
		this.topP = builder.topP;
		this.tools = builder.tools;
		this.toolChoice = builder.toolChoice;
		this.user = builder.user;
	}

	public List<OpenAiChatMessage> getMessages() {
		return messages;
	}

	public String getModel() {
		return model;
	}

	public Integer getFrequencyPenalty() {
		return frequencyPenalty;
	}

	public Map<String, String> getLogitBias() {
		return logitBias;
	}

	public Integer getMaxTokens() {
		return maxTokens;
	}

	public Integer getN() {
		return n;
	}

	public Integer getPresencePenalty() {
		return presencePenalty;
	}

	public ResponseFormat getResponseFormat() {
		return responseFormat;
	}

	public Integer getSeed() {
		return seed;
	}

	public List<String> getStop() {
		return stop;
	}

	public Boolean getStream() {
		return stream;
	}

	public Double getTemperature() {
		return temperature;
	}

	public Integer getTopP() {
		return topP;
	}

	public List<Tool> getTools() {
		return tools;
	}

	public String getToolChoice() {
		return toolChoice;
	}

	public String getUser() {
		return user;
	}

	public static class Builder {

		@JsonProperty("messages")
		private List<OpenAiChatMessage> messages;

		@JsonProperty("model")
		private String model;

		@JsonProperty("frequency_penalty")
		private Integer frequencyPenalty;

		@JsonProperty("logit_bias")
		private Map<String, String> logitBias;

		@JsonProperty("max_tokens")
		private Integer maxTokens;

		@JsonProperty("n")
		private Integer n;

		@JsonProperty("presence_penalty")
		private Integer presencePenalty;

		@JsonProperty("response_format")
		private ResponseFormat responseFormat;

		@JsonProperty("seed")
		private Integer seed;

		@JsonProperty("stop")
		private List<String> stop;

		@JsonProperty("stream")
		private Boolean stream;

		@JsonProperty("temperature")
		private Double temperature;

		@JsonProperty("top_p")
		private Integer topP;

		@JsonProperty("tools")
		private List<Tool> tools;

		@JsonProperty("tool_choice")
		private String toolChoice;

		@JsonProperty("user")
		private String user;

		public Builder messages(List<OpenAiChatMessage> messages) {
			this.messages = messages;
			return this;
		}

		public Builder model(String model) {
			this.model = model;
			return this;
		}

		public Builder frequencyPenalty(Integer frequencyPenalty) {
			this.frequencyPenalty = frequencyPenalty;
			return this;
		}

		public Builder logitBias(Map<String, String> logitBias) {
			this.logitBias = logitBias;
			return this;
		}

		public Builder maxTokens(Integer maxTokens) {
			this.maxTokens = maxTokens;
			return this;
		}

		public Builder n(Integer n) {
			this.n = n;
			return this;
		}

		public Builder presencePenalty(Integer presencePenalty) {
			this.presencePenalty = presencePenalty;
			return this;
		}

		public Builder responseFormat(ResponseFormat responseFormat) {
			this.responseFormat = responseFormat;
			return this;
		}

		public Builder seed(Integer seed) {
			this.seed = seed;
			return this;
		}

		public Builder stop(List<String> stop) {
			this.stop = stop;
			return this;
		}

		public Builder stream(Boolean stream) {
			this.stream = stream;
			return this;
		}

		public Builder temperature(Double temperature) {
			this.temperature = temperature;
			return this;
		}

		public Builder topP(Integer topP) {
			this.topP = topP;
			return this;
		}

		public Builder tools(List<Tool> tools) {
			this.tools = tools;
			return this;
		}

		public Builder toolChoice(String toolChoice) {
			this.toolChoice = toolChoice;
			return this;
		}

		public Builder user(String user) {
			this.user = user;
			return this;
		}

		public ChatCompletionsRequest build() {
			return new ChatCompletionsRequest(this);
		}

	}

	public record Function(

			@JsonProperty("name") String name,

			@JsonProperty("description") String description,

			@JsonProperty("parameters") Map<String, Object> parameters,

			@JsonProperty("arguments") String arguments) {
	}

	public record ResponseFormat(

			@JsonProperty("type") String type) {
	}

	public record Tool(

			@JsonProperty("function") Function function,

			@JsonProperty("type") String type) {
	}

}
