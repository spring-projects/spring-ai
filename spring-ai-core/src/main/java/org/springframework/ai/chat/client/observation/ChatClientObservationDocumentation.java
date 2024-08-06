/*
* Copyright 2024 - 2024 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.springframework.ai.chat.client.observation;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */
public enum ChatClientObservationDocumentation implements ObservationDocumentation {

	/**
	 * AI Chat Client observations
	 */
	AI_CHAT_CLIENT {
		@Override
		public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
			return DefaultChatClientObservationConvention.class;
		}

		@Override
		public KeyName[] getLowCardinalityKeyNames() {
			return LowCardinalityKeyNames.values();
		}

		@Override
		public KeyName[] getHighCardinalityKeyNames() {
			return new KeyName[] {};
		}

	};

	public enum LowCardinalityKeyNames implements KeyName {

		/**
		 * Is the chat model response a stream.
		 */
		STREAM {
			@Override
			public String asString() {
				return "chat.client.stream";
			}
		},

		/**
		 * ChatModel response raw status code, or {@code "IO_ERROR"} in case of
		 * {@code IOException}, or {@code "CLIENT_ERROR"} if no response was received.
		 */
		STATUS {
			@Override
			public String asString() {
				return "chat.client.status";
			}
		},

		/**
		 * Name of the exception thrown during the chat model request, or
		 * {@value KeyValue#NONE_VALUE} if no exception happened.
		 */
		EXCEPTION {
			@Override
			public String asString() {
				return "exception";
			}
		},

	}

	public enum HighCardinalityKeyNames implements KeyName {

		/**
		 * Enabled tool function names.
		 */
		TOOL_FUNCTION_NAMES {
			@Override
			public String asString() {
				return "chat.client.tool.function.names";
			}
		},
		/**
		 * List of configured chat client advisors.
		 */
		CHAT_CLIENT_ADVISOR {
			@Override
			public String asString() {
				return "chat.client.advisors";
			}
		},
		/**
		 * Map of advisor parameters.
		 */
		CHAT_CLIENT_ADVISOR_PARAM {
			@Override
			public String asString() {
				return "chat.client.advisor.params";
			}
		},
		/**
		 * Chat client user text.
		 */
		CHAT_CLIENT_USER_TEXT {
			@Override
			public String asString() {
				return "chat.client.user.text";
			}
		},
		/**
		 * Chat client user parameters.
		 */
		CHAT_CLIENT_USER_PARAM {
			@Override
			public String asString() {
				return "chat.client.user.params";
			}
		},
		/**
		 * Chat client system text.
		 */
		CHAT_CLIENT_SYSTEM_TEXT {
			@Override
			public String asString() {
				return "chat.client.system.text";
			}
		},
		/**
		 * Chat client system parameters.
		 */
		CHAT_CLIENT_SYSTEM_PARAM {
			@Override
			public String asString() {
				return "chat.client.system.params";
			}
		};

		@Override
		public String asString() {
			throw new UnsupportedOperationException("Unimplemented method 'asString'");
		}

	}

}