package edu.cmu.lti.oaqa.baseqa.retrieval;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import com.google.common.base.CharMatcher;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;

import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.baseqa.util.ViewType;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.type.kb.ConceptType;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class ConceptMerger extends JCasAnnotator_ImplBase {

  private boolean includeDefaultView;

  private String viewNamePrefix;

  private boolean useName;

  private static final String KEY_PREFIX = "__";

  private static CharMatcher alphaNumeric = CharMatcher.JAVA_LETTER_OR_DIGIT;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    includeDefaultView = UimaContextHelper.getConfigParameterBooleanValue(context,
            "include-default-view", true);
    viewNamePrefix = UimaContextHelper.getConfigParameterStringValue(context, "view-name-prefix",
            null);
    useName = UimaContextHelper.getConfigParameterBooleanValue(context, "use-name", true);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    // create views and get all concepts in the views
    List<JCas> views = new ArrayList<>();
    if (includeDefaultView) {
      views.add(jcas);
    }
    views.addAll(ViewType.listViews(jcas, viewNamePrefix));
    List<Concept> concepts = views.stream().map(TypeUtil::getConcepts).flatMap(Collection::stream)
            .collect(toList());
    // preserve concept fields
    SetMultimap<String, String> id2names = HashMultimap.create();
    concepts.stream().forEach(concept -> TypeUtil.getConceptIds(concept).stream()
            .forEach(id -> id2names.putAll(id, TypeUtil.getConceptNames(concept))));
    SetMultimap<String, String> id2uris = HashMultimap.create();
    concepts.stream().forEach(concept -> TypeUtil.getConceptIds(concept).stream()
            .forEach(id -> id2uris.putAll(id, TypeUtil.getConceptUris(concept))));
    SetMultimap<String, ConceptMention> id2mentions = HashMultimap.create();
    concepts.stream().forEach(concept -> TypeUtil.getConceptIds(concept).stream()
            .forEach(id -> id2mentions.putAll(id, TypeUtil.getConceptMentions(concept))));
    // also remove duplicated concept type entries
    SetMultimap<String, List<String>> id2types = HashMultimap.create();
    concepts.stream()
            .forEach(concept -> TypeUtil.getConceptIds(concept).stream()
                    .forEach(id -> TypeUtil.getConceptTypes(concept).stream()
                            .forEach(type -> id2types.put(id, toTypeList(type)))));
    // connectivity detection for merging
    UndirectedGraph<String, DefaultEdge> graph = new SimpleGraph<String, DefaultEdge>(
            DefaultEdge.class);
    concepts.stream().map(TypeUtil::getConceptIds).flatMap(Collection::stream)
            .map(id -> KEY_PREFIX + id).forEach(graph::addVertex);
    concepts.stream().map(TypeUtil::getConceptIds).map(ImmutableSet::copyOf)
            .map(ids -> Sets.cartesianProduct(ids, ids)).flatMap(Set::stream)
            .filter(idpair -> idpair.get(0).compareTo(idpair.get(1)) > 0).forEach(idpair -> graph
                    .addEdge(KEY_PREFIX + idpair.get(0), KEY_PREFIX + idpair.get(1)));
    if (useName) {
      id2names.values().stream().map(ConceptMerger::nameKey).forEach(graph::addVertex);
      id2names.entries().stream().forEach(
              entry -> graph.addEdge(KEY_PREFIX + entry.getKey(), nameKey(entry.getValue())));
    }
    views.stream().forEach(view -> view.removeAllIncludingSubtypes(Concept.type));
    ConnectivityInspector<String, DefaultEdge> ci = new ConnectivityInspector<>(graph);
    ci.connectedSets().stream().map(subgraph -> {
      Set<String> ids = subgraph.stream().filter(str -> str.startsWith(KEY_PREFIX))
              .map(str -> str.substring(KEY_PREFIX.length())).collect(toSet());
      List<String> names = ids.stream().map(id2names::get).flatMap(Set::stream)
              .filter(Objects::nonNull).distinct().collect(toList());
      List<String> uris = ids.stream().map(id2uris::get).flatMap(Set::stream)
              .filter(Objects::nonNull).distinct().collect(toList());
      List<ConceptType> types = ids.stream().map(id2types::get).flatMap(Set::stream)
              .filter(Objects::nonNull).distinct().map(type -> parseTypeList(jcas, type))
              .collect(toList());
      List<ConceptMention> mentions = ids.stream().map(id2mentions::get).flatMap(Set::stream)
              .filter(Objects::nonNull).collect(toList());
      return TypeFactory.createConcept(jcas, names, uris, ImmutableList.copyOf(ids), mentions,
              types);
    } ).forEach(Concept::addToIndexes);
  }

  private static String nameKey(String name) {
    return alphaNumeric.retainFrom(name.toLowerCase());
  }

  private static List<String> toTypeList(ConceptType type) {
    return Arrays.asList(type.getId(), type.getName(), type.getAbbreviation());
  }

  private static ConceptType parseTypeList(JCas jcas, List<String> type) {
    return TypeFactory.createConceptType(jcas, type.get(0), type.get(1), type.get(2));
  }

}
