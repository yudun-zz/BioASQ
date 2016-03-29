package edu.cmu.lti.oaqa.baseqa.answer.generators;

import java.util.Arrays;
import java.util.List;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;

import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class YesNoCavGenerator extends ConfigurableProvider
        implements CavGenerator {

  @Override
  public boolean accept(JCas jcas) throws AnalysisEngineProcessException {
    return TypeUtil.getQuestion(jcas).getQuestionType().equals("YES_NO");
  }

  @Override
  public List<CandidateAnswerVariant> generate(JCas jcas) throws AnalysisEngineProcessException {
    CandidateAnswerVariant yes = TypeFactory.createCandidateAnswerVariant(jcas, "yes");
    CandidateAnswerVariant no = TypeFactory.createCandidateAnswerVariant(jcas, "no");
    return Arrays.asList(yes, no);
  }

}
