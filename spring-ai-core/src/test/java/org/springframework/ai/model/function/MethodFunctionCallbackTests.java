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
package org.springframework.ai.model.function;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */

public class MethodFunctionCallbackTests {

	record MyRecord(String foo, String bar) {
	}

	public enum Unit {

		CELSIUS, FAHRENHEIT

	}

	public static class TestClassWithFunctionMethods {

		public static void argumentLessReturnVoid() {
			arguments.put("method called", "argumentLessReturnVoid");
		}

		public static String myStaticMethod(String city, Unit unit, int intNumber, MyRecord record,
				List<Integer> intList) {
			System.out.println("City: " + city + " Unit: " + unit + " intNumber: " + intNumber + " Record: " + record
					+ " List: " + intList);

			arguments.put("city", city);
			arguments.put("unit", unit);
			arguments.put("intNumber", intNumber);
			arguments.put("record", record);
			arguments.put("intList", intList);

			return "23";
		}

		public String myNonStaticMethod(String city, Unit unit, int intNumber, MyRecord record, List<Integer> intList) {
			System.out.println("City: " + city + " Unit: " + unit + " intNumber: " + intNumber + " Record: " + record
					+ " List: " + intList);

			arguments.put("city", city);
			arguments.put("unit", unit);
			arguments.put("intNumber", intNumber);
			arguments.put("record", record);
			arguments.put("intList", intList);

			return "23";
		}

	}

	public static Map<String, Object> arguments = new ConcurrentHashMap<>();

	@BeforeEach
	public void beforeEach() {
		arguments.clear();
	}

	String value = """
			{
			  "unit": "CELSIUS",
			  "city": "Barcelona",
			  "intNumber": 123,
			  "record": {
				"foo": "foo",
				"bar": "bar"
			  },
			  "intList": [1, 2, 3]
			}
			""";

	@Test
	public void staticMethod() throws NoSuchMethodException, SecurityException {

		Method method = ReflectionUtils.findMethod(TestClassWithFunctionMethods.class, "myStaticMethod", String.class,
				Unit.class, int.class, MyRecord.class, List.class);

		assertThat(method).isNotNull();
		assertThat(Modifier.isStatic(method.getModifiers())).isTrue();

		var functionCallback = MethodFunctionCallback.builder()
			.method(method)
			.description("weather at location")
			.mapper(new ObjectMapper())
			.build();

		String response = functionCallback.call(value);

		assertThat(response).isEqualTo("23");

		assertThat(arguments).hasSize(5);
		assertThat(arguments.get("city")).isEqualTo("Barcelona");
		assertThat(arguments.get("unit")).isEqualTo(Unit.CELSIUS);
		assertThat(arguments.get("intNumber")).isEqualTo(123);
		assertThat(arguments.get("record")).isEqualTo(new MyRecord("foo", "bar"));
		assertThat(arguments.get("intList")).isEqualTo(List.of(1, 2, 3));
	}

	@Test
	public void nonStaticMethod() throws NoSuchMethodException, SecurityException {

		Method method = TestClassWithFunctionMethods.class.getMethod("myNonStaticMethod", String.class, Unit.class,
				int.class, MyRecord.class, List.class);

		assertThat(Modifier.isStatic(method.getModifiers())).isFalse();

		var functionCallback = MethodFunctionCallback.builder()
			.functionObject(new TestClassWithFunctionMethods())
			.method(method)
			.description("weather at location")
			.mapper(new ObjectMapper())
			.build();

		String response = functionCallback.call(value);

		assertThat(response).isEqualTo("23");

		assertThat(arguments).hasSize(5);
		assertThat(arguments.get("city")).isEqualTo("Barcelona");
		assertThat(arguments.get("unit")).isEqualTo(Unit.CELSIUS);
		assertThat(arguments.get("intNumber")).isEqualTo(123);
		assertThat(arguments.get("record")).isEqualTo(new MyRecord("foo", "bar"));
		assertThat(arguments.get("intList")).isEqualTo(List.of(1, 2, 3));
	}

	@Test
	public void noArgsNoReturnMethod() throws NoSuchMethodException, SecurityException {

		Method method = TestClassWithFunctionMethods.class.getMethod("argumentLessReturnVoid");

		assertThat(Modifier.isStatic(method.getModifiers())).isTrue();

		var functionCallback = MethodFunctionCallback.builder()
			.method(method)
			.description("weather at location")
			.mapper(new ObjectMapper())
			.build();

		String response = functionCallback.call(value);

		assertThat(response).isEqualTo("Done");

		assertThat(arguments.get("method called")).isEqualTo("argumentLessReturnVoid");
	}

}
