def parse_obo(filename, outputfilename):
	file = open(filename, "r")
	lines = file.readlines()
	start = 0
	output = "id@#$$#@name@#$$#@def@#$$#@synonym\n"

	#find the first line of concept
	for i in range(len(lines)):
		if lines[i].strip() == "[Term]":
			start = i
			break

	#precess each term
	while start < len(lines):
		term = dict()
		end = start+1
		while end < len(lines) and lines[end].strip() != "[Term]":
			end += 1
		for i in range(start+1, end):
			try:
				index, content = lines[i].strip().split(": ")
				if index == "id" or index == "def" or index == "name" or index == "synonym":
					term[index] = content
			except:
				print lines[i]
		if "id" in term:
			output += term["id"]
			if "name" in term:
				output += "@#$$#@" + term["name"]
			else:
				output += "@#$$#@null"
			if "def" in term:
				output += "@#$$#@" + term["def"]
			else:
				output += "@#$$#@null"
			if "synonym" in term:
				output += "@#$$#@" + term["synonym"]
			else:
				output += "@#$$#@null"
			output += "\n"
			print term["id"]
		start = end
	file.close()

	outputfile = open(outputfilename, "w")
	outputfile.write(output)
	outputfile.close()

parse_obo("go.obo.txt", "go_parse.csv")
#parse_obo("doid.obo.txt", "doid_parse.csv")
