/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.chat.history.store;

import org.springframework.ai.chat.messages.MessageType;

/**
 * @author Christian Tzolov
 */
// @Entity
public class MessageEntity {

	// @Id
	private String id;

	private String sessionId;

	private String textContent;

	// @Enumerated(EnumType.STRING)
	private MessageType type;

	// Media content

	private String properties;

}
