package cz.spojenka.lwdn;

import java.io.IOException;

public interface LwdnSocketFactory {

    public LwdnSocket openSocket() throws IOException;
}
