package io.github.imetaxas.benchmark;

/** Wraps Vanna v2 (Python) via a long-lived subprocess using {@code scripts/vanna_harness.py}. */
public final class VannaHarness extends PythonHarness {

  @Override
  public String name() {
    return "vanna";
  }

  @Override
  protected String scriptPath() {
    return "scripts/vanna_harness.py";
  }
}
