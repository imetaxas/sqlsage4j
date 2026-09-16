package io.github.imetaxas.sqlsage4j;

import com.google.auto.value.AutoValue;
import io.github.imetaxas.sqlsage4j.enums.PromptEnum;
import java.util.List;
import javax.annotation.Nullable;

@AutoValue
public abstract class Prompt {
  Prompt() {}

  public abstract PromptEnum userPrompt();

  @Nullable
  public abstract PromptEnum systemPrompt();

  @Nullable
  public abstract PromptEnum assistantPrompt();

  @Nullable
  public abstract String initialPrompt();

  @Nullable
  public abstract List<QuestionAnswer> sampleQuestionsAnswers();

  @Nullable
  public abstract List<SampleDocument> sampleDocuments();

  @Nullable
  public abstract List<Ontology> ontologies();

  @Nullable
  public abstract List<DDL> ddls();

  @Nullable
  public abstract List<String> queries();

  public static Builder builder() {
    return new AutoValue_Prompt.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    Builder() {}

    public abstract Builder userPrompt(PromptEnum userPrompt);

    public abstract Builder systemPrompt(PromptEnum systemPrompt);

    public abstract Builder assistantPrompt(PromptEnum assistantPrompt);

    public abstract Builder initialPrompt(String initialPrompt);

    public abstract Builder sampleQuestionsAnswers(List<QuestionAnswer> sampleQuestionsAnswers);

    public abstract Builder sampleDocuments(List<SampleDocument> sampleDocuments);

    public abstract Builder ontologies(List<Ontology> ontologies);

    public abstract Builder ddls(List<DDL> ddls);

    public abstract Builder queries(List<String> queries);

    public abstract Prompt build();
  }
}
