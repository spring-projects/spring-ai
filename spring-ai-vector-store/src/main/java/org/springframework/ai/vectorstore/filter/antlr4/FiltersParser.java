// Generated from org/springframework/ai/vectorstore/filter/antlr4/Filters.g4 by ANTLR 4.13.1
package org.springframework.ai.vectorstore.filter.antlr4;

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

// ############################################################
// # NOTE: This is ANTLR4 auto-generated code. Do not modify! #
// ############################################################

import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({ "all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue" })
public class FiltersParser extends Parser {

	static {
		RuntimeMetaData.checkVersion("4.13.1", RuntimeMetaData.VERSION);
	}

	protected static final DFA[] _decisionToDFA;

	protected static final PredictionContextCache _sharedContextCache = new PredictionContextCache();

	public static final int LONG_SUFFIX = 1, WHERE = 2, DOT = 3, COMMA = 4, LEFT_SQUARE_BRACKETS = 5,
			RIGHT_SQUARE_BRACKETS = 6, LEFT_PARENTHESIS = 7, RIGHT_PARENTHESIS = 8, EQUALS = 9, MINUS = 10, PLUS = 11,
			GT = 12, GE = 13, LT = 14, LE = 15, NE = 16, AND = 17, OR = 18, IN = 19, NIN = 20, NOT = 21, IS = 22,
			NULL = 23, BOOLEAN_VALUE = 24, QUOTED_STRING = 25, INTEGER_VALUE = 26, DECIMAL_VALUE = 27, IDENTIFIER = 28,
			WS = 29;

	public static final int RULE_where = 0, RULE_booleanExpression = 1, RULE_constantArray = 2, RULE_compare = 3,
			RULE_identifier = 4, RULE_constant = 5;

	private static String[] makeRuleNames() {
		return new String[] { "where", "booleanExpression", "constantArray", "compare", "identifier", "constant" };
	}

	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] { null, null, null, "'.'", "','", "'['", "']'", "'('", "')'", "'=='", "'-'", "'+'", "'>'",
				"'>='", "'<'", "'<='", "'!='" };
	}

	private static final String[] _LITERAL_NAMES = makeLiteralNames();

	private static String[] makeSymbolicNames() {
		return new String[] { null, "LONG_SUFFIX", "WHERE", "DOT", "COMMA", "LEFT_SQUARE_BRACKETS",
				"RIGHT_SQUARE_BRACKETS", "LEFT_PARENTHESIS", "RIGHT_PARENTHESIS", "EQUALS", "MINUS", "PLUS", "GT", "GE",
				"LT", "LE", "NE", "AND", "OR", "IN", "NIN", "NOT", "IS", "NULL", "BOOLEAN_VALUE", "QUOTED_STRING",
				"INTEGER_VALUE", "DECIMAL_VALUE", "IDENTIFIER", "WS" };
	}

	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();

	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}

	@Override
	public String getGrammarFileName() {
		return "Filters.g4";
	}

	@Override
	public String[] getRuleNames() {
		return ruleNames;
	}

	@Override
	public String getSerializedATN() {
		return _serializedATN;
	}

	@Override
	public ATN getATN() {
		return _ATN;
	}

	public FiltersParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this, _ATN, _decisionToDFA, _sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class WhereContext extends ParserRuleContext {

		public TerminalNode WHERE() {
			return getToken(FiltersParser.WHERE, 0);
		}

		public BooleanExpressionContext booleanExpression() {
			return getRuleContext(BooleanExpressionContext.class, 0);
		}

		public TerminalNode EOF() {
			return getToken(FiltersParser.EOF, 0);
		}

		public WhereContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}

		@Override
		public int getRuleIndex() {
			return RULE_where;
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).enterWhere(this);
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).exitWhere(this);
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor)
				return ((FiltersVisitor<? extends T>) visitor).visitWhere(this);
			else
				return visitor.visitChildren(this);
		}

	}

	public final WhereContext where() throws RecognitionException {
		WhereContext _localctx = new WhereContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_where);
		try {
			enterOuterAlt(_localctx, 1);
			{
				setState(12);
				match(WHERE);
				setState(13);
				booleanExpression(0);
				setState(14);
				match(EOF);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class BooleanExpressionContext extends ParserRuleContext {

		public BooleanExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}

		@Override
		public int getRuleIndex() {
			return RULE_booleanExpression;
		}

		public BooleanExpressionContext() {
		}

		public void copyFrom(BooleanExpressionContext ctx) {
			super.copyFrom(ctx);
		}

	}

	@SuppressWarnings("CheckReturnValue")
	public static class NinExpressionContext extends BooleanExpressionContext {

		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class, 0);
		}

		public ConstantArrayContext constantArray() {
			return getRuleContext(ConstantArrayContext.class, 0);
		}

		public TerminalNode NOT() {
			return getToken(FiltersParser.NOT, 0);
		}

		public TerminalNode IN() {
			return getToken(FiltersParser.IN, 0);
		}

		public TerminalNode NIN() {
			return getToken(FiltersParser.NIN, 0);
		}

		public NinExpressionContext(BooleanExpressionContext ctx) {
			copyFrom(ctx);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).enterNinExpression(this);
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).exitNinExpression(this);
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor)
				return ((FiltersVisitor<? extends T>) visitor).visitNinExpression(this);
			else
				return visitor.visitChildren(this);
		}

	}

	@SuppressWarnings("CheckReturnValue")
	public static class IsNullExpressionContext extends BooleanExpressionContext {

		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class, 0);
		}

		public TerminalNode IS() {
			return getToken(FiltersParser.IS, 0);
		}

		public TerminalNode NULL() {
			return getToken(FiltersParser.NULL, 0);
		}

		public IsNullExpressionContext(BooleanExpressionContext ctx) {
			copyFrom(ctx);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).enterIsNullExpression(this);
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).exitIsNullExpression(this);
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor)
				return ((FiltersVisitor<? extends T>) visitor).visitIsNullExpression(this);
			else
				return visitor.visitChildren(this);
		}

	}

	@SuppressWarnings("CheckReturnValue")
	public static class IsNotNullExpressionContext extends BooleanExpressionContext {

		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class, 0);
		}

		public TerminalNode IS() {
			return getToken(FiltersParser.IS, 0);
		}

		public TerminalNode NOT() {
			return getToken(FiltersParser.NOT, 0);
		}

		public TerminalNode NULL() {
			return getToken(FiltersParser.NULL, 0);
		}

		public IsNotNullExpressionContext(BooleanExpressionContext ctx) {
			copyFrom(ctx);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).enterIsNotNullExpression(this);
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).exitIsNotNullExpression(this);
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor)
				return ((FiltersVisitor<? extends T>) visitor).visitIsNotNullExpression(this);
			else
				return visitor.visitChildren(this);
		}

	}

	@SuppressWarnings("CheckReturnValue")
	public static class AndExpressionContext extends BooleanExpressionContext {

		public BooleanExpressionContext left;

		public Token operator;

		public BooleanExpressionContext right;

		public List<BooleanExpressionContext> booleanExpression() {
			return getRuleContexts(BooleanExpressionContext.class);
		}

		public BooleanExpressionContext booleanExpression(int i) {
			return getRuleContext(BooleanExpressionContext.class, i);
		}

		public TerminalNode AND() {
			return getToken(FiltersParser.AND, 0);
		}

		public AndExpressionContext(BooleanExpressionContext ctx) {
			copyFrom(ctx);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).enterAndExpression(this);
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).exitAndExpression(this);
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor)
				return ((FiltersVisitor<? extends T>) visitor).visitAndExpression(this);
			else
				return visitor.visitChildren(this);
		}

	}

	@SuppressWarnings("CheckReturnValue")
	public static class InExpressionContext extends BooleanExpressionContext {

		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class, 0);
		}

		public TerminalNode IN() {
			return getToken(FiltersParser.IN, 0);
		}

		public ConstantArrayContext constantArray() {
			return getRuleContext(ConstantArrayContext.class, 0);
		}

		public InExpressionContext(BooleanExpressionContext ctx) {
			copyFrom(ctx);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).enterInExpression(this);
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).exitInExpression(this);
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor)
				return ((FiltersVisitor<? extends T>) visitor).visitInExpression(this);
			else
				return visitor.visitChildren(this);
		}

	}

	@SuppressWarnings("CheckReturnValue")
	public static class NotExpressionContext extends BooleanExpressionContext {

		public TerminalNode NOT() {
			return getToken(FiltersParser.NOT, 0);
		}

		public BooleanExpressionContext booleanExpression() {
			return getRuleContext(BooleanExpressionContext.class, 0);
		}

		public NotExpressionContext(BooleanExpressionContext ctx) {
			copyFrom(ctx);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).enterNotExpression(this);
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).exitNotExpression(this);
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor)
				return ((FiltersVisitor<? extends T>) visitor).visitNotExpression(this);
			else
				return visitor.visitChildren(this);
		}

	}

	@SuppressWarnings("CheckReturnValue")
	public static class CompareExpressionContext extends BooleanExpressionContext {

		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class, 0);
		}

		public CompareContext compare() {
			return getRuleContext(CompareContext.class, 0);
		}

		public ConstantContext constant() {
			return getRuleContext(ConstantContext.class, 0);
		}

		public CompareExpressionContext(BooleanExpressionContext ctx) {
			copyFrom(ctx);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).enterCompareExpression(this);
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).exitCompareExpression(this);
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor)
				return ((FiltersVisitor<? extends T>) visitor).visitCompareExpression(this);
			else
				return visitor.visitChildren(this);
		}

	}

	@SuppressWarnings("CheckReturnValue")
	public static class OrExpressionContext extends BooleanExpressionContext {

		public BooleanExpressionContext left;

		public Token operator;

		public BooleanExpressionContext right;

		public List<BooleanExpressionContext> booleanExpression() {
			return getRuleContexts(BooleanExpressionContext.class);
		}

		public BooleanExpressionContext booleanExpression(int i) {
			return getRuleContext(BooleanExpressionContext.class, i);
		}

		public TerminalNode OR() {
			return getToken(FiltersParser.OR, 0);
		}

		public OrExpressionContext(BooleanExpressionContext ctx) {
			copyFrom(ctx);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).enterOrExpression(this);
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).exitOrExpression(this);
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor)
				return ((FiltersVisitor<? extends T>) visitor).visitOrExpression(this);
			else
				return visitor.visitChildren(this);
		}

	}

	@SuppressWarnings("CheckReturnValue")
	public static class GroupExpressionContext extends BooleanExpressionContext {

		public TerminalNode LEFT_PARENTHESIS() {
			return getToken(FiltersParser.LEFT_PARENTHESIS, 0);
		}

		public BooleanExpressionContext booleanExpression() {
			return getRuleContext(BooleanExpressionContext.class, 0);
		}

		public TerminalNode RIGHT_PARENTHESIS() {
			return getToken(FiltersParser.RIGHT_PARENTHESIS, 0);
		}

		public GroupExpressionContext(BooleanExpressionContext ctx) {
			copyFrom(ctx);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).enterGroupExpression(this);
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).exitGroupExpression(this);
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor)
				return ((FiltersVisitor<? extends T>) visitor).visitGroupExpression(this);
			else
				return visitor.visitChildren(this);
		}

	}

	public final BooleanExpressionContext booleanExpression() throws RecognitionException {
		return booleanExpression(0);
	}

	private BooleanExpressionContext booleanExpression(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		BooleanExpressionContext _localctx = new BooleanExpressionContext(_ctx, _parentState);
		BooleanExpressionContext _prevctx = _localctx;
		int _startState = 2;
		enterRecursionRule(_localctx, 2, RULE_booleanExpression, _p);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
				setState(48);
				_errHandler.sync(this);
				switch (getInterpreter().adaptivePredict(_input, 1, _ctx)) {
					case 1: {
						_localctx = new CompareExpressionContext(_localctx);
						_ctx = _localctx;
						_prevctx = _localctx;

						setState(17);
						identifier();
						setState(18);
						compare();
						setState(19);
						constant();
					}
						break;
					case 2: {
						_localctx = new InExpressionContext(_localctx);
						_ctx = _localctx;
						_prevctx = _localctx;
						setState(21);
						identifier();
						setState(22);
						match(IN);
						setState(23);
						constantArray();
					}
						break;
					case 3: {
						_localctx = new NinExpressionContext(_localctx);
						_ctx = _localctx;
						_prevctx = _localctx;
						setState(25);
						identifier();
						setState(29);
						_errHandler.sync(this);
						switch (_input.LA(1)) {
							case NOT: {
								setState(26);
								match(NOT);
								setState(27);
								match(IN);
							}
								break;
							case NIN: {
								setState(28);
								match(NIN);
							}
								break;
							default:
								throw new NoViableAltException(this);
						}
						setState(31);
						constantArray();
					}
						break;
					case 4: {
						_localctx = new IsNullExpressionContext(_localctx);
						_ctx = _localctx;
						_prevctx = _localctx;
						setState(33);
						identifier();
						setState(34);
						match(IS);
						setState(35);
						match(NULL);
					}
						break;
					case 5: {
						_localctx = new IsNotNullExpressionContext(_localctx);
						_ctx = _localctx;
						_prevctx = _localctx;
						setState(37);
						identifier();
						setState(38);
						match(IS);
						setState(39);
						match(NOT);
						setState(40);
						match(NULL);
					}
						break;
					case 6: {
						_localctx = new GroupExpressionContext(_localctx);
						_ctx = _localctx;
						_prevctx = _localctx;
						setState(42);
						match(LEFT_PARENTHESIS);
						setState(43);
						booleanExpression(0);
						setState(44);
						match(RIGHT_PARENTHESIS);
					}
						break;
					case 7: {
						_localctx = new NotExpressionContext(_localctx);
						_ctx = _localctx;
						_prevctx = _localctx;
						setState(46);
						match(NOT);
						setState(47);
						booleanExpression(1);
					}
						break;
				}
				_ctx.stop = _input.LT(-1);
				setState(58);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input, 3, _ctx);
				while (_alt != 2 && _alt != org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER) {
					if (_alt == 1) {
						if (_parseListeners != null)
							triggerExitRuleEvent();
						_prevctx = _localctx;
						{
							setState(56);
							_errHandler.sync(this);
							switch (getInterpreter().adaptivePredict(_input, 2, _ctx)) {
								case 1: {
									_localctx = new AndExpressionContext(
											new BooleanExpressionContext(_parentctx, _parentState));
									((AndExpressionContext) _localctx).left = _prevctx;
									pushNewRecursionContext(_localctx, _startState, RULE_booleanExpression);
									setState(50);
									if (!(precpred(_ctx, 4)))
										throw new FailedPredicateException(this, "precpred(_ctx, 4)");
									setState(51);
									((AndExpressionContext) _localctx).operator = match(AND);
									setState(52);
									((AndExpressionContext) _localctx).right = booleanExpression(5);
								}
									break;
								case 2: {
									_localctx = new OrExpressionContext(
											new BooleanExpressionContext(_parentctx, _parentState));
									((OrExpressionContext) _localctx).left = _prevctx;
									pushNewRecursionContext(_localctx, _startState, RULE_booleanExpression);
									setState(53);
									if (!(precpred(_ctx, 3)))
										throw new FailedPredicateException(this, "precpred(_ctx, 3)");
									setState(54);
									((OrExpressionContext) _localctx).operator = match(OR);
									setState(55);
									((OrExpressionContext) _localctx).right = booleanExpression(4);
								}
									break;
							}
						}
					}
					setState(60);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input, 3, _ctx);
				}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ConstantArrayContext extends ParserRuleContext {

		public TerminalNode LEFT_SQUARE_BRACKETS() {
			return getToken(FiltersParser.LEFT_SQUARE_BRACKETS, 0);
		}

		public List<ConstantContext> constant() {
			return getRuleContexts(ConstantContext.class);
		}

		public ConstantContext constant(int i) {
			return getRuleContext(ConstantContext.class, i);
		}

		public TerminalNode RIGHT_SQUARE_BRACKETS() {
			return getToken(FiltersParser.RIGHT_SQUARE_BRACKETS, 0);
		}

		public List<TerminalNode> COMMA() {
			return getTokens(FiltersParser.COMMA);
		}

		public TerminalNode COMMA(int i) {
			return getToken(FiltersParser.COMMA, i);
		}

		public ConstantArrayContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}

		@Override
		public int getRuleIndex() {
			return RULE_constantArray;
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).enterConstantArray(this);
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).exitConstantArray(this);
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor)
				return ((FiltersVisitor<? extends T>) visitor).visitConstantArray(this);
			else
				return visitor.visitChildren(this);
		}

	}

	public final ConstantArrayContext constantArray() throws RecognitionException {
		ConstantArrayContext _localctx = new ConstantArrayContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_constantArray);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
				setState(61);
				match(LEFT_SQUARE_BRACKETS);
				setState(62);
				constant();
				setState(67);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la == COMMA) {
					{
						{
							setState(63);
							match(COMMA);
							setState(64);
							constant();
						}
					}
					setState(69);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(70);
				match(RIGHT_SQUARE_BRACKETS);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class CompareContext extends ParserRuleContext {

		public TerminalNode EQUALS() {
			return getToken(FiltersParser.EQUALS, 0);
		}

		public TerminalNode GT() {
			return getToken(FiltersParser.GT, 0);
		}

		public TerminalNode GE() {
			return getToken(FiltersParser.GE, 0);
		}

		public TerminalNode LT() {
			return getToken(FiltersParser.LT, 0);
		}

		public TerminalNode LE() {
			return getToken(FiltersParser.LE, 0);
		}

		public TerminalNode NE() {
			return getToken(FiltersParser.NE, 0);
		}

		public CompareContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}

		@Override
		public int getRuleIndex() {
			return RULE_compare;
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).enterCompare(this);
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).exitCompare(this);
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor)
				return ((FiltersVisitor<? extends T>) visitor).visitCompare(this);
			else
				return visitor.visitChildren(this);
		}

	}

	public final CompareContext compare() throws RecognitionException {
		CompareContext _localctx = new CompareContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_compare);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
				setState(72);
				_la = _input.LA(1);
				if (!((((_la) & ~0x3f) == 0 && ((1L << _la) & 127488L) != 0))) {
					_errHandler.recoverInline(this);
				}
				else {
					if (_input.LA(1) == Token.EOF)
						matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class IdentifierContext extends ParserRuleContext {

		public List<TerminalNode> IDENTIFIER() {
			return getTokens(FiltersParser.IDENTIFIER);
		}

		public TerminalNode IDENTIFIER(int i) {
			return getToken(FiltersParser.IDENTIFIER, i);
		}

		public TerminalNode DOT() {
			return getToken(FiltersParser.DOT, 0);
		}

		public TerminalNode QUOTED_STRING() {
			return getToken(FiltersParser.QUOTED_STRING, 0);
		}

		public IdentifierContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}

		@Override
		public int getRuleIndex() {
			return RULE_identifier;
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).enterIdentifier(this);
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).exitIdentifier(this);
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor)
				return ((FiltersVisitor<? extends T>) visitor).visitIdentifier(this);
			else
				return visitor.visitChildren(this);
		}

	}

	public final IdentifierContext identifier() throws RecognitionException {
		IdentifierContext _localctx = new IdentifierContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_identifier);
		try {
			setState(79);
			_errHandler.sync(this);
			switch (getInterpreter().adaptivePredict(_input, 5, _ctx)) {
				case 1:
					enterOuterAlt(_localctx, 1); {
					setState(74);
					match(IDENTIFIER);
					setState(75);
					match(DOT);
					setState(76);
					match(IDENTIFIER);
				}
					break;
				case 2:
					enterOuterAlt(_localctx, 2); {
					setState(77);
					match(IDENTIFIER);
				}
					break;
				case 3:
					enterOuterAlt(_localctx, 3); {
					setState(78);
					match(QUOTED_STRING);
				}
					break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ConstantContext extends ParserRuleContext {

		public ConstantContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}

		@Override
		public int getRuleIndex() {
			return RULE_constant;
		}

		public ConstantContext() {
		}

		public void copyFrom(ConstantContext ctx) {
			super.copyFrom(ctx);
		}

	}

	@SuppressWarnings("CheckReturnValue")
	public static class LongConstantContext extends ConstantContext {

		public TerminalNode INTEGER_VALUE() {
			return getToken(FiltersParser.INTEGER_VALUE, 0);
		}

		public TerminalNode LONG_SUFFIX() {
			return getToken(FiltersParser.LONG_SUFFIX, 0);
		}

		public TerminalNode MINUS() {
			return getToken(FiltersParser.MINUS, 0);
		}

		public TerminalNode PLUS() {
			return getToken(FiltersParser.PLUS, 0);
		}

		public LongConstantContext(ConstantContext ctx) {
			copyFrom(ctx);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).enterLongConstant(this);
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).exitLongConstant(this);
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor)
				return ((FiltersVisitor<? extends T>) visitor).visitLongConstant(this);
			else
				return visitor.visitChildren(this);
		}

	}

	@SuppressWarnings("CheckReturnValue")
	public static class DecimalConstantContext extends ConstantContext {

		public TerminalNode DECIMAL_VALUE() {
			return getToken(FiltersParser.DECIMAL_VALUE, 0);
		}

		public TerminalNode MINUS() {
			return getToken(FiltersParser.MINUS, 0);
		}

		public TerminalNode PLUS() {
			return getToken(FiltersParser.PLUS, 0);
		}

		public DecimalConstantContext(ConstantContext ctx) {
			copyFrom(ctx);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).enterDecimalConstant(this);
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).exitDecimalConstant(this);
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor)
				return ((FiltersVisitor<? extends T>) visitor).visitDecimalConstant(this);
			else
				return visitor.visitChildren(this);
		}

	}

	@SuppressWarnings("CheckReturnValue")
	public static class TextConstantContext extends ConstantContext {

		public TerminalNode QUOTED_STRING() {
			return getToken(FiltersParser.QUOTED_STRING, 0);
		}

		public TextConstantContext(ConstantContext ctx) {
			copyFrom(ctx);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).enterTextConstant(this);
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).exitTextConstant(this);
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor)
				return ((FiltersVisitor<? extends T>) visitor).visitTextConstant(this);
			else
				return visitor.visitChildren(this);
		}

	}

	@SuppressWarnings("CheckReturnValue")
	public static class BooleanConstantContext extends ConstantContext {

		public TerminalNode BOOLEAN_VALUE() {
			return getToken(FiltersParser.BOOLEAN_VALUE, 0);
		}

		public BooleanConstantContext(ConstantContext ctx) {
			copyFrom(ctx);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).enterBooleanConstant(this);
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).exitBooleanConstant(this);
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor)
				return ((FiltersVisitor<? extends T>) visitor).visitBooleanConstant(this);
			else
				return visitor.visitChildren(this);
		}

	}

	@SuppressWarnings("CheckReturnValue")
	public static class IntegerConstantContext extends ConstantContext {

		public TerminalNode INTEGER_VALUE() {
			return getToken(FiltersParser.INTEGER_VALUE, 0);
		}

		public TerminalNode MINUS() {
			return getToken(FiltersParser.MINUS, 0);
		}

		public TerminalNode PLUS() {
			return getToken(FiltersParser.PLUS, 0);
		}

		public IntegerConstantContext(ConstantContext ctx) {
			copyFrom(ctx);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).enterIntegerConstant(this);
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener)
				((FiltersListener) listener).exitIntegerConstant(this);
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor)
				return ((FiltersVisitor<? extends T>) visitor).visitIntegerConstant(this);
			else
				return visitor.visitChildren(this);
		}

	}

	public final ConstantContext constant() throws RecognitionException {
		ConstantContext _localctx = new ConstantContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_constant);
		int _la;
		try {
			setState(96);
			_errHandler.sync(this);
			switch (getInterpreter().adaptivePredict(_input, 9, _ctx)) {
				case 1:
					_localctx = new LongConstantContext(_localctx);
					enterOuterAlt(_localctx, 1); {
					setState(82);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la == MINUS || _la == PLUS) {
						{
							setState(81);
							_la = _input.LA(1);
							if (!(_la == MINUS || _la == PLUS)) {
								_errHandler.recoverInline(this);
							}
							else {
								if (_input.LA(1) == Token.EOF)
									matchedEOF = true;
								_errHandler.reportMatch(this);
								consume();
							}
						}
					}

					setState(84);
					match(INTEGER_VALUE);
					setState(85);
					match(LONG_SUFFIX);
				}
					break;
				case 2:
					_localctx = new IntegerConstantContext(_localctx);
					enterOuterAlt(_localctx, 2); {
					setState(87);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la == MINUS || _la == PLUS) {
						{
							setState(86);
							_la = _input.LA(1);
							if (!(_la == MINUS || _la == PLUS)) {
								_errHandler.recoverInline(this);
							}
							else {
								if (_input.LA(1) == Token.EOF)
									matchedEOF = true;
								_errHandler.reportMatch(this);
								consume();
							}
						}
					}

					setState(89);
					match(INTEGER_VALUE);
				}
					break;
				case 3:
					_localctx = new DecimalConstantContext(_localctx);
					enterOuterAlt(_localctx, 3); {
					setState(91);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la == MINUS || _la == PLUS) {
						{
							setState(90);
							_la = _input.LA(1);
							if (!(_la == MINUS || _la == PLUS)) {
								_errHandler.recoverInline(this);
							}
							else {
								if (_input.LA(1) == Token.EOF)
									matchedEOF = true;
								_errHandler.reportMatch(this);
								consume();
							}
						}
					}

					setState(93);
					match(DECIMAL_VALUE);
				}
					break;
				case 4:
					_localctx = new TextConstantContext(_localctx);
					enterOuterAlt(_localctx, 4); {
					setState(94);
					match(QUOTED_STRING);
				}
					break;
				case 5:
					_localctx = new BooleanConstantContext(_localctx);
					enterOuterAlt(_localctx, 5); {
					setState(95);
					match(BOOLEAN_VALUE);
				}
					break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
		switch (ruleIndex) {
			case 1:
				return booleanExpression_sempred((BooleanExpressionContext) _localctx, predIndex);
		}
		return true;
	}

	private boolean booleanExpression_sempred(BooleanExpressionContext _localctx, int predIndex) {
		switch (predIndex) {
			case 0:
				return precpred(_ctx, 4);
			case 1:
				return precpred(_ctx, 3);
		}
		return true;
	}

	public static final String _serializedATN = "\u0004\u0001\u001dc\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"
			+ "\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"
			+ "\u0005\u0007\u0005\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001"
			+ "\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"
			+ "\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"
			+ "\u0001\u0003\u0001\u001e\b\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"
			+ "\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"
			+ "\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"
			+ "\u0001\u0001\u0001\u0003\u00011\b\u0001\u0001\u0001\u0001\u0001\u0001"
			+ "\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0005\u00019\b\u0001\n\u0001"
			+ "\f\u0001<\t\u0001\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0005"
			+ "\u0002B\b\u0002\n\u0002\f\u0002E\t\u0002\u0001\u0002\u0001\u0002\u0001"
			+ "\u0003\u0001\u0003\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001"
			+ "\u0004\u0003\u0004P\b\u0004\u0001\u0005\u0003\u0005S\b\u0005\u0001\u0005"
			+ "\u0001\u0005\u0001\u0005\u0003\u0005X\b\u0005\u0001\u0005\u0001\u0005"
			+ "\u0003\u0005\\\b\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0003\u0005"
			+ "a\b\u0005\u0001\u0005\u0000\u0001\u0002\u0006\u0000\u0002\u0004\u0006"
			+ "\b\n\u0000\u0002\u0002\u0000\t\t\f\u0010\u0001\u0000\n\u000bo\u0000\f"
			+ "\u0001\u0000\u0000\u0000\u00020\u0001\u0000\u0000\u0000\u0004=\u0001\u0000"
			+ "\u0000\u0000\u0006H\u0001\u0000\u0000\u0000\bO\u0001\u0000\u0000\u0000"
			+ "\n`\u0001\u0000\u0000\u0000\f\r\u0005\u0002\u0000\u0000\r\u000e\u0003"
			+ "\u0002\u0001\u0000\u000e\u000f\u0005\u0000\u0000\u0001\u000f\u0001\u0001"
			+ "\u0000\u0000\u0000\u0010\u0011\u0006\u0001\uffff\uffff\u0000\u0011\u0012"
			+ "\u0003\b\u0004\u0000\u0012\u0013\u0003\u0006\u0003\u0000\u0013\u0014\u0003"
			+ "\n\u0005\u0000\u00141\u0001\u0000\u0000\u0000\u0015\u0016\u0003\b\u0004"
			+ "\u0000\u0016\u0017\u0005\u0013\u0000\u0000\u0017\u0018\u0003\u0004\u0002"
			+ "\u0000\u00181\u0001\u0000\u0000\u0000\u0019\u001d\u0003\b\u0004\u0000"
			+ "\u001a\u001b\u0005\u0015\u0000\u0000\u001b\u001e\u0005\u0013\u0000\u0000"
			+ "\u001c\u001e\u0005\u0014\u0000\u0000\u001d\u001a\u0001\u0000\u0000\u0000"
			+ "\u001d\u001c\u0001\u0000\u0000\u0000\u001e\u001f\u0001\u0000\u0000\u0000"
			+ "\u001f \u0003\u0004\u0002\u0000 1\u0001\u0000\u0000\u0000!\"\u0003\b\u0004"
			+ "\u0000\"#\u0005\u0016\u0000\u0000#$\u0005\u0017\u0000\u0000$1\u0001\u0000"
			+ "\u0000\u0000%&\u0003\b\u0004\u0000&\'\u0005\u0016\u0000\u0000\'(\u0005"
			+ "\u0015\u0000\u0000()\u0005\u0017\u0000\u0000)1\u0001\u0000\u0000\u0000"
			+ "*+\u0005\u0007\u0000\u0000+,\u0003\u0002\u0001\u0000,-\u0005\b\u0000\u0000"
			+ "-1\u0001\u0000\u0000\u0000./\u0005\u0015\u0000\u0000/1\u0003\u0002\u0001"
			+ "\u00010\u0010\u0001\u0000\u0000\u00000\u0015\u0001\u0000\u0000\u00000"
			+ "\u0019\u0001\u0000\u0000\u00000!\u0001\u0000\u0000\u00000%\u0001\u0000"
			+ "\u0000\u00000*\u0001\u0000\u0000\u00000.\u0001\u0000\u0000\u00001:\u0001"
			+ "\u0000\u0000\u000023\n\u0004\u0000\u000034\u0005\u0011\u0000\u000049\u0003"
			+ "\u0002\u0001\u000556\n\u0003\u0000\u000067\u0005\u0012\u0000\u000079\u0003"
			+ "\u0002\u0001\u000482\u0001\u0000\u0000\u000085\u0001\u0000\u0000\u0000"
			+ "9<\u0001\u0000\u0000\u0000:8\u0001\u0000\u0000\u0000:;\u0001\u0000\u0000"
			+ "\u0000;\u0003\u0001\u0000\u0000\u0000<:\u0001\u0000\u0000\u0000=>\u0005"
			+ "\u0005\u0000\u0000>C\u0003\n\u0005\u0000?@\u0005\u0004\u0000\u0000@B\u0003"
			+ "\n\u0005\u0000A?\u0001\u0000\u0000\u0000BE\u0001\u0000\u0000\u0000CA\u0001"
			+ "\u0000\u0000\u0000CD\u0001\u0000\u0000\u0000DF\u0001\u0000\u0000\u0000"
			+ "EC\u0001\u0000\u0000\u0000FG\u0005\u0006\u0000\u0000G\u0005\u0001\u0000"
			+ "\u0000\u0000HI\u0007\u0000\u0000\u0000I\u0007\u0001\u0000\u0000\u0000"
			+ "JK\u0005\u001c\u0000\u0000KL\u0005\u0003\u0000\u0000LP\u0005\u001c\u0000"
			+ "\u0000MP\u0005\u001c\u0000\u0000NP\u0005\u0019\u0000\u0000OJ\u0001\u0000"
			+ "\u0000\u0000OM\u0001\u0000\u0000\u0000ON\u0001\u0000\u0000\u0000P\t\u0001"
			+ "\u0000\u0000\u0000QS\u0007\u0001\u0000\u0000RQ\u0001\u0000\u0000\u0000"
			+ "RS\u0001\u0000\u0000\u0000ST\u0001\u0000\u0000\u0000TU\u0005\u001a\u0000"
			+ "\u0000Ua\u0005\u0001\u0000\u0000VX\u0007\u0001\u0000\u0000WV\u0001\u0000"
			+ "\u0000\u0000WX\u0001\u0000\u0000\u0000XY\u0001\u0000\u0000\u0000Ya\u0005"
			+ "\u001a\u0000\u0000Z\\\u0007\u0001\u0000\u0000[Z\u0001\u0000\u0000\u0000"
			+ "[\\\u0001\u0000\u0000\u0000\\]\u0001\u0000\u0000\u0000]a\u0005\u001b\u0000"
			+ "\u0000^a\u0005\u0019\u0000\u0000_a\u0005\u0018\u0000\u0000`R\u0001\u0000"
			+ "\u0000\u0000`W\u0001\u0000\u0000\u0000`[\u0001\u0000\u0000\u0000`^\u0001"
			+ "\u0000\u0000\u0000`_\u0001\u0000\u0000\u0000a\u000b\u0001\u0000\u0000" + "\u0000\n\u001d08:CORW[`";

	public static final ATN _ATN = new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}

}