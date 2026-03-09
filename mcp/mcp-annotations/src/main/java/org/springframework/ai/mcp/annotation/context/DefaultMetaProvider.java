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

package org.springframework.ai.mcp.annotation.context;

import java.util.Map;

/**
 * Default {@link MetaProvider} implementation that disables the "_meta" field in tool,
 * prompt, resource declarations.
 *
 * <p>
 * This provider deliberately returns {@code null} from {@link #getMeta()} to signal that
 * no "_meta" information is included.
 * </p>
 *
 * <p>
 * Use this when your tool, prompt, or resource does not need to expose any meta
 * information or you want to keep responses minimal by default.
 * </p>
 *
 * @author Vadzim Shurmialiou
 * @author Craig Walls
 */
public class DefaultMetaProvider implements MetaProvider {

	/**
	 * Returns {@code null} to indicate that no "_meta" field should be included in.
	 */
	@Override
	public Map<String, Object> getMeta() {
		return null;
	}

}
