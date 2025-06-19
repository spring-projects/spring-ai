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

package org.springframework.ai.template.st;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stringtemplate.v4.STErrorListener;
import org.stringtemplate.v4.misc.ErrorType;
import org.stringtemplate.v4.misc.STMessage;

/**
 * An ErrorListener for the {@link StTemplateRenderer} rendering exceptions.
 * <p>
 * By default, StringTemplate uses
 * {@link org.stringtemplate.v4.misc.ErrorManager#DEFAULT_ERROR_LISTENER} as the exception
 * handler, which outputs exceptions via System.err.println. This can lead to a loss of
 * detailed exception logs from the user's perspective. The current ErrorListener retains
 * the behavior of the default handler but additionally outputs the exceptions through
 * logging mechanisms.
 * </p>
 *
 * @author Sun Yuhan
 */
public class StTemplateRenderErrorListener implements STErrorListener {

	private final Logger logger = LoggerFactory.getLogger(StTemplateRenderErrorListener.class);

	@Override
	public void compileTimeError(STMessage msg) {
		logger.error(msg.toString());
	}

	@Override
	public void runTimeError(STMessage msg) {
		if (msg.error != ErrorType.NO_SUCH_PROPERTY) { // ignore these
			logger.error(msg.toString());
		}
	}

	@Override
	public void IOError(STMessage msg) {
		logger.error(msg.toString());
	}

	@Override
	public void internalError(STMessage msg) {
		logger.error(msg.toString());
	}

}
