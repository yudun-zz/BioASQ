package edu.cmu.lti.oaqa.baseqa.answer.scorers;

import java.util.Map;

import org.apache.uima.jcas.JCas;

import com.google.common.collect.ImmutableMap;

import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class NameCountCavScorer extends ConfigurableProvider implements CavScorer {

  @Override
  public Map<String, Double> score(JCas jcas, CandidateAnswerVariant cav) {
    int value = TypeUtil.getCandidateAnswerVariantNames(cav).size();
    return ImmutableMap.of("name-count", (double) value);
  }

}
