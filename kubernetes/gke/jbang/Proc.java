import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Minimal external-process runner, abstracted behind an interface so tests can
 * record/stub `gcloud` / `kubectl` / `helm` invocations without a real GCP project.
 * The DO tool ({@code dvara-infra/jbang/DvaraInfra.java}) shells out inline; we factor
 * it out to make the GKE provisioning logic unit-testable.
 */
public interface Proc {

    /** Run a command with extra env + optional stdin; never throws on non-zero exit. */
    Result run(List<String> command, Map<String, String> env, String stdin);

    default Result run(List<String> command) { return run(command, Map.of(), null); }
    default Result run(String... command) { return run(List.of(command), Map.of(), null); }

    record Result(int exit, String stdout, String stderr) {
        public boolean ok() { return exit == 0; }
        public String out() { return stdout == null ? "" : stdout.strip(); }
        public String errOut() { return stderr == null ? "" : stderr.strip(); }
    }

    /** Production implementation backed by {@link ProcessBuilder}. */
    final class Real implements Proc {
        private final boolean echo;
        public Real(boolean echo) { this.echo = echo; }

        @Override
        public Result run(List<String> command, Map<String, String> env, String stdin) {
            if (echo) System.err.println("+ " + String.join(" ", command));
            try {
                var pb = new ProcessBuilder(command);
                pb.environment().putAll(env);
                Process p = pb.start();
                if (stdin != null) {
                    try (var os = p.getOutputStream()) { os.write(stdin.getBytes()); }
                }
                String out = new String(p.getInputStream().readAllBytes());
                String err = new String(p.getErrorStream().readAllBytes());
                int code = p.waitFor();
                if (echo && !err.isBlank()) System.err.println(err.strip());
                return new Result(code, out, err);
            } catch (IOException e) {
                throw new RuntimeException("exec failed: " + String.join(" ", command), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("interrupted: " + String.join(" ", command), e);
            }
        }
    }
}
