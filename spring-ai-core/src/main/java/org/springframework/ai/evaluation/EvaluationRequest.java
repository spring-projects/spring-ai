package org.springframework.ai.evaluation;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.Content;

import java.util.List;
import java.util.Objects;

public class EvaluationRequest {

	private final Prompt prompt;

	private final List<Content> dataList;

	private final ChatResponse chatResponse;

	public EvaluationRequest(Prompt prompt, List<Content> dataList, ChatResponse chatResponse) {
		this.prompt = prompt;
		this.dataList = dataList;
		this.chatResponse = chatResponse;
	}

	public Prompt getPrompt() {
		return prompt;
	}

	public List<Content> getDataList() {
		return dataList;
	}

	public ChatResponse getChatResponse() {
		return chatResponse;
	}

	@Override
	public String toString() {
		return "EvaluationRequest{" + "prompt=" + prompt + ", dataList=" + dataList + ", chatResponse=" + chatResponse
				+ '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof EvaluationRequest that))
			return false;
		return Objects.equals(prompt, that.prompt) && Objects.equals(dataList, that.dataList)
				&& Objects.equals(chatResponse, that.chatResponse);
	}

	@Override
	public int hashCode() {
		return Objects.hash(prompt, dataList, chatResponse);
	}

}
