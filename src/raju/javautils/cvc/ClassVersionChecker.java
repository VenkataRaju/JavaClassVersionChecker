package raju.javautils.cvc;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import raju.javautils.cvc.Result.Success;

public final class ClassVersionChecker
{
	private static final int MILLIS_PER_SEC = 1000, MILLIS_PER_MIN = 60000, MILLIS_PER_HOUR = 3600000;
	private static final int MAX_SPACE_FOR_JAR_FILE_NAME = 35;
	private static final int SPACE_FOR_VERSION = 8;

	private static void printUsage()
	{
		System.out
				.println(
				"Usage: java -jar java-class-version-checker-<version>.jar [-e] [-v] Path1 Path2 ..\n"
						+ "-e Comma separated list of file extensions. e.g. jar,war,class,.. \n"
						+ "   default: scans all the jar files in the provided paths\n"
						+ "-v Verbosity. Valid values are 1(default) and 2\n"
						+ "   1: Scans all the class files in an archive. Prints stats\n"
						+ "      Outputs version, no. of classes, jar file and other versions of class files found in the archive\n"
						+ "      -gc(default) Group by container e.g. folder, archive \n"
						+ "      -gv Group by Java version\n"
						+ "   2: Scans all class files in an archive and lists all the files in output\n"
						+ "Path can be any folder or file which matches the provided extension\n"
						+ "e.g. 1. java -jar java-class-version-checker-<version>.jar Folder1 Folder2\n"
						+ "     2. java -jar java-class-version-checker-<version>.jar -e war,ear xyz.war abc.ear Folder2\n"
						+ "     3. java -jar java-class-version-checker-<version>.jar abc.jar\n"
						+ "     4. java -jar java-class-version-checker-<version>.jar -e class,jar abc.jar Xyz.class Folder\n"
						+ "Note: Except 'class' all other files (with matching extension e.g. war,zip,ear) will be considered as a compressed zip files\n");
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
		final Set<String> fileExtns = new HashSet<String>(Arrays.asList(extnsStr.split(",\\s*")));

		String verbosityStr = (verbosityStr = getParamValue(params, "-v")) == null ? "1" : verbosityStr;
		if (!Arrays.asList("1", "2").contains(verbosityStr))
		{
			System.out.printf("ERROR: Invalid value provided for verbosity(-v): %s%n%n", verbosityStr);
			printUsage();
			return;
		}

		final int verbosity = Integer.parseInt(verbosityStr);

		boolean groupByContainer0 = true;
		if (verbosity == 1)
		{
			if (params.contains("-gv"))
			{
				groupByContainer0 = false;
				params.remove("-gv");
			}
			else
				params.remove("-gc");
		}
		final boolean groupByContainer = groupByContainer0;

		List<File> inputPathsToScan = new ArrayList<File>();
		for (String inputFileOrFolderStr : params)
			inputPathsToScan.add(new File(inputFileOrFolderStr));

		if (inputPathsToScan.isEmpty())
		{
			System.out.printf("ERROR: No paths are provided%n");
			printUsage();
			return;
		}

		final long startTime = System.currentTimeMillis();
		final Scanner scanner = new Scanner(inputPathsToScan, fileExtns);
		final FutureTask<Void> scanTask = new FutureTask<Void>(new Runnable()
		{
			public void run()
			{
				scanner.scan();
			}
		}, null);
		new Thread(scanTask).start();

		final ScheduledExecutorService se = Executors.newSingleThreadScheduledExecutor();

		se.scheduleWithFixedDelay(new Runnable()
		{
			private final Map<Version, Set<String>> containerPathsByVersion;
			private final Map<String, Map<Version, Integer>> noOfClassesByVersionByContainerPath;

			private final Map<Version, List<Result.Success>> successByVersion;

			{
				if (verbosity == 1)
				{
					containerPathsByVersion = new TreeMap<Version, Set<String>>();
					noOfClassesByVersionByContainerPath = new LinkedHashMap<String, Map<Version, Integer>>();
					successByVersion = null;
				}
				else if (verbosity == 2)
				{
					containerPathsByVersion = null;
					noOfClassesByVersionByContainerPath = null;
					successByVersion = new TreeMap<Version, List<Success>>();
				}
				else
					throw new IllegalArgumentException("Unhandled verbosity: " + verbosity);
			}

			public void run()
			{
				boolean done = scanTask.isDone();

				processNewResults();

				if (done)
				{
					se.shutdown();
					displayResults();
				}

				int elapsedTime = (int) (System.currentTimeMillis() - startTime);
				System.out.printf("Elapsed time: %s, Searched: %s files\r", getReadableTime(elapsedTime),
						formatNumber(scanner.getNoOfFilesScanned()));

				if (done)
					System.out.println("\nCompleted");
			}

			private void processNewResults()
			{
				System.out.printf("%52s\r", ""); // Clean "Elapsed time..."

				for (Result result : scanner.getNewResults())
				{
					if (!result.isSuccess())
					{
						System.out.println(result.getFailure().failureMessage);
						continue;
					}

					Success success = result.getSuccess();

					if (verbosity == 1)
					{
						Set<String> containerPaths = containerPathsByVersion.get(success.version);
						if (containerPaths == null)
							containerPathsByVersion.put(success.version, containerPaths = new LinkedHashSet<String>());
						containerPaths.add(success.containerPath);

						Map<Version, Integer> noOfClassesByVersion = noOfClassesByVersionByContainerPath.get(success.containerPath);
						if (noOfClassesByVersion == null)
							noOfClassesByVersionByContainerPath
									.put(success.containerPath, noOfClassesByVersion = new TreeMap<Version, Integer>());

						Integer noOfClasses = noOfClassesByVersion.get(success.version);
						noOfClasses = noOfClasses == null ? 1 : noOfClasses + 1;
						noOfClassesByVersion.put(success.version, noOfClasses);
					}
					else
					{
						List<Success> successList = successByVersion.get(success.version);
						if (successList == null)
							successByVersion.put(success.version, successList = new ArrayList<Result.Success>());
						successList.add(success);
					}
				}
			}

			private void displayResults()
			{
				try
				{
					displayResults0();
				}
				catch (Exception e)
				{
					System.err.printf("%nAn error occured: %s%n", e.getMessage());
					e.printStackTrace();
				}
			}

			private void displayResults0() throws InterruptedException, ExecutionException
			{
				if (verbosity == 1)
				{
					if (containerPathsByVersion.isEmpty())
						System.out.println("No files/classes found");
					else
					{
						int containerNameMaxLen = 0;
						for (String containerPath : noOfClassesByVersionByContainerPath.keySet())
						{
							String containerName = containerName(containerPath);
							if (containerName.length() > containerNameMaxLen)
								containerNameMaxLen = containerName.length();
						}

						if (groupByContainer)
						{
							boolean moreThanOneversionInContainer = false;
							for (Map<Version, Integer> map : noOfClassesByVersionByContainerPath.values())
							{
								if (map.size() > 1)
								{
									moreThanOneversionInContainer = true;
									break;
								}
							}

							String formatStr = "%-" + Math.min(MAX_SPACE_FOR_JAR_FILE_NAME, containerNameMaxLen) + "s %-"
									+ (moreThanOneversionInContainer ? 2 * SPACE_FOR_VERSION : SPACE_FOR_VERSION) + "s %s%n";

							StringBuilder versionInfo = new StringBuilder();
							for (Entry<String, Map<Version, Integer>> entry : noOfClassesByVersionByContainerPath.entrySet())
							{
								String containerPath = entry.getKey();
								String containerName = containerName(containerPath);

								versionInfo.setLength(0);
								for (Entry<Version, Integer> entry2 : entry.getValue().entrySet())
								{
									Version version = entry2.getKey();
									versionInfo.append(versionStr(version)).append("(").append(entry2.getValue()).append("),");
								}

								// Removing last comma(,)
								if (versionInfo.length() > 0)
									versionInfo.setLength(versionInfo.length() - 1);

								System.out.printf(formatStr, containerName, versionInfo, containerParent(containerPath));
							}
						}
						else
						{
							String formatStr = "%3s %-" + Math.min(MAX_SPACE_FOR_JAR_FILE_NAME, containerNameMaxLen) + "s %s%n";

							for (Entry<Version, Set<String>> entry : containerPathsByVersion.entrySet())
							{
								String versionStr = versionStr(entry.getKey());
								for (String containerPath : entry.getValue())
									System.out.printf(formatStr, versionStr, containerName(containerPath), containerParent(containerPath));
							}
						}
					}
				}
				else
				{// verbosity == 2
					if (successByVersion.isEmpty())
						System.out.println("No files/classes found");

					String formatStr = "%s %s" + File.separatorChar + "%s%n";
					for (Entry<Version, List<Success>> entry : successByVersion.entrySet())
					{
						Version version = entry.getKey();
						List<Success> successes = entry.getValue();
						for (Success success : successes)
							System.out.printf(formatStr, versionStr(version), success.containerPath, success.className);
					}
				}

				scanTask.get();

				System.out.println();
			}

			private String versionStr(Version version)
			{
				return version.javaMajor == -1
						? "Unknown. Cls version: " + version.classMajor + "." + version.classMinor
						: version.javaMajor + "." + version.javaMinor;
			}

			private String containerName(String containerPath)
			{
				return containerPath.substring(containerPath.lastIndexOf(File.separatorChar) + 1);
			}

			private String containerParent(String containerPath)
			{
				int dotIndex = containerPath.lastIndexOf(File.separatorChar);
				return containerPath.substring(0, dotIndex == -1 ? containerPath.length() : dotIndex);
			}

		}, 100, 400, TimeUnit.MILLISECONDS);
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

	private static String getReadableTime(int elapsedTime)
	{
		@SuppressWarnings("resource")
		Formatter f = new Formatter();
		boolean added = false;

		if (elapsedTime >= MILLIS_PER_HOUR)
		{
			int hours = (int) (elapsedTime / MILLIS_PER_HOUR);
			elapsedTime %= MILLIS_PER_HOUR;
			f.format("%dh:", hours);
			added = true;
		}
		if (added || elapsedTime >= MILLIS_PER_MIN)
		{
			int mins = (int) (elapsedTime / MILLIS_PER_MIN);
			elapsedTime %= MILLIS_PER_MIN;
			f.format("%02dm:", mins);
		}

		int secs = (int) (elapsedTime / MILLIS_PER_SEC);
		f.format("%02ds", secs);

		return f.toString();
	}

	private static String formatNumber(int num)
	{
		String numStr = Integer.toString(num);
		int len = numStr.length();
		StringBuilder sb = new StringBuilder(len + 4);
		if (num < 0)
		{
			sb.append('-');
			numStr = numStr.substring(1);
			len--;
		}
		boolean evenNoOfDigits = len % 2 == 0;
		for (int i = 0, end = len - 3; i < end; i++, evenNoOfDigits = !evenNoOfDigits)
		{
			sb.append(numStr.charAt(i));
			if (evenNoOfDigits)
				sb.append(',');
		}
		sb.append(numStr.substring(Math.max(len - 3, 0)));
		return sb.toString();
	}
}
