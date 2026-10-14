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

package org.springframework.ai.session.compaction;

import java.util.Arrays;
import java.util.List;

import org.springframework.util.Assert;

/**
 * A {@link CompactionTrigger} that fires when <em>any</em> of its composed triggers fires
 * (OR semantics).
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class CompositeCompactionTrigger implements CompactionTrigger {

	private final List<CompactionTrigger> triggers;

	private CompositeCompactionTrigger(List<CompactionTrigger> triggers) {
		Assert.notEmpty(triggers, "triggers must not be empty");
		this.triggers = List.copyOf(triggers);
	}

	/**
	 * Creates a composite trigger that fires if any of the given triggers fires.
	 */
	public static CompositeCompactionTrigger anyOf(CompactionTrigger... triggers) {
		Assert.notEmpty(triggers, "triggers must not be empty");
		return new CompositeCompactionTrigger(Arrays.asList(triggers));
	}

	@Override
	public boolean shouldCompact(CompactionRequest request) {
		return this.triggers.stream().anyMatch(t -> t.shouldCompact(request));
	}

}
