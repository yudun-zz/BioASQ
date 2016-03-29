package edu.cmu.lti.oaqa.baseqa.providers.ml.transducer.featctors;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;

import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class TokenOverlappingFeatureConstrutor extends ConfigurableProvider
        implements TransducerFeatureConstructorProvider {

  private Set<String> stoplist;

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    String stoplistFile = (String) getParameterValue("stoplist");
    try {
      stoplist = Resources.readLines(getClass().getResource(stoplistFile), Charsets.UTF_8).stream()
              .collect(toSet());
    } catch (IOException e) {
      throw new ResourceInitializationException(e);
    }
    return ret;
  }

  @Override
  public List<Map<String, Double>> constructFeaturesSequence(List<Token> tokens, JCas jcas,
          JCas refJcas) {
    Set<String> qtokens = TypeUtil.getOrderedTokens(refJcas).stream()
            .filter(token -> !stoplist.contains(token.getLemmaForm())
                    && !stoplist.contains(token.getCoveredText().toLowerCase()))
            .map(Token::getLemmaForm).collect(toSet());
    return tokens.stream()
            .map(Token::getLemmaForm).map(token -> qtokens.contains(token)
                    ? ImmutableMap.of("[token-overlap]", 1.0) : ImmutableMap.<String, Double> of())
            .collect(toList());
  }

}
