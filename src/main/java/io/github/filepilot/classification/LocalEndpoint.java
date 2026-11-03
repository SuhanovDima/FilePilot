package io.github.filepilot.classification;

import io.github.filepilot.config.PilotException;
import java.net.URI;
import java.util.Set;

public final class LocalEndpoint {
    private LocalEndpoint() {}
    public static URI validate(String value) {
        try {
            URI uri=URI.create(value); String host=uri.getHost();
            PilotException.require("http".equals(uri.getScheme()) && host != null && Set.of("localhost","127.0.0.1","::1","[::1]").contains(host)
                && uri.getPort()>0 && uri.getPort()<=65535 && "/v1".equals(uri.getPath())
                && uri.getUserInfo()==null && uri.getQuery()==null && uri.getFragment()==null,"LOCAL_ENDPOINT_REQUIRED");
            // Pin localhost to numeric IPv4, eliminating DNS rebinding/proxy resolution.
            return host.equals("localhost") ? URI.create("http://127.0.0.1:"+uri.getPort()+"/v1") : uri;
        } catch (IllegalArgumentException e) { throw new PilotException("LOCAL_ENDPOINT_REQUIRED"); }
    }
}
