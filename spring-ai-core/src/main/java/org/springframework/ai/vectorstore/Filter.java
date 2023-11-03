/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.vectorstore;

import java.util.List;

/**
 * @author Christian Tzolov
 */
public class Filter {

	// filters runtime model
	public interface Operand {

	}

	public record K(String key) implements Operand {
	}

	public record V(Object value) implements Operand {
	}

	enum TYPE {

		and, or, eq, ne, gt, gte, lt, lte, in, nin

	}

	public record OP(TYPE type, Operand left, Operand right) implements Operand {
	}

	public record GROUP(Operand content) implements Operand {
	};

	// Parsers
	public static abstract class AbstractParser {

		public String parse(Operand operand) {
			var context = new StringBuilder();
			this.parse(operand, context);
			return context.toString();
		}

		protected void parse(Operand operand, StringBuilder context) {

			if (operand instanceof GROUP group) {
				this.doStartGroup(group, context);
				this.parse(group.content, context);
				this.doEndGroup(group, context);
			}
			else if (operand instanceof K key) {
				this.doKey(key, context);
			}
			else if (operand instanceof V value) {
				this.doValue(value, context);
			}
			else if (operand instanceof OP operation) {
				if ((operation.type != TYPE.and && operation.type != TYPE.or) && !(operation.right instanceof V)) {
					throw new RuntimeException("Non AND/OR operations must have Value right argument!");
				}
				this.parse(operation.left, context);
				this.doOperation(operation, context);
				this.parse(operation.right, context);
			}
		}

		protected abstract void doOperation(OP op, StringBuilder context);

		protected abstract void doKey(K key, StringBuilder context);

		protected void doValue(V value, StringBuilder context) {
			if (value.value instanceof List list) {
				doStartValueRange(value, context);
				int c = 0;
				for (Object v : list) {
					this.doSingleValue(v, context);
					if (c++ < list.size() - 1) {
						this.doAddValueRangeSpitter(value, context);
					}
				}
				this.doEndValueRange(value, context);
			}
			else {
				this.doSingleValue(value.value, context);
			}

		}

		protected abstract void doSingleValue(Object value, StringBuilder context);

		protected abstract void doStartGroup(GROUP group, StringBuilder context);

		protected abstract void doEndGroup(GROUP group, StringBuilder context);

		protected abstract void doStartValueRange(V listValue, StringBuilder context);

		protected abstract void doEndValueRange(V listValue, StringBuilder context);

		protected abstract void doAddValueRangeSpitter(V listValue, StringBuilder context);

	}

	public static class PrintParser extends AbstractParser {

		public void doOperation(OP op, StringBuilder context) {
			context.append(" " + op.type + " ");
		}

		public void doKey(K key, StringBuilder context) {
			context.append(key.key);
		}

		@Override
		protected void doStartValueRange(V listValue, StringBuilder context) {
			context.append("[");

		}

		@Override
		protected void doEndValueRange(V listValue, StringBuilder context) {
			context.append("]");
		}

		@Override
		protected void doSingleValue(Object value, StringBuilder context) {
			if (value instanceof String text) {
				context.append(String.format("\"%s\"", value));
			}
			else {
				context.append(value);
			}
		}

		public void doStartGroup(GROUP group, StringBuilder context) {
			context.append("(");
		}

		public void doEndGroup(GROUP group, StringBuilder context) {
			context.append(")");
		}

		@Override
		protected void doAddValueRangeSpitter(V listValue, StringBuilder context) {
			context.append(",");
		}

	}

	public static class JsonPathParser extends AbstractParser {

		@Override
		protected void doOperation(OP op, StringBuilder context) {
			switch (op.type) {
				case and:
					context.append(" && ");
					break;
				case or:
					context.append(" || ");
					break;
				case eq:
					context.append(" == ");
					break;
				case ne:
					context.append(" != ");
					break;
				case lt:
					context.append(" < ");
					break;
				case lte:
					context.append(" <= ");
					break;
				case gt:
					context.append(" > ");
					break;
				case gte:
					context.append(" >= ");
					break;
				case in:
					context.append(" in ");
					break;
				case nin:
					context.append(" nin ");
					break;

				default:
					throw new RuntimeException("Not supported operation:" + op.type);
			}
		}

		@Override
		protected void doKey(K key, StringBuilder context) {
			context.append("$." + key.key);
		}

		@Override
		protected void doStartGroup(GROUP group, StringBuilder context) {
			context.append("(");
		}

		@Override
		protected void doEndGroup(GROUP group, StringBuilder context) {
			context.append(")");
		}

		@Override
		protected void doSingleValue(Object value, StringBuilder context) {
			if (value instanceof String text) {
				context.append(String.format("\"%s\"", value));
			}
			else {
				context.append(value);
			}
		}

		@Override
		protected void doStartValueRange(V listValue, StringBuilder context) {
			context.append("[");
		}

		@Override
		protected void doEndValueRange(V listValue, StringBuilder context) {
			context.append("]");
		}

		@Override
		protected void doAddValueRangeSpitter(V listValue, StringBuilder context) {
			context.append(",");
		}

	}

	// public static class PineconeParser {

	// public String parse(Operand operand) {
	// var context = new StringBuilder();
	// this.parse(operand, context);
	// return context.toString();
	// }

	// protected void parse(Operand operand, StringBuilder context) {

	// if (operand instanceof GROUP group) {
	// this.doStartGroup(group, context);
	// this.parse(group.content, context);
	// this.doEndGroup(group, context);
	// }
	// else if (operand instanceof K key) {
	// this.doKey(key, context);
	// }
	// else if (operand instanceof V value) {
	// this.doValue(value, context);
	// }
	// else if (operand instanceof OP operation) {
	// if ((operation.type != TYPE.and && operation.type != TYPE.or) && !(operation.right
	// instanceof V)) {
	// throw new RuntimeException("Non AND/OR operations must have Value right
	// argument!");
	// }
	// this.parse(operation.left, context);
	// this.doOperation(operation, context);
	// this.parse(operation.right, context);
	// }
	// }

	// protected void doOperation(OP op, StringBuilder context) {
	// context.append(" $" + op.type + " ");
	// }

	// }

	public static void main(String[] args) {

		// year >= 2020 OR country == "BG" AND city != "Sofia"
		var exp1 = new OP(TYPE.or, new OP(TYPE.gte, new K("year"), new V(2020)), new OP(TYPE.and,
				new OP(TYPE.eq, new K("country"), new V("BG")), new OP(TYPE.ne, new K("city"), new V("Sofia"))));

		// (year >= 2020 OR country == "BG") AND city nin ["Sofia", "Plovdiv"]
		var exp2 = new OP(TYPE.and,
				new GROUP(new OP(TYPE.or, new OP(TYPE.eq, new K("country"), new V("BG")),
						new OP(TYPE.gte, new K("year"), new V(2020)))),
				new OP(TYPE.nin, new K("city"), new V(List.of("Sofia", "Varna"))));

		var exp3 = new OP(TYPE.and, new OP(TYPE.eq, new K("isLive"), new V(true)),
				new OP(TYPE.and, new OP(TYPE.gte, new K("year"), new V(2020)),
						new OP(TYPE.in, new K("country"), new V(List.of("BG", "NL", "US")))));

		var parser = new JsonPathParser();
		System.out.println("EXP1 = " + parser.parse(exp1));

		System.out.println("EXP2 = " + parser.parse(exp2));

		System.out.println("EXP3 = " + parser.parse(exp3));

	}

}
