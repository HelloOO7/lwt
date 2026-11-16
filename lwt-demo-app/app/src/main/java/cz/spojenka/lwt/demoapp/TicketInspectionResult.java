package cz.spojenka.lwt.demoapp;

public enum TicketInspectionResult {
    OK,
    INVALID_SYNTAX,
    INVALID_SIGNATURE,
    INVALID_TOTP,
    BEFORE_VALIDITY,
    PAST_VALIDITY,
    CHECKED_OUT
}
