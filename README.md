# JavaClassVersionChecker
Command line tool to check the Java class version (The Java version it is compiled for)

Minimum Java version required to run this: 1.5

<pre>
D:\userdata\--\Desktop\MyApps>java -jar java-class-version-checker-0.2-alpha.jar
Usage: java -jar java-class-version-checker-<version>.jar [-e] [-v] Path1 Path2 ..
-e Comma separated list of file extensions. e.g. jar(default),war,class,..
-v Verbosity. Valid values are 1(default) and 2
   1: Prints stats: version, no. of classes, jar file and other versions of class files found in the archive
      -gc(default) Group by container e.g. folder, archive
      -gv Group by Java version
   2: Lists all the files with version in the output
Path can be any folder or file which matches the provided extension(s)
e.g. 1. java -jar java-class-version-checker-<version>.jar Folder1WithJars Folder2WithJars
     2. java -jar java-class-version-checker-<version>.jar -e jar,war,ear xyz.war abc.ear Folder2
     3. java -jar java-class-version-checker-<version>.jar abc.jar
     4. java -jar java-class-version-checker-<version>.jar -e class,jar abc.jar Xyz.class FolderWithClasses FolderWithJars
Note: Except 'class' all other files (with matching extension e.g. war,zip,ear) will be considered as compressed zip files
</pre>

