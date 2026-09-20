package io.github.imetaxas.benchmark;

/**
 * Wraps LangChain SQL (Python) via a long-lived subprocess using {@code
 * scripts/langchain_harness.py}.
 */
public final class LangChainHarness extends PythonHarness {

  @Override
  public String name() {
    return "langchain";
  }

  @Override
  protected String scriptPath() {
    return "scripts/langchain_harness.py";
  }
}
