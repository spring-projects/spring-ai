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

import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced by
 * {@link FiltersParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for operations with
 * no return type.
 */
public interface FiltersVisitor<T> extends ParseTreeVisitor<T> {

	/**
	 * Visit a parse tree produced by {@link FiltersParser#where}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitWhere(FiltersParser.WhereContext ctx);

	/**
	 * Visit a parse tree produced by the {@code NinExpression} labeled alternative in
	 * {@link FiltersParser#booleanExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNinExpression(FiltersParser.NinExpressionContext ctx);

	/**
	 * Visit a parse tree produced by the {@code IsNullExpression} labeled alternative in
	 * {@link FiltersParser#booleanExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIsNullExpression(FiltersParser.IsNullExpressionContext ctx);

	/**
	 * Visit a parse tree produced by the {@code IsNotNullExpression} labeled alternative
	 * in {@link FiltersParser#booleanExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIsNotNullExpression(FiltersParser.IsNotNullExpressionContext ctx);

	/**
	 * Visit a parse tree produced by the {@code AndExpression} labeled alternative in
	 * {@link FiltersParser#booleanExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAndExpression(FiltersParser.AndExpressionContext ctx);

	/**
	 * Visit a parse tree produced by the {@code InExpression} labeled alternative in
	 * {@link FiltersParser#booleanExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInExpression(FiltersParser.InExpressionContext ctx);

	/**
	 * Visit a parse tree produced by the {@code NotExpression} labeled alternative in
	 * {@link FiltersParser#booleanExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNotExpression(FiltersParser.NotExpressionContext ctx);

	/**
	 * Visit a parse tree produced by the {@code CompareExpression} labeled alternative in
	 * {@link FiltersParser#booleanExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCompareExpression(FiltersParser.CompareExpressionContext ctx);

	/**
	 * Visit a parse tree produced by the {@code OrExpression} labeled alternative in
	 * {@link FiltersParser#booleanExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitOrExpression(FiltersParser.OrExpressionContext ctx);

	/**
	 * Visit a parse tree produced by the {@code GroupExpression} labeled alternative in
	 * {@link FiltersParser#booleanExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitGroupExpression(FiltersParser.GroupExpressionContext ctx);

	/**
	 * Visit a parse tree produced by {@link FiltersParser#constantArray}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConstantArray(FiltersParser.ConstantArrayContext ctx);

	/**
	 * Visit a parse tree produced by {@link FiltersParser#compare}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCompare(FiltersParser.CompareContext ctx);

	/**
	 * Visit a parse tree produced by {@link FiltersParser#identifier}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIdentifier(FiltersParser.IdentifierContext ctx);

	/**
	 * Visit a parse tree produced by the {@code LongConstant} labeled alternative in
	 * {@link FiltersParser#constant}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLongConstant(FiltersParser.LongConstantContext ctx);

	/**
	 * Visit a parse tree produced by the {@code IntegerConstant} labeled alternative in
	 * {@link FiltersParser#constant}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIntegerConstant(FiltersParser.IntegerConstantContext ctx);

	/**
	 * Visit a parse tree produced by the {@code DecimalConstant} labeled alternative in
	 * {@link FiltersParser#constant}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDecimalConstant(FiltersParser.DecimalConstantContext ctx);

	/**
	 * Visit a parse tree produced by the {@code TextConstant} labeled alternative in
	 * {@link FiltersParser#constant}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTextConstant(FiltersParser.TextConstantContext ctx);

	/**
	 * Visit a parse tree produced by the {@code BooleanConstant} labeled alternative in
	 * {@link FiltersParser#constant}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBooleanConstant(FiltersParser.BooleanConstantContext ctx);

}