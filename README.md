# JavaClassVersionChecker
Command line tool to check the Java class version (The Java version it is compiled for)

Minimum Java version required to run this: 1.5

<pre>
D:\--\Desktop>java -jar java-class-version-checker-0.1-alpha.jar
Usage: java -jar class-version-checker-<version>.jar [-e] [-v] Path1 Path2 ..
-e Comma separated list of file extensions. e.g. jar,war,class,..
   default: scans all the jar files in the provided paths
-v Verbosity. Valid values are 1(default) and 2
   1: Scans all the class files in an archive. Prints stats
      Outputs version, no. of classes, jar file and other versions of class files found in the archive
      -gc(default) Group by container e.g. folder, archive
      -gv Group by Java version
   2: Scans all class files in an archive and lists all the files in output
Path can be any folder or file which matches the provided extension
e.g. 1. java jar-class-target-java-version-checker-.jar Folder1 Folder2
     2. java jar-class-target-java-version-checker-.jar -e war,ear xyz.war abc.ear Folder2
     3. java jar-class-target-java-version-checker-.jar abc.jar
     4. java jar-class-target-java-version-checker-.jar -e class,jar abc.jar Xyz.class Folder
Note: Except 'class' all other files (with matching extension e.g. war,zip,ear) will be considered as a compressed zip files
</pre>

![javaclassversionchecker-0 1-alpha](https://cloud.githubusercontent.com/assets/4668696/6602488/f6e67170-c841-11e4-95bc-9efc7ed2d0c6.png)
