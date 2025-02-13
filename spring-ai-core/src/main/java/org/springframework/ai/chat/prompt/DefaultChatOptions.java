/*
 * Copyright 2024-2025 the original author or authors.
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
import java.util.List;
import java.util.Objects;

/**
 * Default implementation for the {@link ChatOptions}.
 *
 * @author Alexandros Pappas
 */
public class DefaultChatOptions extends AbstractChatOptions {

	public static Builder builder() {
		return new Builder();
	}

	public void setModel(String model) {
		this.model = model;
	}

	public void setFrequencyPenalty(Double frequencyPenalty) {
		this.frequencyPenalty = frequencyPenalty;
	}

	public void setMaxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	public void setPresencePenalty(Double presencePenalty) {
		this.presencePenalty = presencePenalty;
	}

	public void setStopSequences(List<String> stopSequences) {
		this.stopSequences = stopSequences;
	}

	public void setTemperature(Double temperature) {
		this.temperature = temperature;
	}

	public void setTopK(Integer topK) {
		this.topK = topK;
	}

	public void setTopP(Double topP) {
		this.topP = topP;
	}

	@Override
	@SuppressWarnings("unchecked")
	public DefaultChatOptions copy() {
		return DefaultChatOptions.builder()
			.model(this.getModel())
			.frequencyPenalty(this.getFrequencyPenalty())
			.maxTokens(this.getMaxTokens())
			.presencePenalty(this.getPresencePenalty())
			.stopSequences(this.getStopSequences() == null ? null : new ArrayList<>(this.getStopSequences()))
			.temperature(this.getTemperature())
			.topK(this.getTopK())
			.topP(this.getTopP())
			.build();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;

		DefaultChatOptions that = (DefaultChatOptions) o;

		if (!Objects.equals(model, that.model))
			return false;
		if (!Objects.equals(frequencyPenalty, that.frequencyPenalty))
			return false;
		if (!Objects.equals(maxTokens, that.maxTokens))
			return false;
		if (!Objects.equals(presencePenalty, that.presencePenalty))
			return false;
		if (!Objects.equals(stopSequences, that.stopSequences))
			return false;
		if (!Objects.equals(temperature, that.temperature))
			return false;
		if (!Objects.equals(topK, that.topK))
			return false;
		return Objects.equals(topP, that.topP);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((this.model == null) ? 0 : this.model.hashCode());
		result = prime * result + ((this.maxTokens == null) ? 0 : this.maxTokens.hashCode());
		result = prime * result + ((this.frequencyPenalty == null) ? 0 : this.frequencyPenalty.hashCode());
		result = prime * result + ((this.presencePenalty == null) ? 0 : this.presencePenalty.hashCode());
		result = prime * result + ((this.stopSequences == null) ? 0 : this.stopSequences.hashCode());
		result = prime * result + ((this.temperature == null) ? 0 : this.temperature.hashCode());
		result = prime * result + ((this.topP == null) ? 0 : this.topP.hashCode());
		result = prime * result + ((this.topK == null) ? 0 : this.topK.hashCode());
		return result;
	}

	public static class Builder extends AbstractChatOptions.Builder<DefaultChatOptions, Builder> {

		public Builder() {
			super(new DefaultChatOptions());
		}

		@Override
		protected Builder self() {
			return this;
		}

		@Override
		public DefaultChatOptions build() {
			DefaultChatOptions optionsToBuild = new DefaultChatOptions();
			optionsToBuild.setModel(this.model);
			optionsToBuild.setFrequencyPenalty(this.frequencyPenalty);
			optionsToBuild.setMaxTokens(this.maxTokens);
			optionsToBuild.setPresencePenalty(this.presencePenalty);
			optionsToBuild.setStopSequences(this.stopSequences);
			optionsToBuild.setTemperature(this.temperature);
			optionsToBuild.setTopK(this.topK);
			optionsToBuild.setTopP(this.topP);
			return optionsToBuild;
		}

	}

}
