/*
 * Copyright 2023-2023 the original author or authors.
 *
 * Licensed under the Apache License; Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing; software
 * distributed under the License is distributed on an "AS IS" BASIS;
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND; either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.openai.api;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest.ResponseFormat;

/**
 *
 * @author Christian Tzolov
 */
@JsonInclude(Include.NON_NULL)
public class OpenAiOptions {

	// @formatter:off
	@JsonProperty("messages") List<ChatCompletionMessage> messages;
	@JsonProperty("model") String model;
	@JsonProperty("frequency_penalty") Float frequencyPenalty;
	@JsonProperty("logit_bias") Map<String, Object> logitBias;
	@JsonProperty("max_tokens") Integer maxTokens;
	@JsonProperty("n") Integer n;
	@JsonProperty("presence_penalty") Float presencePenalty;
	@JsonProperty("response_format") ResponseFormat responseFormat;
	@JsonProperty("seed") Integer seed;
	@JsonProperty("stop") String stop;
	@JsonProperty("stream") Boolean stream;
	@JsonProperty("temperature") Float temperature;
	@JsonProperty("top_p") Float topP;
	// @JsonProperty("tools") List<FunctionTool> tools;
	// @JsonProperty("tool_choice") ToolChoice toolChoice;
	@JsonProperty("user") String user;
	// @formatter:on


	public List<ChatCompletionMessage> getMessages() {
		return messages;
	}
	public void setMessages(List<ChatCompletionMessage> messages) {
		this.messages = messages;
	}
	public String getModel() {
		return model;
	}
	public void setModel(String model) {
		this.model = model;
	}
	public Float getFrequencyPenalty() {
		return frequencyPenalty;
	}
	public void setFrequencyPenalty(Float frequencyPenalty) {
		this.frequencyPenalty = frequencyPenalty;
	}
	public Map<String, Object> getLogitBias() {
		return logitBias;
	}
	public void setLogitBias(Map<String, Object> logitBias) {
		this.logitBias = logitBias;
	}
	public Integer getMaxTokens() {
		return maxTokens;
	}
	public void setMaxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
	}
	public Integer getN() {
		return n;
	}
	public void setN(Integer n) {
		this.n = n;
	}
	public Float getPresencePenalty() {
		return presencePenalty;
	}
	public void setPresencePenalty(Float presencePenalty) {
		this.presencePenalty = presencePenalty;
	}
	public ResponseFormat getResponseFormat() {
		return responseFormat;
	}
	public void setResponseFormat(ResponseFormat responseFormat) {
		this.responseFormat = responseFormat;
	}
	public Integer getSeed() {
		return seed;
	}
	public void setSeed(Integer seed) {
		this.seed = seed;
	}
	public String getStop() {
		return stop;
	}
	public void setStop(String stop) {
		this.stop = stop;
	}
	public Boolean getStream() {
		return stream;
	}
	public void setStream(Boolean stream) {
		this.stream = stream;
	}
	public Float getTemperature() {
		return temperature;
	}
	public void setTemperature(Float temperature) {
		this.temperature = temperature;
	}
	public Float getTopP() {
		return topP;
	}
	public void setTopP(Float topP) {
		this.topP = topP;
	}
	public String getUser() {
		return user;
	}
	public void setUser(String user) {
		this.user = user;
	}


}
