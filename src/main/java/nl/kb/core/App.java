package nl.kb.core;

import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.forms.MultiPartBundle;
import io.dropwizard.jdbi.DBIFactory;
import io.dropwizard.jersey.jackson.JsonProcessingExceptionMapper;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import nl.kb.core.config.FileStorageFactory;
import nl.kb.core.config.FileStorageGoal;
import nl.kb.core.databasetasks.LoadDatabaseSchemaTask;
import nl.kb.core.endpoints.HarvesterEndpoint;
import nl.kb.core.endpoints.ObjectHarvesterEndpoint;
import nl.kb.core.endpoints.RecordEndpoint;
import nl.kb.core.endpoints.RecordStatusEndpoint;
import nl.kb.core.endpoints.RepositoriesEndpoint;
import nl.kb.core.endpoints.RootEndpoint;
import nl.kb.core.endpoints.StatusWebsocketServlet;
import nl.kb.core.endpoints.StylesheetEndpoint;
import nl.kb.core.identifierharvester.IdentifierHarvestErrorFlowHandler;
import nl.kb.core.identifierharvester.IdentifierHarvester;
import nl.kb.core.idgen.IdGenerator;
import nl.kb.core.idgen.uuid.UUIDGenerator;
import nl.kb.core.mail.Mailer;
import nl.kb.core.mail.mailer.StubbedMailer;
import nl.kb.core.model.record.RecordBatchLoader;
import nl.kb.core.model.record.RecordDao;
import nl.kb.core.model.record.RecordReporter;
import nl.kb.core.model.reporting.ErrorReportDao;
import nl.kb.core.model.reporting.ErrorReporter;
import nl.kb.core.model.reporting.ExcelReportBuilder;
import nl.kb.core.model.reporting.ExcelReportDao;
import nl.kb.core.model.repository.RepositoryController;
import nl.kb.core.model.repository.RepositoryDao;
import nl.kb.core.model.repository.RepositoryValidator;
import nl.kb.core.model.statuscodes.ProcessStatus;
import nl.kb.core.model.stylesheet.StylesheetDao;
import nl.kb.core.model.stylesheet.StylesheetManager;
import nl.kb.core.objectharvester.ObjectHarvestErrorFlowHandler;
import nl.kb.core.objectharvester.ObjectHarvester;
import nl.kb.core.objectharvester.ObjectHarvesterOperations;
import nl.kb.core.objectharvester.ObjectHarvesterResourceOperations;
import nl.kb.core.scheduledjobs.DailyIdentifierHarvestScheduler;
import nl.kb.core.scheduledjobs.IdentifierHarvestSchedulerDaemon;
import nl.kb.core.scheduledjobs.ObjectHarvestSchedulerDaemon;
import nl.kb.core.websocket.SocketNotifier;
import nl.kb.filestorage.FileStorage;
import nl.kb.http.HttpFetcher;
import nl.kb.http.LenientHttpFetcher;
import nl.kb.http.responsehandlers.ResponseHandlerFactory;
import nl.kb.manifest.ManifestFinalizer;
import nl.kb.xslt.XsltTransformerFactory;
import org.skife.jdbi.v2.DBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.util.Map;

public class App extends Application<Config> {
    private static final Logger LOG = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws Exception {
        new App().run(args);
    }

    @Override
    public void initialize(Bootstrap<Config> bootstrap) {
        // Serve static files
        bootstrap.addBundle(new AssetsBundle("/assets", "/assets"));
        bootstrap.addBundle(new MultiPartBundle());
        // Support ENV variables in configuration yaml files.
        bootstrap.setConfigurationSourceProvider(
                new SubstitutingSourceProvider(bootstrap.getConfigurationSourceProvider(),
                        new EnvironmentVariableSubstitutor(false))
        );
    }

    @Override
    public void run(Config config, Environment environment) throws Exception {
        final Mailer mailer = config.getMailerFactory() == null ?
                new StubbedMailer() : config.getMailerFactory().getMailer();

        final DBIFactory factory = new DBIFactory();
        final DBI db = factory.build(environment, config.getDataSourceFactory(), "datasource");

        // Fault tolerant HTTP GET clients
        final HttpFetcher httpFetcherForIdentifierHarvest = new LenientHttpFetcher(true);
        final HttpFetcher httpFetcherForObjectHarvest = new LenientHttpFetcher(false);

        // Handler factory for responses from httpFetcher
        final ResponseHandlerFactory responseHandlerFactory = new ResponseHandlerFactory();


        // Data access objects
        final RepositoryDao repositoryDao = db.onDemand(RepositoryDao.class);
        final RecordDao recordDao = db.onDemand(RecordDao.class);
        final ErrorReportDao errorReportDao = db.onDemand(ErrorReportDao.class);
        final ExcelReportDao excelReportDao = db.onDemand(ExcelReportDao.class);
        final StylesheetDao stylesheetDao = db.onDemand(StylesheetDao.class);

        // File storage access
        final Map<FileStorageGoal, FileStorageFactory> fileStorageFactories = config.getFileStorageFactory();
        if (fileStorageFactories == null) {
            throw new IllegalStateException("No file storage configuration provided");
        }
        if (fileStorageFactories.get(FileStorageGoal.PROCESSING) == null) {
            throw new IllegalStateException("No file storage location provided for 'processing'");
        }
        if (fileStorageFactories.get(FileStorageGoal.DONE) == null) {
            throw new IllegalStateException("No file storage location provided for 'done'");
        }
        if (fileStorageFactories.get(FileStorageGoal.REJECTED) == null) {
            throw new IllegalStateException("No file storage location provided for 'rejected'");
        }

        final FileStorage processingStorage = fileStorageFactories.get(FileStorageGoal.PROCESSING).getFileStorage();
        final FileStorage doneStorage = fileStorageFactories.get(FileStorageGoal.DONE).getFileStorage();
        final FileStorage rejectedStorage = fileStorageFactories.get(FileStorageGoal.REJECTED).getFileStorage();

        // Handler for websocket broadcasts to the browser
        final SocketNotifier socketNotifier = new SocketNotifier();


        // Generates database aggregation of record (publication) statuses
        final RecordReporter recordReporter = new RecordReporter(db);
        // Generates database aggregation of reported errors
        final ErrorReporter errorReporter = new ErrorReporter(db);


        // Data transfer controllers
        // Fetches track numbers for new records using the number generator service
        final IdGenerator idGenerator = new UUIDGenerator();
        // Stores harvest states for the repositories in the database
        final RepositoryController repositoryController = new RepositoryController(repositoryDao, socketNotifier);
        // Stores batches of new records and updates ~oai deleted~ existing records in the database
        final RecordBatchLoader recordBatchLoader = new RecordBatchLoader(
                recordDao, repositoryDao, idGenerator, recordReporter, socketNotifier,
                config.getBatchLoadSampleMode());
        // Handler for errors in services the IdentfierHarvesters depend on (numbers endpoint; oai endpoint)
        final IdentifierHarvestErrorFlowHandler identifierHarvestErrorFlowHandler =
                new IdentifierHarvestErrorFlowHandler(repositoryController, mailer);


        // Builder for new instances of identifier harvesters
        final IdentifierHarvester.Builder harvesterBuilder = new IdentifierHarvester.Builder(repositoryController,
                recordBatchLoader, httpFetcherForIdentifierHarvest, responseHandlerFactory, repositoryDao);

        // Process that manages the amount of running identifier harvesters every 200ms
        final IdentifierHarvestSchedulerDaemon identifierHarvesterDaemon = new IdentifierHarvestSchedulerDaemon(
                harvesterBuilder,
                socketNotifier,
                identifierHarvestErrorFlowHandler,
                config.getMaxParallelHarvests()
        );

        // Helper classes for the ObjectHarvester
        // handles downloads of resources
        final ObjectHarvesterResourceOperations objectHarvesterResourceOperations =
                new ObjectHarvesterResourceOperations(httpFetcherForObjectHarvest, responseHandlerFactory);

        // Organises the operations of downloading a full publication object
        final ObjectHarvesterOperations objectHarvesterOperations = new ObjectHarvesterOperations(
                processingStorage, rejectedStorage, doneStorage,
                httpFetcherForObjectHarvest, responseHandlerFactory, new XsltTransformerFactory(),
                objectHarvesterResourceOperations, new ManifestFinalizer());

        // Handles expected failure flow (exceed maximum consecutive download failures
        final ObjectHarvestErrorFlowHandler objectHarvestErrorFlowHandler = new ObjectHarvestErrorFlowHandler(
                repositoryController, repositoryDao, mailer);

        // The object harvester
        final ObjectHarvester objectHarvester = new ObjectHarvester.Builder()
                .setRepositoryDao(repositoryDao)
                .setRecordDao(recordDao)
                .setErrorReportDao(errorReportDao)
                .setStylesheetDao(stylesheetDao)
                .setObjectHarvesterOperations(objectHarvesterOperations)
                .setRecordReporter(recordReporter)
                .setErrorReporter(errorReporter)
                .setSocketNotifier(socketNotifier)
                .setMaxSequentialDownloadFailures(config.getMaxConsecutiveDownloadFailures())
                .setObjectHarvestErrorFlowHandler(objectHarvestErrorFlowHandler)
                .create();

        // Initialize wrapped services (injected in endpoints)

        // Process that starts publication downloads every n miliseconds
        final ObjectHarvestSchedulerDaemon objectHarvesterDaemon = new ObjectHarvestSchedulerDaemon(
                objectHarvester,
                socketNotifier,
                config.getMaxParallelDownloads(),
                config.getDownloadQueueFillDelayMs()
        );

        // Validator for OAI/PMH settings of a repository
        final RepositoryValidator repositoryValidator = new RepositoryValidator(
                httpFetcherForIdentifierHarvest, responseHandlerFactory, stylesheetDao);

        // Fix potential data problems caused by hard termination of application
        try {
            // Reset all records which have PROCESSING state to PENDING
            recordDao.fetchAllByProcessStatus(ProcessStatus.PROCESSING.getCode()).forEachRemaining(record -> {
                record.setState(ProcessStatus.PENDING);
                recordDao.updateState(record);
            });
        } catch (Exception e) {
            LOG.warn("Failed to fix data on boot, probably caused by missing schema", e);
        }

        // Register endpoints

        // CRUD operations for repositories (harvest definitions)
        register(environment, new RepositoriesEndpoint(repositoryDao, repositoryValidator, repositoryController));

        // Read operations for records (find, view, download)
        register(environment, new RecordEndpoint(recordDao, errorReportDao, recordReporter,
                socketNotifier));

        // Operational controls for repository harvesters
        register(environment, new HarvesterEndpoint(repositoryDao, identifierHarvesterDaemon));

        // Operational controls for record fetcher
        register(environment, new ObjectHarvesterEndpoint(objectHarvesterDaemon));

        // Record status endpoint
        register(environment, new RecordStatusEndpoint(recordReporter, errorReporter, excelReportDao,
            new ExcelReportBuilder()));

        // Stylesheet management
        register(environment, new StylesheetEndpoint(new StylesheetManager(stylesheetDao), socketNotifier));

        // HTML + javascript app
        register(environment, new RootEndpoint(config.getHostName()));

        // Make JsonProcessingException show details
        register(environment, new JsonProcessingExceptionMapper(true));

        // Websocket servlet status update notifier
        registerServlet(environment, new StatusWebsocketServlet(), "statusWebsocket");

        // Lifecycle (scheduled databasetasks/deamons)
        // Process that starts publication downloads every 200ms
        environment.lifecycle().manage(new ManagedPeriodicTask(objectHarvesterDaemon));

        // Process that manages the amount of running identifier harvesters every 200ms
        environment.lifecycle().manage(new ManagedPeriodicTask(identifierHarvesterDaemon));

        // Process that starts harvests daily, weekly or monthly
        environment.lifecycle().manage(new ManagedPeriodicTask(new DailyIdentifierHarvestScheduler(
                repositoryDao,
                identifierHarvesterDaemon
        )));


        // Database task endpoints
        environment.admin().addTask(new LoadDatabaseSchemaTask(db));
    }


    private void register(Environment environment, Object component) {
        environment.jersey().register(component);
    }

    private void registerServlet(Environment environment, Servlet servlet, String name) {
        environment.servlets().addServlet(name, servlet).addMapping("/status-socket");
    }
}
