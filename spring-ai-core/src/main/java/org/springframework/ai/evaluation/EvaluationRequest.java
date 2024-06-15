package org.springframework.ai.evaluation;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.Content;

import java.util.List;
import java.util.Objects;

/**
 * Represents an evaluation request, which includes the user's text, a list of content
 * data, and a chat response. The evaluation request is used to evaluate the relevance or
 * correctness of the chat response based on the context.
 *
 * @author Mark Pollack
 * @since 1.0.0 M1
 */
public class EvaluationRequest {

	private final String userText;

	private final List<Content> dataList;

	private final ChatResponse chatResponse;

	public EvaluationRequest(String userText, List<Content> dataList, ChatResponse chatResponse) {
		this.userText = userText;
		this.dataList = dataList;
		this.chatResponse = chatResponse;
	}

	public String getUserText() {
		return this.userText;
	}

	public List<Content> getDataList() {
		return dataList;
	}

	public ChatResponse getChatResponse() {
		return chatResponse;
	}

	@Override
	public String toString() {
		return "EvaluationRequest{" + "userText='" + userText + '\'' + ", dataList=" + dataList + ", chatResponse="
				+ chatResponse + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof EvaluationRequest that))
			return false;
		return Objects.equals(userText, that.userText) && Objects.equals(dataList, that.dataList)
				&& Objects.equals(chatResponse, that.chatResponse);
	}

	@Override
	public int hashCode() {
		return Objects.hash(userText, dataList, chatResponse);
	}

}
