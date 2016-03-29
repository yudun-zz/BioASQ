package edu.cmu.lti.oaqa.baseqa.retrieval;

import java.util.List;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.baseqa.util.ViewType;
import edu.cmu.lti.oaqa.type.retrieval.Passage;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class PassageToViewCopier extends JCasAnnotator_ImplBase {

  private String viewNamePrefix;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    viewNamePrefix = UimaContextHelper.getConfigParameterStringValue(context, "view-name-prefix");
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    List<Passage> passages = TypeUtil.getRankedPassages(jcas);
    passages.stream().forEach(passage -> {
      try {
        ViewType.createView(jcas, viewNamePrefix, createPassageViewId(passage), passage.getText());
      } catch (Exception e) {
        e.printStackTrace();
      }
    } );
  }

  private static String createPassageViewId(Passage passage) {
    return passage.getUri() + "/" + passage.getBeginSection() + "/"
            + passage.getOffsetInBeginSection() + "/" + passage.getEndSection() + "/"
            + passage.getOffsetInEndSection();
  }

}
