package edu.cmu.lti.oaqa.baseqa.answer.generators;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import edu.cmu.lti.oaqa.baseqa.answer.CavUtil;
import edu.cmu.lti.oaqa.baseqa.util.ViewType;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;
import edu.cmu.lti.oaqa.type.nlp.LexicalAnswerType;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class QuantityCavGenerator extends ConfigurableProvider implements CavGenerator {

  private static final String QUANTITY_TYPE = "_QUANTITY";

  private static final String CD_POS_TAG = "CD";

  private String viewNamePrefix;

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    viewNamePrefix = (String) getParameterValue("view-name-prefix");
    return ret;
  }

  @Override
  public boolean accept(JCas jcas) throws AnalysisEngineProcessException {
    return (TypeUtil.getQuestion(jcas).getQuestionType().equals("FACTOID")
            || TypeUtil.getQuestion(jcas).getQuestionType().equals("LIST"))
            && TypeUtil.getLexicalAnswerTypes(jcas).stream().limit(1)
                    .map(LexicalAnswerType::getLabel).allMatch(QUANTITY_TYPE::equals);
  }

  @Override
  public List<CandidateAnswerVariant> generate(JCas jcas) throws AnalysisEngineProcessException {
    return ViewType.listViews(jcas, viewNamePrefix).stream()
            .flatMap(view -> TypeUtil.getOrderedTokens(view).stream()
                    .filter(token -> CD_POS_TAG.equals(token.getPartOfSpeech()))
                    .map(token -> CavUtil.createCandidateAnswerVariant(jcas, token)))
            .collect(toList());
  }

}
