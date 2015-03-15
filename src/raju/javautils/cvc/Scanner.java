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
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Scans .class files for their Version in the folders and the archive files
 * <p>
 * Note: Inscances of this class can't be reused. i.e.
 * {@linkplain Scanner#scan()} method can be called only once.
 */
/**
 * @author djc867
 *
 */
final class Scanner
{
	private final Collection<File> inputPathsToScan;
	private final Set<String> fileExtns;
	private final FileFilter fileFilter;

	private final Queue<Result> results = new ConcurrentLinkedQueue<Result>();

	private boolean used;

	private volatile int noOfFilesScanned;

	/**
	 * Note: {@code extns} should not start with .(dot) e.g. for valid extensions:
	 * jar, war, ear
	 */
	Scanner(Collection<File> inputPathsToScan, final Set<String> fileExtns)
	{
		this.inputPathsToScan = Util.checkNotNull(inputPathsToScan);
		this.fileExtns = Util.checkNotNull(fileExtns);
		this.fileFilter = new FileFilter()
		{
			public boolean accept(File file)
			{
				return file.isDirectory() || fileExtns.contains(fileExtn(file.getName()));
			}
		};
	}

	/** Note: Can be called only once */
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

	int getNoOfFilesScanned()
	{
		return noOfFilesScanned;
	}

	private void scanFolderOrFile(File input)
	{
		if (input.isFile())
		{
			scanFile(input);
			return;
		}

		for (File child : input.listFiles(fileFilter))
			scanFolderOrFile(child);
	}

	private void scanFile(File file)
	{
		if (!file.exists())
		{
			results.add(Result.failure("Unable to find file: " + file.getAbsolutePath()));
			return;
		}

		String fileName = file.getName();
		String fileExtn = fileExtn(fileName);

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

				for (ZipEntry zipEntry : Collections.list(zipFile.entries()))
				{
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

			String entryName = platformEntryName(zipEntry.getName());
			String zipEntryExtn = fileExtn(entryName);

			try
			{
				// Note: zipEntryExtn may be null, so keeping the "class" string
				// first in the equals check
				if ("class".equals(zipEntryExtn))
					findClassVersion(entryName, containerPath, zipInputStream);
				else if (fileExtns.contains(fileExtn(entryName)))
				{
					ZipInputStream zis = new ZipInputStream(zipInputStream);
					scanZipInputStream(containerPath + File.separatorChar + entryName, zis);
				}
			}
			finally
			{
				zipInputStream.closeEntry();
			}
		}
	}

	/**
	 * File extension without dot. e.g. class for a .class file.
	 * <p>
	 * This method expects that there is at least one character before the dot(.)
	 * and one character after the dot(.). Otherwise returns null
	 */
	private static String fileExtn(String fileName)
	{
		int dotIndex = fileName.lastIndexOf('.');
		return (dotIndex > 0 && ++dotIndex != fileName.length())
				? fileName.substring(dotIndex) : null;
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

		results.add(Result.success(className,
				Version.fromClassVersion(classMajorVersion, classMinorVersion), containerPath));
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
			results.add(Result.failure("Unable to close Closeable: " + c));
		}
	}
}
