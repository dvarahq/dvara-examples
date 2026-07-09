///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21
//DEPS org.junit.jupiter:junit-jupiter:5.10.2
//DEPS org.junit.platform:junit-platform-launcher:1.10.2
//DEPS com.fasterxml.jackson.core:jackson-databind:2.18.1
//DEPS com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.1
//SOURCES Proc.java
//SOURCES GkeValues.java
//SOURCES GkeState.java
//SOURCES Gcloud.java
//SOURCES HelmGke.java

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** Unit tests — assert the gcloud/helm command construction + idempotency without a real GCP project.
 *  Run: {@code jbang DvaraGkeTest.java} (self-runs via the JUnit Platform launcher). */
class DvaraGkeTest {

    public static void main(String[] args) {
        var request = org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request()
                .selectors(org.junit.platform.engine.discovery.DiscoverySelectors.selectClass(DvaraGkeTest.class))
                .build();
        var launcher = org.junit.platform.launcher.core.LauncherFactory.create();
        var listener = new org.junit.platform.launcher.listeners.SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);
        var summary = listener.getSummary();
        var out = new java.io.PrintWriter(System.out, true);
        summary.printTo(out);
        summary.printFailuresTo(out);
        System.out.println("Tests: " + summary.getTestsSucceededCount() + " passed, "
                + summary.getTestsFailedCount() + " failed.");
        if (summary.getTotalFailureCount() > 0) System.exit(1);
    }

    /** Records every invocation and returns scripted results by command-substring. */
    static final class FakeProc implements Proc {
        final List<List<String>> calls = new ArrayList<>();
        Function<List<String>, Result> responder = c -> new Result(0, "", "");

        public Result run(List<String> command, Map<String, String> env, String stdin) {
            calls.add(command);
            return responder.apply(command);
        }
        boolean ran(String... allOf) {
            return calls.stream().anyMatch(c -> {
                String s = String.join(" ", c);
                for (String x : allOf) if (!s.contains(x)) return false;
                return true;
            });
        }
        long count(String token) {
            return calls.stream().filter(c -> String.join(" ", c).contains(token)).count();
        }
    }

    // ---- GkeValues ---------------------------------------------------------

    @Test void values_requiredAndDefaults() throws Exception {
        var v = GkeValues.of("gcp:\n  projectId: p1\ncluster:\n  name: c1\n");
        assertEquals("p1", v.text("gcp.projectId"));
        assertEquals("c1", v.text("cluster.name"));
        assertEquals("autopilot", v.text("cluster.mode", "autopilot"));
        assertEquals(2, v.intAt("cluster.nodeCount", 2));
        assertTrue(v.optional("domain.base").isEmpty());
    }

    @Test void values_replaceMeRefused() throws Exception {
        var v = GkeValues.of("gcp:\n  projectId: REPLACE_ME\n");
        assertThrows(IllegalStateException.class, () -> v.text("gcp.projectId"));
    }

    // ---- GkeState round-trip ----------------------------------------------

    @Test void state_jsonRoundTrip() throws Exception {
        var st = new GkeState();
        st.projectId = "p"; st.cluster = "c"; st.cloudsqlPrivateIp = "10.1.2.3";
        Path tmp = Files.createTempFile("gke-state", ".json");
        st.save(tmp);
        var back = GkeState.load(tmp);
        assertEquals("p", back.projectId);
        assertEquals("10.1.2.3", back.cloudsqlPrivateIp);
    }

    // ---- Gcloud: idempotency + command shape ------------------------------

    @Test void cluster_createdWhenAbsent_autopilot() {
        var p = new FakeProc();
        p.responder = c -> String.join(" ", c).contains("clusters describe")
                ? new Proc.Result(1, "", "not found") : new Proc.Result(0, "", "");
        new Gcloud(p, "proj").ensureCluster("dvara", "us-central1", "default",
                "autopilot", 2, "e2-standard-2", false);
        assertTrue(p.ran("container", "clusters", "create-auto", "dvara"), "should create autopilot cluster");
        // Autopilot auto-enables Workload Identity + VPC-native and REJECTS these
        // flags (gcloud: "unrecognized arguments"); they must NOT be passed.
        assertFalse(p.ran("--workload-pool=proj.svc.id.goog"), "autopilot must not pass --workload-pool");
        assertFalse(p.ran("--enable-ip-alias"), "autopilot must not pass --enable-ip-alias");
    }

    @Test void cluster_skippedWhenPresent() {
        var p = new FakeProc(); // everything returns exit 0 → describe says "exists"
        new Gcloud(p, "proj").ensureCluster("dvara", "us-central1", "default",
                "autopilot", 2, "e2-standard-2", false);
        assertFalse(p.ran("clusters", "create-auto"), "must NOT create when cluster already exists");
    }

    @Test void cloudSql_createdWithPrivateIpFlag() {
        var p = new FakeProc();
        p.responder = c -> String.join(" ", c).contains("instances describe")
                ? new Proc.Result(1, "", "") : new Proc.Result(0, "", "");
        new Gcloud(p, "proj").ensureCloudSql("dvara-pg", "us-central1", "default",
                "db-custom-1-3840", "POSTGRES_16");
        assertTrue(p.ran("sql", "instances", "create", "dvara-pg"));
        assertTrue(p.ran("--no-assign-ip"), "Cloud SQL must be private-IP only");
        assertTrue(p.ran("--network=projects/proj/global/networks/default"));
        // db-custom-* tiers require ENTERPRISE edition; new instances default to
        // ENTERPRISE_PLUS (which rejects them), so the edition must be pinned.
        assertTrue(p.ran("--edition=ENTERPRISE"), "Cloud SQL must pin ENTERPRISE edition for custom tiers");
    }

    @Test void privateServicesAccess_createsRangeThenPeers() {
        var p = new FakeProc();
        p.responder = c -> String.join(" ", c).contains("addresses describe")
                ? new Proc.Result(1, "", "") : new Proc.Result(0, "", "");
        new Gcloud(p, "proj").ensurePrivateServicesAccess("default");
        assertTrue(p.ran("compute", "addresses", "create", "google-managed-services-default"));
        assertTrue(p.ran("services", "vpc-peerings", "connect"));
    }

    @Test void enableApis_passesAllApis() {
        var p = new FakeProc();
        new Gcloud(p, "proj").enableApis(Gcloud.requiredApis());
        assertTrue(p.ran("services", "enable", "container.googleapis.com"));
        assertTrue(p.ran("sqladmin.googleapis.com"));
        assertTrue(p.ran("secretmanager.googleapis.com"));
    }

    @Test void cloudSqlPrivateIp_parsedFromJson() {
        var p = new FakeProc();
        p.responder = c -> new Proc.Result(0,
                "{\"ipAddresses\":[{\"type\":\"PRIMARY\",\"ipAddress\":\"1.2.3.4\"},"
              + "{\"type\":\"PRIVATE\",\"ipAddress\":\"10.20.30.40\"}]}", "");
        assertEquals("10.20.30.40", new Gcloud(p, "proj").cloudSqlPrivateIp("dvara-pg"));
    }

    // ---- HelmGke -----------------------------------------------------------

    @Test void helm_upgradeInstall_commandShape() {
        var p = new FakeProc();
        new HelmGke(p, Map.of("KUBECONFIG", "/tmp/kc")).upgradeInstall(
                "dvara", "dvara", "oci://ghcr.io/dvarahq/dvara/dvara", "",
                List.of("values-gke.yaml"), Map.of("image.tag", "1.2.2"));
        assertTrue(p.ran("helm", "upgrade", "--install", "dvara"));
        assertTrue(p.ran("-f", "values-gke.yaml"));
        assertTrue(p.ran("--set", "image.tag=1.2.2"));
        assertTrue(p.ran("--wait"));
    }

    @Test void helm_uninstall_onlyWhenInstalled() {
        var p = new FakeProc();
        p.responder = c -> String.join(" ", c).contains("status")
                ? new Proc.Result(1, "", "") : new Proc.Result(0, "", ""); // not installed
        new HelmGke(p, Map.of("KUBECONFIG", "/tmp/kc")).uninstall("dvara", "dvara");
        assertEquals(0, p.count("uninstall"), "must not uninstall a release that isn't present");
    }
}
