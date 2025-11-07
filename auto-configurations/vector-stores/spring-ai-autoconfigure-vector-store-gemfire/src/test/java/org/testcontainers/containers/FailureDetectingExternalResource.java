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

package org.testcontainers.containers;

import java.util.ArrayList;
import java.util.List;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.MultipleFailureException;
import org.junit.runners.model.Statement;

/**
 * {@link TestRule} which is called before and after each test, and also is notified on
 * success/failure.
 *
 * This mimics the behaviour of TestWatcher to some degree, but failures occurring in this
 * rule do not contribute to the overall failure count (which can otherwise cause strange
 * negative test success figures).
 */
public class FailureDetectingExternalResource implements TestRule {

	@Override
	public Statement apply(Statement base, Description description) {

		return new Statement() {
			@Override
			public void evaluate() throws Throwable {

				List<Throwable> errors = new ArrayList<Throwable>();

				starting(description);

				try {
					base.evaluate();
					succeeded(description);
				}
				catch (Throwable e) {
					errors.add(e);
					failed(e, description);
				}
				finally {
					finished(description);
				}

				MultipleFailureException.assertEmpty(errors);
			}
		};
	}

	protected void starting(Description description) {

	}

	protected void succeeded(Description description) {
	}

	protected void failed(Throwable e, Description description) {
	}

	protected void finished(Description description) {
	}

}
