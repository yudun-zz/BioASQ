package edu.cmu.lti.oaqa.baseqa.providers.kb;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.resource.Resource;

public interface SynonymExpansionProvider extends Resource {

  Set<String> getSynonyms(String id) throws AnalysisEngineProcessException;

  Map<String, Set<String>> getSynonyms(Collection<String> ids)
          throws AnalysisEngineProcessException;

}
