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

package org.springframework.ai.model.session.autoconfigure;

import org.springframework.ai.session.SessionService;

/**
 * Wraps the auto-configured {@link SessionService} in another one. Every bean of this
 * type is applied, in bean order, to the service {@link SessionServiceAutoConfiguration}
 * creates, so a cross-cutting concern such as authorization is added explicitly rather
 * than by post-processing every {@code SessionService} bean. A {@code SessionService} the
 * application defines itself is left alone.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
@FunctionalInterface
public interface SessionServiceDecorator {

	/**
	 * Returns the service to expose in place of the given one.
	 * @param sessionService the service built so far
	 * @return the decorated service
	 */
	SessionService decorate(SessionService sessionService);

}
