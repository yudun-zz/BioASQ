from nltk.tokenize import RegexpTokenizer
from stemming.porter2 import stem
import json
from Config import *
from math import log

__author__ = 'Shimin Wang'
__andrewID__ = 'shiminw'

tokenizer = RegexpTokenizer(r'\w+')

rawqueries = []
queris = []
index = {}

with open(SAMPLE_DATA_PATH_PREFIX + "buenos_aires.json") as data_file:
    rawdata = json.load(data_file)

for cat in rawdata:
    for q in rawdata[cat]:
        if q["question"] is not None:
            rawqueries.append(q["question"])
            question = tokenizer.tokenize(q["question"])
            stemResult = [stem(w).lower() for w in question]
            queris.append(reduce( lambda d, c: d.update([(c, d.get(c, 0)+1)]) or d, stemResult, {}))

C = 0
qid = 0
for question in queris:
    for term in question:
        fre = question[term]
        C += fre
        if term not in index:
            index[term] = {"doclist": {qid: fre}, "tf": fre, "df": 1}
        else:
            index[term]["doclist"][qid] = fre
            index[term]["tf"] += fre
            index[term]["df"] += 1
    qid += 1

N = len(queris)

def tfidf(term):
    return term["tf"] * log(1.0 * (N - term["df"]) / term["df"])

for term in index:
    index[term]["tf-idf"] = tfidf(index[term])

index = {"C": C, "index": index}

f = open(INTERMEDIDA_DATA_FOR_VIZ_SAMPLE_DATA + "rawqueries.json", "w")
f.write(json.dumps(rawqueries))
f.close()

f = open(INTERMEDIDA_DATA_FOR_VIZ_SAMPLE_DATA + "queries.json", "w")
f.write(json.dumps(queris))
f.close()

f = open(INTERMEDIDA_DATA_FOR_VIZ_SAMPLE_DATA + "index.json", "w")
f.write(json.dumps(index))
f.close()
