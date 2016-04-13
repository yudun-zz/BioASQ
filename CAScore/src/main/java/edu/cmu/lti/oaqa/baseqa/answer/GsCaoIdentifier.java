package edu.cmu.lti.oaqa.baseqa.answer;

import static java.util.stream.Collectors.toSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.baseqa.util.ViewType;
import edu.cmu.lti.oaqa.gerp.util.Pair;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerOccurrence;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class GsCaoIdentifier extends JCasAnnotator_ImplBase {

  private String viewNamePrefix;

  private boolean expand;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    viewNamePrefix = UimaContextHelper.getConfigParameterStringValue(context, "view-name-prefix");
    expand = UimaContextHelper.getConfigParameterBooleanValue(context, "expand", true);
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    // get GS answer names
    Set<String> gsNames = TypeUtil.getRankedAnswers(ViewType.getGsView(jcas)).stream()
            .map(TypeUtil::getCandidateAnswerVariantNames).flatMap(List::stream)
            .map(String::toLowerCase).filter(str -> str.length() > 0).collect(toSet());
    if (expand) {
      // expand the GS answer names by including their synonyms
      Set<String> expandedGsNames = TypeUtil.getConcepts(jcas).stream()
              .map(TypeUtil::getConceptNames).map(ImmutableSet::copyOf)
              .filter(cnames -> !Sets.intersection(cnames, gsNames).isEmpty()).flatMap(Set::stream)
              .map(String::toLowerCase).filter(str -> str.length() > 0).collect(toSet());
      gsNames.addAll(expandedGsNames);
    }
    for (JCas view : ViewType.listViews(jcas, viewNamePrefix)) {
      String text = view.getDocumentText();
      // filter spans that do not align with exact tokens (e.g. men -> women)
      Set<CandidateAnswerOccurrence> gsCaos = gsNames.stream().map(String::toLowerCase)
              .map(gsName -> getAllSpans(text.toLowerCase(), gsName)).flatMap(List::stream)
              .filter(span -> isAlignedWithTokens(view, span)).map(span -> TypeFactory
                      .createCandidateAnswerOccurrence(view, span.getKey(), span.getValue()))
              .collect(toSet());
      gsCaos.forEach(CandidateAnswerOccurrence::addToIndexes);
      System.out.println(" - " + gsCaos.size() + " CAOs annotated to " + view.getViewName());
    }
  }

  private static List<Pair<Integer, Integer>> getAllSpans(String text, String target) {
    List<Pair<Integer, Integer>> indexes = new ArrayList<>();
    int index = text.indexOf(target);
    while (index >= 0) {
      indexes.add(Pair.of(index, index + target.length()));
      index = text.indexOf(target, index + target.length());
    }
    return indexes;
  }

  private static boolean isAlignedWithTokens(JCas jcas, Pair<Integer, Integer> span) {
    int begin = span.getKey();
    int end = span.getValue();
    List<Token> tokens = JCasUtil.selectCovered(jcas, Token.class, begin, end);
    return tokens.stream().mapToInt(Token::getBegin).min().orElse(0) == begin
            && tokens.stream().mapToInt(Token::getEnd).max().orElse(0) == end;
  }

}
