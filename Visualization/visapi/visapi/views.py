from django.http import HttpResponse


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
def startVisSubGraph(request):
    # TODO: the main entry for the slave to initialize a vis of a subgraph of a (q, subcategory) pair
    return HttpResponse("Hi")




#
# API for classifier
#
def beginClassify(request):
    # TODO: begin the classification of a question
    # return top 3 most related subcategory
    return HttpResponse("Hi")