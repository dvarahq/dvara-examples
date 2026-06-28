import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Thin, idempotent wrapper over the {@code gcloud} / {@code kubectl} CLIs — the GCP
 * analog of {@code dvara-infra/jbang/DoApi.java} (which talks the DO REST API). We shell
 * out via {@link Proc} instead of using the GCP Java SDK: it mirrors the reference
 * {@code deploy.sh} command-for-command, inherits {@code gcloud} auth/ADC, and stays
 * unit-testable (inject a recording {@link Proc}).
 *
 * <p>Every provisioning method follows {@code describe → (exists? skip : create)} so a
 * re-run after a partial failure resumes — same contract as {@code deploy.sh}.
 */
public final class Gcloud {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Proc proc;
    private final String project;

    public Gcloud(Proc proc, String project) {
        this.proc = proc;
        this.project = project;
    }

    // -- low-level -----------------------------------------------------------

    private List<String> base(String... args) {
        var cmd = new ArrayList<String>();
        cmd.add("gcloud");
        cmd.add("--project=" + project);
        cmd.add("--quiet");
        for (String a : args) cmd.add(a);
        return cmd;
    }

    private Proc.Result gcloud(String... args) { return proc.run(base(args)); }

    private boolean exists(String... describeArgs) {
        return gcloud(describeArgs).ok();
    }

    // -- 1. APIs -------------------------------------------------------------

    public void enableApis(List<String> apis) {
        var cmd = base("services", "enable");
        cmd.addAll(apis);
        Proc.Result r = proc.run(cmd);
        require(r, "enable APIs: " + apis);
    }

    // -- 2. Private services access (so Cloud SQL gets a private IP) ----------

    public void ensurePrivateServicesAccess(String network) {
        String addr = "google-managed-services-" + network;
        if (!exists("compute", "addresses", "describe", addr, "--global")) {
            require(gcloud("compute", "addresses", "create", addr,
                    "--global", "--purpose=VPC_PEERING", "--prefix-length=16",
                    "--network=" + network), "create peering range");
        }
        // peering connect is naturally idempotent (errors if already connected → tolerate)
        proc.run(base("services", "vpc-peerings", "connect",
                "--service=servicenetworking.googleapis.com",
                "--ranges=" + addr, "--network=" + network));
    }

    // -- 3. GKE cluster (VPC-native + Workload Identity) ---------------------

    public void ensureCluster(String cluster, String region, String network, String mode,
                              int nodeCount, String machineType, boolean secretManagerAddon) {
        if (exists("container", "clusters", "describe", cluster, "--region=" + region)) return;
        boolean autopilot = mode.equalsIgnoreCase("autopilot");
        var cmd = base("container", "clusters",
                autopilot ? "create-auto" : "create",
                cluster, "--region=" + region, "--network=" + network);
        if (!autopilot) {
            // Autopilot enables Workload Identity + VPC-native (ip-alias) by default
            // and REJECTS these flags; only Standard clusters need them explicitly.
            cmd.add("--workload-pool=" + project + ".svc.id.goog");
            cmd.add("--enable-ip-alias");
            cmd.add("--num-nodes=" + nodeCount);
            cmd.add("--machine-type=" + machineType);
        }
        if (secretManagerAddon) cmd.add("--enable-secret-manager"); // CONFIRM per GKE version (see deploy.sh)
        require(proc.run(cmd), "create cluster " + cluster);
    }

    public void getCredentials(String cluster, String region, Map<String, String> env) {
        var cmd = base("container", "clusters", "get-credentials", cluster, "--region=" + region);
        require(proc.run(cmd, env, null), "get-credentials " + cluster);
    }

    // -- 4. Cloud SQL (private IP) -------------------------------------------

    public void ensureCloudSql(String instance, String region, String network, String tier, String pgVersion) {
        if (exists("sql", "instances", "describe", instance)) return;
        // Pin ENTERPRISE edition: new Cloud SQL instances default to ENTERPRISE_PLUS,
        // which rejects db-custom-* tiers (requires db-perf-optimized-*). ENTERPRISE
        // accepts custom tiers and is the cheaper edition — right for a smoke test.
        require(gcloud("sql", "instances", "create", instance,
                "--database-version=" + pgVersion, "--tier=" + tier, "--edition=ENTERPRISE",
                "--region=" + region,
                "--network=projects/" + project + "/global/networks/" + network,
                "--no-assign-ip"), "create Cloud SQL " + instance);
    }

    public void ensureDatabase(String instance, String db) {
        if (!exists("sql", "databases", "describe", db, "--instance=" + instance))
            require(gcloud("sql", "databases", "create", db, "--instance=" + instance),
                    "create database " + db);
    }

    public void ensureUser(String instance, String user, String password) {
        // `sql users list` returns exit 0 even with NO match, so an exit-based
        // exists() check wrongly reports the user present and skips creation
        // (Postgres then rejects the connection as "password authentication
        // failed"). Check the filtered output is non-empty instead.
        Proc.Result r = gcloud("sql", "users", "list", "--instance=" + instance,
                "--filter=name=" + user, "--format=value(name)");
        if (!r.ok() || r.out().isBlank()) {
            require(gcloud("sql", "users", "create", user, "--instance=" + instance,
                    "--password=" + password), "create user " + user);
        }
    }

    public String cloudSqlConnectionName(String instance) {
        return field(gcloud("sql", "instances", "describe", instance, "--format=json"), "connectionName");
    }

    public String cloudSqlPrivateIp(String instance) {
        Proc.Result r = gcloud("sql", "instances", "describe", instance, "--format=json");
        require(r, "describe Cloud SQL " + instance);
        try {
            for (JsonNode ip : JSON.readTree(r.out()).path("ipAddresses")) {
                if ("PRIVATE".equals(ip.path("type").asText())) return ip.path("ipAddress").asText();
            }
        } catch (Exception e) { throw new RuntimeException("parse Cloud SQL ip", e); }
        throw new IllegalStateException("no PRIVATE ip on " + instance);
    }

    // -- 5. Secret Manager + Workload Identity (secret-manager mode) ---------

    public void ensureSecret(String name, String value) {
        if (!exists("secrets", "describe", name))
            require(proc.run(base("secrets", "create", name, "--data-file=-"), Map.of(), value),
                    "create secret " + name);
        else
            require(proc.run(base("secrets", "versions", "add", name, "--data-file=-"), Map.of(), value),
                    "add secret version " + name);
    }

    public String ensureServiceAccount(String saName) {
        String email = saName + "@" + project + ".iam.gserviceaccount.com";
        if (!exists("iam", "service-accounts", "describe", email))
            require(gcloud("iam", "service-accounts", "create", saName,
                    "--display-name=Dvara GKE workload identity"), "create GSA " + saName);
        return email;
    }

    public void bindWorkloadIdentity(String gsaEmail, String namespace, String ksa) {
        require(gcloud("iam", "service-accounts", "add-iam-policy-binding", gsaEmail,
                "--role=roles/iam.workloadIdentityUser",
                "--member=serviceAccount:" + project + ".svc.id.goog[" + namespace + "/" + ksa + "]"),
                "bind WI " + ksa);
        require(gcloud("projects", "add-iam-policy-binding", project,
                "--role=roles/secretmanager.secretAccessor",
                "--member=serviceAccount:" + gsaEmail), "grant secretAccessor");
    }

    // -- teardown ------------------------------------------------------------

    public void deleteCluster(String cluster, String region) {
        if (exists("container", "clusters", "describe", cluster, "--region=" + region))
            require(gcloud("container", "clusters", "delete", cluster, "--region=" + region),
                    "delete cluster " + cluster);
    }

    public void deleteCloudSql(String instance) {
        if (exists("sql", "instances", "describe", instance))
            require(gcloud("sql", "instances", "delete", instance), "delete Cloud SQL " + instance);
    }

    // -- helpers -------------------------------------------------------------

    private static void require(Proc.Result r, String what) {
        if (!r.ok()) throw new RuntimeException("gcloud failed [" + what + "] exit=" + r.exit()
                + (r.errOut().isBlank() ? "" : "\n" + r.errOut()));
    }

    private static String field(Proc.Result r, String key) {
        require(r, "read " + key);
        try { return JSON.readTree(r.out()).path(key).asText(); }
        catch (Exception e) { throw new RuntimeException("parse field " + key, e); }
    }

    public static List<String> requiredApis() {
        return List.of(
                "container.googleapis.com", "compute.googleapis.com",
                "sqladmin.googleapis.com", "servicenetworking.googleapis.com",
                "secretmanager.googleapis.com", "iam.googleapis.com");
    }
}
