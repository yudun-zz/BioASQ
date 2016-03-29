package edu.cmu.lti.oaqa.baseqa.providers.ml;

import java.util.List;
import java.util.Map;

import org.apache.uima.resource.Resource;

import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.type.nlp.Focus;
import edu.cmu.lti.oaqa.type.nlp.Token;

public interface FeatureConstructorProvider extends Resource {

  Map<String, Double> constructFeatures(List<Token> tokens, List<ConceptMention> cmentions,
          Focus focus);

}
