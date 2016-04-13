package edu.cmu.lti.oaqa.baseqa.retrieval;

import java.util.List;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import edu.cmu.lti.oaqa.baseqa.providers.nlp.NlpProvider;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.baseqa.util.ViewType;
import edu.cmu.lti.oaqa.type.nlp.Token;

public class PassageParser extends JCasAnnotator_ImplBase {

  private NlpProvider nlpProvider;

  private String viewNamePrefix;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    String nlpProviderName = UimaContextHelper.getConfigParameterStringValue(context,
            "nlp-provider");
    nlpProvider = ProviderCache.getProvider(nlpProviderName, NlpProvider.class);
    viewNamePrefix = UimaContextHelper.getConfigParameterStringValue(context, "view-name-prefix");
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    ViewType.listViews(jcas, viewNamePrefix).stream().map(nlpProvider::parseDependency)
            .flatMap(List::stream).forEach(Token::addToIndexes);
  }

}
