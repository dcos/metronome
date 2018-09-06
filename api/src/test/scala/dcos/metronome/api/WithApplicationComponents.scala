package dcos.metronome
package api

import controllers.AssetsComponents
import dcos.metronome.history.{ JobHistoryService, JobHistoryServiceFixture }
import dcos.metronome.jobinfo.JobInfoService
import dcos.metronome.jobinfo.impl.JobInfoServiceImpl
import dcos.metronome.jobrun.{ JobRunService, JobRunServiceFixture }
import dcos.metronome.jobspec.JobSpecService
import dcos.metronome.jobspec.impl.JobSpecServiceFixture
import dcos.metronome.queue.{ LaunchQueueService, QueueServiceFixture }
import mesosphere.marathon.core.base.ActorsModule
import mesosphere.marathon.core.plugin.PluginManager
import mesosphere.marathon.metrics.dummy.DummyMetricsModule
import org.scalatest.{ TestData, TestSuite }
import org.scalatestplus.play.guice.{ GuiceOneAppPerTest, GuiceOneServerPerSuite, GuiceOneServerPerTest }
import play.api.ApplicationLoader.Context
import play.api.i18n.I18nComponents
import play.api.routing.Router

import scala.concurrent.duration._
import scala.concurrent.duration.Duration
import play.api._
import play.filters.HttpFiltersComponents

/**
  * A trait that provides a components in scope and creates new components when newApplication is called
  *
  * This class has several methods that can be used to customize the behavior in specific ways.
  *
  * @tparam C the type of the fully-built components class
  */
trait WithApplicationComponents[C <: BuiltInComponents] {
  private var _components: C = _

  /**
    * @return The current components
    */
  final def components: C = _components

  /**
    * @return the components to be used by the application
    */
  def createComponents(context: Context): C

  /**
    * @return new application instance and set the components. This must be called for components to be properly set up.
    */
  final def newApplication: Application = {
    _components = createComponents(context)
    initialize(_components)
  }

  /**
    * Initialize the application from the components. This can be used to do eager instantiation or otherwise
    * set up things.
    *
    * @return the application that will be used for testing
    */
  def initialize(components: C): Application = _components.application

  /**
    * @return a context to use to create the application.
    */
  def context: ApplicationLoader.Context = {
    val classLoader = ApplicationLoader.getClass.getClassLoader
    val env = new Environment(new java.io.File("."), classLoader, Mode.Test)
    ApplicationLoader.createContext(env)
  }
}

trait OneAppPerTestWithComponents[T <: BuiltInComponents]
    extends GuiceOneAppPerTest
    with WithApplicationComponents[T] {
  this: TestSuite =>

  override def newAppForTest(testData: TestData): Application = newApplication
}

trait OneServerPerTestWithComponents[T <: BuiltInComponents]
    extends GuiceOneServerPerTest
    with WithApplicationComponents[T] {
  this: TestSuite =>

  override def newAppForTest(testData: TestData): Application = newApplication
}

trait OneServerPerSuiteWithComponents[T <: BuiltInComponents]
    extends GuiceOneServerPerSuite
    with WithApplicationComponents[T] {
  this: TestSuite =>

  override implicit lazy val app: Application = newApplication
}

class MockApiComponents(context: Context) extends BuiltInComponentsFromContext(context) with I18nComponents with AssetsComponents with HttpFiltersComponents {
  import com.softwaremill.macwire._
  // set up logger
  LoggerConfigurator(context.environment.classLoader).foreach {
    _.configure(context.environment)
  }

  override lazy val httpErrorHandler = new ErrorHandler

  lazy val actorsModule: ActorsModule = new ActorsModule(actorSystem)
  lazy val pluginManager: PluginManager = PluginManager.None
  lazy val jobSpecService: JobSpecService = JobSpecServiceFixture.simpleJobSpecService()
  lazy val jobRunService: JobRunService = JobRunServiceFixture.simpleJobRunService()
  lazy val jobHistoryService: JobHistoryService = JobHistoryServiceFixture.simpleHistoryService(Seq.empty)
  lazy val jobInfoService: JobInfoService = wire[JobInfoServiceImpl]
  lazy val queueService: LaunchQueueService = QueueServiceFixture.simpleQueueService()
  lazy val metricsModule: DummyMetricsModule = new DummyMetricsModule()

  lazy val config: ApiConfig = new ApiConfig {
    override def leaderProxyTimeout: Duration = 30.seconds
    override def hostnameWithPort: String = s"$hostname:$effectivePort"
    override def hostname: String = "localhost"
    override def effectivePort: Int = 9000
  }

  lazy val apiModule: ApiModule = wire[ApiModule]

  override def router: Router = apiModule.router
}
