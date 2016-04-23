package edu.cmu.lti.oaqa.baseqa.answer.scorers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class Token2GramOverlappingCountCavScorer extends ConfigurableProvider
		implements CavScorer {
	@Override
	public Map<String, Double> score(JCas jcas, CandidateAnswerVariant cav) {
		List<String> qtokenCoveredText = TypeUtil.getOrderedTokens(jcas)
				.stream().flatMap(token -> Stream.of(token.getCoveredText()))
				.map(String::toLowerCase).collect(Collectors.toList());
		List<String> qtokenLemmaForm = TypeUtil.getOrderedTokens(jcas).stream()
				.flatMap(token -> Stream.of(token.getLemmaForm()))
				.map(String::toLowerCase).collect(Collectors.toList());

		Set<String> qtoken2Gram = new HashSet<String>();

		for (int i = 0; i < qtokenCoveredText.size() - 1; i++) {
			qtoken2Gram.add(qtokenCoveredText.get(i) + " "
					+ qtokenCoveredText.get(i + 1));
		}

		for (int i = 0; i < qtokenLemmaForm.size() - 1; i++) {
			qtoken2Gram.add(qtokenLemmaForm.get(i) + " "
					+ qtokenLemmaForm.get(i + 1));
		}

		@SuppressWarnings("unchecked")
		List<Token>[] tmp = (List<Token>[]) TypeUtil
				.getCandidateAnswerOccurrences(cav).stream()
				.map(cao -> JCasUtil.selectCovered(Token.class, cao)).toArray();

		double[] qtokenOverlappingRatios = new double[tmp.length];
		int idx = 0;
		for (List<Token> tokens : tmp) {
			List<String> strtmp = new ArrayList<String>();
			for (int i = 0; i < tokens.size() - 1; i++) {
				String twoGram = tokens.get(i) + " " + tokens.get(i + 1);
				strtmp.add(twoGram);
			}
			qtokenOverlappingRatios[idx++] = CavScorer.safeDividedBy(
					strtmp.stream().map(String::toLowerCase)
							.filter(qtoken2Gram::contains).count(),
					strtmp.size());
		}
		return CavScorer.generateSummaryFeatures(qtokenOverlappingRatios,
				"token-overlap", "avg", "pos-ratio", "any-one");
	}
}
