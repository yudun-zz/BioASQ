__author__ = 'Shimin Wang'
__andrewID__ = 'shiminw'

import time
import json
from Config import *
from stop_words import get_stop_words
from nltk.tokenize import RegexpTokenizer
from stemming.porter2 import stem
from math import pow
import numpy as np


stop_words_en = get_stop_words('en')
stop_words_fr = get_stop_words('french')
stop_words_sp = get_stop_words('spanish')
stop_words_gr = get_stop_words('german')

my_stop_words = stop_words_en + stop_words_sp
tokenizer = RegexpTokenizer(r'\w+')

class VisDataGeneratorLSI:
    def IndriScore(self, q1, l1, q2, l2, index, mu=0, lamda=0.4):
        if l1 > l2:
            return self.IndriScore(q2, l2, q1, l1, index, mu, lamda)

        score = 1
        for term in q1:
            tf = q2.get(term, 0)
            if term in index:
                mle = index[term]["tf"] * 1.0 / self.C
            else:
                mle = 0
            score *= pow((1 - lamda) * (tf + mu * mle) / (l2 + mu) + lamda * mle, q1[term])

        return pow(score, 1.0 / l2)

    def linearMap(self, domain, range, x):
        if range[1] > range[0]:
            return range[0] + (range[1] - range[0]) * (x - domain[0]) / (domain[1] - domain[0])
        else:
            return range[0] - (range[0] - range[1]) * (x - domain[0]) / (domain[1] - domain[0])

    def processRawQuestion(self, q):
        question = tokenizer.tokenize(q)
        stemResult = [stem(w).lower() for w in question if w not in my_stop_words]
        return reduce(lambda d, c: d.update([(c, d.get(c, 0) + 1)]) or d, stemResult, {})

    def processRawQuestions(self):
        res = []
        for q in self.raw_questions:
            res.append(self.processRawQuestion(q["question"]))
        return res

    def __init__(self, query, raw_questions, questions_index):
        # load all questions of this subcat to memory
        self.raw_questions = raw_questions
        self.questions = self.processRawQuestions()
        self.raw_query = query
        self.query = self.processRawQuestion(self.raw_query)
        self.query_len = sum(self.query.values())
        self.questions_index = questions_index
        self.C = self.questions_index["C"]

    def getVisData(self):
        C = self.questions_index["C"]
        N = len(self.raw_questions)
        index = self.questions_index["index"]
        questionsLen = [sum(q.values()) for q in self.questions]
        questions = self.questions
        raw_questions = self.raw_questions

        matrix = [[0 for i in raw_questions] for q in raw_questions]

        for i in range(N):
            for j in range(i, N):
                value = self.IndriScore(questions[i], questionsLen[i], questions[j], questionsLen[j], index)
                matrix[i][j] = matrix[j][i] = value

        U, S, V = np.linalg.svd(matrix)

        S = S[:TOPIC_NUM]
        topics = [[] for i in range(TOPIC_NUM)]

        globalMaxTopicCorrelation = -1e30
        globalMaxQuesDis = -1e30

        nodes = [[] for i in range(TOPIC_NUM)]
        links = [[] for i in range(TOPIC_NUM)]
        maxScoreWithQuery = [-1e30 for i in range(TOPIC_NUM)]

        topicCorrelations = {}
        for i in range(len(U)):
            q = U[i]
            topicIdx = 0
            maxTopicCorrelation = q[0] * S[0]
            for j in range(1, TOPIC_NUM):
                topicCorrelation = q[j] * S[j]
                if topicCorrelation > maxTopicCorrelation:
                    maxTopicCorrelation = topicCorrelation
                    topicIdx = j

            topicCorrelations[i] = maxTopicCorrelation
            if maxTopicCorrelation > globalMaxTopicCorrelation:
                globalMaxTopicCorrelation = maxTopicCorrelation

            if maxTopicCorrelation >= TOPIC_CORRELATION_THREHOLD:
                links[topicIdx].append({"source": i, "target": N + topicIdx})

                scoreWithQuery = self.IndriScore(questions[i], questionsLen[i], self.query, self.query_len, index)
                nodes[topicIdx].append({"name": raw_questions[i]["question"], "group": topicIdx,
                                        "answerNum": raw_questions[i]["answerNum"],
                                        "scoreWithQuery": scoreWithQuery})
                if scoreWithQuery > maxScoreWithQuery[topicIdx]:
                    maxScoreWithQuery[topicIdx] = scoreWithQuery
                topics[topicIdx].append(i)

        # calculate the distances of each question to the topic node in each group
        for topicIdx in range(TOPIC_NUM):
            for link in links[topicIdx]:
                link["value"] = int(self.linearMap([TOPIC_CORRELATION_THREHOLD, globalMaxTopicCorrelation], [20, 1],
                                                   topicCorrelations[link["source"]]))

        for topicIdx in range(TOPIC_NUM):
            nodes[topicIdx].append({"name": "group" + str(topicIdx), "group": topicIdx, "answerNum": 0, "scoreWithQuery": 0})

        # formalize each node in each topic
        # indexMapping[topicIdx] map the real_id to formalized_id
        indexMapping = [{} for i in range(TOPIC_NUM)]
        for topicIdx in range(TOPIC_NUM):
            l = len(nodes[topicIdx])
            indexMapping[topicIdx][N + topicIdx] = l - 1
            mappedIdx = 0
            for link in links[topicIdx]:
                indexMapping[topicIdx][link["source"]] = mappedIdx
                link["source"] = mappedIdx
                link["target"] = l - 1
                mappedIdx += 1


        quesDis = [[] for i in range(TOPIC_NUM)]
        for topicIdx in range(TOPIC_NUM):
            qlist = topics[topicIdx]
            qlistlen = len(qlist)
            for i in range(qlistlen):
                for j in range(i + 1, qlistlen):
                    q1idx = qlist[i]
                    q2idx = qlist[j]
                    qdis = matrix[q1idx][q2idx]
                    if qdis < QUESTION_SCORE_THREHOLD:
                        continue
                    if qdis > globalMaxQuesDis:
                        globalMaxQuesDis = qdis

                    quesDis[topicIdx].append([q1idx, q2idx, qdis])

        # cosntruct the json for visualization
        for topicIdx in range(TOPIC_NUM):
                topic = quesDis[topicIdx]
                for q in topic:
                    links[topicIdx].append({"source": indexMapping[topicIdx][q[0]],
                                            "target": indexMapping[topicIdx][q[1]],
                                  "value": int(self.linearMap([QUESTION_SCORE_THREHOLD, globalMaxQuesDis], [20, 1],
                                                   q[2])) })


        print "link num =", sum([len(link) for link in links])

        force_directed_json = [{"nodes": nodes[i], "links": links[i], "maxScoreWithQuery": maxScoreWithQuery[i]}
                               for i in range(TOPIC_NUM) if maxScoreWithQuery[i] > 0]
        return force_directed_json
