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
import org.stringtemplate.v4.misc.STMessage;

/**
 * {@link STErrorListener} implementation that logs errors using SLF4J.
 */
public class Slf4jStErrorListener implements STErrorListener {

    private static final Logger logger = LoggerFactory.getLogger(StTemplateRenderer.class);

    @Override
    public void compileTimeError(STMessage msg) {
        logger.error("StringTemplate compile error: {}", msg);
    }

    @Override
    public void runTimeError(STMessage msg) {
        logger.error("StringTemplate runtime error: {}", msg);
    }

    @Override
    public void IOError(STMessage msg) {
        logger.error("StringTemplate IO error: {}", msg);
    }

    @Override
    public void internalError(STMessage msg) {
        logger.error("StringTemplate internal error: {}", msg);
    }

}
