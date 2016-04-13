package edu.cmu.lti.oaqa.baseqa.answer.scorers;

import java.util.Map;

import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;
import edu.cmu.lti.oaqa.type.nlp.Focus;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class FocusOverlappingCountCavScorer extends ConfigurableProvider implements CavScorer {

  @Override
  public Map<String, Double> score(JCas jcas, CandidateAnswerVariant cav) {
    double[] focusOverlappingRatios = new double[] { 0.0 };
    Focus focus = TypeUtil.getFocus(jcas);
    if (focus != null) {
      String focusLabel = focus.getLabel();
      focusOverlappingRatios = TypeUtil.getCandidateAnswerOccurrences(cav).stream()
              .map(cao -> JCasUtil.selectCovered(Token.class, cao))
              .mapToDouble(tokens -> CavScorer.safeDividedBy(
                      tokens.stream().map(Token::getLemmaForm).filter(focusLabel::equals).count(),
                      tokens.size()))
              .toArray();
    }
    return CavScorer.generateSummaryFeatures(focusOverlappingRatios, "focus-overlap", "avg",
            "pos-ratio");
  }

}
