package cz.spojenka.lwt;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.ByteBuffer;

import cz.spojenka.lwt.util.ByteBufferUtils;

public class CICOPersistence {

    private static final String PK_SESSION_ACTIVE = "sessionActive";
    private static final String PK_LAST_TICKET = "lastTicket";
    private static final String PK_LAST_FOREGROUND_CONTROLLER = "lastForegroundController";

    private static CICOPersistence INSTANCE;

    private final SharedPreferences prefs;

    private boolean runtimeSessionActive;
    private CICOTicketFragment runtimeLastTicket;

    private CICOPersistence(Context context) {
        prefs = context.getSharedPreferences(CICOPersistence.class.getSimpleName(), Context.MODE_PRIVATE);
    }

    public static CICOPersistence getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = new CICOPersistence(context.getApplicationContext());
        }
        return INSTANCE;
    }

    public void putSessionActive(boolean active) {
        runtimeSessionActive = active;
        prefs.edit()
                .putBoolean(PK_SESSION_ACTIVE, active)
                .apply();
    }

    private boolean getSessionActiveFromPreferences() {
        return prefs.getBoolean(PK_SESSION_ACTIVE, false);
    }

    public boolean isSessionActive() {
        return runtimeSessionActive;
    }

    public boolean wasLastSessionTerminatedUnexpectedly() {
        return !isSessionActive() && getSessionActiveFromPreferences();
    }

    public void putLastTicket(CICOTicketFragment ticket) {
        this.runtimeLastTicket = ticket;
        if (ticket != null) {
            prefs.edit()
                    .putString(PK_LAST_TICKET, Base64.encodeToString(ByteBufferUtils.toByteArray(ticket.getByteBuffer()), Base64.DEFAULT))
                    .apply();
        } else {
            prefs.edit().remove(PK_LAST_TICKET).apply();
        }
    }

    public CICOTicketFragment getLastTicket() {
        if (runtimeLastTicket == null) {
            String ticketBase64 = prefs.getString(PK_LAST_TICKET, null);
            if (ticketBase64 != null) {
                byte[] ticketBytes = Base64.decode(ticketBase64, Base64.DEFAULT);
                runtimeLastTicket = CICOTicketFragment.getRootAsCICOTicketFragment(ByteBuffer.wrap(ticketBytes));
            }
        }
        return runtimeLastTicket;
    }

    public void putLastForegroundController(Class<?> foregroundControllerClass) {
        prefs.edit()
                .putString(PK_LAST_FOREGROUND_CONTROLLER, foregroundControllerClass.getName())
                .apply();
    }

    public Class<?> getLastForegroundController() {
        String controllerName = prefs.getString(PK_LAST_FOREGROUND_CONTROLLER, null);
        if (controllerName != null) {
            try {
                return Class.forName(controllerName);
            } catch (ClassNotFoundException e) {
                return null;
            }
        } else {
            return null;
        }
    }
}
