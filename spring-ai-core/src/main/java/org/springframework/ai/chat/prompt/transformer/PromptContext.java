package org.springframework.ai.chat.prompt.transformer;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.Content;

import java.util.*;

/**
 * The shared, at the moment, mutable, data structure that can be used to implement
 * ChatAgent functionality.
 *
 * @author Mark Pollack
 * @since 1.0 M1
 */
public class PromptContext {

	private Prompt prompt; // The most up-to-date prompt to use

	private List<Content> contents; // The most up-to-date data to use

	private List<Prompt> promptHistory;

	private String conversationId;

	private Map<String, Object> metadata = new HashMap<>();

	public PromptContext(Prompt prompt) {
		this(prompt, new ArrayList<>());
	}

	public PromptContext(Prompt prompt, String conversationId) {
		this(prompt, new ArrayList<>());
		this.conversationId = conversationId;
	}

	public PromptContext(Prompt prompt, List<Content> contents) {
		this.prompt = prompt;
		this.promptHistory = new ArrayList<>();
		this.promptHistory.add(prompt);
		this.contents = contents;
	}

	public Prompt getPrompt() {
		return prompt;
	}

	public void setPrompt(Prompt prompt) {
		this.prompt = prompt;
	}

	public void addData(Content datum) {
		this.contents.add(datum);
	}

	public List<Content> getNodes() {
		return contents;
	}

	public void setNodes(List<Content> contents) {
		this.contents = contents;
	}

	public void addPromptHistory(Prompt prompt) {
		this.promptHistory.add(prompt);
	}

	public List<Prompt> getPromptHistory() {
		return promptHistory;
	}

	public String getConversationId() {
		return conversationId;
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	@Override
	public String toString() {
		return "PromptContext{" + "prompt=" + prompt + ", contents=" + contents + ", promptHistory=" + promptHistory
				+ ", conversationId='" + conversationId + '\'' + ", metadata=" + metadata + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof PromptContext that))
			return false;
		return Objects.equals(prompt, that.prompt) && Objects.equals(contents, that.contents)
				&& Objects.equals(promptHistory, that.promptHistory)
				&& Objects.equals(conversationId, that.conversationId) && Objects.equals(metadata, that.metadata);
	}

	@Override
	public int hashCode() {
		return Objects.hash(prompt, contents, promptHistory, conversationId, metadata);
	}

}
