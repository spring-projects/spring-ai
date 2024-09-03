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

package org.springframework.ai.moderation;

import org.springframework.ai.model.AbstractResponseMetadata;
import org.springframework.ai.model.ResponseMetadata;

/**
 * Defines the metadata associated with a moderation response, extending a base response
 * interface. This interface is intended to provide additional context or data about the
 * moderation process result.
 *
 * @author Ahmed Yousri
 * @since 1.0.0
 */
public class ModerationResponseMetadata extends AbstractResponseMetadata implements ResponseMetadata {

}
