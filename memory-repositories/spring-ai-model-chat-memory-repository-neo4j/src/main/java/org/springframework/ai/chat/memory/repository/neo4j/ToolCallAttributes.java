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

package org.springframework.ai.chat.memory.repository.neo4j;

/**
 * @author Enrico Rampazzo
 * @deprecated since 2.1.0, for removal in 3.0.0, in favor of session memory:
 * {@code org.springframework.ai.session.SessionRepository} in {@code spring-ai-session},
 * with a JDBC store in {@code spring-ai-session-jdbc}. There is no session store for
 * Neo4j yet; one is planned before 3.0.0, when this module is removed.
 */
@Deprecated(since = "2.1.0", forRemoval = true)
@SuppressWarnings("removal")
public enum ToolCallAttributes implements AttributeGetter {

	ID("id"), NAME("name"), ARGUMENTS("arguments"), TYPE("type"), IDX("idx");

	private final String value;

	ToolCallAttributes(String value) {
		this.value = value;
	}

	public String getValue() {
		return this.value;
	}

}
