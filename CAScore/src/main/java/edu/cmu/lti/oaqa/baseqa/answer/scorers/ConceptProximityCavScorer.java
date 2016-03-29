package edu.cmu.lti.oaqa.baseqa.answer.scorers;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;

import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class ConceptProximityCavScorer extends ConfigurableProvider implements CavScorer {

  private Set<String> stoplist;

  private int windowSize;

  private int infinity;

  private double smoothing;

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    String stoplistFile = (String) getParameterValue("stoplist");
    try {
      stoplist = Resources.readLines(getClass().getResource(stoplistFile), Charsets.UTF_8).stream()
              .collect(toSet());
    } catch (IOException e) {
      throw new ResourceInitializationException(e);
    }
    windowSize = (int) getParameterValue("window-size");
    infinity = (int) getParameterValue("infinity");
    smoothing = (double) getParameterValue("smoothing");
    return ret;
  }

  @Override
  public Map<String, Double> score(JCas jcas, CandidateAnswerVariant cav) {
    Set<Concept> qconcepts = TypeUtil.getOrderedConceptMentions(jcas).stream()
            .filter(cmention -> !stoplist.contains(cmention.getCoveredText().toLowerCase()))
            .map(ConceptMention::getConcept).collect(toSet());
    double[] distances = TypeUtil.getCandidateAnswerOccurrences(cav).stream().mapToDouble(cao -> {
      List<Concept> precedingConcepts = JCasUtil
              .selectPreceding(ConceptMention.class, cao, windowSize).stream()
              .sorted(Comparator.comparing(ConceptMention::getEnd, Comparator.reverseOrder()))
              .map(ConceptMention::getConcept).collect(toList());
      List<Concept> followingConcepts = JCasUtil
              .selectFollowing(ConceptMention.class, cao, windowSize).stream()
              .sorted(Comparator.comparing(ConceptMention::getBegin))
              .map(ConceptMention::getConcept).collect(toList());
      return qconcepts.stream().mapToDouble(qconcept -> {
        int precedingDistance = precedingConcepts.indexOf(qconcept);
        if (precedingDistance == -1) {
          precedingDistance = infinity;
        }
        int followingDistance = followingConcepts.indexOf(qconcept);
        if (followingDistance == -1) {
          followingDistance = infinity;
        }
        return Math.min(precedingDistance, followingDistance);
      } ).average().orElse(infinity);
    } ).toArray();
    ImmutableMap.Builder<String, Double> builder = ImmutableMap.builder();
    double[] negdistances = Arrays.stream(distances).map(distance -> distance - infinity).toArray();
    builder.putAll(
            CavScorer.generateSummaryFeatures(negdistances, "concept-negdistance", "avg", "min"));
    double[] proximities = Arrays.stream(distances).map(distance -> 1.0 / (smoothing + distance))
            .toArray();
    builder.putAll(CavScorer.generateSummaryFeatures(proximities, "concept-proximity", "avg", "max",
            "min", "pos-ratio"));
    return builder.build();
  }

}
