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

package org.springframework.ai.vectorstore.coherence;

import com.tangosol.io.pof.PofConfigProvider;

/**
 * Contributes the POF type registrations required by
 * {@link CoherenceFilterExpressionConverter} so that filters built by this module can be
 * serialized to remote cluster members when Coherence is configured to use POF.
 *
 * @author Dimitar Proynov
 */
public class CoherencePofConfigProvider implements PofConfigProvider {

	@Override
	public String getConfigURI() {
		return "spring-ai-coherence-pof-config.xml";
	}

}
