const fs = require('fs');
let code = fs.readFileSync('backend/src/test/java/com/portscape/scan/ScanServiceTest.java', 'utf8');

code = code.replace(/when\(executor\.execute\(anyList\(\), any\(\)\)\)\n(.*)\.thenReturn\("discovery-xml", "version-xml"\);/g, 
  'when(executor.execute(anyList(), any())).thenReturn("discovery-xml");\n        when(executor.execute(anyList())).thenReturn("version-xml");');
  
// specifically fix line 207:
code = code.replace('when(executor.execute(anyList(), any())).thenReturn("discovery-xml", "version-xml");', 
  'when(executor.execute(anyList(), any())).thenReturn("discovery-xml");\n        when(executor.execute(anyList())).thenReturn("version-xml");');

fs.writeFileSync('backend/src/test/java/com/portscape/scan/ScanServiceTest.java', code);
