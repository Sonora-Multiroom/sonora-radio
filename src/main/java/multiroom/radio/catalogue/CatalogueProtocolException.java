package multiroom.radio.catalogue;

/** The catalogue answered, but with something this extension cannot read, or with a refusal. */
public class CatalogueProtocolException extends RuntimeException {

    /** Creates the exception. */
    public CatalogueProtocolException(String message) {
        super(message);
    }

    /** Creates the exception with a cause. */
    public CatalogueProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
