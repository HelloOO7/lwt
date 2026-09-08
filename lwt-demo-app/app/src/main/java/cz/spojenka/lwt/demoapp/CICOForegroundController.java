package cz.spojenka.lwt.demoapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.core.content.res.ResourcesCompat;
import cz.spojenka.android.util.CollectionUtils;
import cz.spojenka.lwt.CICOService;
import cz.spojenka.lwt.LwtDevice;
import cz.spojenka.lwt.TripAdvertisementData;
import cz.spojenka.lwt.TripAdvertisementDataExt;
import cz.spojenka.lwt.demoapp.databinding.DeviceListItemBinding;
import cz.spojenka.lwt.util.TextMarkupConverter;

public class CICOForegroundController implements CICOService.ForegroundController {

    private static final boolean TEST_LEGACY_TINT = true;

    private static final int NOTIFICATION_ID_SERVICE = 0xC1C0001;
    private static final int NOTIFICATION_ID_ERROR_BT_OFF = 0xC1C0002;

    private static final String NOTIFICATION_GROUP = "CICO";
    private static final String NOTIFICATION_CHANNEL_SERVICE = "CICOService";
    private static final String NOTIFICATION_CHANNEL_ALERTS = "CICOAlerts";

    private final Context context;
    private final NotificationManager notificationManager;

    private boolean createGroupCalled = false;
    private boolean createServiceChannelCalled = false;
    private boolean createAlertsChannelCalled = false;

    public CICOForegroundController(Context context) {
        this.context = context;
        this.notificationManager = context.getSystemService(NotificationManager.class);
    }

    @Override
    public int getNotificationId() {
        return NOTIFICATION_ID_SERVICE;
    }

    public static void refreshChannelsOnLocaleChange(Context context) {
        CICOForegroundController dummyController = new CICOForegroundController(context);
        dummyController.ensureNotificationGroup();
        dummyController.ensureServiceNotificationChannel();
        dummyController.ensureAlertsNotificationChannel();
    }

    private void ensureNotificationGroup() {
        if (createGroupCalled) {
            return;
        }
        createGroupCalled = true;
        NotificationChannelGroup group = new NotificationChannelGroup(NOTIFICATION_GROUP, context.getString(R.string.cico_notification_group_name));
        notificationManager.createNotificationChannelGroup(group);
    }

    private void ensureServiceNotificationChannel() {
        if (createServiceChannelCalled) {
            return;
        }
        createServiceChannelCalled = true;
        ensureNotificationGroup();
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_SERVICE, context.getString(R.string.cico_notification_channel_name_service), NotificationManager.IMPORTANCE_LOW);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        channel.setShowBadge(false);
        channel.setGroup(NOTIFICATION_GROUP);
        notificationManager.createNotificationChannel(channel);
    }

    private void ensureAlertsNotificationChannel() {
        if (createAlertsChannelCalled) {
            return;
        }
        createAlertsChannelCalled = true;
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ALERTS, context.getString(R.string.cico_notification_channel_name_alerts), NotificationManager.IMPORTANCE_HIGH);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        channel.setShowBadge(true);
        channel.setLightColor(context.getColor(R.color.cico_notification_alerts_light_color));
        channel.setGroup(NOTIFICATION_GROUP);
        notificationManager.createNotificationChannel(channel);
    }

    private NotificationCompat.Builder serviceNotification;
    private NotificationCompat.Action showTicketAction;
    private NotificationCompat.Action checkOutAction;
    private Notification lastBuiltNotification;
    private boolean notifHasBigContent = false;
    private String notifBigContentIdentifier = null;
    private CharSequence currentSmallContentText;

    private void ensureNotification() {
        if (serviceNotification != null) {
            return;
        }
        ensureServiceNotificationChannel();
        serviceNotification = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_SERVICE)
                .setOngoing(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                //.setColorized(true)
                //.setColor(context.getColor(R.color.cico_notification_service_color))
                .setSmallIcon(R.drawable.ic_transit_ticket_24px)
                .setContentTitle(context.getString(R.string.cico_notification_title))
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle());

        setSmallContentText(context.getString(R.string.cico_notification_text_idle));

        showTicketAction = new NotificationCompat.Action(
                null,
                context.getString(R.string.cico_notification_action_show_ticket),
                PendingIntent.getActivity(
                        context,
                        1,
                        new Intent(context, CICOTicketDisplayActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                )
        );

        checkOutAction = new NotificationCompat.Action(
                null,
                context.getString(R.string.cico_notification_action_check_out),
                PendingIntent.getActivity(
                        context,
                        1,
                        new Intent(context, CheckOutActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)
        );

        serviceNotification.addAction(checkOutAction);

        buildNotification(); //so that lastBuiltNotification is not null
    }

    private boolean setSmallContentText(CharSequence text) {
        if (currentSmallContentText != null && text.toString().equals(currentSmallContentText.toString())) {
            // must compare strings, because ForegroundColorSpan does not override equals()
            return false;
        }
        currentSmallContentText = text;
        serviceNotification.setContentText(text);
        return true;
    }

    private Notification buildNotification() {
        return (lastBuiltNotification = serviceNotification.build());
    }

    @Override
    public Notification createNotification() {
        ensureNotification();
        return buildNotification();
    }

    @Override
    public void onServiceStateChanged(CICOService.ServiceState state) {
        if (!state.hasFlag(CICOService.ServiceState.FLAG_FOREGROUND_SERVICE_ACTIVE)) {
            return;
        }

        ensureNotification();

        boolean notificationChanged = false;
        boolean shouldHaveShowTicketAction = state.currentTicket() != null;
        boolean hasShowTicketAction = lastBuiltNotification.actions.length == 2;
        if (shouldHaveShowTicketAction != hasShowTicketAction) {
            serviceNotification.clearActions();
            if (shouldHaveShowTicketAction) {
                serviceNotification.addAction(showTicketAction);
            }
            serviceNotification.addAction(checkOutAction);
            notificationChanged = true;
        }
        String newBigContentIdentifier = createDeviceDetailIdentifier(state.currentDevice());
        if (!newBigContentIdentifier.equals(notifBigContentIdentifier)) {
            notifBigContentIdentifier = newBigContentIdentifier;
            RemoteViews detailViews = createDeviceDetailViews(state.currentDevice());
            if (detailViews == null) {
                if (notifHasBigContent) {
                    notifHasBigContent = false;
                    serviceNotification.setCustomBigContentView(null);
                    notificationChanged = true;
                }
            } else {
                notifHasBigContent = true;
                serviceNotification.setCustomBigContentView(detailViews);
                notificationChanged = true;
            }
        }
        if (state.currentDevice() == null) {
            notificationChanged |= setSmallContentText(context.getString(R.string.cico_notification_text_idle));
        } else {
            notificationChanged |= setSmallContentText(getShortDeviceInfo(state.currentDevice(), notifHasBigContent));
        }

        if (notificationChanged) {
            notificationManager.notify(NOTIFICATION_ID_SERVICE, buildNotification());
        }
    }

    private CharSequence getShortDeviceInfo(LwtDevice device, boolean hasBigInfo) {
        if (device instanceof LwtDevice.Vehicle vehicle) {
            var advData = vehicle.getAdvData();
            if (advData != null) {
                CharSequence lineName;
                CharSequence direction = null;
                if (advData instanceof TripAdvertisementDataExt ext) {
                    ensureTripViewController();
                    // plain text, as we do not want the background/foreground color in the notification
                    lineName = TextMarkupConverter.toPlainText(ext.getLineName(), false);
                    textMarkupConverter.setFallbackTintMode(TextMarkupConverter.TINT_MODE_FOLLOW_SYSTEM);
                    direction = textMarkupConverter.toSpannableString(ext.getHeadsign());
                } else {
                    lineName = advData.isTrain() ? advData.getParsedTrainLineNumber() : String.valueOf(advData.getLineLicenseNumber());
                }
                int format;
                if (direction == null) {
                    format = R.string.cico_notification_text_on_line_legacy;
                } else {
                    format = hasBigInfo ? R.string.cico_notification_text_on_line_expandable : R.string.cico_notification_text_on_line_standalone;
                }
                CharSequence formatString = context.getText(format);
                return TextUtils.replace(formatString, new String[]{"$ROUTE", "$DIRECTION"}, new CharSequence[]{lineName, direction});
            }
        }
        return "";
    }

    private String createDeviceDetailIdentifier(LwtDevice device) {
        if (device instanceof LwtDevice.Vehicle vehicle) {
            TripAdvertisementData advData = vehicle.getAdvData();
            if (advData != null) {
                List<Object> parts = List.of(
                        advData.getLineType(),
                        advData.getLineLicenseNumber(),
                        advData.getStopCisNumber(),
                        advData.getDirectionCisNumber(),
                        advData.getDelay(),
                        advData.isAtStop()
                );
                if (advData instanceof TripAdvertisementDataExt ext) {
                    parts = new ArrayList<>(parts);
                    parts.addAll(List.of(ext.getLineName(), ext.getHeadsign(), ext.getCurrentStopName()));
                }
                return String.join("|", CollectionUtils.toStringList(parts));
            }
        }
        return "";
    }

    private RemoteViews createDeviceDetailViews(LwtDevice device) {
        if (device instanceof LwtDevice.Vehicle vehicle) {
            return createVehicleDetailViews(vehicle);
        }
        return null;
    }

    private TextMarkupConverter textMarkupConverter;
    private DeviceListItemBinding tempDevListItem;
    private TripInfoViewController tempTripInfoView;

    private RemoteViews createVehicleDetailViews(LwtDevice.Vehicle vehicle) {
        if (vehicle.getAdvData() instanceof TripAdvertisementDataExt advData) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.device_list_item_remote);
            ensureTripViewController();
            textMarkupConverter.setFallbackTintMode(TextMarkupConverter.TINT_MODE_INTRINSIC);
            tempTripInfoView.bind(advData);

            var src = tempDevListItem;
            views.setTextViewText(R.id.tvLineNumber, src.tvLineNumber.getText());
            views.setTextViewText(R.id.tvHeadsign, src.tvHeadsign.getText());
            views.setTextViewText(R.id.tvNextStop, src.tvNextStop.getText());
            views.setTextViewText(R.id.tvDelayDisplay, src.tvDelayDisplay.getText());
            int lineNumBgRes = ResourcesCompat.ID_NULL;
            if (src.tvLineNumber.getBackground() != null) {
                lineNumBgRes = R.drawable.line_number_background_no_padding;
            }
            views.setImageViewResource(R.id.ivLineNumberBackground, lineNumBgRes);
            if (lineNumBgRes != ResourcesCompat.ID_NULL) {
                ColorStateList tintList = src.tvLineNumber.getBackgroundTintList();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !TEST_LEGACY_TINT) {
                    views.setColorStateList(R.id.ivLineNumberBackground, "setImageTintList", tintList);
                } else {
                    int color = tintList != null ? tintList.getDefaultColor() : Color.TRANSPARENT;
                    views.setInt(R.id.ivLineNumberBackground, "setColorFilter", color);
                }
            }

            return views;
        } else {
            // can not use non-extended data
            return null;
        }
    }

    private void ensureTripViewController() {
        if (tempTripInfoView == null) {
            // null font to force using fallback characters, as notifications can not use
            // our XML fonts (and users can override them anyway)
            textMarkupConverter = new TextMarkupConverter(null);
            tempDevListItem = DeviceListItemBinding.inflate(LayoutInflater.from(context));
            tempTripInfoView = new TripInfoViewController(tempDevListItem, textMarkupConverter);
        }
    }

    private void showErrorNotification(@StringRes int title, @StringRes int contentText, PendingIntent action) {
        Notification notification = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_no_transfer_24px)
                .setContentTitle(context.getString(title))
                .setContentText(context.getString(contentText))
                .setContentIntent(action)
                .build();

        notificationManager.notify(NOTIFICATION_ID_ERROR_BT_OFF, notification);
    }

    @Override
    public void onServiceError(CICOService.ErrorCode errorCode) {
        ensureAlertsNotificationChannel();
        if (errorCode == CICOService.ErrorCode.BLUETOOTH_TURNED_OFF) {
            showErrorNotification(
                    R.string.cico_notification_error_bluetooth_off_title,
                    R.string.cico_notification_error_bluetooth_off_text,
                    PendingIntent.getActivity(
                            context,
                            1,
                            new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                    )
            );
        }
    }

    @Override
    public void onServiceErrorResolved(CICOService.ErrorCode errorCode) {
        if (errorCode == CICOService.ErrorCode.BLUETOOTH_TURNED_OFF) {
            notificationManager.cancel(NOTIFICATION_ID_ERROR_BT_OFF);
        }
    }
}
