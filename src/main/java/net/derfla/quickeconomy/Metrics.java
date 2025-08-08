package net.derfla.quickeconomy;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import javax.net.ssl.HttpsURLConnection;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * bStats Metrics Collection Class
 * 
 * This class provides functionality for collecting and sending plugin usage statistics
 * to bStats (https://bStats.org). It collects anonymous data to help plugin authors
 * understand how their plugins are being used.
 * 
 * @author bStats Team
 * @version 3.0.2
 * @since 1.0.0
 */
public class Metrics {

    /** The plugin instance this metrics collector is associated with */
    private final Plugin plugin;

    /** The underlying metrics base that handles data collection and transmission */
    private final MetricsBase metricsBase;

    /**
     * Creates a new Metrics instance for collecting plugin statistics.
     * 
     * Initializes the bStats configuration, sets up data collection parameters,
     * and creates the underlying MetricsBase instance. If no configuration exists,
     * it creates a default configuration file with appropriate settings.
     *
     * @param plugin Your plugin instance that will be monitored
     * @param serviceId The unique service ID for your plugin, obtainable from
     *                  <a href="https://bStats.org/what-is-my-plugin-id">bStats plugin ID page</a>
     */
    public Metrics(JavaPlugin plugin, int serviceId) {
        this.plugin = plugin;
        // Get the config file
        File bStatsFolder = new File(plugin.getDataFolder().getParentFile(), "bStats");
        File configFile = new File(bStatsFolder, "config.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        if (!config.isSet("serverUuid")) {
            config.addDefault("enabled", true);
            config.addDefault("serverUuid", UUID.randomUUID().toString());
            config.addDefault("logFailedRequests", false);
            config.addDefault("logSentData", false);
            config.addDefault("logResponseStatusText", false);
            // Inform the server owners about bStats
            config
                    .options()
                    .setHeader(Arrays.asList(
                            "bStats (https://bStats.org) collects some basic information for plugin authors, like how",
                            "many people use their plugin and their total player count. It's recommended to keep bStats",
                            "enabled, but if you're not comfortable with this, you can turn this setting off. There is no",
                            "performance penalty associated with having metrics enabled, and data sent to bStats is fully",
                            "anonymous."
                    ))
                    .copyDefaults(true);
            try {
                config.save(configFile);
            } catch (IOException ignored) {
            }
        }
        // Load the data
        boolean enabled = config.getBoolean("enabled", true);
        String serverUUID = config.getString("serverUuid");
        boolean logErrors = config.getBoolean("logFailedRequests", false);
        boolean logSentData = config.getBoolean("logSentData", false);
        boolean logResponseStatusText = config.getBoolean("logResponseStatusText", false);
        metricsBase =
                new MetricsBase(
                        "bukkit",
                        serverUUID,
                        serviceId,
                        enabled,
                        this::appendPlatformData,
                        this::appendServiceData,
                        submitDataTask -> Bukkit.getScheduler().runTask(plugin, submitDataTask),
                        plugin::isEnabled,
                        (message, error) -> this.plugin.getLogger().log(Level.WARNING, message, error),
                        (message) -> this.plugin.getLogger().log(Level.INFO, message),
                        logErrors,
                        logSentData,
                        logResponseStatusText);
    }

    /**
     * Shuts down the underlying scheduler service.
     * This should be called when the plugin is disabled to clean up resources.
     */
    public void shutdown() {
        metricsBase.shutdown();
    }

    /**
     * Adds a custom chart to the metrics collection.
     * Custom charts allow plugins to send specific data points to bStats
     * for visualization on their plugin page.
     *
     * @param chart The custom chart to add to the metrics collection
     */
    public void addCustomChart(CustomChart chart) {
        metricsBase.addCustomChart(chart);
    }

    /**
     * Appends platform-specific data to the JSON builder.
     * This includes server information like player count, Bukkit version,
     * Java version, OS details, and plugin metadata.
     * 
     * @param builder The JSON object builder to append data to
     */
    private void appendPlatformData(JsonObjectBuilder builder) {
        builder.appendField("playerAmount", getPlayerAmount());
        builder.appendField("onlineMode", Bukkit.getOnlineMode() ? 1 : 0);
        builder.appendField("bukkitVersion", Bukkit.getVersion());
        builder.appendField("bukkitName", Bukkit.getName());
        builder.appendField("javaVersion", System.getProperty("java.version"));
        builder.appendField("osName", System.getProperty("os.name"));
        builder.appendField("osArch", System.getProperty("os.arch"));
        builder.appendField("osVersion", System.getProperty("os.version"));
        builder.appendField("coreCount", Runtime.getRuntime().availableProcessors());
        builder.appendField("pluginVersion", plugin.getPluginMeta().getVersion());
        builder.appendField("author", String.join(", ", plugin.getPluginMeta().getAuthors()));
        builder.appendField("dependencies", String.join(", ", plugin.getPluginMeta().getPluginDependencies()));
        builder.appendField("softDependencies", String.join(", ", plugin.getPluginMeta().getPluginSoftDependencies()));
    }

    /**
     * Appends service-specific data to the JSON builder.
     * This includes plugin-specific information like version.
     * 
     * @param builder The JSON object builder to append data to
     */
    private void appendServiceData(JsonObjectBuilder builder) {
        builder.appendField("pluginVersion", plugin.getPluginMeta().getVersion());
    }

    /**
     * Gets the current number of online players on the server.
     * Uses reflection to support different Bukkit API versions.
     * 
     * @return The number of online players
     */
    private int getPlayerAmount() {
        try {
            Method onlinePlayersMethod = Class.forName("org.bukkit.Server").getMethod("getOnlinePlayers");
            return onlinePlayersMethod.getReturnType().equals(Collection.class)
                    ? ((Collection<?>) onlinePlayersMethod.invoke(Bukkit.getServer())).size()
                    : ((Player[]) onlinePlayersMethod.invoke(Bukkit.getServer())).length;
        } catch (Exception e) {
            // Just use the new method if the reflection failed
            return Bukkit.getOnlinePlayers().size();
        }
    }

    /**
     * The core metrics collection and transmission engine.
     * This class handles the actual data collection, formatting, and
     * transmission to the bStats service.
     */
    public static class MetricsBase {

        /** The version of the Metrics class */
        public static final String METRICS_VERSION = "3.0.2";

        /** The bStats API endpoint URL template for data submission */
        private static final String REPORT_URL = "https://bStats.org/api/v2/data/%s";

        /** Scheduler for periodic data submission tasks */
        private final ScheduledExecutorService scheduler;

        /** The platform identifier (e.g., "bukkit") */
        private final String platform;

        /** Unique server identifier */
        private final String serverUuid;

        /** The service ID for this plugin */
        private final int serviceId;

        /** Consumer for appending platform-specific data */
        private final Consumer<JsonObjectBuilder> appendPlatformDataConsumer;

        /** Consumer for appending service-specific data */
        private final Consumer<JsonObjectBuilder> appendServiceDataConsumer;

        /** Consumer for handling task submission */
        private final Consumer<Runnable> submitTaskConsumer;

        /** Supplier to check if the service is still enabled */
        private final Supplier<Boolean> checkServiceEnabledSupplier;

        /** Consumer for logging errors */
        private final BiConsumer<String, Throwable> errorLogger;

        /** Consumer for logging information messages */
        private final Consumer<String> infoLogger;

        /** Whether errors should be logged */
        private final boolean logErrors;

        /** Whether sent data should be logged */
        private final boolean logSentData;

        /** Whether response status text should be logged */
        private final boolean logResponseStatusText;

        /** Set of custom charts to include in metrics */
        private final Set<CustomChart> customCharts = new HashSet<>();

        /** Whether metrics collection is enabled */
        private final boolean enabled;

        /**
         * Creates a new MetricsBase class instance with comprehensive configuration.
         * 
         * This constructor initializes the metrics collection system with all necessary
         * parameters for data collection, formatting, and transmission to bStats.
         * It sets up a dedicated scheduler thread and configures logging options.
         *
         * @param platform The platform identifier (e.g., "bukkit", "sponge")
         * @param serverUuid The unique server identifier for anonymization
         * @param serviceId The unique service ID for this plugin from bStats
         * @param enabled Whether metrics collection and transmission is enabled
         * @param appendPlatformDataConsumer Consumer that appends platform-specific data to JSON
         * @param appendServiceDataConsumer Consumer that appends service-specific data to JSON
         * @param submitTaskConsumer Consumer for task submission delegation (can be null)
         * @param checkServiceEnabledSupplier Supplier to verify if the service is still enabled
         * @param errorLogger Consumer for logging error messages and exceptions
         * @param infoLogger Consumer for logging informational messages
         * @param logErrors Whether error messages should be logged
         * @param logSentData Whether transmitted data should be logged for debugging
         * @param logResponseStatusText Whether HTTP response status should be logged
         */
        public MetricsBase(
                String platform,
                String serverUuid,
                int serviceId,
                boolean enabled,
                Consumer<JsonObjectBuilder> appendPlatformDataConsumer,
                Consumer<JsonObjectBuilder> appendServiceDataConsumer,
                Consumer<Runnable> submitTaskConsumer,
                Supplier<Boolean> checkServiceEnabledSupplier,
                BiConsumer<String, Throwable> errorLogger,
                Consumer<String> infoLogger,
                boolean logErrors,
                boolean logSentData,
                boolean logResponseStatusText) {
            ScheduledThreadPoolExecutor scheduler =
                    new ScheduledThreadPoolExecutor(1, task -> new Thread(task, "bStats-Metrics"));
            // We want delayed tasks (non-periodic) that will execute in the future to be
            // cancelled when the scheduler is shutdown.
            // Otherwise, we risk preventing the server from shutting down even when
            // MetricsBase#shutdown() is called
            scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            this.scheduler = scheduler;
            this.platform = platform;
            this.serverUuid = serverUuid;
            this.serviceId = serviceId;
            this.enabled = enabled;
            this.appendPlatformDataConsumer = appendPlatformDataConsumer;
            this.appendServiceDataConsumer = appendServiceDataConsumer;
            this.submitTaskConsumer = submitTaskConsumer;
            this.checkServiceEnabledSupplier = checkServiceEnabledSupplier;
            this.errorLogger = errorLogger;
            this.infoLogger = infoLogger;
            this.logErrors = logErrors;
            this.logSentData = logSentData;
            this.logResponseStatusText = logResponseStatusText;
            checkRelocation();
            if (enabled) {
                // WARNING: Removing the option to opt-out will get your plugin banned from
                // bStats
                startSubmitting();
            }
        }

        /**
         * Adds a custom chart to the metrics collection.
         * 
         * @param chart The custom chart to add
         */
        public void addCustomChart(CustomChart chart) {
            this.customCharts.add(chart);
        }

        /**
         * Shuts down the metrics scheduler and stops data collection.
         */
        public void shutdown() {
            scheduler.shutdown();
        }

        /**
         * Starts the periodic data submission process.
         * Schedules initial and recurring tasks to submit metrics data to bStats.
         * Uses randomized delays to distribute server load evenly.
         * WARNING: Modifying this code will get your plugin banned on bStats. Just
         * don't do it!
         */
        private void startSubmitting() {
            final Runnable submitTask =
                    () -> {
                        if (!enabled || !checkServiceEnabledSupplier.get()) {
                            // Submitting data or service is disabled
                            scheduler.shutdown();
                            return;
                        }
                        if (submitTaskConsumer != null) {
                            submitTaskConsumer.accept(this::submitData);
                        } else {
                            this.submitData();
                        }
                    };
            // Many servers tend to restart at a fixed time at xx:00 which causes an uneven
            // distribution of requests on the
            // bStats backend. To circumvent this problem, we introduce some randomness into
            // the initial and second delay.
            // WARNING: You must not modify and part of this Metrics class, including the
            // submit delay or frequency!
            // WARNING: Modifying this code will get your plugin banned on bStats. Just
            // don't do it!
            long initialDelay = (long) (1000 * 60 * (3 + Math.random() * 3));
            long secondDelay = (long) (1000 * 60 * (Math.random() * 30));
            scheduler.schedule(submitTask, initialDelay, TimeUnit.MILLISECONDS);
            scheduler.scheduleAtFixedRate(
                    submitTask, initialDelay + secondDelay, 1000 * 60 * 30, TimeUnit.MILLISECONDS);
        }

        /**
         * Collects and submits metrics data to bStats.
         * Builds the JSON payload with platform data, service data, and custom charts,
         * then schedules the actual network transmission.
         */
        private void submitData() {
            final JsonObjectBuilder baseJsonBuilder = new JsonObjectBuilder();
            appendPlatformDataConsumer.accept(baseJsonBuilder);
            final JsonObjectBuilder serviceJsonBuilder = new JsonObjectBuilder();
            appendServiceDataConsumer.accept(serviceJsonBuilder);
            JsonObjectBuilder.JsonObject[] chartData =
                    customCharts.stream()
                            .map(customChart -> customChart.getRequestJsonObject(errorLogger, logErrors))
                            .filter(Objects::nonNull)
                            .toArray(JsonObjectBuilder.JsonObject[]::new);
            serviceJsonBuilder.appendField("id", serviceId);
            serviceJsonBuilder.appendField("customCharts", chartData);
            baseJsonBuilder.appendField("service", serviceJsonBuilder.build());
            baseJsonBuilder.appendField("serverUUID", serverUuid);
            baseJsonBuilder.appendField("metricsVersion", METRICS_VERSION);
            JsonObjectBuilder.JsonObject data = baseJsonBuilder.build();
            scheduler.execute(
                    () -> {
                        try {
                            // Send the data
                            sendData(data);
                        } catch (Exception e) {
                            // Something went wrong! :(
                            if (logErrors) {
                                errorLogger.accept("Could not submit bStats metrics data", e);
                            }
                        }
                    });
        }

        /**
         * Sends the metrics data to the bStats API endpoint.
         * Compresses the data using GZIP and transmits it via HTTPS POST request.
         * 
         * @param data The JSON data to transmit
         * @throws Exception if network transmission fails
         */
        private void sendData(JsonObjectBuilder.JsonObject data) throws Exception {
            if (logSentData) {
                infoLogger.accept("Sent bStats metrics data: " + data.toString());
            }
            String url = String.format(REPORT_URL, platform);
            HttpsURLConnection connection = (HttpsURLConnection) new URI(url).toURL().openConnection();
            // Compress the data to save bandwidth
            byte[] compressedData = compress(data.toString());
            connection.setRequestMethod("POST");
            connection.addRequestProperty("Accept", "application/json");
            connection.addRequestProperty("Connection", "close");
            connection.addRequestProperty("Content-Encoding", "gzip");
            connection.addRequestProperty("Content-Length", String.valueOf(compressedData.length));
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "Metrics-Service/1");
            connection.setDoOutput(true);
            try (DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {
                outputStream.write(compressedData);
            }
            StringBuilder builder = new StringBuilder();
            try (BufferedReader bufferedReader =
                         new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    builder.append(line);
                }
            }
            if (logResponseStatusText) {
                infoLogger.accept("Sent data to bStats and received response: " + builder);
            }
        }

        /**
         * Checks that the bStats class was properly relocated to prevent conflicts.
         * This ensures that plugins don't use the default bStats package which
         * could cause conflicts between different plugins.
         */
        private void checkRelocation() {
            // You can use the property to disable the check in your test environment
            if (System.getProperty("bstats.relocatecheck") == null
                    || !System.getProperty("bstats.relocatecheck").equals("false")) {
                // Maven's Relocate is clever and changes strings, too. So we have to use this
                // little "trick" ... :D
                final String defaultPackage =
                        new String(new byte[] {'o', 'r', 'g', '.', 'b', 's', 't', 'a', 't', 's'});
                final String examplePackage =
                        new String(new byte[] {'y', 'o', 'u', 'r', '.', 'p', 'a', 'c', 'k', 'a', 'g', 'e'});
                // We want to make sure no one just copy & pastes the example and uses the wrong
                // package names
                if (MetricsBase.class.getPackage().getName().startsWith(defaultPackage)
                        || MetricsBase.class.getPackage().getName().startsWith(examplePackage)) {
                    throw new IllegalStateException("bStats Metrics class has not been relocated correctly!");
                }
            }
        }

        /**
         * Compresses the given string using GZIP compression.
         * This reduces the size of data transmitted to bStats servers.
         *
         * @param str The string to compress
         * @return The compressed byte array, or null if input is null
         * @throws IOException if compression fails
         */
        private static byte[] compress(final String str) throws IOException {
            if (str == null) {
                return null;
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(outputStream)) {
                gzip.write(str.getBytes(StandardCharsets.UTF_8));
            }
            return outputStream.toByteArray();
        }
    }

    /**
     * A simple pie chart that displays a single string value.
     * This chart type is useful for displaying categorical data like
     * server version, game mode, or configuration options.
     */
    public static class SimplePie extends CustomChart {

        /** The callable that provides the chart data */
        private final Callable<String> callable;

        /**
         * Creates a new SimplePie chart.
         *
         * @param chartId The unique identifier for this chart
         * @param callable The callable that returns the string value for the chart
         */
        public SimplePie(String chartId, Callable<String> callable) {
            super(chartId);
            this.callable = callable;
        }

        /**
         * Retrieves the chart data as a JSON object.
         * 
         * @return The chart data as JSON, or null if no data available
         * @throws Exception if data retrieval fails
         */
        @Override
        protected JsonObjectBuilder.JsonObject getChartData() throws Exception {
            String value = callable.call();
            if (value == null || value.isEmpty()) {
                // Null = skip the chart
                return null;
            }
            return new JsonObjectBuilder().appendField("value", value).build();
        }
    }

    /**
     * A multi-line chart that displays multiple numeric values over time.
     * Useful for showing trends and comparing multiple metrics simultaneously.
     */
    public static class MultiLineChart extends CustomChart {

        /** The callable that provides the chart data as a map */
        private final Callable<Map<String, Integer>> callable;

        /**
         * Creates a new MultiLineChart.
         *
         * @param chartId The unique identifier for this chart
         * @param callable The callable that returns a map of line names to values
         */
        public MultiLineChart(String chartId, Callable<Map<String, Integer>> callable) {
            super(chartId);
            this.callable = callable;
        }

        @Override
        protected JsonObjectBuilder.JsonObject getChartData() throws Exception {
            JsonObjectBuilder valuesBuilder = new JsonObjectBuilder();
            Map<String, Integer> map = callable.call();
            if (map == null || map.isEmpty()) {
                // Null = skip the chart
                return null;
            }
            boolean allSkipped = true;
            for (Map.Entry<String, Integer> entry : map.entrySet()) {
                if (entry.getValue() == 0) {
                    // Skip this invalid
                    continue;
                }
                allSkipped = false;
                valuesBuilder.appendField(entry.getKey(), entry.getValue());
            }
            if (allSkipped) {
                // Null = skip the chart
                return null;
            }
            return new JsonObjectBuilder().appendField("values", valuesBuilder.build()).build();
        }
    }

    /**
     * An advanced pie chart that displays multiple categories with their values.
     * This chart shows the distribution of different categories as slices of a pie.
     */
    public static class AdvancedPie extends CustomChart {

        /** The callable that provides the chart data as a map */
        private final Callable<Map<String, Integer>> callable;

        /**
         * Creates a new AdvancedPie chart.
         *
         * @param chartId The unique identifier for this chart
         * @param callable The callable that returns a map of category names to values
         */
        public AdvancedPie(String chartId, Callable<Map<String, Integer>> callable) {
            super(chartId);
            this.callable = callable;
        }

        @Override
        protected JsonObjectBuilder.JsonObject getChartData() throws Exception {
            JsonObjectBuilder valuesBuilder = new JsonObjectBuilder();
            Map<String, Integer> map = callable.call();
            if (map == null || map.isEmpty()) {
                // Null = skip the chart
                return null;
            }
            boolean allSkipped = true;
            for (Map.Entry<String, Integer> entry : map.entrySet()) {
                if (entry.getValue() == 0) {
                    // Skip this invalid
                    continue;
                }
                allSkipped = false;
                valuesBuilder.appendField(entry.getKey(), entry.getValue());
            }
            if (allSkipped) {
                // Null = skip the chart
                return null;
            }
            return new JsonObjectBuilder().appendField("values", valuesBuilder.build()).build();
        }
    }

    /**
     * A simple bar chart that displays categories with single values.
     * Each category is represented as a bar with its corresponding value.
     */
    public static class SimpleBarChart extends CustomChart {

        /** The callable that provides the chart data as a map */
        private final Callable<Map<String, Integer>> callable;

        /**
         * Creates a new SimpleBarChart.
         *
         * @param chartId The unique identifier for this chart
         * @param callable The callable that returns a map of category names to values
         */
        public SimpleBarChart(String chartId, Callable<Map<String, Integer>> callable) {
            super(chartId);
            this.callable = callable;
        }

        @Override
        protected JsonObjectBuilder.JsonObject getChartData() throws Exception {
            JsonObjectBuilder valuesBuilder = new JsonObjectBuilder();
            Map<String, Integer> map = callable.call();
            if (map == null || map.isEmpty()) {
                // Null = skip the chart
                return null;
            }
            for (Map.Entry<String, Integer> entry : map.entrySet()) {
                valuesBuilder.appendField(entry.getKey(), new int[] {entry.getValue()});
            }
            return new JsonObjectBuilder().appendField("values", valuesBuilder.build()).build();
        }
    }

    /**
     * An advanced bar chart that displays categories with multiple values.
     * Each category can have multiple bars representing different metrics.
     */
    public static class AdvancedBarChart extends CustomChart {

        /** The callable that provides the chart data as a map of arrays */
        private final Callable<Map<String, int[]>> callable;

        /**
         * Creates a new AdvancedBarChart.
         *
         * @param chartId The unique identifier for this chart
         * @param callable The callable that returns a map of category names to value arrays
         */
        public AdvancedBarChart(String chartId, Callable<Map<String, int[]>> callable) {
            super(chartId);
            this.callable = callable;
        }

        @Override
        protected JsonObjectBuilder.JsonObject getChartData() throws Exception {
            JsonObjectBuilder valuesBuilder = new JsonObjectBuilder();
            Map<String, int[]> map = callable.call();
            if (map == null || map.isEmpty()) {
                // Null = skip the chart
                return null;
            }
            boolean allSkipped = true;
            for (Map.Entry<String, int[]> entry : map.entrySet()) {
                if (entry.getValue().length == 0) {
                    // Skip this invalid
                    continue;
                }
                allSkipped = false;
                valuesBuilder.appendField(entry.getKey(), entry.getValue());
            }
            if (allSkipped) {
                // Null = skip the chart
                return null;
            }
            return new JsonObjectBuilder().appendField("values", valuesBuilder.build()).build();
        }
    }

    /**
     * A drilldown pie chart that displays hierarchical data.
     * Users can click on pie slices to see sub-categories within each main category.
     */
    public static class DrilldownPie extends CustomChart {

        /** The callable that provides nested chart data */
        private final Callable<Map<String, Map<String, Integer>>> callable;

        /**
         * Creates a new DrilldownPie chart.
         *
         * @param chartId The unique identifier for this chart
         * @param callable The callable that returns nested maps for hierarchical data
         */
        public DrilldownPie(String chartId, Callable<Map<String, Map<String, Integer>>> callable) {
            super(chartId);
            this.callable = callable;
        }

        @Override
        public JsonObjectBuilder.JsonObject getChartData() throws Exception {
            JsonObjectBuilder valuesBuilder = new JsonObjectBuilder();
            Map<String, Map<String, Integer>> map = callable.call();
            if (map == null || map.isEmpty()) {
                // Null = skip the chart
                return null;
            }
            boolean reallyAllSkipped = true;
            for (Map.Entry<String, Map<String, Integer>> entryValues : map.entrySet()) {
                JsonObjectBuilder valueBuilder = new JsonObjectBuilder();
                boolean allSkipped = true;
                for (Map.Entry<String, Integer> valueEntry : map.get(entryValues.getKey()).entrySet()) {
                    valueBuilder.appendField(valueEntry.getKey(), valueEntry.getValue());
                    allSkipped = false;
                }
                if (!allSkipped) {
                    reallyAllSkipped = false;
                    valuesBuilder.appendField(entryValues.getKey(), valueBuilder.build());
                }
            }
            if (reallyAllSkipped) {
                // Null = skip the chart
                return null;
            }
            return new JsonObjectBuilder().appendField("values", valuesBuilder.build()).build();
        }
    }

    /**
     * Abstract base class for all custom chart types.
     * Provides common functionality for chart data collection and formatting.
     */
    public abstract static class CustomChart {

        /** The unique identifier for this chart */
        private final String chartId;

        /**
         * Creates a new custom chart with the specified ID.
         * 
         * @param chartId The unique identifier for this chart
         * @throws IllegalArgumentException if chartId is null
         */
        protected CustomChart(String chartId) {
            if (chartId == null) {
                throw new IllegalArgumentException("chartId must not be null");
            }
            this.chartId = chartId;
        }

        /**
         * Builds the JSON request object for this chart.
         * 
         * @param errorLogger Consumer for logging errors that occur during data retrieval
         * @param logErrors Whether errors should be logged
         * @return The JSON object for the chart request, or null if data is unavailable
         */
        public JsonObjectBuilder.JsonObject getRequestJsonObject(
                BiConsumer<String, Throwable> errorLogger, boolean logErrors) {
            JsonObjectBuilder builder = new JsonObjectBuilder();
            builder.appendField("chartId", chartId);
            try {
                JsonObjectBuilder.JsonObject data = getChartData();
                if (data == null) {
                    // If the data is null we don't send the chart.
                    return null;
                }
                builder.appendField("data", data);
            } catch (Throwable t) {
                if (logErrors) {
                    errorLogger.accept("Failed to get data for custom chart with id " + chartId, t);
                }
                return null;
            }
            return builder.build();
        }

        /**
         * Abstract method that subclasses must implement to provide chart data.
         * 
         * @return The chart data as a JSON object, or null if no data available
         * @throws Exception if data retrieval fails
         */
        protected abstract JsonObjectBuilder.JsonObject getChartData() throws Exception;
    }

    /**
     * A single line chart that displays a single numeric value over time.
     * Useful for tracking individual metrics like player count or server performance.
     */
    public static class SingleLineChart extends CustomChart {

        /** The callable that provides the chart data */
        private final Callable<Integer> callable;

        /**
         * Creates a new SingleLineChart.
         *
         * @param chartId The unique identifier for this chart
         * @param callable The callable that returns the numeric value for the chart
         */
        public SingleLineChart(String chartId, Callable<Integer> callable) {
            super(chartId);
            this.callable = callable;
        }

        @Override
        protected JsonObjectBuilder.JsonObject getChartData() throws Exception {
            int value = callable.call();
            if (value == 0) {
                // Null = skip the chart
                return null;
            }
            return new JsonObjectBuilder().appendField("value", value).build();
        }
    }

    /**
     * An extremely simple JSON builder for constructing JSON objects.
     *
     * <p>While this class is neither feature-rich nor the most performant JSON builder available,
     * it's sufficient for the metrics data collection use-case. It provides basic functionality
     * to build JSON objects with various data types.
     */
    public static class JsonObjectBuilder {

        /** The string builder used to construct the JSON */
        private StringBuilder builder = new StringBuilder();

        /** Whether at least one field has been added to the JSON object */
        private boolean hasAtLeastOneField = false;

        /**
         * Creates a new JSON object builder and initializes it with an opening brace.
         */
        public JsonObjectBuilder() {
            builder.append("{");
        }

        /**
         * Appends a null field to the JSON.
         *
         * @param key The key of the field.
         * @return A reference to this object.
         */
        public JsonObjectBuilder appendNull(String key) {
            appendFieldUnescaped(key, "null");
            return this;
        }

        /**
         * Appends a string field to the JSON.
         *
         * @param key The key of the field.
         * @param value The value of the field.
         * @return A reference to this object.
         */
        public JsonObjectBuilder appendField(String key, String value) {
            if (value == null) {
                throw new IllegalArgumentException("JSON value must not be null");
            }
            appendFieldUnescaped(key, "\"" + escape(value) + "\"");
            return this;
        }

        /**
         * Appends an integer field to the JSON.
         *
         * @param key The key of the field.
         * @param value The value of the field.
         * @return A reference to this object.
         */
        public JsonObjectBuilder appendField(String key, int value) {
            appendFieldUnescaped(key, String.valueOf(value));
            return this;
        }

        /**
         * Appends an object to the JSON.
         *
         * @param key The key of the field.
         * @param object The object.
         * @return A reference to this object.
         */
        public JsonObjectBuilder appendField(String key, JsonObject object) {
            if (object == null) {
                throw new IllegalArgumentException("JSON object must not be null");
            }
            appendFieldUnescaped(key, object.toString());
            return this;
        }

        /**
         * Appends a string array to the JSON.
         *
         * @param key The key of the field.
         * @param values The string array.
         * @return A reference to this object.
         */
        public JsonObjectBuilder appendField(String key, String[] values) {
            if (values == null) {
                throw new IllegalArgumentException("JSON values must not be null");
            }
            String escapedValues =
                    Arrays.stream(values)
                            .map(value -> "\"" + escape(value) + "\"")
                            .collect(Collectors.joining(","));
            appendFieldUnescaped(key, "[" + escapedValues + "]");
            return this;
        }

        /**
         * Appends an integer array to the JSON.
         *
         * @param key The key of the field.
         * @param values The integer array.
         * @return A reference to this object.
         */
        public JsonObjectBuilder appendField(String key, int[] values) {
            if (values == null) {
                throw new IllegalArgumentException("JSON values must not be null");
            }
            String escapedValues =
                    Arrays.stream(values).mapToObj(String::valueOf).collect(Collectors.joining(","));
            appendFieldUnescaped(key, "[" + escapedValues + "]");
            return this;
        }

        /**
         * Appends an object array to the JSON.
         *
         * @param key The key of the field.
         * @param values The integer array.
         * @return A reference to this object.
         */
        public JsonObjectBuilder appendField(String key, JsonObject[] values) {
            if (values == null) {
                throw new IllegalArgumentException("JSON values must not be null");
            }
            String escapedValues =
                    Arrays.stream(values).map(JsonObject::toString).collect(Collectors.joining(","));
            appendFieldUnescaped(key, "[" + escapedValues + "]");
            return this;
        }

        /**
         * Appends a field to the object.
         *
         * @param key The key of the field.
         * @param escapedValue The escaped value of the field.
         */
        private void appendFieldUnescaped(String key, String escapedValue) {
            if (builder == null) {
                throw new IllegalStateException("JSON has already been built");
            }
            if (key == null) {
                throw new IllegalArgumentException("JSON key must not be null");
            }
            if (hasAtLeastOneField) {
                builder.append(",");
            }
            builder.append("\"").append(escape(key)).append("\":").append(escapedValue);
            hasAtLeastOneField = true;
        }

        /**
         * Builds the JSON string and invalidates this builder.
         *
         * @return The built JSON string.
         */
        public JsonObject build() {
            if (builder == null) {
                throw new IllegalStateException("JSON has already been built");
            }
            JsonObject object = new JsonObject(builder.append("}").toString());
            builder = null;
            return object;
        }

        /**
         * Escapes the given string like stated in https://www.ietf.org/rfc/rfc4627.txt.
         *
         * <p>This method escapes only the necessary characters '"', '\'. and '\u0000' - '\u001F'.
         * Compact escapes are not used (e.g., '\n' is escaped as "\u000a" and not as "\n").
         *
         * @param value The value to escape.
         * @return The escaped value.
         */
        private static String escape(String value) {
            final StringBuilder builder = new StringBuilder();
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '"') {
                    builder.append("\\\"");
                } else if (c == '\\') {
                    builder.append("\\\\");
                } else if (c <= '\u000F') {
                    builder.append("\\u000").append(Integer.toHexString(c));
                } else if (c <= '\u001F') {
                    builder.append("\\u00").append(Integer.toHexString(c));
                } else {
                    builder.append(c);
                }
            }
            return builder.toString();
        }

        /**
         * A simple representation of a JSON object.
         *
         * <p>This class provides type safety for the {@link JsonObjectBuilder} by wrapping
         * the final JSON string. It prevents raw string inputs for methods that expect
         * JSON objects, ensuring proper data handling.
         */
        public static class JsonObject {

            /** The JSON string representation */
            private final String value;

            /**
             * Creates a new JSON object wrapper.
             * 
             * @param value The JSON string to wrap
             */
            private JsonObject(String value) {
                this.value = value;
            }

            /**
             * Returns the JSON string representation.
             * 
             * @return The JSON string
             */
            @Override
            public String toString() {
                return value;
            }
        }
    }
}
