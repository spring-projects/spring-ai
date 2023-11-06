package org.springframework.ai.vectorstore.filter;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.ANTLRErrorStrategy;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.ParseCancellationException;

import org.springframework.ai.vectorstore.filter.antlr4.FiltersBaseVisitor;
import org.springframework.ai.vectorstore.filter.antlr4.FiltersLexer;
import org.springframework.ai.vectorstore.filter.antlr4.FiltersParser;
import org.springframework.util.Assert;

/**
 *
 * Parse a text, vector-store agnostic, filter expression language into
 * {@link Filter.Expression}.
 *
 * The vector-store agnostic, filter expression language is defined by a formal ANTLR4
 * grammar (Filters.g4). The language looks and feels like a subset of the well known SQL
 * WHERE filter expressions. For example you can use the parser like this:
 *
 * <pre>{@code
 *
 * var parser = new FilterExpressionTextParser();
 *
 * var exp1 = parser.parse("country == 'BG'"); // creates:
 *
 * new Expression(EQ, new Key("country"), new Value("BG"));
 *
 * var exp2 = parser.parse("genre == 'drama' && year >= 2020"); // creates:
 *
 * new Expression(AND, new Expression(EQ, new Key("genre"), new Value("drama")),
 * 		new Expression(GTE, new Key("year"), new Value(2020)));
 *
 * var exp3 = parser.parse("genre in ['comedy', 'documentary', 'drama']"); // creates:
 *
 * new Expression(IN, new Key("genre"), new Value(List.of("comedy", "documentary", "drama")));
 *
 * var exp4 = parser.parse("year >= 2020 || country == 'BG' && city != 'Sofia'"); // creates:
 *
 * new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
 * 		new Expression(AND, new Expression(EQ, new Key("country"), new Value("BG")),
 * 				new Expression(NE, new Key("city"), new Value("Sofia"))));
 *
 * var exp5 = parser.parse("(year >= 2020 || country == \"BG\") && city NOT IN ['Sofia', \"Plovdiv\"]"); // creates:
 *
 * new Expression(AND,
 * 		new Group(new Expression(OR, new Expression(EQ, new Key("country"), new Value("BG")),
 * 				new Expression(GTE, new Key("year"), new Value(2020)))),
 * 		new Expression(NIN, new Key("city"), new Value(List.of("Sofia", "Varna"))));
 *
 * var exp6 = parser.parse("isOpen == true && year >= 2020 && country IN ['BG', 'NL', 'US']"); // creates:
 *
 * new Expression(AND, new Expression(EQ, new Key("isOpen"), new Value(true)),
 * 		new Expression(AND, new Expression(GTE, new Key("year"), new Value(2020)),
 * 				new Expression(IN, new Key("country"), new Value(List.of("BG", "NL", "US")))));
 *
 * var exp7 = parser.parse("price >= 15.6 && price <= 20.13"); // creates:
 *
 * new Expression(AND,
 * 		new Expression(GTE, new Key("price"), new Value(15.6)),
 * 		new Expression(LTE, new Key("price"), new Value(20.13)));
 *
 * }</pre>
 *
 * @author Christian Tzolov
 */
public class FilterExpressionTextParser {

	private static final String WHERE_PREFIX = "WHERE";

	private final ANTLRErrorListener errorListener;

	private final ANTLRErrorStrategy errorHandler;

	public FilterExpressionTextParser() {
		this(DescriptiveErrorListener.INSTANCE, new BailErrorStrategy());
	}

	public FilterExpressionTextParser(ANTLRErrorListener listener, ANTLRErrorStrategy handler) {
		this.errorListener = listener;
		this.errorHandler = handler;
	}

	public Filter.Expression parse(String textExpression) {

		Assert.hasText(textExpression, "Expression should not be empty!");

		// Prefix the expression with the compulsory WHERE keyword.
		if (!textExpression.toUpperCase().startsWith(WHERE_PREFIX)) {
			textExpression = String.format("%s %s", WHERE_PREFIX, textExpression);
		}

		var lexer = new FiltersLexer(CharStreams.fromString(textExpression));
		var tokens = new CommonTokenStream(lexer);
		var parser = new FiltersParser(tokens);

		if (this.errorListener != null) {
			parser.removeErrorListeners();
			parser.addErrorListener(this.errorListener);
		}

		if (this.errorHandler != null) {
			parser.setErrorHandler(this.errorHandler);
		}

		var filterExpressionVisitor = new FilterExpressionVisitor();
		try {
			Filter.Operand operand = filterExpressionVisitor.visit(parser.where());
			return filterExpressionVisitor.castToExpression(operand);
		}
		catch (ParseCancellationException e) {
			throw new RuntimeException(e);
		}
	}

	public static class FilterExpressionVisitor extends FiltersBaseVisitor<Filter.Operand> {

		private static final Map<String, Filter.ExpressionType> EXPRESSION_TYPES_MAP = Map.of("==",
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
			var twiceQuotedText = ctx.getText();
			String onceQuotedText = twiceQuotedText.substring(1, twiceQuotedText.length() - 1);
			return new Filter.Value(onceQuotedText);
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
			ctx.constant().forEach(constantCtx -> {
				list.add(((Filter.Value) this.visit(constantCtx)).value());
			});
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
			return new Filter.Expression(this.covertCompare(ctx.compare().getText()),
					this.visitIdentifier(ctx.identifier()), this.visit(ctx.constant()));
		}

		private Filter.ExpressionType covertCompare(String compare) {
			if (!EXPRESSION_TYPES_MAP.containsKey(compare)) {
				throw new RuntimeException("Unknown compare operator: " + compare);
			}
			return EXPRESSION_TYPES_MAP.get(compare);
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

		public static DescriptiveErrorListener INSTANCE = new DescriptiveErrorListener();

		public static boolean REPORT_SYNTAX_ERRORS = true;

		@Override
		public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine,
				String msg, RecognitionException e) {

			if (!REPORT_SYNTAX_ERRORS) {
				return;
			}

			String sourceName = recognizer.getInputStream().getSourceName();
			if (!sourceName.isEmpty()) {
				sourceName = String.format("%s:%d:%d: ", sourceName, line, charPositionInLine);
			}

			System.err.println(sourceName + "line " + line + ":" + charPositionInLine + " " + msg);
			System.out.println();
		}

		@Override
		public void reportAmbiguity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, boolean exact,
				BitSet ambigAlts, ATNConfigSet configs) {
			super.reportAmbiguity(recognizer, dfa, startIndex, stopIndex, exact, ambigAlts, configs);
		}

		@Override
		public void reportAttemptingFullContext(Parser recognizer, DFA dfa, int startIndex, int stopIndex,
				BitSet conflictingAlts, ATNConfigSet configs) {
			super.reportAttemptingFullContext(recognizer, dfa, startIndex, stopIndex, conflictingAlts, configs);
		}

		@Override
		public void reportContextSensitivity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, int prediction,
				ATNConfigSet configs) {
			super.reportContextSensitivity(recognizer, dfa, startIndex, stopIndex, prediction, configs);
		}

	}

}