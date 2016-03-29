package edu.cmu.lti.oaqa.baseqa.quesanal.lat;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.yaml.snakeyaml.Yaml;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multiset;
import com.google.common.io.Files;
import com.google.common.io.Resources;

import edu.cmu.lti.oaqa.baseqa.providers.kb.ConceptSearchProvider;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.baseqa.util.ViewType;
import edu.cmu.lti.oaqa.type.input.Question;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.kb.ConceptType;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class AnswerTypeLabelExtractor extends JCasAnnotator_ImplBase {

  private ConceptSearchProvider conceptSearch;

  private List<List<String>> quantityQuestionPhrases;

  private String answerSemanticTypesFile;

  private List<Map<String, Object>> yamlSummary;

  private static final String QUANTITY_LABEL = "_QUANTITY";

  private static final String CHOICE_LABEL = "_CHOICE";

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    String conceptSearchProviderName = UimaContextHelper.getConfigParameterStringValue(context,
            "concept-search-provider");
    conceptSearch = ProviderCache.getProvider(conceptSearchProviderName,
            ConceptSearchProvider.class);
    String quantityQuestionWordsFile = UimaContextHelper.getConfigParameterStringValue(context,
            "quantity-question-words-file");
    try {
      quantityQuestionPhrases = Resources
              .readLines(getClass().getResource(quantityQuestionWordsFile), Charsets.UTF_8).stream()
              .map(line -> Arrays.asList(line.split(" "))).collect(toList());
    } catch (IOException e) {
      throw new ResourceInitializationException(e);
    }
    answerSemanticTypesFile = UimaContextHelper.getConfigParameterStringValue(context,
            "answer-semantic-types-file");
    yamlSummary = new ArrayList<>();
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    // prepare input
    List<String> lemmas = TypeUtil.getOrderedTokens(jcas).stream().map(Token::getLemmaForm)
            .collect(toList());
    // identify answer semantic type
    boolean quantity = quantityQuestionPhrases.stream()
            .map(phrase -> Collections.indexOfSubList(lemmas, phrase)).filter(index -> index >= 0)
            .findAny().isPresent();
    if (quantity) {
      TypeFactory.createConceptType(jcas, QUANTITY_LABEL).addToIndexes();
      parpareYamlSummary(jcas);
      return;
    }
    boolean choice = (lemmas.get(0).equals("do") || lemmas.get(0).equals("be"))
            && lemmas.contains("or");
    if (choice) {
      TypeFactory.createConceptType(jcas, CHOICE_LABEL).addToIndexes();
      parpareYamlSummary(jcas);
      return;
    }
    List<String> variants = TypeUtil.getRankedAnswers(ViewType.getGsView(jcas)).stream()
            .map(TypeUtil::getCandidateAnswerVariantNames).flatMap(List::stream).map(String::trim)
            .filter(variant -> variant.length() > 0).collect(toList());
    for (String variant : variants) {
      Optional<Concept> optional = conceptSearch.search(jcas, variant);
      if (!optional.isPresent())
        continue;
      TypeUtil.getConceptTypes(optional.get()).forEach(ConceptType::addToIndexes);
    }
    // prepare for yaml summary
    parpareYamlSummary(jcas);
  }

  private void parpareYamlSummary(JCas jcas) throws AnalysisEngineProcessException {
    Map<String, Object> yamlQuestion = new HashMap<>();
    yamlSummary.add(yamlQuestion);
    Question question = TypeUtil.getQuestion(jcas);
    String body = question.getText().trim().replaceAll("\\s+", " ");
    yamlQuestion.put("question", body);
    yamlQuestion.put("qid", question.getId());
    List<String> variants = TypeUtil.getRankedAnswers(ViewType.getGsView(jcas)).stream()
            .map(TypeUtil::getCandidateAnswerVariantNames).flatMap(List::stream).map(String::trim)
            .filter(variant -> variant.length() > 0).collect(toList());
    yamlQuestion.put("answers", variants);
    List<Map<String, String>> types = TypeUtil.getConceptTypes(jcas).stream()
            .map(type -> ImmutableMap.of("name", type.getName(), "id",
                    Strings.nullToEmpty(type.getId()), "abbr", type.getAbbreviation()))
            .collect(toList());
    yamlQuestion.put("types", types);
    List<Map<String, Object>> typeCount = types.stream()
            .collect(groupingBy(Function.identity(), counting())).entrySet().stream()
            .sorted(Comparator.comparing(Map.Entry::getValue, Comparator.reverseOrder()))
            .map(entry -> ImmutableMap.<String, Object> builder().putAll(entry.getKey())
                    .put("count", entry.getValue()).build())
            .collect(toList());
    yamlQuestion.put("type-count", typeCount);
  }

  @Override
  public void collectionProcessComplete() throws AnalysisEngineProcessException {
    super.collectionProcessComplete();
    Yaml yaml = new Yaml();
    try {
      Files.write(yaml.dump(yamlSummary), new File(answerSemanticTypesFile), Charsets.UTF_8);
    } catch (IOException e) {
      throw new AnalysisEngineProcessException(e);
    }
  }

  public static void main(String[] args) throws Exception {
    analyzeQuestionSemanticType(
            new File("src/main/resources/bioqa/quesanal/lat/feat-label/3b-dev-qa-types-pre.yaml"),
            new File("src/main/resources/bioqa/quesanal/lat/feat-label/3b-dev-qa-types-pre.tsv"));
  }

  private static void analyzeQuestionSemanticType(File input, File output) throws IOException {
    Yaml yaml = new Yaml();
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> yamlQuestions = yaml.loadAs(Files.newReader(input, Charsets.UTF_8),
            List.class);
    BufferedWriter writer = Files.newWriter(output, Charsets.UTF_8);
    Multiset<String> typeCount = HashMultiset.create();
    for (Map<String, Object> yamlQuestion : yamlQuestions) {
      @SuppressWarnings("unchecked")
      String types = ((List<Map<String, Object>>) List.class.cast(yamlQuestion.get("type-count")))
              .stream().map(map -> map.get("name") + ": " + map.get("count"))
              .collect(joining(", "));
      @SuppressWarnings("unchecked")
      String answers = ((List<String>) List.class.cast(yamlQuestion.get("answers"))).stream()
              .collect(joining(", "));
      writer.write(yamlQuestion.get("question") + "\t" + types + "\t" + answers + "\n");
    }
    typeCount.stream().collect(groupingBy(Function.identity(), counting())).entrySet().stream()
            .sorted(Comparator.comparing(Map.Entry::getValue, Comparator.reverseOrder()))
            .map(entry -> entry.getKey() + ": " + entry.getValue()).forEach(System.out::println);
    writer.close();
  }

}
