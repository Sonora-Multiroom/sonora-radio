package multiroom.radio.rest.query;

import multiroom.radio.text.Text;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks the names a client sent. Spring ignores a parameter a handler does not declare and joins a
 * repeated one with a comma, so a typo such as {@code countrycode} would silently search everything;
 * both are refused here, from the raw map, instead.
 */
public final class RequestParameters {

    /** How much of a client-supplied name is ever echoed back. */
    static final int ECHOED_NAME_LIMIT = 32;

    private RequestParameters() {
    }

    /**
     * Refuses a name outside {@code allowed} and a name sent more than once.
     *
     * @throws InvalidParameterException naming the first offender, cleaned and cut to 32 characters
     */
    public static void check(MultiValueMap<String, String> sent, Set<String> allowed) {
        for (Map.Entry<String, List<String>> entry : sent.entrySet()) {
            String name = entry.getKey();
            if (!allowed.contains(name)) {
                throw new InvalidParameterException(echo(name), "is not a parameter of this request");
            }
            if (entry.getValue() != null && entry.getValue().size() > 1) {
                throw new InvalidParameterException(name, "must be sent only once");
            }
        }
    }

    /** A client-supplied name made safe to put in an answer or a log line. */
    static String echo(String name) {
        return Text.bounded(name, ECHOED_NAME_LIMIT);
    }
}
