package edu.cmu.lti.oaqa.baseqa.retrieval;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.FSCollectionFactory;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import edu.cmu.lti.oaqa.baseqa.providers.kb.ConceptSearchProvider;
import edu.cmu.lti.oaqa.baseqa.providers.kb.SynonymExpansionProvider;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class ConceptSearcher extends JCasAnnotator_ImplBase {

  private ConceptSearchProvider conceptSearchProvider;

  private SynonymExpansionProvider synonsymExpanisonProvider;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    String conceptSearchProviderName = UimaContextHelper.getConfigParameterStringValue(context,
            "concept-search-provider");
    conceptSearchProvider = ProviderCache.getProvider(conceptSearchProviderName,
            ConceptSearchProvider.class);
    String synonymExpansionProviderName = UimaContextHelper.getConfigParameterStringValue(context,
            "synonym-expansion-provider");
    synonsymExpanisonProvider = ProviderCache.getProvider(synonymExpansionProviderName,
            SynonymExpansionProvider.class);
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    Collection<Concept> concepts = TypeUtil.getConcepts(jcas);
    Set<Concept> missingIdConcepts = concepts.stream()
            .filter(concept -> TypeUtil.getConceptIds(concept).isEmpty()).collect(toSet());
    // retrieving IDs
    System.out.println("Retrieving IDs for " + missingIdConcepts.size() + " concepts.");
    for (Concept concept : missingIdConcepts) {
      Optional<Concept> response = conceptSearchProvider.search(jcas,
              TypeUtil.getConceptPreferredName(concept));
      if (response.isPresent()) {
        TypeUtil.mergeConcept(jcas, concept, response.get());
      }
    }
    // retrieving synonyms (names)
    System.out.println("Retrieving synonyms for " + concepts.size() + " concepts.");
    Map<String, Concept> id2concept = new HashMap<>();
    concepts.stream().forEach(
            concept -> TypeUtil.getConceptIds(concept).forEach(id -> id2concept.put(id, concept)));
    Map<String, Set<String>> id2synonyms = synonsymExpanisonProvider
            .getSynonyms(id2concept.keySet());
    id2concept.entrySet().forEach(entry -> {
      Concept concept = entry.getValue();
      List<String> names = Stream
              .concat(TypeUtil.getConceptNames(concept).stream(),
                      id2synonyms.get(entry.getKey()).stream())
              .filter(Objects::nonNull).distinct().collect(toList());
      concept.setNames(FSCollectionFactory.createStringList(jcas, names));
    } );
  }
  
  @Override
  public void collectionProcessComplete() throws AnalysisEngineProcessException {
    super.collectionProcessComplete();
    conceptSearchProvider.destroy();
    synonsymExpanisonProvider.destroy();
  }

}
