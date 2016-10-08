import json
from Config import *
from stop_words import get_stop_words
from nltk import pos_tag
from math import pow
import numpy as np

__author__ = 'Shimin Wang'
__andrewID__ = 'shiminw'


with open(INTERMEDIDA_DATA_FOR_VIZ_SAMPLE_DATA + "rawqueries.json") as data_file:
    rawqueries = json.load(data_file)
with open(INTERMEDIDA_DATA_FOR_VIZ_SAMPLE_DATA + "queries.json") as data_file:
    queries = json.load(data_file)
with open(INTERMEDIDA_DATA_FOR_VIZ_SAMPLE_DATA + "index.json") as data_file:
    index = json.load(data_file)

stop_words_en = get_stop_words('en')
stop_words_fr = get_stop_words('french')
stop_words_sp = get_stop_words('spanish')
stop_words_gr = get_stop_words('german')

C = index["C"]
N = len(queries)
index = index["index"]
queriesLen = [sum(q.values()) for q in queries]


def IndriDistance(q1, l1, q2, l2, index, mu=0, lamda=0.4):
    if l1 > l2:
        return IndriDistance(q2, l2, q1, l1, index, mu, lamda)

    score = 1
    for term in q1:
        if term in q2:
            tf = q2[term]
        else:
            tf = 0
        mle = index[term]["tf"] * 1.0 / C
        score *= pow((1-lamda) * (tf + mu * mle) / (l2 + mu) + lamda * mle, q1[term])

    return pow(score, 1.0/l2)

def linearMap(domain, range, x):
    return range[0] + (range[1]-range[0]) * (x-domain[0]) / (domain[1]-domain[0])



matrix = [[0 for i in queries] for q in queries]
l = len(queries)
for i in range(l):
    for j in range(i, l):
        value = IndriDistance(queries[i], queriesLen[i], queries[j], queriesLen[j], index)
        matrix[i][j] = matrix[j][i] = value

U, S, V = np.linalg.svd(matrix)


topicnum = 5
S = S[:topicnum]
topics = [[] for i in range(topicnum)]

globalMaxTopicCorrelation = -1e30
globalMaxQuesDis = -1e30


nodes = []
links = []
indexMapping = {}


for i in range(len(U)):
    q = U[i]
    topicIdx = 0
    maxTopicCorrelation = q[0] * S[0]
    for j in range(1, topicnum):
        topicCorrelation = q[j] * S[j]
        if topicCorrelation > maxTopicCorrelation:
            maxTopicCorrelation = topicCorrelation
            topicIdx = j

    if maxTopicCorrelation > globalMaxTopicCorrelation:
        globalMaxTopicCorrelation = maxTopicCorrelation

    links.append({"source": i, "target": N+topicIdx,
                  "value": int(linearMap([TOPIC_DISTANCE_THREHOLD, globalMaxTopicCorrelation], [1, 20], maxTopicCorrelation))})
    nodes.append({"name": rawqueries[i], "group": topicIdx})
    topics[topicIdx].append(i)


for topicIdx in range(topicnum):
    nodes.append({"name": "concept" + str(topicIdx), "group": topicIdx})

quesDis = [[] for i in range(topicnum)]
for topicIdx in range(topicnum):
    qlist = topics[topicIdx]
    qlistlen = len(qlist)
    for i in range(qlistlen):
        for j in range(i + 1, qlistlen):
            q1idx = qlist[i]
            q2idx = qlist[j]
            q1 = queries[q1idx]
            q2 = queries[q2idx]
            qdis = IndriDistance(q1, queriesLen[q1idx], q2, queriesLen[q2idx], index)
            if qdis <= QUESTION_DISTANCE_THREHOLD:
                continue
            if qdis > globalMaxQuesDis:
                globalMaxQuesDis = qdis

            quesDis[topicIdx].append([q1idx, q2idx, qdis])


# cosntruct the json for visualization
for topic in quesDis:
    for q in topic:
        links.append({"source": q[0], "target": q[1],
                      "value": int(linearMap([QUESTION_DISTANCE_THREHOLD, globalMaxQuesDis], [1, 20], q[2]))})

print "link num =", len(links)

force_directed = {"nodes": nodes, "links": links}
with open("force_directed.json", "w") as f:
    json.dump(force_directed, f)
