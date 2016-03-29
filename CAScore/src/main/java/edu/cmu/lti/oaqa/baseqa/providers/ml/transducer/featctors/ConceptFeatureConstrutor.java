package edu.cmu.lti.oaqa.baseqa.providers.ml.transducer.featctors;

import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.SetMultimap;

import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.type.kb.ConceptType;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class ConceptFeatureConstrutor extends ConfigurableProvider
        implements TransducerFeatureConstructorProvider {

  @Override
  public List<Map<String, Double>> constructFeaturesSequence(List<Token> tokens, JCas jcas,
          JCas refJcas) {
    SetMultimap<Token, ConceptMention> token2cmentions = HashMultimap.create();
    TypeUtil.getConceptMentions(jcas).stream()
            .forEach(cmention -> JCasUtil.selectCovered(Token.class, cmention).stream()
                    .forEach(token -> token2cmentions.put(token, cmention)));
    return tokens.stream().map(token -> {
      ImmutableMap.Builder<String, Double> builder = ImmutableMap.builder();
      Set<ConceptMention> cmentions = token2cmentions.get(token);
      if (cmentions.isEmpty()) {
        return builder.build();
      }
      builder.put("[concept:count]", (double) cmentions.size());
      builder.put("[concept:exist]", 1.0);
      cmentions.stream().map(ConceptMention::getConcept).map(TypeUtil::getConceptTypes)
              .flatMap(Collection::stream).map(ConceptType::getAbbreviation).distinct()
              .map(abbr -> "[concept:type]" + abbr).forEach(feat -> builder.put(feat, 1.0));
      cmentions.stream().filter(cmention -> cmention.getBegin() == token.getBegin()).findAny()
              .ifPresent(cmention -> builder.put("[concept:begin]", 1.0));
      cmentions.stream().filter(cmention -> cmention.getEnd() == token.getEnd()).findAny()
              .ifPresent(cmention -> builder.put("[concept:end]", 1.0));
      return builder.build();
    } ).collect(toList());
  }

}
