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

package org.springframework.ai.anthropicsdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks cache breakpoints used (max 4 allowed by Anthropic). Non-static to ensure each
 * request has its own instance.
 *
 * @author Soby Chacko
 * @since 2.0.0
 */
class SdkCacheBreakpointTracker {

	private static final Logger logger = LoggerFactory.getLogger(SdkCacheBreakpointTracker.class);

	private int count = 0;

	private boolean hasWarned = false;

	public boolean canUse() {
		return this.count < 4;
	}

	public boolean allBreakpointsAreUsed() {
		return !this.canUse();
	}

	public void use() {
		if (this.count < 4) {
			this.count++;
		}
		else if (!this.hasWarned) {
			logger.warn(
					"Anthropic cache breakpoint limit (4) reached. Additional cache_control directives will be ignored. "
							+ "Consider using fewer cache strategies or simpler content structure.");
			this.hasWarned = true;
		}
	}

	public int getCount() {
		return this.count;
	}

}
