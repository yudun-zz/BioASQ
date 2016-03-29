package edu.cmu.lti.oaqa.baseqa.answer.scorers;

import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class StopwordCountCavScorer extends ConfigurableProvider implements CavScorer {

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
  public Map<String, Double> score(JCas jcas, CandidateAnswerVariant cav) {
    double[] stopwordRatios = TypeUtil.getCandidateAnswerOccurrences(cav).stream()
            .map(cao -> JCasUtil.selectCovered(Token.class, cao))
            .mapToDouble(tokens -> CavScorer.safeDividedBy(tokens.stream().filter(token -> {
              return stoplist.contains(token.getCoveredText().toLowerCase())
                      || stoplist.contains(token.getLemmaForm());
            } ).count(), tokens.size())).toArray();
    return CavScorer.generateSummaryFeatures(stopwordRatios, "stopword", "avg", "min", "one-ratio",
            "any-one");
  }

}
