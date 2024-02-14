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

package org.springframework.ai.moderation;

import org.springframework.ai.model.ModelRequest;
import java.util.Objects;

/**
 * Represents a prompt for moderation containing a single message and the options for the
 * moderation model. This class offers constructors to create a prompt from a single
 * message or a simple instruction string, allowing for customization of moderation
 * options through `ModerationOptions`. It simplifies creating moderation requests for
 * different use cases.
 *
 * @author Ahmed Yousri
 * @since 1.0.0
 */
public class ModerationPrompt implements ModelRequest<ModerationMessage> {

	private final ModerationMessage message;

	private ModerationOptions moderationModelOptions;

	public ModerationPrompt(ModerationMessage message, ModerationOptions moderationModelOptions) {
		this.message = message;
		this.moderationModelOptions = moderationModelOptions;
	}

	public ModerationPrompt(String instructions, ModerationOptions moderationOptions) {
		this(new ModerationMessage(instructions), moderationOptions);
	}

	public ModerationPrompt(String instructions) {
		this(new ModerationMessage(instructions), ModerationOptionsBuilder.builder().build());
	}

	@Override
	public ModerationMessage getInstructions() {
		return message;
	}

	public ModerationOptions getOptions() {
		return moderationModelOptions;
	}

	public void setOptions(ModerationOptions moderationModelOptions) {
		this.moderationModelOptions = moderationModelOptions;
	}

	@Override
	public String toString() {
		return "ModerationPrompt{" + "message=" + message + ", moderationModelOptions=" + moderationModelOptions + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof ModerationPrompt))
			return false;
		ModerationPrompt that = (ModerationPrompt) o;
		return Objects.equals(message, that.message)
				&& Objects.equals(moderationModelOptions, that.moderationModelOptions);
	}

	@Override
	public int hashCode() {
		return Objects.hash(message, moderationModelOptions);
	}

}
