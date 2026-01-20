/*
 * Copyright 2025-2026 the original author or authors.
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

package org.springframework.ai.skill.fixtures;

import org.springframework.ai.skill.annotation.Skill;
import org.springframework.ai.skill.annotation.SkillContent;

/**
 * Test Calculator Skill
 *
 * <p>
 * Simple POJO Skill for testing framework functionality
 *
 * @author Semir
 */
@Skill(name = "calculator", description = "A simple calculator skill for basic arithmetic operations",
		source = "example", extensions = { "version=1.0.0", "author=Semir" })
public class CalculatorSkill {

	public CalculatorSkill() {
	}

	@SkillContent
	public String content() {
		return """
				# Calculator Skill

				A simple calculator skill that provides basic arithmetic operations.

				## Features

				- Addition
				- Subtraction
				- Multiplication
				- Division

				## Usage

				Use this skill to perform basic calculations.
				""";
	}

}
