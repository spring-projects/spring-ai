package org.springframework.ai.ollama.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Ollama generate a completion api response model
 *
 * @author nullptr
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OllamaGenerateResult {

	@JsonProperty("model")
	private String model;

	@JsonProperty("created_at")
	private String createdAt;

	@JsonProperty("response")
	private String response;

	@JsonProperty("done")
	private Boolean done;

	@JsonProperty("context")
	private List<Long> context;

	@JsonProperty("total_duration")
	private Long totalDuration;

	@JsonProperty("load_duration")
	private Long loadDuration;

	@JsonProperty("prompt_eval_count")
	private Long promptEvalCount;

	@JsonProperty("prompt_eval_duration")
	private Long promptEvalDuration;

	@JsonProperty("eval_count")
	private Long evalCount;

	@JsonProperty("eval_duration")
	private Long evalDuration;

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(String createdAt) {
		this.createdAt = createdAt;
	}

	public String getResponse() {
		return response;
	}

	public void setResponse(String response) {
		this.response = response;
	}

	public Boolean getDone() {
		return done;
	}

	public void setDone(Boolean done) {
		this.done = done;
	}

	public List<Long> getContext() {
		return context;
	}

	public void setContext(List<Long> context) {
		this.context = context;
	}

	public Long getTotalDuration() {
		return totalDuration;
	}

	public void setTotalDuration(Long totalDuration) {
		this.totalDuration = totalDuration;
	}

	public Long getLoadDuration() {
		return loadDuration;
	}

	public void setLoadDuration(Long loadDuration) {
		this.loadDuration = loadDuration;
	}

	public Long getPromptEvalCount() {
		return promptEvalCount;
	}

	public void setPromptEvalCount(Long promptEvalCount) {
		this.promptEvalCount = promptEvalCount;
	}

	public Long getPromptEvalDuration() {
		return promptEvalDuration;
	}

	public void setPromptEvalDuration(Long promptEvalDuration) {
		this.promptEvalDuration = promptEvalDuration;
	}

	public Long getEvalCount() {
		return evalCount;
	}

	public void setEvalCount(Long evalCount) {
		this.evalCount = evalCount;
	}

	public Long getEvalDuration() {
		return evalDuration;
	}

	public void setEvalDuration(Long evalDuration) {
		this.evalDuration = evalDuration;
	}

	@Override
	public String toString() {
		return "OllamaGenerateResult{" + "model='" + model + '\'' + ", createdAt='" + createdAt + '\'' + ", response='"
				+ response + '\'' + ", done='" + done + '\'' + ", context=" + context + ", totalDuration="
				+ totalDuration + ", loadDuration=" + loadDuration + ", promptEvalCount=" + promptEvalCount
				+ ", promptEvalDuration=" + promptEvalDuration + ", evalCount=" + evalCount + ", evalDuration="
				+ evalDuration + '}';
	}

}
