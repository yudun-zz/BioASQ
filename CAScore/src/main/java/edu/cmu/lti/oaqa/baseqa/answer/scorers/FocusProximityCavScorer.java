package edu.cmu.lti.oaqa.baseqa.answer.scorers;

import static java.util.stream.Collectors.toList;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import com.google.common.collect.ImmutableMap;

import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;
import edu.cmu.lti.oaqa.type.nlp.Focus;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class FocusProximityCavScorer extends ConfigurableProvider implements CavScorer {

  private int windowSize;

  private int infinity;

  private double smoothing;

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    windowSize = (int) getParameterValue("window-size");
    infinity = (int) getParameterValue("infinity");
    smoothing = (double) getParameterValue("smoothing");
    return ret;
  }

  @Override
  public Map<String, Double> score(JCas jcas, CandidateAnswerVariant cav) {
    double[] distances = new double[] { infinity };
    Focus focus = TypeUtil.getFocus(jcas);
    if (focus != null) {
      String focusLabel = focus.getLabel();
      distances = TypeUtil.getCandidateAnswerOccurrences(cav).stream().mapToDouble(cao -> {
        List<String> precedingTokens = JCasUtil.selectPreceding(Token.class, cao, windowSize)
                .stream().sorted(Comparator.comparing(Token::getEnd, Comparator.reverseOrder()))
                .map(Token::getLemmaForm).collect(toList());
        List<String> followingTokens = JCasUtil.selectFollowing(Token.class, cao, windowSize)
                .stream().sorted(Comparator.comparing(Token::getBegin)).map(Token::getLemmaForm)
                .collect(toList());
        int precedingDistance = precedingTokens.indexOf(focusLabel);
        if (precedingDistance == -1) {
          precedingDistance = infinity;
        }
        int followingDistance = followingTokens.indexOf(focusLabel);
        if (followingDistance == -1) {
          followingDistance = infinity;
        }
        return Math.min(precedingDistance, followingDistance);
      } ).toArray();
    }
    ImmutableMap.Builder<String, Double> builder = ImmutableMap.builder();
    double[] negdistances = Arrays.stream(distances).map(distance -> distance - infinity).toArray();
    builder.putAll(
            CavScorer.generateSummaryFeatures(negdistances, "focus-negdistances", "avg", "min"));
    double[] proximities = Arrays.stream(distances).map(distance -> 1.0 / (smoothing + distance))
            .toArray();
    builder.putAll(CavScorer.generateSummaryFeatures(proximities, "focus-proximities", "avg", "max",
            "min", "pos-ratio"));
    return builder.build();
  }

}
