/*
 * Copyright 2025-2025 the original author or authors.
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

/**
 * Core support for Model Context Protocol (MCP) integration in Spring AI.
 * <p>
 * This package provides the foundational classes and utilities for integrating MCP with
 * Spring AI's tool system. It includes:
 * <ul>
 * <li>Tool callback implementations for both synchronous and asynchronous MCP
 * operations</li>
 * <li>Tool callback providers that discover and expose MCP tools</li>
 * <li>Utility classes for converting between Spring AI and MCP tool representations</li>
 * <li>Support for customizing MCP client behavior</li>
 * </ul>
 * <p>
 * The classes in this package enable seamless integration between Spring AI applications
 * and MCP servers, allowing language models to discover and invoke tools through a
 * standardized protocol.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
@NullMarked
package org.springframework.ai.mcp;

import org.jspecify.annotations.NullMarked;
