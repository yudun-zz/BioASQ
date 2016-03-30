import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.StringTokenizer;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class Main {

    static int RET_DOC = 20;
    static int QUESTION_TEST = 1;
    static Map<String, Integer> typeToInd = new HashMap<String, Integer>();
    static Map<Integer, String> indToType = new HashMap<Integer, String>();
    static float[][] arr = new float[1701632][200];

    public static void main(String[] args) throws SolrServerException, IOException, ParseException {
	String serverUrl = "http://ur.lti.cs.cmu.edu:8986/solr/medline";
	SolrClient client = new HttpSolrClient(serverUrl);
	SolrQuery newquery = new SolrQuery();

	String line;

	BufferedReader br2 = new BufferedReader(
		new FileReader(new File("src/main/resources/types.txt")));
	int ind = 0;
	while ((line = br2.readLine()) != null) {
	    typeToInd.put(line, ind);
	    indToType.put(ind, line);
	    ind++;
	}

	BufferedReader br = new BufferedReader(
		new FileReader(new File("src/main/resources/vectors.txt")));

	int counter = -1;
	while ((line = br.readLine()) != null) {
	    counter += 1;
	    // process the line.
	    String[] items = line.split(" ");
	    for (int i = 0; i < items.length; i++) {
		float val = Float.parseFloat(items[i]);
		arr[counter][i] = val;
	    }

	    if (counter % 10000 == 0) {
		System.out.println(counter);
	    }

	}

	List<String> ids = new ArrayList<String>();
	Map<String, String> idToQuestion = new HashMap<String, String>();
	Map<String, List<String>> idToGoldDocs = new HashMap<String, List<String>>();

	JSONParser parser = new JSONParser();

	BufferedReader br3 = new BufferedReader(
		new FileReader(new File("src/main/resources/bioqa_devel_answers.json")));
	JSONArray aArray = (JSONArray) parser.parse(br3);

	for (int i = 0; i < aArray.size(); i++) {
	    JSONObject aObject = (JSONObject) aArray.get(i);
	    String aId = (String) aObject.get("id");
	    JSONArray docArray = (JSONArray) aObject.get("documents");
	    List<String> docList = new ArrayList<String>();

	    for (int j = 0; j < docArray.size(); j++) {
		docList.add((String) docArray.get(j));
	    }

	    idToGoldDocs.put(aId, docList);
	}

	BufferedReader br4 = new BufferedReader(
		new FileReader(new File("src/main/resources/bioqa_devel_questions.json")));
	JSONArray qArray = (JSONArray) parser.parse(br4);

	for (int i = 0; i < qArray.size(); i++) {
	    JSONObject qObject = (JSONObject) qArray.get(i);
	    String qId = (String) qObject.get("id");
	    String qBody = (String) qObject.get("body");

	    if (idToGoldDocs.containsKey(qId)) {
		// to avoid questions with no gold docs
		idToQuestion.put(qId, qBody);
		ids.add(qId);
	    }
	}

	double map = 0;

	for (int i = 0; i < Math.min(QUESTION_TEST, ids.size()); i++) {
	    String id = ids.get(i);
	    String question = idToQuestion.get(id);
	    List<String> goldDocs = idToGoldDocs.get(id);

	    newquery.setQuery(question);
	    newquery.setRows(RET_DOC); // set the maximum retrieved documents
	    QueryResponse rsp = client.query(newquery);
	    SolrDocumentList retDocList = rsp.getResults();

	    int countHit = 0;
	    float avg_precsion = 0;

	    for (int j = 0; j < retDocList.size(); j++) {
		SolrDocument solrDoc = retDocList.get(j);
		String doc = (String) solrDoc.getFieldValue("Id");
		if (goldDocs.contains(doc)) {
		    countHit++;
		    avg_precsion += (float) countHit / (j + 1);
		}
	    }

	    double precision = (double) countHit / RET_DOC;
	    double recall = (double) countHit / goldDocs.size();
	    double fmeasure = 0;
	    if (precision != 0 || recall != 0) {
		fmeasure = 2 * precision * recall / (precision + recall);
	    }
	    avg_precsion = avg_precsion / goldDocs.size();

	    map += avg_precsion;

	    System.out.println("precision = " + precision);
	    System.out.println("recall = " + recall);
	    System.out.println("f measure = " + fmeasure);
	    System.out.println("avg_precsion = " + avg_precsion);
	}

	map = map / QUESTION_TEST;

	System.out.println("MAP = " + map);

	client.close();
    }

    private static String expandQuestion(String question) {
	// TODO
	return "";

    }

    private static String expandWord(String word, int num) {
	int wordInd = typeToInd.get(word);
	float[] wordVec = arr[wordInd];

	double[] distances = getDistances(wordVec, arr);

	List<KVpair> nearestIns = largestK(distances, num);

	String res = "";
	for (KVpair pair : nearestIns) {
	    res += indToType.get(pair.key) + " ";
	}
	System.out.println(word + " --> " + res);
	return res;
    }

    private static List<KVpair> largestK(double[] array, int k) {
	Comparator<KVpair> myComp = new Comparator<KVpair>() {
	    public int compare(KVpair p1, KVpair p2) {
		return (p1.value > p2.value) ? 1 : -1;
	    };
	};

	PriorityQueue<KVpair> queue = new PriorityQueue<KVpair>(k + 1, myComp);

	int i = 0;
	while (i < k) {
	    queue.add(new KVpair(i, array[i]));
	    i++;
	}
	for (; i < array.length; i++) {
	    KVpair pair = queue.peek();
	    if (array[i] > pair.value) {
		queue.poll();
		queue.add(new KVpair(i, array[i]));
	    }
	}
	List<KVpair> res = new ArrayList<KVpair>();
	while (!queue.isEmpty()) {
	    res.add(queue.poll());
	}
	return res;
    }

    private static double[] getDistances(float[] vec, float[][] vectors) {
	double[] res = new double[vectors.length];
	for (int i = 0; i < vectors.length; i++) {
	    res[i] = getCosSim(vec, vectors[i]);
	}
	return res;
    }

    private static double getCosSim(float[] v1, float[] v2) {
	double res = 0;
	double s1 = 0;
	double s2 = 0;
	for (int i = 0; i < v1.length; i++) {
	    res += v1[i] * v2[i];
	    s1 += v1[i] * v1[i];
	    s2 += v2[i] * v2[i];
	}
	return res / (Math.sqrt(s1 * s2));
    }
}

class KVpair {
    int key;
    double value;

    public KVpair(int key, double value) {
	this.key = key;
	this.value = value;
    }
}
