/*
 * Copyright 2024 the original author or authors.
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

package org.springframework.ai.assistant;

import org.springframework.ai.model.ModelOptions;
import org.springframework.lang.Nullable;

import java.util.Map;

/**
 * AssistantOptions represent the common options, portable across different assistant
 * models.
 *
 * @author Alexandros Pappas
 */
public interface AssistantOptions extends ModelOptions {

	@Nullable
	String getModel();

	@Nullable
	String getName();

	@Nullable
	String getDescription();

	@Nullable
	String getInstructions();

	@Nullable
	Map<String, String> getMetadata();

	@Nullable
	Double getTemperature();

	@Nullable
	Double getTopP();

}
