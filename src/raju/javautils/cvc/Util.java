package raju.javautils.cvc;

final class Util
{
	static <T> T checkNotNull(T obj)
	{
		if (obj == null)
			throw new NullPointerException();
		return obj;
	}
}