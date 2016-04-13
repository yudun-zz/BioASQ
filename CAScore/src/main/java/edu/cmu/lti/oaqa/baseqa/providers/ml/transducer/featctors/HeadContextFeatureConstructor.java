package edu.cmu.lti.oaqa.baseqa.providers.ml.transducer.featctors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableMap;

import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.nlp.Token;

public class HeadContextFeatureConstructor extends ConfigurableProvider
        implements TransducerContextFeatureConstructorProvider {

  @Override
  public List<Map<String, Double>> constructFeaturesSequence(List<Token> tokens,
          List<Map<String, Double>> featuresSequence) {
    List<Map<String, Double>> contextFeaturesSequence = new ArrayList<>();
    for (Token token : tokens) {
      Token head = token.getHead();
      if (head == null) {
        contextFeaturesSequence.add(ImmutableMap.of());
        continue;
      }
      int headIdx = tokens.indexOf(head);
      if (headIdx < 0) {
        contextFeaturesSequence.add(ImmutableMap.of());
        continue;
      }
      Map<String, Double> headFeatures = featuresSequence.get(headIdx);
      String prefix = "[head]";
      Map<String, Double> features = headFeatures.entrySet().stream()
              .collect(Collectors.toMap(entry -> prefix + entry.getKey(), Map.Entry::getValue));
      contextFeaturesSequence.add(features);
    }
    return contextFeaturesSequence;
  }

}
