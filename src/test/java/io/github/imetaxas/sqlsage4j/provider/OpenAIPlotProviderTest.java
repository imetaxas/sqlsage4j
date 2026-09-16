package io.github.imetaxas.sqlsage4j.provider;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.client.MockLLMClient;
import io.github.imetaxas.sqlsage4j.db.DataFrame;
import java.util.List;
import org.junit.jupiter.api.Test;

final class OpenAIPlotProviderTest {

  @Test
  void generatePlotCode_extractsPythonFromMarkdownBlock() {
    MockLLMClient llm =
        new MockLLMClient()
            .withResponse(
                """
                Here is the chart code:
                ```python
                import plotly.express as px
                fig = px.bar(df, x='category', y='revenue')
                fig.show()
                ```
                """);

    OpenAIPlotProvider provider = new OpenAIPlotProvider(llm);
    DataFrame df =
        new DataFrame(
            List.of("category", "revenue"),
            List.of(List.of("Electronics", 5000), List.of("Furniture", 2000)));

    String code = provider.generatePlotCode("Revenue by category", "SELECT ...", df);

    assertThat(code).contains("import plotly.express as px");
    assertThat(code).contains("fig.show()");
    assertThat(code).doesNotContain("```");
  }

  @Test
  void generatePlotCode_extractsFromGenericCodeBlock() {
    MockLLMClient llm =
        new MockLLMClient()
            .withResponse(
                """
                ```
                fig = px.line(df, x='date', y='count')
                ```
                """);

    OpenAIPlotProvider provider = new OpenAIPlotProvider(llm);
    DataFrame df = new DataFrame(List.of("date", "count"), List.of(List.of("2024-01", 100)));

    String code = provider.generatePlotCode("Trend over time", "SELECT ...", df);

    assertThat(code).contains("fig = px.line");
    assertThat(code).doesNotContain("```");
  }

  @Test
  void generatePlotCode_returnsRawWhenNoCodeBlock() {
    MockLLMClient llm = new MockLLMClient().withResponse("fig = px.scatter(df, x='x', y='y')");

    OpenAIPlotProvider provider = new OpenAIPlotProvider(llm);
    DataFrame df = new DataFrame(List.of("x", "y"), List.of(List.of(1, 2)));

    String code = provider.generatePlotCode("Scatter plot", "SELECT ...", df);

    assertThat(code).isEqualTo("fig = px.scatter(df, x='x', y='y')");
  }

  @Test
  void generatePlotCode_passesContextToLLM() {
    MockLLMClient llm = new MockLLMClient().withResponse("code");

    OpenAIPlotProvider provider = new OpenAIPlotProvider(llm);
    DataFrame df =
        new DataFrame(
            List.of("name", "value"),
            List.of(List.of("A", 10), List.of("B", 20), List.of("C", 30)));

    provider.generatePlotCode("Show top items", "SELECT name, value FROM items", df);

    String systemPrompt = llm.lastPrompt().get(0).content();
    assertThat(systemPrompt).contains("name");
    assertThat(systemPrompt).contains("value");
  }

  @Test
  void generatePlotCode_handlesEmptyDataFrame() {
    MockLLMClient llm = new MockLLMClient().withResponse("```python\nprint('no data')\n```");

    OpenAIPlotProvider provider = new OpenAIPlotProvider(llm);
    DataFrame df = DataFrame.empty();

    String code = provider.generatePlotCode("Empty result", "SELECT ...", df);
    assertThat(code).isEqualTo("print('no data')");
  }
}
