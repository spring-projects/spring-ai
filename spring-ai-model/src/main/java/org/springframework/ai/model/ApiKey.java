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
package org.springframework.ai.model;

/**
 * Some model providers API leverage short-lived api keys which must be renewed at regular
 * intervals using another credential. For example, a GCP service account can be exchanged
 * for an api key to call Vertex AI.
 *
 * Model clients use the ApiKey interface to get an api key before they make any request
 * to the model provider. Implementations of this interface can cache the api key and
 * perform a key refresh when it is required.
 *
 * @author Adib Saikali
 */
public interface ApiKey {

	/**
	 * Returns an api key to use for a making request. Users of this method should NOT
	 * cache the returned api key, instead call this method whenever you need an api key.
	 * Implementors of this method MUST ensure that the returned key is not expired.
	 * @return the current value of the api key
	 */
	String getValue();

}
