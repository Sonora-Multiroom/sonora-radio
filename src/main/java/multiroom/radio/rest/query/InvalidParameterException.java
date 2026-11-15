package multiroom.radio.rest.query;

/**
 * A request parameter the extension refuses before any catalogue call. One exception type gives one
 * 400 path that always names the parameter. The reason is a fixed sentence: it never repeats the
 * client's value, which could be long or hostile.
 */
public class InvalidParameterException extends RuntimeException {

    private final String parameter;
    private final String reason;

    /**
     * Creates the exception.
     *
     * @param parameter the offending parameter's name, already safe to show (no control characters, short)
     * @param reason    what is wrong and what is allowed
     */
    public InvalidParameterException(String parameter, String reason) {
        super(parameter + ": " + reason);
        this.parameter = parameter;
        this.reason = reason;
    }

    /** The offending parameter. */
    public String parameter() {
        return parameter;
    }

    /** What is wrong and what is allowed. */
    public String reason() {
        return reason;
    }
}
