/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.vectorstore.filter.antlr4;

// Generated from org/springframework/ai/vectorstore/filter/antlr4/Filters.g4 by ANTLR 4.13.1

// ############################################################
// # NOTE: This is ANTLR4 auto-generated code. Do not modify! #
// ############################################################

import java.util.List;

import org.antlr.v4.runtime.FailedPredicateException;
import org.antlr.v4.runtime.NoViableAltException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.RuntimeMetaData;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.VocabularyImpl;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNDeserializer;
import org.antlr.v4.runtime.atn.ParserATNSimulator;
import org.antlr.v4.runtime.atn.PredictionContextCache;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;
import org.antlr.v4.runtime.tree.TerminalNode;

@SuppressWarnings({ "all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue" })
public class FiltersParser extends Parser {

	public static final int WHERE = 1, DOT = 2, COMMA = 3, LEFT_SQUARE_BRACKETS = 4, RIGHT_SQUARE_BRACKETS = 5,
			LEFT_PARENTHESIS = 6, RIGHT_PARENTHESIS = 7, EQUALS = 8, MINUS = 9, PLUS = 10, GT = 11, GE = 12, LT = 13,
			LE = 14, NE = 15, AND = 16, OR = 17, IN = 18, NIN = 19, NOT = 20, BOOLEAN_VALUE = 21, QUOTED_STRING = 22,
			INTEGER_VALUE = 23, DECIMAL_VALUE = 24, IDENTIFIER = 25, WS = 26;

	public static final int RULE_where = 0, RULE_booleanExpression = 1, RULE_constantArray = 2, RULE_compare = 3,
			RULE_identifier = 4, RULE_constant = 5;

	public static final String[] ruleNames = makeRuleNames();

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;

	public static final String _serializedATN = "\u0004\u0001\u001aY\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"
			+ "\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"
			+ "\u0005\u0007\u0005\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001"
			+ "\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"
			+ "\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"
			+ "\u0001\u0003\u0001\u001e\b\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"
			+ "\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0003\u0001(\b"
			+ "\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"
			+ "\u0001\u0005\u00010\b\u0001\n\u0001\f\u00013\t\u0001\u0001\u0002\u0001"
			+ "\u0002\u0001\u0002\u0001\u0002\u0005\u00029\b\u0002\n\u0002\f\u0002<\t"
			+ "\u0002\u0001\u0002\u0001\u0002\u0001\u0003\u0001\u0003\u0001\u0004\u0001"
			+ "\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0003\u0004G\b\u0004\u0001"
			+ "\u0005\u0003\u0005J\b\u0005\u0001\u0005\u0001\u0005\u0003\u0005N\b\u0005"
			+ "\u0001\u0005\u0001\u0005\u0004\u0005R\b\u0005\u000b\u0005\f\u0005S\u0001"
			+ "\u0005\u0003\u0005W\b\u0005\u0001\u0005\u0000\u0001\u0002\u0006\u0000"
			+ "\u0002\u0004\u0006\b\n\u0000\u0002\u0002\u0000\b\b\u000b\u000f\u0001\u0000"
			+ "\t\nb\u0000\f\u0001\u0000\u0000\u0000\u0002\'\u0001\u0000\u0000\u0000"
			+ "\u00044\u0001\u0000\u0000\u0000\u0006?\u0001\u0000\u0000\u0000\bF\u0001"
			+ "\u0000\u0000\u0000\nV\u0001\u0000\u0000\u0000\f\r\u0005\u0001\u0000\u0000"
			+ "\r\u000e\u0003\u0002\u0001\u0000\u000e\u000f\u0005\u0000\u0000\u0001\u000f"
			+ "\u0001\u0001\u0000\u0000\u0000\u0010\u0011\u0006\u0001\uffff\uffff\u0000"
			+ "\u0011\u0012\u0003\b\u0004\u0000\u0012\u0013\u0003\u0006\u0003\u0000\u0013"
			+ "\u0014\u0003\n\u0005\u0000\u0014(\u0001\u0000\u0000\u0000\u0015\u0016"
			+ "\u0003\b\u0004\u0000\u0016\u0017\u0005\u0012\u0000\u0000\u0017\u0018\u0003"
			+ "\u0004\u0002\u0000\u0018(\u0001\u0000\u0000\u0000\u0019\u001d\u0003\b"
			+ "\u0004\u0000\u001a\u001b\u0005\u0014\u0000\u0000\u001b\u001e\u0005\u0012"
			+ "\u0000\u0000\u001c\u001e\u0005\u0013\u0000\u0000\u001d\u001a\u0001\u0000"
			+ "\u0000\u0000\u001d\u001c\u0001\u0000\u0000\u0000\u001e\u001f\u0001\u0000"
			+ "\u0000\u0000\u001f \u0003\u0004\u0002\u0000 (\u0001\u0000\u0000\u0000"
			+ "!\"\u0005\u0006\u0000\u0000\"#\u0003\u0002\u0001\u0000#$\u0005\u0007\u0000"
			+ "\u0000$(\u0001\u0000\u0000\u0000%&\u0005\u0014\u0000\u0000&(\u0003\u0002"
			+ "\u0001\u0001\'\u0010\u0001\u0000\u0000\u0000\'\u0015\u0001\u0000\u0000"
			+ "\u0000\'\u0019\u0001\u0000\u0000\u0000\'!\u0001\u0000\u0000\u0000\'%\u0001"
			+ "\u0000\u0000\u0000(1\u0001\u0000\u0000\u0000)*\n\u0004\u0000\u0000*+\u0005"
			+ "\u0010\u0000\u0000+0\u0003\u0002\u0001\u0005,-\n\u0003\u0000\u0000-.\u0005"
			+ "\u0011\u0000\u0000.0\u0003\u0002\u0001\u0004/)\u0001\u0000\u0000\u0000"
			+ "/,\u0001\u0000\u0000\u000003\u0001\u0000\u0000\u00001/\u0001\u0000\u0000"
			+ "\u000012\u0001\u0000\u0000\u00002\u0003\u0001\u0000\u0000\u000031\u0001"
			+ "\u0000\u0000\u000045\u0005\u0004\u0000\u00005:\u0003\n\u0005\u000067\u0005"
			+ "\u0003\u0000\u000079\u0003\n\u0005\u000086\u0001\u0000\u0000\u00009<\u0001"
			+ "\u0000\u0000\u0000:8\u0001\u0000\u0000\u0000:;\u0001\u0000\u0000\u0000"
			+ ";=\u0001\u0000\u0000\u0000<:\u0001\u0000\u0000\u0000=>\u0005\u0005\u0000"
			+ "\u0000>\u0005\u0001\u0000\u0000\u0000?@\u0007\u0000\u0000\u0000@\u0007"
			+ "\u0001\u0000\u0000\u0000AB\u0005\u0019\u0000\u0000BC\u0005\u0002\u0000"
			+ "\u0000CG\u0005\u0019\u0000\u0000DG\u0005\u0019\u0000\u0000EG\u0005\u0016"
			+ "\u0000\u0000FA\u0001\u0000\u0000\u0000FD\u0001\u0000\u0000\u0000FE\u0001"
			+ "\u0000\u0000\u0000G\t\u0001\u0000\u0000\u0000HJ\u0007\u0001\u0000\u0000"
			+ "IH\u0001\u0000\u0000\u0000IJ\u0001\u0000\u0000\u0000JK\u0001\u0000\u0000"
			+ "\u0000KW\u0005\u0017\u0000\u0000LN\u0007\u0001\u0000\u0000ML\u0001\u0000"
			+ "\u0000\u0000MN\u0001\u0000\u0000\u0000NO\u0001\u0000\u0000\u0000OW\u0005"
			+ "\u0018\u0000\u0000PR\u0005\u0016\u0000\u0000QP\u0001\u0000\u0000\u0000"
			+ "RS\u0001\u0000\u0000\u0000SQ\u0001\u0000\u0000\u0000ST\u0001\u0000\u0000"
			+ "\u0000TW\u0001\u0000\u0000\u0000UW\u0005\u0015\u0000\u0000VI\u0001\u0000"
			+ "\u0000\u0000VM\u0001\u0000\u0000\u0000VQ\u0001\u0000\u0000\u0000VU\u0001"
			+ "\u0000\u0000\u0000W\u000b\u0001\u0000\u0000\u0000\n\u001d\'/1:FIMSV";

	public static final ATN _ATN = new ATNDeserializer().deserialize(_serializedATN.toCharArray());

	protected static final DFA[] _decisionToDFA;

	protected static final PredictionContextCache _sharedContextCache = new PredictionContextCache();

	private static final String[] _LITERAL_NAMES = makeLiteralNames();

	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();

	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	static {
		RuntimeMetaData.checkVersion("4.13.1", RuntimeMetaData.VERSION);
	}

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

	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}

	public FiltersParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this, _ATN, _decisionToDFA, _sharedContextCache);
	}

	private static String[] makeRuleNames() {
		return new String[] { "where", "booleanExpression", "constantArray", "compare", "identifier", "constant" };
	}

	private static String[] makeLiteralNames() {
		return new String[] { null, null, "'.'", "','", "'['", "']'", "'('", "')'", "'=='", "'-'", "'+'", "'>'", "'>='",
				"'<'", "'<='", "'!='" };
	}

	private static String[] makeSymbolicNames() {
		return new String[] { null, "WHERE", "DOT", "COMMA", "LEFT_SQUARE_BRACKETS", "RIGHT_SQUARE_BRACKETS",
				"LEFT_PARENTHESIS", "RIGHT_PARENTHESIS", "EQUALS", "MINUS", "PLUS", "GT", "GE", "LT", "LE", "NE", "AND",
				"OR", "IN", "NIN", "NOT", "BOOLEAN_VALUE", "QUOTED_STRING", "INTEGER_VALUE", "DECIMAL_VALUE",
				"IDENTIFIER", "WS" };
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
				setState(39);
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
						_localctx = new GroupExpressionContext(_localctx);
						_ctx = _localctx;
						_prevctx = _localctx;
						setState(33);
						match(LEFT_PARENTHESIS);
						setState(34);
						booleanExpression(0);
						setState(35);
						match(RIGHT_PARENTHESIS);
					}
						break;
					case 5: {
						_localctx = new NotExpressionContext(_localctx);
						_ctx = _localctx;
						_prevctx = _localctx;
						setState(37);
						match(NOT);
						setState(38);
						booleanExpression(1);
					}
						break;
				}
				_ctx.stop = _input.LT(-1);
				setState(49);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input, 3, _ctx);
				while (_alt != 2 && _alt != org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER) {
					if (_alt == 1) {
						if (_parseListeners != null) {
							triggerExitRuleEvent();
						}
						_prevctx = _localctx;
						{
							setState(47);
							_errHandler.sync(this);
							switch (getInterpreter().adaptivePredict(_input, 2, _ctx)) {
								case 1: {
									_localctx = new AndExpressionContext(
											new BooleanExpressionContext(_parentctx, _parentState));
									((AndExpressionContext) _localctx).left = _prevctx;
									pushNewRecursionContext(_localctx, _startState, RULE_booleanExpression);
									setState(41);
									if (!(precpred(_ctx, 4))) {
										throw new FailedPredicateException(this, "precpred(_ctx, 4)");
									}
									setState(42);
									((AndExpressionContext) _localctx).operator = match(AND);
									setState(43);
									((AndExpressionContext) _localctx).right = booleanExpression(5);
								}
									break;
								case 2: {
									_localctx = new OrExpressionContext(
											new BooleanExpressionContext(_parentctx, _parentState));
									((OrExpressionContext) _localctx).left = _prevctx;
									pushNewRecursionContext(_localctx, _startState, RULE_booleanExpression);
									setState(44);
									if (!(precpred(_ctx, 3))) {
										throw new FailedPredicateException(this, "precpred(_ctx, 3)");
									}
									setState(45);
									((OrExpressionContext) _localctx).operator = match(OR);
									setState(46);
									((OrExpressionContext) _localctx).right = booleanExpression(4);
								}
									break;
							}
						}
					}
					setState(51);
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

	public final ConstantArrayContext constantArray() throws RecognitionException {
		ConstantArrayContext _localctx = new ConstantArrayContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_constantArray);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
				setState(52);
				match(LEFT_SQUARE_BRACKETS);
				setState(53);
				constant();
				setState(58);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la == COMMA) {
					{
						{
							setState(54);
							match(COMMA);
							setState(55);
							constant();
						}
					}
					setState(60);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(61);
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

	public final CompareContext compare() throws RecognitionException {
		CompareContext _localctx = new CompareContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_compare);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
				setState(63);
				_la = _input.LA(1);
				if (!((((_la) & ~0x3f) == 0 && ((1L << _la) & 63744L) != 0))) {
					_errHandler.recoverInline(this);
				}
				else {
					if (_input.LA(1) == Token.EOF) {
						matchedEOF = true;
					}
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

	public final IdentifierContext identifier() throws RecognitionException {
		IdentifierContext _localctx = new IdentifierContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_identifier);
		try {
			setState(70);
			_errHandler.sync(this);
			switch (getInterpreter().adaptivePredict(_input, 5, _ctx)) {
				case 1:
					enterOuterAlt(_localctx, 1); {
					setState(65);
					match(IDENTIFIER);
					setState(66);
					match(DOT);
					setState(67);
					match(IDENTIFIER);
				}
					break;
				case 2:
					enterOuterAlt(_localctx, 2); {
					setState(68);
					match(IDENTIFIER);
				}
					break;
				case 3:
					enterOuterAlt(_localctx, 3); {
					setState(69);
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

	public final ConstantContext constant() throws RecognitionException {
		ConstantContext _localctx = new ConstantContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_constant);
		int _la;
		try {
			int _alt;
			setState(86);
			_errHandler.sync(this);
			switch (getInterpreter().adaptivePredict(_input, 9, _ctx)) {
				case 1:
					_localctx = new IntegerConstantContext(_localctx);
					enterOuterAlt(_localctx, 1); {
					setState(73);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la == MINUS || _la == PLUS) {
						{
							setState(72);
							_la = _input.LA(1);
							if (!(_la == MINUS || _la == PLUS)) {
								_errHandler.recoverInline(this);
							}
							else {
								if (_input.LA(1) == Token.EOF) {
									matchedEOF = true;
								}
								_errHandler.reportMatch(this);
								consume();
							}
						}
					}

					setState(75);
					match(INTEGER_VALUE);
				}
					break;
				case 2:
					_localctx = new DecimalConstantContext(_localctx);
					enterOuterAlt(_localctx, 2); {
					setState(77);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la == MINUS || _la == PLUS) {
						{
							setState(76);
							_la = _input.LA(1);
							if (!(_la == MINUS || _la == PLUS)) {
								_errHandler.recoverInline(this);
							}
							else {
								if (_input.LA(1) == Token.EOF) {
									matchedEOF = true;
								}
								_errHandler.reportMatch(this);
								consume();
							}
						}
					}

					setState(79);
					match(DECIMAL_VALUE);
				}
					break;
				case 3:
					_localctx = new TextConstantContext(_localctx);
					enterOuterAlt(_localctx, 3); {
					setState(81);
					_errHandler.sync(this);
					_alt = 1;
					do {
						switch (_alt) {
							case 1: {
								{
									setState(80);
									match(QUOTED_STRING);
								}
							}
								break;
							default:
								throw new NoViableAltException(this);
						}
						setState(83);
						_errHandler.sync(this);
						_alt = getInterpreter().adaptivePredict(_input, 8, _ctx);
					}
					while (_alt != 2 && _alt != org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER);
				}
					break;
				case 4:
					_localctx = new BooleanConstantContext(_localctx);
					enterOuterAlt(_localctx, 4); {
					setState(85);
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

	@SuppressWarnings("CheckReturnValue")
	public static class WhereContext extends ParserRuleContext {

		public WhereContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}

		public TerminalNode WHERE() {
			return getToken(FiltersParser.WHERE, 0);
		}

		public BooleanExpressionContext booleanExpression() {
			return getRuleContext(BooleanExpressionContext.class, 0);
		}

		public TerminalNode EOF() {
			return getToken(FiltersParser.EOF, 0);
		}

		@Override
		public int getRuleIndex() {
			return RULE_where;
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener) {
				((FiltersListener) listener).enterWhere(this);
			}
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener) {
				((FiltersListener) listener).exitWhere(this);
			}
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor) {
				return ((FiltersVisitor<? extends T>) visitor).visitWhere(this);
			}
			else {
				return visitor.visitChildren(this);
			}
		}

	}

	@SuppressWarnings("CheckReturnValue")
	public static class BooleanExpressionContext extends ParserRuleContext {

		public BooleanExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}

		public BooleanExpressionContext() {
		}

		@Override
		public int getRuleIndex() {
			return RULE_booleanExpression;
		}

		public void copyFrom(BooleanExpressionContext ctx) {
			super.copyFrom(ctx);
		}

	}

	@SuppressWarnings("CheckReturnValue")
	public static class NinExpressionContext extends BooleanExpressionContext {

		public NinExpressionContext(BooleanExpressionContext ctx) {
			copyFrom(ctx);
		}

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

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener) {
				((FiltersListener) listener).enterNinExpression(this);
			}
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener) {
				((FiltersListener) listener).exitNinExpression(this);
			}
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor) {
				return ((FiltersVisitor<? extends T>) visitor).visitNinExpression(this);
			}
			else {
				return visitor.visitChildren(this);
			}
		}

	}

	@SuppressWarnings("CheckReturnValue")
	public static class AndExpressionContext extends BooleanExpressionContext {

		public BooleanExpressionContext left;

		public Token operator;

		public BooleanExpressionContext right;

		public AndExpressionContext(BooleanExpressionContext ctx) {
			copyFrom(ctx);
		}

		public List<BooleanExpressionContext> booleanExpression() {
			return getRuleContexts(BooleanExpressionContext.class);
		}

		public BooleanExpressionContext booleanExpression(int i) {
			return getRuleContext(BooleanExpressionContext.class, i);
		}

		public TerminalNode AND() {
			return getToken(FiltersParser.AND, 0);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener) {
				((FiltersListener) listener).enterAndExpression(this);
			}
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener) {
				((FiltersListener) listener).exitAndExpression(this);
			}
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor) {
				return ((FiltersVisitor<? extends T>) visitor).visitAndExpression(this);
			}
			else {
				return visitor.visitChildren(this);
			}
		}

	}

	@SuppressWarnings("CheckReturnValue")
	public static class InExpressionContext extends BooleanExpressionContext {

		public InExpressionContext(BooleanExpressionContext ctx) {
			copyFrom(ctx);
		}

		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class, 0);
		}

		public TerminalNode IN() {
			return getToken(FiltersParser.IN, 0);
		}

		public ConstantArrayContext constantArray() {
			return getRuleContext(ConstantArrayContext.class, 0);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener) {
				((FiltersListener) listener).enterInExpression(this);
			}
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener) {
				((FiltersListener) listener).exitInExpression(this);
			}
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor) {
				return ((FiltersVisitor<? extends T>) visitor).visitInExpression(this);
			}
			else {
				return visitor.visitChildren(this);
			}
		}

	}

	@SuppressWarnings("CheckReturnValue")
	public static class NotExpressionContext extends BooleanExpressionContext {

		public NotExpressionContext(BooleanExpressionContext ctx) {
			copyFrom(ctx);
		}

		public TerminalNode NOT() {
			return getToken(FiltersParser.NOT, 0);
		}

		public BooleanExpressionContext booleanExpression() {
			return getRuleContext(BooleanExpressionContext.class, 0);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener) {
				((FiltersListener) listener).enterNotExpression(this);
			}
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener) {
				((FiltersListener) listener).exitNotExpression(this);
			}
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor) {
				return ((FiltersVisitor<? extends T>) visitor).visitNotExpression(this);
			}
			else {
				return visitor.visitChildren(this);
			}
		}

	}

	@SuppressWarnings("CheckReturnValue")
	public static class CompareExpressionContext extends BooleanExpressionContext {

		public CompareExpressionContext(BooleanExpressionContext ctx) {
			copyFrom(ctx);
		}

		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class, 0);
		}

		public CompareContext compare() {
			return getRuleContext(CompareContext.class, 0);
		}

		public ConstantContext constant() {
			return getRuleContext(ConstantContext.class, 0);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener) {
				((FiltersListener) listener).enterCompareExpression(this);
			}
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener) {
				((FiltersListener) listener).exitCompareExpression(this);
			}
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor) {
				return ((FiltersVisitor<? extends T>) visitor).visitCompareExpression(this);
			}
			else {
				return visitor.visitChildren(this);
			}
		}

	}

	@SuppressWarnings("CheckReturnValue")
	public static class OrExpressionContext extends BooleanExpressionContext {

		public BooleanExpressionContext left;

		public Token operator;

		public BooleanExpressionContext right;

		public OrExpressionContext(BooleanExpressionContext ctx) {
			copyFrom(ctx);
		}

		public List<BooleanExpressionContext> booleanExpression() {
			return getRuleContexts(BooleanExpressionContext.class);
		}

		public BooleanExpressionContext booleanExpression(int i) {
			return getRuleContext(BooleanExpressionContext.class, i);
		}

		public TerminalNode OR() {
			return getToken(FiltersParser.OR, 0);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener) {
				((FiltersListener) listener).enterOrExpression(this);
			}
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener) {
				((FiltersListener) listener).exitOrExpression(this);
			}
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor) {
				return ((FiltersVisitor<? extends T>) visitor).visitOrExpression(this);
			}
			else {
				return visitor.visitChildren(this);
			}
		}

	}

	@SuppressWarnings("CheckReturnValue")
	public static class GroupExpressionContext extends BooleanExpressionContext {

		public GroupExpressionContext(BooleanExpressionContext ctx) {
			copyFrom(ctx);
		}

		public TerminalNode LEFT_PARENTHESIS() {
			return getToken(FiltersParser.LEFT_PARENTHESIS, 0);
		}

		public BooleanExpressionContext booleanExpression() {
			return getRuleContext(BooleanExpressionContext.class, 0);
		}

		public TerminalNode RIGHT_PARENTHESIS() {
			return getToken(FiltersParser.RIGHT_PARENTHESIS, 0);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener) {
				((FiltersListener) listener).enterGroupExpression(this);
			}
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener) {
				((FiltersListener) listener).exitGroupExpression(this);
			}
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor) {
				return ((FiltersVisitor<? extends T>) visitor).visitGroupExpression(this);
			}
			else {
				return visitor.visitChildren(this);
			}
		}

	}

	@SuppressWarnings("CheckReturnValue")
	public static class ConstantArrayContext extends ParserRuleContext {

		public ConstantArrayContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}

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

		@Override
		public int getRuleIndex() {
			return RULE_constantArray;
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener) {
				((FiltersListener) listener).enterConstantArray(this);
			}
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener) {
				((FiltersListener) listener).exitConstantArray(this);
			}
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor) {
				return ((FiltersVisitor<? extends T>) visitor).visitConstantArray(this);
			}
			else {
				return visitor.visitChildren(this);
			}
		}

	}

	@SuppressWarnings("CheckReturnValue")
	public static class CompareContext extends ParserRuleContext {

		public CompareContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}

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

		@Override
		public int getRuleIndex() {
			return RULE_compare;
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener) {
				((FiltersListener) listener).enterCompare(this);
			}
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener) {
				((FiltersListener) listener).exitCompare(this);
			}
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor) {
				return ((FiltersVisitor<? extends T>) visitor).visitCompare(this);
			}
			else {
				return visitor.visitChildren(this);
			}
		}

	}

	@SuppressWarnings("CheckReturnValue")
	public static class IdentifierContext extends ParserRuleContext {

		public IdentifierContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}

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

		@Override
		public int getRuleIndex() {
			return RULE_identifier;
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener) {
				((FiltersListener) listener).enterIdentifier(this);
			}
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener) {
				((FiltersListener) listener).exitIdentifier(this);
			}
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor) {
				return ((FiltersVisitor<? extends T>) visitor).visitIdentifier(this);
			}
			else {
				return visitor.visitChildren(this);
			}
		}

	}

	@SuppressWarnings("CheckReturnValue")
	public static class ConstantContext extends ParserRuleContext {

		public ConstantContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}

		public ConstantContext() {
		}

		@Override
		public int getRuleIndex() {
			return RULE_constant;
		}

		public void copyFrom(ConstantContext ctx) {
			super.copyFrom(ctx);
		}

	}

	@SuppressWarnings("CheckReturnValue")
	public static class DecimalConstantContext extends ConstantContext {

		public DecimalConstantContext(ConstantContext ctx) {
			copyFrom(ctx);
		}

		public TerminalNode DECIMAL_VALUE() {
			return getToken(FiltersParser.DECIMAL_VALUE, 0);
		}

		public TerminalNode MINUS() {
			return getToken(FiltersParser.MINUS, 0);
		}

		public TerminalNode PLUS() {
			return getToken(FiltersParser.PLUS, 0);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener) {
				((FiltersListener) listener).enterDecimalConstant(this);
			}
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener) {
				((FiltersListener) listener).exitDecimalConstant(this);
			}
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor) {
				return ((FiltersVisitor<? extends T>) visitor).visitDecimalConstant(this);
			}
			else {
				return visitor.visitChildren(this);
			}
		}

	}

	@SuppressWarnings("CheckReturnValue")
	public static class TextConstantContext extends ConstantContext {

		public TextConstantContext(ConstantContext ctx) {
			copyFrom(ctx);
		}

		public List<TerminalNode> QUOTED_STRING() {
			return getTokens(FiltersParser.QUOTED_STRING);
		}

		public TerminalNode QUOTED_STRING(int i) {
			return getToken(FiltersParser.QUOTED_STRING, i);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener) {
				((FiltersListener) listener).enterTextConstant(this);
			}
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener) {
				((FiltersListener) listener).exitTextConstant(this);
			}
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor) {
				return ((FiltersVisitor<? extends T>) visitor).visitTextConstant(this);
			}
			else {
				return visitor.visitChildren(this);
			}
		}

	}

	@SuppressWarnings("CheckReturnValue")
	public static class BooleanConstantContext extends ConstantContext {

		public BooleanConstantContext(ConstantContext ctx) {
			copyFrom(ctx);
		}

		public TerminalNode BOOLEAN_VALUE() {
			return getToken(FiltersParser.BOOLEAN_VALUE, 0);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener) {
				((FiltersListener) listener).enterBooleanConstant(this);
			}
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener) {
				((FiltersListener) listener).exitBooleanConstant(this);
			}
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor) {
				return ((FiltersVisitor<? extends T>) visitor).visitBooleanConstant(this);
			}
			else {
				return visitor.visitChildren(this);
			}
		}

	}

	@SuppressWarnings("CheckReturnValue")
	public static class IntegerConstantContext extends ConstantContext {

		public IntegerConstantContext(ConstantContext ctx) {
			copyFrom(ctx);
		}

		public TerminalNode INTEGER_VALUE() {
			return getToken(FiltersParser.INTEGER_VALUE, 0);
		}

		public TerminalNode MINUS() {
			return getToken(FiltersParser.MINUS, 0);
		}

		public TerminalNode PLUS() {
			return getToken(FiltersParser.PLUS, 0);
		}

		@Override
		public void enterRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener) {
				((FiltersListener) listener).enterIntegerConstant(this);
			}
		}

		@Override
		public void exitRule(ParseTreeListener listener) {
			if (listener instanceof FiltersListener) {
				((FiltersListener) listener).exitIntegerConstant(this);
			}
		}

		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if (visitor instanceof FiltersVisitor) {
				return ((FiltersVisitor<? extends T>) visitor).visitIntegerConstant(this);
			}
			else {
				return visitor.visitChildren(this);
			}
		}

	}

}
