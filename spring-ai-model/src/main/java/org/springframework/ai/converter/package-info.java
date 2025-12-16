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
 * Provides converters for transforming AI model text outputs into structured Java types.
 *
 * <p>
 * The output of AI models traditionally arrives as a {@code String}, even if you ask for
 * the reply to be in JSON. This package provides specialized converters that employ
 * meticulously crafted prompts and parsing logic to convert text responses into usable
 * data structures for application integration.
 *
 * <p>
 * For detailed documentation and usage examples, see the <a href=
 * "https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html">Structured
 * Output Converter Reference Guide</a>.
 */
@NullMarked
package org.springframework.ai.converter;

import org.jspecify.annotations.NullMarked;
