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

package org.springframework.ai.systemone.api;

/**
 * The model aliases of the hosted System One service. Aliases are resolved server side,
 * so pinning a version is only necessary when an application must not move underneath
 * itself. Other System One servers name their models differently.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public enum SystemOneModel {

	/** Alias tracking the current flagship release. */
	LATEST("jev-latest"),

	/** Alias tracking the current preview release. */
	PREVIEW("jev-preview");

	private final String id;

	SystemOneModel(String id) {
		this.id = id;
	}

	/**
	 * @return the model name sent to the server
	 */
	public String id() {
		return this.id;
	}

}
