package edu.cmu.lti.oaqa.baseqa.answer.scorers;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;

import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class TokenProximityCavScorer extends ConfigurableProvider implements CavScorer {

  private Set<String> stoplist;

  private int windowSize;

  private int infinity;

  private double smoothing;

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
    windowSize = (int) getParameterValue("window-size");
    infinity = (int) getParameterValue("infinity");
    smoothing = (double) getParameterValue("smoothing");
    return ret;
  }

  @Override
  public Map<String, Double> score(JCas jcas, CandidateAnswerVariant cav) {
    Set<String> qtokens = TypeUtil.getOrderedTokens(jcas).stream()
            .filter(token -> !stoplist.contains(token.getLemmaForm())
                    && !stoplist.contains(token.getCoveredText().toLowerCase()))
            .map(Token::getLemmaForm).collect(toSet());
    double[] distances = TypeUtil.getCandidateAnswerOccurrences(cav).stream().mapToDouble(cao -> {
      List<String> precedingTokens = JCasUtil.selectPreceding(Token.class, cao, windowSize).stream()
              .sorted(Comparator.comparing(Token::getEnd, Comparator.reverseOrder()))
              .map(Token::getLemmaForm).collect(toList());
      List<String> followingTokens = JCasUtil.selectFollowing(Token.class, cao, windowSize).stream()
              .sorted(Comparator.comparing(Token::getBegin)).map(Token::getLemmaForm)
              .collect(toList());
      return qtokens.stream().mapToDouble(qtoken -> {
        int precedingDistance = precedingTokens.indexOf(qtoken);
        if (precedingDistance == -1) {
          precedingDistance = infinity;
        }
        int followingDistance = followingTokens.indexOf(qtoken);
        if (followingDistance == -1) {
          followingDistance = infinity;
        }
        return Math.min(precedingDistance, followingDistance);
      } ).average().orElse(infinity);
    } ).toArray();
    ImmutableMap.Builder<String, Double> builder = ImmutableMap.builder();
    double[] negdistances = Arrays.stream(distances).map(distance -> distance - infinity).toArray();
    builder.putAll(
            CavScorer.generateSummaryFeatures(negdistances, "token-negdistance", "avg", "min"));
    double[] proximities = Arrays.stream(distances).map(distance -> 1.0 / (smoothing + distance))
            .toArray();
    builder.putAll(CavScorer.generateSummaryFeatures(proximities, "token-proximities", "avg", "max",
            "min", "pos-ratio"));
    return builder.build();
  }

}
