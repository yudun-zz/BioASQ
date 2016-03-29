package edu.cmu.lti.oaqa.baseqa.answer.modifiers;

import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerOccurrence;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class CavMerger extends ConfigurableProvider implements CavModifier {

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    return ret;
  }

  @Override
  public boolean accept(JCas jcas) throws AnalysisEngineProcessException {
    return true;
  }

  @SuppressWarnings("unchecked")
  @Override
  public void modify(JCas jcas) throws AnalysisEngineProcessException {
    Collection<CandidateAnswerVariant> cavs = TypeUtil.getCandidateAnswerVariants(jcas);
    UndirectedGraph<Object, DefaultEdge> graph = new SimpleGraph<Object, DefaultEdge>(
            DefaultEdge.class);
    cavs.forEach(cav -> {
      Set<String> names = ImmutableSet.copyOf(TypeUtil.getCandidateAnswerVariantNames(cav));
      Set<CandidateAnswerOccurrence> caos = ImmutableSet
              .copyOf(TypeUtil.getCandidateAnswerOccurrences(cav));
      Stream.concat(names.stream(), caos.stream()).forEach(graph::addVertex);
      Sets.cartesianProduct(names, caos).forEach(pair -> graph.addEdge(pair.get(0), pair.get(1)));
    } );
    jcas.removeAllIncludingSubtypes(CandidateAnswerVariant.type);
    ConnectivityInspector<Object, DefaultEdge> ci = new ConnectivityInspector<>(graph);
    ci.connectedSets().stream().map(subgraph -> {
      List<String> names = subgraph.stream().filter(String.class::isInstance)
              .map(String.class::cast).collect(toList());
      List<CandidateAnswerOccurrence> caos = subgraph.stream()
              .filter(CandidateAnswerOccurrence.class::isInstance)
              .map(CandidateAnswerOccurrence.class::cast).collect(toList());
      return TypeFactory.createCandidateAnswerVariant(jcas, caos, names);
    } ).forEach(CandidateAnswerVariant::addToIndexes);
  }

}
