package de.svws_nrw.edugate.control.error;

/** Fehlerformat gemäß RFC 7807 ({@code application/problem+json}). */
public record ProblemDetail(String type, String title, int status, String detail, String instance) {

    public static ProblemDetail of(final String type, final String title, final int status, final String detail) {
        return new ProblemDetail(type, title, status, detail, null);
    }
}
