__author__ = 'Shimin Wang'
__andrewID__ = 'shiminw'


import xml.etree.cElementTree as ET
from Config import *
import json
import time

with open(METADATA_PATH) as data_file:
    data = json.load(data_file)

datalist = []

# Item Sample:
# {
#   "Andalucia": {
#     "maincat": {
#       "Voyage": {
#         "answerNum": 36,
#         "questionNum": 9
#       },
#       "Travel": {
#         "answerNum": 2917,
#         "questionNum": 694
#       }
#     },
#     "answerNum": 2953,
#     "questionNum": 703
#   }
# }

for objkey in data:
    newobj = {"name" : objkey}
    newobj.update(data[objkey])
    datalist.append(newobj)

datalistByQuestionNum = sorted(datalist, key = lambda x : x["questionNum"], reverse = True)
print json.dumps(datalistByQuestionNum[0:10])

datalistByMainCatNum = sorted(datalist, key = lambda x : len(x["maincat"]), reverse = True)
print json.dumps(datalistByMainCatNum[0:10])

