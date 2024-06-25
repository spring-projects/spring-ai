/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.anthropic.api;

import org.springframework.ai.anthropic.api.AnthropicApi.MediaContent;
import org.springframework.ai.anthropic.api.AnthropicApi.StreamResponse;
import org.springframework.ai.model.ModelOptionsUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to support streaming function calling.
 * <p>
 * It can merge the streamed {@link StreamResponse} chunks in case of function calling
 * message.
 *
 * @author Mariusz Bernacki
 * @since 1.0.0
 */
public class AnthropicStreamFunctionCallingHelper {

	public boolean isStreamingToolFunctionCall(StreamResponse response) {
		if (response == null || response.contentBlock() == null) {
			return false;
		}

		return response.contentBlock().type() == MediaContent.Type.TOOL_USE;
	}

	public boolean isStreamingToolFunctionCallFinish(StreamResponse response) {
		return response.delta() != null && "tool_use".equals(response.delta().get("stop_reason"));
	}

	public StreamResponse emptyChunk() {
		return new StreamResponse(null, null, null, null, null, null, new ArrayList<>());
	}

	public StreamResponse mergeChunks(StreamResponse previous, StreamResponse current) {
		if (previous == null) {
			return current;
		}
		if (current == null) {
			return previous;
		}

		List<MediaContent> mergedContent = mergeToolUses(previous, current);

		if (isStreamingToolFunctionCallFinish(current)) {
			finalizeToolUsesAggregation(mergedContent);
		}

		return new StreamResponse(lastElement(previous.type(), current.type()),
				lastElement(previous.index(), current.index()), lastElement(previous.message(), current.message()),
				lastElement(previous.contentBlock(), current.contentBlock()),
				lastElement(previous.delta(), current.delta()), lastElement(previous.usage(), current.usage()),
				mergedContent);
	}

	private List<MediaContent> mergeToolUses(StreamResponse previous, StreamResponse current) {
		List<MediaContent> mergedContent = new ArrayList<>(previous.mergedToolUses());

		if (current.contentBlock() != null) {
			mergedContent.add(current.contentBlock());
		}
		else if (!mergedContent.isEmpty() && current.delta() != null && current.delta().containsKey("partial_json")) {
			int lastIndex = mergedContent.size() - 1;
			MediaContent previousMedia = mergedContent.get(lastIndex);
			MediaContent currentMedia = new MediaContent(previousMedia.type(), previousMedia.index(),
					previousMedia.id(), previousMedia.name(),
					concat(previousMedia.inputJson(), (String) current.delta().get("partial_json")));

			mergedContent.set(lastIndex, currentMedia);
		}

		return mergedContent;
	}

	public void finalizeToolUsesAggregation(List<MediaContent> mergedToolUses) {
		mergedToolUses.replaceAll(media -> {
			if (media.inputJson() == null) {
				return media;
			}

			return new MediaContent(media.type(), media.source(), media.text(), null, media.id(), media.name(),
					ModelOptionsUtils.jsonToMap(media.inputJson()), null, media.toolUseId(), media.content());
		});
	}

	private static <T> T lastElement(T left, T right) {
		return right != null ? right : left;
	}

	private static String concat(String left, String right) {
		if (left == null) {
			return right;
		}
		if (right == null) {
			return left;
		}

		return left + right;
	}

}
