def parse_Chem(filename, outputfilename):
	file = open(filename, "r")
	lines = file.readlines()
	start = 0
	output = "id@#$$#@name@#$$#@def@#$$#@synonym\n"
	
	start = 37
	while start < len(lines):
		concept = dict()
		end = start + 1
		while end < len(lines) and lines[end].strip() != "--":
			end += 1
		for i in range(start + 1, end):
			try:
				terms = lines[i].split()
				if terms[0] == "ID" or terms[0] == "DF" or terms[0] == "NA":
					concept[terms[0]] = lines[i][3:].strip()
				if terms[0] == "TM":
					if "TM" not in concept:
						concept["TM"] = ""
					index = lines[i].find("@")
					if index != -1:
						concept["TM"] += lines[i][3:index].strip() + ";"
					else:
						concept["TM"] += lines[i][3:].strip() + ";"
			except:
				print lines[i]
		if "ID" in concept:
			output += concept["ID"]
			if "NA" in concept:
				output += "@#$$#@" + concept["NA"]
			else:
				output += "@#$$#@null"
			if "DF" in concept:
				output += "@#$$#@" + concept["DF"]
			else:
				output += "@#$$#@null"
			if "TM" in concept:
				output += "@#$$#@" + concept["TM"]
			else:
				output += "@#$$#@null"
			output += "\n"
			print concept["ID"]
		start = end
	file.close()
	outputfile = open(outputfilename, "w")
	outputfile.write(output)
	outputfile.close()

parse_Chem("ChemlistV1_2.ontology", "Chem_parse.csv")
