export _JAVA_OPTIONS="-XX:-UseSplitVerifier "$_JAVA_OPTIONS
export CLASSPATH=/home/ec2-user/aws-java-sdk-1.11.1014/lib/aws-java-sdk-1.11.1014.jar:/home/ec2-user/aws-java-sdk-1.11.1014/third-party/lib/*:/home/ec2-user/RadarScanner_CNV/lib/*:.
cd /home/ec2-user/RadarScanner_CNV/src

eval $(ssh-agent)
ssh-add /home/ec2-user/.ssh/id_ed25519
git fetch origin main
git reset --hard origin/main

java EntryPoint
