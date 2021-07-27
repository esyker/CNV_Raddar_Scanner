# File to generate several test files to instrument the code

python write_scriptTest.py "C:\cnv\cnv\cnv-project-instrumented"

## Arguments of write_scriptTest.py
# Argv[1]
server   // to generate queries for the WebServer - the user is asked for the IP
solver   // to generate queries for the solver
# Argv[2]
none: the file is generated in the working folder
path: if it exists the file is generated in the path defined
# Argv[3]
Max number of links wanted


// Commands to run the instrumented code... (IN LINUX FOLDER ~/cnv/cnv-project
// This way the output that would be written in the shell is saved in file output.txt
cd ../cnv-project-instrumented
. scriptTest.sh &> output.txt
