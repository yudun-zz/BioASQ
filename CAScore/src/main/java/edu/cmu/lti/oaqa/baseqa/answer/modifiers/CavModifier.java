package edu.cmu.lti.oaqa.baseqa.answer.modifiers;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.Resource;

public interface CavModifier extends Resource {

  boolean accept(JCas jcas) throws AnalysisEngineProcessException;

  void modify(JCas jcas) throws AnalysisEngineProcessException;

}
