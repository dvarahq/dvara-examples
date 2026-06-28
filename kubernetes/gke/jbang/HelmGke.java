import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code helm} + {@code kubectl} wrapper for the single cloud-agnostic dvara chart with
 * the GKE overlay ({@code values-gke.yaml}). Kept separate from {@link Gcloud} so the
 * chart install is cloud-neutral (cf. {@code dvara-infra/jbang/Helm.java}). All calls run
 * with {@code KUBECONFIG} forwarded via {@code env}.
 */
public final class HelmGke {

    private final Proc proc;
    private final Map<String, String> env; // carries KUBECONFIG

    public HelmGke(Proc proc, Map<String, String> env) {
        this.proc = proc;
        this.env = env;
    }

    // -- kubectl -------------------------------------------------------------

    public void ensureNamespace(String ns) {
        if (!proc.run(List.of("kubectl", "get", "namespace", ns), env, null).ok())
            require(proc.run(List.of("kubectl", "create", "namespace", ns), env, null), "create ns " + ns);
    }

    /** Upsert an opaque Secret from literal key=value pairs (k8s secret-mode). */
    public void applySecret(String ns, String name, Map<String, String> data) {
        var cmd = new ArrayList<>(List.of("kubectl", "-n", ns, "create", "secret", "generic", name));
        data.forEach((k, v) -> cmd.add("--from-literal=" + k + "=" + v));
        cmd.add("--dry-run=client");
        cmd.add("-o=yaml");
        Proc.Result rendered = proc.run(cmd, env, null);
        require(rendered, "render secret " + name);
        require(proc.run(List.of("kubectl", "apply", "-f", "-"), env, rendered.out()), "apply secret " + name);
    }

    public void applyManifest(String yaml) {
        require(proc.run(List.of("kubectl", "apply", "-f", "-"), env, yaml), "kubectl apply manifest");
    }

    public String serviceIngressIp(String ns, String name) {
        Proc.Result r = proc.run(List.of("kubectl", "-n", ns, "get", "ingress", name,
                "-o=jsonpath={.status.loadBalancer.ingress[0].ip}"), env, null);
        return r.ok() ? r.out() : "";
    }

    // -- helm ----------------------------------------------------------------

    public boolean isInstalled(String release, String ns) {
        Proc.Result r = proc.run(List.of("helm", "-n", ns, "status", release), env, null);
        return r.ok();
    }

    public void upgradeInstall(String release, String ns, String chartRef, String chartVersion,
                               List<String> valuesFiles, Map<String, String> setValues) {
        var cmd = new ArrayList<>(List.of("helm", "upgrade", "--install", release, chartRef,
                "-n", ns, "--create-namespace"));
        if (chartVersion != null && !chartVersion.isBlank()) { cmd.add("--version"); cmd.add(chartVersion); }
        for (String vf : valuesFiles) { cmd.add("-f"); cmd.add(vf); }
        new LinkedHashMap<>(setValues).forEach((k, v) -> { cmd.add("--set"); cmd.add(k + "=" + v); });
        cmd.add("--wait");
        cmd.add("--timeout=10m");
        require(proc.run(cmd, env, null), "helm upgrade --install " + release);
    }

    public void uninstall(String release, String ns) {
        if (isInstalled(release, ns))
            require(proc.run(List.of("helm", "-n", ns, "uninstall", release), env, null),
                    "helm uninstall " + release);
    }

    public void restartDeployments(String ns) {
        proc.run(List.of("kubectl", "-n", ns, "rollout", "restart", "deployment"), env, null);
    }

    private static void require(Proc.Result r, String what) {
        if (!r.ok()) throw new RuntimeException("failed [" + what + "] exit=" + r.exit()
                + (r.errOut().isBlank() ? "" : "\n" + r.errOut()));
    }
}
