package raju.javautils.cvc;

@SuppressWarnings("serial")
final class MutableInteger extends Number implements Comparable<MutableInteger>
{
	private int value;

	void increment()
	{
		value++;
	}

	@Override
	public int hashCode()
	{
		return value;
	}

	@Override
	public boolean equals(Object obj)
	{
		return (obj instanceof MutableInteger) && (value == ((MutableInteger) obj).value);
	}

	public int compareTo(MutableInteger o)
	{
		return value < o.value ? -1 : value > o.value ? 1 : 0;
	}

	@Override
	public int intValue()
	{
		return value;
	}

	@Override
	public long longValue()
	{
		return value;
	}

	@Override
	public float floatValue()
	{
		return value;
	}

	@Override
	public double doubleValue()
	{
		return value;
	}

	@Override
	public String toString()
	{
		return Integer.toString(value);
	}
}