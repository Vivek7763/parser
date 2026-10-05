import os
import re
import glob

workspace_root = "/Users/GaneshvivekMannam/Desktop/parsing/prowide-iso20022"
archive_dir = "/Users/GaneshvivekMannam/Desktop/parsing/archive_business_area_payments_clearing_and_settlement_06baaee10c"
schemas_dir = os.path.join(workspace_root, "schemas")

messages = {}

def get_or_create(msg_type):
    if msg_type not in messages:
        messages[msg_type] = {
            'message_type': msg_type,
            'namespace': f"urn:iso:std:iso:20022:tech:xsd:{msg_type}",
            'business_area': msg_type.split('.')[0],
            'xsd_available': False,
            'xml_sample': False,
            'java_model': False
        }
    return messages[msg_type]

# 1. Find all generated models
for root, dirs, files in os.walk(workspace_root):
    if "src/generated/java/com/prowidesoftware/swift/model/mx" in root:
        for file in files:
            if file.startswith("Mx") and file.endswith(".java") and len(file) > 10:
                filepath = os.path.join(root, file)
                with open(filepath, 'r', encoding='utf-8') as f:
                    content = f.read()
                    match = re.search(r'NAMESPACE\s*=\s*"urn:iso:std:iso:20022:tech:xsd:([^"]+)"', content)
                    if match:
                        msg_type = match.group(1)
                        get_or_create(msg_type)['java_model'] = True

# 2. Find XML samples
xml_dirs = [os.path.join(workspace_root, "iso20022-core", "src", "test", "resources")]
for d in xml_dirs:
    for root, dirs, files in os.walk(d):
        for file in files:
            if file.endswith(".xml"):
                msg_type = file.replace(".xml", "")
                get_or_create(msg_type)['xml_sample'] = True

# 3. Find XSDs
xsd_dirs = [archive_dir, schemas_dir]
for d in xsd_dirs:
    for root, dirs, files in os.walk(d):
        for file in files:
            if file.endswith(".xsd"):
                msg_type = file.replace(".xsd", "")
                get_or_create(msg_type)['xsd_available'] = True

print(f"| Business Area | Message Type | Namespace | XSD Available? | XML Sample? | Generated Model? | Prowide Parsing? | Schema Validation? | Notes |")
print(f"|---|---|---|---|---|---|---|---|---|")

for msg_type in sorted(messages.keys()):
    m = messages[msg_type]
    ba = m['business_area']
    xsd = "Yes" if m['xsd_available'] else "No"
    xml = "Yes" if m['xml_sample'] else "No"
    model = "Yes" if m['java_model'] else "No"
    prowide = "Yes" if m['java_model'] else "No"
    validation = "Yes" if m['xsd_available'] else "No"
    
    notes = []
    if xsd == "No" and model == "Yes":
        notes.append("Validation degrades to JAXB")
    if model == "No" and xsd == "Yes":
        notes.append("Schema exists but no Prowide model")
    
    print(f"| {ba} | {msg_type} | {m['namespace']} | {xsd} | {xml} | {model} | {prowide} | {validation} | {', '.join(notes)} |")
