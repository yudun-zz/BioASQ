package edu.cmu.lti.oaqa.baseqa.providers.ml.transducer.featctors;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import com.google.common.collect.ImmutableMap;

import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class ConceptOverlappingFeatureConstrutor extends ConfigurableProvider
        implements TransducerFeatureConstructorProvider {

  @Override
  public List<Map<String, Double>> constructFeaturesSequence(List<Token> tokens, JCas jcas,
          JCas refJcas) {
    Set<Concept> qconcepts = TypeUtil.getConceptMentions(refJcas).stream()
            .map(ConceptMention::getConcept).collect(toSet());
    return tokens.stream().map(token -> {
      ImmutableMap.Builder<String, Double> builder = ImmutableMap.builder();
      JCasUtil.selectCovering(ConceptMention.class, token).stream().map(ConceptMention::getConcept)
              .filter(qconcepts::contains).findAny()
              .ifPresent(concept -> builder.put("[concept-overlap]", 1.0));
      return builder.build();
    } ).collect(toList());
  }

}
