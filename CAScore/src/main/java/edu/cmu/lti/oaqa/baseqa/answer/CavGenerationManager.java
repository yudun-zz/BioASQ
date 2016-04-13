package edu.cmu.lti.oaqa.baseqa.answer;

import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.List;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import edu.cmu.lti.oaqa.baseqa.answer.generators.CavGenerator;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerOccurrence;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class CavGenerationManager extends JCasAnnotator_ImplBase {

  private List<CavGenerator> generators;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    String generatorNames = UimaContextHelper.getConfigParameterStringValue(context, "generators");
    generators = ProviderCache.getProviders(generatorNames, CavGenerator.class);
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    for (CavGenerator generator : generators) {
      if (generator.accept(jcas)) {
        List<CandidateAnswerVariant> cavs = generator.generate(jcas);
        cavs.forEach(CandidateAnswerVariant::addToIndexes);
        cavs.stream().map(TypeUtil::getCandidateAnswerOccurrences).flatMap(Collection::stream)
                .forEach(CandidateAnswerOccurrence::addToIndexes);
        List<Collection<String>> names = cavs.stream().map(TypeUtil::getCandidateAnswerVariantNames)
                .collect(toList());
        System.out.println("Answer candidates generated: " + names + " from "
                + generator.getClass().getSimpleName());
      }
    }
  }

}
