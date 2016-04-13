__author__ = 'Shimin Wang'
__andrewID__ = 'shiminw'

# an metadata snalyser to extract metadata from the Yahoo Answer dataset
# we want to get:
# 1. The total number of questions and answers in this dataset
# 2. The number of questions and answers in each category and sub-category

import xml.etree.cElementTree as ET
from Config import *
import json
import time

# parsing parameter
startQuestionNum = 1
endQuestionNum = 4500000
parsingBegun = False

# metadata we need to get
questionNum = 0 # expect 4483032, actual 4483031
answerNum = 0

targetCatName = "Buenos Aires"

sampleData = {}
# { "Education & Reference" :
#                 [{
#                     "question": "Why are yawns contagious?",
#                     "answernum": 2
#                 }]
#               }

context = ET.iterparse(DATA_PATH, events=("start", "end"))

# turn it into an iterator
context = iter(context)
catAppear = False
currentCat = ""
thisQuestionAnswerNum = 0

start = time.time()
for event, elem in context:
    tag = elem.tag
    value = elem.text
    if parsingBegun and value:
        value = value.encode('utf-8').strip()

    # this is a start of a tag
    if event == 'start' :
        if tag == "vespaadd" :
            thisQuestionAnswerNum = 1
            if questionNum == startQuestionNum - 1:
                parsingBegun = True
        elif parsingBegun:
            if tag == "cat":
                currentCat = value
                if currentCat == targetCatName:
                    catAppear = True

            elif tag == "subject":
                currentQuestion = value

            elif tag == "maincat":
                if catAppear and currentCat:
                    maincat = value
                    if maincat not in sampleData:
                        sampleData[maincat] = []
                    currentObj = {"question": currentQuestion}

            elif tag == "answer_item":
                thisQuestionAnswerNum += 1

    # this is a end of a tag
    if event == 'end':
        if tag =='vespaadd':
            questionNum += 1
            answerNum += thisQuestionAnswerNum

            if catAppear and maincat:
                print currentObj["question"]
                currentObj["answerNum"] = thisQuestionAnswerNum
                sampleData[maincat].append(currentObj)

            catAppear = False
            # stop parsing when we finish parsing the needed partial
            if questionNum == endQuestionNum:
                break

    elem.clear()

print "finished in ", time.time() - start, "s. "
print questionNum - startQuestionNum, " questions parsed"

f = open(SAMPLE_DATA_PATH_PREFIX + "sample.json", "w")
f.write(json.dumps(sampleData))
f.close()

