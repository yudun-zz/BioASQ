from django.http import HttpResponse
import json
from Config import *
from django.views.decorators.csrf import csrf_exempt
from visualizer.Visualizer import Visualizer
import threading

# the preloaded question index
with open(INDEX_PREFIX + "index.json") as data_file:
    question_index = json.load(data_file)

def index(request):
    return HttpResponse("index")

#
# API for masters
#
def getVis(request):
    # TODO: the main entry for the master to initialize a vis job
    return HttpResponse("Hi")

def updateClassificationResult(request):
    # TODO: receive the result of classification and kick off the
    # distribution of sub categories to different slaves
    return HttpResponse("Hi")

def updateSubgraph(request):
    # TODO: called by slave when they finish rendering subgraph
    # when all the subgraphs are collected, it will kick off a combine task
    return HttpResponse("Hi")


#
# API for slaves
#
@csrf_exempt
def startVisSubGraph(request):
    global question_index
    body = json.loads(request.body)
    query = body['query']
    subcat = body['subcat']

    # kick off the async visualization using the questions set and question_index
    visualizer = Visualizer(query, subcat, question_index)
    threading.Thread(target=visualizer.run).start()

    return HttpResponse("ok")


#
# API for classifier
#
def beginClassify(request):
    # TODO: begin the classification of a question
    # return top 3 most related subcategory
    return HttpResponse("Hi")


