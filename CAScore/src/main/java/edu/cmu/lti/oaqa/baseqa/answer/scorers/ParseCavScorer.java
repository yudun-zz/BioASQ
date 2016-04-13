package edu.cmu.lti.oaqa.baseqa.answer.scorers;

import java.util.Map;

import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import com.google.common.collect.ImmutableMap;

import edu.cmu.lti.oaqa.baseqa.answer.CavUtil;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class ParseCavScorer extends ConfigurableProvider implements CavScorer {

  @Override
  public Map<String, Double> score(JCas jcas, CandidateAnswerVariant cav) {
    ImmutableMap.Builder<String, Double> builder = ImmutableMap.builder();
    double[] depths = TypeUtil.getCandidateAnswerOccurrences(cav).stream()
            .map(TypeUtil::getHeadTokenOfAnnotation).mapToDouble(CavUtil::getDepth).toArray();
    builder.putAll(CavScorer.generateSummaryFeatures(depths, "depth", "avg", "max", "min"));
    double[] consitutentForests = TypeUtil.getCandidateAnswerOccurrences(cav).stream()
            .map(cao -> CavUtil.isConstituentForest(CavUtil.getJCas(cao),
                    JCasUtil.selectCovered(Token.class, cao)))
            .mapToDouble(value -> value ? 1.0 : 0.0).toArray();
    builder.putAll(CavScorer.generateSummaryFeatures(consitutentForests, "consitutent-forest",
            "avg", "max", "min", "one-ratio", "any-one"));
    double[] consitutents = TypeUtil.getCandidateAnswerOccurrences(cav).stream()
            .map(cao -> CavUtil.isConstituent(CavUtil.getJCas(cao),
                    JCasUtil.selectCovered(Token.class, cao)))
            .mapToDouble(value -> value ? 1.0 : 0.0).toArray();
    builder.putAll(CavScorer.generateSummaryFeatures(consitutents, "consitutent", "avg", "max",
            "min", "one-ratio", "any-one"));
    return builder.build();
  }

}
