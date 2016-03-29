package edu.cmu.lti.oaqa.baseqa.answer.scorers;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerOccurrence;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.type.kb.ConceptType;
import edu.cmu.lti.oaqa.type.nlp.LexicalAnswerType;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class TypeCoercionCavScorer extends ConfigurableProvider implements CavScorer {

  private Iterable<Integer> latsLimits;

  @SuppressWarnings("unchecked")
  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    latsLimits = (Iterable<Integer>) getParameterValue("lats-limit");
    return ret;
  }

  @Override
  public Map<String, Double> score(JCas jcas, CandidateAnswerVariant cav) {
    List<String> lats = TypeUtil.getLexicalAnswerTypes(jcas).stream()
            .map(LexicalAnswerType::getLabel).collect(toList());
    Collection<CandidateAnswerOccurrence> caos = TypeUtil.getCandidateAnswerOccurrences(cav);
    List<Set<String>> typesList = caos.stream()
            .map(cao -> JCasUtil.selectCovered(ConceptMention.class, cao).stream()
                    .map(ConceptMention::getConcept).map(TypeUtil::getConceptTypes)
                    .flatMap(Collection::stream).map(ConceptType::getAbbreviation).collect(toSet()))
            .collect(toList());
    ImmutableMap.Builder<String, Double> feat2value = ImmutableMap.builder();
    latsLimits.forEach(limit -> {
      Set<String> limitedLats = ImmutableSet.copyOf(lats.subList(0, limit));
      double[] typecorRatios = typesList.stream().mapToDouble(types -> {
        int overlap = Sets.intersection(limitedLats, types).size();
        int maxOverlap = Math.min(limitedLats.size(), types.size());
        return CavScorer.safeDividedBy(overlap, maxOverlap);
      } ).toArray();
      feat2value.putAll(CavScorer.generateSummaryFeatures(typecorRatios, "type-coercion-" + limit,
              "avg", "max", "min", "pos-ratio", "one-ratio", "any-one"));
    } );
    return feat2value.build();
  }

}
