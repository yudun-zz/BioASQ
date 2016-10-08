import com.google.gson.*;

import java.util.HashSet;
import java.util.List;

public class Evaluation {
    private int correct = 0;
    private int totalAnswer = 0;
    private int totalGoldAnswer = 0;
    private int totalQuestion = 0;
    private double totalPresition = 0;

    /**
     * compare the retrieved answer list and the gold answer list
     * @param ans retrieved answer
     * @param goldString gold answer
     * @return True if retrieved answer match the gold answer. False otherwise.
     */
    private boolean compareList(List<String> ans, List<String> goldString) {
        HashSet<String> goldSet = new HashSet<>();
        for (String s : goldString)
            goldSet.add(s.toLowerCase());
        for (String s : ans)
            if (goldSet.contains(s.toLowerCase()))
                return true;
        return false;
    }

    /**
     * evaluation the precision of each question
     * @param question evaluation question
     */
    private void evaluation(Question question) {
        totalQuestion++;
        int count = 0;
        totalAnswer += question.retrieved.answer.size();
        totalGoldAnswer += question.goldStandard.answer.size();
        for (List<String> ans : question.retrieved.answer) {
            for (List<String> goldString : question.goldStandard.answer)
                if (compareList(ans, goldString)) {
                    correct++;
                    count++;
                    break;
                }
        }
        totalPresition += ((double) count) / question.retrieved.answer.size();
    }

    /**
     * parse the json string and extract the question and answer part
     * @param jsonString input json string
     */
    public void run(String jsonString) {
        Gson gson = new GsonBuilder().create();
        Question[] value = gson.fromJson(jsonString, Question[].class);
        for (Question question : value)
            evaluation(question);

    }

    public double getPrecision() {
        return ((double) correct) / totalAnswer;
    }

    public double getRecall() {
        return ((double) correct) / totalGoldAnswer;
    }

    public double getMeanAveragePrecision() {
        return totalPresition / totalQuestion;
    }

    public double getF1Score() {
        return 2 * getPrecision() * getRecall() / (getPrecision() + getRecall());
    }

    public static void main(String[] args) {
        Evaluation evaluation = new Evaluation();
    }
}
