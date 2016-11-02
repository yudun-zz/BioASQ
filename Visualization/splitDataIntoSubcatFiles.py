import json
from Config import *

with open(SAMPLE_DATA_PATH_PREFIX + "chicago.json") as data_file:
    questions = json.load(data_file)

for subcat in questions:
    f = open(HIERACHY_DATA_PATH_PREFIX + subcat+".json", "w")
    f.write(json.dumps(questions[subcat]))
    f.close()
