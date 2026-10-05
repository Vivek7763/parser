import os
import json

matrix_file = "/Users/GaneshvivekMannam/Desktop/parsing/prowide-iso20022/matrix.md"
target_file = "/Users/GaneshvivekMannam/.gemini/antigravity-ide/brain/8def3d04-64fb-4069-8aa8-23fc63898c4e/analysis_results.md"

with open(matrix_file, 'r', encoding='utf-8') as f:
    lines = f.readlines()

headers = lines[0:2]
data = lines[2:]

total = len(data)
xsds = sum(1 for line in data if " | Yes |" in line.split("|")[4])
xmls = sum(1 for line in data if " | Yes |" in line.split("|")[5])
models = sum(1 for line in data if " | Yes |" in line.split("|")[6])

summary = f"""
### 23. Repository-Wide ISO Message Support Matrix
We conducted a comprehensive scan across the entire repository and supplied schema archives to determine exactly what is currently covered.

**Summary of findings:**
- **Total Identifiable Message Types:** {total}
- **Prowide Generated Java Models Available:** {models} (This dictates what can be JAXB-parsed)
- **XSD Schemas Available:** {xsds} (This dictates what our `ISOParser` can validate)
- **XML Test Samples Available:** {xmls}

The exact 35 target messages are still undefined, but the matrix below shows the current repository coverage.

<details>
<summary><b>Click here to expand the full 3000-row Support Matrix</b></summary>

"""

with open(target_file, 'a', encoding='utf-8') as f:
    f.write("\n" + summary)
    f.writelines(lines)
    f.write("\n</details>\n")
