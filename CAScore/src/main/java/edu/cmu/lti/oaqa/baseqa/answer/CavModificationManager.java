package edu.cmu.lti.oaqa.baseqa.answer;

import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.List;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import edu.cmu.lti.oaqa.baseqa.answer.modifiers.CavModifier;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class CavModificationManager extends JCasAnnotator_ImplBase {

  private List<CavModifier> modifiers;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    String handlerNames = UimaContextHelper.getConfigParameterStringValue(context, "handlers");
    modifiers = ProviderCache.getProviders(handlerNames, CavModifier.class);
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    for (CavModifier modifier : modifiers) {
      if (modifier.accept(jcas)) {
        modifier.modify(jcas);
        List<Collection<String>> names = TypeUtil.getCandidateAnswerVariants(jcas).stream()
                .map(TypeUtil::getCandidateAnswerVariantNames).collect(toList());
        System.out.println("Answer candidates modified: " + names + " from "
                + modifier.getClass().getSimpleName());
      }
    }
  }

}
