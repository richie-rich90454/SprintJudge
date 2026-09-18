package com.sprintjudge.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Edge-case coverage for the accuracy-first composite. */
class SjsCalculatorTest {

    @Test
    void difficultyWeightCoversDokAndFormats() {
        assertEquals(1.0, SjsCalculator.difficultyWeight(1, 0.5, "MCQ"), 0.001);
        assertEquals(1.5, SjsCalculator.difficultyWeight(2, 0.5, "MCQ"), 0.001);
        assertEquals(2.5, SjsCalculator.difficultyWeight(3, 0.5, "MCQ"), 0.001);
        assertEquals(4.0, SjsCalculator.difficultyWeight(4, 0.5, "MCQ"), 0.001);
        assertEquals(1.0, SjsCalculator.difficultyWeight(9, 0.5, "MCQ"), 0.001);
        assertEquals(1.2, SjsCalculator.difficultyWeight(1, 0.5, "NUMERIC"), 0.001);
        assertEquals(1.2, SjsCalculator.difficultyWeight(1, 0.5, "MULTI_SELECT"), 0.001);
        assertEquals(1.3, SjsCalculator.difficultyWeight(1, 0.5, "ORDERING"), 0.001);
        assertEquals(1.5, SjsCalculator.difficultyWeight(1, 0.5, "CODE"), 0.001);
        assertEquals(1.5, SjsCalculator.difficultyWeight(1, 0.5, "OJ_FULL"), 0.001);
        assertEquals(1.5, SjsCalculator.difficultyWeight(1, 0.5, "OJ_PATCH"), 0.001);
        assertEquals(1.5, SjsCalculator.difficultyWeight(1, 0.5, "CODE_COMPLETION"), 0.001);
        assertEquals(2.0, SjsCalculator.difficultyWeight(1, 0.5, "FREE_RESPONSE"), 0.001);
        assertEquals(2.0, SjsCalculator.difficultyWeight(1, 0.5, "COMPOSITION"), 0.001);
        assertEquals(2.0, SjsCalculator.difficultyWeight(1, 0.5, "PROOF"), 0.001);
        assertEquals(1.0, SjsCalculator.difficultyWeight(1, 0.5, null), 0.001);
        assertEquals(1.0, SjsCalculator.difficultyWeight(1, 0.5, "UNKNOWN"), 0.001);
    }

    @Test
    void difficultyWeightClampsPValue() {
        assertEquals(1.5, SjsCalculator.difficultyWeight(1, 0.0, "MCQ"), 0.001);
        assertEquals(0.5, SjsCalculator.difficultyWeight(1, 1.0, "MCQ"), 0.001);
        assertEquals(1.5, SjsCalculator.difficultyWeight(1, -2.0, "MCQ"), 0.001);
        assertEquals(0.5, SjsCalculator.difficultyWeight(1, 5.0, "MCQ"), 0.001);
    }

    @Test
    void speedCreditIsLinearBetweenQuartiles() {
        assertEquals(1.0, SjsCalculator.speedCredit(5.0, 10.0, 20.0, 30.0), 0.001);
        assertEquals(0.25, SjsCalculator.speedCredit(40.0, 10.0, 20.0, 30.0), 0.001);
        assertEquals(1.0, SjsCalculator.speedCredit(10.0, 10.0, 20.0, 30.0), 0.001);
        assertEquals(0.75, SjsCalculator.speedCredit(20.0, 10.0, 20.0, 30.0), 0.001);
        assertEquals(0.875, SjsCalculator.speedCredit(15.0, 10.0, 20.0, 30.0), 0.001);
        assertEquals(0.5, SjsCalculator.speedCredit(25.0, 10.0, 20.0, 30.0), 0.001);
    }

    @Test
    void signalCoversAllQuadrants() {
        assertEquals("FLUENT", SjsCalculator.speedAccuracySignal(10.0, 20.0, 0.9));
        assertEquals("GUESSING", SjsCalculator.speedAccuracySignal(10.0, 20.0, 0.4));
        assertEquals("CAREFUL", SjsCalculator.speedAccuracySignal(30.0, 20.0, 0.8));
        assertEquals("STRUGGLING", SjsCalculator.speedAccuracySignal(30.0, 20.0, 0.4));
        assertEquals("FLUENT", SjsCalculator.speedAccuracySignal(20.0, 20.0, 0.7));
    }

    @Test
    void tierCoversEveryBand() {
        assertEquals("Gold", SjsCalculator.tier(90.0));
        assertEquals("Gold", SjsCalculator.tier(85.0));
        assertEquals("Silver", SjsCalculator.tier(75.0));
        assertEquals("Silver", SjsCalculator.tier(70.0));
        assertEquals("Bronze", SjsCalculator.tier(60.0));
        assertEquals("Bronze", SjsCalculator.tier(55.0));
        assertEquals("Rising", SjsCalculator.tier(45.0));
        assertEquals("Rising", SjsCalculator.tier(40.0));
        assertEquals("Practicing", SjsCalculator.tier(10.0));
    }

    @Test
    void compositeAppliesDocumentedWeights() {
        assertEquals(80.0, SjsCalculator.composite(80.0, 80.0, 80.0, 80.0, 80.0), 0.001);
        assertEquals(50.0, SjsCalculator.composite(100.0, 0.0, 0.0, 0.0, 0.0), 0.001);
        assertEquals(20.0, SjsCalculator.composite(0.0, 100.0, 0.0, 0.0, 0.0), 0.001);
    }
}
