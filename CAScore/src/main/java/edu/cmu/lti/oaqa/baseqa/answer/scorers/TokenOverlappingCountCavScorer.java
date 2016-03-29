package edu.cmu.lti.oaqa.baseqa.answer.scorers;

import static java.util.stream.Collectors.toSet;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class TokenOverlappingCountCavScorer extends ConfigurableProvider implements CavScorer {

  @Override
  public Map<String, Double> score(JCas jcas, CandidateAnswerVariant cav) {
    Set<String> qtokens = TypeUtil.getOrderedTokens(jcas).stream()
            .flatMap(token -> Stream.of(token.getCoveredText(), token.getLemmaForm()))
            .map(String::toLowerCase).collect(toSet());
    double[] qtokenOverlappingRatios = TypeUtil.getCandidateAnswerOccurrences(cav).stream()
            .map(cao -> JCasUtil.selectCovered(Token.class, cao))
            .mapToDouble(
                    tokens -> CavScorer.safeDividedBy(tokens.stream().map(Token::getCoveredText)
                            .map(String::toLowerCase).filter(qtokens::contains).count(),
                            tokens.size()))
            .toArray();
    return CavScorer.generateSummaryFeatures(qtokenOverlappingRatios, "token-overlap", "avg",
            "pos-ratio", "any-one");
  }

}
