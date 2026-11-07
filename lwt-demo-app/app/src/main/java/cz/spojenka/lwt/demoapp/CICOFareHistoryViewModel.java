package cz.spojenka.lwt.demoapp;

import android.app.Application;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import cz.spojenka.android.system.livedata.LiveErrorSignal;
import cz.spojenka.android.system.livedata.LiveList;
import cz.spojenka.lwt.ticketing.api.CICOClearedFare;
import cz.spojenka.lwt.ticketing.api.CICOSessionInfo;
import cz.spojenka.lwt.ticketing.api.PagedResponse;
import cz.spojenka.lwt.ticketing.client.TicketingClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.HttpException;
import retrofit2.Response;

public class CICOFareHistoryViewModel extends AndroidViewModel {

    private static final int PAGING_SIZE = 20;

    private final TicketingClient ticketingClient;

    private final LiveList<CICOSessionInfo> activeSessions = new LiveList<>();
    private final LiveList<CICOClearedFare> fareHistory = new LiveList<>();

    private final MutableLiveData<Boolean> hasMoreActiveSessions = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> hasMoreFareHistory = new MutableLiveData<>(true);

    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(true);
    private final LiveErrorSignal loadError = new LiveErrorSignal();

    private final MutableLiveData<CICOClearedFare> evaluatedSessionResult = new MutableLiveData<>(null);
    private final LiveErrorSignal evaluateSessionError = new LiveErrorSignal();

    private Call<?> activeSessionsCall;
    private Call<?> fareHistoryCall;
    private Call<CICOClearedFare> evaluateSessionCall;

    public CICOFareHistoryViewModel(@NonNull Application application) {
        super(application);
        ticketingClient = TicketingRepository.getInstance(application).createTicketingClient();
        addCloseable(this::cancelLoad);
    }

    private void cancelLoad() {
        if (activeSessionsCall != null) {
            activeSessionsCall.cancel();
            activeSessionsCall = null;
        }
        if (fareHistoryCall != null) {
            fareHistoryCall.cancel();
            fareHistoryCall = null;
        }
    }

    private void resetData() {
        if (!activeSessions.isEmpty()) {
            activeSessions.clear();
        }
        if (!fareHistory.isEmpty()) {
            fareHistory.clear();
        }
        hasMoreActiveSessions.setValue(true);
        hasMoreFareHistory.setValue(true);
        isLoading.setValue(true);
    }

    public void load() {
        cancelLoad();
        resetData();
        loadMoreActiveSessions();
        loadMoreFareHistory();
    }

    public LiveList<CICOSessionInfo> getActiveSessions() {
        return activeSessions;
    }

    public LiveList<CICOClearedFare> getFareHistory() {
        return fareHistory;
    }

    public LiveData<Boolean> getHasMoreActiveSessions() {
        return hasMoreActiveSessions;
    }

    public LiveData<Boolean> getHasMoreFareHistory() {
        return hasMoreFareHistory;
    }

    public LiveErrorSignal getLoadError() {
        return loadError;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    private void updateIsLoadingLiveData() {
        if (loadError.hasError()) {
            isLoading.setValue(false);
        } else {
            if (activeSessions.isInitialized() && fareHistory.isInitialized()) {
                isLoading.setValue(false);
            }
        }
    }

    public void loadMoreActiveSessions() {
        activeSessionsCall = tryLoadNextPage(activeSessions, hasMoreActiveSessions, page -> ticketingClient.getAccountsAPI().getCicoSessions(false, page, PAGING_SIZE));
    }

    public void loadMoreFareHistory() {
        fareHistoryCall = tryLoadNextPage(fareHistory, hasMoreFareHistory, page -> ticketingClient.getAccountsAPI().getCicoFareHistory(page, PAGING_SIZE));
    }

    private <T> Call<PagedResponse<T>> tryLoadNextPage(LiveList<T> dest, MutableLiveData<Boolean> hasMoreDest, Function<Integer, Call<PagedResponse<T>>> loader) {
        if (Boolean.TRUE.equals(hasMoreDest.getValue())) {
            var call = loader.apply(dest.size() / PAGING_SIZE);
            call.enqueue(new Callback<>() {
                @Override
                public void onResponse(@NonNull Call<PagedResponse<T>> call, @NonNull Response<PagedResponse<T>> response) {
                    if (!response.isSuccessful()) {
                        onFailure(call, new HttpException(response));
                        return;
                    }
                    PagedResponse<T> data = Objects.requireNonNull(response.body());
                    dest.addAll(data.content());
                    if (data.page().number() >= data.page().totalPages() - 1) {
                        hasMoreDest.setValue(false);
                    }
                    updateIsLoadingLiveData();
                }

                @Override
                public void onFailure(@NonNull Call<PagedResponse<T>> call, @NonNull Throwable t) {
                    loadError.setValue(t);
                    updateIsLoadingLiveData();
                }
            });
            return call;
        }
        return null;
    }

    private void cancelEvaluateSession() {
        if (evaluateSessionCall != null) {
            evaluateSessionCall.cancel();
            evaluateSessionCall = null;
        }
    }

    public void evaluateSession(UUID sessionId) {
        cancelEvaluateSession();
        evaluateSessionCall = ticketingClient.getCicoAPI().evaluateSession(sessionId);
        evaluateSessionCall.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<CICOClearedFare> call, @NonNull Response<CICOClearedFare> response) {
                if (!response.isSuccessful()) {
                    onFailure(call, new HttpException(response));
                } else {
                    evaluatedSessionResult.setValue(Objects.requireNonNull(response.body()));
                }
            }

            @Override
            public void onFailure(@NonNull Call<CICOClearedFare> call, @NonNull Throwable t) {
                evaluateSessionError.setValue(t);
            }
        });
    }

    public LiveData<CICOClearedFare> getEvaluatedSessionResult() {
        return evaluatedSessionResult;
    }

    public LiveErrorSignal getEvaluateSessionError() {
        return evaluateSessionError;
    }

    public void ackEvaluatedSessionResult() {
        evaluatedSessionResult.setValue(null);
    }
}
