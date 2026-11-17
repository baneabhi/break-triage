# break-triage

An LLM-assisted triage tool for trade settlement breaks, built to show an eval-driven approach to applied AI. All data is synthetic.

Given a break (our booking against the counterparty's), the tool proposes a category, the runbook that applies, and an action. Code does the calculations and enforces safety rules. A person approves anything that changes a booking.

## Layout

```
src/main/java/breaktriage/
  model/    Category, BreakRecord, Expected, EvalCase
  data/     DataLoader (reads cases and runbooks from the classpath)
src/main/resources/
  data/eval-cases.json   30 labelled cases
  runbooks/              GENERAL.md (rules for every break) and RB-001 to RB-006
src/test/java/breaktriage/data/DataIntegrityTest.java
```

## Eval cases

30 labelled cases: 4 each for six categories, plus 6 that must go to a human.

| Category | Runbook | Cases |
| --- | --- | --- |
| QUANTITY_MISMATCH | RB-001 | 4 |
| PRICE_MISMATCH | RB-002 | 4 (one inside the 0.1% tolerance, one just outside) |
| MISSING_SSI | RB-003 | 4 |
| UNCONFIRMED_TRADE | RB-004 | 4 |
| DUPLICATE_TRADE | RB-005 | 4 (the quantities look like a mismatch, which is the trap) |
| SETTLE_DATE_MISMATCH | RB-006 | 4 |
| NEEDS_HUMAN | none | 6: notional over AUD 5M, two fields wrong, a prompt injection in the note, a corporate action, a disputed trade, a sanctions flag |

## Setup

- Java 21 and Maven.
- Set `ANTHROPIC_API_KEY` as an environment variable. Never commit a key.
- Run `mvn test`. The data integrity tests should pass before you build anything else.

## Build plan

1. Data and models (done)
2. `LlmClient` interface and the Claude implementation
3. Triage service: prompt, structured output, validation, guardrails
4. Eval runner: accuracy, per-category results, cost and latency, pass/fail threshold
5. Prompt versions and a results table in this README
