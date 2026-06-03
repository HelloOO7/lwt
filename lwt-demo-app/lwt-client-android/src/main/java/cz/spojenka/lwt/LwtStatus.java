package cz.spojenka.lwt;

public class LwtStatus {

    public static int OK = 200;
    public static int CREATED = 201;
    public static int ACCEPTED = 202;
    public static int NO_CONTENT = 204;

    public static int BAD_REQUEST = 400;
    public static int UNAUTHORIZED = 401;
    public static int FORBIDDEN = 403;
    public static int NOT_FOUND = 404;
    public static int CONTENT_TOO_LARGE = 413;
    public static int TOO_MANY_REQUESTS = 429;

    public static int INTERNAL_SERVER_ERROR = 500;
    public static int NOT_IMPLEMENTED = 501;
    public static int SERVICE_UNAVAILABLE = 503;

    public static boolean isExtended(int status) {
        return status >= 100000 && status <= 999999;
    }

    public static int getMainStatus(int status) {
        if (isExtended(status)) {
            return status / 1000;
        } else {
            return status;
        }
    }

    public static int getExtendedStatus(int status) {
        if (isExtended(status)) {
            return status % 1000;
        } else {
            return 0;
        }
    }

    public static int toHttpStatus(int status) {
        return getMainStatus(status);
    }

    public static boolean isOK(int status) {
        int mainStatus = getMainStatus(status);
        return mainStatus >= 200 && mainStatus < 300;
    }

    public static boolean isError(int status) {
        return !isOK(status);
    }
}
