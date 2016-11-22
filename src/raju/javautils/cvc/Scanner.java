package raju.javautils.cvc;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Scans {@code .class} files for their Version in the folders and the archive files
 * <p>
 * Note: Instances of this class can't be reused. i.e.
 * {@linkplain Scanner#scan()} method can be called only once.
 */
final class Scanner
{
	private final Collection<File> inputPathsToScan;
	private final Set<String> fileExtns;
	private final FileFilter fileFilter;

	private final Queue<Result> results = new ConcurrentLinkedQueue<Result>();

	private boolean used;

	private volatile int noOfFilesScanned, noOfClassFilesScanned;

	/**
	 * Note: {@code extns} should not start with .(dot) e.g. for valid extensions:
	 * jar, war, ear
	 */
	Scanner(Collection<File> inputPathsToScan, final Set<String> fileExtns)
	{
		this.inputPathsToScan = inputPathsToScan;
		this.fileExtns = fileExtns;
		this.fileFilter = new FileFilter()
		{
			public boolean accept(File file)
			{
				String fileExtn;
				return file.isDirectory() ||
						((fileExtn = fileExtn(file.getName())) != null && fileExtns.contains(fileExtn));
			}
		};
	}

	/**
	 * Note: Can be called only once
	 * 
	 * @throws IllegalStateException
	 *           if called more than once
	 */
	void scan()
	{
		if (used)
			throw new IllegalStateException("Already used");
		used = true;

		for (File inputPath : inputPathsToScan)
		{
			if (fileFilter.accept(inputPath))
				scanFolderOrFile(inputPath);
			else
				results.add(Result.failure("Ignoring invalid input: " + inputPath.getPath()));
		}
	}

	int noOfFilesScanned()
	{
		return noOfFilesScanned;
	}

	int noOfClassFilesScanned()
	{
		return noOfClassFilesScanned;
	}

	private void scanFolderOrFile(File input)
	{
		if (!input.exists())
			results.add(Result.failure("Unable to find file: " + input.getAbsolutePath()));
		else if (input.isFile()) // We don't want to read the System files so using isFile and isDirectory
			scanFile(input);
		else if (input.isDirectory())
		{
			File[] children = input.listFiles(fileFilter);

			if (children == null)
			{
				results.add(Result.failure("Unable to read the directory: " + input.getAbsolutePath()));
				return;
			}

			for (File child : children)
				scanFolderOrFile(child);
		}
	}

	private void scanFile(File file)
	{
		String fileName = file.getName();
		String fileExtn = fileExtn(fileName);

		if (fileExtn == null)
			return;

		if (fileExtn.equals("class"))
		{
			InputStream is = null;
			try
			{
				is = new FileInputStream(file);
				findClassVersion(file.getName(), file.getParentFile().getPath(), is);
			}
			catch (IOException e)
			{
				results.add(Result.failure("IO Error: " + e.getMessage() + ", while reading: " + file.getPath()));
			}
			finally
			{
				close(is);
			}
		}
		else
		{// jar, war, ear, zip, etc..
			ZipFile zipFile = null;

			try
			{
				zipFile = new ZipFile(file);

				Enumeration<? extends ZipEntry> entries = zipFile.entries();
				while (entries.hasMoreElements())
				{
					ZipEntry zipEntry = entries.nextElement();

					if (zipEntry.isDirectory())
						continue;

					String entryName = platformEntryName(zipEntry.getName());
					String zipEntryExtn = fileExtn(entryName);

					InputStream is = null;
					try
					{
						// Note: zipEntryExtn may be null, so keeping the "class" string
						// first in the equals check
						if ("class".equals(zipEntryExtn))
						{
							is = zipFile.getInputStream(zipEntry);
							findClassVersion(entryName, file.getPath(), is);
						}
						else if (fileExtns.contains(fileExtn(entryName)))
						{
							is = zipFile.getInputStream(zipEntry);
							ZipInputStream zis = new ZipInputStream(is);
							scanZipInputStream(file.getPath() + File.separatorChar + entryName, zis);
						}
					}
					finally
					{
						close(is);
					}
				}
			}
			catch (ZipException e)
			{
				results.add(Result.failure("Unable to open file: " + file.getPath()));
			}
			catch (IOException e)
			{
				results.add(Result.failure("IO Error: " + e.getMessage() + ", while reading: " + file.getPath()));
			}
			finally
			{
				try
				{
					// Too bad, it doesn't implement Closeable
					if (zipFile != null)
						zipFile.close();
				}
				catch (IOException e)
				{
					results.add(Result.failure("Unable to close zip file: " + file.getPath()));
				}
			}
		}

		// FindBugs ignore warning note:
		// Not a bug as only one thread updates this and other thread need
		// not read the most recent value. i.e. 'current - 1' is okay.
		noOfFilesScanned++;
	}

	private void scanZipInputStream(String containerPath, ZipInputStream zipInputStream) throws IOException
	{
		for (ZipEntry zipEntry; (zipEntry = zipInputStream.getNextEntry()) != null;)
		{
			if (zipEntry.isDirectory())
				continue;

			String entryName = zipEntry.getName();
			String zipEntryExtn = fileExtn(entryName);

			// fileExtns.contains(null) throws NullPointer (As we are using IgnnoreCase String comparator)
			if (zipEntryExtn == null)
				continue;

			entryName = platformEntryName(entryName);

			if (zipEntryExtn.equals("class"))
				findClassVersion(entryName, containerPath, zipInputStream);
			else if (fileExtns.contains(zipEntryExtn))
			{
				ZipInputStream zis = new ZipInputStream(zipInputStream);
				scanZipInputStream(containerPath + File.separatorChar + entryName, zis);
			}

			// Not keeping in finally as this is not a system resource (anyways file
			// is closed in finally)
			zipInputStream.closeEntry();
		}
	}

	/**
	 * File extension without dot. e.g. {@code class} for a {@code .class} file.
	 * <p>
	 * This method expects that there is at least one character before the dot(.)
	 * and one character after the dot(.). Otherwise returns null
	 */
	private static String fileExtn(String fileName)
	{
		int i = fileName.lastIndexOf('.') + 1;
		return (i != 0 && i != fileName.length()) ? fileName.substring(i) : null;
	}

	/**
	 * Replaces separators in {@code zipEntryName} with platform specific
	 * separator
	 */
	private static String platformEntryName(String zipEntryName)
	{
		return zipEntryName.replace('/', File.separatorChar);
	}

	private void findClassVersion(String className, String containerPath, InputStream is) throws IOException
	{
		DataInputStream dataIS = new DataInputStream(is);

		dataIS.readInt();

		int classMinorVersion = dataIS.readShort();
		int classMajorVersion = dataIS.readShort();

		Version classJavaVersion = Version.fromClassVersion(classMajorVersion, classMinorVersion);
		results.add(Result.success(className, containerPath, classJavaVersion));

		// FindBugs ignore warning note:
		// Not a bug as only one thread updates this and other thread need
		// not read the most recent value. i.e. 'current - 1' is okay.
		noOfClassFilesScanned++;
	}

	List<Result> getNewResults()
	{
		if (results.isEmpty())
			return Collections.emptyList();
		List<Result> resultsList = new ArrayList<Result>();
		for (Result result; (result = results.poll()) != null;)
			resultsList.add(result);
		return resultsList;
	}

	void close(Closeable c)
	{
		try
		{
			if (c != null)
				c.close();
		}
		catch (IOException e)
		{
			results.add(Result.failure("Unable to close: " + c));
		}
	}
}