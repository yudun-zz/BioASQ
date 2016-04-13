package edu.cmu.lti.oaqa.baseqa.quesanal.lat;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.common.base.Charsets;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import com.google.common.io.Resources;

import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.nlp.LexicalAnswerType;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class LexicalAnswerTypeCVPredictLoader extends JCasAnnotator_ImplBase {

  private Map<String, List<String>> qid2lats;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    String cvPredictFile = UimaContextHelper.getConfigParameterStringValue(context,
            "cv-predict-file");
    List<String> lines;
    try {
      lines = Resources.readLines(getClass().getResource(cvPredictFile), Charsets.UTF_8);
    } catch (IOException e) {
      throw new ResourceInitializationException(e);
    }
    qid2lats = lines.stream().map(line -> line.split("\t")).collect(toMap(segs -> segs[0],
            segs -> Arrays.stream(segs[1].split(";")).collect(toList()), (x, y) -> x));
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    String id = TypeUtil.getQuestion(jcas).getId();
    List<String> lats = qid2lats.get(id);
    lats.stream().map(lat -> TypeFactory.createLexicalAnswerType(jcas, lat))
            .forEachOrdered(LexicalAnswerType::addToIndexes);
  }

}
