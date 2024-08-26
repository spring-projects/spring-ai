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
package org.springframework.ai.vertexai.anthropic.model.stream;

import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Special event used to aggregate multiple tool use events into a single event with list
 * of aggregated ContentBlockToolUse.
 *
 * @author Alessio Bertazzo
 * @since 1.0.0
 */
public class ToolUseAggregationEvent implements StreamEvent {

	private Integer index;

	private String id;

	private String name;

	private String partialJson = "";

	private List<ContentBlockStartEvent.ContentBlockToolUse> toolContentBlocks = new ArrayList<>();

	@Override
	public EventType type() {
		return EventType.TOOL_USE_AGGREGATE;
	}

	public List<ContentBlockStartEvent.ContentBlockToolUse> getToolContentBlocks() {
		return this.toolContentBlocks;
	}

	public boolean isEmpty() {
		return (this.index == null || this.id == null || this.name == null || !StringUtils.hasText(this.partialJson));
	}

	public ToolUseAggregationEvent withIndex(Integer index) {
		this.index = index;
		return this;
	}

	public ToolUseAggregationEvent withId(String id) {
		this.id = id;
		return this;
	}

	public ToolUseAggregationEvent withName(String name) {
		this.name = name;
		return this;
	}

	public ToolUseAggregationEvent appendPartialJson(String partialJson) {
		this.partialJson = this.partialJson + partialJson;
		return this;
	}

	public void squashIntoContentBlock() {
		Map<String, Object> map = (StringUtils.hasText(this.partialJson))
				? ModelOptionsUtils.jsonToMap(this.partialJson) : Map.of();
		this.toolContentBlocks.add(new ContentBlockStartEvent.ContentBlockToolUse("tool_use", this.id, this.name, map));
		this.index = null;
		this.id = null;
		this.name = null;
		this.partialJson = "";
	}

	@Override
	public String toString() {
		return "EventToolUseBuilder [index=" + index + ", id=" + id + ", name=" + name + ", partialJson=" + partialJson
				+ ", toolUseMap=" + toolContentBlocks + "]";
	}

}
