# JavaClassVersionChecker
Command line tool to check the Java class version (The Java version it is compiled for)

Minimum Java version required to run this: 1.5

<pre>
user@user ~/Dir/Dir $ java -jar java-class-version-checker-0.3.jar 
Usage: java -jar java-class-version-checker-&lt;version&gt;.jar [-e] [-v] Path1 Path2 ..
-e Comma separated list of file extensions. e.g. jar(default),war,class,.. 
-v Verbosity. Valid values are 1(default) and 2
   1: Prints stats: version, no. of classes, jar file and other versions of class files
      found in the archive/folder
      -gc(default) Group by container e.g. folder, archive
      -gv Group by Java version
   2: Lists all the files with version in the output

Path can be any folder or file which matches the provided extension(s)
e.g. 1. java -jar java-class-version-checker-&lt;version&gt;.jar Folder1WithJars Folder2WithJars
     2. java -jar java-class-version-checker-&lt;version&gt;.jar -e jar,war,ear xyz.war abc.ear Folder2
     3. java -jar java-class-version-checker-&lt;version&gt;.jar abc.jar
     4. java -jar java-class-version-checker-&lt;version&gt;.jar -e class,jar abc.jar Xyz.class
        FolderWithClasses FolderWithJars

Note: Except 'class' all other files (with matching extension e.g. war,zip,ear) will be
      considered as compressed zip files
</pre>

Examples:

<pre>
user@user ~/user/MyApps $ java -jar java-class-version-checker-0.3.jar -gc ../MyApps/java-class-version-checker-0.3.jar 
java-class-version-checker-0.3.jar 1.5(10)  ../MyApps

00s, 1 file, 10 classes
Completed

user@user ~/user/MyApps $ java -jar java-class-version-checker-0.3.jar -gv ../MyApps/java-class-version-checker-0.3.jar 
1.5 java-class-version-checker-0.3.jar ../MyApps

00s, 1 file, 10 classes
Completed

user@user ~/user/MyApps $ java -jar java-class-version-checker-0.3.jar -v 2 ../MyApps/java-class-version-checker-0.3.jar 
1.5 ../MyApps/java-class-version-checker-0.3.jar/raju/javautils/cvc/ProgressUpdater.class
1.5 ../MyApps/java-class-version-checker-0.3.jar/raju/javautils/cvc/MutableInteger.class
1.5 ../MyApps/java-class-version-checker-0.3.jar/raju/javautils/cvc/Version.class
1.5 ../MyApps/java-class-version-checker-0.3.jar/raju/javautils/cvc/Main$1.class
1.5 ../MyApps/java-class-version-checker-0.3.jar/raju/javautils/cvc/Main.class
1.5 ../MyApps/java-class-version-checker-0.3.jar/raju/javautils/cvc/Result$Failure.class
1.5 ../MyApps/java-class-version-checker-0.3.jar/raju/javautils/cvc/Result$Success.class
1.5 ../MyApps/java-class-version-checker-0.3.jar/raju/javautils/cvc/Result.class
1.5 ../MyApps/java-class-version-checker-0.3.jar/raju/javautils/cvc/Scanner$1.class
1.5 ../MyApps/java-class-version-checker-0.3.jar/raju/javautils/cvc/Scanner.class

00s, 1 file, 10 classes
Completed
</pre>
