package edu.cmu.lti.oaqa.baseqa.answer.generators;

import java.util.List;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.Resource;

import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;

public interface CavGenerator extends Resource {

  boolean accept(JCas jcas) throws AnalysisEngineProcessException;

  List<CandidateAnswerVariant> generate(JCas jcas) throws AnalysisEngineProcessException;

}
