package edu.cmu.lti.oaqa.baseqa.preprocess;

import java.util.ArrayList;
import java.util.Collection;
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
import edu.cmu.lti.oaqa.type.retrieval.Passage;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class PassageConceptCache extends JCasAnnotator_ImplBase {

  private int batchSize;

  private List<String> texts;

  private ConceptProvider conceptProvider;

  private SynonymExpansionProvider synonymExpansionProvider;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    texts = new ArrayList<>();
    // concept cache
    String conceptProviderName = UimaContextHelper.getConfigParameterStringValue(context,
            "concept-provider");
    batchSize = UimaContextHelper.getConfigParameterIntValue(context, "batch-size", 500);
    conceptProvider = ProviderCache.getProvider(conceptProviderName, ConceptProvider.class);
    // synonym cache
    String synonymExpansionProviderName = UimaContextHelper.getConfigParameterStringValue(context,
            "synonym-expansion-provider");
    synonymExpansionProvider = ProviderCache.getProvider(synonymExpansionProviderName,
            SynonymExpansionProvider.class);
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    System.out.println("QID: " + TypeUtil.getQuestion(jcas).getId());
    Collection<Passage> passages = TypeUtil.getRankedPassages(jcas);
    passages.stream().map(Passage::getText).forEachOrdered(texts::add);
    if (texts.size() > batchSize) {
      ConceptCacheUtil.cacheTexts(texts, conceptProvider, synonymExpansionProvider);
      texts.clear();
    }
  }

  @Override
  public void collectionProcessComplete() throws AnalysisEngineProcessException {
    ConceptCacheUtil.cacheTexts(texts, conceptProvider, synonymExpansionProvider);
    super.collectionProcessComplete();
    conceptProvider.destroy();
    synonymExpansionProvider.destroy();
  }

}
