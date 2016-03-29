package edu.cmu.lti.oaqa.baseqa.providers.ml.transducer;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.resource.Resource;

public interface TransducerProvider extends Resource {

  List<String> predict(List<Map<String, Double>> featuresSequence)
          throws AnalysisEngineProcessException;

  double infer(List<Map<String, Double>> featuresSequence, List<String> labelSequence)
          throws AnalysisEngineProcessException;

  default List<List<String>> predict(List<Map<String, Double>> featuresSequence, int k)
          throws AnalysisEngineProcessException {
    return Arrays.asList(predict(featuresSequence));
  }

  void train(List<List<Map<String, Double>>> X, List<List<String>> Y, String zerothLabel)
          throws AnalysisEngineProcessException;

  default void train(List<List<Map<String, Double>>> X, List<List<String>> Y)
          throws AnalysisEngineProcessException {
    train(X, Y, "<START>");
  }

}
