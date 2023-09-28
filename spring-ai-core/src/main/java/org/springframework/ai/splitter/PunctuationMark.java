package org.springframework.ai.splitter;

import java.util.Arrays;

public enum PunctuationMark {
    PERIOD('.'),
    QUESTION_MARK('?'),
    EXCLAMATION_MARK('!'),
    SINGLE_QUOTATION('\''),
    DOUBLE_QUOTATION('\"'),
    LINE_BREAK('\n');

    private final char mark;

    PunctuationMark(char mark) {
        this.mark = mark;
    }

    public static Boolean first(char character){
        return Arrays.stream(PunctuationMark.values())
                     .anyMatch(punctuation -> punctuation.mark  == character);
    }
}