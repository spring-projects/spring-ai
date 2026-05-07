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

package org.springframework.ai.model.ollama.autoconfigure;

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.ollama.api.ThinkOption;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

/**
 * Ollama Chat autoconfiguration properties.
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
@ConfigurationProperties(OllamaChatProperties.CONFIG_PREFIX)
public class OllamaChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.ollama.chat";

	private String model = OllamaModel.MISTRAL.id();

	private @Nullable Object format;

	private @Nullable String keepAlive;

	private @Nullable Boolean truncate;

	private @Nullable ThinkOption thinkOption;

	private @Nullable Boolean useNUMA;

	private @Nullable Integer numCtx;

	private @Nullable Integer numBatch;

	private @Nullable Integer numGPU;

	private @Nullable Integer mainGPU;

	private @Nullable Boolean lowVRAM;

	private @Nullable Boolean f16KV;

	private @Nullable Boolean logitsAll;

	private @Nullable Boolean vocabOnly;

	private @Nullable Boolean useMMap;

	private @Nullable Boolean useMLock;

	private @Nullable Integer numThread;

	private @Nullable Integer numKeep;

	private @Nullable Integer seed;

	private @Nullable Integer numPredict;

	private @Nullable Integer topK;

	private @Nullable Double topP;

	private @Nullable Double minP;

	private @Nullable Float tfsZ;

	private @Nullable Float typicalP;

	private @Nullable Integer repeatLastN;

	private @Nullable Double temperature;

	private @Nullable Double repeatPenalty;

	private @Nullable Double presencePenalty;

	private @Nullable Double frequencyPenalty;

	private @Nullable Integer mirostat;

	private @Nullable Float mirostatTau;

	private @Nullable Float mirostatEta;

	private @Nullable Boolean penalizeNewline;

	private @Nullable List<String> stop;

	private @Nullable Boolean internalToolExecutionEnabled;

	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public @Nullable Object getFormat() {
		return this.format;
	}

	public void setFormat(@Nullable Object format) {
		this.format = format;
	}

	public @Nullable String getKeepAlive() {
		return this.keepAlive;
	}

	public void setKeepAlive(@Nullable String keepAlive) {
		this.keepAlive = keepAlive;
	}

	public @Nullable Boolean getTruncate() {
		return this.truncate;
	}

	public void setTruncate(@Nullable Boolean truncate) {
		this.truncate = truncate;
	}

	public @Nullable ThinkOption getThinkOption() {
		return this.thinkOption;
	}

	public void setThinkOption(@Nullable ThinkOption thinkOption) {
		this.thinkOption = thinkOption;
	}

	public @Nullable Boolean getUseNUMA() {
		return this.useNUMA;
	}

	public void setUseNUMA(@Nullable Boolean useNUMA) {
		this.useNUMA = useNUMA;
	}

	public @Nullable Integer getNumCtx() {
		return this.numCtx;
	}

	public void setNumCtx(@Nullable Integer numCtx) {
		this.numCtx = numCtx;
	}

	public @Nullable Integer getNumBatch() {
		return this.numBatch;
	}

	public void setNumBatch(@Nullable Integer numBatch) {
		this.numBatch = numBatch;
	}

	public @Nullable Integer getNumGPU() {
		return this.numGPU;
	}

	public void setNumGPU(@Nullable Integer numGPU) {
		this.numGPU = numGPU;
	}

	public @Nullable Integer getMainGPU() {
		return this.mainGPU;
	}

	public void setMainGPU(@Nullable Integer mainGPU) {
		this.mainGPU = mainGPU;
	}

	public @Nullable Boolean getLowVRAM() {
		return this.lowVRAM;
	}

	public void setLowVRAM(@Nullable Boolean lowVRAM) {
		this.lowVRAM = lowVRAM;
	}

	public @Nullable Boolean getF16KV() {
		return this.f16KV;
	}

	public void setF16KV(@Nullable Boolean f16KV) {
		this.f16KV = f16KV;
	}

	public @Nullable Boolean getLogitsAll() {
		return this.logitsAll;
	}

	public void setLogitsAll(@Nullable Boolean logitsAll) {
		this.logitsAll = logitsAll;
	}

	public @Nullable Boolean getVocabOnly() {
		return this.vocabOnly;
	}

	public void setVocabOnly(@Nullable Boolean vocabOnly) {
		this.vocabOnly = vocabOnly;
	}

	public @Nullable Boolean getUseMMap() {
		return this.useMMap;
	}

	public void setUseMMap(@Nullable Boolean useMMap) {
		this.useMMap = useMMap;
	}

	public @Nullable Boolean getUseMLock() {
		return this.useMLock;
	}

	public void setUseMLock(@Nullable Boolean useMLock) {
		this.useMLock = useMLock;
	}

	public @Nullable Integer getNumThread() {
		return this.numThread;
	}

	public void setNumThread(@Nullable Integer numThread) {
		this.numThread = numThread;
	}

	public @Nullable Integer getNumKeep() {
		return this.numKeep;
	}

	public void setNumKeep(@Nullable Integer numKeep) {
		this.numKeep = numKeep;
	}

	public @Nullable Integer getSeed() {
		return this.seed;
	}

	public void setSeed(@Nullable Integer seed) {
		this.seed = seed;
	}

	public @Nullable Integer getNumPredict() {
		return this.numPredict;
	}

	public void setNumPredict(@Nullable Integer numPredict) {
		this.numPredict = numPredict;
	}

	public @Nullable Integer getTopK() {
		return this.topK;
	}

	public void setTopK(@Nullable Integer topK) {
		this.topK = topK;
	}

	public @Nullable Double getTopP() {
		return this.topP;
	}

	public void setTopP(@Nullable Double topP) {
		this.topP = topP;
	}

	public @Nullable Double getMinP() {
		return this.minP;
	}

	public void setMinP(@Nullable Double minP) {
		this.minP = minP;
	}

	public @Nullable Float getTfsZ() {
		return this.tfsZ;
	}

	public void setTfsZ(@Nullable Float tfsZ) {
		this.tfsZ = tfsZ;
	}

	public @Nullable Float getTypicalP() {
		return this.typicalP;
	}

	public void setTypicalP(@Nullable Float typicalP) {
		this.typicalP = typicalP;
	}

	public @Nullable Integer getRepeatLastN() {
		return this.repeatLastN;
	}

	public void setRepeatLastN(@Nullable Integer repeatLastN) {
		this.repeatLastN = repeatLastN;
	}

	public @Nullable Double getTemperature() {
		return this.temperature;
	}

	public void setTemperature(@Nullable Double temperature) {
		this.temperature = temperature;
	}

	public @Nullable Double getRepeatPenalty() {
		return this.repeatPenalty;
	}

	public void setRepeatPenalty(@Nullable Double repeatPenalty) {
		this.repeatPenalty = repeatPenalty;
	}

	public @Nullable Double getPresencePenalty() {
		return this.presencePenalty;
	}

	public void setPresencePenalty(@Nullable Double presencePenalty) {
		this.presencePenalty = presencePenalty;
	}

	public @Nullable Double getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	public void setFrequencyPenalty(@Nullable Double frequencyPenalty) {
		this.frequencyPenalty = frequencyPenalty;
	}

	public @Nullable Integer getMirostat() {
		return this.mirostat;
	}

	public void setMirostat(@Nullable Integer mirostat) {
		this.mirostat = mirostat;
	}

	public @Nullable Float getMirostatTau() {
		return this.mirostatTau;
	}

	public void setMirostatTau(@Nullable Float mirostatTau) {
		this.mirostatTau = mirostatTau;
	}

	public @Nullable Float getMirostatEta() {
		return this.mirostatEta;
	}

	public void setMirostatEta(@Nullable Float mirostatEta) {
		this.mirostatEta = mirostatEta;
	}

	public @Nullable Boolean getPenalizeNewline() {
		return this.penalizeNewline;
	}

	public void setPenalizeNewline(@Nullable Boolean penalizeNewline) {
		this.penalizeNewline = penalizeNewline;
	}

	public @Nullable List<String> getStop() {
		return this.stop;
	}

	public void setStop(@Nullable List<String> stop) {
		this.stop = stop;
	}

	public @Nullable Boolean getInternalToolExecutionEnabled() {
		return this.internalToolExecutionEnabled;
	}

	public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
		this.internalToolExecutionEnabled = internalToolExecutionEnabled;
	}

	public OllamaChatOptions toOptions() {
		OllamaChatOptions.Builder builder = OllamaChatOptions.builder();
		builder.model(this.model);
		if (this.format != null) {
			builder.format(this.format);
		}
		if (this.keepAlive != null) {
			builder.keepAlive(this.keepAlive);
		}
		if (this.truncate != null) {
			builder.truncate(this.truncate);
		}
		if (this.thinkOption != null) {
			builder.thinkOption(this.thinkOption);
		}
		if (this.useNUMA != null) {
			builder.useNUMA(this.useNUMA);
		}
		if (this.numCtx != null) {
			builder.numCtx(this.numCtx);
		}
		if (this.numBatch != null) {
			builder.numBatch(this.numBatch);
		}
		if (this.numGPU != null) {
			builder.numGPU(this.numGPU);
		}
		if (this.mainGPU != null) {
			builder.mainGPU(this.mainGPU);
		}
		if (this.lowVRAM != null) {
			builder.lowVRAM(this.lowVRAM);
		}
		if (this.f16KV != null) {
			builder.f16KV(this.f16KV);
		}
		if (this.logitsAll != null) {
			builder.logitsAll(this.logitsAll);
		}
		if (this.vocabOnly != null) {
			builder.vocabOnly(this.vocabOnly);
		}
		if (this.useMMap != null) {
			builder.useMMap(this.useMMap);
		}
		if (this.useMLock != null) {
			builder.useMLock(this.useMLock);
		}
		if (this.numThread != null) {
			builder.numThread(this.numThread);
		}
		if (this.numKeep != null) {
			builder.numKeep(this.numKeep);
		}
		if (this.seed != null) {
			builder.seed(this.seed);
		}
		if (this.numPredict != null) {
			builder.numPredict(this.numPredict);
		}
		if (this.topK != null) {
			builder.topK(this.topK);
		}
		if (this.topP != null) {
			builder.topP(this.topP);
		}
		if (this.minP != null) {
			builder.minP(this.minP);
		}
		if (this.tfsZ != null) {
			builder.tfsZ(this.tfsZ);
		}
		if (this.typicalP != null) {
			builder.typicalP(this.typicalP);
		}
		if (this.repeatLastN != null) {
			builder.repeatLastN(this.repeatLastN);
		}
		if (this.temperature != null) {
			builder.temperature(this.temperature);
		}
		if (this.repeatPenalty != null) {
			builder.repeatPenalty(this.repeatPenalty);
		}
		if (this.presencePenalty != null) {
			builder.presencePenalty(this.presencePenalty);
		}
		if (this.frequencyPenalty != null) {
			builder.frequencyPenalty(this.frequencyPenalty);
		}
		if (this.mirostat != null) {
			builder.mirostat(this.mirostat);
		}
		if (this.mirostatTau != null) {
			builder.mirostatTau(this.mirostatTau);
		}
		if (this.mirostatEta != null) {
			builder.mirostatEta(this.mirostatEta);
		}
		if (this.penalizeNewline != null) {
			builder.penalizeNewline(this.penalizeNewline);
		}
		if (this.stop != null) {
			builder.stop(this.stop);
		}
		if (this.internalToolExecutionEnabled != null) {
			builder.internalToolExecutionEnabled(this.internalToolExecutionEnabled);
		}
		return builder.build();
	}

	private Options options = new Options();

	@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat")
	@Deprecated(since = "2.0.0", forRemoval = true)
	public Options getOptions() {
		return this.options;
	}

	public void setOptions(Options options) {
		this.options = options;
	}

	public class Options {

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.model")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public String getModel() {
			return OllamaChatProperties.this.getModel();
		}

		public void setModel(String model) {
			OllamaChatProperties.this.setModel(model);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.format")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Object getFormat() {
			return OllamaChatProperties.this.getFormat();
		}

		public void setFormat(@Nullable Object format) {
			OllamaChatProperties.this.setFormat(format);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.keep-alive")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getKeepAlive() {
			return OllamaChatProperties.this.getKeepAlive();
		}

		public void setKeepAlive(@Nullable String keepAlive) {
			OllamaChatProperties.this.setKeepAlive(keepAlive);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.truncate")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getTruncate() {
			return OllamaChatProperties.this.getTruncate();
		}

		public void setTruncate(@Nullable Boolean truncate) {
			OllamaChatProperties.this.setTruncate(truncate);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.think-option")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable ThinkOption getThinkOption() {
			return OllamaChatProperties.this.getThinkOption();
		}

		public void setThinkOption(@Nullable ThinkOption thinkOption) {
			OllamaChatProperties.this.setThinkOption(thinkOption);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.use-numa")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getUseNUMA() {
			return OllamaChatProperties.this.getUseNUMA();
		}

		public void setUseNUMA(@Nullable Boolean useNUMA) {
			OllamaChatProperties.this.setUseNUMA(useNUMA);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.num-ctx")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getNumCtx() {
			return OllamaChatProperties.this.getNumCtx();
		}

		public void setNumCtx(@Nullable Integer numCtx) {
			OllamaChatProperties.this.setNumCtx(numCtx);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.num-batch")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getNumBatch() {
			return OllamaChatProperties.this.getNumBatch();
		}

		public void setNumBatch(@Nullable Integer numBatch) {
			OllamaChatProperties.this.setNumBatch(numBatch);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.num-gpu")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getNumGPU() {
			return OllamaChatProperties.this.getNumGPU();
		}

		public void setNumGPU(@Nullable Integer numGPU) {
			OllamaChatProperties.this.setNumGPU(numGPU);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.main-gpu")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getMainGPU() {
			return OllamaChatProperties.this.getMainGPU();
		}

		public void setMainGPU(@Nullable Integer mainGPU) {
			OllamaChatProperties.this.setMainGPU(mainGPU);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.low-vram")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getLowVRAM() {
			return OllamaChatProperties.this.getLowVRAM();
		}

		public void setLowVRAM(@Nullable Boolean lowVRAM) {
			OllamaChatProperties.this.setLowVRAM(lowVRAM);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.f16-kv")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getF16KV() {
			return OllamaChatProperties.this.getF16KV();
		}

		public void setF16KV(@Nullable Boolean f16KV) {
			OllamaChatProperties.this.setF16KV(f16KV);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.logits-all")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getLogitsAll() {
			return OllamaChatProperties.this.getLogitsAll();
		}

		public void setLogitsAll(@Nullable Boolean logitsAll) {
			OllamaChatProperties.this.setLogitsAll(logitsAll);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.vocab-only")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getVocabOnly() {
			return OllamaChatProperties.this.getVocabOnly();
		}

		public void setVocabOnly(@Nullable Boolean vocabOnly) {
			OllamaChatProperties.this.setVocabOnly(vocabOnly);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.use-m-map")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getUseMMap() {
			return OllamaChatProperties.this.getUseMMap();
		}

		public void setUseMMap(@Nullable Boolean useMMap) {
			OllamaChatProperties.this.setUseMMap(useMMap);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.use-m-lock")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getUseMLock() {
			return OllamaChatProperties.this.getUseMLock();
		}

		public void setUseMLock(@Nullable Boolean useMLock) {
			OllamaChatProperties.this.setUseMLock(useMLock);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.num-thread")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getNumThread() {
			return OllamaChatProperties.this.getNumThread();
		}

		public void setNumThread(@Nullable Integer numThread) {
			OllamaChatProperties.this.setNumThread(numThread);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.num-keep")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getNumKeep() {
			return OllamaChatProperties.this.getNumKeep();
		}

		public void setNumKeep(@Nullable Integer numKeep) {
			OllamaChatProperties.this.setNumKeep(numKeep);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.seed")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getSeed() {
			return OllamaChatProperties.this.getSeed();
		}

		public void setSeed(@Nullable Integer seed) {
			OllamaChatProperties.this.setSeed(seed);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.num-predict")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getNumPredict() {
			return OllamaChatProperties.this.getNumPredict();
		}

		public void setNumPredict(@Nullable Integer numPredict) {
			OllamaChatProperties.this.setNumPredict(numPredict);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.top-k")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getTopK() {
			return OllamaChatProperties.this.getTopK();
		}

		public void setTopK(@Nullable Integer topK) {
			OllamaChatProperties.this.setTopK(topK);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.top-p")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getTopP() {
			return OllamaChatProperties.this.getTopP();
		}

		public void setTopP(@Nullable Double topP) {
			OllamaChatProperties.this.setTopP(topP);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.min-p")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getMinP() {
			return OllamaChatProperties.this.getMinP();
		}

		public void setMinP(@Nullable Double minP) {
			OllamaChatProperties.this.setMinP(minP);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.tfs-z")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Float getTfsZ() {
			return OllamaChatProperties.this.getTfsZ();
		}

		public void setTfsZ(@Nullable Float tfsZ) {
			OllamaChatProperties.this.setTfsZ(tfsZ);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.typical-p")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Float getTypicalP() {
			return OllamaChatProperties.this.getTypicalP();
		}

		public void setTypicalP(@Nullable Float typicalP) {
			OllamaChatProperties.this.setTypicalP(typicalP);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.repeat-last-n")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getRepeatLastN() {
			return OllamaChatProperties.this.getRepeatLastN();
		}

		public void setRepeatLastN(@Nullable Integer repeatLastN) {
			OllamaChatProperties.this.setRepeatLastN(repeatLastN);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.temperature")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getTemperature() {
			return OllamaChatProperties.this.getTemperature();
		}

		public void setTemperature(@Nullable Double temperature) {
			OllamaChatProperties.this.setTemperature(temperature);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.repeat-penalty")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getRepeatPenalty() {
			return OllamaChatProperties.this.getRepeatPenalty();
		}

		public void setRepeatPenalty(@Nullable Double repeatPenalty) {
			OllamaChatProperties.this.setRepeatPenalty(repeatPenalty);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.presence-penalty")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getPresencePenalty() {
			return OllamaChatProperties.this.getPresencePenalty();
		}

		public void setPresencePenalty(@Nullable Double presencePenalty) {
			OllamaChatProperties.this.setPresencePenalty(presencePenalty);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.frequency-penalty")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Double getFrequencyPenalty() {
			return OllamaChatProperties.this.getFrequencyPenalty();
		}

		public void setFrequencyPenalty(@Nullable Double frequencyPenalty) {
			OllamaChatProperties.this.setFrequencyPenalty(frequencyPenalty);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.mirostat")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getMirostat() {
			return OllamaChatProperties.this.getMirostat();
		}

		public void setMirostat(@Nullable Integer mirostat) {
			OllamaChatProperties.this.setMirostat(mirostat);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.mirostat-tau")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Float getMirostatTau() {
			return OllamaChatProperties.this.getMirostatTau();
		}

		public void setMirostatTau(@Nullable Float mirostatTau) {
			OllamaChatProperties.this.setMirostatTau(mirostatTau);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.mirostat-eta")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Float getMirostatEta() {
			return OllamaChatProperties.this.getMirostatEta();
		}

		public void setMirostatEta(@Nullable Float mirostatEta) {
			OllamaChatProperties.this.setMirostatEta(mirostatEta);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.penalize-newline")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getPenalizeNewline() {
			return OllamaChatProperties.this.getPenalizeNewline();
		}

		public void setPenalizeNewline(@Nullable Boolean penalizeNewline) {
			OllamaChatProperties.this.setPenalizeNewline(penalizeNewline);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.stop")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable List<String> getStop() {
			return OllamaChatProperties.this.getStop();
		}

		public void setStop(@Nullable List<String> stop) {
			OllamaChatProperties.this.setStop(stop);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.chat.internal-tool-execution-enabled")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getInternalToolExecutionEnabled() {
			return OllamaChatProperties.this.getInternalToolExecutionEnabled();
		}

		public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
			OllamaChatProperties.this.setInternalToolExecutionEnabled(internalToolExecutionEnabled);
		}

	}

}
