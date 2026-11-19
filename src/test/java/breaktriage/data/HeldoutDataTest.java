package breaktriage.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import breaktriage.model.Category;
import breaktriage.model.EvalCase;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class HeldoutDataTest {

    @Test
    void heldoutSetIsConsistentAndSeparateFromTheMainSet() throws IOException {
        List<EvalCase> heldout = DataLoader.loadEvalCases("eval-heldout.json");
        assertEquals(12, heldout.size());

        Set<String> mainIds = DataLoader.loadEvalCases().stream()
                .map(EvalCase::id).collect(Collectors.toSet());
        Map<String, String> runbooks = DataLoader.loadRunbooks();

        for (EvalCase c : heldout) {
            assertFalse(mainIds.contains(c.id()), c.id() + " overlaps the main set");
            if (c.expected().category() == Category.NEEDS_HUMAN) {
                assertNull(c.expected().runbookId());
            } else {
                assertTrue(runbooks.containsKey(c.expected().runbookId()));
            }
        }
    }
}