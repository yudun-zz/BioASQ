__author__ = 'Shimin Wang'
__andrewID__ = 'shiminw'

import json
import time

from visDataGenerator.VisDataGeneratorLSI import VisDataGeneratorLSI
from ..Config import *


class Visualizer:
    def __init__(self, query, subcat, questions_index):
        # load all questions of this subcat to memory
        with open(DATA_PATH_PREFIX + subcat + ".json") as data_file:
            self.questions = json.load(data_file)
        self.questions = [q for q in self.questions if q["question"] is not None]
        self.query = query
        self.questions_index = questions_index

    def createVisData(self):
        visDataGenerator = VisDataGeneratorLSI(self.query, self.questions, self.questions_index)
        return visDataGenerator.getVisData()

    def beginVis(self, visData):
        print self.query, len(self.questions)
        print json.dumps(visData[1])
        # TODO: run nodejs module to get the relative position of nodes
        return None

    def notifyMaster(self):
        return None

    def run(self):
        visData = self.createVisData()
        self.beginVis(visData)
        # send finish signal to master node
        self.notifyMaster()