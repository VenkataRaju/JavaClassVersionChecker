package raju.javautils.cvc;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class Main
{
	private static void printUsage()
	{
		System.out
				.printf(
				"Usage: java -jar java-class-version-checker-<version>.jar [-e] [-v] Path1 Path2 ..%n"
						+ "-e Comma separated list of file extensions. e.g. jar(default),war,class,.. %n"
						+ "-v Verbosity. Valid values are 1(default) and 2%n"
						+ "   1: Prints stats: version, no. of classes, jar file and other versions of class files found in the archive%n"
						+ "      -gc(default) Group by container e.g. folder, archive%n"
						+ "      -gv Group by Java version%n"
						+ "   2: Lists all the files with version in the output%n"
						+ "Path can be any folder or file which matches the provided extension(s)%n"
						+ "e.g. 1. java -jar java-class-version-checker-<version>.jar Folder1WithJars Folder2WithJars%n"
						+ "     2. java -jar java-class-version-checker-<version>.jar -e jar,war,ear xyz.war abc.ear Folder2%n"
						+ "     3. java -jar java-class-version-checker-<version>.jar abc.jar%n"
						+ "     4. java -jar java-class-version-checker-<version>.jar -e class,jar abc.jar Xyz.class FolderWithClasses FolderWithJars%n"
						+ "Note: Except 'class' all other files (with matching extension e.g. war,zip,ear) will be considered as compressed zip files%n");
	}

	public static void main(String[] args)
	{
		try
		{
			main0(args);
		}
		catch (Exception e)
		{
			System.out.printf("An exception occured: %s%n%n", e.getMessage());
			e.printStackTrace();
		}
	}

	private static void main0(String[] args) throws IOException
	{
		if (args.length == 0)
		{
			printUsage();
			return;
		}

		List<String> params = new ArrayList<String>(Arrays.asList(args));

		String extnsStr = (extnsStr = getParamValue(params, "-e")) == null ? "jar" : extnsStr;
		String[] fileExtnsArr = extnsStr.split(",");
		Set<String> fileExtns = new HashSet<String>();
		for (String fileExtn : fileExtnsArr)
			if ((fileExtn = fileExtn.trim()).length() != 0)
				fileExtns.add(fileExtn);

		String verbosityStr = (verbosityStr = getParamValue(params, "-v")) == null ? "1" : verbosityStr;
		if (!Arrays.asList("1", "2").contains(verbosityStr))
		{
			System.out.printf("ERROR: Invalid value provided for verbosity(-v): %s%n%n", verbosityStr);
			printUsage();
			return;
		}

		int verbosity = Integer.parseInt(verbosityStr);

		boolean groupByContainer = true;
		if (verbosity == 1)
		{
			if (params.remove("-gv"))
				groupByContainer = false;
			else
				params.remove("-gc");
		}

		List<File> inputPathsToScan = new ArrayList<File>();
		for (String inputFileOrFolderStr : params)
			inputPathsToScan.add(new File(inputFileOrFolderStr));

		if (inputPathsToScan.isEmpty())
		{
			System.out.printf("ERROR: No paths are provided%n");
			printUsage();
			return;
		}

		final Scanner scanner = new Scanner(inputPathsToScan, fileExtns);
		FutureTask<Void> scanTask = new FutureTask<Void>(new Runnable()
		{
			public void run()
			{
				scanner.scan();
			}
		}, null);

		ScheduledExecutorService se = Executors.newSingleThreadScheduledExecutor();
		se.scheduleWithFixedDelay(new ProgressUpdater(verbosity, groupByContainer, scanTask, scanner, se), 100, 450, TimeUnit.MILLISECONDS);

		new Thread(scanTask).start();
	}

	/**
	 * If {@code name} is found in {@code params}, its next value will be returned
	 * and both the {@code name} and it's value will be removed from the
	 * {@code params} list.
	 */
	private static String getParamValue(List<String> params, String name)
	{
		for (Iterator<String> it = params.iterator(); it.hasNext();)
		{
			if (it.next().equals(name) && it.hasNext())
			{
				it.remove();
				String value = it.next();
				it.remove();
				return value;
			}
		}

		return null;
	}
}