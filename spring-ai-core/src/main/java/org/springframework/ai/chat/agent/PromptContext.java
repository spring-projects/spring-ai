package org.springframework.ai.chat.agent;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.node.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The shared, at the moment, mutable, data structure that can be used to implement
 * ChatAgent functionality.
 *
 * @author Mark Pollack
 * @since 1.0 M1
 */
public class PromptContext {

	private Prompt prompt; // The most up-to-date prompt to use

	List<Node<?>> nodes; // The most up-to-date data to use

	private List<Prompt> promptHistory;

	private String conversationId;

	private Map<String, Object> metadata;

	public PromptContext(Prompt prompt) {
		this(prompt, new ArrayList<>());
	}

	public PromptContext(Prompt prompt, String conversationId) {
		this(prompt, new ArrayList<>());
		this.conversationId = conversationId;
	}

	public PromptContext(Prompt prompt, List<Node<?>> nodes) {
		this.prompt = prompt;
		this.promptHistory = new ArrayList<>();
		this.promptHistory.add(prompt);
		this.nodes = nodes;
	}

	public Prompt getPrompt() {
		return prompt;
	}

	public void setPrompt(Prompt prompt) {
		this.prompt = prompt;
	}

	public void addData(Node<?> datum) {
		this.nodes.add(datum);
	}

	public List<Node<?>> getNodes() {
		return nodes;
	}

	public void setNodes(List<Node<?>> nodes) {
		this.nodes = nodes;
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
		return "PromptContext{" + "prompt=" + prompt + ", nodes=" + nodes + ", promptHistory=" + promptHistory
				+ ", conversationId='" + conversationId + '\'' + ", metadata=" + metadata + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof PromptContext that))
			return false;
		return Objects.equals(prompt, that.prompt) && Objects.equals(nodes, that.nodes)
				&& Objects.equals(promptHistory, that.promptHistory)
				&& Objects.equals(conversationId, that.conversationId) && Objects.equals(metadata, that.metadata);
	}

	@Override
	public int hashCode() {
		return Objects.hash(prompt, nodes, promptHistory, conversationId, metadata);
	}

}
