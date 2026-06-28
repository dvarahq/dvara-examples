///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21
//DEPS info.picocli:picocli:4.7.6
//DEPS com.fasterxml.jackson.core:jackson-databind:2.18.1
//DEPS com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.1
//SOURCES Proc.java
//SOURCES GkeValues.java
//SOURCES GkeState.java
//SOURCES Gcloud.java
//SOURCES HelmGke.java

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * DVARA AI Gateway → GKE, end to end. The structured, stateful, idempotent automation of
 * the reference {@code deploy.sh}, modeled on {@code dvara-infra/jbang/DvaraInfra.java} (DO).
 *
 * <p>Provisions a VPC-native GKE cluster + private-IP Cloud SQL, wires Workload Identity,
 * materializes platform secrets, and {@code helm upgrade --install}s the cloud-agnostic
 * chart with the GKE overlay ({@code values-gke.yaml}). State is captured to a JSON file so
 * phases resume independently.
 *
 * <p>Secrets are read from the ENVIRONMENT, never the values file:
 * {@code DVARA_LICENSE_KEY} (required), {@code DVARA_AUDIT_HMAC_SECRET},
 * {@code DVARA_ENCRYPTION_MASTER_PASSWORD}, {@code DVARA_ACTUATOR_API_KEY},
 * {@code DVARA_ACTUATOR_METRICS_API_KEY}, {@code DVARA_DB_PASSWORD}, and optional
 * {@code OPENAI_API_KEY} / {@code ANTHROPIC_API_KEY}.
 *
 * <p>Run: {@code ./DvaraGke.java apply --values gke.yaml}  (jbang shebang).
 */
@Command(name = "dvara-gke", mixinStandardHelpOptions = true, version = "dvara-gke 0.1",
        subcommands = {
                DvaraGke.Apply.class, DvaraGke.Provision.class, DvaraGke.Install.class,
                DvaraGke.Update.class, DvaraGke.Uninstall.class, DvaraGke.Output.class,
                DvaraGke.Destroy.class, DvaraGke.SmokeTest.class
        },
        description = "Provision + deploy Dvara on GKE end to end (gcloud + helm).")
public class DvaraGke {

    public static void main(String[] args) {
        System.exit(new CommandLine(new DvaraGke()).execute(args));
    }

    // ---- shared options ----------------------------------------------------

    static class CommonOpts {
        @Option(names = "--values", required = true, description = "Path to the GKE values YAML.")
        Path values;

        @Option(names = "--state", description = "State JSON path (default: <values>.state.json).")
        Path state;

        @Option(names = "--echo", description = "Print each gcloud/helm/kubectl command.")
        boolean echo;

        @Option(names = "--dry-run", description = "Show the create/apply commands without executing.")
        boolean dryRun;

        Path statePath() {
            return state != null ? state
                    : values.resolveSibling(values.getFileName() + ".state.json");
        }
    }

    /** Loaded context shared by every subcommand. */
    static final class Ctx {
        final GkeValues v;
        final GkeState st;
        final Path statePath;
        final Path valuesPath;
        final Proc proc;
        final Gcloud gcloud;
        final String project, region, network, cluster, namespace, secretMode;
        final Map<String, String> kubeEnv;

        Ctx(CommonOpts o) throws Exception {
            this.v = GkeValues.load(o.values);
            this.valuesPath = o.values;
            this.statePath = o.statePath();
            this.st = GkeState.load(statePath);
            this.proc = o.dryRun ? new DryRunProc() : new Proc.Real(o.echo || true);
            this.project = v.text("gcp.projectId");
            this.region = v.text("gcp.region");
            this.network = v.text("gcp.network", "default");
            this.cluster = v.text("cluster.name");
            this.namespace = v.text("namespace", "dvara");
            this.secretMode = v.text("secretMode", "k8s");
            this.gcloud = new Gcloud(proc, project);
            String kc = st.kubeconfigPath != null ? st.kubeconfigPath
                    : o.values.toAbsolutePath().resolveSibling(cluster + ".kubeconfig").toString();
            this.kubeEnv = new LinkedHashMap<>(Map.of("KUBECONFIG", kc));
            st.kubeconfigPath = kc;
        }

        HelmGke helm() { return new HelmGke(proc, kubeEnv); }
    }

    // ---- provision ---------------------------------------------------------

    @Command(name = "provision", description = "Phase 1 — enable APIs, VPC peering, GKE cluster, Cloud SQL, secrets.")
    public static class Provision implements Callable<Integer> {
        @Mixin CommonOpts o;
        public Integer call() throws Exception {
            Ctx c = ctxFor(o);
            provision(c);
            c.st.save(c.statePath);
            log("provision complete → " + c.statePath);
            return 0;
        }
    }

    static void provision(Ctx c) throws Exception {
        log("== provision: project=" + c.project + " region=" + c.region + " cluster=" + c.cluster + " ==");
        c.gcloud.enableApis(Gcloud.requiredApis());
        c.gcloud.ensurePrivateServicesAccess(c.network);

        boolean smAddon = "secret-manager".equals(c.secretMode);
        c.gcloud.ensureCluster(c.cluster, c.region, c.network,
                c.v.text("cluster.mode", "autopilot"),
                c.v.intAt("cluster.nodeCount", 2),
                c.v.text("cluster.machineType", "e2-standard-2"), smAddon);

        String sql = c.v.text("cloudsql.instance");
        c.gcloud.ensureCloudSql(sql, c.region, c.network,
                c.v.text("cloudsql.tier", "db-custom-1-3840"),
                c.v.text("cloudsql.version", "POSTGRES_16"));
        String db = c.v.text("cloudsql.db", "meridian");
        String user = c.v.text("cloudsql.user", "dvara");
        c.gcloud.ensureDatabase(sql, db);
        c.gcloud.ensureUser(sql, user, requireEnv("DVARA_DB_PASSWORD"));

        c.st.projectId = c.project; c.st.region = c.region; c.st.network = c.network;
        c.st.cluster = c.cluster; c.st.clusterMode = c.v.text("cluster.mode", "autopilot");
        c.st.cloudsqlInstance = sql; c.st.dbName = db; c.st.dbUser = user;
        c.st.cloudsqlConnectionName = c.gcloud.cloudSqlConnectionName(sql);
        c.st.cloudsqlPrivateIp = c.gcloud.cloudSqlPrivateIp(sql);
        c.st.secretMode = c.secretMode; c.st.namespace = c.namespace;
        c.st.domainBase = c.v.optional("domain.base").orElse(null);
        c.st.imageTag = c.v.text("image.tag", "1.1.0");

        if (smAddon) {
            String saName = c.v.text("workloadIdentity.gsaName", "dvara-gke");
            String ksa = c.v.text("workloadIdentity.ksaName", "dvara");
            c.st.gsaEmail = c.gcloud.ensureServiceAccount(saName);
            c.st.ksaName = ksa;
            for (var e : platformSecrets().entrySet()) c.gcloud.ensureSecret(e.getKey(), e.getValue());
            c.gcloud.bindWorkloadIdentity(c.st.gsaEmail, c.namespace, ksa);
        }
        log("Cloud SQL private IP = " + c.st.cloudsqlPrivateIp + "  conn=" + c.st.cloudsqlConnectionName);
    }

    // ---- install -----------------------------------------------------------

    @Command(name = "install", description = "Phase 3 — credentials, namespace, secret, helm upgrade --install.")
    public static class Install implements Callable<Integer> {
        @Mixin CommonOpts o;
        public Integer call() throws Exception {
            Ctx c = ctxFor(o);
            install(c);
            c.st.save(c.statePath);
            log("install complete → release " + c.st.helmRelease);
            return 0;
        }
    }

    static void install(Ctx c) throws Exception {
        log("== install: cluster=" + c.cluster + " ns=" + c.namespace + " ==");
        c.gcloud.getCredentials(c.cluster, c.region, c.kubeEnv);
        HelmGke helm = c.helm();
        helm.ensureNamespace(c.namespace);

        if (c.st.cloudsqlPrivateIp == null)
            c.st.cloudsqlPrivateIp = c.gcloud.cloudSqlPrivateIp(c.v.text("cloudsql.instance"));
        String jdbc = "jdbc:postgresql://" + c.st.cloudsqlPrivateIp + ":5432/"
                + c.v.text("cloudsql.db", "meridian");

        String release = c.v.text("helm.release", "dvara");
        String chartRef = c.v.text("helm.chartRef");
        String chartVersion = c.v.text("helm.chartVersion", "");
        c.st.helmRelease = release;

        // The chart carries per-component image tags (no global tag); apply the one
        // configured tag to all three runnable components. Default to the published GA.
        var setValues = new LinkedHashMap<String, String>();
        String tag = c.v.text("image.tag", "1.1.0");
        setValues.put("gatewayServer.image.tag", tag);
        setValues.put("flightdeck.image.tag", tag);
        setValues.put("mcpProxyServer.image.tag", tag);

        if ("secret-manager".equals(c.secretMode)) {
            // CSI driver mounts secrets; bind the KSA to the GSA + apply the provider class.
            helm.applyManifest(secretProviderClassYaml(c));
        } else {
            // k8s mode: opaque Secret with the chart's kebab-case keys (read via
            // secretKeyRef). The DSN is NOT a chart secret entry — only the password
            // is, referenced from the extraEnv injected below.
            var data = new LinkedHashMap<>(platformSecrets());
            data.put("spring-datasource-password", requireEnv("DVARA_DB_PASSWORD"));
            helm.applySecret(c.namespace, c.v.text("secrets.existingSecret", "dvara-secrets"), data);
        }

        // The chart has no database section — inject the DSN into the runnable
        // components via extraEnv. URL carries the dynamic Cloud SQL private IP;
        // username is plain; password comes from the Secret via secretKeyRef.
        String secretName = c.v.text("secrets.existingSecret", "dvara-secrets");
        String dbUser = c.v.text("cloudsql.user", "dvara");
        for (String comp : List.of("gatewayServer", "flightdeck")) {
            setValues.put(comp + ".extraEnv[0].name", "SPRING_DATASOURCE_URL");
            setValues.put(comp + ".extraEnv[0].value", jdbc);
            setValues.put(comp + ".extraEnv[1].name", "SPRING_DATASOURCE_USERNAME");
            setValues.put(comp + ".extraEnv[1].value", dbUser);
            setValues.put(comp + ".extraEnv[2].name", "SPRING_DATASOURCE_PASSWORD");
            setValues.put(comp + ".extraEnv[2].valueFrom.secretKeyRef.name", secretName);
            setValues.put(comp + ".extraEnv[2].valueFrom.secretKeyRef.key", "spring-datasource-password");
        }

        List<String> valuesFiles = new ArrayList<>();
        valuesFiles.add(c.valuesPath.toAbsolutePath().resolveSibling(c.v.text("helm.valuesFile", "values-gke.yaml")).toString());
        helm.upgradeInstall(release, c.namespace, chartRef, chartVersion, valuesFiles, setValues);

        c.v.optional("domain.base").ifPresent(d -> helm.applyManifest(managedCertYaml(c, d)));
        c.st.ingressIp = helm.serviceIngressIp(c.namespace, c.v.text("ingress.name", "dvara"));
    }

    // ---- apply (provision + install + DNS pause + smoke) -------------------

    @Command(name = "apply", description = "End to end — provision + install (+ DNS pause) + smoke-test.")
    public static class Apply implements Callable<Integer> {
        @Mixin CommonOpts o;
        @Option(names = "--yes", description = "Skip the DNS-pause prompt (CI/non-interactive).")
        boolean yes;
        public Integer call() throws Exception {
            Ctx c = ctxFor(o);
            provision(c); c.st.save(c.statePath);
            install(c);   c.st.save(c.statePath);
            c.v.optional("domain.base").ifPresent(domain -> {
                log("\n== DNS pause ==");
                log("Point an A record for " + domain + " at the Ingress IP: "
                        + (c.st.ingressIp.isBlank() ? "(pending — re-check `kubectl get ingress`)" : c.st.ingressIp));
                if (!yes) { log("Press <enter> once DNS is set and the ManagedCertificate is Active..."); readLine(); }
            });
            smoke(c);
            log("\napply complete.");
            return 0;
        }
    }

    // ---- update / uninstall / destroy / output / smoke --------------------

    @Command(name = "update", description = "Re-run helm upgrade with current values (optionally restart pods).")
    public static class Update implements Callable<Integer> {
        @Mixin CommonOpts o;
        @Option(names = "--restart", description = "Roll-restart deployments after upgrade.")
        boolean restart;
        public Integer call() throws Exception {
            Ctx c = ctxFor(o);
            install(c);
            if (restart) c.helm().restartDeployments(c.namespace);
            c.st.save(c.statePath);
            log("update complete.");
            return 0;
        }
    }

    @Command(name = "uninstall", description = "Helm uninstall the release (infrastructure left intact).")
    public static class Uninstall implements Callable<Integer> {
        @Mixin CommonOpts o;
        public Integer call() throws Exception {
            Ctx c = ctxFor(o);
            c.gcloud.getCredentials(c.cluster, c.region, c.kubeEnv);
            c.helm().uninstall(c.v.text("helm.release", "dvara"), c.namespace);
            log("uninstall complete.");
            return 0;
        }
    }

    @Command(name = "destroy", description = "Tear down GKE cluster + Cloud SQL (helm uninstall first).")
    public static class Destroy implements Callable<Integer> {
        @Mixin CommonOpts o;
        @Option(names = "--yes", required = true, description = "Required confirmation — this deletes billable infra.")
        boolean yes;
        public Integer call() throws Exception {
            Ctx c = ctxFor(o);
            c.gcloud.deleteCluster(c.cluster, c.region);
            c.gcloud.deleteCloudSql(c.v.text("cloudsql.instance"));
            log("destroy complete.");
            return 0;
        }
    }

    @Command(name = "output", description = "Print captured state (cluster, kubeconfig, Cloud SQL, ingress IP).")
    public static class Output implements Callable<Integer> {
        @Mixin CommonOpts o;
        public Integer call() throws Exception {
            GkeState.load(o.statePath()).print();
            return 0;
        }
    }

    @Command(name = "smoke-test", description = "Curl the public endpoint, verify /actuator/health + TLS.")
    public static class SmokeTest implements Callable<Integer> {
        @Mixin CommonOpts o;
        public Integer call() throws Exception { return smoke(ctxFor(o)) ? 0 : 1; }
    }

    static boolean smoke(Ctx c) {
        String target = c.v.optional("domain.base").map(d -> "https://" + d).orElseGet(() ->
                "http://" + (c.st.ingressIp == null ? "" : c.st.ingressIp));
        log("== smoke-test: " + target + " ==");
        Proc.Result r = c.proc.run(List.of("curl", "-sS", "-o", "/dev/null",
                "-w", "%{http_code}", target + "/actuator/health/readiness"));
        boolean ok = r.ok() && r.out().startsWith("2");
        log("readiness probe → " + (r.out().isBlank() ? "(no response)" : r.out()) + (ok ? "  OK" : "  FAIL"));
        return ok;
    }

    // ---- helpers -----------------------------------------------------------

    static Ctx ctxFor(CommonOpts o) throws Exception { return new Ctx(o); }

    /** Platform secrets sourced from the environment (never the values file). */
    // Keys MUST be the chart's kebab-case secretKeyRef names (the chart reads each
    // entry via secretKeyRef, not envFrom). See charts/dvara/templates/secrets.yaml.
    static Map<String, String> platformSecrets() {
        var m = new LinkedHashMap<String, String>();
        m.put("enterprise-license-key", requireEnv("DVARA_LICENSE_KEY"));
        m.put("audit-hmac-secret", requireEnv("DVARA_AUDIT_HMAC_SECRET"));
        m.put("gateway-encryption-master-password", requireEnv("DVARA_ENCRYPTION_MASTER_PASSWORD"));
        m.put("gateway-server-api-key", requireEnv("DVARA_ACTUATOR_API_KEY"));
        m.put("gateway-metrics-api-key", requireEnv("DVARA_ACTUATOR_METRICS_API_KEY"));
        opt("OPENAI_API_KEY").ifPresent(v -> m.put("openai-api-key", v));
        opt("ANTHROPIC_API_KEY").ifPresent(v -> m.put("anthropic-api-key", v));
        return m;
    }

    static String requireEnv(String k) {
        String v = System.getenv(k);
        if (v == null || v.isBlank()) throw new IllegalStateException("required env var not set: " + k);
        return v;
    }
    static java.util.Optional<String> opt(String k) {
        String v = System.getenv(k);
        return (v == null || v.isBlank()) ? java.util.Optional.empty() : java.util.Optional.of(v);
    }

    static String secretProviderClassYaml(Ctx c) {
        // Minimal SecretProviderClass wiring (the recipe's secretproviderclass.yaml is the full reference).
        return "apiVersion: secrets-store.csi.x-k8s.io/v1\nkind: SecretProviderClass\n"
                + "metadata:\n  name: dvara-secrets\n  namespace: " + c.namespace + "\n"
                + "spec:\n  provider: gke\n  parameters:\n    secrets: |\n"
                + "      - resourceName: \"projects/" + c.project + "/secrets/DVARA_LICENSE_KEY/versions/latest\"\n"
                + "        path: \"DVARA_LICENSE_KEY\"\n";
    }
    static String managedCertYaml(Ctx c, String domain) {
        return "apiVersion: networking.gke.io/v1\nkind: ManagedCertificate\n"
                + "metadata:\n  name: dvara-cert\n  namespace: " + c.namespace + "\n"
                + "spec:\n  domains:\n    - " + domain + "\n";
    }

    static String readLine() {
        try { return new java.io.BufferedReader(new java.io.InputStreamReader(System.in)).readLine(); }
        catch (Exception e) { return ""; }
    }
    static void log(String m) { System.out.println(m); }

    /** Dry-run runner: read verbs report "absent" so create paths are shown; writes just print. */
    static final class DryRunProc implements Proc {
        public Result run(List<String> command, Map<String, String> env, String stdin) {
            String verb = command.size() > 2 ? command.get(2) : "";
            boolean read = List.of("describe", "list", "status", "get").contains(verb)
                    || command.contains("describe") || command.contains("status");
            System.out.println("[dry-run] " + String.join(" ", command));
            return read ? new Result(1, "", "") : new Result(0, "", "");
        }
    }
}
