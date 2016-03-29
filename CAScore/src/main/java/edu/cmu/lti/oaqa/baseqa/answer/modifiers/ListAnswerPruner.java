package edu.cmu.lti.oaqa.baseqa.answer.modifiers;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.answer.Answer;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class ListAnswerPruner extends ConfigurableProvider implements AnswerModifier {

  private double threshold = Double.NEGATIVE_INFINITY;

  private double ratio = Double.NEGATIVE_INFINITY;

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    if (null != getParameterValue("threshold")) {
      threshold = Double.class.cast(getParameterValue("threshold"));
    }
    if (null != getParameterValue("ratio")) {
      ratio = Double.class.cast(getParameterValue("ratio"));
    }
    return ret;
  }

  @Override
  public boolean accept(JCas jcas) throws AnalysisEngineProcessException {
    return TypeUtil.getQuestion(jcas).getQuestionType().equals("LIST");
  }

  @Override
  public void modify(JCas jcas) throws AnalysisEngineProcessException {
    if (threshold != Double.NEGATIVE_INFINITY) {
      List<Answer> removedAnswers = TypeUtil.getRankedAnswers(jcas).stream()
              .filter(answer -> answer.getScore() < threshold).collect(toList());
      removedAnswers.forEach(Answer::removeFromIndexes);
    }
    if (ratio != Double.NEGATIVE_INFINITY) {
      List<Answer> answers = TypeUtil.getRankedAnswers(jcas);
      double cutoff = answers.get(0).getScore() * ratio;
      List<Answer> removedAnswers = answers.stream().filter(answer -> answer.getScore() < cutoff)
              .collect(toList());
      removedAnswers.forEach(Answer::removeFromIndexes);
    }
  }

}
