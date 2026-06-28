import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Captured provisioning state, persisted as JSON next to the values file (like
 * {@code DvaraInfra.State}). Lets {@code install}/{@code smoke-test}/{@code destroy}
 * resume without re-deriving resource names, and makes a partial-failure re-run safe.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public final class GkeState {

    private static final ObjectMapper JSON =
            new ObjectMapper().enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);

    public String projectId;
    public String region;
    public String network;
    public String subnet;
    public String cluster;
    public String clusterMode;          // autopilot | standard
    public String cloudsqlInstance;
    public String cloudsqlConnectionName;
    public String cloudsqlPrivateIp;
    public String dbName;
    public String dbUser;
    public String namespace;
    public String kubeconfigPath;
    public String secretMode;           // k8s | secret-manager
    public String gsaEmail;             // workload-identity Google SA (secret-manager mode)
    public String ksaName;              // kubernetes SA bound to the GSA
    public String domainBase;
    public String ingressIp;
    public String imageTag;
    public String helmRelease;

    public static GkeState load(Path file) throws IOException {
        if (file == null || !Files.exists(file)) return new GkeState();
        return JSON.readValue(Files.readString(file), GkeState.class);
    }

    public void save(Path file) throws IOException {
        Files.writeString(file, JSON.writeValueAsString(this));
    }

    public String toJson() throws IOException { return JSON.writeValueAsString(this); }

    public void print() {
        System.out.println("project        : " + projectId);
        System.out.println("region         : " + region);
        System.out.println("cluster        : " + cluster + " (" + clusterMode + ")");
        System.out.println("namespace      : " + namespace);
        System.out.println("kubeconfig     : " + kubeconfigPath);
        System.out.println("cloud sql      : " + cloudsqlInstance
                + "  conn=" + cloudsqlConnectionName + "  privateIp=" + cloudsqlPrivateIp);
        System.out.println("secret mode    : " + secretMode
                + (gsaEmail != null ? "  gsa=" + gsaEmail : ""));
        System.out.println("domain / ip    : " + (domainBase != null ? domainBase : "(ip-only)")
                + "  ingressIp=" + ingressIp);
        System.out.println("helm release   : " + helmRelease + "  imageTag=" + imageTag);
    }
}
