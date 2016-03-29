package edu.cmu.lti.oaqa.baseqa.providers.kb;

import java.util.List;
import java.util.Optional;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.Resource;

import edu.cmu.lti.oaqa.type.kb.Concept;

public interface ConceptSearchProvider extends Resource {

  Optional<Concept> search(JCas jcas, String string) throws AnalysisEngineProcessException;

  default Optional<Concept> search(JCas jcas, String string, String searchType)
          throws AnalysisEngineProcessException {
    return search(jcas, string, searchType, 1).stream().findFirst();
  }

  List<Concept> search(JCas jcas, String string, String searchType, int hits)
          throws AnalysisEngineProcessException;

}
