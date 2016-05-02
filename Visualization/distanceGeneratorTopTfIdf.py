import json
from Config import *
from stop_words import get_stop_words
from nltk import pos_tag
from math import pow

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

sorted_index = sorted(index.items(), key=lambda value: value[1]["tf-idf"], reverse=True)
topics = []
for item in sorted_index:
    if item[0] not in stop_words_en and item[0] not in stop_words_fr \
            and item[0] not in stop_words_sp and item[0] not in stop_words_gr:
        topics.append(item[0])
topics = pos_tag(topics)
nountopic = []
for topic, pos in topics:
    if pos == 'NN' or pos == 'NNP' or pos == 'NNS' or pos == 'NNPS':
        nountopic.append(topic)

topics = nountopic[:10]
topicnum = len(topics)

# initialize distance json
distance = []
for i in range(topicnum):
    distance.append({"idx": N+i, "name": topics[i], "topicdis": [], "quesdis": []})

maxdis = 0
nodes = []
indexMapping = {}
realidx = 0
for i in range(N):
    question = queries[i]
    topicIdx = 0
    maxTopicDis = 0
    for ti in range(topicnum):
        topic = topics[ti]
        tdis = IndriDistance({topic: 1}, 1, question, queriesLen[i], index)
        if tdis > maxTopicDis:
            maxTopicDis = tdis
            topicIdx = ti
        if tdis > maxdis:
            maxdis = tdis

    if maxTopicDis <= TOPIC_DISTANCE_THREHOLD:
        continue
    indexMapping[i] = realidx
    realidx += 1

    nodes.append({"name": rawqueries[i], "group": topicIdx})
    distance[topicIdx]["topicdis"].append([distance[topicIdx]["idx"], i, maxTopicDis])


for ti in range(topicnum):
    topic = distance[ti]
    nodes.append({"name": topics[ti], "group": ti})
    indexMapping[N+ti] = realidx
    realidx += 1

    qnum = len(topic["topicdis"])
    for i in range(qnum):
        for j in range(i+1, qnum):
            q1idx = topic["topicdis"][i][1]
            q2idx = topic["topicdis"][j][1]
            q1 = queries[q1idx]
            q2 = queries[q2idx]
            qdis = IndriDistance(q1, queriesLen[q1idx], q2, queriesLen[q2idx], index)
            if qdis <= QUESTION_DISTANCE_THREHOLD:
                continue
            if qdis > maxdis:
                maxdis = qdis
            topic["quesdis"].append([q1idx, q2idx, qdis])

    print topic["name"], len(topic["topicdis"])

print "maxdis=", maxdis


# cosntruct the json for visualization
links = []
for topic in distance:
    for q in topic["topicdis"]:
        links.append({"source": indexMapping[q[0]], "target": indexMapping[q[1]],
                      "value": int(linearMap([TOPIC_DISTANCE_THREHOLD, maxdis], [1, 20], q[2]))})
    for q in topic["quesdis"]:
        links.append({"source": indexMapping[q[0]], "target": indexMapping[q[1]],
                      "value": int(linearMap([QUESTION_DISTANCE_THREHOLD, maxdis], [1, 20], q[2]))})

force_directed = {"nodes": nodes, "links": links}
with open("force_directed.json", "w") as f:
    json.dump(force_directed, f)
