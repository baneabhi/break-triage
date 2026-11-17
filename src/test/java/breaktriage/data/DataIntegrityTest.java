package breaktriage.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import breaktriage.model.Category;
import breaktriage.model.EvalCase;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Checks the synthetic data is consistent before we trust any eval score built on it. */
class DataIntegrityTest {

    @Test
    void loadsThirtyCasesWithUniqueIds() throws IOException {
        List<EvalCase> cases = DataLoader.loadEvalCases();
        assertEquals(30, cases.size());

        Set<String> ids = new HashSet<>();
        for (EvalCase c : cases) {
            assertTrue(ids.add(c.id()), "duplicate case id " + c.id());
        }
    }

    @Test
    void everyRunbookLoadsAndIsNotEmpty() throws IOException {
        Map<String, String> runbooks = DataLoader.loadRunbooks();
        assertEquals(DataLoader.RUNBOOK_IDS.size(), runbooks.size());
        runbooks.forEach((id, text) -> assertFalse(text.isBlank(), id + " is empty"));
        assertFalse(DataLoader.loadGeneralRules().isBlank());
    }

    @Test
    void expectedRunbookExistsUnlessNeedsHuman() throws IOException {
        Map<String, String> runbooks = DataLoader.loadRunbooks();
        for (EvalCase c : DataLoader.loadEvalCases()) {
            if (c.expected().category() == Category.NEEDS_HUMAN) {
                assertNull(c.expected().runbookId(), c.id() + " should have no runbook");
            } else {
                assertNotNull(c.expected().runbookId(), c.id() + " needs a runbook");
                assertTrue(runbooks.containsKey(c.expected().runbookId()),
                        c.id() + " references unknown runbook " + c.expected().runbookId());
            }
        }
    }

    @Test
    void notionalIsCalculatedInCode() throws IOException {
        EvalCase first = DataLoader.loadEvalCases().get(0);
        // 10,000 units at 12.50 = 125,000
        assertEquals(0, first.input().notional().compareTo(new java.math.BigDecimal("125000")));
    }
}
