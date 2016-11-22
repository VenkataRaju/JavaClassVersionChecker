package raju.javautils.cvc;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class Main
{
	public static void main(String[] args)
	{
		try
		{
			main0(args);
		}
		catch (Exception e)
		{
			System.err.printf("An error occured: %s%n%n", e.getMessage());
			e.printStackTrace();
		}
	}

	private static Void main0(String[] args) throws IOException
	{
		if (args.length == 0)
			return printUsage(null /* No specific error message. Just print the usage */);

		List<String> argsList = new ArrayList<String>(Arrays.asList(args));

		String extnsStr = (extnsStr = argValue(argsList, "-e")) == null ? "jar" : extnsStr;
		String[] fileExtnsArr = extnsStr.split(",");

		Set<String> fileExtns = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);

		for (String fileExtn : fileExtnsArr)
			if ((fileExtn = fileExtn.trim()).length() != 0)
				fileExtns.add(fileExtn);

		String verbosityStr = (verbosityStr = argValue(argsList, "-v")) == null ? "1" : verbosityStr;
		if (!Arrays.asList("1", "2").contains(verbosityStr))
			return printUsage("ERROR: Invalid value provided for verbosity(-v): " + verbosityStr);

		int verbosity = Integer.parseInt(verbosityStr);

		boolean groupByContainer = true;
		if (verbosity == 1)
		{
			if (argsList.remove("-gv"))
				groupByContainer = false;
			else
				argsList.remove("-gc");
		}
		else if (argsList.contains("-gv") || argsList.contains("-gc"))
		{
			return printUsage("ERROR: -gv/-gc flags are applicable only for the verbocity level 1");
		}

		List<File> inputPathsToScan = new ArrayList<File>();
		for (String inputFileOrFolderStr : argsList)
			inputPathsToScan.add(new File(inputFileOrFolderStr));

		if (inputPathsToScan.isEmpty())
			return printUsage("ERROR: No paths are provided");

		final Scanner scanner = new Scanner(inputPathsToScan, fileExtns);
		FutureTask<Void> scanTask = new FutureTask<Void>(new Runnable()
		{
			public void run()
			{
				scanner.scan();
			}
		}, null);

		ScheduledExecutorService se = Executors.newSingleThreadScheduledExecutor();
		se.scheduleWithFixedDelay(new ProgressUpdater(verbosity, groupByContainer, scanner, scanTask, se), 100, 450,
				TimeUnit.MILLISECONDS);

		new Thread(scanTask).start();

		return null;
	}

	/**
	 * If {@code propName} is found in {@code args}, its next value will be returned
	 * and both the {@code propName} and it's value will be removed from the
	 * {@code args} list.
	 */
	private static String argValue(List<String> args, String propName)
	{
		int index = args.indexOf(propName);
		if (index != -1 && index + 1 != args.size())
		{
			args.remove(index);
			return args.remove(index);
		}
		return null;
	}

	/**
	 * @param optionalErrorMessage
	 *          May be null. This message will be printed first in the error console (if not null),
	 *          then the usage information will be printed.
	 * @return null. Always. As a convenience to print the message and to return
	 *         from the method from which this method is called
	 */
	private static Void printUsage(String optionalErrorMessage)
	{
		if (optionalErrorMessage != null)
			System.err.printf("%s%n%n", optionalErrorMessage);

		System.out.printf(
				"Usage: java -jar java-class-version-checker-<version>.jar [-e] [-v] Path1 Path2 ..%n"
						+ "-e Comma separated list of file extensions. e.g. jar(default),war,class,.. %n"
						+ "-v Verbosity. Valid values are 1(default) and 2%n"
						+ "   1: Prints stats: version, no. of classes, jar file and other versions of class files%n"
						+ "      found in the archive/folder%n"
						+ "      -gc(default) Group by container e.g. folder, archive%n"
						+ "      -gv Group by Java version%n"
						+ "   2: Lists all the files with version in the output%n%n"
						+ "Path can be any folder or file which matches the provided extension(s)%n"
						+ "e.g. 1. java -jar java-class-version-checker-<version>.jar Folder1WithJars Folder2WithJars%n"
						+ "     2. java -jar java-class-version-checker-<version>.jar -e jar,war,ear xyz.war abc.ear Folder2%n"
						+ "     3. java -jar java-class-version-checker-<version>.jar abc.jar%n"
						+ "     4. java -jar java-class-version-checker-<version>.jar -e class,jar abc.jar Xyz.class%n"
						+ "        FolderWithClasses FolderWithJars%n%n"
						+ "Note: Except 'class' all other files (with matching extension e.g. war,zip,ear) will be%n"
						+ "      considered as compressed zip files%n");

		return null;
	}
}