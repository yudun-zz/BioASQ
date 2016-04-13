package edu.cmu.lti.oaqa.baseqa.retrieval;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import edu.cmu.lti.oaqa.baseqa.providers.kb.ConceptProvider;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.baseqa.util.ViewType;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.type.kb.ConceptType;
import edu.cmu.lti.oaqa.util.TypeUtil;

/**
 * 
 * @author Zi Yang
 *
 */
public class PassageConceptRecognizer extends JCasAnnotator_ImplBase {

  private ConceptProvider conceptProvider;

  private String viewNamePrefix;

  private Set<String> allowedConceptTypes;

  private boolean checkConceptTypes;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    String conceptProviderName = UimaContextHelper.getConfigParameterStringValue(context,
            "concept-provider");
    conceptProvider = ProviderCache.getProvider(conceptProviderName, ConceptProvider.class);
    viewNamePrefix = UimaContextHelper.getConfigParameterStringValue(context, "view-name-prefix");
    String allowedConceptTypesFile = UimaContextHelper.getConfigParameterStringValue(context,
            "allowed-concept-types", null);
    try {
      allowedConceptTypes = Resources
              .readLines(getClass().getResource(allowedConceptTypesFile), Charsets.UTF_8).stream()
              .collect(toSet());
      checkConceptTypes = true;
    } catch (Exception e) {
      checkConceptTypes = false;
    }
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    List<JCas> views = ViewType.listViews(jcas, viewNamePrefix);
    List<Concept> concepts = conceptProvider.getConcepts(views).stream().flatMap(List::stream)
            .filter(concept -> !checkConceptTypes
                    || containsAllowedConceptType(concept, allowedConceptTypes))
            .collect(toList());
    concepts.forEach(Concept::addToIndexes);
    concepts.stream().map(TypeUtil::getConceptMentions).flatMap(Collection::stream)
            .forEach(ConceptMention::addToIndexes);
  }

  private static boolean containsAllowedConceptType(Concept concept,
          Set<String> allowedConceptTypeAbbreviations) {
    return TypeUtil.getConceptTypes(concept).stream().map(ConceptType::getAbbreviation)
            .anyMatch(allowedConceptTypeAbbreviations::contains);
  }

}
