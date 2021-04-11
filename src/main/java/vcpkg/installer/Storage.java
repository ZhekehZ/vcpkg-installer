package vcpkg.installer;

import vcpkg.installer.Utils.TetraConsumer;
import vcpkg.installer.Utils.TriConsumer;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static vcpkg.installer.Config.VCPKG_PATH;

public class Storage {
    static private final Pattern fromSearch = Pattern.compile("^(\\S+)\\s+((\\S+)\\s\\s)?\\s+(.*)?$");
    static private final Pattern fromList = Pattern.compile("^([^:]+):\\S+\\s+((\\S+)\\s\\s)?\\s+(.*)?$");

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    private final ConcurrentHashMap<String, PackageInfo> storage = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> names = new ConcurrentLinkedDeque<>();

    private boolean searchMode = false;

    private final Runnable triggerUI;

    private final AtomicReference<String> nextSearch = new AtomicReference<>(null);

    public Storage(Runnable triggerUI) {
        this.triggerUI = triggerUI;
        scheduler.scheduleAtFixedRate(this::updateInfo, 0, 2, TimeUnit.SECONDS);
    }

    public void shutdown() {
        scheduler.shutdown();
        pool.shutdown();
    }

    public void installAll(
            BiConsumer<String, Boolean> onFinishInstall,
            TetraConsumer<String, RemoveStatus, String, Consumer<Boolean>> onFinishRemove,
            BiConsumer<String, Boolean> onFinishRemoveRecursive
    ) {
        storage.forEach((name, info) -> {
            if (info.toInstall()) {
                info.setStatus(PackageInfo.Status.INSTALLING);
                pool.submit(() -> install(name, ok -> onFinishInstall.accept(name, ok)));
            } else if (info.toRemove()) {
                info.setStatus(PackageInfo.Status.REMOVING);
                pool.submit(() -> remove(name,
                        (ok, str, con) -> onFinishRemove.call(name, ok, str, con),
                        ok -> onFinishRemoveRecursive.accept(name, ok)));
            }
        });
    }

    public void searchAction(String str) {
        nextSearch.set(str);
        pool.submit(this::searchActionFunc);
    }

    public List<String> getNames() {
        return new ArrayList<>(names);
    }

    public PackageInfo get(String name) {
        return storage.getOrDefault(name, new PackageInfo("", "", "", PackageInfo.Status.REMOVING));
    }

    public enum RemoveStatus {
        OK, FAIL, ASK_RECURSIVE
    }

    private static List<String> readAll(InputStream input) throws IOException {
        var reader = new BufferedReader(new InputStreamReader(input));
        var result = new ArrayList<String>();
        while (reader.ready()) {
            result.add(reader.readLine());
        }
        return result;
    }

    synchronized private static List<String> runVCPKGGetOutput(
        List<String> command,
        boolean ignoreOutput,
        boolean withTimeout,
        boolean outputError
    ) {
        try {
            var pb = new ProcessBuilder(command);
            var process = pb.start();

            while (process.isAlive()) {
                try {
                    boolean ok = withTimeout
                        ? process.waitFor(2, TimeUnit.SECONDS)
                        : process.waitFor() == 0;

                    if (ok) {
                        if (process.exitValue() != 0) {
                            return outputError ? readAll(process.getInputStream()) : null;
                        }
                        break;
                    }
                    return outputError ? readAll(process.getInputStream()) : null;
                } catch (InterruptedException ignored) { }
            }
            if (outputError) return List.of();
            return ignoreOutput ? List.of() : readAll(process.getInputStream());
        } catch (IOException e) {
            return null;
        }
    }

    private static List<String> runVCPKG(List<String> command, boolean ignoreOutput, boolean withTimeout) {
        return runVCPKGGetOutput(command, ignoreOutput, withTimeout, false);
    }

    private void updateInfo() {
        var lines = runVCPKG(List.of(VCPKG_PATH, "list"), false, true);
        if (lines == null) {
            return;
        }

        var installed = lines.stream()
            .map(fromList::matcher)
            .filter(Matcher::find)
            .map(match -> new String[] { match.group(1), match.group(3), match.group(4) })
            .collect(Collectors.toMap(m -> m[0], Function.identity()));

        if (searchMode) {
            storage.forEach(
                (name, info) -> {
                    if (installed.containsKey(name))  {
                        info.ensureInstalled();
                    } else {
                        info.ensureRemoved();
                    }
                }
            );
        } else {
            storage.entrySet().removeIf(e -> {
                if (!installed.containsKey(e.getKey())) {
                    var pane = e.getValue().getUi().getRootPane();
                    if (pane != null) {
                        pane.remove(e.getValue().getUi());
                    }
                    e.getValue().getUi().setVisible(false);
                    return true;
                }
                return false;
            });
            installed.forEach(
                (name, arr) -> {
                    if (!storage.containsKey(name)) {
                        var newInfo = new PackageInfo(arr[0], arr[1], arr[2], PackageInfo.Status.INSTALLED);
                        storage.put(name, newInfo);
                    }
                }
            );

            names.clear();
            names.addAll(storage.keySet());
        }

        triggerUI.run();
    }

    private void searchActionFunc() {
        while (true) {
            var str = nextSearch.get();
            while (!nextSearch.compareAndSet(str, null)) {
                str = nextSearch.get();
            }
            if (str == null) {
                return;
            }

            if (str.isEmpty()) {
                searchMode = false;
                return;
            }
            searchMode = true;

            var lines = runVCPKG(List.of(VCPKG_PATH, "search", str), false, true);

            if (lines == null) {
                return;
            }

            final var found = lines.stream()
                    .map(fromSearch::matcher)
                    .filter(Matcher::find)
                    .map(match -> new String[]{match.group(1), match.group(3), match.group(4)})
                    .collect(Collectors.toMap(m -> m[0], Function.identity(), (a, b) -> a));

            storage.keySet().removeIf(k -> !found.containsKey(k));
            found.forEach(
                    (name, arr) -> storage.putIfAbsent(name,
                            new PackageInfo(arr[0], arr[1], arr[2], PackageInfo.Status.NOT_INSTALLED))
            );
            names.clear();
            names.addAll(storage.keySet());

            triggerUI.run();
        }
    }


    private void install(String name, Consumer<Boolean> onFinish) {
        var x = runVCPKG(List.of(VCPKG_PATH, "install", name), true, false);
        updateInfo();
        onFinish.accept(x != null);
    }

    private void remove(
            String name,
            TriConsumer<RemoveStatus, String, Consumer<Boolean>> onFinish,
            Consumer<Boolean> onFinishR
    ) {
        var x = runVCPKGGetOutput(
                List.of(VCPKG_PATH, "remove", name), false, false, true);
        updateInfo();
        if (x == null || x.isEmpty()) {
            onFinish.call(x == null ? RemoveStatus.FAIL : RemoveStatus.OK, null, null);
        } else {
            var sj = new StringJoiner("\n");
            x.forEach(sj::add);
            onFinish.call(RemoveStatus.ASK_RECURSIVE, sj.toString(), cancel ->
                    pool.submit(() -> removeRecurseOrCancel(name, cancel, onFinishR)));
        }
    }


    private void removeRecurseOrCancel(String name, Boolean cancel, Consumer<Boolean> onFinish) {
        var x = runVCPKG(List.of(VCPKG_PATH, "remove", name, "--recurse"), true, false);
        if (cancel) {
            storage.get(name).setStatus(PackageInfo.Status.INSTALLED);
        }
        updateInfo();
        onFinish.accept(x != null);
    }

}
