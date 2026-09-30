/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.mcp.annotation;

/**
 * Defines the visibility of an MCP App tool per SEP-1865.
 *
 * <p>
 * The visibility controls which consumers can see and invoke the tool:
 * <ul>
 * <li>{@link #MODEL} — visible to the LLM agent (default for all tools)</li>
 * <li>{@link #APP} — visible to the MCP App iframe (backend tools called by the UI)</li>
 * </ul>
 *
 * <p>
 * On the wire, visibility is serialized as a JSON array of lowercase strings in
 * {@code _meta.ui.visibility}, e.g. {@code ["model"]} or {@code ["app"]} or
 * {@code ["model", "app"]}.
 *
 * @author Alexandros Pappas
 * @see McpTool#visibility()
 */
public enum Visibility {

	/**
	 * Tool is visible to the LLM agent.
	 */
	MODEL("model"),

	/**
	 * Tool is visible to the MCP App iframe (backend tool called by UI, not the LLM).
	 */
	APP("app");

	private final String wireValue;

	Visibility(String wireValue) {
		this.wireValue = wireValue;
	}

	/**
	 * Returns the lowercase wire-format string used in {@code _meta.ui.visibility}.
	 * @return the wire value, e.g. "model" or "app"
	 */
	public String getWireValue() {
		return this.wireValue;
	}

}
