package edu.cmu.lti.oaqa.baseqa.answer.scorers;

import java.util.Map;

import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import com.google.common.collect.ImmutableMap;

import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class DevCoveredTokenCountCavScorer extends ConfigurableProvider
		implements CavScorer {

	@Override
	public Map<String, Double> score(JCas jcas, CandidateAnswerVariant cav) {
		// TODO Auto-generated method stub
		double avgCount = TypeUtil
				.getCandidateAnswerOccurrences(cav)
				.stream()
				.mapToInt(
						cao -> JCasUtil.selectCovered(Token.class, cao).size())
				.average().orElse(0);
		double devCount = CavScorer.safeDividedBy(Math.sqrt(TypeUtil
				.getCandidateAnswerOccurrences(cav)
				.stream()
				.mapToDouble(
						cao -> Math.pow(JCasUtil
								.selectCovered(Token.class, cao).size()
								- avgCount, 2)).average().orElse(0.0)),
				avgCount);
		return ImmutableMap.of("dev-covered-token-count", devCount);
	}

}
