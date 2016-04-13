package edu.cmu.lti.oaqa.baseqa.providers.ml;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.type.kb.ConceptType;
import edu.cmu.lti.oaqa.type.nlp.Focus;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class FeatureConstructorProviderImpl extends ConfigurableProvider
        implements FeatureConstructorProvider {

  private List<List<String>> quantityQuestionPhrases;

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    String quantityQuestionWordsFile = (String) getParameterValue("quantity-question-words-file");
    try {
      quantityQuestionPhrases = Files.readLines(new File(quantityQuestionWordsFile), Charsets.UTF_8)
              .stream().map(line -> Arrays.asList(line.split(" "))).collect(toList());
    } catch (IOException e) {
      throw new ResourceInitializationException(e);
    }
    return ret;
  }

  @Override
  public Map<String, Double> constructFeatures(List<Token> tokens, List<ConceptMention> cmentions,
          Focus focus) {
    // construct features
    Map<String, Double> features = new HashMap<>();
    for (ConceptMention cmention : cmentions) {
      double score = cmention.getScore();
      for (ConceptType st : TypeUtil.getConceptTypes(cmention.getConcept())) {
        String semTypeAbbr = st.getAbbreviation();
        String semType = semTypeAbbr + "[st]";
        if (!features.containsKey(semType) || features.get(semType) < score) {
          features.put(semType, score);
        }
        Token token = TypeUtil.getHeadTokenOfAnnotation(cmention);
        String semTypeDepLabel = semTypeAbbr + "/" + token.getDepLabel() + "[st-dl]";
        if (!features.containsKey(semTypeDepLabel) || features.get(semTypeDepLabel) < score) {
          features.put(semTypeDepLabel, score);
        }
        String semTypeHeadDepLabel = semTypeAbbr + "/"
                + (token.getHead() == null ? "null" : token.getHead().getDepLabel()) + "[st-hdl]";
        features.put(semTypeHeadDepLabel, score);
      }
    }
    for (Token token : tokens) {
      features.put(token.getLemmaForm() + "[l]", 1.0);
    }
    if (focus != null) {
      features.put(focus.getLabel() + "[f]", 1.0);
    }
    List<String> lemmas = tokens.stream().map(Token::getLemmaForm).collect(toList());
    boolean choice = (lemmas.get(0).equals("do") || lemmas.get(0).equals("be"))
            && lemmas.contains("or");
    features.put("Choice", choice ? 1d : 0d);
    boolean quantity = quantityQuestionPhrases.stream()
            .map(phrase -> Collections.indexOfSubList(lemmas, phrase)).filter(index -> index >= 0)
            .findAny().isPresent();
    features.put("Quantity", quantity ? 1.0 : 0.0);
    return features;
  }

}
