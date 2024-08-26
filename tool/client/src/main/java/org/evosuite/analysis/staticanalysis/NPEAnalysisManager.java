package org.evosuite.analysis.staticanalysis;

@SuppressWarnings("unused")
public class NPEAnalysisManager {
  private NPEAnalysisManager() {
  }

  public static void runAnalyzers(String methodKey) {
    // NullLiteralAnalyzer.getInstance().analyze(methodKey);
    // NullableFieldAccessAnalyzer.getInstance().analyze(methodKey);
    // ExternalInvocationAnalyzer.getInstance().analyze(methodKey);
    // NullableGetterInvocationAnalyzer.getInstance().analyze(methodKey);
    // NullableParameterAccessAnalyzer.getInstance().analyze(methodKey);
  }
  
  public static int getWeight(String methodKey) {
    int weight = 0;
    weight += NullLiteralAnalyzer.getInstance().getScore(methodKey);
    weight += NullableFieldAccessAnalyzer.getInstance().getScore(methodKey);
    weight += NullableGetterInvocationAnalyzer.getInstance().getScore(methodKey);
    weight += NullableParameterAccessAnalyzer.getInstance().getScore(methodKey);
    return weight;
  }
}
