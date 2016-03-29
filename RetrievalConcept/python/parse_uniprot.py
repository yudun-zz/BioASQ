def parse_uniprot(inputfilename, outputfilename):
	file = open(inputfilename, "r")
	lines = file.readlines()
	start = 0
	output = "id@#$$#@name@#$$#@def@#$$#@synonym\n"
	while start < len(lines):
		term = dict()
		end = start + 1
		while end < len(lines) and lines[end].strip() != "//":
			end += 1
		for i in range(start+1, end):
			try:
				tokens = lines[i].strip().split()
				if tokens[0] == "ID":
					term[tokens[0]] = tokens[1]
				if tokens[0] == "GN":
					if "=" in tokens[1]:
						label, tokens[1] = tokens[1].split("=")
					if " OR " in tokens[1]:
						print lines[i]
						term[tokens[0]] = tokens[1].split(" OR ")[0]
						term["syn"] = ",".join(tokens[1].split(" OR ")[1:])
						print term[tokens[0]], term["syn"]
					elif " AND " in tokens[1]:
						print lines[i]
						term[tokens[0]] = tokens[1].split(" AND ")[0]
                                                term["syn"] = ",".join(tokens[1].split(" AND ")[1:])
						print term[tokens[0]], term["syn"]
					else:
						term[tokens[0]] = tokens[1]
				if tokens[0] == "DE":
					term[tokens[0]] = " ".join(tokens[1:])
			except:
				print lines[i]
				print start, "&& ", end
		if "ID" in term:
			output += term["ID"]
                        if "GN" in term:
                                output += "@#$$#@" + term["GN"]
                        else:
                                output += "@#$$#@null"
                        if "DE" in term:
                                output += "@#$$#@" + term["DE"]
                        else:
                                output += "@#$$#@null"
			
                        output += "@#$$#@null\n"
                start = end
        file.close()

        outputfile = open(outputfilename, "w")
        outputfile.write(output)
        outputfile.close()

parse_uniprot("uniprot_sprot.dat", "uniprot_parse.csv")
		
