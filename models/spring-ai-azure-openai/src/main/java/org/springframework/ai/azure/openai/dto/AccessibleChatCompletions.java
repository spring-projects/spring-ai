package org.springframework.ai.azure.openai.dto;

import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.CompletionsUsage;
import com.azure.ai.openai.models.ContentFilterResultsForPrompt;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.util.Assert;

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

	public static AccessibleChatCompletions empty() {
		final var item = new AccessibleChatCompletions();
		item.id = null;
		item.choices = new ArrayList<>();
		item.usage = null;
		item.createdAt = OffsetDateTime.MIN;
		item.promptFilterResults = new ArrayList<>();
		item.systemFingerprint = null;
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

	public static AccessibleChatCompletions merge(AccessibleChatCompletions left, AccessibleChatCompletions right) {
		Assert.isTrue(left != null, "");
		if (right == null) {
			Assert.isTrue(left.id != null, "");
			return left;
		}
		Assert.isTrue(left.id != null || right.id != null, "");

		final var instance = new AccessibleChatCompletions();
		instance.id = left.id != null ? left.id : right.id;

		if (right.choices == null) {
			instance.choices = left.choices;
		}
		else {
			if (left.choices == null || left.choices.isEmpty()) {
				instance.choices = right.choices;
			}
			else {
				instance.choices = List.of(AccessibleChatChoice.merge(left.choices.get(0), right.choices.get(0)));
			}
		}

		// For these properties if right contains that use it!
		instance.usage = right.usage == null ? left.usage : right.usage;
		instance.createdAt = left.createdAt.isAfter(right.createdAt) ? left.createdAt : right.createdAt;
		instance.promptFilterResults = right.promptFilterResults == null ? left.promptFilterResults
				: right.promptFilterResults;
		instance.systemFingerprint = right.systemFingerprint == null ? left.systemFingerprint : right.systemFingerprint;
		return instance;
	}

}
