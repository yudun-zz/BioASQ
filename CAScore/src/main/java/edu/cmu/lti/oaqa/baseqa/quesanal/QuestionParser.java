package edu.cmu.lti.oaqa.baseqa.quesanal;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import edu.cmu.lti.oaqa.baseqa.providers.nlp.NlpProvider;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.nlp.Token;

public class QuestionParser extends JCasAnnotator_ImplBase {

  private NlpProvider nlpProvider;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    String nlpProviderName = UimaContextHelper.getConfigParameterStringValue(context,
            "nlp-provider");
    nlpProvider = ProviderCache.getProvider(nlpProviderName, NlpProvider.class);
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    nlpProvider.parseDependency(jcas).forEach(Token::addToIndexes);
  }

}
