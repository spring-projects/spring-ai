/*
 * Copyright 2023 the original author or authors.
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
package org.springframework.ai.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.util.StandardLineEnding.CR;
import static org.springframework.ai.util.StandardLineEnding.CRLF;
import static org.springframework.ai.util.StandardLineEnding.LF;
import static org.springframework.ai.util.StandardLineEnding.findByLineSeparator;

/**
 * @author Kirk Lund
 */
class StandardLineEndingTest {

	@Test
	void lineSeparator_returnsSlashR_forCR() {
		assertThat(CR.lineSeparator()).isEqualTo("\r");
	}

	@Test
	void lineSeparator_returnsSlashN_forLF() {
		assertThat(LF.lineSeparator()).isEqualTo("\n");
	}

	@Test
	void lineSeparator_returnsSlashRSlashN_forCRLF() {
		assertThat(CRLF.lineSeparator()).isEqualTo("\r\n");
	}

	@Test
	void findByLineSeparator_returnsCR_forSlashR() {
		assertThat(findByLineSeparator("\r")).isEqualTo(CR);
	}

	@Test
	void findByLineSeparator_returnsLF_forSlashN() {
		assertThat(findByLineSeparator("\n")).isEqualTo(LF);
	}

	@Test
	void findByLineSeparator_returnsCR_forSlashRSlashN() {
		assertThat(findByLineSeparator("\r\n")).isEqualTo(CRLF);
	}

}