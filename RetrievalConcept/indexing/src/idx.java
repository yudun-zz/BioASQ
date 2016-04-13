/**
 * Created by Sherry on 4/5/16.
 */

import java.io.*;
import java.util.ArrayList;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.ScoreDoc;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;


public class idx {

    public static final String FILES_TO_INDEX_DIRECTORY = "ontology";
    public static final String INDEX_DIRECTORY = "indexDirectory";

    public static final String FIELD_ID = "id";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_DEF = "definition";
    public static final String FIELD_SYN = "synonym";

    public static void main(String[] args) throws Exception {

        double map_for_hit = 0;
        int hit = 0;
        //createIndex();
        //searchIndex("Which is the receptor for substrates of Chaperone Mediated Autophagy");
        double MAP = 0.0;
        int queryNumber = 0;
        JSONParser parser = new JSONParser();
        JSONArray goldStandard = (JSONArray) parser.parse(new FileReader("./3b-dev-concepts.json"));
        for (Object o:goldStandard){
            ArrayList goldConcepts = new ArrayList();
            JSONObject query = (JSONObject) o;
            String body = (String) query.get("body");
            JSONArray answers = (JSONArray) query.get("concepts");
            for (Object c : answers){
                String url = (String) c;
                String terms[] = url.split("=");
                String conceptId = terms[terms.length-1];
                if (!(conceptId.startsWith("D")|| conceptId.startsWith("G") ||conceptId.startsWith("h"))){
                    conceptId = "GO:" + conceptId;
                }

                if (terms[terms.length-1].contains("/")){
                    String subterms[] = terms[terms.length-1].split("/");
                    conceptId = subterms[subterms.length-1];
                    if (conceptId.contains("#")){
                        conceptId = conceptId.substring(conceptId.indexOf("#")+1);
                    }
                }
                goldConcepts.add(conceptId);
            }

            double AP = evaluation(body, goldConcepts);
            MAP += AP;
            queryNumber ++;
            if (AP != 0) {
                System.out.print(queryNumber);
                System.out.print(": ");
                System.out.println(body);
                System.out.println(goldConcepts);
                System.out.println(AP);
                hit += 1;
                map_for_hit += AP;
            }
        }
        MAP /= queryNumber;

        map_for_hit /= hit;
        System.out.print("MAP: ");
        System.out.println(MAP);

        System.out.print("hit_map: ");
        System.out.println(map_for_hit);

    }

    public static double evaluation(String query, ArrayList<String> concepts ){
        try {
            ArrayList<String> results = searchIndex(query);
            int length = results.size();
            double relevantNumber = 0;
            double AP = 0;
            for (int i = 0; i < length; i++){
                if (concepts.contains(results.get(i))){
                    relevantNumber += 1;
                    AP += relevantNumber/(i+1);
                    System.out.print(results.get(i));
                    System.out.print("  ");
                    System.out.print(relevantNumber);
                    System.out.print("  ");
                    System.out.print(i+1);
                    System.out.print("  ");
                }
            }
            if (relevantNumber != 0){
                AP /= relevantNumber;
            }
            return AP;
        }catch (Exception e){
            e.printStackTrace();
            return 0.0;
        }
    }

    public static void createIndex() throws CorruptIndexException, LockObtainFailedException, IOException {
        Analyzer analyzer = new StandardAnalyzer();
        Directory directory = FSDirectory.open(new File(INDEX_DIRECTORY).toPath());
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        IndexWriter indexWriter = new IndexWriter(directory, config);

        File dir = new File(FILES_TO_INDEX_DIRECTORY);
        File[] files = dir.listFiles();
        for (File file : files) {
            if (file.getName().compareTo(".DS_Store") == 0){continue;}

            BufferedReader reader = new BufferedReader(new FileReader(file));

            String line = null;
            while ((line = reader.readLine()) != null){
                Document document = new Document();
                String []terms = line.split("@#");
                if (terms.length != 4){
                    System.out.println("Error data format");
                    return;
                }
                document.add(new Field(FIELD_ID, terms[0], TextField.TYPE_STORED));
                if (terms[1].compareTo("null") != 0){
                    document.add(new Field(FIELD_NAME, terms[1].substring(4), TextField.TYPE_STORED));
                }
                if (terms[2].compareTo("null") != 0){
                    document.add(new Field(FIELD_DEF, terms[2].substring(4), TextField.TYPE_STORED));
                }
                if (terms[3].compareTo("null") != 0){
                    document.add(new Field(FIELD_SYN, terms[3].substring(4), TextField.TYPE_STORED));
                }
                indexWriter.addDocument(document);
            }
        }
        indexWriter.close();
    }

    public static ArrayList searchIndex(String searchString) throws IOException, ParseException {

        ArrayList results = new ArrayList();

        //System.out.println("Searching for '" + searchString + "'");
        Directory directory = FSDirectory.open(new File(INDEX_DIRECTORY).toPath());
        DirectoryReader indexReader = DirectoryReader.open(directory);
        IndexSearcher indexSearcher = new IndexSearcher(indexReader);
        QueryParser queryParser;
        Analyzer analyzer = new StandardAnalyzer();

        queryParser = new QueryParser(FIELD_DEF, analyzer);


        Query query = queryParser.parse(searchString);
        ScoreDoc[] hits = indexSearcher.search(query, null, 10).scoreDocs;
        //System.out.println("Number of hits: " + hits.length);
        for (int i = 0; i < hits.length; i ++){
            Document hitDoc = indexSearcher.doc(hits[i].doc);
            //System.out.println("This is the text to be indexed: " + hitDoc.get(FIELD_ID));
            results.add(hitDoc.get(FIELD_ID));
        }
        return results;
    }

}