package io.github.imetaxas.sqlsage4j.spring;

import io.github.imetaxas.sqlsage4j.client.LLMClient;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Auto-configures the sqlsage4j health indicator when Spring Boot Actuator is on the classpath. */
@AutoConfiguration(after = SqlSage4jAutoConfiguration.class)
@ConditionalOnClass(org.springframework.boot.actuate.health.HealthIndicator.class)
@ConditionalOnBean(LLMClient.class)
@ConditionalOnEnabledHealthIndicator("sqlsage4j")
public class SqlSage4jHealthAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public SqlSage4jHealthIndicator sqlSage4jHealthIndicator(LLMClient llmClient) {
    return new SqlSage4jHealthIndicator(llmClient);
  }
}
