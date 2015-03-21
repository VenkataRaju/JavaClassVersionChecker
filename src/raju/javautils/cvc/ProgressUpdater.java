package raju.javautils.cvc;

import java.io.File;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import raju.javautils.cvc.Result.Success;

/** Updates the progress in the Command line UI */
final class ProgressUpdater implements Runnable
{
	private static final int MILLIS_PER_SEC = 1000, MILLIS_PER_MIN = 60000, MILLIS_PER_HOUR = 3600000;

	private static final String PROGRESS_LINE_CLEARN_STRING = "%48s\r";
	private static final int MAX_SPACE_FOR_JAR_FILE_NAME = 35;
	private static final int SPACE_FOR_VERSION = 8;

	private final long startTime = System.currentTimeMillis();

	private final int verbosity;
	private final boolean groupByContainer;

	private final Future<Void> scanTask;
	private final Scanner scanner;
	private final ExecutorService es;

	private final Map<String, Map<Version, MutableInteger>> noOfClassesByVersionByContainerPath;
	private final Map<Version, Set<String>> containerPathsByVersion;

	private final Map<Version, List<Result.Success>> successByVersion;

	ProgressUpdater(int verbosity, boolean groupByContainer,
			Future<Void> scanTask, Scanner scanner, ExecutorService es)
	{
		this.verbosity = verbosity;
		this.groupByContainer = groupByContainer;

		this.es = Util.checkNotNull(es);
		this.scanTask = Util.checkNotNull(scanTask);
		this.scanner = Util.checkNotNull(scanner);

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

		int elapsedTime = (int) (System.currentTimeMillis() - startTime);
		int noOfFilesScanned = scanner.getNoOfFilesScanned();
		int noOfClassFilesScanned = scanner.getNoOfClassFilesScanned();
		System.out.printf("%s, %s file%s, %s classe%s\r", getReadableTime(elapsedTime),
				formatNumber(noOfFilesScanned), noOfFilesScanned != 1 ? "s" : "",
				formatNumber(noOfClassFilesScanned), noOfClassFilesScanned != 1 ? "s" : "");

		if (done)
			System.out.println("\nCompleted");
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

	private void processNewResults()
	{
		// Clean "Elapsed time, noOfFilesScanned and noOfClassesScanned"
		System.out.printf(PROGRESS_LINE_CLEARN_STRING, "");

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
				if (groupByContainer)
				{
					Map<Version, MutableInteger> noOfClassesByVersion = noOfClassesByVersionByContainerPath.get(success.containerPath);
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
			System.err.printf("%nAn error occured: %s%n", e.getMessage());
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
				System.out.println("No files/classes found");
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
					for (Set<String> containerPaths : containerPathsByVersion.values())
					{
						for (String containerPath : containerPaths)
						{
							if (containerPath.length() > containerNameMaxLen)
								containerNameMaxLen = containerPath.length();
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

		scanTask.get(); // To check for exception (if any)

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
}
