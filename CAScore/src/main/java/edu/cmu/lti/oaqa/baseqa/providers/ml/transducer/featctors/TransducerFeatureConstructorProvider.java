package edu.cmu.lti.oaqa.baseqa.providers.ml.transducer.featctors;

import java.util.List;
import java.util.Map;

import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.Resource;

import edu.cmu.lti.oaqa.type.nlp.Token;

public interface TransducerFeatureConstructorProvider extends Resource {

  List<Map<String, Double>> constructFeaturesSequence(List<Token> tokens, JCas jcas, JCas refJcas);

}
