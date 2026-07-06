package cz.spojenka.android.system;

import android.content.Context;
import android.content.res.Configuration;

public abstract class ConfigurationValue<T> {

    private Configuration currentConfiguration;
    private T currentValue;

    public T get(Context context) {
        Configuration newConfiguration = context.getResources().getConfiguration();
        if (currentConfiguration == null || !currentConfiguration.equals(newConfiguration)) {
            currentConfiguration = newConfiguration;
            currentValue = fetch(context);
        }
        return currentValue;
    }

    protected abstract T fetch(Context context);
}
