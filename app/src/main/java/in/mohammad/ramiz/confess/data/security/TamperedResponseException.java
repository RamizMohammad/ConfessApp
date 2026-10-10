package in.mohammad.ramiz.confess.data.security;

import java.io.IOException;

/**
 * A server answer whose signature didn't verify (or was stripped): something
 * between the phone and the server changed it. The response is discarded.
 */
public class TamperedResponseException extends IOException {
    public TamperedResponseException(String message) {
        super(message);
    }
}
