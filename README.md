# ClassDiagramExtractor
Automatically extract Class Diagram from Java code
Groups packages together and uses coloring

Notes
- No command line options - clone & run setting source folder in code
- This produces a gv file for use with GraphViz (dot), available here: https://www.graphviz.org/

Example generate image from gv file:
- dot test.gv -Tpng -o test.png

Example optional use of unflatten diagram:
- unflatten -o test2.gv -l 4 -f -c 4 test.gv
