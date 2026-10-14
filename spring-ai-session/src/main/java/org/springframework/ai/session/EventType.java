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

package org.springframework.ai.session;

import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import org.springframework.util.Assert;

/**
 * What a {@link SessionEvent} records. A type is its name: the well-known types are
 * constants here, and an application or an agent runtime defines its own with
 * {@link #of(String)}; every store keeps the name, and a filter by type works for any of
 * them. Only a {@link #MESSAGE} event takes part in prompt assembly and compaction, and
 * only a type that {@link #canCarryMessage() carries a message} wraps a Spring AI
 * message.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class EventType {

	private static final Pattern NAME = Pattern.compile("[A-Z][A-Z0-9_.]*");

	/**
	 * A conversation message; the event carries a
	 * {@link org.springframework.ai.chat.messages.Message}.
	 */
	public static final EventType MESSAGE = of("MESSAGE");

	/**
	 * A compaction boundary: the payload names, by id, the events that make up the active
	 * view from here on; see
	 * {@link org.springframework.ai.session.compaction.CompactionBoundary}.
	 */
	public static final EventType COMPACTION = of("COMPACTION");

	private static final Set<EventType> MESSAGE_BEARING = Set.of(MESSAGE);

	private final String name;

	private EventType(String name) {
		this.name = name;
	}

	/**
	 * The event type with the given name: a well-known one, or an application's own. A
	 * name is upper case, digits, underscores and dots, like {@code MY_APP.PROGRESS}.
	 * @throws IllegalArgumentException if the name is not of that form
	 */
	@JsonCreator
	public static EventType of(String name) {
		Assert.hasText(name, "name must not be null or empty");
		Assert.isTrue(NAME.matcher(name).matches(),
				() -> "Event type name '" + name + "' must match " + NAME.pattern());
		return new EventType(name);
	}

	/** The name, as stores keep it and as JSON carries it. */
	@JsonValue
	public String name() {
		return this.name;
	}

	/**
	 * Whether an event of this type carries a Spring AI message: a {@link #MESSAGE} event
	 * does.
	 */
	public boolean canCarryMessage() {
		return MESSAGE_BEARING.contains(this);
	}

	@Override
	public boolean equals(Object other) {
		return this == other || (other instanceof EventType type && this.name.equals(type.name));
	}

	@Override
	public int hashCode() {
		return this.name.hashCode();
	}

	@Override
	public String toString() {
		return this.name;
	}

}
