package io.github.imetaxas.sqlsage4j.provider;

import io.github.imetaxas.sqlsage4j.db.DataFrame;

public interface DiagramsPlotProvider {

  String generatePlotCode(String question, String sql, DataFrame df);
}
