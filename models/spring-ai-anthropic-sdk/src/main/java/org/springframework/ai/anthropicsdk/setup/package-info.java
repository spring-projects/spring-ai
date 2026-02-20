/*
 * Copyright 2023-2025 the original author or authors.
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
 * Setup and configuration utilities for creating Anthropic SDK client instances.
 *
 * <p>
 * This package contains factory classes for configuring and instantiating the Anthropic
 * Java SDK clients used by Spring AI's Anthropic integration. It handles
 * environment-based configuration, credential detection, and provides sensible defaults
 * for connection settings.
 *
 * <p>
 * <b>Key Class:</b>
 * <ul>
 * <li>{@link org.springframework.ai.anthropicsdk.setup.AnthropicSdkSetup} - Factory for
 * creating SDK clients</li>
 * </ul>
 *
 * @since 2.0.0
 * @see org.springframework.ai.anthropicsdk.setup.AnthropicSdkSetup
 */
@NullMarked
package org.springframework.ai.anthropicsdk.setup;

import org.jspecify.annotations.NullMarked;
