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

import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link FiltersParser}.
 */
public interface FiltersListener extends ParseTreeListener {

	/**
	 * Enter a parse tree produced by {@link FiltersParser#where}.
	 * @param ctx the parse tree
	 */
	void enterWhere(FiltersParser.WhereContext ctx);

	/**
	 * Exit a parse tree produced by {@link FiltersParser#where}.
	 * @param ctx the parse tree
	 */
	void exitWhere(FiltersParser.WhereContext ctx);

	/**
	 * Enter a parse tree produced by the {@code NinExpression} labeled alternative in
	 * {@link FiltersParser#booleanExpression}.
	 * @param ctx the parse tree
	 */
	void enterNinExpression(FiltersParser.NinExpressionContext ctx);

	/**
	 * Exit a parse tree produced by the {@code NinExpression} labeled alternative in
	 * {@link FiltersParser#booleanExpression}.
	 * @param ctx the parse tree
	 */
	void exitNinExpression(FiltersParser.NinExpressionContext ctx);

	/**
	 * Enter a parse tree produced by the {@code IsNullExpression} labeled alternative in
	 * {@link FiltersParser#booleanExpression}.
	 * @param ctx the parse tree
	 */
	void enterIsNullExpression(FiltersParser.IsNullExpressionContext ctx);

	/**
	 * Exit a parse tree produced by the {@code IsNullExpression} labeled alternative in
	 * {@link FiltersParser#booleanExpression}.
	 * @param ctx the parse tree
	 */
	void exitIsNullExpression(FiltersParser.IsNullExpressionContext ctx);

	/**
	 * Enter a parse tree produced by the {@code IsNotNullExpression} labeled alternative
	 * in {@link FiltersParser#booleanExpression}.
	 * @param ctx the parse tree
	 */
	void enterIsNotNullExpression(FiltersParser.IsNotNullExpressionContext ctx);

	/**
	 * Exit a parse tree produced by the {@code IsNotNullExpression} labeled alternative
	 * in {@link FiltersParser#booleanExpression}.
	 * @param ctx the parse tree
	 */
	void exitIsNotNullExpression(FiltersParser.IsNotNullExpressionContext ctx);

	/**
	 * Enter a parse tree produced by the {@code AndExpression} labeled alternative in
	 * {@link FiltersParser#booleanExpression}.
	 * @param ctx the parse tree
	 */
	void enterAndExpression(FiltersParser.AndExpressionContext ctx);

	/**
	 * Exit a parse tree produced by the {@code AndExpression} labeled alternative in
	 * {@link FiltersParser#booleanExpression}.
	 * @param ctx the parse tree
	 */
	void exitAndExpression(FiltersParser.AndExpressionContext ctx);

	/**
	 * Enter a parse tree produced by the {@code InExpression} labeled alternative in
	 * {@link FiltersParser#booleanExpression}.
	 * @param ctx the parse tree
	 */
	void enterInExpression(FiltersParser.InExpressionContext ctx);

	/**
	 * Exit a parse tree produced by the {@code InExpression} labeled alternative in
	 * {@link FiltersParser#booleanExpression}.
	 * @param ctx the parse tree
	 */
	void exitInExpression(FiltersParser.InExpressionContext ctx);

	/**
	 * Enter a parse tree produced by the {@code NotExpression} labeled alternative in
	 * {@link FiltersParser#booleanExpression}.
	 * @param ctx the parse tree
	 */
	void enterNotExpression(FiltersParser.NotExpressionContext ctx);

	/**
	 * Exit a parse tree produced by the {@code NotExpression} labeled alternative in
	 * {@link FiltersParser#booleanExpression}.
	 * @param ctx the parse tree
	 */
	void exitNotExpression(FiltersParser.NotExpressionContext ctx);

	/**
	 * Enter a parse tree produced by the {@code CompareExpression} labeled alternative in
	 * {@link FiltersParser#booleanExpression}.
	 * @param ctx the parse tree
	 */
	void enterCompareExpression(FiltersParser.CompareExpressionContext ctx);

	/**
	 * Exit a parse tree produced by the {@code CompareExpression} labeled alternative in
	 * {@link FiltersParser#booleanExpression}.
	 * @param ctx the parse tree
	 */
	void exitCompareExpression(FiltersParser.CompareExpressionContext ctx);

	/**
	 * Enter a parse tree produced by the {@code OrExpression} labeled alternative in
	 * {@link FiltersParser#booleanExpression}.
	 * @param ctx the parse tree
	 */
	void enterOrExpression(FiltersParser.OrExpressionContext ctx);

	/**
	 * Exit a parse tree produced by the {@code OrExpression} labeled alternative in
	 * {@link FiltersParser#booleanExpression}.
	 * @param ctx the parse tree
	 */
	void exitOrExpression(FiltersParser.OrExpressionContext ctx);

	/**
	 * Enter a parse tree produced by the {@code GroupExpression} labeled alternative in
	 * {@link FiltersParser#booleanExpression}.
	 * @param ctx the parse tree
	 */
	void enterGroupExpression(FiltersParser.GroupExpressionContext ctx);

	/**
	 * Exit a parse tree produced by the {@code GroupExpression} labeled alternative in
	 * {@link FiltersParser#booleanExpression}.
	 * @param ctx the parse tree
	 */
	void exitGroupExpression(FiltersParser.GroupExpressionContext ctx);

	/**
	 * Enter a parse tree produced by {@link FiltersParser#constantArray}.
	 * @param ctx the parse tree
	 */
	void enterConstantArray(FiltersParser.ConstantArrayContext ctx);

	/**
	 * Exit a parse tree produced by {@link FiltersParser#constantArray}.
	 * @param ctx the parse tree
	 */
	void exitConstantArray(FiltersParser.ConstantArrayContext ctx);

	/**
	 * Enter a parse tree produced by {@link FiltersParser#compare}.
	 * @param ctx the parse tree
	 */
	void enterCompare(FiltersParser.CompareContext ctx);

	/**
	 * Exit a parse tree produced by {@link FiltersParser#compare}.
	 * @param ctx the parse tree
	 */
	void exitCompare(FiltersParser.CompareContext ctx);

	/**
	 * Enter a parse tree produced by {@link FiltersParser#identifier}.
	 * @param ctx the parse tree
	 */
	void enterIdentifier(FiltersParser.IdentifierContext ctx);

	/**
	 * Exit a parse tree produced by {@link FiltersParser#identifier}.
	 * @param ctx the parse tree
	 */
	void exitIdentifier(FiltersParser.IdentifierContext ctx);

	/**
	 * Enter a parse tree produced by the {@code LongConstant} labeled alternative in
	 * {@link FiltersParser#constant}.
	 * @param ctx the parse tree
	 */
	void enterLongConstant(FiltersParser.LongConstantContext ctx);

	/**
	 * Exit a parse tree produced by the {@code LongConstant} labeled alternative in
	 * {@link FiltersParser#constant}.
	 * @param ctx the parse tree
	 */
	void exitLongConstant(FiltersParser.LongConstantContext ctx);

	/**
	 * Enter a parse tree produced by the {@code IntegerConstant} labeled alternative in
	 * {@link FiltersParser#constant}.
	 * @param ctx the parse tree
	 */
	void enterIntegerConstant(FiltersParser.IntegerConstantContext ctx);

	/**
	 * Exit a parse tree produced by the {@code IntegerConstant} labeled alternative in
	 * {@link FiltersParser#constant}.
	 * @param ctx the parse tree
	 */
	void exitIntegerConstant(FiltersParser.IntegerConstantContext ctx);

	/**
	 * Enter a parse tree produced by the {@code DecimalConstant} labeled alternative in
	 * {@link FiltersParser#constant}.
	 * @param ctx the parse tree
	 */
	void enterDecimalConstant(FiltersParser.DecimalConstantContext ctx);

	/**
	 * Exit a parse tree produced by the {@code DecimalConstant} labeled alternative in
	 * {@link FiltersParser#constant}.
	 * @param ctx the parse tree
	 */
	void exitDecimalConstant(FiltersParser.DecimalConstantContext ctx);

	/**
	 * Enter a parse tree produced by the {@code TextConstant} labeled alternative in
	 * {@link FiltersParser#constant}.
	 * @param ctx the parse tree
	 */
	void enterTextConstant(FiltersParser.TextConstantContext ctx);

	/**
	 * Exit a parse tree produced by the {@code TextConstant} labeled alternative in
	 * {@link FiltersParser#constant}.
	 * @param ctx the parse tree
	 */
	void exitTextConstant(FiltersParser.TextConstantContext ctx);

	/**
	 * Enter a parse tree produced by the {@code BooleanConstant} labeled alternative in
	 * {@link FiltersParser#constant}.
	 * @param ctx the parse tree
	 */
	void enterBooleanConstant(FiltersParser.BooleanConstantContext ctx);

	/**
	 * Exit a parse tree produced by the {@code BooleanConstant} labeled alternative in
	 * {@link FiltersParser#constant}.
	 * @param ctx the parse tree
	 */
	void exitBooleanConstant(FiltersParser.BooleanConstantContext ctx);

}