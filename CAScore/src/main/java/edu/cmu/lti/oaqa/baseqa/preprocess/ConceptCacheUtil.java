package edu.cmu.lti.oaqa.baseqa.preprocess;

import static java.util.stream.Collectors.toSet;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;

import edu.cmu.lti.oaqa.baseqa.providers.kb.ConceptProvider;
import edu.cmu.lti.oaqa.baseqa.providers.kb.SynonymExpansionProvider;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class ConceptCacheUtil {

  public static void cacheTexts(List<String> texts, ConceptProvider conceptProvider,
          SynonymExpansionProvider synonymExpansionProvider) throws AnalysisEngineProcessException {
    List<List<Concept>> concepts = conceptProvider.getConcepts(texts, "__");
    Set<String> ids = concepts.stream().flatMap(List::stream).map(TypeUtil::getConceptIds)
            .flatMap(Collection::stream).collect(toSet());
    synonymExpansionProvider.getSynonyms(ids);
  }

}
