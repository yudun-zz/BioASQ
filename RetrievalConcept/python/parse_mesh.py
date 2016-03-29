def parse_mesh(filename, outputfilename):
	file = open(filename, "r")
	lines = file.readlines()
	start = 0
	output = "id@#$$#@name@#$$#@def@#$$#@synonym\n"
	
	while start < len(lines):
		concept = dict()
		end = start + 1
		while end < len(lines) and lines[end].strip() != "*NEWRECORD":
			end += 1
		for i in range(start+1, end):
			try:
				terms = lines[i].split()
				if terms == None or len(terms) == 0:
					continue
				if terms[0] == "UI" or terms[0] == "MH" or terms[0] == "MS":
					concept[terms[0]] = lines[i][5:].strip()
				if terms[0] == "ENTRY":
					if "ENTRY" not in concept:
						concept["ENTRY"] = ""
					index = lines[i].find("@")
					if index != -1:
						concept["ENTRY"] += lines[i][8:index].strip() + ";"
					else:
						concept["ENTRY"] += lines[i][8:].strip() + ";"
			except:
				print lines[i]

		if "UI" in concept:
			output += concept["UI"]
			if "MH" in concept:
				output += "@#$$#@" + concept["MH"]
			else:
				output += "@#$$#@null"
			if "MS" in concept:
				output += "@#$$#@" + concept["MS"]
                        else:
                                output += "@#$$#@null"
			if "ENTRY" in concept:
				output += "@#$$#@" + concept["ENTRY"]
                        else:
                                output += "@#$$#@null"
			output += "\n"
			print concept["UI"]
		start = end
	file.close()
	outputfile = open(outputfilename, "w")
	outputfile.write(output)
	outputfile.close()

parse_mesh("d2016.bin", "d2016_parse.csv")
