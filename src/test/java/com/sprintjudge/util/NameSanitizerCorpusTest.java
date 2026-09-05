package com.sprintjudge.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NameSanitizerCorpusTest {

    @Test
    void nullYieldsEmpty() {
        assertEquals("", NameSanitizer.sanitize(null));
    }

    @Test
    void emptyYieldsEmpty() {
        assertEquals("", NameSanitizer.sanitize(""));
    }

    @Test
    void blankSpacesYieldEmpty() {
        assertEquals("", NameSanitizer.sanitize("   "));
    }

    @Test
    void blankTabsYieldEmpty() {
        assertEquals("", NameSanitizer.sanitize("  \t  "));
    }

    @Test
    void boldTagsCollapseToWords() {
        assertEquals("bhib", NameSanitizer.sanitize("<b>hi</b>"));
    }

    @Test
    void svgOnloadPayloadStripsBrackets() {
        assertEquals("svg onloadx", NameSanitizer.sanitize("<svg onload=x>"));
    }

    @Test
    void javascriptUriPayloadDropsColonAndParens() {
        assertEquals("javascriptalert1", NameSanitizer.sanitize("javascript:alert(1)"));
    }

    @Test
    void eventHandlerQuotesAreStripped() {
        assertEquals("onmouseoveralert1", NameSanitizer.sanitize("\" onmouseover=\"alert(1)"));
    }

    @Test
    void dropTablePayloadKeepsWordsAndHyphens() {
        assertEquals("DROP TABLE users --", NameSanitizer.sanitize("'; DROP TABLE users; --"));
    }

    @Test
    void angleBracketsVanish() {
        assertEquals("div", NameSanitizer.sanitize("<div>"));
    }

    @Test
    void ampersandEqualsQuestionBangVanish() {
        assertEquals("abcd", NameSanitizer.sanitize("a&b=c?d!"));
    }

    @Test
    void tenThousandCharsTruncateToTwenty() {
        assertEquals("x".repeat(20), NameSanitizer.sanitize("x".repeat(10000)));
    }

    @Test
    void tenThousandCharsAllValidStillTwentyLong() {
        assertEquals(20, NameSanitizer.sanitize("ab".repeat(5000)).length());
    }

    @Test
    void hostileTagRepetitionTruncatesToTwentyBs() {
        assertEquals("b".repeat(20), NameSanitizer.sanitize("<b>".repeat(5000)));
    }

    @Test
    void nbspBetweenLettersIsDropped() {
        assertEquals("ab", NameSanitizer.sanitize("a" + (char) 0x00A0 + "b"));
    }

    @Test
    void emSpaceBetweenLettersIsDropped() {
        assertEquals("ab", NameSanitizer.sanitize("a" + (char) 0x2003 + "b"));
    }

    @Test
    void ideographicSpaceIsDropped() {
        assertEquals("ab", NameSanitizer.sanitize("a" + (char) 0x3000 + "b"));
    }

    @Test
    void zeroWidthSpaceIsDropped() {
        assertEquals("zerowidth", NameSanitizer.sanitize((char) 0x200B + "zero" + (char) 0x200B + "width"));
    }

    @Test
    void carriageReturnIsDropped() {
        assertEquals("ab", NameSanitizer.sanitize("a\rb"));
    }

    @Test
    void crlfIsDropped() {
        assertEquals("abc", NameSanitizer.sanitize("a\r\nb\rc"));
    }

    @Test
    void verticalTabIsDropped() {
        assertEquals("ab", NameSanitizer.sanitize("a" + (char) 0x000B + "b"));
    }

    @Test
    void nullCharIsDropped() {
        assertEquals("abc", NameSanitizer.sanitize("a" + (char) 0x0000 + "b" + (char) 0x0007 + "c"));
    }

    @Test
    void escapeCharIsDropped() {
        assertEquals("ab", NameSanitizer.sanitize("a" + (char) 0x001B + "b"));
    }

    @Test
    void onlyInvalidCharsYieldEmpty() {
        assertEquals("", NameSanitizer.sanitize("!!!@@@###$$$"));
    }

    @Test
    void onlyBracketsYieldEmpty() {
        assertEquals("", NameSanitizer.sanitize("<>"));
    }

    @Test
    void nineteenCharsSurviveUntouched() {
        assertEquals("a".repeat(19), NameSanitizer.sanitize("a".repeat(19)));
    }

    @Test
    void trailingSpaceAtCutIsTrimmedToNineteen() {
        assertEquals("a".repeat(19), NameSanitizer.sanitize("a".repeat(19) + " " + "b".repeat(10)));
    }

    @Test
    void leadingAndTrailingSpacesTrimmed() {
        assertEquals("alice", NameSanitizer.sanitize("  alice  "));
    }

    @Test
    void digitsPreserved() {
        assertEquals("player123", NameSanitizer.sanitize("player123"));
    }

    @Test
    void hyphensUnderscoresAndSpacesKept() {
        assertEquals("A-B_C 9", NameSanitizer.sanitize("A-B_C 9"));
    }

    @Test
    void leadingTrailingHyphensKept() {
        assertEquals("-ab-", NameSanitizer.sanitize("-ab-"));
    }

    @Test
    void latinAccentPreserved() {
        assertEquals("Caf" + (char) 0x00E9, NameSanitizer.sanitize("Caf" + (char) 0x00E9));
    }

    @Test
    void surrogateEmojiBetweenLettersRemoved() {
        assertEquals("AB", NameSanitizer.sanitize("A\ud83d\ude00B"));
    }

    @Test
    void atSignAndDotDroppedFromEmail() {
        assertEquals("aliceexamplecom", NameSanitizer.sanitize("alice@example.com"));
    }

    @Test
    void slashAndBackslashDropped() {
        assertEquals("ab", NameSanitizer.sanitize("a/b\\"));
    }

    @Test
    void pipeAndTildeDropped() {
        assertEquals("ab", NameSanitizer.sanitize("a|b~"));
    }

    @Test
    void quotesAndBacktickDropped() {
        assertEquals("abcd", NameSanitizer.sanitize("a'b\"c`d"));
    }

    @Test
    void singleValidCharSurvives() {
        assertEquals("z", NameSanitizer.sanitize("z"));
    }
}
