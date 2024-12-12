/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.evaluation;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.springframework.ai.document.Document;
import org.springframework.ai.model.Content;

/**
 * Represents an evaluation request, which includes the user's text, a list of content
 * data, and a chat response. The evaluation request is used to evaluate the relevance or
 * correctness of the chat response based on the context.
 *
 * @author Mark Pollack
 * @author Eddú Meléndez
 * @since 1.0.0 M1
 */
public class EvaluationRequest {

	private final String userText;

	private final List<Document> dataList;

	private final String responseContent;

	public EvaluationRequest(String userText, String responseContent) {
		this(userText, Collections.emptyList(), responseContent);
	}

	public EvaluationRequest(List<Document> dataList, String responseContent) {
		this("", dataList, responseContent);
	}

	public EvaluationRequest(String userText, List<Document> dataList, String responseContent) {
		this.userText = userText;
		this.dataList = dataList;
		this.responseContent = responseContent;
	}

	public String getUserText() {
		return this.userText;
	}

	public List<Document> getDataList() {
		return this.dataList;
	}

	public String getResponseContent() {
		return this.responseContent;
	}

	@Override
	public String toString() {
		return "EvaluationRequest{" + "userText='" + this.userText + '\'' + ", dataList=" + this.dataList
				+ ", chatResponse=" + this.responseContent + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof EvaluationRequest that)) {
			return false;
		}
		return Objects.equals(this.userText, that.userText) && Objects.equals(this.dataList, that.dataList)
				&& Objects.equals(this.responseContent, that.responseContent);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.userText, this.dataList, this.responseContent);
	}

}
