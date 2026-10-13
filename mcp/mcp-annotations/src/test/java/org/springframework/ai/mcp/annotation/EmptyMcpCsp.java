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

import java.lang.annotation.Annotation;

/**
 * Test fixture: an {@link McpCsp} instance that declares nothing, equivalent to the
 * default of {@link McpResource#csp()}. Used by tests that implement {@link McpResource}
 * programmatically.
 *
 * @author Alexandros Pappas
 */
public final class EmptyMcpCsp implements McpCsp {

	public static final EmptyMcpCsp INSTANCE = new EmptyMcpCsp();

	private EmptyMcpCsp() {
	}

	@Override
	public Class<? extends Annotation> annotationType() {
		return McpCsp.class;
	}

	@Override
	public String[] connectDomains() {
		return new String[0];
	}

	@Override
	public String[] resourceDomains() {
		return new String[0];
	}

	@Override
	public String[] frameDomains() {
		return new String[0];
	}

	@Override
	public String[] baseUriDomains() {
		return new String[0];
	}

}
