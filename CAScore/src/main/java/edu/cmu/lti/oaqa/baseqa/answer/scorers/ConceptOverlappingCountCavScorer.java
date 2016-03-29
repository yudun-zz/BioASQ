package edu.cmu.lti.oaqa.baseqa.answer.scorers;

import static java.util.stream.Collectors.toSet;

import java.util.Map;
import java.util.Set;

import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class ConceptOverlappingCountCavScorer extends ConfigurableProvider implements CavScorer {

  @Override
  public Map<String, Double> score(JCas jcas, CandidateAnswerVariant cav) {
    Set<Concept> qconcepts = TypeUtil.getConceptMentions(jcas).stream()
            .map(ConceptMention::getConcept).collect(toSet());
    double[] qconceptOverlappingRatios = TypeUtil.getCandidateAnswerOccurrences(cav).stream()
            .map(cao -> JCasUtil.selectCovered(ConceptMention.class, cao))
            .mapToDouble(concepts -> CavScorer.safeDividedBy(concepts.stream()
                    .map(ConceptMention::getConcept).filter(qconcepts::contains).count(),
                    concepts.size()))
            .toArray();
    return CavScorer.generateSummaryFeatures(qconceptOverlappingRatios, "concept-overlap", "avg",
            "pos-ratio", "any-one");
  }

}
