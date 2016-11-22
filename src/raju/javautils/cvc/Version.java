package raju.javautils.cvc;

final class Version implements Comparable<Version>
{
	private static final Version[] VERSIONS_CACHE = {
			new Version(46, 0, 1, 2), new Version(47, 0, 1, 3),
			new Version(48, 0, 1, 4), new Version(49, 0, 1, 5),
			new Version(50, 0, 1, 6), new Version(51, 0, 1, 7),
			new Version(52, 0, 1, 8), new Version(53, 0, 1, 9) };

	final int classMajor, classMinor;
	final int javaMajor, javaMinor;

	private Version(int classMajor, int classMinor, int javaMajor, int javaMinor)
	{
		this.classMajor = classMajor;
		this.classMinor = classMinor;

		this.javaMajor = javaMajor;
		this.javaMinor = javaMinor;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = prime * +classMajor;
		result = prime * result + classMinor;
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (!(obj instanceof Version))
			return false;
		Version other = (Version) obj;
		return (this == other) || (classMajor == other.classMajor && classMinor == other.classMinor);
	}

	public int compareTo(Version o)
	{
		return classMajor < o.classMajor ? -1
				: classMajor > o.classMajor ? 1 : classMinor < o.classMinor ? -1 : classMinor > o.classMinor ? 1 : 0;
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
		// 1.9 -> 53

		if (classMajor >= 46)
		{
			if (classMinor <= 53)
				return VERSIONS_CACHE[classMajor - 46];
			return new Version(classMajor, classMinor, 1, classMajor - 44 /* WARN: May Break */ );
		}
		if (classMajor == 45)
			return new Version(classMajor, classMinor, 1, (classMinor <= 3) ? 0 : 1);
		return new Version(classMajor, classMinor, -1, -1);
	}

	@Override
	public String toString()
	{
		return "Version [classMajor=" + classMajor + ", classMinor=" + classMinor + ", javaMajor=" + javaMajor
				+ ", javaMinor=" + javaMinor + "]";
	}
}