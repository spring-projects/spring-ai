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

package org.springframework.ai.model.deliverance.autoconfigure;

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.deliverance.DeliveranceChatOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Deliverance chat.
 *
 * @author Edward Capriolo
 * @since 2.0.1
 */
@ConfigurationProperties(DeliveranceChatProperties.CONFIG_PREFIX)
public class DeliveranceChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.deliverance.chat";

	private @Nullable String model;

	private @Nullable Double temperature;

	private @Nullable Integer maxTokens;

	private @Nullable Double topP;

	private @Nullable Integer topK;

	private @Nullable List<String> stopSequences;

	private @Nullable Integer seed;

	private @Nullable Boolean logprobs;

	private @Nullable Integer topLogprobs;

	private @Nullable Double xtcThreshold;

	private @Nullable Double xtcProbability;

	private @Nullable String guidedRegex;

	private @Nullable String guidedJson;

	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	public @Nullable Double getTemperature() {
		return this.temperature;
	}

	public void setTemperature(@Nullable Double temperature) {
		this.temperature = temperature;
	}

	public @Nullable Integer getMaxTokens() {
		return this.maxTokens;
	}

	public void setMaxTokens(@Nullable Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	public @Nullable Double getTopP() {
		return this.topP;
	}

	public void setTopP(@Nullable Double topP) {
		this.topP = topP;
	}

	public @Nullable Integer getTopK() {
		return this.topK;
	}

	public void setTopK(@Nullable Integer topK) {
		this.topK = topK;
	}

	public @Nullable List<String> getStopSequences() {
		return this.stopSequences;
	}

	public void setStopSequences(@Nullable List<String> stopSequences) {
		this.stopSequences = stopSequences;
	}

	public @Nullable Integer getSeed() {
		return this.seed;
	}

	public void setSeed(@Nullable Integer seed) {
		this.seed = seed;
	}

	public @Nullable Boolean getLogprobs() {
		return this.logprobs;
	}

	public void setLogprobs(@Nullable Boolean logprobs) {
		this.logprobs = logprobs;
	}

	public @Nullable Integer getTopLogprobs() {
		return this.topLogprobs;
	}

	public void setTopLogprobs(@Nullable Integer topLogprobs) {
		this.topLogprobs = topLogprobs;
	}

	public @Nullable Double getXtcThreshold() {
		return this.xtcThreshold;
	}

	public void setXtcThreshold(@Nullable Double xtcThreshold) {
		this.xtcThreshold = xtcThreshold;
	}

	public @Nullable Double getXtcProbability() {
		return this.xtcProbability;
	}

	public void setXtcProbability(@Nullable Double xtcProbability) {
		this.xtcProbability = xtcProbability;
	}

	public @Nullable String getGuidedRegex() {
		return this.guidedRegex;
	}

	public void setGuidedRegex(@Nullable String guidedRegex) {
		this.guidedRegex = guidedRegex;
	}

	public @Nullable String getGuidedJson() {
		return this.guidedJson;
	}

	public void setGuidedJson(@Nullable String guidedJson) {
		this.guidedJson = guidedJson;
	}

	public DeliveranceChatOptions toOptions() {
		return DeliveranceChatOptions.builder()
			.model(this.model)
			.temperature(this.temperature)
			.maxTokens(this.maxTokens)
			.topP(this.topP)
			.topK(this.topK)
			.stopSequences(this.stopSequences)
			.seed(this.seed)
			.logprobs(this.logprobs)
			.topLogprobs(this.topLogprobs)
			.xtcThreshold(this.xtcThreshold)
			.xtcProbability(this.xtcProbability)
			.guidedRegex(this.guidedRegex)
			.guidedJson(this.guidedJson)
			.build();
	}

}
