package edu.cmu.lti.oaqa.baseqa.answer.generators;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import edu.cmu.lti.oaqa.baseqa.answer.CavUtil;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.type.nlp.LexicalAnswerType;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class ConceptCavGenerator extends ConfigurableProvider implements CavGenerator {

  private Set<String> stoplist;

  private boolean checkStoplist;

  private boolean filterQuestionTokens;

  private boolean filterQuestionConcepts;

  private static final String CHOICE_TYPE = "_CHOICE";

  private static final String QUANTITY_TYPE = "_QUANTITY";

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    String stoplistFile = (String) getParameterValue("stoplist");
    try {
      stoplist = Resources.readLines(getClass().getResource(stoplistFile), Charsets.UTF_8).stream()
              .collect(toSet());
      checkStoplist = true;
    } catch (Exception e) {
      checkStoplist = false;
    }
    filterQuestionTokens = (Boolean) getParameterValue("filter-question-tokens");
    filterQuestionConcepts = (Boolean) getParameterValue("filter-question-concepts");
    return ret;
  }

  @Override
  public boolean accept(JCas jcas) throws AnalysisEngineProcessException {
    // should check if the domain of the answer type label can be accepted by the concept-provider
    // if concept-provider supports accept() check.
    return (TypeUtil.getQuestion(jcas).getQuestionType().equals("FACTOID")
            || TypeUtil.getQuestion(jcas).getQuestionType().equals("LIST"))
            && TypeUtil.getLexicalAnswerTypes(jcas).stream().limit(1)
                    .map(LexicalAnswerType::getLabel)
                    .noneMatch(label -> label.equals(CHOICE_TYPE) || label.equals(QUANTITY_TYPE));
  }

  @Override
  public List<CandidateAnswerVariant> generate(JCas jcas) throws AnalysisEngineProcessException {
    // create answer variants groups from each concept
    List<CandidateAnswerVariant> cavs = TypeUtil.getConcepts(jcas).stream()
            .map(concept -> CavUtil.createCandidateAnswerVariant(jcas, concept)).collect(toList());
    // filter out stopwords and concepts in the question
    Set<String> filteredStrings = new HashSet<>();
    if (checkStoplist) {
      filteredStrings.addAll(stoplist);
    }
    if (filterQuestionTokens) {
      TypeUtil.getOrderedTokens(jcas).stream()
              .flatMap(token -> Stream.of(token.getCoveredText(), token.getLemmaForm()))
              .map(String::toLowerCase).forEach(filteredStrings::add);
    }
    if (filterQuestionConcepts) {
      TypeUtil.getConceptMentions(jcas).stream().map(ConceptMention::getCoveredText)
              .map(String::toLowerCase).map(String::trim).forEach(filteredStrings::add);
    }
    return CavUtil.cleanup(jcas, cavs, filteredStrings);
  }

}
