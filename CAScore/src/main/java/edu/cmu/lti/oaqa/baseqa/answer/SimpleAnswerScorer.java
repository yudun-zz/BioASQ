package edu.cmu.lti.oaqa.baseqa.answer;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.answer.Answer;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerOccurrence;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.type.kb.ConceptType;
import edu.cmu.lti.oaqa.type.nlp.LexicalAnswerType;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class SimpleAnswerScorer extends JCasAnnotator_ImplBase {

  private float typeCoerSmoothing;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    typeCoerSmoothing = UimaContextHelper.getConfigParameterFloatValue(context,
            "type-coer-smoothing", 0.1f);
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    Set<String> lats = TypeUtil.getLexicalAnswerTypes(jcas).stream()
            .map(LexicalAnswerType::getLabel).collect(toSet());
    List<Answer> answers = TypeUtil.getCandidateAnswerVariants(jcas).stream().map(cav -> {
      Collection<CandidateAnswerOccurrence> caos = TypeUtil.getCandidateAnswerOccurrences(cav);
      long latCoercionCount = caos.stream()
              .filter(cao -> JCasUtil.selectCovered(ConceptMention.class, cao).stream()
                      .map(ConceptMention::getConcept).map(TypeUtil::getConceptTypes)
                      .flatMap(Collection::stream).map(ConceptType::getAbbreviation)
                      .anyMatch(lats::contains))
              .count();
      double score = (latCoercionCount / (double) caos.size() + typeCoerSmoothing) * caos.size();
      System.out.println(TypeUtil.getCandidateAnswerVariantNames(cav) + " " + score);
      return TypeFactory.createAnswer(jcas, score, Arrays.asList(cav));
    } ).sorted(TypeUtil.ANSWER_SCORE_COMPARATOR).collect(toList());
    answers.forEach(Answer::addToIndexes);
    System.out.println("Ranked top 5 answers " + answers.stream().limit(5)
            .map(TypeUtil::getCandidateAnswerVariantNames).collect(toList()));
  }

}
