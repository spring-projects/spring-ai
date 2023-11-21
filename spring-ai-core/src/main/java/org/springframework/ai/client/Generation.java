/*
 * Copyright 2023 the original author or authors.
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

package org.springframework.ai.client;

import java.util.Collections;
import java.util.Map;

import org.springframework.ai.metadata.ChoiceMetadata;
import org.springframework.lang.Nullable;

public class Generation {

	// Just text for now
	private final String text;

	private Map<String, Object> info;

	private ChoiceMetadata choiceMetadata;

	public Generation(String text) {
		this(text, Collections.emptyMap());
	}

	public Generation(String text, Map<String, Object> info) {
		this.text = text;
		this.info = Map.copyOf(info);
	}

	public String getText() {
		return this.text;
	}

	public Map<String, Object> getInfo() {
		return this.info;
	}

	public ChoiceMetadata getChoiceMetadata() {
		ChoiceMetadata choiceMetadata = this.choiceMetadata;
		return choiceMetadata != null ? choiceMetadata : ChoiceMetadata.NULL;
	}

	public Generation withChoiceMetadata(@Nullable ChoiceMetadata choiceMetadata) {
		this.choiceMetadata = choiceMetadata;
		return this;
	}

	@Override
	public String toString() {
		return "Generation{" + "text='" + text + '\'' + ", info=" + info + '}';
	}

}
