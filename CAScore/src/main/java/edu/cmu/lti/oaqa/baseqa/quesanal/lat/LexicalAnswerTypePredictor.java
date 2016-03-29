package edu.cmu.lti.oaqa.baseqa.quesanal.lat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import edu.cmu.lti.oaqa.baseqa.providers.ml.ClassifierProvider;
import edu.cmu.lti.oaqa.baseqa.providers.ml.FeatureConstructorProvider;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.type.nlp.Focus;
import edu.cmu.lti.oaqa.type.nlp.LexicalAnswerType;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class LexicalAnswerTypePredictor extends JCasAnnotator_ImplBase {

  private FeatureConstructorProvider featureConstructor;

  private ClassifierProvider classifier;

  private BufferedWriter predictFileWriter;

  private int limit;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    String featureConstructorName = UimaContextHelper.getConfigParameterStringValue(context,
            "feature-constructor");
    featureConstructor = ProviderCache.getProvider(featureConstructorName,
            FeatureConstructorProvider.class);
    String classifierName = UimaContextHelper.getConfigParameterStringValue(context, "classifier");
    classifier = ProviderCache.getProvider(classifierName, ClassifierProvider.class);
    String predictFilename = UimaContextHelper.getConfigParameterStringValue(context,
            "predict-file", null);
    limit = UimaContextHelper.getConfigParameterIntValue(context, "limit", 1);
    if (predictFilename != null) {
      try {
        predictFileWriter = Files.newWriter(new File(predictFilename), Charsets.UTF_8);
      } catch (FileNotFoundException e) {
        throw new ResourceInitializationException(e);
      }
    }
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    // load data
    List<Token> tokens = TypeUtil.getOrderedTokens(jcas);
    List<ConceptMention> cmentions = TypeUtil.getOrderedConceptMentions(jcas);
    Focus focus = TypeUtil.getFocus(jcas);
    Map<String, Double> features = featureConstructor.constructFeatures(tokens, cmentions, focus);
    // predication
    List<String> lats = classifier.predict(features, limit);
    lats.stream().map(lat -> TypeFactory.createLexicalAnswerType(jcas, lat))
            .forEachOrdered(LexicalAnswerType::addToIndexes);
    String question = TypeUtil.getQuestion(jcas).getText().trim().replaceAll("\\s", " ")
            .replaceAll("–", "-").replaceAll("’", "'");
    // TODO: debug info
    System.out.println("Question: " + question);
    System.out.println("Found LAT " + lats);
    // print to file if exists
    if (predictFileWriter != null) {
      try {
        predictFileWriter.write(question + "\t" + lats + "\n");
      } catch (IOException e) {
        throw new AnalysisEngineProcessException(e);
      }
    }
  }

  @Override
  public void collectionProcessComplete() throws AnalysisEngineProcessException {
    super.collectionProcessComplete();
    if (predictFileWriter != null) {
      try {
        predictFileWriter.close();
      } catch (IOException e) {
        throw new AnalysisEngineProcessException(e);
      }
    }
  }

}
