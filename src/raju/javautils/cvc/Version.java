package raju.javautils.cvc;

// TODO: Load extra mappings from the external file
final class Version implements Comparable<Version>
{
	// Cache from 1.2 to 1.8 Java Versions
	private static final Version[] V_1_2_TO_V_1_8 = { new Version(1, 2, 46, 0),
			new Version(1, 3, 47, 0), new Version(1, 4, 48, 0),
			new Version(1, 5, 49, 0), new Version(1, 6, 50, 0),
			new Version(1, 7, 51, 0), new Version(1, 8, 52, 0) };

	final int javaMajor, javaMinor;
	final int classMajor, classMinor;

	private Version(int javaMajor, int javaMinor, int classMajor, int classMinor)
	{
		this.javaMajor = javaMajor;
		this.javaMinor = javaMinor;
		this.classMajor = classMajor;
		this.classMinor = classMinor;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = prime + classMajor;
		result = prime * result + classMinor;
		result = prime * result + javaMajor;
		result = prime * result + javaMinor;
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (!(obj instanceof Version))
			return false;
		Version that = (Version) obj;
		return (this == that)
				|| (javaMajor == that.javaMajor && javaMinor == that.javaMinor
						&& classMajor == that.classMajor && classMinor == that.classMinor);
	}

	public int compareTo(Version o)
	{
		return javaMajor < o.javaMajor ? -1 : javaMajor > o.javaMajor ? 1 :
				javaMinor < o.javaMinor ? -1 : javaMinor > o.javaMinor ? 1 :
						classMajor < o.classMajor ? -1 : classMajor > o.classMajor ? 1 :
								classMinor < o.classMinor ? -1 : classMinor > o.classMinor ? 1 : 0;
	}

	static Version fromClassVersion(int classMajor, int classMinor)
	{
		// 1.0 -> 45.0 - 45.3
		// 1.1 -> 45.4 - 45.65535
		// 1.2 -> 46
		// 1.3 -> 47
		// 1.4 -> 48
		// 1.5 -> 49
		// 1.6 -> 50
		// 1.7 -> 51
		// 1.8 -> 52

		if (classMajor >= 46 && classMinor <= 52)
			return V_1_2_TO_V_1_8[classMajor - 46];
		if (classMajor == 45)
			return new Version(1, (classMinor <= 3) ? 0 : 1, classMajor, classMinor);
		return new Version(-1, -1, classMajor, classMinor);
	}

	@Override
	public String toString()
	{
		return "Version [javaMajor=" + javaMajor + ", javaMinor=" + javaMinor + ", classMajor=" + classMajor + ", classMinor=" + classMinor
				+ "]";
	}
}
