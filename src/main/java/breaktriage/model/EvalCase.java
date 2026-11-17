package breaktriage.model;

/** A labelled test case: an input break and the answer we expect. */
public record EvalCase(String id, BreakRecord input, Expected expected) {}
