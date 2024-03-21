package org.springframework.ai.azure.openai.dto;

import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.CompletionsUsage;
import com.azure.ai.openai.models.ContentFilterResultsForPrompt;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class AccessibleChatCompletions {

	@JsonProperty(value = "id")
	public String id;

	@JsonProperty(value = "choices")
	public List<AccessibleChatChoice> choices;

	@JsonProperty(value = "usage")
	public CompletionsUsage usage;

	@JsonProperty(value = "created")
	public OffsetDateTime createdAt;

	@JsonProperty(value = "prompt_filter_results")
	public List<ContentFilterResultsForPrompt> promptFilterResults;

	@JsonProperty(value = "system_fingerprint")
	public String systemFingerprint;

	public static AccessibleChatCompletions from(ChatCompletions chatCompletions) {
		final var item = new AccessibleChatCompletions();
		item.id = chatCompletions.getId();
		item.choices = chatCompletions.getChoices().stream().map(AccessibleChatChoice::from).toList();
		item.usage = chatCompletions.getUsage();
		item.createdAt = chatCompletions.getCreatedAt();
		item.promptFilterResults = chatCompletions.getPromptFilterResults();
		item.systemFingerprint = chatCompletions.getSystemFingerprint();
		return item;
	}

	public String getId() {
		return id;
	}

	public List<AccessibleChatChoice> getChoices() {
		return choices;
	}

	public CompletionsUsage getUsage() {
		return usage;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public List<ContentFilterResultsForPrompt> getPromptFilterResults() {
		return promptFilterResults;
	}

	public String getSystemFingerprint() {
		return systemFingerprint;
	}

	public AccessibleChatCompletions merge(AccessibleChatCompletions other) {
		if (this.id == null && other.id != null) {
			this.id = other.getId();
		}

		var choices = new ArrayList<AccessibleChatChoice>();
		if (this.choices != null) {
			choices.addAll(this.choices);
		}
		if (other.choices != null) {
			choices.addAll(other.choices);
		}
		this.choices = choices;
		this.usage = other.usage != null ? other.usage : this.usage;
		this.createdAt = other.createdAt != null ? other.createdAt : this.createdAt;
		this.promptFilterResults = other.promptFilterResults != null ? other.promptFilterResults
				: this.promptFilterResults;
		this.systemFingerprint = other.systemFingerprint != null ? other.systemFingerprint : this.systemFingerprint;
		return this;
	}

}
