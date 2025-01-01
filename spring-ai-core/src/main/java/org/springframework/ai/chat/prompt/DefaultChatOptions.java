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

package org.springframework.ai.chat.prompt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Default implementation for the {@link ChatOptions}.
 */
public class DefaultChatOptions implements ChatOptions {

	private String model;

	private Double frequencyPenalty;

	private Integer maxTokens;

	private Double presencePenalty;

	private List<String> stopSequences;

	private Double temperature;

	private Integer topK;

	private Double topP;

	@Override
	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	@Override
	public Double getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	public void setFrequencyPenalty(Double frequencyPenalty) {
		this.frequencyPenalty = frequencyPenalty;
	}

	@Override
	public Integer getMaxTokens() {
		return this.maxTokens;
	}

	public void setMaxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	@Override
	public Double getPresencePenalty() {
		return this.presencePenalty;
	}

	public void setPresencePenalty(Double presencePenalty) {
		this.presencePenalty = presencePenalty;
	}

	@Override
	public List<String> getStopSequences() {
		return this.stopSequences != null ? Collections.unmodifiableList(this.stopSequences) : null;
	}

	public void setStopSequences(List<String> stopSequences) {
		this.stopSequences = stopSequences;
	}

	@Override
	public Double getTemperature() {
		return this.temperature;
	}

	public void setTemperature(Double temperature) {
		this.temperature = temperature;
	}

	@Override
	public Integer getTopK() {
		return this.topK;
	}

	public void setTopK(Integer topK) {
		this.topK = topK;
	}

	@Override
	public Double getTopP() {
		return this.topP;
	}

	public void setTopP(Double topP) {
		this.topP = topP;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends ChatOptions> T copy() {
		DefaultChatOptions copy = new DefaultChatOptions();
		copy.setModel(this.getModel());
		copy.setFrequencyPenalty(this.getFrequencyPenalty());
		copy.setMaxTokens(this.getMaxTokens());
		copy.setPresencePenalty(this.getPresencePenalty());
		copy.setStopSequences(this.getStopSequences() != null ? new ArrayList<>(this.getStopSequences()) : null);
		copy.setTemperature(this.getTemperature());
		copy.setTopK(this.getTopK());
		copy.setTopP(this.getTopP());
		return (T) copy;
	}

}
