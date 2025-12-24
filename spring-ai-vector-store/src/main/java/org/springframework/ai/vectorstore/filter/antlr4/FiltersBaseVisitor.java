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

import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;

/**
 * This class provides an empty implementation of {@link FiltersVisitor}, which can be
 * extended to create a visitor which only needs to handle a subset of the available
 * methods.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for operations with
 * no return type.
 */
@SuppressWarnings("CheckReturnValue")
public class FiltersBaseVisitor<T> extends AbstractParseTreeVisitor<T> implements FiltersVisitor<T> {

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * The default implementation returns the result of calling {@link #visitChildren} on
	 * {@code ctx}.
	 * </p>
	 */
	@Override
	public T visitWhere(FiltersParser.WhereContext ctx) {
		return visitChildren(ctx);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * The default implementation returns the result of calling {@link #visitChildren} on
	 * {@code ctx}.
	 * </p>
	 */
	@Override
	public T visitNinExpression(FiltersParser.NinExpressionContext ctx) {
		return visitChildren(ctx);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * The default implementation returns the result of calling {@link #visitChildren} on
	 * {@code ctx}.
	 * </p>
	 */
	@Override
	public T visitIsNullExpression(FiltersParser.IsNullExpressionContext ctx) {
		return visitChildren(ctx);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * The default implementation returns the result of calling {@link #visitChildren} on
	 * {@code ctx}.
	 * </p>
	 */
	@Override
	public T visitIsNotNullExpression(FiltersParser.IsNotNullExpressionContext ctx) {
		return visitChildren(ctx);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * The default implementation returns the result of calling {@link #visitChildren} on
	 * {@code ctx}.
	 * </p>
	 */
	@Override
	public T visitAndExpression(FiltersParser.AndExpressionContext ctx) {
		return visitChildren(ctx);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * The default implementation returns the result of calling {@link #visitChildren} on
	 * {@code ctx}.
	 * </p>
	 */
	@Override
	public T visitInExpression(FiltersParser.InExpressionContext ctx) {
		return visitChildren(ctx);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * The default implementation returns the result of calling {@link #visitChildren} on
	 * {@code ctx}.
	 * </p>
	 */
	@Override
	public T visitNotExpression(FiltersParser.NotExpressionContext ctx) {
		return visitChildren(ctx);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * The default implementation returns the result of calling {@link #visitChildren} on
	 * {@code ctx}.
	 * </p>
	 */
	@Override
	public T visitCompareExpression(FiltersParser.CompareExpressionContext ctx) {
		return visitChildren(ctx);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * The default implementation returns the result of calling {@link #visitChildren} on
	 * {@code ctx}.
	 * </p>
	 */
	@Override
	public T visitOrExpression(FiltersParser.OrExpressionContext ctx) {
		return visitChildren(ctx);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * The default implementation returns the result of calling {@link #visitChildren} on
	 * {@code ctx}.
	 * </p>
	 */
	@Override
	public T visitGroupExpression(FiltersParser.GroupExpressionContext ctx) {
		return visitChildren(ctx);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * The default implementation returns the result of calling {@link #visitChildren} on
	 * {@code ctx}.
	 * </p>
	 */
	@Override
	public T visitConstantArray(FiltersParser.ConstantArrayContext ctx) {
		return visitChildren(ctx);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * The default implementation returns the result of calling {@link #visitChildren} on
	 * {@code ctx}.
	 * </p>
	 */
	@Override
	public T visitCompare(FiltersParser.CompareContext ctx) {
		return visitChildren(ctx);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * The default implementation returns the result of calling {@link #visitChildren} on
	 * {@code ctx}.
	 * </p>
	 */
	@Override
	public T visitIdentifier(FiltersParser.IdentifierContext ctx) {
		return visitChildren(ctx);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * The default implementation returns the result of calling {@link #visitChildren} on
	 * {@code ctx}.
	 * </p>
	 */
	@Override
	public T visitLongConstant(FiltersParser.LongConstantContext ctx) {
		return visitChildren(ctx);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * The default implementation returns the result of calling {@link #visitChildren} on
	 * {@code ctx}.
	 * </p>
	 */
	@Override
	public T visitIntegerConstant(FiltersParser.IntegerConstantContext ctx) {
		return visitChildren(ctx);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * The default implementation returns the result of calling {@link #visitChildren} on
	 * {@code ctx}.
	 * </p>
	 */
	@Override
	public T visitDecimalConstant(FiltersParser.DecimalConstantContext ctx) {
		return visitChildren(ctx);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * The default implementation returns the result of calling {@link #visitChildren} on
	 * {@code ctx}.
	 * </p>
	 */
	@Override
	public T visitTextConstant(FiltersParser.TextConstantContext ctx) {
		return visitChildren(ctx);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * The default implementation returns the result of calling {@link #visitChildren} on
	 * {@code ctx}.
	 * </p>
	 */
	@Override
	public T visitBooleanConstant(FiltersParser.BooleanConstantContext ctx) {
		return visitChildren(ctx);
	}

}