package edu.cmu.lti.oaqa.baseqa.preprocess;

import java.util.ArrayList;
import java.util.List;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import edu.cmu.lti.oaqa.baseqa.providers.kb.ConceptProvider;
import edu.cmu.lti.oaqa.baseqa.providers.kb.SynonymExpansionProvider;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.input.Question;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class QuestionConceptCache extends JCasAnnotator_ImplBase {

  private ConceptProvider conceptProvider;
  
  private SynonymExpansionProvider synonymExpansionProvider;

  private List<String> texts;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    texts = new ArrayList<>();
    // concept cache
    String conceptProviderName = UimaContextHelper.getConfigParameterStringValue(context,
            "concept-provider");
    conceptProvider = ProviderCache.getProvider(conceptProviderName, ConceptProvider.class);
    // synonym cache
    String synonymExpansionProviderName = UimaContextHelper.getConfigParameterStringValue(context,
            "synonym-expansion-provider");
    synonymExpansionProvider = ProviderCache.getProvider(synonymExpansionProviderName,
            SynonymExpansionProvider.class);
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    Question question = TypeUtil.getQuestion(jcas);
    texts.add(question.getText());
  }

  @Override
  public void collectionProcessComplete() throws AnalysisEngineProcessException {
    super.collectionProcessComplete();
    ConceptCacheUtil.cacheTexts(texts, conceptProvider, synonymExpansionProvider);
    conceptProvider.destroy();
    synonymExpansionProvider.destroy();
  }

}
