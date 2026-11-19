# break-triage

An LLM-assisted triage tool for trade settlement breaks, with an evaluation harness. It is a small project built to show how to put a language model into an operations workflow safely: deterministic guardrails around the model, validated output, and quality measured with labelled test cases instead of by eye.

All data is synthetic. The runbooks are simplified examples, not any firm's real procedures.

## What it does

A settlement break is a mismatch between our booking and the counterparty's. Given a break, the tool returns:

- a category (quantity mismatch, price mismatch, missing SSI, unconfirmed trade, duplicate trade, settlement date mismatch, or needs a human)
- the runbook that applies
- a proposed action and a one-sentence rationale

It only proposes. A person approves anything that changes a booking.

## How it works

```
break record
   |
   v
Guardrails (plain code)
   | over AUD 5M, or more than one field differs --> NEEDS_HUMAN   [RULE]
   v
Prompt: general rules + six runbooks + the break
   (calculations supplied by code, free-text note escaped and treated as data)
   |
   v
Claude --> JSON {category, runbookId, action, rationale}
   |
   v
Validation: valid JSON, known category, runbook matches category, action allowed
   | pass --> result                                                [MODEL]
   | fail --> one retry --> still invalid --> NEEDS_HUMAN           [FALLBACK]
   |
   API unreachable after the SDK's own retries --> NEEDS_HUMAN      [ERROR]
```

### Design decisions

- **Rules that must never fail live in code, not in the prompt.** The notional limit and the "more than one field differs" rule are applied before the model is called. Those cases skip the model, which is safer and cheaper.
- **The model never does arithmetic.** Notional, the price difference percentage and the list of differing fields are calculated in code and given to the model as facts.
- **Free text is data, not instructions.** The note field can contain anything, including attempts to give the model orders. The prompt says so, angle brackets in the note are escaped, and an instruction-like note should escalate.
- **The model's output is never trusted.** It must parse, name a real category, cite the runbook that belongs to that category, and propose an action allowed for that category. One retry, then a human takes over.
- **Failing safe beats guessing.** Every failure path ends in `NEEDS_HUMAN`.
- **Outages are separated from bad answers.** Transient API failures (rate limits, 5xx, network) become `ERROR`. Configuration mistakes such as a bad API key crash loudly instead of quietly escalating everything. The two are counted separately so an outage is never mistaken for a drop in accuracy.
- **No framework.** The project uses the official Anthropic Java SDK directly, behind a small `LlmClient` interface, so every part of the prompt, validation and retry logic is visible and testable with a fake client.
- **No retrieval yet.** With six short runbooks, all of them fit in the prompt. Retrieval is worth adding once the runbooks grow, and the eval would show whether it helps.

## Evaluation

Quality is measured with labelled cases, run by `EvalRunner`.

- **Development set:** 30 cases (`eval-cases.json`), used while building and tuning the prompt. Four cases for each of six categories, plus six that must go to a human: a trade over the notional limit, two fields wrong, a prompt injection in the note, a corporate action, a disputed trade, and a sanctions flag.
- **Held-out set:** 12 harder cases (`eval-heldout.json`) written after tuning. They use different wording, boundary values (a notional of exactly AUD 5,000,000, a price difference of exactly 0.1%), an empty note, a misleading distractor, and a different style of injection. The held-out set was run once, with the prompt frozen.

Metrics reported:

- **Accuracy** on the cases that were scored (API errors are excluded and reported separately)
- **Unsafe automations:** cases that needed a human but were automated. This is the costly mistake, so the pass gate requires zero.
- **Decisions by source:** rule, model, fallback, error
- **Tokens and average latency per model call**

The run passes only if accuracy is at least 90%, there are no unsafe automations, and there are no API errors.

### Results

Model: `claude-haiku-5-5`.

| Set | Prompt | Cases | Accuracy | Unsafe | Fallbacks | Tokens in / out | Avg latency per call |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Development | v1 | 30 | 93.3% (28/30) | 0 | 2 | 74,544 / 10,206 | 2,231 ms |
| Development | v2 | 30 | 100% (30/30) | 0 | 0 | 67,635 / 7,581 | 1,789 ms |
| Held-out | v2 | 12 | 100% (12/12) | 0 | 0 | 27,553 / 2,967 | 1,829 ms |

The raw result files are in `docs/eval-results/`.

### What v1 got wrong

Both v1 failures were safe: the model's answer was rejected twice and the case went to a human. The cause was a malformed reply, not a wrong classification. Logging the raw reply showed a valid JSON object that stopped partway through a long rationale, which is most likely the reply hitting the output token limit. Prompt v2 asks for a rationale of one sentence and at most 30 words, and the limit was raised as a safety net. The fallbacks disappeared and output tokens dropped by about a quarter.

## Limitations

- The data is synthetic and the runbooks are simplified, so the results say nothing about real operations data.
- The held-out set has only 12 cases. With no failures in 12, the true error rate could still be fairly high, so read 100% as "generalised on a first small check", not "reliable".
- Each case was run once. The model is not deterministic, so scores can move by a case or two between runs.
- The eval scores the category and runbook. Actions are validated against an allowed list but not compared with an expected action.
- Cost is only reported if you supply token prices through environment variables.

## Running it

Requirements: Java 21 and Maven.

```
mvn test
```

The unit tests use a fake model client and need no API key.

To run the evaluation against the live API:

```
export ANTHROPIC_API_KEY=your-key
mvn -q compile exec:java -Dexec.mainClass=breaktriage.eval.EvalRunner
```

Options (environment variables):

| Variable | Purpose | Default |
| --- | --- | --- |
| `BREAK_MODEL` | Model to use | `claude-haiku-5-5` |
| `EVAL_SET` | Which case file to run, for example `eval-heldout.json` | `eval-cases.json` |
| `EVAL_ONLY` | Comma-separated case IDs to run, for example `CASE-004,CASE-022` | all cases |
| `EVAL_THRESHOLD` | Accuracy needed to pass | `0.90` |
| `BREAK_PRICE_IN`, `BREAK_PRICE_OUT` | USD per million input and output tokens, to print an estimated cost | unset |

The runner exits with code 0 on pass and 1 on fail, so it can gate a CI job. Each run writes a JSON file to `results/`, which is not committed.

## Project layout

```
src/main/java/breaktriage/
  model/    Category, BreakRecord, Expected, EvalCase
  data/     DataLoader
  llm/      LlmClient, ClaudeClient, LlmResponse, LlmUnavailableException
  triage/   Guardrails, PromptBuilder, TriageService, TriageResult, DecisionSource
  eval/     EvalRunner, Evaluator, Summary, CaseOutcome
src/main/resources/
  data/     eval-cases.json, eval-heldout.json
  runbooks/ GENERAL.md, RB-001 to RB-006
src/test/java/   unit tests with a fake model client
docs/eval-results/   saved eval runs
```

## Possible next steps

- Run each case several times and report the spread.
- Log the API stop reason, to detect truncated replies directly.
- Add retrieval once the runbook set is large enough to need it, and measure the effect with the eval.
- Run the eval in CI on prompt changes.
- Grow the held-out set, and write a fresh one whenever it has been used for tuning.