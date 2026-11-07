package cz.spojenka.android.system;

import android.content.Context;
import androidx.annotation.StringRes;

public class IndString extends IndirectableResource<String> {
    public IndString(String resource) {
        super(resource);
    }

    public IndString(@StringRes int id) {
        super(id);
    }

    public static IndString ofNull() {
        return new IndString(null);
    }

    @Override
    protected String resolve(Context context) {
        return context.getString(this.id);
    }
}
