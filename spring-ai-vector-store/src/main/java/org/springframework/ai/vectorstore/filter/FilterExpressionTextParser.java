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

package org.springframework.ai.vectorstore.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.antlr.v4.runtime.ANTLRErrorStrategy;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.misc.ParseCancellationException;

import org.springframework.ai.vectorstore.filter.antlr4.FiltersBaseVisitor;
import org.springframework.ai.vectorstore.filter.antlr4.FiltersLexer;
import org.springframework.ai.vectorstore.filter.antlr4.FiltersParser;
import org.springframework.ai.vectorstore.filter.antlr4.FiltersParser.NotExpressionContext;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.util.Assert;

/**
 *
 * Parse a textual, vector-store agnostic, filter expression language into
 * {@link Filter.Expression}.
 *
 * The vector-store agnostic, filter expression language is defined by a formal ANTLR4
 * grammar (Filters.g4). The language looks and feels like a subset of the well known SQL
 * WHERE filter expressions. For example, you can use the parser like this:
 *
 * <pre>{@code
 *
 * var parser = new FilterExpressionTextParser();
 *
 * exp1 = parser.parse("country == 'BG'"); // creates:
 *  |
 *  +->	new Expression(EQ, new Key("country"), new Value("BG"));
 *
 * exp2 = parser.parse("genre == 'drama' && year >= 2020"); // creates:
 *  |
 *  +->	new Expression(AND,
 * 			new Expression(EQ, new Key("genre"), new Value("drama")),
 * 			new Expression(GTE, new Key("year"), new Value(2020)));
 *
 * exp3 = parser.parse("genre in ['comedy', 'documentary', 'drama']");
 *  |
 *  +->	new Expression(IN, new Key("genre"), new Value(List.of("comedy", "documentary", "drama")));
 *
 * exp4 = parser.parse("year >= 2020 || country == 'BG' && city != 'Sofia'");
 *  |
 *  +->	new Expression(OR,
 * 			new Expression(GTE, new Key("year"), new Value(2020)),
 * 			new Expression(AND,
 * 					new Expression(EQ, new Key("country"), new Value("BG")),
 * 					new Expression(NE, new Key("city"), new Value("Sofia"))));
 *
 * exp5 = parser.parse("(year >= 2020 || country == \"BG\") && city NOT IN ['Sofia', \"Plovdiv\"]"); // creates:
 *  |
 *  +->	new Expression(AND,
 * 			new Group(new Expression(OR, new Expression(EQ, new Key("country"), new Value("BG")),
 * 				new Expression(GTE, new Key("year"), new Value(2020)))),
 * 			new Expression(NIN, new Key("city"), new Value(List.of("Sofia", "Varna"))));
 *
 * exp6 = parser.parse("isOpen == true && year >= 2020 && country IN ['BG', 'NL', 'US']"); // creates:
 *  |
 *  +->	new Expression(AND,
 * 			new Expression(EQ, new Key("isOpen"), new Value(true)),
 * 			new Expression(AND,
 * 				new Expression(GTE, new Key("year"), new Value(2020)),
 * 				new Expression(IN, new Key("country"), new Value(List.of("BG", "NL", "US")))));
 *
 * exp7 = parser.parse("price >= 15.6 && price <= 20.13"); // creates:
 *  |
 *  +->	new Expression(AND,
 * 			new Expression(GTE, new Key("price"), new Value(15.6)),
 * 			new Expression(LTE, new Key("price"), new Value(20.13)));
 *
 * }</pre>
 *
 * @author Christian Tzolov
 * @author Sun Yuhan
 */
public class FilterExpressionTextParser {

	private static final String WHERE_PREFIX = "WHERE";

	private final DescriptiveErrorListener errorListener;

	private final ANTLRErrorStrategy errorHandler;

	private final Map<String, Filter.Expression> cache = new ConcurrentHashMap<>();

	public FilterExpressionTextParser() {
		this(new BailErrorStrategy());
	}

	public FilterExpressionTextParser(ANTLRErrorStrategy handler) {
		this.errorListener = DescriptiveErrorListener.INSTANCE;
		this.errorHandler = handler;
	}

	public Filter.Expression parse(String textFilterExpression) {

		Assert.hasText(textFilterExpression, "Expression should not be empty!");

		// Prefix the expression with the compulsory WHERE keyword.
		if (!textFilterExpression.toUpperCase().startsWith(WHERE_PREFIX)) {
			textFilterExpression = String.format("%s %s", WHERE_PREFIX, textFilterExpression);
		}

		if (this.cache.containsKey(textFilterExpression)) {
			return this.cache.get(textFilterExpression);
		}

		var lexer = new FiltersLexer(CharStreams.fromString(textFilterExpression));
		var tokens = new CommonTokenStream(lexer);
		var parser = new FiltersParser(tokens);

		parser.removeErrorListeners();
		this.errorListener.errorMessages.clear();
		parser.addErrorListener(this.errorListener);

		if (this.errorHandler != null) {
			parser.setErrorHandler(this.errorHandler);
		}

		var filterExpressionVisitor = new FilterExpressionVisitor();
		try {
			Filter.Operand operand = filterExpressionVisitor.visit(parser.where());
			var filterExpression = filterExpressionVisitor.castToExpression(operand);
			this.cache.putIfAbsent(textFilterExpression, filterExpression);
			return filterExpression;
		}
		catch (ParseCancellationException e) {
			var msg = String.join("", this.errorListener.errorMessages);
			var rootCause = NestedExceptionUtils.getRootCause(e);
			throw new FilterExpressionParseException(msg, rootCause);
		}
	}

	public void clearCache() {
		this.cache.clear();
	}

	/** For testing only */
	Map<String, Filter.Expression> getCache() {
		return this.cache;
	}

	public static class FilterExpressionParseException extends RuntimeException {

		public FilterExpressionParseException(String message, Throwable cause) {
			super(message, cause);
		}

	}

	public static class FilterExpressionVisitor extends FiltersBaseVisitor<Filter.Operand> {

		private static final Map<String, Filter.ExpressionType> COMP_EXPRESSION_TYPE_MAP = Map.of("==",
				Filter.ExpressionType.EQ, "!=", Filter.ExpressionType.NE, ">", Filter.ExpressionType.GT, ">=",
				Filter.ExpressionType.GTE, "<", Filter.ExpressionType.LT, "<=", Filter.ExpressionType.LTE);

		@Override
		public Filter.Operand visitWhere(FiltersParser.WhereContext ctx) {
			return this.visit(ctx.booleanExpression());
		}

		@Override
		public Filter.Operand visitIdentifier(FiltersParser.IdentifierContext ctx) {
			return new Filter.Key(ctx.getText());
		}

		@Override
		public Filter.Operand visitTextConstant(FiltersParser.TextConstantContext ctx) {
			String onceQuotedText = unescapeStringValue(ctx.getText());
			return new Filter.Value(onceQuotedText);
		}

		/**
		 * Convert the DSL string representation (enclosed in single or double quotes)
		 * into a java String object. This not only means removing the enclosing quotes,
		 * but also un-escaping potential inner quotes, as well as unescaping the escaping
		 * caracter (the backslash).
		 */
		private String unescapeStringValue(String in) {
			char quoteStyle = in.charAt(0);
			in = in.substring(1, in.length() - 1);
			return switch (quoteStyle) {
				case '"' -> in.replace("\\\"", "\"").replace("\\\\", "\\");
				case '\'' -> in.replace("\\'", "'").replace("\\\\", "\\");
				default -> throw new IllegalStateException();
			};
		}

		@Override
		public Filter.Operand visitIntegerConstant(FiltersParser.IntegerConstantContext ctx) {
			return new Filter.Value(Integer.valueOf(ctx.getText()));
		}

		@Override
		public Filter.Operand visitDecimalConstant(FiltersParser.DecimalConstantContext ctx) {
			return new Filter.Value(Double.valueOf(ctx.getText()));
		}

		@Override
		public Filter.Operand visitBooleanConstant(FiltersParser.BooleanConstantContext ctx) {
			return new Filter.Value(Boolean.valueOf(ctx.getText()));
		}

		@Override
		public Filter.Operand visitConstantArray(FiltersParser.ConstantArrayContext ctx) {
			List<Object> list = new ArrayList<>();
			ctx.constant().forEach(constantCtx -> list.add(((Filter.Value) this.visit(constantCtx)).value()));
			return new Filter.Value(list);
		}

		@Override
		public Filter.Operand visitInExpression(FiltersParser.InExpressionContext ctx) {
			return new Filter.Expression(Filter.ExpressionType.IN, this.visitIdentifier(ctx.identifier()),
					this.visitConstantArray(ctx.constantArray()));
		}

		@Override
		public Filter.Operand visitNinExpression(FiltersParser.NinExpressionContext ctx) {
			return new Filter.Expression(Filter.ExpressionType.NIN, this.visitIdentifier(ctx.identifier()),
					this.visitConstantArray(ctx.constantArray()));
		}

		@Override
		public Filter.Operand visitCompareExpression(FiltersParser.CompareExpressionContext ctx) {
			return new Filter.Expression(this.convertCompare(ctx.compare().getText()),
					this.visitIdentifier(ctx.identifier()), this.visit(ctx.constant()));
		}

		@Override
		public Filter.Operand visitIsNullExpression(FiltersParser.IsNullExpressionContext ctx) {
			return new Filter.Expression(Filter.ExpressionType.ISNULL, this.visitIdentifier(ctx.identifier()));
		}

		@Override
		public Filter.Operand visitIsNotNullExpression(FiltersParser.IsNotNullExpressionContext ctx) {
			return new Filter.Expression(Filter.ExpressionType.ISNOTNULL, this.visitIdentifier(ctx.identifier()));
		}

		private Filter.ExpressionType convertCompare(String compare) {
			if (!COMP_EXPRESSION_TYPE_MAP.containsKey(compare)) {
				throw new RuntimeException("Unknown compare operator: " + compare);
			}
			return COMP_EXPRESSION_TYPE_MAP.get(compare);
		}

		@Override
		public Filter.Operand visitAndExpression(FiltersParser.AndExpressionContext ctx) {
			return new Filter.Expression(Filter.ExpressionType.AND, this.visit(ctx.left), this.visit(ctx.right));
		}

		@Override
		public Filter.Operand visitOrExpression(FiltersParser.OrExpressionContext ctx) {
			return new Filter.Expression(Filter.ExpressionType.OR, this.visit(ctx.left), this.visit(ctx.right));
		}

		@Override
		public Filter.Operand visitGroupExpression(FiltersParser.GroupExpressionContext ctx) {
			return new Filter.Group(castToExpression(this.visit(ctx.booleanExpression())));
		}

		@Override
		public Filter.Operand visitNotExpression(NotExpressionContext ctx) {
			return new Filter.Expression(Filter.ExpressionType.NOT, this.visit(ctx.booleanExpression()), null);
		}

		@Override
		public Filter.Operand visitLongConstant(FiltersParser.LongConstantContext ctx) {
			String text = ctx.getText();
			// Remove the trailing 'l' or 'L'
			long value = Long.parseLong(text.substring(0, text.length() - 1));
			return new Filter.Value(value);
		}

		public Filter.Expression castToExpression(Filter.Operand expression) {
			if (expression instanceof Filter.Group group) {
				// Remove the top-level grouping.
				return group.content();
			}
			else if (expression instanceof Filter.Expression exp) {
				return exp;
			}
			throw new RuntimeException("Invalid expression: " + expression);
		}

	}

	public static class DescriptiveErrorListener extends BaseErrorListener {

		public static final DescriptiveErrorListener INSTANCE = new DescriptiveErrorListener();

		public final List<String> errorMessages = new CopyOnWriteArrayList<>();

		@Override
		public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine,
				String msg, RecognitionException e) {

			String sourceName = recognizer.getInputStream().getSourceName();

			var errorMessage = String.format("Source: %s, Line: %s:%s, Error: %s", sourceName, line, charPositionInLine,
					msg);

			this.errorMessages.add(errorMessage);
		}

	}

}
