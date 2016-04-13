package edu.cmu.lti.oaqa.baseqa.quesanal;

import java.util.List;
import java.util.Set;

import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;

import com.google.common.collect.ImmutableSet;

import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class QuestionFocusExtractor extends JCasAnnotator_ImplBase {

  private static final String ROOT_DEP_LABEL = "root";

  private static final String DEP_DEP_LABEL = "dep";

  private static final Set<String> NOUN_POS_TAGS = ImmutableSet.of("NN", "NNP", "NNS", "NNPS");

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    List<Token> tokens = TypeUtil.getOrderedTokens(jcas);
    Token root = tokens.stream()
            .filter(token -> token.getHead() == null || ROOT_DEP_LABEL.equals(token.getDepLabel()))
            .findFirst().orElseThrow(AnalysisEngineProcessException::new);
    tokens.stream().filter(token -> token.getHead() != null)
            .filter(token -> token.getHead().equals(root))
            .filter(token -> NOUN_POS_TAGS.contains(token.getPartOfSpeech()))
            .filter(token -> !DEP_DEP_LABEL.equals(token.getDepLabel())).findFirst()
            .ifPresent(token -> {
              System.out.println("Found focus: " + token.getLemmaForm());
              TypeFactory.createFocus(jcas, token, token.getLemmaForm()).addToIndexes();
            } );
  }

}
