package edu.cmu.lti.oaqa.baseqa.providers.ml.transducer.featctors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.nlp.Token;

public class PrecedingFollowingContextFeatureConstructor extends ConfigurableProvider
        implements TransducerContextFeatureConstructorProvider {

  private int precedingSize;

  private int followingSize;

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    precedingSize = Integer.class.cast(getParameterValue("preceding-size"));
    followingSize = Integer.class.cast(getParameterValue("following-size"));
    return ret;
  }

  @Override
  public List<Map<String, Double>> constructFeaturesSequence(List<Token> tokens,
          List<Map<String, Double>> featuresSequence) {
    int sequenceLength = featuresSequence.size();
    List<Map<String, Double>> contextFeaturesSequence = new ArrayList<>();
    for (int i = 0; i < sequenceLength; i++) {
      Map<String, Double> features = new HashMap<>();
      for (int j = 1; j <= Math.min(i, precedingSize); j++) {
        String prefix = "[preceding-" + j + "]";
        featuresSequence.get(i - j).entrySet().stream()
                .forEach(entry -> features.put(prefix + entry.getKey(), entry.getValue()));
      }
      for (int j = 1; j <= Math.min(sequenceLength - i - 1, followingSize); j++) {
        String prefix = "[following-" + j + "]";
        featuresSequence.get(i + j).entrySet().stream()
                .forEach(entry -> features.put(prefix + entry.getKey(), entry.getValue()));
      }
      contextFeaturesSequence.add(features);
    }
    return contextFeaturesSequence;
  }

}
