/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.chat.history;

import java.util.List;

import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.document.Document;

public class EngineResponse {

	private final ChatResponse chatResponse;

	private final List<Document> retrievedDocuments;

	private final List<ChatHistoryGroup> chatHistoryGroups;

	public EngineResponse(ChatResponse chatResponse, List<Document> documents,
			List<ChatHistoryGroup> chatHistoryGroups) {
		this.chatResponse = chatResponse;
		this.retrievedDocuments = documents;
		this.chatHistoryGroups = chatHistoryGroups;
	}

	public ChatResponse getChatResponse() {
		return this.chatResponse;
	}

	public List<Document> getRetrievedDocuments() {
		return this.retrievedDocuments;
	}

	public List<ChatHistoryGroup> getChatHistoryGroups() {
		return this.chatHistoryGroups;
	}

	@Override
	public String toString() {
		return "EngineResponse{" + "chatResponse=" + chatResponse + ", documents=" + retrievedDocuments + '}';
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((chatResponse == null) ? 0 : chatResponse.hashCode());
		result = prime * result + ((retrievedDocuments == null) ? 0 : retrievedDocuments.hashCode());
		result = prime * result + ((chatHistoryGroups == null) ? 0 : chatHistoryGroups.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		EngineResponse other = (EngineResponse) obj;
		if (chatResponse == null) {
			if (other.chatResponse != null)
				return false;
		}
		else if (!chatResponse.equals(other.chatResponse))
			return false;
		if (retrievedDocuments == null) {
			if (other.retrievedDocuments != null)
				return false;
		}
		else if (!retrievedDocuments.equals(other.retrievedDocuments))
			return false;
		if (chatHistoryGroups == null) {
			if (other.chatHistoryGroups != null)
				return false;
		}
		else if (!chatHistoryGroups.equals(other.chatHistoryGroups))
			return false;
		return true;
	}

}