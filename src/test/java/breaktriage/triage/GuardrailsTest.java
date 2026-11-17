package breaktriage.triage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import breaktriage.data.DataLoader;
import breaktriage.model.BreakRecord;
import java.io.IOException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class GuardrailsTest {

    private static BreakRecord input(String caseId) throws IOException {
        return DataLoader.loadEvalCases().stream()
                .filter(c -> c.id().equals(caseId)).findFirst().orElseThrow().input();
    }

    @Test
    void largeNotionalMustEscalate() throws IOException {
        assertTrue(Guardrails.mustEscalate(input("CASE-025")).isPresent());
    }

    @Test
    void twoDifferingFieldsMustEscalate() throws IOException {
        assertTrue(Guardrails.mustEscalate(input("CASE-026")).isPresent());
    }

    @Test
    void ordinaryBreakIsNotEscalated() throws IOException {
        assertTrue(Guardrails.mustEscalate(input("CASE-001")).isEmpty());
    }

    @Test
    void priceDifferenceJustOverTolerance() throws IOException {
        assertEquals(new BigDecimal("0.1099"),
                Guardrails.priceDifferencePct(input("CASE-008")).orElseThrow());
    }
}