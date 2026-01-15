grammar Filters;

@header {
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
}

where
    : WHERE booleanExpression EOF
    ;

booleanExpression
    : identifier compare constant                                 # CompareExpression
    | identifier IN constantArray                                 # InExpression
    | identifier (NOT IN | NIN) constantArray                     # NinExpression
    | identifier IS NULL                                          # IsNullExpression
    | identifier IS NOT NULL                                      # IsNotNullExpression
    | left=booleanExpression operator=AND right=booleanExpression # AndExpression
    | left=booleanExpression operator=OR right=booleanExpression  # OrExpression
    | LEFT_PARENTHESIS booleanExpression RIGHT_PARENTHESIS        # GroupExpression
    | NOT booleanExpression                                       # NotExpression
    ;

constantArray
    : LEFT_SQUARE_BRACKETS constant (COMMA constant)* RIGHT_SQUARE_BRACKETS
    ;

compare:
    EQUALS | GT | GE | LT | LE | NE;

identifier
    : IDENTIFIER DOT IDENTIFIER
    | IDENTIFIER
    | QUOTED_STRING
    ;

constant
    : (MINUS | PLUS)? INTEGER_VALUE LONG_SUFFIX # LongConstant
    | (MINUS | PLUS)? INTEGER_VALUE # IntegerConstant
    | (MINUS | PLUS)? DECIMAL_VALUE # DecimalConstant
    | QUOTED_STRING                 # TextConstant
    | BOOLEAN_VALUE                 # BooleanConstant
    ;

LONG_SUFFIX : [lL];

WHERE : 'WHERE' | 'where';

DOT: '.';
COMMA: ',';
LEFT_SQUARE_BRACKETS: '[';
RIGHT_SQUARE_BRACKETS: ']';
LEFT_PARENTHESIS: '(';
RIGHT_PARENTHESIS: ')';
EQUALS: '==';
MINUS : '-';
PLUS: '+';
GT: '>';
GE: '>=';
LT: '<';
LE: '<=';
NE: '!=';

AND: 'AND' | 'and' | '&&';
OR: 'OR' | 'or' | '||';
IN: 'IN' | 'in';
NIN: 'NIN' | 'nin';
NOT: 'NOT' | 'not';
IS: 'IS' | 'is';
NULL: 'NULL' | 'null';

BOOLEAN_VALUE
    : 'TRUE' | 'true' | 'FALSE' | 'false'
    ;

QUOTED_STRING
    : '\'' ( ~('\''|'\\') | ('\\' .) )* '\''
    | '"' ( ~('"'|'\\') | ('\\' .) )* '"'
    ;

INTEGER_VALUE
    : DIGIT+
    ;

DECIMAL_VALUE
    : DECIMAL_DIGITS
    ;

IDENTIFIER
    : (LETTER | '_') (LETTER | DIGIT | '_')*
    ;

fragment DECIMAL_DIGITS
    : DIGIT+ '.' DIGIT*
    | '.' DIGIT+
    ;

fragment DIGIT
    : [0-9]
    ;

fragment LETTER
    : [a-zA-Z]
    ;

WS
    : [ \r\n\t]+ -> channel(HIDDEN)
    ;
