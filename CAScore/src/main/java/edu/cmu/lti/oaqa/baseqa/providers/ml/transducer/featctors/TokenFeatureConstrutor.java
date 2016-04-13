package edu.cmu.lti.oaqa.baseqa.providers.ml.transducer.featctors;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;

import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.gerp.util.Pair;
import edu.cmu.lti.oaqa.type.nlp.Focus;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class TokenFeatureConstrutor extends ConfigurableProvider
        implements TransducerFeatureConstructorProvider {

  private Set<String> stoplist;

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
    return ret;
  }

  @Override
  public List<Map<String, Double>> constructFeaturesSequence(List<Token> tokens, JCas jcas,
          JCas refJcas) {
    return tokens.stream().map(token -> {
      String surface = token.getCoveredText();
      ImmutableMap.Builder<String, Double> builder = ImmutableMap.builder();
      builder.put("[token:lemma]" + token.getLemmaForm(), 1.0);
      builder.put("[token:length]", (double) surface.length());
      builder.put("[token:char-type]" + getSurfaceCharTypes(surface), 1.0);
      builder.put("[token:pos]" + token.getPartOfSpeech(), 1.0);
      builder.put("[token:dep]" + token.getDepLabel(), 1.0);
      if (stoplist.contains(surface.toLowerCase()) || stoplist.contains(token.getLemmaForm())) {
        builder.put("[token:stop]", 1.0);
      }
      Focus focus = TypeUtil.getFocus(refJcas);
      if (focus != null && focus.getToken().equals(token)) {
        builder.put("[token:focus]", 1.0);
      }
      return builder.build();
    }).collect(toList());
  }

  private static final List<Pair<Character, CharMatcher>> TYPE_MATCHERS = ImmutableList
          .<Pair<Character, CharMatcher>> builder().add(Pair.of('L', CharMatcher.JAVA_LOWER_CASE))
          .add(Pair.of('U', CharMatcher.JAVA_UPPER_CASE)).add(Pair.of('D', CharMatcher.JAVA_DIGIT))
          .addAll("!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~".chars().mapToObj(c -> (char) c)
                  .map(c -> Pair.of(c, CharMatcher.is(c))).collect(toList()))
          .add(Pair.of('N', CharMatcher.ASCII.negate())).build();

  private String getSurfaceCharTypes(String surface) {
    if (surface.length() == 0) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    char head = surface.charAt(0);
    for (Pair<Character, CharMatcher> typeMatcher : TYPE_MATCHERS) {
      if (typeMatcher.getValue().matches(head)) {
        builder.append(typeMatcher.getKey());
      }
    }
    if (surface.length() == 1) {
      return builder.toString();
    }
    String remaining = surface.substring(1);
    for (Pair<Character, CharMatcher> typeMatcher : TYPE_MATCHERS) {
      if (typeMatcher.getValue().matchesAnyOf(remaining)) {
        builder.append(typeMatcher.getKey());
      }
    }
    return builder.toString();
  }

}
