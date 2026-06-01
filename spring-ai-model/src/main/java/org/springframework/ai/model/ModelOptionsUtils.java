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

package org.springframework.ai.model;

import org.jspecify.annotations.Nullable;

import org.springframework.lang.Contract;
import org.springframework.util.ObjectUtils;

/**
 * Utility class for manipulating {@link ModelOptions} objects.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author chabinhwang
 * @author Sebastien Deleuze
 * @since 0.8.0
 */
public abstract class ModelOptionsUtils {

	/**
	 * Return the runtime value if not empty, or else the default value.
	 */
	@Contract("_, !null -> !null")
	public static <T> @Nullable T mergeOption(@Nullable T runtimeValue, @Nullable T defaultValue) {
		return ObjectUtils.isEmpty(runtimeValue) ? defaultValue : runtimeValue;
	}

}
