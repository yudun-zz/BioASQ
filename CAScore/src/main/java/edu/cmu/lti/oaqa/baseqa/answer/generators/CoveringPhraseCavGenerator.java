package edu.cmu.lti.oaqa.baseqa.answer.generators;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;

import com.google.common.collect.SetMultimap;

import edu.cmu.lti.oaqa.baseqa.answer.CavUtil;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class CoveringPhraseCavGenerator extends ConfigurableProvider implements CavGenerator {

  // private static final String PUNCT_DEP_LABEL = "punct";

  @Override
  public boolean accept(JCas jcas) throws AnalysisEngineProcessException {
    return TypeUtil.getQuestion(jcas).getQuestionType().equals("FACTOID")
            || TypeUtil.getQuestion(jcas).getQuestionType().equals("LIST");
  }

  @Override
  public List<CandidateAnswerVariant> generate(JCas jcas) throws AnalysisEngineProcessException {
    Set<Token> heads = TypeUtil.getCandidateAnswerVariants(jcas).stream()
            .map(TypeUtil::getCandidateAnswerOccurrences).flatMap(Collection::stream)
            .map(TypeUtil::getHeadTokenOfAnnotation).collect(toSet());
    Set<Token> parents = heads.stream().map(Token::getHead).filter(t -> t != null)
            .filter(t -> !heads.contains(t)).collect(toSet());
    Map<JCas, List<Token>> view2parents = parents.stream().collect(groupingBy(CavUtil::getJCas));
    return view2parents.entrySet().stream().flatMap(entry -> {
      JCas view = entry.getKey();
      List<Token> tokens = TypeUtil.getOrderedTokens(view);
      SetMultimap<Token, Token> head2children = CavUtil.getHeadTokenMap(tokens);
      return entry.getValue().stream()
              .map(parent -> CavUtil.createCandidateAnswerOccurrenceFromDepBranch(view, parent,
                      head2children, null))
              .map(cao -> TypeFactory.createCandidateAnswerVariant(jcas, Arrays.asList(cao)));
    } ).collect(toList());
  }

}
