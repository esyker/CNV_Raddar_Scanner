export _JAVA_OPTIONS="-XX:-UseSplitVerifier "$_JAVA_OPTIONS
export CLASSPATH=/home/ec2-user/aws-java-sdk-1.11.1014/lib/aws-java-sdk-1.11.1014.jar:/home/ec2-user/aws-java-sdk-1.11.1014/third-party/lib/*:/home/ec2-user/RadarScanner_CNV/lib/*:.
cd /home/ec2-user/RadarScanner_CNV/src

java worker/StatisticsTool -normal ./pt/ulisboa/tecnico/cnv/original_solver/ ./pt/ulisboa/tecnico/cnv/solver/

javac utils/*.java
javac worker/*.java
javac master/*.java
javac master/cluster/*.java
javac EntryPoint.java
java EntryPoint



