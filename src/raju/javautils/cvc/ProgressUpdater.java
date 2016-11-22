package raju.javautils.cvc;

import java.io.File;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import raju.javautils.cvc.Result.Success;

/** Updates the progress in the Command line UI */
final class ProgressUpdater implements Runnable
{
	private static final boolean COUNTRY_IS_INDIA = Locale.getDefault().getCountry().equals("IN");
	private static final int SECONDS_PER_MIN = 60, SECONDS_PER_HOUR = 3600;

	private static final String PROGRESS_LINE_CLEARN_STRING = "%48s\r";
	private static final int MAX_SPACE_FOR_JAR_FILE_NAME = 35;
	private static final int SPACE_FOR_VERSION = 8;

	private final long startTime = System.nanoTime();

	private final int verbosity;
	private final boolean groupByContainer;

	private final Scanner scanner;
	private final Future<Void> scanTask;
	private final ExecutorService es;

	private final Map<String, Map<Version, MutableInteger>> noOfClassesByVersionByContainerPath;
	private final Map<Version, Set<String>> containerPathsByVersion;

	private final Map<Version, List<Result.Success>> successByVersion;

	ProgressUpdater(int verbosity, boolean groupByContainer,
			Scanner scanner, Future<Void> scanTask, ExecutorService es)
	{
		this.verbosity = verbosity;
		this.groupByContainer = groupByContainer;

		this.scanner = scanner;
		this.scanTask = scanTask;
		this.es = es;

		noOfClassesByVersionByContainerPath = (verbosity == 1 && groupByContainer)
				? new LinkedHashMap<String, Map<Version, MutableInteger>>() : null;
		containerPathsByVersion = (verbosity == 1 && groupByContainer)
				? null : new TreeMap<Version, Set<String>>();
		successByVersion = verbosity == 2 ? new TreeMap<Version, List<Success>>() : null;
	}

	/* Called multiple times as a scheduled task */
	public void run()
	{
		boolean done = scanTask.isDone();

		processNewResults();

		if (done)
		{
			es.shutdown();
			displayResults();
		}

		int elapsedTimeInSeconds = (int) TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime);
		int noOfClassFilesScanned = scanner.noOfClassFilesScanned(); // This should be read before noOfFilesScanned
		int noOfFilesScanned = scanner.noOfFilesScanned();
		System.out.printf("%s, %s file%s, %s classe%s\r", getReadableTime(elapsedTimeInSeconds),
				format(noOfFilesScanned), noOfFilesScanned != 1 ? "s" : "",
				format(noOfClassFilesScanned), noOfClassFilesScanned != 1 ? "s" : "");

		if (done)
			System.out.printf("%nCompleted");
	}

	private static String getReadableTime(int elapsedTimeInSeconds)
	{
		@SuppressWarnings("resource")
		Formatter f = new Formatter();
		boolean added = elapsedTimeInSeconds >= SECONDS_PER_HOUR;

		if (added)
		{
			int hours = (int) (elapsedTimeInSeconds / SECONDS_PER_HOUR);
			elapsedTimeInSeconds %= SECONDS_PER_HOUR;
			f.format("%dh:", hours);
		}

		if (added || elapsedTimeInSeconds >= SECONDS_PER_MIN)
		{
			int mins = (int) (elapsedTimeInSeconds / SECONDS_PER_MIN);
			elapsedTimeInSeconds %= SECONDS_PER_MIN;
			f.format("%02dm:", mins);
		}

		f.format("%02ds", elapsedTimeInSeconds);

		return f.toString();
	}

	/**
	 * Locale specific formatting for all the countries other than India.
	 * <p>
	 * For India: Formats number in -12,34,567 format (this is what is used in India, but unfortunately not supported)
	 */
	private static String format(int num)
	{
		if (!COUNTRY_IS_INDIA)
			return String.format("%,d", num);

		String numStr = Integer.toString(num);
		int len = numStr.length();

		/* For every 2 digits 1 comma, except for the last 3.
		 * Using -2 instead of -3 to avoid round of issue */
		StringBuilder sb = new StringBuilder(len + ((len - 2) / 2));

		int startIndex, initAppend;

		if (num < 0)
		{
			sb.append('-');
			startIndex = 1;
			initAppend = (len - 4) % 2;
		}
		else
		{
			startIndex = 0;
			initAppend = (len - 3) % 2;
		}

		if (initAppend == 1)
			sb.append(numStr.charAt(startIndex++)).append(',');

		for (int end = len - 3; startIndex < end;)
			sb.append(numStr, startIndex, startIndex += 2).append(',');
		sb.append(numStr, startIndex, len);

		return sb.toString();
	}

	private void processNewResults()
	{
		// Clean "Elapsed time, noOfFilesScanned and noOfClassesScanned"
		System.out.printf(PROGRESS_LINE_CLEARN_STRING, "");

		for (Result result : scanner.getNewResults())
		{
			if (!result.isSuccess())
			{
				System.err.println(result.getFailure().failureMessage);
				continue;
			}

			Success success = result.getSuccess();

			if (verbosity == 1)
			{
				if (groupByContainer)
				{
					Map<Version, MutableInteger> noOfClassesByVersion = noOfClassesByVersionByContainerPath
							.get(success.containerPath);
					if (noOfClassesByVersion == null)
						noOfClassesByVersionByContainerPath.put(success.containerPath,
								noOfClassesByVersion = new TreeMap<Version, MutableInteger>());

					MutableInteger noOfClasses = noOfClassesByVersion.get(success.version);
					if (noOfClasses == null)
						noOfClassesByVersion.put(success.version, noOfClasses = new MutableInteger());
					noOfClasses.increment();
				}
				else
				{
					Set<String> containerPaths = containerPathsByVersion.get(success.version);
					if (containerPaths == null)
						containerPathsByVersion.put(success.version, containerPaths = new LinkedHashSet<String>());
					containerPaths.add(success.containerPath);
				}
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
			System.err.printf("An error occured: %s%n%n", e.getMessage());
			e.printStackTrace();
			System.out.println();
		}
	}

	private void displayResults0() throws InterruptedException, ExecutionException
	{
		if (verbosity == 1)
		{
			if ((groupByContainer && noOfClassesByVersionByContainerPath.isEmpty())
					|| (!groupByContainer && containerPathsByVersion.isEmpty()))
			{
				System.out.println("No files/classes found");
			}
			else
			{
				int containerNameMaxLen = 0;

				if (groupByContainer)
				{
					boolean moreThanOneversionInContainer = false;
					for (Entry<String, Map<Version, MutableInteger>> entry : noOfClassesByVersionByContainerPath.entrySet())
					{
						String containerPath = entry.getKey();
						String containerName = containerName(containerPath);
						if (containerName.length() > containerNameMaxLen)
							containerNameMaxLen = containerName.length();

						if (!moreThanOneversionInContainer)
						{
							Map<Version, MutableInteger> map = entry.getValue();
							moreThanOneversionInContainer = map.size() > 1;
						}
					}

					String formatStr = "%-" + Math.min(MAX_SPACE_FOR_JAR_FILE_NAME, containerNameMaxLen) + "s %-"
							+ (moreThanOneversionInContainer ? 2 * SPACE_FOR_VERSION : SPACE_FOR_VERSION) + "s %s%n";

					StringBuilder versionInfo = new StringBuilder();

					for (Entry<String, Map<Version, MutableInteger>> entry : noOfClassesByVersionByContainerPath.entrySet())
					{
						String containerPath = entry.getKey();
						String containerName = containerName(containerPath);

						versionInfo.setLength(0);
						for (Entry<Version, MutableInteger> entry2 : entry.getValue().entrySet())
						{
							Version version = entry2.getKey();
							versionInfo.append(versionStr(version)).append("(").append(entry2.getValue().intValue()).append("),");
						}

						// Removing last comma(,)
						if (versionInfo.length() > 0)
							versionInfo.setLength(versionInfo.length() - 1);

						System.out.printf(formatStr, containerName, versionInfo, containerParent(containerPath));
					}
				}
				else
				{ // groupByVersion
					for (Set<String> containerPaths : containerPathsByVersion.values())
					{
						for (String containerPath : containerPaths)
						{
							int containerNameLen = containerPath.length() - (containerPath.lastIndexOf(File.separatorChar) + 1);
							if (containerNameLen > containerNameMaxLen)
								containerNameMaxLen = containerNameLen;
						}
					}

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
		{// verbocity == 2
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

		System.out.println();

		scanTask.get(); // To check for exception (if any)
	}

	private static String versionStr(Version version)
	{
		return version.javaMajor == -1
				? "Unknown. Class version: " + version.classMajor + "." + version.classMinor
				: version.javaMajor + "." + version.javaMinor;
	}

	private static String containerName(String containerPath)
	{
		return containerPath.substring(containerPath.lastIndexOf(File.separatorChar) + 1);
	}

	private static String containerParent(String containerPath)
	{
		int index = containerPath.lastIndexOf(File.separatorChar);
		return containerPath.substring(0, index == -1 ? containerPath.length() : index);
	}
}