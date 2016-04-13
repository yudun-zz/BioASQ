package edu.cmu.lti.oaqa.baseqa;

import java.io.File;
import java.io.IOException;

import org.apache.uima.UIMAException;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.CasIOUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.CasCopier;

import com.google.common.base.Strings;

import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.ecd.phase.ProcessingStepUtils;

public class CasSerializer extends JCasAnnotator_ImplBase {

  private String typesystem;

  private String dir;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    typesystem = UimaContextHelper.getConfigParameterStringValue(context, "typesystem");
    dir = UimaContextHelper.getConfigParameterStringValue(context, "dir");
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    try {
      JCas copied = JCasFactory.createJCas(typesystem);
      CasCopier.copyCas(jcas.getCas(), copied.getCas(), true, true);
      String id = Strings.padStart(ProcessingStepUtils.getSequenceId(jcas), 4, '0');
      CasIOUtil.writeXmi(copied, new File(dir, id + ".xmi"));
    } catch (IOException | UIMAException e) {
      e.printStackTrace();
    }
  }

}
