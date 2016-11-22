package raju.javautils.cvc;

/** Holds either {@link Success} or {@link Failure} */
final class Result
{
	static final class Success
	{
		/** Can be archive(e.g. jar) or folder Path */
		final String containerPath;

		/** Can be Abc.class or x/y/z/Xyz.class */
		final String className;

		final Version version;

		Success(String containerPath, String className, Version version)
		{
			this.containerPath = containerPath;
			this.className = className;
			this.version = version;
		}
	}

	static final class Failure
	{
		final String failureMessage;

		Failure(String failureMessage)
		{
			this.failureMessage = failureMessage;
		}

		@Override
		public String toString()
		{
			return "Failure [failureMessage=" + failureMessage + "]";
		}
	}

	final Object o;

	Result(Success success)
	{
		this.o = success;
	}

	Result(Failure failure)
	{
		this.o = failure;
	}

	static Result success(String containerPath, String className, Version version)
	{
		return new Result(new Success(className, containerPath, version));
	}

	static Result failure(String failureMessage)
	{
		return new Result(new Failure(failureMessage));
	}

	boolean isSuccess()
	{
		return o instanceof Success;
	}

	Success getSuccess()
	{
		if (isSuccess())
			return (Success) o;
		throw new IllegalArgumentException("Result is a Failure");
	}

	Failure getFailure()
	{
		if (isSuccess())
			throw new IllegalArgumentException("Result is a Success");
		return (Failure) o;
	}

	@Override
	public String toString()
	{
		return "Result [o=" + o + "]";
	}
}