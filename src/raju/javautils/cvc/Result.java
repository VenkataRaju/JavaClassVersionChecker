package raju.javautils.cvc;

final class Result
{
	static final class Success
	{
		/** Can be Abc.class or x/y/z/Xyz.class */
		final String className;

		final Version version;

		/** Can be archive(e.g. jar) or folder Path */
		final String containerPath;

		Success(String className, Version version, String containerPath)
		{
			this.className = Util.checkNotNull(className);
			this.version = Util.checkNotNull(version);
			this.containerPath = Util.checkNotNull(containerPath);
		}
	}

	static final class Failure
	{
		final String failureMessage;

		Failure(String failureMessage)
		{
			this.failureMessage = Util.checkNotNull(failureMessage);
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

	static Result success(String className, Version version, String containerPath)
	{
		return new Result(new Success(className, version, containerPath));
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
