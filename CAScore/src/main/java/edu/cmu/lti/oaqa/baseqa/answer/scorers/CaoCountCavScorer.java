package edu.cmu.lti.oaqa.baseqa.answer.scorers;

import java.util.Map;

import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import com.google.common.collect.ImmutableMap;

import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class CaoCountCavScorer extends ConfigurableProvider implements CavScorer {

  @Override
  public Map<String, Double> score(JCas jcas, CandidateAnswerVariant cav) {
    double count = TypeUtil.getCandidateAnswerOccurrences(cav).size();
    double tokenCount = TypeUtil.getCandidateAnswerOccurrences(cav).stream()
            .mapToInt(cao -> JCasUtil.selectCovered(Token.class, cao).size()).sum();
    return ImmutableMap.of("cao-count", count, "token-count", tokenCount);
  }

}
